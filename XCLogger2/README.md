# 项目名

> **文档维护规范**
>
> 本文件采用**倒序时间轴**格式维护：最新的内容在顶部，旧内容沉底。
>
> ```
> ----YYYY-MM-DD（更新）----
> 最新改动在最上面
> ----YYYY-MM-DD----
> 中间改动
> ----YYYY-MM-DD（更旧）----
> 最早改动在最下面
> ```
>
> 任何 AI 或人工续写本文件时，必须：
> 1. 先读此规范
> 2. 新条目插入到对应章节的顶部
> 3. 日期格式严格为 `YYYY-MM-DD`
> 4. 不删除旧记录

---

## 项目概述

XcLogger 是运行在 Android 系统签名环境的企业级日志采集与管理工具。通过 `logcat` 捕获系统日志，提供配置过滤（Tag / Level / 包名到 UID、PID 的动态映射）、文件轮转与清理、压缩归档（全量 / 时间范围）、AIDL 远程控制与广播控制双通道、管道流 ZIP 获取、操作审计等能力。支持多客户多签名多配置 APK 并行编译。

| 约束 | 值 |
|:--|:--|
| 最低 SDK | API 29 (Android 10) |
| 目标 SDK | API 33 (Android 13) |
| 系统要求 | `android:sharedUserId="android.uid.system"` + platform key 签名 |
| 接口 | AIDL（17 个方法，配置协议 API 4）+ 广播（12 个 op_type） |
| 编译 | productFlavors（common + p1416TPinelabs + r2351Combo）× buildTypes（debug + release） |

---

## 一、文件清单

### 构建与工程

| 文件 | 说明 |
|:--|:--|
| `settings.gradle` | 声明模块 `:app` 与 `:xclogger-api` |
| `build.gradle` | 根 build，声明 AGP 8.1.3 |
| `gradle.properties` | `android.useAndroidX=true`、`nonTransitiveRClass=true`、`-Xmx2048m -Dfile.encoding=UTF-8` |
| `keystore.gradle` | 外部签名配置：`p1416TPinelabs`、`r2351Combo` 和 `common` 三个 flavor 的 storePath / storePassword / keyAlias / keyPassword / applicationId |
| `gradle/wrapper/` | Gradle 8.5 wrapper |
| `keys/` | jks 签名密钥目录，对应 keystore.gradle 中的路径 |

### app 模块（APK 源码）

| 路径 | 说明 |
|:--|:--|
| `app/build.gradle` | application 模块（productFlavors × 3，buildTypes × 2，AIDL enabled） |
| `app/proguard-rules.pro` | release 混淆/资源压缩规则 |
| `app/src/main/AndroidManifest.xml` | 清单：system uid、前台服务、广播接收器、AIDL 服务 |
| `app/src/main/aidl/com/xcheng/xclogger/service/IXcLoggerService.aidl` | AIDL 服务接口（17 个方法；配置协议 API 4） |
| `app/src/main/aidl/com/xcheng/xclogger/service/IXcLoggerListener.aidl` | AIDL 回调监听接口（4 个回调方法） |
| `app/src/main/aidl/com/xcheng/xclogger/util/XcLoggerConfig.aidl` | AIDL Parcelable 声明 |

| 包 | 类 | 职责 |
|:--|:--|:--|
| `control` | `CommandSerialExecutor` | 单线程串行执行队列（FIFO）：start/stop/restart/配置/压缩/状态查询 |
| `control` | `PartialConfigMerger` / `ConfigUpdateApplier` | 兼容旧 patch；原子合并基础字段、Tag/Package 白名单、Package 黑名单及包过滤三态 |
| `control` | `SourceResolver` | 广播来源取 `Intent.getPackage()`，未指定为 `adb`；AIDL 通过调用 UID 取包名 |
| `control` | `SourceWhitelistGuard` | 已定义 `adb` 和 `com.xcheng.xcloggertestdemo` 判断，**当前未接入命令执行链路** |
| `recorder` | `SystemLogCatcher` | 启动/监控 logcat 子进程 + 逐行解析 Tag/Level/UID/PID + 包名到 UID/PID 的动态映射 + OFF/白名单/黑名单互斥过滤 + 崩溃标签强制保留 + 子进程异常退避重启 |
| `recorder` | `FilterPipeline` | 保留历史扩展过滤结构，当前采集循环不使用 Tag/Level/Content 黑名单或 Content 白名单 |
| `recorder` | `LogBuffer` | ConcurrentLinkedQueue 写缓冲 + FlushThread 攒批落盘 |
| `filemanager` | `FileManager` | 文件创建/写入/按大小轮转 + 按时间清理 + 按总空间清理（均在创建新文件前 on-the-fly 执行） |
| `filemanager` | `FileCompressService` | 异步压缩 + 将操作历史快照写为每个 ZIP 的最后一个 entry + 上传状态管理 + CTRL_RESULT 广播 + ZIP 清理 |
| `filemanager` | `XcXorEncryption` | XOR 加密工具类（可选） |
| `processctr` | `ConfigLoader` | XML 默认配置 → DB 持久化 → 热更新（stop→apply→start） |
| `processctr` | `LogServiceController` | 前台服务生命周期调度 + sServiceActive 竞态守卫 |
| `processctr` | `ProcessController` | 组装 SystemLogCatcher + LogBuffer + FileManager 并启停 |
| `service` | `LogCaptureService` | 前台 Service（STICKY），创建通知后立即 startForeground |
| `service` | `RemoteBindService` | AIDL 服务 + IXcLoggerListener 注册表 + CompressResultReceiver + getLogZip 管道输出 |
| `receiver` | `XcLoggerBroadcastReceiver` | BOOT_COMPLETED / MY_PACKAGE_REPLACED / CTRL_REQUEST / PACKAGE_ADDED / PACKAGE_REMOVED |
| `receiver` | `PackageEventManager` | 安装/卸载时维护包名到 UID/PID 的缓存；前缀匹配刷新依赖日志读取循环（约 10 秒） |
| `util` | `XcLoggerConfig` | Parcelable 配置模型：基础字段与历史兼容过滤字段；包过滤模式在服务端单独持久化 |
| `util` | `XcLoggerDatabase` | SharedPreferences 包装：持久化完整配置、包过滤三态及兼容字段，并管理运行/压缩状态 |
| `util` | `DatabaseMigration` | 数据库版本升级逻辑 |

