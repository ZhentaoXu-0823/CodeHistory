# XcLogger 项目深度分析

> **分析日期**：2026-06-01  
> **分析范围**：`app/`、`xclogger-api/`、`MTKLogger/`、`mobile_log_d/`  
> **项目版本**：v1.1.5

---

## 一、项目总览

XcLogger 是一个 **Android 系统级日志采集与管理工具**，运行在系统签名环境（`android:sharedUserId="android.uid.system"`），通过执行 `logcat` 命令捕获系统日志，支持配置过滤、文件切片轮转、按时间/容量自动清理、按天压缩打包，以及关键操作全链路审计记录。项目设计目标为 **MDM（移动设备管理）场景** 提供可靠的后台日志基础设施。

| 属性 | 值 |
| --- | --- |
| 包名 | `com.xcheng.xclogger` |
| 最低 SDK | API 29 (Android 10) |
| 目标 SDK | API 33 (Android 13) |
| 构建工具 | Gradle 8.1.3 / AGP 8.1.3 |
| Java 版本 | 1.8 |
| 系统要求 | `android:sharedUserId="android.uid.system"` |

---

## 二、项目目录结构

```
XcLogger/
├── app/                        # 主应用模块
│   └── src/main/
│       ├── aidl/               # AIDL 跨进程接口定义
│       │   └── com/xcheng/xclogger/
│       │       ├── service/
│       │       │   ├── IXcLoggerListener.aidl    # 状态回调接口
│       │       │   └── IXcLoggerService.aidl     # 远程控制接口
│       │       └── util/
│       │           └── XcLoggerConfig.aidl       # 配置对象 Parcelable 定义
│       ├── java/com/xcheng/xclogger/
│       │   ├── control/        # 命令串行执行 & 来源解析 & 白名单守卫
│       │   │   ├── CommandSerialExecutor.java    # 单线程串行命令执行器
│       │   │   ├── ControlRequest.java           # 控制请求数据模型
│       │   │   ├── ControlResult.java            # 控制结果数据模型
│       │   │   ├── PartialConfigMerger.java      # 配置部分合并器
│       │   │   ├── SourceResolver.java           # 来源反解析器
│       │   │   └── SourceWhitelistGuard.java     # 来源白名单守卫
│       │   ├── filemanager/    # 文件创建/写入/轮转/压缩/加密
│       │   │   ├── FileManager.java              # 文件管理核心
│       │   │   ├── FileCompressService.java      # 压缩归档服务
│       │   │   └── XcXorEncryption.java          # XOR 加密/解密工具
│       │   ├── processctr/     # 配置加载 & 服务控制 & 流程编排
│       │   │   ├── ConfigLoader.java             # 配置加载器（XML + DB）
│       │   │   ├── DeveloperActivity.java        # 隐藏开发者调试页
│       │   │   ├── LogServiceController.java     # 前台服务启停控制器
│       │   │   └── ProcessController.java        # 流程编排中心
│       │   ├── receiver/       # 广播接收器（统一入口）
│       │   │   └── XcLoggerBroadcastReceiver.java
│       │   ├── recorder/       # logcat 捕获 & 内存缓冲
│       │   │   ├── LogBuffer.java                # 内存缓冲区
│       │   │   └── SystemLogCatcher.java         # logcat 进程管理 + 过滤
│       │   ├── service/        # 前台采集服务 & AIDL 绑定服务
│       │   │   ├── LogCaptureService.java        # 前台日志采集 Service
│       │   │   └── RemoteBindService.java        # AIDL 远程绑定 Service
│       │   ├── ui/             # 配置编辑界面
│       │   │   └── XcLoggerConfigActivity.java
│       │   ├── util/           # 配置模型 & 数据库 & 迁移
│       │   │   ├── DatabaseMigration.java        # SharedPrefs 版本迁移
│       │   │   ├── XcLoggerConfig.java           # 配置数据模型 (Parcelable)
│       │   │   └── XcLoggerDatabase.java         # SharedPrefs 持久化封装
│       │   └── MainActivity.java                 # 主界面控制器
│       ├── res/
│       │   ├── xml/default_config.xml            # XML 默认配置
│       │   └── layout/                           # UI 布局文件
│       └── AndroidManifest.xml
│
├── xclogger-api/               # 公共 API 库（供外部应用集成）
│   └── src/main/
│       ├── aidl/               # AIDL 接口副本
│       └── java/               # XcLoggerConfig Java 类
│
├── MTKLogger/                  # MTK 传统日志工具（参考实现）
│   └── src/com/
│       ├── debug/loggerui/     # UI 层：Controller / File / Settings / TagLog
│       └── log/handler/        # Handler 层：Connection / Instance
│
├── mobile_log_d/               # 底层 C 实现日志 daemon（参考实现）
│   ├── mobilelog.c             # 主程序入口
│   ├── config.c/h              # 配置管理
│   ├── daemon.c/h              # 守护进程
│   ├── logging.c/h             # 日志核心
│   ├── size_control.c/h        # 文件大小控制
│   └── Android.bp              # AOSP 构建配置
│
└── xclogger_aar/               # 预编译 AAR 分发包
    ├── xclogger-api-release.aar
    └── USAGE.md
```

