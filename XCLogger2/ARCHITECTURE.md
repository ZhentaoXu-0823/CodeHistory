# XcLogger 架构文档

> **最后更新**：2026-07-31
> **项目版本**：v1.3.6 / 配置协议 API 4
> **最低 SDK**：API 29 (Android 10)
> **目标 SDK**：API 33 (Android 13)
> **系统要求**：`android:sharedUserId="android.uid.system"`

---

## 一、项目概述

XcLogger 是运行在系统签名环境的 Android 系统日志采集与管理工具。应用执行 `logcat` 捕获系统日志，提供配置过滤、文件轮转与清理、压缩归档、AIDL/广播控制和操作审计能力，面向 MDM 场景提供后台日志基础设施。

| 能力 | 当前实现 |
|------|----------|
| 日志捕获 | `logcat -v threadtime,uid`；按 Tag / Level 过滤，并将配置包名映射为 UID/PID；UID 为主判据，PID 为后备判据 |
| 文件管理 | 文件写入、按大小轮转、按周期清理、全局索引命名 |
| 配置管理 | XML 默认配置加载至 SharedPreferences；支持部分更新和 XML 文件导入 |
| 服务化 | 前台 Service 承载采集；BOOT_COMPLETED / MY_PACKAGE_REPLACED 后恢复 |
| 远程控制 | AIDL + 广播，支持启停、配置、压缩、上传结果回传和状态查询 |
| 压缩归档 | 无时间范围时按日期生成 ZIP；有时间范围时生成单 ZIP；每个 ZIP 最后写入操作历史快照；输出至 `/data/xclogger/mobilelog` |
| 加密与审计 | 提供 XOR 加密类；关键流程写入 `A_OperationHistory.txt`，压缩时不移动或截断原文件 |

---

## 二、源码范围与 Android Studio 模块

本文只描述当前 Android Studio/Gradle 工程中两个源码模块：

- `app/`：Android Application 模块，产出 XCLogger APK，包含日志采集、服务、广播、AIDL 服务端、UI、资源和 Manifest。
- `xclogger-api/`：Android Library 模块，产出面向外部集成的 API AAR，包含 AIDL 接口副本和 `XcLoggerConfig` Parcelable。

`xclogger_aar/` 仅用于放置预编译分发产物，不是当前 Gradle settings 中的源码模块；其他目录不属于本文档的架构范围。

```
XCLogger/ (rootProject.name)
├── settings.gradle              # 声明 :app、:xclogger-api
├── build.gradle                 # Android Gradle Plugin 8.1.3 声明
├── gradle.properties            # JVM、AndroidX、non-transitive R 配置
├── keystore.gradle              # app flavor 的签名和 applicationId 配置
├── app/                         # com.android.application 模块
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── aidl/                # 服务端 AIDL 定义
│       ├── java/                # APK 实现源码
│       └── res/                 # UI、默认 XML 与 flavor 配置资源
└── xclogger-api/                # com.android.library 模块
    ├── build.gradle
    └── src/main/
        ├── aidl/                # 对外 AIDL 副本
        └── java/                # 对外 XcLoggerConfig Parcelable
```

`app` 与 `xclogger-api` 当前没有声明 Gradle `project(...)` 依赖；两者通过维护同包名、同方法签名的 AIDL 副本，以及同 Parcelable 字段顺序保持接口兼容。修改 AIDL 或 `XcLoggerConfig` 时，必须同步更新两个模块并重新发布 AAR。

---

## 三、核心架构分层

```
控制入口层
  MainActivity / XcLoggerBroadcastReceiver / AIDL Client
                     │
控制编排层
  SourceResolver → CommandSerialExecutor
  SourceWhitelistGuard 已定义，但当前未接入命令执行链路
                     │
服务调度层
  LogServiceController → ConfigLoader → ProcessController
                     │
采集与持久化层
  SystemLogCatcher → LogBuffer → FileManager / XcXorEncryption
```

---

## 四、模块职责分工

