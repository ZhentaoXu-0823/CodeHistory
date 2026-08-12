# XcLogger 外部集成指南

> **最后更新**：2026-08-03
> **适用版本**：XCLogger v2.0.0 / 配置协议 API 4
> 本文档面向外部应用开发者，说明如何按当前 APK 与公共 API 源码实际提供的广播协议、AIDL 远程接口和 AAR 进行集成。

---

## 一、集成方式总览

XcLogger 提供两种外部控制方式：

| 方式 | 适用场景 | 特点 |
|------|----------|------|
| **广播协议** | ADB、自动化脚本、一次性控制 | 无需绑定；结果通过定向 `CTRL_RESULT` 广播发送 |
| **AIDL 绑定** | App 内集成、状态与压缩回调 | 双向通信；可注册 `IXcLoggerListener` |

> **包名提示**：示例使用 `com.xcheng.xclogger`。不同版本的 XCLogger 包名可能不同，例如 `com.ko.xclogger`；连接服务时应填写设备上实际安装的 XCLogger 包名。

> **回调提示**：压缩完成结果会转发给 AIDL 回调。当前支持 `com.xcheng.xclogger` 和 `com.ko.xclogger` 两个 XCLogger 包名；如果以后使用新的 XCLogger 包名，需要同时更新 APK 中的 `TARGET_PACKAGES`，否则收不到压缩回调。

---

## 二、广播控制协议

### 2.1 控制入口

- **请求 Action**：`com.xcheng.xclogger.CTRL_REQUEST`
- **结果 Action**：`com.xcheng.xclogger.CTRL_RESULT`

### 2.2 请求格式

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
    --es op_type <操作类型> \
    [额外参数]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `op_type` | String | 操作类型；缺失或空字符串时按 `query_status` 处理 |

### 2.3 支持的 op_type

| op_type | 说明 | 额外参数 |
|---------|------|----------|
| `start` | 启动日志采集 | — |
| `stop` | 停止日志采集 | — |
| `restart` | 重启日志采集 | — |
| `update_config` | 部分更新基础配置 | 见 2.4 |
| `import_config` | 从 XML 文件导入配置 | `config_file_path` |
| `query_status` | 查询日志采集状态 | — |
| `trigger_compress` | 触发异步压缩，可带时间范围 | `startTime` / `endTime` |
| `upload_result` | 回传上传结果 | `success` |
| `query_compress_status` | 查询压缩状态 | — |
| `cancel_compress` | 取消压缩/上传并清理 ZIP | — |
| `query_files_dir` | 查询当前日志目录 | — |
| `query_zip_dir` | 查询压缩输出目录 | — |

> **时间参数名称**：广播实现读取的是 camelCase 的 `startTime` 和 `endTime`，不是 `start_time`、`end_time`。当前实现会删除非数字字符后使用；非法值或开始时间晚于结束时间会回退为原始快照，且仅传 `endTime` 在有可压缩文件时可能因 ZIP 命名使用空 `startTime` 触发异常。

### 2.4 配置更新参数

`update_config` 会创建配置 patch。当前 `PartialConfigMerger` 合并 8 个基础字段及 Package 黑名单；整数值必须大于 0、字符串必须非空才会覆盖当前值。因此，不能通过 `update_config` 清空字符串配置、清空 Package 黑名单或设置 0。

| Extra 字段 | 类型 | 对应字段 | 说明 |
|-----------|------|----------|------|
| `total_size` | int | `totalSizeMb` | 总空间上限，按 MB 传入 |
| `file_size` | int | `fileSizeMb` | 单文件上限，按 MB 传入 |
| `buffer_size` | int | `bufferSizeBytes` | 缓冲区 byte；更新时向上对齐至 512 的倍数，最大 4096 |
| `log_dir` | String | `logDir` | 日志存储目录 |
| `log_period` | int | `logPeriodHours` | 保留周期，小时 |
| `filter_tag` | String | `filterTag` | Tag 过滤；`all` 为不过滤 |
| `filter_level` | String | `filterLevel` | Level 阈值；默认/查询值为 `v`，表示不按 Level 丢弃；兼容接受 `all` |
| `filter_package` | String | `filterPackage` | 包名过滤；`,` 分隔，`.` 结尾表示前缀匹配，`all` 为不过滤 |
| `filter_package_blacklist` | String | `filterPackageBlacklist` | Package 黑名单；非空值更新名单，实际是否生效由当前包过滤模式决定 |