| 资源路径 | 说明 |
|:--|:--|
| `app/src/main/res/xml/default_config.xml` | common flavor 出厂默认配置（1024MB / 7d / all） |
| `app/src/main/res/xml/p1416t_pinelabs_default_config.xml` | PineLabs 出厂默认配置（300MB / 96h / all） |
| `app/src/main/res/xml/r2351_combo_default_config.xml` | R2351 Combo 出厂默认配置 |
| `app/src/main/res/values/strings.xml` | 含 `flavor_config` 字符串资源（→ ConfigLoader 选 XML） |

### xclogger-api 模块（AAR 对外接口）

| 路径 | 说明 |
|:--|:--|
| `xclogger-api/build.gradle` | library 模块（com.android.library，namespace=com.xcheng.xclogger.api，仅依赖 `androidx.annotation:annotation:1.7.1`） |
| `xclogger-api/src/main/aidl/` | AIDL 接口副本（与 app 模块同包名/同方法签名，无 Gradle 依赖） |
| `xclogger-api/src/main/java/` | 配置 Parcelable、结构化更新/结果模型及单次使用的链式 `XcLoggerConfigUpdater` |
| `xclogger-api/proguard-rules.pro` | consumer-rules：保留 `com.xcheng.xclogger.service.**` 和 `com.xcheng.xclogger.util.**` |

### 分发产物 / 辅助

| 路径 | 说明 |
|:--|:--|
| `xclogger_aar/xclogger-api-release.aar` | 预编译 AAR 产物（非源码模块） |
| `PineLabs_20260714/` | PineLabs 客户交付包（APK + Demo + AAR + 文档） |
| `app/build/apk/` / `app/build/outputs/apk/` | 编译产物，Git 已排除 |
| `test_restart.bat` | logcat 进程重启测试脚本 |

### 文档

| 文件 | 说明 |
|:--|:--|
| `ARCHITECTURE.md` | 架构文档（源码范围、模块职责、数据流、构建架构、AIDL 兼容性） |
| `INTEGRATION.md` | 外部集成文档（集成总览、AAR 接入步骤、广播接入步骤） |
| `XCLogger_AAR_API_Reference.md` | AAR 接口参考（方法表 + 回调表 + 配置字段表 + 完整示例） |
| `XCLogger_AAR_API_Reference_en.md` | 同上英文版 |
| `XCLogger_Broadcast_Protocol.md` | 广播协议参考（12 个 op_type + 响应格式） |
| `XCLogger_Broadcast_Protocol_en.md` | 同上英文版 |
| `XCLogger_Product_Guide_v1.2.14.md` | 产品介绍文档 |
| `XCLogger_Product_Guide_v1.2.14_en.md` | 同上英文版 |

### 非源码目录（保留，不参与编译）

| 目录 | 说明 |
|:--|:--|
| `MTKLogger/` | MTK 日志应用源码，独立项目，与 XCLogger 无关 |
| `mobile_log_d/` | C 语言 logcat 守护进程源码，独立项目，与 XCLogger 无关 |

---

## 二、架构设计

### 2.1 编译架构：多客户多 APK 并行构建

**模块关系**：

`app`（com.android.application）与 `xclogger-api`（com.android.library）**无 Gradle project 依赖**。两者通过"维护同包名 + 同方法签名 AIDL 副本 + 同 Parcelable 字段顺序"保持跨进程序列化兼容。修改 AIDL 或 XcLoggerConfig 时必须同步更新两个模块。

```
keystore.gradle (ext.keystoreConfigs)
  ├── p1416TPinelabs {
  │     storePath: "C:/Users/.../KozenOSSign_P1416T_PINELABs.jks"
  │     storePassword: "<from protected local configuration>"
  │     keyAlias: "KozenOSSign"
  │     keyPassword: "<from protected local configuration>"
  │     applicationId: "com.ko.xclogger"             ← 覆盖默认 applicationId
  │   }
  │   → signingConfigs.p1416TPinelabs (from storePath)
  │   → productFlavors.p1416TPinelabs {
  │       applicationId = keystoreConfigs.p1416TPinelabs.applicationId
  │       resValue "flavor_config", "p1416t_pinelabs_default_config"
  │       signingConfig = signingConfigs.p1416TPinelabs
  │     }
  │
  ├── r2351Combo {
  │     applicationId: "com.ko.xclogger"
  │     resValue "flavor_config", "r2351_combo_default_config"
  │     signingConfig = signingConfigs.r2351Combo
  │   }
  │
  └── common {
        storePath: "" (留空 → 回退 signingConfigs.debug)
        applicationId: "com.xcheng.xclogger"
      }
      → productFlavors.common {
          resValue "flavor_config", "default_config"
          signingConfig = signingConfigs.debug
        }
```

**app/build.gradle 关键逻辑**：