| 包 | 类/组件 | 当前职责 |
|----|---------|----------|
| `control` | `CommandSerialExecutor` | 单线程串行执行 start、stop、restart、配置、压缩与状态查询 |
| `control` | `PartialConfigMerger` / `ConfigUpdateApplier` | 兼容旧 patch；原子处理基础字段、Tag/Package 白名单、Package 黑名单及包过滤三态 |
| `control` | `FilterConfigValidator` | 校验 Tag、Level、Package 过滤值的格式、长度和数量，拒绝 shell 元字符等非法输入 |
| `control` | `SourceResolver` | 广播来源取 `Intent.getPackage()`，未指定时为 `adb`；AIDL 通过调用 UID 取首个包名 |
| `control` | `SourceWhitelistGuard` | 定义 `adb`、`com.xcheng.xcloggertestdemo` 判断，但没有实际拦截请求 |
| `recorder` | `SystemLogCatcher` | 运行/监控 logcat 子进程，执行 Tag、Level 和 Package OFF/白名单/黑名单过滤；动态维护包名到 UID/PID 映射 |
| `recorder` | `FilterPipeline` | 保留历史扩展过滤结构；Tag/Level/Content 黑名单和 Content 白名单未接入当前采集循环 |
| `filemanager` | `FileManager` | 日志文件创建、写入、轮转、清理、操作历史维护及固定长度历史快照 |
| `filemanager` | `FileCompressService` | 异步压缩、将操作历史作为 ZIP 最后一个 entry、上传状态、结果广播、ZIP 清理 |
| `processctr` | `ConfigLoader` | XML / SharedPreferences 配置加载、缓存、更新和导入 |
| `processctr` | `LogServiceController` | 前台服务生命周期调度 + sServiceActive（AtomicBoolean CAS）竞态守卫 |
| `service` | `LogCaptureService` | 创建通知后立即进入前台，启动/停止采集流程 |
| `service` | `RemoteBindService` | AIDL 服务、监听器管理和管道 ZIP 输出 |
| `receiver` | `XcLoggerBroadcastReceiver` | 系统事件、自定义控制广播和结果广播发送 |
| `receiver` | `PackageEventManager` | 安装/卸载包时维护 UID/PID 集合和包名前缀刷新；刷新由日志读取循环约每 10 秒触发 |
| `util` | `XcLoggerConfig` | 跨进程配置对象及历史兼容字段；结构化更新统一使用扁平的 `XcLoggerConfig2` |
| `util` | `XcLoggerDatabase` | SharedPreferences 配置、运行状态、压缩状态和历史路径封装 |

### 压缩包命名与筛选

| 触发方式 | ZIP 输出 |
|---------|----------|
| 未传时间范围 | 对快照日志按日期分组；每个日期生成 `yyyy_MMdd_HHmmss_XXXX.zip` |
| 传入开始或结束时间 | 调用 `filterByTimeRangeV2()` 筛选重叠文件，生成由 `startTime` 参与构造的单个 ZIP；仅传 `endTime` 时可在筛选后因 `startTime=null` 触发异常 |

时间参数会移除非数字字符后按 `yyyyMMddHHmmss` 比较；无法解析或开始时间晚于结束时间时当前实现回退为原始快照。范围压缩以相邻日志文件时间戳估算内容区间，保留与请求区间重叠的文件；最后一份文件只要其开始时间早于结束时间即纳入。当前没有严格格式、真实日期或输出 canonical path 校验。

每个 ZIP 在全部日志 entry 写完后调用 `FileManager.snapshotOperationHistory()` 复制操作历史的固定长度前缀，并将 `A_OperationHistory_yyyyMMddHHmmss.txt` 写为最后一个 entry。共享进程锁只用于确定快照边界和追加单条历史；复制期间可继续追加历史。原历史文件不会被移动、截断或删除，临时快照在压缩结束后清理。

---

## 五、关键数据流

### 5.1 日志采集流

```
控制请求 / 启动广播
  → LogServiceController → LogCaptureService
  → ProcessController.startLogging()
  → ConfigLoader.load() → FileManager
  → SystemLogCatcher.startCapture(config)
      ├── FilterConfigValidator 校验 Tag、Level、Package
      ├── 扫描运行中进程并建立 filterPidSet
      ├── 构造 logcat 参数列表并由 ProcessBuilder 启动
      ├── 启动 readLogcatOutput / readErrorOutput / monitorProcess
      └── Tag AND Level AND PID 匹配 → LogBuffer → FileManager
```