---

## 三、核心架构 & 数据流

### 3.1 架构分层图

```
┌─────────────────────────────────────────────────────────────────┐
│                        控制入口层                                │
│  MainActivity(UI)  │  XcLoggerBroadcastReceiver  │  AIDL Client │
└────────┬────────────────────┬──────────────────────┬────────────┘
         │                    │                      │
         ▼                    ▼                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                    控制编排层 (control/)                         │
│  SourceResolver → SourceWhitelistGuard → CommandSerialExecutor  │
│       (来源反解析)    (白名单校验)           (单线程串行排队)      │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    服务调度层 (processctr/)                       │
│  LogServiceController ──→ ConfigLoader ──→ ProcessController    │
│    (前台服务启停)           (配置加载/缓存)    (组件协调编排)       │
└─────────────────────────────┬───────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         ▼                    ▼                    ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────────────┐
│  采集层       │   │  缓冲层       │   │  持久化层              │
│ SystemLog-   │   │  LogBuffer   │   │  FileManager         │
│ Catcher      │──▶│  (内存缓冲)   │──▶│  (文件创建/写入/轮转)   │
│ (logcat进程) │   │              │   │  XcXorEncryption     │
│              │   │              │   │  (可选XOR加密)         │
└──────────────┘   └──────────────┘   └──────────┬───────────┘
                                                  │
                                                  ▼
                                         ┌──────────────────┐
                                         │  文件系统          │
                                         │  /sdcard/XcLogger │
                                         │  /data/xclogger/  │
                                         │    mobilelog/     │
                                         └──────────────────┘
```

### 3.2 完整数据流（以「启动采集」为例）

```
用户/系统触发
    │
    ▼
MainActivity / BroadcastReceiver / AIDL
    │
    ▼
CommandSerialExecutor  (串行排队，单线程执行)
    │
    ├── "start" ──→ LogServiceController.startLogService()
    │                   │
    │                   ▼
    │               LogCaptureService (前台 Service)
    │                   │ onCreate: startForeground()
    │                   │ onStartCommand: ProcessController.startLogging()
    │                   │
    │                   ▼
    │               ProcessController
    │                   │ ① load() 刷新配置缓存
    │                   │ ② FileManager.updatePaths()
    │                   │ ③ FileManager.createNewMainLogFile()
    │                   │ ④ SystemLogCatcher.startCapture(config)
    │                   │
    │                   ▼
    │               SystemLogCatcher
    │                   │ 构建 logcat -v threadtime,uid 命令
    │                   │ 启动子进程 sh -c "logcat ..."
    │                   │ 逐行读取 stdout
    │                   │ 二级过滤: Tag ∩ Level ∩ UID
    │                   │
    │                   ▼  (匹配的行)
    │               LogBuffer
    │                   │ 内存 byte[] 缓冲区
    │                   │ 满时自动触发 flush
    │                   │
    │                   ▼  (flush 回调)
    │               FileManager.appendToMainLog()
    │                   │ 按需轮转 (文件大小超限 → 创建新文件)
    │                   │ 可选 XOR 加密写入
    │                   │ 写入磁盘 file
    │                   │
    │                   ▼
    │               /storage/emulated/0/XcLogger/
    │               mainlog_000042_20260528_173000_0001.txt
    │
    ├── "stop" ──→ stopService() → onDestroy()
    │                   → logCatcher.stopCapture()
    │                   → logBuffer.flush()
    │                   → fileManager.resetCurrentLogFile()
    │
    ├── "trigger_compress" ──→ FileCompressService (独立线程)
    │                   → 按日期分组 → zip 打包 → 广播通知
    │                   → /data/xclogger/mobilelog/yyyy_MMdd_HHmmss_xxxx.zip
    │
    ├── "upload_result" ──→ FileCompressService.upload()
    │                   → 成功：删除 zip，状态归 IDLE
    │                   → 失败 < 3 次：等待外部重试
    │                   → 失败 ≥ 3 次：强制删除 zip
    │
    └── "update_config" ──→ stop → ConfigLoader.updateConfig() → start
                                │
                                ▼
                            XcLoggerDatabase (SharedPreferences)
```