```
① android { signingConfigs { keystoreConfigs.each { create(it) } } }
   → 遍历 keystoreConfigs → 动态创建 signingConfig

② android { productFlavors { ... } }
   → 遍历 keystoreConfigs → 动态创建 flavor（applicationId + resValue + signingConfig）

③ android { applicationVariants.all { variant →
     outputFileName = "XCLogger_v{versionName}_{yyyyMMddHHmm}.apk"
   } }
   → 统一输出命名（例如 XCLogger_v1.3.6_202607311244.apk）

④ afterEvaluate {
     tasks.register('assembleDebugUnitTest') { dependsOn 'assembleCommonDebugUnitTest' }
     tasks.register('assembleDebugAndroidTest') { dependsOn 'assembleCommonDebugAndroidTest' }
     tasks.named('assembleCommonDebug').configure {
       dependsOn 'assembleP1416TPinelabsDebug', 'assembleR2351ComboDebug'
     }
   }
   → 兼容 Android Studio Build APK(s) 菜单 → 一键编译所有 flavor
```

**ConfigLoader 运行时配置选择**：

```
ConfigLoader.load(context) → db = XcLoggerDatabase(context)
  → if (db.configExists()) return db.load()           // 优先 DB 持久化配置
  → xmlResId = R.xml.{R.string.flavor_config}         // common / p1416t_pinelabs / r2351_combo
  → parseXml(context, xmlResId) → XcLoggerConfig      // totalSizeMb=300/1024, logPeriodHours=96/168 ...
  → db.saveInitialConfig(config)                      // 持久化
  → return config

数据库升级检测：
  ConfigLoader.load() → getDatabaseVersion() < APK_CONFIG_VERSION
    → loadFromXml() + saveInitialConfig()              // 覆盖 DB 旧数据，用新 XML 初始化
```

**新增客户 checklist**：

1. 复制 `default_config.xml` → `<project>_<customer>_default_config.xml`
2. 在 `keystore.gradle → keystoreConfigs` 新增条目（applicationId + jks 信息）
3. 在 `app/build.gradle → productFlavors` 新增对应 flavor
4. jks 文件放入 keys/ 或配置绝对路径

**xclogger-api 模块**：

```
- com.android.library，namespace=com.xcheng.xclogger.api
- minSdk=29，无 productFlavor，仅 debug/release library variants
- 依赖：androidx.annotation:annotation:1.7.1
- consumer-rules：保留 com.xcheng.xclogger.service.** 和 com.xcheng.xclogger.util.**
  （防止 AIDL 接口和 Parcelable 被外部应用混淆后不可用）
```

**Gradle 任务**：

| 目标 | 命令 |
|:--|:--|
| 编译 common Debug + 连带 PineLabs Debug | `gradlew assembleCommonDebug` |
| 仅编译 PineLabs Debug | `gradlew assembleP1416TPinelabsDebug` |
| 编译 API AAR | `gradlew :xclogger-api:assembleRelease` |
| 编译 API Debug AAR | `gradlew :xclogger-api:assembleDebug` |

> AAR 生成后需手动复制至 `xclogger_aar/` 或交付目录——不由 Gradle 自动完成。

### 2.2 AIDL 兼容性约束

```
- IXcLoggerService.aidl / IXcLoggerListener.aidl / XcLoggerConfig.aidl
  必须在 app/ 和 xclogger-api/ 中同步修改

- XcLoggerConfig 字段数量/类型/Parcel 读写顺序必须一致
  → 否则外部 AAR 客户端与 APK 服务端跨进程反序列化不兼容

- AIDL 新增方法只应追加在接口尾部
  → 避免旧客户端交易码错位（AIDL 按声明顺序分配 transact code）

- 发布 AAR 前必须 :xclogger-api:assembleRelease 从当前源码重建
```

### 2.3 运行架构：六层模块