`monitorProcess()` 每 5 秒检查 logcat 子进程。异常退出时按 10s → 20s → 40s → 80s 指数退避重启；主动停止会阻止延迟重启。

### 5.2 配置加载流

```
ConfigLoader.load()
  → getDatabaseVersion() < APK_CONFIG_VERSION ?
      → 从 flavor XML 加载并 saveInitialConfig()
  → 否则优先从 SharedPreferences 加载
  → 无保存配置时从 XML 初始化
```

当前 `getDatabaseVersion()` / `setDatabaseVersion()` 实际读写 `config_version`；`database_version` 常量虽定义，但不在该路径使用。

### 5.3 AIDL 控制流

`RemoteBindService` 将启停、配置更新和压缩请求放入 `CommandSerialExecutor` 顺序执行。唯一的结构化配置入口是 `updateConfiguration2(XcLoggerConfig2, callback)`；`XcLoggerConfig2` 直接承载基础字段、名单变更及可选的 `packageFilterMode`。配置更新完成后异步返回成功状态、错误原因和变更字段；名单增删基于服务端最新配置执行。`getApiVersion()` 当前返回 4。`getConfiguration()` 读取当前配置缓存，配置尚未加载时可能返回 `null`。

### 5.4 包事件与 PID 缓存刷新

`PACKAGE_ADDED` 经 `PackageEventManager.handlePackageInstalled()` 重新扫描当前模式对应名单的 UID/PID；`PACKAGE_REMOVED` 会同步移除映射。`readLogcatOutput()` 约每 10 秒刷新一次缓存。`OFF` 不执行包过滤；白名单和黑名单互斥，不叠加计算。

---

## 六、过滤系统

### 6.1 当前生效过滤

```
logcat 行 → parseLogLine(uid, pid, tid, level, tag)
  → matchesTagFilter()
  → matchesLevelFilter()
  → matchesPackageMode(uid, pid)
  → 三条件 AND 通过后写入文件
```

Tag 配置会生成 `*:S`、`TagA:E`、`TagB:E` 等独立 logcat filter spec；`AndroidRuntime`、`DEBUG`、`libc` 以 `:V` 追加，并在 Java 侧绕过当前过滤。Level 优先级为 `f > e > w > i > d > v`，默认 `v` 表示不按 Level 丢弃。Package 过滤支持 `OFF`、`WHITELIST`、`BLACKLIST` 三态；扫描包与运行进程形成 UID/PID 映射，UID 为稳定主判据，PID 为兼容后备判据。精确包名和以 `.` 结尾的前缀匹配均受支持；白名单无匹配身份时不放行，黑名单无匹配身份时放行。

### 6.2 过滤配置校验与命令执行

`FilterConfigValidator` 在部分更新、XML 文件导入、配置持久化和采集启动前校验三个基础过滤字段：

- `filterTag`：精确小写 `all`，或逗号分隔的 `[A-Za-z0-9_.-]{1,64}`；
- `filterLevel`：精确小写 `all`，或单个 `F/E/W/I/D/V` 字符（大小写均可）；
- `filterPackage`：精确小写 `all`，或逗号分隔的 Java 风格包名；末尾可保留一个 `.` 表示前缀匹配；
- Tag / Package 列表总长度最多 4096 字符、最多 64 项，列表项两侧不允许空白。

非法值会使配置更新或导入失败。`SystemLogCatcher` 将 `logcat`、`-v`、`-T` 和各 filter spec 作为独立参数交给 `ProcessBuilder`，不再通过 `sh -c` 拼接执行，因此过滤值不会被 shell 解释。

### 6.3 兼容过滤字段状态

`XcLoggerConfig`、默认 XML、数据库和 Parcelable 布局继续保留下列历史字段，避免升级后的数据不一致或跨进程序列化破坏：

- `filterTagBlacklist`
- `filterPackageBlacklist`
- `filterLevelBlacklist`
- `filterContent`
- `filterContentBlacklist`