`total_size` 一旦写入数据库即为后续启动和覆盖升级的权威值。仅在数据库完全没有配置时，API 33/35 会优先按主存储容量生成首次值，读取失败时回退日志目录所在分区；其他 Android 版本使用 flavor XML。容量归一化为 8/16/32/64 GB，其中 8/16 GB 对应 512 MB 日志配额，32/64 GB 对应 1024 MB；两种容量读取都失败时使用 256 MB。

v2.0.1 起，显式配置更新受到门限约束：`total_size` 合法范围为 `[首次约定值, 归一化档位 × 90%]`（8GB→6866MB、16GB→13732MB、32GB→27465MB、64GB→54931MB）。AIDL（`updateConfigurationPartial` / `updateConfiguration2`）、广播（`update_config` / `import_config`）和 UI 保存四通道统一校验；超范围时**仅拒绝 total_size 字段**（恢复为当前值），其余字段正常生效，拒绝记录到操作历史。仅 total_size 被拒时，`updateConfigurationPartial` / `updateConfiguration2` 返回失败结果。此外，新建日志文件前执行存储空间保底检查：可用空间 ≤ 1GB 或 ≤ 总存储 10% 时，从最旧往新删除日志文件（排除当前正在写的文件）直至释放至少一个文件大小；删无可删时记录操作历史一次（防抖），日志服务继续运行，空间释放后自动恢复。

过滤字段会在合并后统一校验。`filter_tag` 只允许精确小写 `all`，或由字母、数字、下划线、点、连字符组成的 Tag（单项 1～64 字符）；`filter_level` 只允许精确小写 `all` 或单个 `F/E/W/I/D/V` 字符（大小写均可）；Package 白/黑名单只允许 Java 风格包名，白名单还允许精确小写 `all`，末尾可带一个 `.` 表示前缀。Tag / Package 列表总长度最多 4096 字符、最多 64 项。分号、管道符、重定向符等 shell 特殊字符会被拒绝，结果中的 `success` 为 `false`。

APK 将 logcat 命令拆成参数列表交给 `ProcessBuilder`，不会通过 shell 拼接执行上述值。Package 过滤使用 UID 主判据和 PID 后备判据；进程启动、停止或包事件发生后，映射缓存由采集循环定期刷新。

广播接收器只接受 Package 黑名单，不接受 `filter_tag_blacklist`、`filter_level_blacklist`、`filter_content` 或 `filter_content_blacklist`；即使发送这些 Extra 也会被忽略。广播协议也不能切换包过滤模式：默认 `OFF` 时更新名单不会立即过滤，调用方必须通过 API 4 AAR/AIDL 将模式设为 `WHITELIST` 或 `BLACKLIST`。清空名单及模式切换应使用 AAR updater。

### 2.5 结果广播

结果 Action 为 `com.xcheng.xclogger.CTRL_RESULT`。当前 APK 逐个定向发送至以下包名：

- `com.xcheng.mdm`
- `com.xcheng.xcloggertestdemo`
- `com.xcheng.xclogger`
- `com.ko.xclogger`

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 操作是否成功 |
| `message` | String | 结果或错误说明；目录查询结果也在此字段 |
| `op_type` | String | 对应请求操作 |
| `running_state` | boolean | 日志采集当前状态 |
| `compress_state` | String | 压缩/上传状态 |
| `zip_files` | String | 待上传 ZIP 路径，多个时逗号分隔 |
| `retry_count` | int | 当前上传失败计数 |
| `max_retry_count` | int | 最大失败次数，当前为 3 |