---

## 四、模块详细分析

### 4.1 控制入口层

项目提供 **三种控制入口**，统一汇聚到 `CommandSerialExecutor`：

| 入口 | 实现 | 场景 |
| --- | --- | --- |
| **UI 按钮** | `MainActivity.toggleState()` | 用户手动启停 |
| **广播** | `XcLoggerBroadcastReceiver` | ADB 命令、开机自启、应用升级恢复 |
| **AIDL** | `RemoteBindService` | 外部 App 跨进程集成 |

#### 来源安全模型

```
SourceResolver (来源反解析)
├── 广播请求: Intent.getPackage() → 空则为 "adb"
├── AIDL 请求: Binder.getCallingUid() → PackageManager 反查包名
└── 禁止从请求参数中读取 source（防伪装）

SourceWhitelistGuard (白名单校验)
├── "adb"                        ✓
├── "com.xcheng.xcloggertestdemo" ✓
└── 其他来源                      ✗
```

### 4.2 控制编排层 (`control/`)

#### CommandSerialExecutor（命令串行执行器）

- 使用 `Executors.newSingleThreadExecutor()` 保证 **串行执行**
- 一个命令完整结束后才执行下一个
- 支持的 `op_type`：

| op_type | 说明 |
| --- | --- |
| `start` | 启动日志采集 |
| `stop` | 停止日志采集 |
| `restart` | 重启日志采集（stop → sleep 1s → start） |
| `update_config` | 部分更新配置（stop → 合并更新 → start） |
| `trigger_compress` | 触发压缩（异步，不阻塞队列） |
| `upload_result` | 回传上传成功/失败 |
| `query_compress_status` | 查询压缩/上传状态 |
| `cancel_compress` | 强制取消压缩 |
| `query_status` | 查询当前运行状态 |

每个操作执行前后都记录到操作历史，形成完整审计链：

```text
REQUEST_RECEIVED channel=xxx, op=xxx
SOURCE_RESOLVED source=xxx
SOURCE_ACCEPTED source=xxx
EXECUTE_START op=xxx
EXECUTE_END success=true/false, op=xxx
STATE_AFTER_EXECUTE running=true/false
```

#### PartialConfigMerger（配置部分合并器）

- **部分更新语义**：`patch` 中未设置（值为 0 / null）的字段保留当前值
- 仅 `patch` 中明确设置的字段才会覆盖

### 4.3 服务调度层 (`processctr/`)

#### ConfigLoader（配置加载器，单例）

```
ConfigLoader.load()
  │
  ├── 检查数据库状态
  │     ├── isConfigInitialized()? 或 hasSavedConfig()?
  │     │     └── YES → loadFromDatabase() → 返回
  │     └── NO → loadFromXml(default_config.xml)
  │                └── saveInitialConfig() 写入数据库 → 返回
  │
  └── 缓存到 ConfigLoader.currentConfig
```

#### LogServiceController（服务控制器）

关键设计：**状态先于服务**

```
startLogService():
  ① db.saveRunningState(true)      // 先写状态
  ② context.startForegroundService() // 再启动服务
  ③ recordOperationHistory()

stopLogService():
  ① db.saveRunningState(false)
  ② context.stopService()
  ③ recordOperationHistory()
```

状态先写的设计确保即使 Service 启动过程中崩溃，开机广播也能通过读取 `is_running` 正确恢复。

#### ProcessController（流程编排中心，单例）

构造时完成组件装配，形成流水线：

```java
// 缓冲区满 → 写入文件
logBuffer.setOnFlushListener((data, len) -> fileManager.appendToMainLog(data, len));

// logcat 输出行 → 追加到缓冲区
logCatcher.setOnLogLineListener((data, len) -> logBuffer.append(data, len));
```

### 4.4 采集层 (`recorder/`)

#### SystemLogCatcher（系统日志捕获器）

**logcat 命令构建：**

```
logcat -v threadtime,uid [-T 'MM-dd HH:mm:ss.SSS'] [*:S tag1:level tag2:level...]
```

**智能时间过滤：**

| 条件 | 行为 |
| --- | --- |
| 系统开机时间 < 2 分钟 | 不使用 `-T` 参数，捕获所有日志（避免丢失启动关键日志） |
| 系统开机时间 ≥ 2 分钟 | 使用 `-T` 参数，时间戳 = 当前时间 − 3 秒 |

**两级过滤策略：**