```
第一层——控制入口
  XcLoggerBroadcastReceiver.onReceive(context, intent)
    → switch(action): BOOT_COMPLETED / MY_PACKAGE_REPLACED / CTRL_REQUEST / PACKAGE_ADDED / PACKAGE_REMOVED
  RemoteBindService.AidlBinder (实现 IXcLoggerService, 13 个 AIDL 方法)
  MainActivity / XcLoggerConfigActivity (配置 UI)

第二层——命令编排（串行 + 安全）
  SourceResolver.resolve(intent)
    → 广播: Intent.getPackage() | AIDL: Binder.getCallingUid() → 取包名
  SourceWhitelistGuard.isAllowed(CallerInfo)
     → 已定义 adb 和 demo 判断，但当前未接入 CommandSerialExecutor 执行链路；不会拒绝请求
  CommandSerialExecutor → newSingleThreadExecutor()
    → request.op:
      "start" / "stop" / "restart" / "update_config" / "import_config"
      "trigger_compress" / "upload_result" / "cancel_compress"
      "query_status" / "query_compress_status"
      "query_files_dir" / "query_zip_dir"

第三层——服务调度（生命周期 + 配置）
  LogServiceController
    → startLogService(context, source): 内部 isRunning() 守卫防重复
    → stopLogService(context, source)
  LogCaptureService (前台 Service, START_STICKY)
    → onCreate(): createNotification() → startForeground()
    → onStartCommand(): ProcessController.startLogging(source)
    → onDestroy(): ProcessController.stopLogging("service_destroyed")
  ConfigLoader:
    → load() → DB 或 XML → XcLoggerConfig
    → hotUpdate(patch) → if(running) stop → apply → start
  ProcessController:
     → startLogging() → 更新 FileManager 路径 → 创建新日志文件 → SystemLogCatcher.startCapture()
     → stopLogging() → SystemLogCatcher.stopCapture() → FlushThread 刷盘 → 重置当前文件

第四层——日志采集（logcat + Tag/Level/包过滤链路）
  SystemLogCatcher:
    → startCapture(config)
      → buildTagFilterCommand() → logcat -v threadtime,uid *:S tag:V tag:V ...
      → logcatProcess = new ProcessBuilder(commandArgs).start()
      → readerThread.start()   // 主读取线程
      → monitorThread.start()  // 独立监控线程（每 5 秒 poll isAlive()）
    → readLogcatOutput():
      while((line = reader.readLine()) != null):
        parseLogLine(line) → tag, level, uid
        if (CRITICAL_TAGS.contains(tag)): buffer.append(line); continue
                                         // AndroidRuntime / DEBUG / libc 强制保留，不经过过滤
        if (!matchesUidFilter(uid)): continue
        if (!matchesTagFilter(tag)): continue
        if (!matchesLevelFilter(level)): continue
        buffer.append(line)              // 三条件 AND 通过 → LogBuffer
    → monitorProcess():
      while(running.get()):
        sleep(5000)
        if (!logcatProcess.isAlive() && !stoppedIntentionally):
          scheduleRestart()              // 10s → 20s → 40s → 80s 指数退避，最多 5 次

第五层——文件持久化（写入 + 轮转 + on-the-fly 清理）
  LogBuffer (ConcurrentLinkedQueue):
    → add(line) → queue.offer(line)
    → FlushThread: while(running): line = queue.poll(1s) → FileManager.write(line)
  FileManager:
    → write(line) → currentMainLogFile.append(line)
    → if(currentFileSize > fileSizeMb * 1024 * 1024):
        createNewMainLogFile()
          → generateFileName() → mainlog_{6位索引}_{yyyyMMdd}_{HHmmss}_{4位序号}.txt
          → checkAndCleanBeforeNewFile()
            → deleteOldestFileForSpace()        // 按总空间上限删
            → deleteFilesExceedingTimeLimit()   // 按保留周期删
            → deleteZipExceedingTimeLimit()     // 按 zip 保留周期删

第六层——压缩归档 + AIDL 回调链
  FileCompressService extends Service:
    compress(dayRange):
      sealCurrentFile()             // rotateCurrentLogFileForCompress → 封口当前文件
      selectTargetFiles(dayRange)
        // dayRange=null    → 当天所有文件
        // dayRange 有时间   → filterByTimeRangeV2() 筛选重叠文件
      ZipOutputStream → 先写全部日志 entry
      FileManager.snapshotOperationHistory() → 固定长度快照
      → 将 A_OperationHistory_yyyyMMddHHmmss.txt 写为 ZIP 最后一个 entry
      命名规则:
        未传时间范围: yyyy_MMdd_HHmmss_XXXX.zip (按日期分组，每个日期一个)
        传入时间范围: {effectiveStartTime}-{effectiveEndTime}.zip
      output: /data/xclogger/mobilelog/

    sendCtrl(context, "trigger_compress", true, "...", zipFiles, retry, STATE_WAIT_UPLOAD_RESULT)
      → Intent(ACTION_CTRL_RESULT)
        .putExtra("op_type", "trigger_compress")
        .putExtra("success", true)
        .putExtra("compress_state", "WAIT_UPLOAD_RESULT")
        .putExtra("zip_files", zipFiles)
        .putExtra("retry_count", 0)
        .putExtra("max_retry_count", 3)
      → sendBroadcast(intent)       // 系统广播

  RemoteBindService.CompressResultReceiver.onReceive(context, intent):
    if (opType != "trigger_compress"): return
    if (success && state == "WAIT_UPLOAD_RESULT"):
      notifyCompressReady(zipFiles, retryCount, maxRetryCount)
        → mListeners.beginBroadcast()
        → for i: mListeners.getBroadcastItem(i).onCompressReady(zipFiles, retry, max)
        → catch(RemoteException e) { Log.e(..., e) }
    else:
      notifyCompressFinished(success, message)
        → mListeners...onCompressFinished(success, message)

  上传闭环:
    外部调用 AIDL reportUploadResult(success) → FileCompressService.upload(context, ok)
      if (ok): delZip(z); db.clearCompressTaskState()         // 成功 → 删 ZIP + 清状态
      else: int n = db.getUploadFailCount() + 1
            if (n >= MAX_RETRY): delZip(z); db.clear()        // 超过重试次数 → 强制清
            else: db.setUploadFailCount(n)                    // 保持 WAIT_UPLOAD_RESULT

  getLogZip() 管道流（仅 AIDL，v1.2.13）:
    if (state != "WAIT_UPLOAD_RESULT"): throw RuntimeException
    ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe()
    new Thread:
      FileInputStream(zipPath) → write pipe[1].write(buf) → pipe[1].close()
    return pipe[0]     // 外部 AutoCloseInputStream 逐块读取
    外部读完 → reportUploadResult(true)   // 闭环
```

---

## 三、核心流程

### 3.1 日志采集启动流（伪代码）