`compress_state` 的取值为 `IDLE`、`COMPRESSING`、`WAIT_UPLOAD_RESULT`、`CANCELLING`。

### 2.6 常用示例

```bash
# 启动日志
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type start

# 查询日志状态
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_status

# 更新基础过滤配置
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type update_config \
  --es filter_tag "TagA,TagB" \
  --es filter_level "e"

# 按前缀过滤包名
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type update_config \
  --es filter_package "com.example."

# 触发全量压缩
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type trigger_compress

# 按时间范围压缩：使用实际读取的 camelCase Extra
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type trigger_compress \
  --es startTime "20260601135000" \
  --es endTime "20260602030000"

# 查询日志目录和 ZIP 目录
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_files_dir
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_zip_dir

# 取消压缩
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type cancel_compress

# 回传上传成功
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type upload_result --ez success true
```

### 2.7 旧版 ADB 命令

```bash
adb shell am broadcast -a com.xcheng.xclogger.ADB_CMD --es cmd_name start_xc_log
adb shell am broadcast -a com.xcheng.xclogger.ADB_CMD --es cmd_name stop_xc_log
```

---

## 三、AIDL 远程接口

### 3.1 绑定服务

服务 Action：

```
com.xcheng.xclogger.REMOTE_BIND
```

```java
Intent intent = new Intent("com.xcheng.xclogger.REMOTE_BIND");
intent.setPackage("com.xcheng.xclogger");
bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
```

### 3.2 AIDL 接口与广播操作对照

| AIDL 方法 | 对应广播 op_type | 说明 |
|-----------|------------------|------|
| `startLogging()` | `start` | 启动日志采集 |
| `stopLogging()` | `stop` | 停止日志采集 |
| `isRunning()` | `query_status` | 查询运行状态 |
| `getConfiguration()` | — | 返回 `ConfigLoader` 当前缓存；未加载时可能为 `null` |
| `updateConfigurationPartial(config)` | `update_config` | 部分更新基础配置；运行中执行 stop → update → start |
| `getApiVersion()` | — | 当前返回配置协议版本 `4` |
| `getPackageFilterMode()` | — | 返回 `off`、`whitelist` 或 `blacklist` |
| `updateConfiguration2(update, callback)` | — | 唯一的异步结构化更新入口；`XcLoggerConfig2` 直接承载基础字段、名单变更和可选的 `packageFilterMode` |
| `triggerCompression()` | `trigger_compress` | 不带时间范围的异步压缩 |
| `triggerCompressionWithRange(startTime, endTime)` | `trigger_compress` + `startTime` / `endTime` | 带时间范围的异步压缩 |
| `reportUploadResult(success)` | `upload_result` | 回传上传结果；传 `false` 时当前实现更新重试状态后仍返回 `false`，调用方需查询状态验收 |
| `getCompressStatus()` | `query_compress_status` | 返回分号分隔状态字符串 |
| `cancelCompressTask()` | `cancel_compress` | 取消并清理 ZIP |
| `getLogZip()` | — | 返回首个待上传 ZIP 的 `ParcelFileDescriptor` pipe |
| `registerListener(listener)` | — | 注册回调 |
| `unregisterListener(listener)` | — | 反注册回调 |

`query_files_dir` 与 `query_zip_dir` 仅是广播 `op_type`；它们不属于 `IXcLoggerService` AIDL 接口。

`RemoteBindService` 允许其他 App 连接，目前没有绑定权限检查。`SourceResolver` 只记录是谁发起调用，`SourceWhitelistGuard` 尚未真正执行，因此系统签名或来源记录都不能代替调用权限校验。

### 3.3 IXcLoggerListener 回调

```java
interface IXcLoggerListener {
    void onStatusChanged(int status);
    void onOperationResult(String opType, boolean success, String message, boolean runningState);
    void onCompressFinished(boolean success, String message);
    void onCompressReady(String zipFiles, int retryCount, int maxRetryCount);
}
```

