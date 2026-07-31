# XCLogger v1.3.6 / AAR API 4 交付说明

## 1. 版本与构建结果

- XCLogger2：`versionCode 12`，`versionName 1.3.6`
- XCLogger AAR：API 4
- XCLoggerTestDemo：`versionCode 6`，`versionName 1.5`
- AAR SHA-256：`F91D193BAB2AC84815F45B8C1940E503D6E6EE09FB70EEA176CA2938D87418C7`
- XCLogger2 单元测试、AAR 单元测试及三个 release 产品构建均通过。
- Demo 单元测试及 `assembleStressFleetDebug` clean 构建通过，共生成 6 个 APK。
- 构建仅报告 Java 8 工具链弃用警告，按当前要求未处理。

## 2. 本次功能

结构化配置接口统一为扁平参数对象，不再使用嵌套的 update envelope：

```java
void updateConfiguration2(
        XcLoggerConfig2 update,
        IXcLoggerConfigUpdateCallback callback);
```

### 2.1 包名过滤三态

包名过滤模式统一由一个配置字段保存，不再公开 `usePackageWhitelist()` 和
`usePackageBlacklist()`：

```java
client.configUpdater()
        .packageFilterMode(XcLoggerConfigUpdater.PackageFilterMode.OFF)
        .commitAsync(callback);
```

可用状态：

- `OFF`：不执行包名过滤，白名单和黑名单内容保留但不生效。
- `WHITELIST`：仅放行包名白名单解析到的 UID/PID 日志。
- `BLACKLIST`：拒绝包名黑名单解析到的 UID/PID 日志，其余日志放行。

默认状态为 `OFF`。旧配置中的“白名单 + all + 空黑名单”升级时归一化为 `OFF`，
行为仍是全量放行。

白名单和黑名单分别通过下列接口维护：

```java
client.configUpdater()
        .filterPackages("com.example.app")
        .blacklistPackages("com.example.noisy")
        .packageFilterMode(XcLoggerConfigUpdater.PackageFilterMode.WHITELIST)
        .commitAsync(callback);
```

### 2.2 Tag 与 Level

- Tag 仅支持白名单的全量替换、`addTag()` 和 `removeTag()`。
- Tag 黑名单不再由 UI、广播控制入口或 AAR 接口更新。
- Level 继续使用当前 `F/E/W/I/D/V` 顺序和 logcat 原生参数，未增加黑白名单模式。
- `V` 表示不按 Level 丢弃日志，查询时仍返回 `v`。

### 2.3 内容过滤兼容策略

- 内容白名单、内容黑名单及 Level 黑名单不参与运行时过滤。
- XCLogger2 与 Demo UI 不再提供相关配置入口。
- 数据库、XML、Parcelable 中的历史字段继续保留，升级时不会删除既有值或改变序列化布局。

### 2.4 ZIP 操作历史

压缩流程先写入全部日志文件，再对仍在追加的操作历史文件建立固定长度快照，最后将快照作为
ZIP 的最后一个 entry 写入：

```text
A_OperationHistory_yyyyMMddHHmmss.txt
```

原操作历史文件不会被移动、截断或删除；临时快照在压缩结束后删除。

## 3. Android Studio 构建

### XCLogger2

在 `XCLogger2` 工程中执行：

```powershell
.\gradlew.bat :xclogger-api:clean :xclogger-api:test :xclogger-api:assembleRelease
.\gradlew.bat test :xclogger-api:test :app:assembleCommonRelease :app:assembleP1416TPinelabsRelease :app:assembleR2351ComboRelease
```

### XCLoggerTestDemo

在 `XcLoggerTestDemo` 工程中执行：

```powershell
.\gradlew.bat clean testDebugUnitTest assembleStressFleetDebug
```

Android Studio 的 Gradle 面板中也可直接执行 `assembleStressFleetDebug`，一次生成原 Demo
和 5 个 fork APK。

## 4. Demo 安装与使用

依次安装交付包中的 6 个 Demo APK：

```powershell
adb install -r XCLoggerTestDemo-debug.apk
adb install -r XCLoggerStress-fork1-debug.apk
adb install -r XCLoggerStress-fork2-debug.apk
adb install -r XCLoggerStress-fork3-debug.apk
adb install -r XCLoggerStress-fork4-debug.apk
adb install -r XCLoggerStress-fork5-debug.apk
```

包名分别为：

```text
com.xcheng.xcloggertestdemo
com.xcheng.xcloggertestdemo.fork1
com.xcheng.xcloggertestdemo.fork2
com.xcheng.xcloggertestdemo.fork3
com.xcheng.xcloggertestdemo.fork4
com.xcheng.xcloggertestdemo.fork5
```

打开原 Demo，进入 `LOG STRESS` 页面，设置每个 APK 每秒输出行数并开启后台日志输出。
原 Demo 与已安装的 fork1-fork5 会同时打印日志；每行包含实际包名、实例、Tag、Level、
session、序号、速率与运行时长，Tag 和 `V/D/I/W/E/F` Level 会循环覆盖。

进入 `CONFIG` 页面可验收包名过滤三态、Tag 白名单 replace/add/remove 和 Level 配置。
切换为 `OFF` 时两套包名列表应保留但不参与过滤；重新切回白名单或黑名单后原列表立即生效。

## 5. ZIP 验收

触发 XCLogger 压缩后，可使用以下命令确认操作历史是 ZIP 最后一个 entry：

```powershell
tar -tf <zip文件路径>
```

列表最后一行应为 `A_OperationHistory_yyyyMMddHHmmss.txt`。