当前只有 Package 黑名单在 `BLACKLIST` 模式参与采集。Tag 黑名单、Level 黑名单和 Content 白/黑名单不由 UI、广播入口或 AAR updater 更新，也不参与采集。数据库仍保存全部兼容字段；部分更新会原样保留其既有值。

Package 模式使用单个持久化值 `off` / `whitelist` / `blacklist`。默认 `off`；从旧配置升级时，“whitelist + all + 空黑名单”归一化为 `off`，行为保持全量放行。

---

## 七、动态控制实际行为

1. 广播和 AIDL 的大多数控制请求通过 `CommandSerialExecutor` 单线程排队。
2. 日志运行时更新基础配置会执行 stop → update → start；未运行时只更新配置。`XcLoggerDatabase.saveConfig()` 的 `commit()` 返回 `false` 或抛出异常时，更新返回失败且不替换内存配置。
3. 解析后的来源会记录到历史，但 `SourceWhitelistGuard` 当前不会拒绝调用。
4. `CTRL_RESULT` 会发送给 `TARGET_PACKAGES`：`com.xcheng.mdm`、`com.xcheng.xcloggertestdemo`、`com.xcheng.xclogger`、`com.ko.xclogger`。
5. `query_files_dir`、`query_zip_dir` 是广播 `op_type`，不是 AIDL 方法。

---

## 八、技术规格

| 配置项 | 当前默认/实现 | 说明 |
|--------|---------------|------|
| 日志存储路径 | `/storage/emulated/0/XcLogger` | XML 默认值，可配置 |
| 单文件大小 | 4 | `fileSizeMb`，按 MB 使用 |
| 缓冲区 | XML 2048 byte；DB 无值回退 1024 byte | 部分更新按 512 对齐、最大 4096 |
| 日志保留周期 | 168 小时 | XML 默认值 |
| 总空间上限 | XML 默认 1024 | 字段与持久化名为 `totalSizeMb`，按 MB 传入 |
| 压缩目录 | `/data/xclogger/mobilelog` | 固定输出目录 |

---

## 九、Android Studio / Gradle 构建架构

### 9.1 根工程与构建环境

| 项目 | 当前配置 |
|------|----------|
| 根工程名 | `XCLogger` |
| Gradle 模块 | `:app`、`:xclogger-api` |
| Android Gradle Plugin | 8.1.3 |
| 仓库 | `google()`、`mavenCentral()`、`gradlePluginPortal()` |
| Gradle JVM 参数 | `-Xmx2048m -Dfile.encoding=UTF-8` |
| AndroidX | 已启用 `android.useAndroidX=true` |
| R 类策略 | `android.nonTransitiveRClass=true` |
| Java 编译级别 | 两个模块均为 Java 8 |
| AIDL | 两个模块均启用 `buildFeatures.aidl=true` |

### 9.2 `app` APK 模块

`app` 使用 `com.android.application` 插件，`namespace` 为 `com.xcheng.xclogger`，`compileSdk` 为 33，`minSdk` 为 29，`targetSdk` 为 33，版本为 `versionCode 8` / `versionName 1.2.14`。

| 构建维度 | 配置 |
|----------|------|
| Build Type | `debug`、`release` |
| Debug 签名 | 显式设置 `signingConfig null`，由 flavor 的签名配置决定 |
| Release | 启用 `minifyEnabled true` 和 `shrinkResources true`，使用 `proguard-rules.pro` |
| Flavor dimension | `customer` |
| `common` flavor | applicationId `com.xcheng.xclogger`；`flavor_config=default_config`；使用 `signingConfigs.debug` |
| `p1416TPinelabs` flavor | applicationId、签名从 `keystore.gradle` 的 `keystoreConfigs.p1416TPinelabs` 读取；`flavor_config=p1416t_pinelabs_default_config` |
| `r2351Combo` flavor | applicationId、签名从 `keystore.gradle` 的 `keystoreConfigs.r2351Combo` 读取；`flavor_config=r2351_combo_default_config` |

因此共有 common、p1416TPinelabs、r2351Combo 三组 debug/release APK 变体。每个变体的输出文件名都被设置为 `XCLogger_v<versionName>_<yyyyMMddHHmm>.apk`；debug 与 release 当前使用相同命名规则。

