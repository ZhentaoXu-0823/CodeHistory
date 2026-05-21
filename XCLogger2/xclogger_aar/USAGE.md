# XCLogger AAR 使用说明

本文档说明如何在外部 Android 工程中集成并调用 `xclogger-api-release.aar`，用于对 `XCLogger` 应用进行 AIDL 远程控制与状态监听。

## 1. 产物说明

当前目录包含：

- `xclogger-api-release.aar`：AIDL 接口库
- `USAGE.md`（本文件）

AAR 内包含：

- `com.xcheng.xclogger.service.IXcLoggerService`
- `com.xcheng.xclogger.service.IXcLoggerListener`
- `com.xcheng.xclogger.util.XcLoggerConfig`（Parcelable）

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

`updateConfigurationPartial` 支持“部分更新”：

- 仅设置需要修改的字段
- 未设置字段保持 XCLogger 当前值不变

示例字段：

- `totalSizeGb`
- `fileSizeMb`
- `bufferSizeBytes`
- `logDir`
- `logPeriodHours`
- `filterTag`
- `filterLevel`
- `filterPackage`

## 6. 行为与限制说明

- 运行中更新配置时，XCLogger 内部会按策略执行重载流程以保证生效。
- 调用来源会在 XCLogger 内部做白名单校验（非白名单来源会被拒绝）。
- 操作请求会在 XCLogger 内部串行执行，并记录操作历史。

## 7. 建议验收步骤

1. 绑定服务成功
2. 调用 `isRunning()` 获取初始状态
3. 调用 `startLogging()` 并观察 `onStatusChanged`
4. 调用 `updateConfigurationPartial(...)` 并观察 `onOperationResult`
5. 调用 `triggerCompression()` 并观察 `onCompressFinished`
6. 调用 `stopLogging()` 并确认停止

## 8. 兼容性建议

- 推荐外部工程 `minSdk >= 29`
- 推荐使用与 XCLogger 相同的 `compileSdk/targetSdk`（33）进行联调
- 若外部工程开启混淆，请保持对 AIDL/Parcelable 类的保留