| 级别 | 位置 | 过滤项 | 方式 |
| --- | --- | --- | --- |
| **logcat 级** | 命令行参数 | Tag + Level | `*:S tag:level` 严格模式 |
| **应用级** | readLogcatOutput() | Tag ∩ Level ∩ UID | 逐行解析，三级 AND 过滤 |

**日志行解析格式（threadtime,uid）：**

```
MM-DD HH:MM:ss.mmm   UID   PID   TID   LEVEL   TAG: message
  0        1          2     3     4      5       6
```

- UID 过滤：启动时通过 `PackageManager.getApplicationInfo()` 将包名解析为 UID，缓存到 `HashSet<Integer>`，过滤时 O(1) 查找
- Level 过滤：f(0) > e(1) > w(2) > i(3) > d(4) > v(5)，数字越小优先级越高，`logLevel.priority ≤ filterLevel.priority` 才保留

**多线程架构：**

| 线程 | 职责 |
| --- | --- |
| stdout 读取线程 | 逐行读取 logcat 输出，执行应用层过滤，写入 LogBuffer |
| stderr 读取线程 | 读取 logcat 错误输出，记录到 logcat |
| 进程监控线程 | 每 5 秒检测子进程存活状态，异常退出时记录操作历史 |

#### LogBuffer（日志缓冲区）

- 可配置大小的内存 `byte[]` 缓冲区（默认 4096 字节）
- 单条数据超过缓冲区大小时直接绕过缓冲写入文件（避免 OOM）
- 满时自动触发 `flush()` 回调到 `FileManager.appendToMainLog()`

### 4.5 持久化层 (`filemanager/`)

#### FileManager（文件管理器）

**文件命名规则：**

```
mainlog_<6位全局索引>_yyyyMMdd_HHmmss_<4位日序号>.txt
    │         │              │        │          │
    │         │              │        │          └── 按天自增序号（跨天重置为 0001）
    │         │              │        └── 创建时分秒
    │         │              └── 创建日期
    │         └── 全局唯一递增索引（SharedPreferences K_FILE_INDEX）
    └── 固定前缀
```

**轮转策略：**

| 触发条件 | 行为 |
| --- | --- |
| 当前文件大小 > `file_size` 配置值 | 自动创建新文件 |
| 文件生存时间 < 10 秒但大小超限 | 仍强制轮转（防止小文件泛滥） |

**清理策略（每次创建新文件前触发）：**

| 清理类型 | 规则 | 范围 |
| --- | --- | --- |
| **容量清理** | 总大小 + 预计新文件 > `total_size` → 删除最旧文件 | `mainLogDir/*.txt` |
| **时间清理** | 文件 `lastModified` 距今 > `log_period` 小时 → 删除 | `mainLogDir/*.txt` |
| **Zip 清理** | zip 文件名日期距今 > `log_period` 小时 → 删除 | `/data/xclogger/mobilelog/*.zip` |

#### XcXorEncryption（XOR 加密工具）

**文件头格式（33 字节）：**

```
┌────────┬─────────┬──────────┬──────────┬──────────────┐
│ magic  │ version │  fileId  │  nonce   │ headerLength │
│ 4 bytes│ 1 byte  │ 8 bytes  │ 16 bytes │   4 bytes    │
│ "XCLX" │    1    │          │          │     33       │
└────────┴─────────┴──────────┴──────────┴──────────────┘
```

- `magic = 0x58434C58` ("XCLX")，用于检测文件是否已加密
- 使用 **XorShift64+** PRNG 生成 keystream
- 以 `fileId` 为种子，按文件偏移量进行 XOR（文件头部分不加密）
- 支持加密写入和解密导出

#### FileCompressService（压缩归档服务）

**状态机：**

```
IDLE ──→ COMPRESSING ──→ WAIT_UPLOAD_RESULT ──→ IDLE
  ↑          │                    │                │
  │          │ (cancel)           │ (upload ok)    │
  │          ▼                    │                │
  └── CANCELLING ◄────────────────┘                │
                │ (upload fail ≥ 3)                 │
                └───────────────────────────────────┘
```

**压缩流程：**

1. 如果采集正在运行，先 `rotateLogFileForCompress()` 封口当前文件
2. 扫描 `mainLogDir` 下所有 `mainlog_*.txt`，按文件名中的日期分组
3. 每个日期生成一个 zip：`yyyy_MMdd_HHmmss_<4位hex>.zip`
4. 已结束日期：`HHmmss` = `235959`；当天日期：使用当前时间
5. 拷贝最新 `A_OperationHistory.txt` 到压缩目录作为快照
6. 输出到 `/data/xclogger/mobilelog/`

**上传重试机制：**