- `onStatusChanged`：`0` 为停止，`1` 为运行。
- `onOperationResult`：AIDL 同步控制请求得到的结果；异步压缩初始受理结果的 message 为 `async_result_pending` 时不会回调该方法。
- `onCompressFinished`：压缩成功、失败或无日志文件时触发。
- `onCompressReady`：仅在压缩成功且状态进入 `WAIT_UPLOAD_RESULT` 时触发。应先注册监听器再请求压缩。

API 4 配置更新使用独立的 `IXcLoggerConfigUpdateCallback`：

```java
oneway interface IXcLoggerConfigUpdateCallback {
    void onComplete(in XcLoggerConfigUpdateResult result);
}
```

`onComplete()` 返回状态码、请求 ID、错误消息、实际变更字段及是否重启采集。它与长期注册的 `IXcLoggerListener` 无关，每次 `updateConfiguration2()` 请求单独传入。

### 3.4 使用示例

```java
private IXcLoggerService service;

private final ServiceConnection connection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = IXcLoggerService.Stub.asInterface(binder);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }
};

void bindLogger() {
    Intent intent = new Intent("com.xcheng.xclogger.REMOTE_BIND");
    intent.setPackage("com.xcheng.xclogger");
    bindService(intent, connection, Context.BIND_AUTO_CREATE);
}

void updateFilter() throws RemoteException {
    XcLoggerConfig patch = new XcLoggerConfig();
    patch.setFilterTag("TagA,TagB");
    patch.setFilterLevel("e");
    service.updateConfigurationPartial(patch);
}
```

推荐通过 API 4 AAR 的 `XcLoggerConfigUpdater` 和客户端 wrapper 使用链式更新：

```java
client.configUpdater()
        .filterTags("TagA")
        .addTag("TagB")
        .filterPackages("com.demo.app")
        .blacklistPackages("com.demo.blocked")
        .packageFilterMode(XcLoggerConfigUpdater.PackageFilterMode.WHITELIST)
        .commitAsync(result -> {
            // SUCCESS/NO_CHANGES 的 isSuccess() 为 true
        });
```

replace/add/remove 的最终值由 XCLogger 服务端串行计算，不依赖客户端配置快照。Updater 提交后不可复用。

### 3.5 AIDL 文件路径

```
app/src/main/aidl/com/xcheng/xclogger/service/IXcLoggerService.aidl
app/src/main/aidl/com/xcheng/xclogger/service/IXcLoggerListener.aidl
app/src/main/aidl/com/xcheng/xclogger/service/IXcLoggerConfigUpdateCallback.aidl
app/src/main/aidl/com/xcheng/xclogger/util/XcLoggerConfig.aidl
app/src/main/aidl/com/xcheng/xclogger/util/XcLoggerConfig2.aidl
app/src/main/aidl/com/xcheng/xclogger/util/XcLoggerConfigUpdate.aidl
app/src/main/aidl/com/xcheng/xclogger/util/XcLoggerConfigUpdateResult.aidl

xclogger-api/src/main/aidl/com/xcheng/xclogger/service/IXcLoggerService.aidl
xclogger-api/src/main/aidl/com/xcheng/xclogger/service/IXcLoggerListener.aidl
xclogger-api/src/main/aidl/com/xcheng/xclogger/service/IXcLoggerConfigUpdateCallback.aidl
xclogger-api/src/main/aidl/com/xcheng/xclogger/util/XcLoggerConfig.aidl
xclogger-api/src/main/aidl/com/xcheng/xclogger/util/XcLoggerConfig2.aidl
xclogger-api/src/main/aidl/com/xcheng/xclogger/util/XcLoggerConfigUpdate.aidl
xclogger-api/src/main/aidl/com/xcheng/xclogger/util/XcLoggerConfigUpdateResult.aidl
```

以上 7 个文件构成同一个 Binder 契约，发布 APK 与 AAR 前必须逐文件保持一致。AIDL 新方法只能追加到接口尾部；Parcelable 字段及 Parcel 读写顺序不得单边修改。

---

## 四、AAR 集成方式

### 4.1 接入步骤