```
① 触发来源:
  [AIDL] service.startLogging()
    → CommandSerialExecutor → case "start" →
      LogServiceController.startLogService(context, "aidl:" + source)
  [广播] CTRL_REQUEST:op_type=start
    → XcLoggerBroadcastReceiver.handleControlRequest()
    → CommandSerialExecutor → case "start" → (同上)
  [开机] BOOT_COMPLETED
    → XcLoggerBroadcastReceiver.handleBootCompleted()
    → resolveStartupState() → db.loadRunningState()
    → if (wasRunning): LogServiceController.startLogService(context, "boot")
  [覆盖升级] MY_PACKAGE_REPLACED
    → XcLoggerBroadcastReceiver.handleMyPackageReplaced()
    → resolveStartupState() → db.loadRunningState()
    → if (wasRunning): LogServiceController.startLogService(context, "upgrade")
      // 直接调用，不经过 CommandSerialExecutor（避免线程池异步时序混乱）

② LogServiceController.startLogService(context, source):
  if (!sServiceActive.compareAndSet(false, true)) return   // 全局守卫，防双路重复触发
  context.startForegroundService(createServiceIntent(source))

  ③ LogCaptureService.onCreate():
   createNotification() → startForeground(NOTIF_ID, notification)
   // 当前未调用 LogServiceController.setServiceActive(true)

④ LogCaptureService.onStartCommand(intent):
  source = intent.getStringExtra("source")
  ProcessController.getInstance(this).startLogging(source)

⑤ ProcessController.startLogging(source):
  ConfigLoader.load(context)           // DB 或 flavor XML → XcLoggerConfig
  new FileManager(context, config).startWriteThread()
  new SystemLogCatcher(context).startCapture(config)
    → buildTagFilterCommand()         // logcat -v threadtime,uid *:S tag:V tag:V ... AndroidRuntime:V DEBUG:V libc:V
    → logcatProcess = new ProcessBuilder(commandArgs).start()
    → readerThread.start()            // readLogcatOutput() 循环
    → monitorThread.start()           // monitorProcess() 循环
  running.set(true)

⑥ SystemLogCatcher.startCapture(config):
  // 解析过滤配置
  parseAndUpdateFilterConfig(config):
    if (filterTag != "all"):  buildTagWhitelist()
    if (filterLevel != "all"): buildLevelWhitelist()
     if (filterPackage != "all"):
       if (filterPackage.endsWith(".")): hasPrefixFilter = true; scan installed packages → UID set
       else: resolveExactUidSet(filterPackage)

  // 追加崩溃标签强制保留
  if (tagWhitelist != null):
    for (CRITICAL_TAGS = {AndroidRuntime, DEBUG, libc}): append to logcat filter spec as :V

   // 实际执行：ProcessBuilder(commandArgs).start()，每个参数独立传入
   // 命令示例:
  // 白名单模式: logcat -v threadtime,uid *:S MyTag:V AnotherTag:V AndroidRuntime:V DEBUG:V libc:V
  // 全通过模式: logcat -v threadtime,uid *:V AndroidRuntime:V DEBUG:V libc:V
```

### 3.2 日志读取 / 过滤 / 写入流（伪代码）

```
⑦ SystemLogCatcher.readLogcatOutput():

  parts = line.split("\\s+")
  // threadtime,uid: date time PID TID UID LEVEL TAG: message

  while(running.get()):
    line = reader.readLine()
    if (line == null): break             // logcat 进程退出（→ monitorProcess 检测到 → scheduleRestart）

    uid = parseUid(parts[2])
    pid = parseInt(parts[3])
    tid = parseInt(parts[4])
    level = parts[5]
    tag = parts[6].removeSuffix(":")

  // ① 崩溃标签强制保留（不经过当前过滤）
    if (CRITICAL_TAGS.contains(tag)):
      logBuffer.append(line); continue

  // ② 包过滤三态；UID 为主判据，PID 为解析失败/共享 UID 场景的后备判据
    if (!matchesPackageMode(uid, pid)): continue

    // ③ Tag 过滤
    if (tagWhitelist != null && !tagWhitelist.contains(tag)): continue
    // ④ Level 过滤（由 logcat 原生 filterspec 与 Java 侧阈值共同约束）
    if (levelWhitelist != null && !levelWhitelist.contains(level.lowercase())): continue

    // ⑤ 通过 → LogBuffer
    logBuffer.append(line)

  // ⑥ 前缀匹配 UID 刷新：当前在 readLogcatOutput() 中约每 10 秒检查一次
    if (System.currentTimeMillis() - lastPrefixRefresh > 10000):
      PackageEventManager.refreshPackagePids()
      lastPrefixRefresh = System.currentTimeMillis()

⑧ LogBuffer.FlushThread:
  while(running.get()):
    line = queue.poll(1, TimeUnit.SECONDS)
    if (line != null):
      fileManager.write(line)
    else:
      fileManager.flush()               // 1 秒无数据 → 强制刷盘

⑨ FileManager.write(line):
  currentMainLogFile.append(line)
  if (currentMainLogFile.size() > fileSizeMb * 1024 * 1024):
    createNewMainLogFile():
      generateFileName() → "mainlog_" + String.format("%06d", globalIndex) + "_" + yyyyMMdd + "_" + HHmmss + "_" + String.format("%04d", sequence) + ".txt"
      checkAndCleanBeforeNewFile():
         deleteOldestFileForSpace()       // 每次创建新文件前最多删除一个最旧文件
        deleteFilesExceedingTimeLimit()  // 遍历所有 log 文件，mtime 超过 logPeriodHours → 删除
        deleteZipExceedingTimeLimit()    // 同上，针对 zip 文件
```

> **当前过滤生效范围**：`SystemLogCatcher` 执行 Tag 白名单、logcat 原生 Level 阈值和 Package 三态过滤。Package 使用 UID 主判据与 PID 后备判据；`OFF` 全量放行，`WHITELIST` 使用包白名单，`BLACKLIST` 使用包黑名单。Tag 黑名单、Level 黑名单和 Content 白/黑名单不参与采集；其历史字段仅为数据与 Parcelable 兼容而保留。`AndroidRuntime`、`DEBUG`、`libc` 继续绕过全部过滤。

### 3.3 压缩 + 上传闭环流（伪代码）