- 最多重试 3 次
- 外部通过 `upload_result` 回传结果
- 成功：删除 zip，流程结束
- 失败 < 3 次：保持 `WAIT_UPLOAD_RESULT`，等待外部重试
- 失败 ≥ 3 次：强制删除 zip，状态归 IDLE

**强制重开（再次发送 trigger_compress）：**

| 当前状态 | 行为 |
| --- | --- |
| `IDLE` | 直接开始新压缩 |
| `WAIT_UPLOAD_RESULT` | 删除旧 zip，清理状态，开始新压缩 |
| `COMPRESSING` | 设置取消标记，线程退出后自动启动新压缩 |
| `CANCELLING` | 等待取消完成，自动启动新压缩 |

### 4.6 服务层 (`service/`)

#### LogCaptureService（前台日志采集服务）

```
onCreate()
  └── 第一时间 startForeground()  ← 防止 ANR 超时

onStartCommand()
  └── ProcessController.startLogging(source)
  └── 返回 START_STICKY  ← 被杀后自动重建

onDestroy()
  └── ProcessController.stopLogging("service_destroyed")
```

- 前台通知类型：`FOREGROUND_SERVICE_TYPE_DATA_SYNC`（Android 10+）
- 通知内容：包含应用名、采集状态文字、点击跳转 MainActivity

#### RemoteBindService（AIDL 绑定服务）

| AIDL 接口方法 | 对应 op_type | 说明 |
| --- | --- | --- |
| `startLogging()` | `start` | 启动采集 |
| `stopLogging()` | `stop` | 停止采集 |
| `isRunning()` | `query_status` | 查询运行状态 |
| `getConfiguration()` | — | 获取当前配置（直接读缓存） |
| `updateConfigurationPartial(config)` | `update_config` | 部分更新配置 |
| `triggerCompression()` | `trigger_compress` | 触发压缩 |
| `reportUploadResult(success)` | `upload_result` | 回传上传结果 |
| `getCompressStatus()` | `query_compress_status` | 查询压缩状态 |
| `cancelCompressTask()` | `cancel_compress` | 取消压缩 |
| `registerListener(listener)` | — | 注册回调 |
| `unregisterListener(listener)` | — | 注销回调 |

**回调机制：**

| 回调方法 | 触发时机 |
| --- | --- |
| `onStatusChanged(status)` | `is_running` 状态变化 |
| `onOperationResult(opType, success, message, runningState)` | 操作执行完成（非压缩） |
| `onCompressFinished(success, message)` | 压缩完成 |
| `onCompressReady(zipFiles, retryCount, maxRetryCount)` | 压缩成功，等待上传 |

内部也是调用 `CommandSerialExecutor`，与广播请求走**同一套串行执行队列**。

### 4.7 广播接收器 (`receiver/`)

#### XcLoggerBroadcastReceiver（统一广播入口）

| Action | 行为 |
| --- | --- |
| `BOOT_COMPLETED` | 开机自启：优先 `initial_auto_start_enabled`，否则读 `is_running` |
| `PACKAGE_REPLACED` | 应用升级后恢复 + 启动 RemoteBindService |
| `MY_PACKAGE_REPLACED` | 同上（直接匹配自身包名） |
| `com.xcheng.xclogger.ADB_CMD` | ADB 命令：`{cmd_name: start_xc_log / stop_xc_log}` |
| `com.xcheng.xclogger.CTRL_REQUEST` | **统一控制请求**（推荐的新接口，功能最全） |

**开机/升级恢复判断逻辑：**

```
如果上次加载是从 XML 初始化的？
  ├── YES → 使用 XML 中的 auto_start_enabled 字段
  └── NO  → 使用数据库中的 is_running 状态
```

### 4.8 UI 层

#### MainActivity（主界面）

- 显示运行状态（图标 + 文字）和当前日志路径
- 点击路径可编辑（仅停止状态下）
- **隐藏开发者入口**：快速点击标题 5 次 → 弹出密码框 → 输入 `0000` → 进入 DeveloperActivity
- 预加载机制：第 3 次点击时预先构造 Dialog，1 秒超时自动释放
- `onResume()` 中被动刷新状态（监听 SharedPreferences 变化）

#### XcLoggerConfigActivity（配置界面）

| 配置项 | 输入控件 | 默认值 |
| --- | --- | --- |
| 总容量 (GB) | EditText | 4 |
| 单文件大小 (MB) | EditText | 4 |
| 缓冲区大小 (Byte) | EditText | 4096 |
| 日志目录 | EditText | `/storage/emulated/0/XcLogger` |
| 保留时长 (Hour) | EditText | 168 |
| 过滤 Tag | EditText | `all` |
| 过滤 Level | EditText | `all` |
| 过滤 Package | EditText | `all` |

