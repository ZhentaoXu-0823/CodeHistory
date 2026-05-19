# XcLogger 项目文档

## 项目概述

XcLogger 是一个 Android 日志记录应用，用于捕获系统日志并提供配置管理、服务控制与归档导出能力。项目采用模块化与服务化架构：由前台服务承载持续采集流程，结合广播恢复、AIDL 远程控制与文件压缩服务，实现日志工具在系统场景下的稳定运行。

相较于传统“界面直接拉起采集”的方式，当前实现将核心采集逻辑下沉到 Service 与控制层，具备更强的生命周期韧性与自动恢复能力。

## 功能需求

### 核心功能
- **日志记录系统**：捕获系统日志并存储到文件
- **配置管理**：从 XML 加载默认配置，支持数据库持久化
- **服务化流程控制**：前台服务启动/停止日志采集，支持重启恢复
- **广播控制能力**：支持开机恢复、应用升级恢复、ADB 广播命令控制
- **远程接口能力**：通过 AIDL 提供跨进程控制与状态回调
- **文件管理**：日志文件创建、轮转、历史记录、压缩归档
- **可选加密写入**：支持基于配置开关的 XOR 写入
- **用户界面**：简洁扁平化的配置和状态显示
- **开发者工具**：隐藏的调试功能

### 技术规格
- **日志存储路径（默认）**：`/storage/emulated/0/XcLogger`（可配置）
- **文件命名规则（当前）**：`mainlog_<6位索引>_yyyyMMdd_HHmmss_*.txt`
- **单文件大小限制**：4MB（可配置）
- **缓冲区大小**：XML 默认 4096 字节；数据库兜底默认值 1024 字节
- **历史记录文件**：`A_OperationHistory.txt`
- **压缩输出目录**：`/data/xclogger/mobilelog`
- **配置存储**：SharedPreferences
- **最低 Android 版本**：API 29 (Android 10)
- **目标 Android 版本**：API 33 (Android 13)

## 项目结构

```
XcLogger/
 app/
    src/main/
       aidl/com/xcheng/xclogger/
          service/
             IXcLoggerListener.aidl          # AIDL监听回调接口
             IXcLoggerService.aidl           # AIDL远程控制接口
          util/
             XcLoggerConfig.aidl             # 配置对象AIDL定义
       java/com/xcheng/xclogger/
          MainActivity.java                  # 主界面控制器
          filemanager/
             FileManager.java                # 文件管理模块
             FileCompressService.java        # 文件压缩服务
             XcXorEncryption.java            # XOR加密工具
          processctr/
             ConfigLoader.java               # 配置加载器
             DeveloperActivity.java          # 开发者调试页面
             LogServiceController.java       # 服务控制器
             ProcessController.java          # 流程控制器
          receiver/
             XcLoggerBroadcastReceiver.java  # 广播接收器
          recorder/
             LogBuffer.java                  # 日志缓冲区
             SystemLogCatcher.java           # 系统日志捕获器
          service/
             LogCaptureService.java          # 前台日志采集服务
             RemoteBindService.java          # AIDL绑定服务
          ui/
             XcLoggerConfigActivity.java     # 配置界面
          util/
             DatabaseMigration.java          # 数据迁移工具
             XcLoggerConfig.java             # 配置数据模型
             XcLoggerDatabase.java           # 数据库存储工具类
       res/
          drawable/                          # 图标资源
          layout/                            # 布局文件
          values/                            # 字符串/主题资源
          xml/
             default_config.xml              # 默认配置文件
       AndroidManifest.xml                   # 应用清单
    build.gradle                             # 应用构建配置
 build.gradle                                # 项目构建配置
 settings.gradle                             # 项目设置
 gradle.properties                           # Gradle属性
```

## 类和方法详细说明

### 1. MainActivity.java
**包路径**：`com.xcheng.xclogger`  
**作用**：主界面控制器，提供用户交互入口

**关键职责**：
- 初始化配置与数据库迁移
- 发起权限请求
- 通过 `LogServiceController` 控制日志服务启停
- 展示运行状态与当前路径
- 提供隐藏开发者入口