将 `xclogger_aar/xclogger-api-release.aar` 复制到外部工程：

```text
app/libs/xclogger-api-release.aar
```

```gradle
dependencies {
    implementation files('libs/xclogger-api-release.aar')
}
```

公共 API 源码模块启用了 AIDL 并为消费者保留 AIDL/Parcelable 类；接入预编译 AAR 时通常不需要额外启用 `aidl` build feature。若改为直接引入 API 源码模块或自行复制 AIDL 文件，再按工程构建方式启用 AIDL。

AAR 对外类型为：

- `com.xcheng.xclogger.service.IXcLoggerService`
- `com.xcheng.xclogger.service.IXcLoggerListener`
- `com.xcheng.xclogger.service.IXcLoggerConfigUpdateCallback`
- `com.xcheng.xclogger.util.XcLoggerConfig`
- `com.xcheng.xclogger.util.XcLoggerConfigUpdater`
- `com.xcheng.xclogger.util.XcLoggerConfigUpdate`
- `com.xcheng.xclogger.util.XcLoggerConfig2`
- `com.xcheng.xclogger.util.XcLoggerConfigUpdateResult`

### 4.2 XcLoggerConfig 字段

| 字段 | 类型 | 当前用途 |
|------|------|----------|
| `totalSizeMb` | int | 总空间上限，按 MB 传入 |
| `fileSizeMb` | int | 单文件上限，按 MB 传入 |
| `bufferSizeBytes` | int | 写缓冲大小，byte |
| `logDir` | String | 日志目录 |
| `logPeriodHours` | int | 保留周期，小时 |
| `filterTag` | String | 当前生效的 Tag 过滤 |
| `filterLevel` | String | 当前生效的 Level 阈值；默认返回 `v`，表示不按 Level 丢弃 |
| `filterPackage` | String | 精确/前缀包白名单；服务映射为 UID/PID，在 `WHITELIST` 模式使用 |
| `filterTagBlacklist` | String | 仅为 Parcelable/数据兼容保留；不由 updater 修改，也不参与采集 |
| `filterPackageBlacklist` | String | Package 黑名单；映射 UID/PID 后在 `BLACKLIST` 模式排除，支持 replace/add/remove |
| `filterLevelBlacklist` | String | 仅为兼容保留；持久化但不参与采集 |
| `filterContent` | String | 仅为兼容保留；持久化但不参与采集 |
| `filterContentBlacklist` | String | 仅为兼容保留；持久化但不参与采集 |

`XcLoggerConfig` 通过 Parcelable 跨进程传递。包过滤模式不追加到旧 Parcelable 布局；读取使用 `getPackageFilterMode()`，写入通过 `XcLoggerConfig2` 传给 `updateConfiguration2()`。为保持数据布局一致，外部应用应使用 AAR 提供的类，不应自行复制、改动或重排字段。

`updateConfigurationPartial()` 是兼容接口，其实际 patch 规则与 API 4 updater 不同：正整数和非空字符串才覆盖旧值；`bufferSizeBytes` 会向上对齐到 512 并限制为最大 4096；包过滤模式及 Tag/Level/Content 黑名单字段保持原值。该接口只返回同步布尔结果，不提供字段级结果。新代码应使用 `XcLoggerConfigUpdater`。

### 4.3 压缩与上传流程

`triggerCompression()` 与 `triggerCompressionWithRange()` 为异步请求，并具有强制重开语义：已有压缩、取消或待上传任务时，APK 会清理旧 ZIP 和状态后处理新请求；压缩运行竞争时会标记当前任务取消并在结束后重新启动服务。

```
外部调用 triggerCompression()
  → 当前日志运行时尝试封口/轮转当前文件
  → 创建日志文件快照并压缩
  → 日志 entry 全部写完后建立操作历史固定长度快照
  → 将 A_OperationHistory_yyyyMMddHHmmss.txt 写为每个 ZIP 的最后一个 entry
  → 成功：状态 WAIT_UPLOAD_RESULT，回调 onCompressReady()
  → 外部读取 ZIP、上传后调用 reportUploadResult(success)
      ├── success=true：删除 ZIP，状态 IDLE
      └── success=false：失败计数 +1；第 3 次失败后删除 ZIP 并回到 IDLE
```