- 运行中禁止修改（所有输入框 disabled + 提示）
- 保存时验证 `filter_level` 有效性（f/e/w/i/d/v）
- 多值字段自动去空格、去空项、归一化

#### DeveloperActivity（开发者调试页）

| 按钮 | 功能 |
| --- | --- |
| Print Configuration | logcat 输出数据库配置信息 |
| Reset Database | 重置配置到 XML 默认值 |
| Toggle Encryption | 切换 XOR 加密开关（仅停止时可用） |
| Decrypt Latest File | 解密最新日志文件，输出 `_decrypted.txt` |

---

## 五、配置系统

### 5.1 配置字段完整说明

| 字段 | SharedPrefs Key | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| 总容量 | `total_size` | int (GB) | 4 | 日志目录总大小上限 |
| 单文件大小 | `file_size` | int (MB) | 4 | 触发轮转的阈值 |
| 缓冲区大小 | `buffer_size` | int (Byte) | 4096 | 内存缓冲区大小 |
| 日志目录 | `log_dir` | String | `/storage/emulated/0/XcLogger` | 日志文件存储路径 |
| 保留时长 | `log_period` | int (Hour) | 168 | 日志/zip 过期时间（7 天） |
| 过滤 Tag | `filter_tag` | String | `all` | 逗号分隔，**OR** 关系 |
| 过滤 Level | `filter_level` | String | `all` | 单值，**≥** 语义 |
| 过滤包名 | `filter_package` | String | `all` | 逗号分隔，**OR** 关系，内部转 UID |
| 加密开关 | `encryption_enabled` | boolean | false | 开启后新文件 XOR 加密写入 |
| 全局文件索引 | `file_index` | int | 1 | 日志文件名 6 位序号，每次创建新文件自增 |
| 运行状态 | `is_running` | boolean | false | 采集是否正在运行 |
| 首次启动标记 | `config_initialized` | boolean | false | 区分首次加载和后续加载 |
| 首次自启开关 | `initial_auto_start_enabled` | boolean | 来自 XML | 首次加载后是否自动开始采集 |

### 5.2 过滤规则详解

```
是否保存日志行 = matchesTagFilter(tag)
                AND matchesLevelFilter(level)
                AND matchesUidFilter(uid)
```

| 过滤器 | 匹配逻辑 |
| --- | --- |
| **Tag** | 日志行的 tag 在 `filter_tag` 列表中（逗号分隔，OR 关系） |
| **Level** | 日志行的 level 优先级 ≤ `filter_level` 的优先级（f=0, e=1, w=2, i=3, d=4, v=5，数字越小越高） |
| **Package/UID** | 日志行的 UID 在 `filter_package` 解析出的 UID Set 中（OR 关系） |

示例：`filter_level=i` 时，保留 f、e、w、i 级别的日志，丢弃 d、v 级别的日志。

### 5.3 配置加载优先级链

```
ConfigLoader.load()
  │
  ├── ① 检查数据库
  │     ├── isConfigInitialized() == true    → loadFromDatabase()
  │     └── hasSavedConfig() == true         → loadFromDatabase()
  │
  └── ② 数据库无配置 → loadFromXml(default_config.xml)
        └── saveInitialConfig() 写入数据库
        └── 写入操作历史："Config initialized from XML and saved to database"
```

---

## 六、关键设计决策

### 6.1 串行执行控制请求

所有控制操作（启动、停止、配置更新、压缩）通过 `CommandSerialExecutor` 的 `SingleThreadExecutor` 串行执行，避免竞态条件。压缩任务是异步的（独立线程），但通过 `AtomicBoolean` 保证同一时间只有一个压缩线程。

### 6.2 状态先于服务

`LogServiceController` 在启动 Service **之前** 先将 `is_running=true` 写入 SharedPreferences。这确保即使 Service 启动过程中崩溃，开机广播也能通过读取 `is_running` 正确判断并恢复。

### 6.3 来源反解析防伪装

`SourceResolver` 不从 Intent extra 中读取来源，而是通过底层反解析：
- 广播：`Intent.getPackage()`
- AIDL：`Binder.getCallingUid()` → `PackageManager.getPackagesForUid()`

白名单仅允许 `"adb"` 和 `"com.xcheng.xcloggertestdemo"` 两个来源，防止外部恶意伪造。

### 6.4 全链路操作审计

每个控制操作在 `A_OperationHistory.txt` 中留下完整记录：

