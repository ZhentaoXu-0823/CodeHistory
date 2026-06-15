# XcLogger 架构文档

> **最后更新**：2026-06-15  
> **项目版本**：v1.2.0  
> **最低 SDK**：API 29 (Android 10)  
> **目标 SDK**：API 33 (Android 13)  
> **系统要求**：`android:sharedUserId="android.uid.system"`

---

## 一、项目概述

XcLogger 是一个 Android 系统级日志采集与管理工具，运行在系统签名环境。通过执行 `logcat` 命令捕获系统日志，支持配置过滤、文件切片轮转、按时间/容量自动清理、按时间范围压缩打包，以及关键操作全链路审计记录。项目设计目标为 MDM（移动设备管理）场景提供可靠的后台日志基础设施。

### 核心能力

| 能力 | 说明 |
|------|------|
| 日志捕获 | 执行 `logcat -v threadtime,uid`，支持 Tag/Level/Package 三层过滤 |
| 文件管理 | 按大小轮转（默认 4MB），按周期清理（默认 168h），全局索引命名 |
| 配置管理 | XML 默认配置 → SharedPreferences 持久化，支持热更新 |
| 服务化 | 前台 Service 承载采集，BOOT_COMPLETED / 升级广播自动恢复 |
| 远程控制 | AIDL + 广播双通道，支持启停、配置更新、压缩触发 |
| 压缩归档 | 支持全量压缩和按时间范围压缩，输出到 `/data/xclogger/mobilelog` |
| 可选加密 | 基于 XOR 的文件级加密写入 |
| 操作审计 | 所有关键操作写入 `A_OperationHistory.txt` |

---

## 二、项目结构

```
XcLogger/
├── app/                                    # 主应用模块
│   └── src/main/
│       ├── aidl/com/xcheng/xclogger/
│       │   ├── service/
│       │   │   ├── IXcLoggerService.aidl       # 远程控制接口
│       │   │   └── IXcLoggerListener.aidl      # 状态回调接口
│       │   └── util/
│       │       └── XcLoggerConfig.aidl         # 配置对象 Parcelable 定义
│       ├── java/com/xcheng/xclogger/
│       │   ├── control/                        # 命令串行执行 & 安全控制
│       │   │   ├── CommandSerialExecutor.java  # 单线程串行命令执行器
│       │   │   ├── ControlRequest.java         # 控制请求数据模型
│       │   │   ├── ControlResult.java          # 控制结果数据模型
│       │   │   ├── PartialConfigMerger.java    # 配置部分合并器
│       │   │   ├── SourceResolver.java         # 来源反解析器
│       │   │   └── SourceWhitelistGuard.java   # 来源白名单守卫
│       │   ├── filemanager/                    # 文件创建/写入/轮转/压缩/加密
│       │   │   ├── FileManager.java            # 文件管理核心
│       │   │   ├── FileCompressService.java    # 压缩归档服务
│       │   │   └── XcXorEncryption.java        # XOR 加密/解密工具
│       │   ├── processctr/                     # 配置加载 & 服务控制 & 流程编排
│       │   │   ├── ConfigLoader.java           # 配置加载器（XML + DB + 版本推送）
│       │   │   ├── DeveloperActivity.java      # 隐藏开发者调试页
│       │   │   ├── LogServiceController.java   # 前台服务启停控制器
│       │   │   └── ProcessController.java      # 流程编排中心
│       │   ├── receiver/                       # 广播接收
│       │   │   ├── XcLoggerBroadcastReceiver.java  # 统一广播入口
│       │   │   └── PackageEventManager.java    # 包事件管理器（新增）
│       │   ├── recorder/                       # logcat 捕获 & 内存缓冲 & 过滤
│       │   │   ├── LogBuffer.java              # 内存缓冲区
│       │   │   └── SystemLogCatcher.java       # logcat 进程管理 + Tag/Level/UID 三层过滤
│       │   ├── service/                        # 前台采集 & AIDL 绑定
│       │   │   ├── LogCaptureService.java      # 前台日志采集 Service
│       │   │   └── RemoteBindService.java      # AIDL 远程绑定 Service
│       │   ├── ui/                             # 配置编辑界面
│       │   │   └── XcLoggerConfigActivity.java
│       │   ├── util/                           # 配置模型 & 数据库 & 迁移
│       │   │   ├── DatabaseMigration.java      # SharedPrefs 版本迁移
│       │   │   ├── XcLoggerConfig.java         # 配置数据模型 (Parcelable)
│       │   │   └── XcLoggerDatabase.java       # SharedPrefs 持久化封装
│       │   └── MainActivity.java               # 主界面控制器
│       ├── res/
│       │   └── xml/default_config.xml          # XML 默认配置
│       └── AndroidManifest.xml
│
├── xclogger-api/                  # 公共 API 库（供外部应用集成）
│   └── src/main/
│       ├── aidl/                  # AIDL 接口副本
│       └── java/                  # XcLoggerConfig Java 类
│
├── xclogger_aar/                  # 预编译 AAR 分发包
│   └── xclogger-api-release.aar
│
├── MTKLogger/                     # MTK 传统日志工具（参考实现）
├── mobile_log_d/                  # 底层 C 实现日志 daemon（参考实现）
│
├── ARCHITECTURE.md                # 本文档
├── INTEGRATION.md                 # 外部集成指南
└── xclogger_swt_case.md           # 测试用例
```

