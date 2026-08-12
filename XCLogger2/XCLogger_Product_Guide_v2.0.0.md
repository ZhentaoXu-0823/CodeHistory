# XCLogger 产品指南

> 版本：v2.0.1 / 配置协议 API 4
>
> 更新日期：2026-08-12

## 一、产品定位

XCLogger 是运行在 Android 系统签名环境中的日志采集与管理应用，面向设备运维、问题追踪和 MDM 集成。它持续采集 logcat，提供过滤、轮转、容量清理、压缩归档、上传状态闭环、AIDL 远程控制和操作审计。

本文描述当前代码实际提供的产品能力。AAR 的逐方法签名、参数和完整示例以 `XCLogger_AAR_API_Reference.md` 为准。

## 二、当前能力

| 能力 | 当前行为 |
|---|---|
| 日志采集 | `LogCaptureService` 为前台 Service；采集器启动后读取 `logcat -v threadtime,uid` |
| 真实运行态 | 只有 Service 已创建且 `ProcessController` 的采集器运行时，`isRunning()` 才返回 `true` |
| Tag 过滤 | 白名单；`all` 表示不限制；`AndroidRuntime`、`DEBUG`、`libc` 绕过过滤 |
| Level 过滤 | `f/e/w/i/d/v` 阈值；`v` 为默认无丢弃值；`all` 也被配置校验接受 |
| Package 过滤 | `OFF`、`WHITELIST`、`BLACKLIST` 三态互斥；按精确包名或以 `.` 结尾的前缀解析 UID/PID |
| 文件管理 | 按单文件大小轮转；创建新文件前按保留周期和总空间限制清理旧日志；可用空间 ≤ 1GB 或 ≤ 总存储 10% 时从最旧往新删除（排除当前文件）直至释放至少一个文件大小 |
| 首次容量策略 | 仅数据库无配置时执行；Android API 33/35 将主存储归一化为 8/16/32/64 GB：8/16 GB 配 512 MB 日志，32/64 GB 配 1024 MB；读取失败回退日志分区，再失败使用 256 MB；其他版本采用 flavor XML |
| 配置更新 | 兼容 partial patch 与 API 4 结构化原子更新；运行中有效变更会持久化后重启采集 |
| 压缩归档 | 无范围时按日期生成 ZIP；有任一时间边界时按文件内容时间区间筛选并生成单一 ZIP |
| 上传闭环 | 成功结果删除 ZIP；失败计数递增，第 3 次失败强制清理；取消会清理当前任务 |
| ZIP 管道 | `getLogZip()` 在 `WAIT_UPLOAD_RESULT` 状态通过 pipe 输出第一份 ZIP |
| 操作审计 | 启停、配置、压缩等控制流程写入操作历史；历史快照作为 ZIP 最后一个 entry |
| 多客户构建 | `common`、`p1416TPinelabs`、`r2351Combo` 三个 flavor，分别使用包名、签名和默认 XML |

## 三、过滤语义

### 3.1 Tag 与 Level

Tag 白名单和 Level 阈值同时参与过滤。Tag 名称只接受字母、数字、下划线、点和连字符，长度 1–64。白名单最多 64 项，空白名单归一化为 `all`。

旧配置中的 Tag 黑名单、Level 黑名单、Content 白名单和 Content 黑名单字段仍保留在 Parcelable/XML/数据库中，但当前采集链路不使用它们。

### 3.2 Package 三态

| 模式 | 行为 |
|---|---|
| `OFF` | 保留白名单和黑名单数据，但不进行 Package 过滤 |
| `WHITELIST` | 只保留白名单对应 UID/PID 的日志 |
| `BLACKLIST` | 排除黑名单对应 UID/PID 的日志 |

Package 名单支持精确包名和以 `.` 结尾的前缀。应用安装、卸载或替换事件会刷新包到 UID/PID 的映射。模式不存入旧 `XcLoggerConfig` Parcelable；AAR 客户端通过 `getPackageFilterMode()` 读取，并通过 API 4 updater 修改。

## 四、配置与存储

| 项目 | common | p1416TPinelabs | r2351Combo |
|---|---:|---:|---:|
| XML 总空间 | 1024 MB | 300 MB | 1024 MB |
| 单文件 | 4 MB | 4 MB | 4 MB |
| Buffer | 2048 B | 2048 B | 4096 B |
| 保留周期 | 168 h | 96 h | 96 h |
| Package 模式 | OFF | OFF | OFF |

上述总空间仅是 XML 初始值。API 33/35 设备首次持久化时会优先由主存储容量策略覆盖；数据库已有配置、覆盖升级或显式配置更新不会重新套用首次策略。

API 4 结构化配置规则：

- `totalSizeMb/fileSizeMb/logPeriodHours > 0`，`fileSizeMb <= totalSizeMb`。
- `bufferSizeBytes` 为 512–4096 且按 512 对齐。
- `totalSizeMb` 受门限约束：下限为首次约定值（8/16GB→512MB、32/64GB→1024MB、探测失败 256MB），上限为归一化档位 × 90%；AIDL/广播/UI/导入四通道统一校验，超范围仅拒绝该字段，其余字段正常生效。
- 名单支持 replace/add/remove；Package 黑名单还支持 clear，白名单支持 `all`。
- 请求在单线程 FIFO 中基于服务端最新配置合并，不依赖客户端旧快照。
- 配置落盘失败不替换内存配置；运行中重启失败会返回独立结果码。

## 五、压缩和上传

`triggerCompression()` 与 `triggerCompressionWithRange()` 只同步返回是否受理，最终结果由 Listener 或状态查询提供。

范围参数允许只传一边。源码会移除非数字字符后解析；无法解析或开始晚于结束时保留未过滤快照。仅传结束时间且存在结果时，当前 ZIP 命名路径可能因空开始时间失败，因此生产调用应同时传入有效、有序的 `yyyyMMddHHmmss` 边界。

压缩成功后：

1. 状态进入 `WAIT_UPLOAD_RESULT`。
2. Listener 收到 `onCompressReady()` 和 `onCompressFinished()`。
3. AAR 客户端调用 `getLogZip()` 读取第一份 ZIP。
4. 所有业务上传完成后调用 `reportUploadResult(true)`。
5. 上传失败调用 `false`；第 3 次失败会删除 ZIP 并回到 `IDLE`。

## 六、AAR 接入

当前 `IXcLoggerService` 有 16 个方法，另有 4 个 Listener 回调和 1 个配置结果回调。AAR 支持启停、真实状态查询、配置读取、兼容更新、API 4 原子更新、两种压缩请求、压缩状态、ZIP pipe、上传成功/失败、取消、Listener 生命周期及 API/Package 模式查询。

正式接入以 `XCLogger_AAR_API_Reference.md` 为唯一 API 参考；Demo 的 `xclogger_aar/USAGE.md` 给出 16 方法和 updater 全重载的验收映射。

## 七、平台与交付

| 项目 | 当前值 |
|---|---|
| 最低 SDK | API 23（Android 6.0） |
| 目标 SDK | API 33（Android 13） |
| XCLogger 版本 | 2.0.0（versionCode 15） |
| 配置协议 | API 4 |
| 默认 common 包名 | `com.xcheng.xclogger` |
| 默认日志路径 | `/storage/emulated/0/XcLogger` |
| ZIP 内部目录 | `/data/xclogger/mobilelog` |
| 上传失败上限 | 3 次 |

系统安装要求、签名、权限、构建和部署细节见 `README.md`、`ARCHITECTURE.md` 与 `INTEGRATION.md`。