### 2. FileManager.java (filemanager包)
**包路径**：`com.xcheng.xclogger.filemanager`  
**作用**：文件管理模块，负责日志文件创建、写入、轮转、路径更新与历史记录

**关键职责**：
- 维护日志目录和历史目录
- 创建新日志文件（含全局索引命名）
- 追加写入并按策略轮转
- 记录操作历史
- 支持按开关启用 XOR 加密写入

### 3. ConfigLoader.java (processctr包)
**包路径**：`com.xcheng.xclogger.processctr`  
**作用**：配置加载器，从 XML 与数据库加载配置，并维护内存中的当前配置

**关键流程**：
- 优先读取数据库配置
- 数据库为空时解析 `res/xml/default_config.xml`
- 初始化后写回数据库
- 支持 `updateConfig` 热更新

### 4. DeveloperActivity.java (processctr包)
**包路径**：`com.xcheng.xclogger.processctr`  
**作用**：开发者调试页面，提供数据库信息检查能力

### 5. LogServiceController.java (processctr包)
**包路径**：`com.xcheng.xclogger.processctr`  
**作用**：日志服务控制器，统一封装前台服务启停/重启入口

**关键职责**：
- 启动日志前台服务
- 停止日志前台服务
- 记录服务控制来源（user/boot/broadcast/aidl）
- 同步保存运行状态

### 6. ProcessController.java (processctr包)
**包路径**：`com.xcheng.xclogger.processctr`  
**作用**：流程控制器，协调日志捕获、缓冲落盘与操作历史记录

**关键职责**：
- 读取并刷新最新配置
- 创建日志文件
- 启停 `SystemLogCatcher`
- 触发 `LogBuffer` 到 `FileManager` 的刷盘流程
- 记录操作历史

### 7. LogBuffer.java (recorder包)
**包路径**：`com.xcheng.xclogger.recorder`  
**作用**：日志缓冲区，管理内存中的字节数据并触发批量刷盘

### 8. SystemLogCatcher.java (recorder包)
**包路径**：`com.xcheng.xclogger.recorder`  
**作用**：系统日志捕获器，执行 logcat 命令并处理输出

**关键能力**：
- 构建 logcat 命令
- 支持 tag/level/package 过滤（package 最终映射到 UID）
- 并发读取标准输出与错误输出
- 监控子进程状态并记录异常退出

### 9. LogCaptureService.java (service包)
**包路径**：`com.xcheng.xclogger.service`  
**作用**：前台日志采集服务，承载长时运行采集

**关键职责**：
- `onCreate` 即进入前台，降低启动超时风险
- `onStartCommand` 调用 `ProcessController.startLogging(...)`
- `onDestroy` 调用 `ProcessController.stopLogging(...)`

### 10. RemoteBindService.java (service包)
**包路径**：`com.xcheng.xclogger.service`  
**作用**：AIDL 绑定服务，对外提供远程控制能力

**对外能力**：
- 启停日志
- 查询运行状态
- 获取/更新配置
- 触发压缩
- 监听状态变化和压缩结果

### 11. XcLoggerBroadcastReceiver.java (receiver包)
**包路径**：`com.xcheng.xclogger.receiver`  
**作用**：统一广播入口，负责系统事件与自定义控制命令分发

**监听事件**：
- `BOOT_COMPLETED`
- `PACKAGE_REPLACED` / `MY_PACKAGE_REPLACED`
- `com.xcheng.xclogger.ADB_CMD`
- `com.xcheng.xclogger.FILE_COMPRESS`

### 12. FileCompressService.java (filemanager包)
**包路径**：`com.xcheng.xclogger.filemanager`  
**作用**：日志压缩归档服务

**关键职责**：
- 按日期收集日志并打包 zip
- 输出到 `/data/xclogger/mobilelog`
- 拷贝操作历史文件快照
- 发送成功/失败广播
- 必要时暂停并恢复日志采集

### 13. XcLoggerConfigActivity.java (ui包)
**包路径**：`com.xcheng.xclogger.ui`  
**作用**：配置界面，提供参数编辑与保存