---

## 三、核心架构分层

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
│   (来源反解析)       (白名单校验)          (单线程串行排队)       │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    服务调度层 (processctr/)                       │
│  LogServiceController ──→ ConfigLoader ──→ ProcessController    │
│   (前台服务启停)          (配置加载/缓存/版本推送)  (组件协调编排)  │
└─────────────────────────────┬───────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         ▼                    ▼                    ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────────────┐
│  采集层       │   │  缓冲层       │   │  持久化层              │
│ SystemLog-   │   │  LogBuffer   │   │  FileManager         │
│ Catcher      │──▶│  (内存缓冲)   │──▶│  (文件创建/写入/轮转)   │
│ (logcat进程) │   │              │   │  XcXorEncryption     │
│  + 三层过滤  │   │              │   │  (可选XOR加密)         │
└──────────────┘   └──────────────┘   └──────────────────────┘
```

---

## 四、模块职责分工

### control 包 - 控制编排
| 类 | 职责 |
|----|------|
| `CommandSerialExecutor` | 单线程串行命令执行，保证操作有序性 |
| `ControlRequest` | 控制请求数据模型（含 opType、来源、时间范围参数） |
| `ControlResult` | 控制结果数据模型（含状态、压缩进度） |
| `PartialConfigMerger` | 部分更新配置合并（stop → update → start） |
| `SourceResolver` | 从调用上下文反解析请求来源，防伪装 |
| `SourceWhitelistGuard` | 来源白名单校验 |

### recorder 包 - 日志记录
| 类 | 职责 |
|----|------|
| `LogBuffer` | 内存缓冲区管理，优化 I/O 性能，支持自动刷新 |
| `SystemLogCatcher` | 系统日志捕获，执行 logcat，异步读取输出；Tag/Level/UID 三层过滤 |

### filemanager 包 - 文件管理
| 类 | 职责 |
|----|------|
| `FileManager` | 文件创建、写入、轮转、历史记录与路径维护 |
| `FileCompressService` | 日志压缩、归档输出、结果广播；支持按时间范围筛选文件 |
| `XcXorEncryption` | 可选 XOR 加密能力 |

### processctr 包 - 服务控制与流程编排
| 类 | 职责 |
|----|------|
| `LogServiceController` | 服务启停控制入口 |
| `ProcessController` | 采集流程编排与组件协调 |
| `ConfigLoader` | 配置加载、缓存、更新、版本推送（APK_CONFIG_VERSION） |
| `DeveloperActivity` | 调试辅助 |

### service 包 - 系统服务
| 类 | 职责 |
|----|------|
| `LogCaptureService` | 前台采集服务，onCreate 即进入前台 |
| `RemoteBindService` | AIDL 绑定与远程控制服务；支持 `triggerCompressionWithRange()` |

### receiver 包 - 广播分发
| 类 | 职责 |
|----|------|
| `XcLoggerBroadcastReceiver` | 系统事件与自定义命令分发 |
| `PackageEventManager` | 包事件处理（安装/卸载 UID 刷新）+ 定时前缀扫描 |

### ui 包 - 用户界面
| 类 | 职责 |
|----|------|
| `XcLoggerConfigActivity` | 配置编辑与保存 |

### util 包 - 工具
| 类 | 职责 |
|----|------|
| `XcLoggerConfig` | 配置数据模型（Parcelable） |
| `XcLoggerDatabase` | SharedPreferences 持久化封装 |
| `DatabaseMigration` | SharedPrefs 字段结构版本迁移 |

---

## 五、关键数据流

### 5.1 日志采集流

```
用户操作 / 开机广播 / AIDL
  → LogServiceController
    → LogCaptureService (前台 Service)
      → ProcessController.startLogging()
        → ConfigLoader.load() (配置加载)
        → fileManager.createNewMainLogFile()
        → SystemLogCatcher.startCapture(config)
          ├── parseAndUpdateFilterConfig(config)
          │    ├── filterTag     → filterTags[]
          │    ├── filterLevel   → filterLevel（大写转递 logcat）
          │    └── filterPackage → 精确包名: getApplicationInfo() 逐个 IPC
          │                      → 前缀(以"."结尾): getInstalledApplications() 全量匹配
          │                      → 存入 filterUidSet (ConcurrentHashMap.newKeySet())
          ├── buildLogcatCommand(config) → "logcat -v threadtime,uid *:S TagA:E"
          ├── Runtime.getRuntime().exec("sh", "-c", cmd) 启动 logcat 进程
          └── readLogcatOutput 线程启动
                ├── reader.readLine()
                ├── [每 10 秒] PackageEventManager.refreshPrefixUids() ← 发现新安装包
                ├── parseLogLine() → uid/level/tag
                ├── matchesTagFilter()   ← Tag 已在 *:S 层过滤，无 UID 过滤时直接放行
                ├── matchesLevelFilter() ← 优先级阈值判断 (f>e>w>i>d>v)
                ├── matchesUidFilter()   ← ConcurrentHashMap.contains(uid) O(1)
                └── 三条件 AND 通过 → LogBuffer → FileManager 落盘
