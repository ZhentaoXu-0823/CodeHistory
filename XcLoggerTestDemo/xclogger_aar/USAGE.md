# XCLogger AAR 使用说明

本文档说明如何在外部 Android 工程中集成并调用 `xclogger-api-release.aar`，用于对 `XCLogger` 应用进行 AIDL 远程控制与状态监听。

## 1. 产物说明

当前目录包含：

- `xclogger-api-release.aar`：AIDL 接口库
- `USAGE.md`（本文件）

AAR 内包含：

- `com.xcheng.xclogger.service.IXcLoggerService`
- `com.xcheng.xclogger.service.IXcLoggerListener`
- `com.xcheng.xclogger.service.IXcLoggerConfigUpdateCallback`
- `com.xcheng.xclogger.util.XcLoggerConfig`（Parcelable）
- `com.xcheng.xclogger.util.XcLoggerConfigUpdater`（链式更新器）
- `XcLoggerConfig2` / `XcLoggerConfigUpdateResult`（统一结构化更新请求与结果）

## 2. 外部工程接入

### 2.1 拷贝 AAR

将 `xclogger-api-release.aar` 复制到外部工程模块（如 `app/`）下的 `libs/` 目录。

### 2.2 配置 Gradle

在外部工程模块 `build.gradle` 中添加：

```gradle
android {
    buildFeatures {
        aidl true
    }
}

dependencies {
    implementation files('libs/xclogger-api-release.aar')
}
```

## 3. 绑定服务

XCLogger 暴露了绑定服务 `com.xcheng.xclogger.REMOTE_BIND`。

外部应用可使用显式 Intent 绑定：

```java
Intent intent = new Intent("com.xcheng.xclogger.REMOTE_BIND");
intent.setPackage("com.xcheng.xclogger");
bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
```

在 `onServiceConnected` 中：

```java
IXcLoggerService service = IXcLoggerService.Stub.asInterface(iBinder);
```

## 4. AIDL 接口说明

### 4.1 控制接口

- `boolean startLogging()`
- `boolean stopLogging()`
- `boolean isRunning()`
- `XcLoggerConfig getConfiguration()`
- `boolean updateConfigurationPartial(XcLoggerConfig config)`
- `int getApiVersion()`：当前结构化更新要求返回 `4` 或更高
- `void updateConfiguration2(XcLoggerConfig2, IXcLoggerConfigUpdateCallback)`：唯一的异步结构化更新入口
- `boolean triggerCompression()`

### 4.2 回调接口

注册监听：

- `registerListener(IXcLoggerListener listener)`
- `unregisterListener(IXcLoggerListener listener)`

回调方法：

- `onStatusChanged(int status)`：0=Stopped，1=Running
- `onOperationResult(String opType, boolean success, String message, boolean runningState)`
- `onCompressFinished(boolean success, String message)`

## 5. 配置更新约定

推荐通过 Demo 的 `XcLoggerClient.configUpdater()` 使用 API 4 链式更新：

```java
client.configUpdater()
        .filterTags("ActivityManager")
        .addTag("MyTag")
        .filterPackages("com.demo.app")
        .blacklistPackages("com.demo.blocked")
        .packageFilterMode(XcLoggerConfigUpdater.PackageFilterMode.OFF)
        .filterLevel(XcLoggerConfigUpdater.LogLevel.WARN)
        .commitAsync(result -> {
            Log.i("Config2", "status=" + result.getStatus()
                    + ", changed=" + result.getChangedFields());
        });
```

配置更新支持以下公开字段：

- `totalSizeMb`
- `fileSizeMb`
- `bufferSizeBytes`
- `logDir`
- `logPeriodHours`
- `filterTag`
- `filterLevel`
- `filterPackage`
- `filterPackageBlacklist`
- `packageFilterMode` (`OFF` / `WHITELIST` / `BLACKLIST`)

Tag 白名单与 Package白/黑名单支持 replace/add/remove。add/remove 在 XCLogger 服务端串行执行，不使用客户端旧快照。Tag或Package白名单删除最后一项后自动恢复为 `all`；Package黑名单删除最后一项后变为空。包过滤模式通过一个字段保存三种状态，OFF时两套名单保持不变但不参与过滤。`AndroidRuntime`、`DEBUG`、`libc` 是关键崩溃 Tag，仍按 APK 既有策略绕过所有过滤。

## 6. 行为与限制说明

- 运行中更新配置时，XCLogger 内部会按策略执行重载流程以保证生效。
- 操作请求会在 XCLogger 内部串行执行，并记录操作历史。
- `commitAsync()` 为推荐接口；同步 `commit()` 只能在工作线程调用。
- `SUCCESS` 和 `NO_CHANGES` 的 `isSuccess()` 为 `true`；`APPLIED_RESTART_FAILED` 表示配置已落盘但日志服务重启失败。

## 7. 建议验收步骤

1. 绑定服务成功
2. 调用 `isRunning()` 获取初始状态
3. 调用 `startLogging()` 并观察 `onStatusChanged`
4. 打开 Config Test 页面，先 GET，再设置 Tag白名单、Package名单和三态模式并执行 APPLY
5. 确认页面返回 `status=0`、`changed` 包含对应字段，再次 GET 验证持久化值
6. 使用自动化广播验证原子 add/remove：

```bash
adb shell am broadcast -a com.xcheng.xcloggertestdemo.AUTO_TEST \
  --es op config_update_v2 --es addTag ChainTag \
  --es addBlacklistedPackage com.demo.blocked --es packageFilterMode blacklist
```

7. 查看 `AUTO_TEST_RESULT` 或 `adb logcat -s AutoTest`，结果应为 `PASS`
8. 调用 `config_get`，确认返回的 `filterTag`、`filterPackage`、`packageBlacklist`和`packageFilterMode`与预期一致
9. Package 黑名单验收可调用 `emit_log --es marker XCLOGGER_PACKAGE_MARKER`；黑名单包含 Demo 包时采集文件中不得出现该 marker，清空黑名单后应能出现
10. 调用 `triggerCompression()` 并观察 `onCompressFinished`，导出的ZIP最后一个entry应为`A_OperationHistory_yyyyMMddHHmmss.txt`
11. 调用 `stopLogging()` 并确认停止

## 8. 兼容性建议

- 推荐外部工程 `minSdk >= 29`
- 推荐使用与 XCLogger 相同的 `compileSdk/targetSdk`（33）进行联调
- 若外部工程开启混淆，请保持对 AIDL/Parcelable 类的保留

## 9. Demo 日志输出压测

Demo 主页面的 `LOG STRESS` 可进入日志输出压测页面：

- 支持 10–100 行/秒连续调速，日志标签固定为 `XCLoggerStress`
- 开启后由前台 Service 在独立线程持续打印，退出控制页面不会停止
- 再次进入页面会读取压测工作线程的真实运行状态、当前速率、累计行数和运行时间
- 可通过通知返回控制页面；关闭开关后服务停止

验收时可使用：

```bash
adb logcat -s XCLoggerStress
```