```text
[2026-05-28 17:30:00] REQUEST_RECEIVED channel=broadcast, op=start
[2026-05-28 17:30:00] SOURCE_RESOLVED source=adb
[2026-05-28 17:30:00] SOURCE_ACCEPTED source=adb
[2026-05-28 17:30:00] EXECUTE_START op=start
[2026-05-28 17:30:01] Logging started successfully (source:broadcast:adb)
[2026-05-28 17:30:01] EXECUTE_END success=true, op=start
[2026-05-28 17:30:01] STATE_AFTER_EXECUTE running=true
```

压缩时自动备份操作历史到压缩目录。

### 6.5 运行中修改配置必须 stop → update → start

`CommandSerialExecutor.applyConfigUpdate()` 强制执行此序列，确保新配置对运行中的采集进程生效。否则仅写数据库但不会影响当前运行的 logcat 进程。

### 6.6 UI 一致性约束

- 配置与状态更新**不得主动拉起后台 Activity**
- 仅更新数据源（SharedPreferences），由前台界面在 `onResume()` 中被动刷新
- `MainActivity` 通过 `OnSharedPreferenceChangeListener` 监听 `is_running` 变化，实时更新 UI

### 6.7 极致启动优化

`LogCaptureService.onCreate()` 第一行即调用 `startForeground()`。Android 系统要求前台 Service 在 `onCreate()` 返回后 5 秒内调用 `startForeground()`，超时则 ANR。将调用放在 `onCreate()` 第一行确保任何初始化耗时都不会触发此限制。

---

## 七、外部集成指南

### 7.1 广播方式（推荐给外部 Demo / 自动化脚本）

**请求广播：**

```bash
# 启动采集
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type start

# 停止采集
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type stop

# 触发压缩
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type trigger_compress

# 查询状态
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_status

# 查询压缩状态
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_compress_status

# 回传上传结果
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type upload_result --ez success true

# 取消压缩
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type cancel_compress

# 更新配置（部分更新）
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type update_config \
  --ei file_size 8 \
  --ei log_period 720 \
  --es filter_tag "MyApp,AnotherApp"
```

**监听结果广播：**

注册监听 `com.xcheng.xclogger.CTRL_RESULT`，回执字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | boolean | 操作是否成功 |
| `message` | String | 结果或错误说明 |
| `op_type` | String | 对应请求操作类型 |
| `running_state` | boolean | 日志采集状态 |
| `compress_state` | String | 压缩/上传状态 |
| `zip_files` | String | 待上传 zip 路径（多个逗号分隔） |
| `retry_count` | int | 当前上传失败次数 |
| `max_retry_count` | int | 最大失败次数（固定 3） |

### 7.2 AIDL 方式（推荐给 App 内集成）

**绑定服务：**

```java
Intent intent = new Intent("com.xcheng.xclogger.REMOTE_BIND");
intent.setPackage("com.xcheng.xclogger");
bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
```

**获取接口：**

```java
IXcLoggerService service = IXcLoggerService.Stub.asInterface(binder);
service.startLogging();
service.stopLogging();
boolean running = service.isRunning();
XcLoggerConfig config = service.getConfiguration();
service.updateConfigurationPartial(partialConfig);
service.triggerCompression();
service.reportUploadResult(true);
```

**注册回调监听：**

```java
service.registerListener(new IXcLoggerListener.Stub() {
    @Override public void onStatusChanged(int status) { /* 0:stopped, 1:running */ }
    @Override public void onOperationResult(String opType, boolean success, String message, boolean runningState) { }
    @Override public void onCompressFinished(boolean success, String message) { }
    @Override public void onCompressReady(String zipFiles, int retryCount, int maxRetryCount) { }
});
```

### 7.3 推荐的外部 Demo 接入流程（压缩上传场景）

```
① 注册监听 com.xcheng.xclogger.CTRL_RESULT
② 发送 CTRL_REQUEST op_type=trigger_compress
③ 等待 CTRL_RESULT op_type=trigger_compress
   ├── success=true, compress_state=WAIT_UPLOAD_RESULT
   │     → 读取 zip_files, 执行上传
   │     → 上传完成后发送 upload_result
   └── success=false → 压缩失败, 查看 message
④ 收到 CTRL_RESULT op_type=upload_result
   ├── success=true → 上传成功, 流程结束
   ├── success=false, compress_state=WAIT_UPLOAD_RESULT, zip_files 非空
   │     → 重试上传同一个 zip
   └── success=false, compress_state=IDLE
         → 失败 3 次, zip 已强制删除, 流程结束
⑤ 如需强制重开压缩，直接再次发送 trigger_compress（不需先 cancel）
```

---

## 八、文件路径约定