### 14. XcLoggerConfig.java (util包)
**包路径**：`com.xcheng.xclogger.util`  
**作用**：配置数据模型，存储日志容量、缓冲区、目录与过滤规则

### 15. XcLoggerDatabase.java (util包)
**包路径**：`com.xcheng.xclogger.util`  
**作用**：数据库工具类，管理 SharedPreferences 持久化

**关键字段（部分）**：
- 配置项字段（容量、目录、过滤条件）
- `is_running`（运行状态）
- `file_index`（日志全局索引）
- `operation_history_path`（历史路径）
- `encryption_enabled`（加密开关）

## 模块职责分工

### recorder包 - 日志记录模块
- **LogBuffer**：内存缓冲区管理，优化 I/O 性能，支持自动刷新
- **SystemLogCatcher**：系统日志捕获，执行 logcat，异步读取输出

### filemanager包 - 文件管理与归档模块
- **FileManager**：文件创建、写入、轮转、历史记录与路径维护
- **FileCompressService**：日志压缩、归档输出、结果广播
- **XcXorEncryption**：可选加密能力

### processctr包 - 服务控制与流程编排模块
- **LogServiceController**：服务启停控制入口
- **ProcessController**：采集流程编排与组件协调
- **ConfigLoader**：配置加载、缓存与更新
- **DeveloperActivity**：调试辅助

### service包 - 系统服务模块
- **LogCaptureService**：前台采集服务
- **RemoteBindService**：AIDL 绑定与远程控制服务

### receiver包 - 广播分发模块
- **XcLoggerBroadcastReceiver**：系统事件与自定义命令处理

### ui包 - 用户界面模块
- **XcLoggerConfigActivity**：配置编辑与保存

### util包 - 工具模块
- **XcLoggerConfig**：配置数据模型
- **XcLoggerDatabase**：持久化存储封装
- **DatabaseMigration**：数据库迁移支持

## 数据流图

```
用户操作 -> MainActivity -> LogServiceController -> LogCaptureService
                                           |
                                           v
                                  ProcessController -> SystemLogCatcher
                                           |
                                           v
                                      LogBuffer -> FileManager -> 文件系统
                                           |
                                           v
                                      操作历史记录
```

## 配置流程

```
应用启动 -> ConfigLoader.load() -> XcLoggerDatabase.loadConfig()
                                  |
                                  +-- 数据库无配置 -> 解析 default_config.xml -> 保存到数据库
                                  |
                                  +-- 返回 currentConfig 缓存
```

## 运行与恢复流程

```
系统开机/应用升级广播 -> XcLoggerBroadcastReceiver
                     |
                     +-- 若 is_running=true -> 启动 LogCaptureService
                     |
                     +-- 启动 RemoteBindService 提供远程接口
```

## 远程控制流程（AIDL）

```
客户端进程 -> RemoteBindService(IXcLoggerService)
          |
          +-- start/stop -> LogServiceController
          +-- get/update config -> ConfigLoader/XcLoggerDatabase
          +-- triggerCompression -> FileCompressService
          +-- status/compress callback -> IXcLoggerListener
```

## 动态控制扩展硬约束

为保证广播与 AIDL 扩展的一致性、稳定性与可审计性，新增以下硬约束：

1. **全链路操作必须写入 History 文件**
   - 任一控制请求（Broadcast/AIDL）都必须记录到操作历史。
   - 至少包含：请求接收、来源反解析结果、白名单校验结果、执行开始、执行结束、执行后运行状态。
   - 禁止在多入口分散拼接记录；应通过统一控制层集中记录，避免遗漏。

2. **运行中修改日志配置必须先停止再开启**
   - 当日志采集处于运行状态时，`update_config` 不允许仅写数据库。
   - 必须执行 stop -> update -> start，保证新配置对运行态生效。
   - 若重启链路任一步失败，必须写入 History 并返回失败信息。

3. **来源（source）必须内部反解析，禁止外部传入**
   - 所有请求来源由框架内部从调用上下文反解析。
   - 禁止信任请求参数中的 source 文本，防止伪装。
   - 仅允许白名单来源发起控制请求：`adb`、`com.xcheng.xcloggertestdemo`。