`ConfigLoader.loadFromXml()` 读取 flavor 的 `flavor_config` string resource，选择相应 XML 默认配置。新增客户 flavor 时，需要在 `keystore.gradle` 添加签名/applicationId 条目，在 `app/build.gradle` 添加 flavor，并新增对应 XML 资源。

`afterEvaluate` 中额外注册了兼容性测试任务别名，并使 `assembleCommonDebug` 依赖 `assembleP1416TPinelabsDebug` 和 `assembleR2351ComboDebug`；在 Android Studio 或命令行选择/执行 `assembleCommonDebug` 时，会连带构建三个 debug flavor。

### 9.3 `xclogger-api` AAR 模块

`xclogger-api` 使用 `com.android.library` 插件，`namespace` 为 `com.xcheng.xclogger.api`，`compileSdk` 为 33，`minSdk` 为 29。该模块没有 product flavor，生成 debug/release library 变体；其 release 构建关闭混淆（`minifyEnabled false`）。

模块依赖仅包含 `androidx.annotation:annotation:1.7.1`，并将 `consumer-rules.pro` 作为消费者规则。消费者规则保留 `com.xcheng.xclogger.service.**` 和 `com.xcheng.xclogger.util.**`，避免 AIDL 接口和 Parcelable 类被混淆。

常用构建任务：

| 目标 | Gradle 任务 |
|------|-------------|
| 构建 common Debug APK，并连带构建另外两个 Debug flavor | `./gradlew assembleCommonDebug` |
| 构建 common Release APK | `./gradlew assembleCommonRelease` |
| 构建 PineLabs Debug APK | `./gradlew assembleP1416TPinelabsDebug` |
| 构建 PineLabs Release APK | `./gradlew assembleP1416TPinelabsRelease` |
| 构建 R2351 Combo Release APK | `./gradlew assembleR2351ComboRelease` |
| 构建 API Release AAR | `./gradlew :xclogger-api:assembleRelease` |

> **发布约束**：`xclogger-api` 的 Gradle 构建只生成 AAR；将生成物复制到 `xclogger_aar/` 或其他交付目录是额外发布步骤，不由当前 Gradle 脚本自动完成。

---

## 十、管道流式传输 ZIP

`getLogZip()` 仅在 `WAIT_UPLOAD_RESULT` 状态可调用。服务从 `zip_files` 中选择第一份 ZIP，创建 `ParcelFileDescriptor` pipe，并由后台线程读取 ZIP、写入 pipe，以避免外部进程直接读取受 SELinux 约束的文件。外部读取并上传完成后，应调用 `reportUploadResult(true)`，由 XCLogger 删除 ZIP 并清理状态。

---

## 十一、源码模块间的 AIDL 兼容性

`app` 是 AIDL 服务端，`xclogger-api` 是外部应用编译时使用的接口库。两者接口当前通过源代码副本保持一致，而不是通过 `app` 依赖 `xclogger-api` 保证一致。

- `IXcLoggerService.aidl`、`IXcLoggerListener.aidl`、`XcLoggerConfig.aidl` 必须在两个模块同步修改。
- `XcLoggerConfig` 的字段数量、类型、`writeToParcel()` / Parcel 构造函数的读写顺序必须同步；否则外部 AAR 客户端与 APK 服务端将发生跨进程反序列化不兼容。
- AIDL 新增方法应只追加在接口尾部并同步发布 APK 与 AAR，避免旧客户端交易码错位。
- 发布 AAR 前，应使用 `:xclogger-api:assembleRelease` 从当前源码重建，并与目标 APK 使用同一份接口定义进行验证。

## 十二、多客户构建与 applicationId

`app` 的 `customer` flavor 决定 APK applicationId 和默认配置 XML。`common` 使用 `com.xcheng.xclogger` 与 `default_config.xml`；`p1416TPinelabs`、`r2351Combo` 从 `keystore.gradle` 读取 applicationId、签名并使用各自默认 XML。外部绑定和广播回调必须使用实际安装 flavor 的 applicationId。