```

### 5.2 配置加载流程

```
应用启动 / 服务启动
  → ConfigLoader.load()
    ├── [版本检查] db.getDatabaseVersion() < APK_CONFIG_VERSION ?
    │     ├── 是 → loadFromXml() → saveInitialConfig() → setDatabaseVersion()
    │     │       记录 "Config upgraded from vX to vY (buffer_size: a -> b, ...)"
    │     └── 否 → 走正常 DB 路径
    │
    ├── 数据库有配置？→ loadFromDatabase() → 返回当前配置
    └── 数据库无配置？→ loadFromXml() → saveInitialConfig() → 返回默认配置
```

### 5.3 远程控制流（AIDL）

```
客户端进程绑定 RemoteBindService
  → IXcLoggerService.Stub 代理
    ├── startLogging() / stopLogging() → submitAndWait → CommandSerialExecutor
    ├── getConfiguration() → ConfigLoader.current()（直接读缓存）
    ├── updateConfigurationPartial(config) → submitAndWait → CommandSerialExecutor
    │     → stop → ConfigLoader.updateConfig() → start
    ├── triggerCompression() / triggerCompressionWithRange()
    │   → submitAndWait → CommandSerialExecutor
    │   → FileCompressService 启动 → 异步压缩
    ├── reportUploadResult(success) → FileCompressService.upload()
    ├── getCompressStatus() → FileCompressService.query()
    ├── cancelCompressTask() → FileCompressService.cancel()
    ├── registerListener / unregisterListener → RemoteCallbackList
    └── 异步回调 → IXcLoggerListener
          ├── onStatusChanged(int)     ← 运行状态变化
          ├── onOperationResult(...)   ← 每次操作结果
          ├── onCompressFinished(...)  ← 压缩完成
          └── onCompressReady(...)     ← 压缩包待上传