4. **串行执行控制请求**
   - 日志工具内部对请求统一排队串行执行。
   - 一个命令完整结束后才可执行下一个命令。
   - 执行结果仍按原通道返回：广播请求走广播回执，AIDL 请求走 AIDL 回执。

5. **UI 一致性与体验约束**
   - 配置与状态更新不得主动拉起后台 Activity。
   - 仅更新数据源并由前台界面在生命周期回调时被动刷新。
   - 禁止为“强制刷新 UI”引入异常跳转行为。

## 开发规范

### 注释规范
- 类顶部：包含类的作用描述和关键方法简介
- 方法顶部：包含参数说明和返回值说明
- 注释应聚焦设计意图和关键约束，避免重复代码字面含义

### 编码规范
- 包名：`com.xcheng.xclogger`
- 类名：PascalCase
- 方法名：camelCase
- 常量：UPPER_SNAKE_CASE
- 属性：camelCase

### 异常处理
- 文件操作、进程操作使用 try-catch 包装
- 提供安全默认值
- 记录错误日志与关键操作历史

### 线程安全
- 使用 AtomicBoolean 保证状态一致性
- 单例获取使用 synchronized
- 后台任务使用 ExecutorService/工作线程

## 测试建议

### 功能测试
- 日志记录启停流程
- 前台服务存活与恢复
- 配置保存/加载/热更新
- 文件轮转与清理策略
- 广播命令控制（ADB_CMD、FILE_COMPRESS）
- AIDL 调用与回调流程

### 性能测试
- 内存使用与缓冲区效率
- 文件 I/O 与刷盘吞吐
- 长时运行稳定性
- 压缩耗时与恢复行为

### 兼容性测试
- Android API 29-33
- 不同设备存储环境
- 前台服务与通知行为
- 权限授予/拒绝场景

## 部署说明

### 权限要求
- `READ_EXTERNAL_STORAGE` - 读取外部存储
- `WRITE_EXTERNAL_STORAGE` - 写入外部存储
- `RECEIVE_BOOT_COMPLETED` - 接收开机广播
- `FOREGROUND_SERVICE` - 运行前台服务
- `WAKE_LOCK` - 保持关键任务执行
- `POST_NOTIFICATIONS` - Android 13+ 通知权限

### 组件说明
- 前台服务：`LogCaptureService`
- 绑定服务：`RemoteBindService`
- 广播接收器：`XcLoggerBroadcastReceiver`
- 压缩服务：`FileCompressService`

### 系统部署前提
- `AndroidManifest.xml` 中使用 `android:sharedUserId="android.uid.system"`
- 该配置通常要求系统签名或系统镜像部署环境支持

### 最低要求
- Android API 29+ (Android 10)
- 存储访问权限
- 前台服务通知能力

### 构建配置
- 编译 SDK：33
- 目标 SDK：33
- 最低 SDK：29
- Java 版本：1.8

## 与参考工程关系

- **mobile_log_d**：底层日志 daemon 参考工程，偏平台侧 C 实现，通常由上层应用控制。
- **MTKLogger**：传统日志工具 UI/控制参考工程。
- **XcLogger(app)**：当前自定义实现，采用 Android 应用层服务化方案，重点在稳定采集、远程控制与归档能力。

## 维护指南

### 日志文件管理
- 监控日志目录空间占用
- 定期验证轮转与清理策略
- 检查压缩输出目录权限
- 验证历史文件完整性

### 配置管理
- 备份关键配置
- 升级时检查默认值与数据库兜底值差异
- 关注 `file_index`、`is_running`、`operation_history_path` 等关键字段

### 性能优化
- 按设备能力调整缓冲区大小
- 评估轮转阈值与压缩时机
- 监控 logcat 子进程稳定性

### 故障排除
- 检查权限与通知设置
- 验证服务是否成功进入前台
- 查看操作历史与系统日志
- 使用开发者入口进行诊断

## 版本信息

- **文档版本**：2.0
- **版本代码**：1
- **构建工具**：Gradle 8.1.3
- **Android Gradle Plugin**：8.1.3
- **目标框架**：Android 13 (API 33)