```
⑩ FileCompressService.compress(dayRange):
  if (state != IDLE): return false
  state = COMPRESSING

  // 1. 封口当前文件
  FileManager.rotateCurrentLogFileForCompress()
    → 当前正在写入的文件不再写入新日志，后续日志自动创建新文件

  // 2. 筛选目标文件
  if (dayRange == null || dayRange.length == 0):
    files = 当日所有 mainlog_* 文件
  else:
    String.startTime = dayRange[0], String.endTime = dayRange[1]
    files = filterByTimeRangeV2(allMainLogFiles, startTime, endTime)
      → 时间参数移除非数字字符后按 yyyyMMddHHmmss 比较
      → 以相邻文件时间戳估算内容区间，保留重叠文件
      → 只传 startTime → 从该时间到最新
       → 只传 endTime → 可筛选从最早到该时间，但有结果时 ZIP 命名使用空 startTime，当前会触发异常
      → 均传 → 取覆盖区间的最小连续文件段

  // 3. 压缩
   // ZIP 名称由 startTime 参与构造；当前候选路径未做 canonical 目录边界校验
   new ZipOutputStream(zipPath)
  for (File f : files): zos.putNextEntry(f); write ...
  hist("Log entries compressed...")
  historySnapshot = FileManager.snapshotOperationHistory(tempFile)
  zos.putNextEntry("A_OperationHistory_yyyyMMddHHmmss.txt")  // ZIP 最后一个 entry
  write(historySnapshot)
  zos.close()

  // 4. 通知外部
  state = WAIT_UPLOAD_RESULT
  db.setCompressState(WAIT_UPLOAD_RESULT)
  sendCtrl(context, "trigger_compress", true, "compress success", zipFiles, 0, STATE_WAIT_UPLOAD_RESULT)
    → Intent(com.xcheng.xclogger.CTRL_RESULT)
    → sendBroadcast(intent)    // 送往 TARGET_PACKAGES 中所有已安装的包

⑪ RemoteBindService.CompressResultReceiver.onReceive(context, intent):
  if (!"trigger_compress".equals(intent.getStringExtra("op_type"))): return
  // 需要 XCLogger 自身包名在 TARGET_PACKAGES 中, 否则收不到广播

  success = intent.getBooleanExtra("success")
  state = intent.getStringExtra("compress_state")
  zipFiles = intent.getStringExtra("zip_files")
  retryCount = intent.getIntExtra("retry_count")
  maxRetry = intent.getIntExtra("max_retry_count")

  if (success && "WAIT_UPLOAD_RESULT".equals(state)):
    notifyCompressReady(zipFiles, retryCount, maxRetry)
      → mListeners.beginBroadcast()
      → for(i): mListeners.getBroadcastItem(i).onCompressReady(zipFiles, retry, max)
      → catch(RemoteException e) { Log.e(TAG, "callback failed", e) }
  else:
    notifyCompressFinished(success, message)
      → mListeners...onCompressFinished(success, message)

  外部在 onCompressReady 回调中:
    ① 调用 getLogZip() 流式拉取 ZIP → 写入 sdcard
    ② 上传到远端
    ③ 调用 reportUploadResult(true) 完成闭环

⑫ FileCompressService.upload(context, ok):
  if (state != WAIT_UPLOAD_RESULT || zipFiles.isEmpty()):
    return Result(false, "no pending compressed files")
  if (ok):
    delZip(zipFiles)               // new File(z).delete()
    db.clearCompressTaskState()    // state → IDLE, zip → ""
    return Result(true, "upload success")
  else:
    int n = db.getUploadFailCount() + 1
    if (n >= MAX_RETRY = 3):
      delZip(zipFiles)             // 超过重试次数 → 强制清
      db.clearCompressTaskState()
      return Result(false, "upload failed, max retry reached")
    db.setUploadFailCount(n)       // 保持 WAIT_UPLOAD_RESULT，等待下次重试

⑬ getLogZip() 管道流:
  // 前提: db.getCompressState() == "WAIT_UPLOAD_RESULT"
  ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe()
  new Thread(() → {
    FileInputStream fis = new FileInputStream(zipFiles.split(",")[0])
    FileOutputStream fos = new FileOutputStream(pipe[1].getFileDescriptor())
    byte[] buf = new byte[8192]; int len
    while ((len = fis.read(buf)) != -1) fos.write(buf, 0, len)
    fis.close(); fos.close()
  }).start()
  return pipe[0]    // 外部: new ParcelFileDescriptor.AutoCloseInputStream(pfd) → 读
```

### 3.4 覆盖升级自恢复流（伪代码）

```
⑭ XCLogger APK 被覆盖安装 (adb install -r / OTA 升级)：

  路径A——PMS 直接拉服务:
    context.startForegroundService(Intent(pkg, LogCaptureService.class))
    → LogCaptureService.onCreate()
       → 不调用 LogServiceController.setServiceActive(true)；该标志当前仅由 startLogService() 维护
    → LogCaptureService.onStartCommand(intent)
      → ProcessController.startLogging("direct")
        → SystemLogCatcher.startCapture() → 创建新文件  → 恢复采集

  路径B——MY_PACKAGE_REPLACED 广播（兜底）:
    XcLoggerBroadcastReceiver.onReceive(context, MY_PACKAGE_REPLACED intent)
      → handleMyPackageReplaced()
        → resolveStartupState() → db.loadRunningState() → true
        → LogServiceController.startLogService(context, "upgrade")
          // startLogService 入口的 isRunning() 守卫检测到路径A已启动 → 直接返回

  时序分析:
    PMS 调用 startForegroundService 与 MY_PACKAGE_REPLACED 广播几乎同时到达
    → 路径A 先执行 → isRunning() = true
    → 路径B 后执行 → isRunning() 守卫阻止重复启动
    → 结果: 不产生 2 个新日志文件（修复前会生成 000004 + 000005）
```

---

## 四、核心功能

### 4.1 功能总览