无时间范围时，压缩服务会按日期分组生成 ZIP；有时间范围时生成单个范围 ZIP。每个 ZIP 都以操作历史快照作为最后一个 entry，快照不移动、截断或删除原历史文件。`getLogZip()` 只传输待上传列表中的第一份 ZIP，因此多日期压缩场景若产生多个 ZIP，外部应结合 `zip_files` 处理其余路径；直接读取这些路径是否可行取决于设备 SELinux/权限策略。

### 4.4 时间范围压缩

`triggerCompressionWithRange(startTime, endTime)` 中，`null` 或空字符串表示不限制对应边界。

| 参数组合 | 当前行为 |
|----------|----------|
| 仅 `startTime` | 筛选开始时间之后、与文件内容区间重叠的文件 |
| 仅 `endTime` | 筛选结束时间之前、与文件内容区间重叠的文件；有结果时后续 ZIP 命名可能因空 `startTime` 触发异常 |
| 两者均传 | 筛选与范围重叠的文件 |
| 两者均不传 | 走无时间范围的按日期压缩路径 |

若参数不能解析为数字，服务保留原始快照；若开始时间晚于结束时间，范围筛选会返回原始快照。仅传 `endTime` 且筛选结果非空时，后续 ZIP 名称构建使用空 `startTime`，当前可能抛出 `NullPointerException`。调用方应传入有效且有序的 `yyyyMMddHHmmss` 时间值。

`import_config` 会校验 XML 根节点、声明字段、基础字段值以及上述过滤值；当前导入支持基础配置字段，非空扩展过滤字段会被拒绝。只有配置成功提交到数据库，并且运行中服务的 stop/start 调用未抛出异常后，才会尝试删除源文件；无有效变更也会先完成一次持久化提交，但不会重启服务。源文件删除失败时操作返回失败，已提交的配置不会回滚。目录和文件名边界仍由调用方负责。

---

## 五、常见问题

### 5.1 为什么收不到 `CTRL_RESULT`？

结果广播是显式定向到 APK 内置 `TARGET_PACKAGES` 的，不会自动发送给任意注册者。集成应用必须使用已列入该数组的包名；新增集成包名需要重新构建 APK 并将其加入目标数组。

### 5.2 新安装的前缀匹配包何时生效？

`PACKAGE_ADDED` 会刷新匹配包的运行中 PID 集合。除此之外，当前刷新逻辑在 `readLogcatOutput()` 循环中约每 10 秒检查一次；它不是独立轮询线程，因此依赖读取线程持续运行。

### 5.3 当前哪些过滤维度生效？

当前支持 Tag 白名单、原生 Level 阈值和 Package 三态过滤。Package `OFF` 全量放行；`WHITELIST` 使用白名单；`BLACKLIST` 使用黑名单，白/黑名单不会叠加。Tag 黑名单、Level 黑名单及 Content 白/黑名单仅保留兼容数据，不参与采集。`AndroidRuntime`、`DEBUG`、`libc` 作为关键崩溃 Tag 仍无条件保留。

### 5.4 如何确认压缩完成？

`triggerCompression()` 返回 `true` 仅表示请求已受理。最终结果：

- 广播方式：接收 `CTRL_RESULT`，并检查 `compress_state` / `zip_files`。
- AIDL 方式：先注册监听器，再等待 `onCompressReady()` 或 `onCompressFinished()`。

---

## 六、版本信息

- **文档版本**：按当前源码更新
- **应用版本**：v2.0.0（versionCode 15）
- **配置协议**：API 4
- **最低 SDK**：API 23 (Android 6.0)
- **目标 SDK**：API 33 (Android 13)
- **AIDL 能力**：支持范围压缩、取消压缩、压缩状态查询和 `getLogZip()` 管道读取