```

### 5.4 包事件处理流

```
新增安装 com.e2scorp.d300.nicepay
  │
  ├── [广播方式] PACKAGE_ADDED → XcLoggerBroadcastReceiver
  │     → PackageEventManager.handlePackageInstalled()
  │       → SystemLogCatcher.addPackageUid()
  │
  └── [轮询方式] readLogcatOutput 每 10 秒
        → PackageEventManager.refreshPrefixUids()
          → getInstalledApplications(0)
          → SystemLogCatcher.addPackageUidIfNew()

两种方式最终都走到 filterUidSet.add(uid)，即时生效。
```

---

## 六、过滤系统

### 6.1 Tag 过滤（两层）

| 层 | 机制 | 位置 |
|----|------|------|
| logcat 命令层 | `*:S TagA:E TagB:E` → logcat 源头限流 | `buildTagFilterCommand()` |
| 应用层（兜底） | `matchesTagFilter(tag)` → 精确 equals | `SystemLogCatcher` |

当无 UID 过滤时，应用层 tag 检查跳过（因为 *:S 已过滤）。

### 6.2 Level 过滤（应用层）

优先级：`f(0) > e(1) > w(2) > i(3) > d(4) > v(5)`

`matchesLevelFilter(level)` 检查日志行 level 优先级 ≤ 配置 level 优先级（数字越小优先级越高）。

### 6.3 Package 过滤（应用层）

| 配置写法 | 语义 | 查询方式 |
|---------|------|---------|
| `com.example.app` | 精确匹配 | `PackageManager.getApplicationInfo()` |
| `com.example.`（以 "." 结尾） | 前缀匹配 | `PackageManager.getInstalledApplications()` → `startsWith` |

启动时全量扫描填充 `filterUidSet`（`ConcurrentHashMap.newKeySet()`），运行时 `filterUidSet.contains(uid)` O(1) 查表。

---

## 七、版本推送机制

### 7.1 配置推送

```
新增 `config_version` 键（与 DatabaseMigration 的 database_version 独立）
  → ConfigLoader 增加 APK_CONFIG_VERSION 常量
  → load() 时检测 db version < APK version → 全量用 XML 覆盖 DB
  → 用于 OTA 后统一推送新默认值
```

### 7.2 key 隔离

| key | 管理方 | 用途 |
|-----|--------|------|
| `database_version` | `DatabaseMigration` | SharedPreferences 字段结构迁移 |
| `config_version` | `ConfigLoader` | XML 默认配置推送版本 |

---

## 八、动态控制硬约束

1. **全链路操作必须写入 History 文件**
2. **运行中修改日志配置必须先停止再开启**（stop → update → start）
3. **来源（source）必须内部反解析，禁止外部传入**
4. **串行执行控制请求**（CommandSerialExecutor 单线程排队）
5. **UI 一致性与体验约束**（配置更新不主动拉起 Activity）

---

## 九、技术规格

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 日志存储路径 | `/storage/emulated/0/XcLogger` | 可配置 |
| 单文件大小 | 4 MB | 可配置，达到后自动轮转 |
| 缓冲区大小 | 2048 byte | 可配置 |
| 日志保留周期 | 168 小时（7 天） | 超期自动清理 |
| 总空间上限 | 4 GB | 按设备存储动态调整预留 3% |
| 压缩输出目录 | `/data/xclogger/mobilelog` | 仅保留 1 份 ZIP |
| 文件命名规则 | `mainlog_<6位索引>_yyyyMMdd_HHmmss_*.txt` | |

---

## 十、与参考工程关系

| 工程 | 定位 |
|------|------|
| `mobile_log_d` | 底层 C 实现日志 daemon，偏平台侧 |
| `MTKLogger` | 传统日志工具 UI/控制参考实现 |
| `XcLogger` | 当前自定义 Android 应用层服务化方案 |

---

## 十一、开发规范

- 类顶部：类作用描述 + 关键方法简介
- 方法顶部：参数说明 + 返回值说明
- 包名：`com.xcheng.xclogger`
- 类名：PascalCase / 方法名：camelCase / 常量：UPPER_SNAKE_CASE
- 文件/进程操作：try-catch 包装 + 安全默认值 + 操作历史记录
- 线程安全：`AtomicBoolean` + `synchronized` + `ExecutorService` + `ConcurrentHashMap`