| 功能 | AIDL 入口 | 广播入口 | 数据流摘要 |
|:--|:--|:--|:--|
| 启动采集 | `IXcLoggerService.startLogging()` | `CTRL_REQUEST:op_type=start` | `→ CommandSerialExecutor{case "start"} → LogServiceController.startLogService() → startForegroundService() → ProcessController.startLogging()` |
| 停止采集 | `IXcLoggerService.stopLogging()` | `CTRL_REQUEST:op_type=stop` | `→ CommandSerialExecutor{case "stop"} → LogServiceController.stopLogService() → ProcessController.stopLogging()` |
| 重启采集 | — | `CTRL_REQUEST:op_type=restart` | `→ stop → start` |
| 查询状态 | `IXcLoggerService.isRunning()` | `CTRL_REQUEST:op_type=query_status` | `→ LogServiceController.isRunning() → running.get()` |
| 获取配置 | `IXcLoggerService.getConfiguration()` | `CTRL_REQUEST:op_type=query_status` | `→ ConfigLoader.current() → XcLoggerConfig (Parcelable, 13 fields)` |
| 更新配置 | `updateConfigurationPartial(cfg)` / `updateConfiguration2(XcLoggerConfig2, callback)` | `CTRL_REQUEST:op_type=update_config + extras` | 旧接口兼容 patch；`XcLoggerConfig2` 直接承载字段更新、名单 replace/add/remove 和包过滤模式，持久化成功后按需重启 |
| 导入配置 | — | `CTRL_REQUEST:op_type=import_config + xml_path` | `→ ConfigLoader.importFromXml(xmlPath) → if(running)stop → apply → start` |
| 全量压缩 | `IXcLoggerService.triggerCompression()` | `CTRL_REQUEST:op_type=trigger_compress` | `→ FileCompressService.compress(null) → sealCurrentFile → selectTodayFiles → ZIP → sendCtrl()` |
| 时间范围压缩 | `IXcLoggerService.triggerCompressionWithRange(st, et)` | `CTRL_REQUEST:op_type=trigger_compress + extras` | `→ triggerCompress(new String[]{st, et}) → filterByTimeRangeV2 → ZIP` |
| 查询压缩状态 | `IXcLoggerService.getCompressStatus()` | `CTRL_REQUEST:op_type=query_compress_status` | `→ db.getCompressState() → "state=WAIT_UPLOAD_RESULT;zip_files=/path/xxx.zip;retry_count=0;max_retry_count=3"` |
| 上报上传结果 | `IXcLoggerService.reportUploadResult(success)` | `CTRL_REQUEST:op_type=upload_result + ez success` | `→ FileCompressService.upload(ctx, ok) → ok?delZip+IDLE : retry++ (≥3→force delete)` |
| 取消压缩 | `IXcLoggerService.cancelCompressTask()` | `CTRL_REQUEST:op_type=cancel_compress` | `→ FileCompressService.cancel() → delZip + state=CANCELLING → IDLE` |
| 管道获取 ZIP | `IXcLoggerService.getLogZip()` | **无**（仅 AIDL） | `→ if(state≠WAIT_UPLOAD_RESULT)throw → createPipe → bg thread read zip → return pipe[0]` |
| 查询日志目录 | `getConfiguration().getLogDir()` | `CTRL_REQUEST:op_type=query_files_dir` | `→ resp.message = config.getLogDir() → sendBroadcast(CTRL_RESULT)` |
| 查询压缩目录 | 固定值 | `CTRL_REQUEST:op_type=query_zip_dir` | `→ resp.message = "/data/xclogger/mobilelog" → sendBroadcast(CTRL_RESULT)` |
| 注册监听 | `IXcLoggerService.registerListener(l)` | **无** | `→ RemoteBinder.mListeners.register(listener)` |
| 反注册监听 | `IXcLoggerService.unregisterListener(l)` | **无** | `→ RemoteBinder.mListeners.unregister(listener)` |

> `query_files_dir` 和 `query_zip_dir` 是**广播 op_type**，不是 AIDL 方法。`getLogZip()` 是**仅 AIDL 方法**，无广播版本。

### 4.2 压缩回调链路（关键注意事项）

```
TARGET_PACKAGES = {com.xcheng.mdm, com.xcheng.xcloggertestdemo, com.xcheng.xclogger, com.ko.xclogger}

压缩回调全链路:
  FileCompressService.sendCtrl()
    → sendBroadcast(Intent(ACTION_CTRL_RESULT))    // 显式→送 TARGET_PACKAGES
    → CompressResultReceiver.onReceive()           // 需 XCLogger 自身包名在其中
      → notifyCompressReady()/notifyCompressFinished()
        → mListeners → onCompressReady()/onCompressFinished()
          → 外部 IXcLoggerListener 回调

⚠ 回调生效必要条件:
  1. XCLogger 自身包名必须在 TARGET_PACKAGES 中（common=com.xcheng.xclogger, pinelabs=com.ko.xclogger）
  2. 外部在触发压缩前必须先调用 registerListener() 注册 IXcLoggerListener
```

---

## 五、权限模型

| 层级 | 机制 | 代码位置 |
|:--|:--|:--|
| **系统级签名** | `sharedUserId="android.uid.system"` + platform key 签名（每个 flavor 的 jks 由 `keystore.gradle` 配置） | `app/src/main/AndroidManifest.xml` |
| **CTRL_RESULT 广播过滤** | `XcLoggerBroadcastReceiver.TARGET_PACKAGES[]` = `{com.xcheng.mdm, com.xcheng.xcloggertestdemo, com.xcheng.xclogger, com.ko.xclogger}` | `receiver/XcLoggerBroadcastReceiver.java:22-27` |
| **来源反解析** | `SourceResolver.resolve(intent)` → 广播取 `Intent.getPackage()` / AIDL 取 `Binder.getCallingUid()` → 首个包名；**不信任外部传入的来源信息** | `control/SourceResolver.java` |
| **来源白名单守卫**（定义但未激活） | `SourceWhitelistGuard` 已定义 `adb` 和 `com.xcheng.xcloggertestdemo` 判断，但当前 **未接入 CommandSerialExecutor 执行链路**（代码中无调用） | `control/SourceWhitelistGuard.java` |
| **串行执行防并发** | `CommandSerialExecutor.execute(request)` → `newSingleThreadExecutor().execute()` → 严格 FIFO，防止配置/压缩状态并发读写 | `control/CommandSerialExecutor.java` |
| **覆盖升级竞态守卫** | `LogServiceController.sServiceActive`（AtomicBoolean CAS）用于 `startLogService()` 入口；`LogCaptureService.onCreate()` 当前未回写该标志 | `processctr/LogServiceController.java` / `service/LogCaptureService.java` |
| **AIDL 远程异常容错** | `notifyCompressReady()` / `notifyCompressFinished()` → `catch(RemoteException e)` → 只打日志，不中断广播循环 | `service/RemoteBindService.java` |
| **XOR 加密（可选）** | `XcXorEncryption` 提供 `encryptLogFile()` / `decryptLatestLogFile()`，按字节异或 key | `filemanager/XcXorEncryption.java` |