| 用途 | 路径 | 说明 |
| --- | --- | --- |
| 日志文件 | `/storage/emulated/0/XcLogger/` | 可配置 |
| 操作历史 | 与日志同目录下的 `A_OperationHistory.txt` | 不可配置，随日志目录 |
| 压缩输出 | `/data/xclogger/mobilelog/` | 固定路径 |
| 历史备份 | `A_OperationHistory_<timestamp>.txt` | 压缩时拷贝到压缩目录 |

---

## 九、权限与组件声明

### 权限

| 权限 | 用途 |
| --- | --- |
| `READ_EXTERNAL_STORAGE` | 读取外部存储 |
| `WRITE_EXTERNAL_STORAGE` | 写入外部存储 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 |
| `FOREGROUND_SERVICE` | 运行前台服务 |
| `WAKE_LOCK` | 保持后台任务执行 |
| `POST_NOTIFICATIONS` | 前台通知（Android 13+） |

### 四大组件

| 组件 | 类型 | exported | 说明 |
| --- | --- | --- | --- |
| `MainActivity` | Activity | true | 主界面 + LAUNCHER |
| `XcLoggerConfigActivity` | Activity | false | 配置界面 |
| `DeveloperActivity` | Activity | false | 隐藏开发者页 |
| `LogCaptureService` | Service | false | 前台日志采集 |
| `RemoteBindService` | Service | true | AIDL 远程绑定 |
| `FileCompressService` | Service | false | 压缩归档 |
| `XcLoggerBroadcastReceiver` | Receiver | true | 统一广播入口 |

---

## 十、项目中的关联工程

| 目录 | 角色 | 语言 |
| --- | --- | --- |
| `MTKLogger/` | MTK 传统日志工具 UI/控制参考实现。提供 `AbstractLogController`、`TagLog`、`LogFileManager` 等抽象，支持多种 log 类型（Mobile/Modem/Network/BT/GPS/Connsys） | Java |
| `mobile_log_d/` | 底层日志 daemon 参考实现。包含 `mobilelog.c`（主程序）、`config.c`（配置）、`daemon.c`（守护）、`logging.c`（日志核心）、`size_control.c`（文件大小控制），通过 `Android.bp` 构建 | C |
| `xclogger-api/` | 公共 AIDL 接口库，供外部应用依赖以进行类型安全的跨进程通信。包含 `IXcLoggerService.aidl`、`IXcLoggerListener.aidl`、`XcLoggerConfig.java` | Java + AIDL |
| `xclogger_aar/` | 预编译 `.aar` 分发包，含使用说明 | AAR |

---

## 十一、构建与部署

### 构建配置

| 参数 | 值 |
| --- | --- |
| compileSdk | 33 |
| targetSdk | 33 |
| minSdk | 29 |
| Java 版本 | 1.8 |
| Gradle | 8.1.3 |
| AGP | 8.1.3 |

### 部署前提

- 设备需使用**系统签名**或部署在系统镜像中（`android:sharedUserId="android.uid.system"`）
- Android 10 (API 29) 及以上
- 需要授予存储读写权限

### 已构建产物

- `app/release/XCLogger_v1.1.5_202605281736.apk`

---

## 十二、类关系总图

```
                          MainActivity
                               │
                    ┌──────────┼──────────┐
                    │          │          │
                    ▼          ▼          ▼
          ConfigLoader   LogServiceController   XcLoggerConfigActivity
               │                │
               │                │
               ▼                ▼
        XcLoggerDatabase   LogCaptureService (Foreground Service)
        (SharedPrefs)            │
               │                 │
               │                 ▼
               │          ProcessController (单例)
               │            │         │
               │            ▼         ▼
               │     SystemLogCatcher  LogBuffer
               │            │              │
               │            │              ▼
               │            │         FileManager
               │            │            │
               │            │            ▼
               │            │      XcXorEncryption
               │            │
               │            ▼
               │      RemoteBindService (AIDL Service)
               │            │
               │            ▼
               │      CommandSerialExecutor (单线程)
               │            │
               │     ┌──────┴──────┐
               │     │             │
               │     ▼             ▼
               │  SourceResolver  SourceWhitelistGuard
               │
               ▼
        XcLoggerBroadcastReceiver
               │
               ▼
        FileCompressService (独立线程压缩)
```

---

> **文档版本**：1.0  
> **原始文档**：[PROJECT_DOCUMENTATION.md](PROJECT_DOCUMENTATION.md)、[xclogger_introduction_v1.1.0.md](xclogger_introduction_v1.1.0.md)、[USAGE_BROADCAST.md](USAGE_BROADCAST.md)