---

## 六、运维指南

### 6.1 启动 / 部署

```bash
# 编译所有 flavor Debug APK（common + PineLabs + R2351 Combo）
cd D:\GitHubRepositories\XCLogger2
gradlew.bat assembleCommonDebug

# 仅编译 PineLabs Debug APK
gradlew.bat assembleP1416TPinelabsDebug

# 仅编译 API AAR
gradlew.bat :xclogger-api:assembleRelease

# 安装 PineLabs APK（需 system 签名环境，若用 adb install 则需先卸载旧版）
adb install -r app\build\outputs\apk\p1416TPinelabs\release\XCLogger_v1.3.6_*.apk
adb shell am force-stop com.ko.xclogger   # 停止旧进程，避免新旧代码混合
```

### 6.2 常用 ADB 控制

```bash
# 启动
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type start

# 停止
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type stop

# 触发压缩
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type trigger_compress

# 查询日志目录
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_files_dir

# 查询压缩目录
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_zip_dir

# 查看操作历史
adb shell cat /storage/emulated/0/XcLogger/A_OperationHistory.txt
```

### 6.3 日志导出

```bash
# 导出设备日志（到 PC）
adb shell ls -l /storage/emulated/0/XcLogger/
adb pull /storage/emulated/0/XcLogger/ D:\temp\device_logs\

# 导出压缩 ZIP
adb shell ls -l /data/xclogger/mobilelog/
adb pull /data/xclogger/mobilelog/ D:\temp\device_zips\
```

---

## 七、变更记录

`----2026-07-31----`

**v1.3.6（当前版本）/ 配置协议 API 4**

- Package 过滤使用单个持久化三态：`OFF`、`WHITELIST`、`BLACKLIST`；默认 `OFF`，两套名单切换时保持不变。
- Package 匹配以日志 UID 为主判据、PID 为兼容后备判据；支持精确包名和以 `.` 结尾的前缀。
- AAR updater 统一使用 `packageFilterMode()`；移除未公开的 `usePackageWhitelist()` / `usePackageBlacklist()`。
- Tag 仅公开白名单 replace/add/remove；Tag 黑名单、Level 黑名单及 Content 白/黑名单不再由 UI、服务入口或 updater 更新。
- 历史过滤字段仍由数据库、XML 和 Parcelable 保留，避免升级及跨进程兼容问题。
- 每个 ZIP 先压缩日志，再对操作历史建立固定长度快照，并将历史写为最后一个 entry。
- 新增/纳入 `r2351Combo` flavor；XCLogger2 版本更新为 1.3.6（versionCode 12）。

`----2026-07-20----`

**v1.2.14**（历史版本）

- totalSizeGb → totalSizeMb 全局重命名（配置字段单位由 GB 改为 MB，default_config.xml `total_size` 值由 4 改为 1024）
- AIDL v3：新增 `getLogZip()` 管道流式传输（ParcelFileDescriptor pipe，解决 SELinux avc denial）
- 广播新增 `query_files_dir` / `query_zip_dir` op_type（查询日志存储路径和压缩输出目录）
- 多客户多签名多配置编译：productFlavors（common / p1416TPinelabs）+ keystore.gradle（applicationId + jks 加密信息）+ afterEvaluate 一键编译
- Pinelabs 客户定制：`p1416t_pinelabs_default_config.xml`（300MB / 96h），包名 `com.ko.xclogger`，jks `KozenOSSign_P1416T_PINELABs.jks`
- 覆盖升级自恢复修复：LogServiceController.startLogService() 入口 isRunning() 守卫 + handleMyPackageReplaced 直接调用 LogServiceController.startLogService()；sServiceActive 已定义但未接入 LogCaptureService.onCreate()（预留后续版本）
- 压缩回调修复：TARGET_PACKAGES 包含 XCLogger 自身包名（common=com.xcheng.xclogger, pinelabs=com.ko.xclogger）
- Demo PinelabsTestActivity：AIDL 全链路 + 压缩回调日志（`[AIDL-CALL]`/`[AIDL-RETURN]`/`[AIDL-CALLBACK]`）+ log() 输出到 logcat（tag: XcLoggerDemo）
- 过滤增强：buildTagFilterCommand() 追加崩溃标签（AndroidRuntime/DEBUG/libc:V），readLogcatOutput() 中崩溃标签绕过当前过滤；包过滤改为动态维护运行中 PID

`----2026-07-13----`

**v1.2.13** 初始发布

- Android 系统签名 + 前台 Service（START_STICKY）保活 + logcat 实时采集
- Tag / Level / 包名到运行中 PID 的动态过滤 + 崩溃标签强制保留
- 文件管理：4MB 文件分片 + 6 位全局索引命名 + 按时间 / 总空间 on-the-fly 清理
- 压缩归档：全量（按日期分组 yyyy_MMdd_HHmmss_XXXX.zip）+ 时间范围（{st}-{et}.zip）
- 上传重试：最多 3 次，超过自动清；成功后自动删 ZIP + 归零状态
- AIDL 远程控制（11 个方法）+ 广播远程控制（10 个 op_type）双通道
- PineLabs 客户定制：包名 com.ko.xclogger，300MB / 4 天
