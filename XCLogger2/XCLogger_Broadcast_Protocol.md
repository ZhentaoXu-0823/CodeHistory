# XCLogger 广播协议参考文档

> **版本**：v2.0.0  
> **更新日期**：2026-08-11  
> **适用平台**：Android 10（API 29）及以上，系统签名环境  
> **目标读者**：需要通过广播方式远程控制 XCLogger 的集成方开发者

---

## 一、概述

XCLogger 通过 Android 系统广播机制暴露远程控制接口。外部发送广播请求即可控制日志采集、基础配置更新、配置文件导入与压缩任务，无需编写代码或集成 SDK。

> **结果投递限制**：请求接收器是 exported 组件，当前未声明广播 permission；但 `CTRL_RESULT` 是定向广播，只发送给 APK 内置的目标包名：`com.xcheng.mdm`、`com.xcheng.xcloggertestdemo`、`com.xcheng.xclogger`、`com.ko.xclogger`。未列入该列表的应用无法直接接收结果，需在 APK 中增加其包名后重新构建。

### 核心 Action

| Action | 方向 | 说明 |
|--------|------|------|
| `com.xcheng.xclogger.CTRL_REQUEST` | 外部 → XCLogger | 发送控制请求 |
| `com.xcheng.xclogger.CTRL_RESULT` | XCLogger → 外部 | 返回操作结果 |

### 兼容旧接口

| Action | 说明 | 状态 |
|--------|------|------|
| `com.xcheng.xclogger.ADB_CMD` + `cmd_name=start_xc_log` | 旧版启动方式 | 兼容保留 |
| `com.xcheng.xclogger.ADB_CMD` + `cmd_name=stop_xc_log` | 旧版停止方式 | 兼容保留 |

---

## 二、请求格式

### 2.1 通用格式

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
    --es op_type <操作类型> \
    [额外参数]
```

### 2.2 必填参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `op_type` | String | 要执行的操作类型，见下表 |

### 2.3 可选额外参数

| 参数 | 类型 | 适用 op_type | 说明 |
|------|------|-------------|------|
| `total_size` | int | `update_config` | 总空间上限（MB） |
| `file_size` | int | `update_config` | 单文件上限（MB） |
| `buffer_size` | int | `update_config` | 缓冲区大小（byte；更新时按 512 向上对齐，最大 4096） |
| `log_dir` | String | `update_config` | 日志存储目录 |
| `log_period` | int | `update_config` | 保留周期（小时） |
| `filter_tag` | String | `update_config` | 当前生效的 Tag 过滤 |
| `filter_level` | String | `update_config` | 当前生效的 Level 阈值 |
| `filter_package` | String | `update_config` | Package 白名单；按精确包名/前缀映射 UID/PID，在 `WHITELIST` 模式生效 |
| `filter_package_blacklist` | String | `update_config` | Package 黑名单；按精确包名/前缀映射 UID/PID，在 `BLACKLIST` 模式生效 |
| `startTime` | String | `trigger_compress` | 时间范围压缩起始时间；建议格式 `yyyyMMddHHmmss` |
| `endTime` | String | `trigger_compress` | 时间范围压缩结束时间；建议格式 `yyyyMMddHHmmss` |
| `success` | boolean | `upload_result` | 上传是否成功（`true` / `false`） |
| `config_file_path` | String | `import_config` | XML 配置文件的绝对路径 |

---

## 三、支持的操作（op_type）

### 全部操作一览

| op_type | 功能 | 额外参数 | 同步/异步 |
|---------|------|---------|-----------|
| `start` | 启动日志采集 | 无 | 同步 |
| `stop` | 停止日志采集 | 无 | 同步 |
| `restart` | 重启日志采集 | 无 | 同步 |
| `update_config` | 部分更新配置 | 见下方配置参数 | 同步（自动 stop→update→start） |
| `import_config` | 从 XML 文件导入配置 | `config_file_path` | 同步 |
| `query_status` | 查询运行状态 | 无 | 同步 |
| `trigger_compress` | 触发压缩 | `startTime` / `endTime`（可选；当前建议同时传入） | **异步** |
| `upload_result` | 回传上传结果 | `success` | 同步 |
| `query_compress_status` | 查询压缩/上传状态 | 无 | 同步 |
| `cancel_compress` | 取消压缩/上传并删除 ZIP | 无 | 同步 |
| `query_files_dir` | 查询日志存储路径（v1.2.14） | 无 | 同步 |
| `query_zip_dir` | 查询压缩输出目录（v1.2.14） | 无 | 同步 |

### 3.1 start — 启动日志采集

启动后台日志采集前台服务。如果已运行则无效果。

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type start
```

### 3.2 stop — 停止日志采集

停止后台日志采集服务。如果未运行则无效果。

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type stop
```

### 3.3 restart — 重启日志采集

先停止日志采集，再重新启动。适用于配置变更后需要重启的场景。

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type restart
```

### 3.4 update_config — 部分更新配置

部分更新配置字段，**只传需要修改的字段**，不传的字段保持原值。XCLogger 会自动执行"停止 → 应用新配置 → 启动"流程。

**配置参数完整表**：

| 参数 | 类型 | 说明 |
|------|------|------|
| `total_size` | int | 日志总空间上限，单位 MB |
| `file_size` | int | 单文件大小上限，单位 MB |
| `buffer_size` | int | 缓冲区大小，单位 byte；向上对齐至 512 的倍数，最大 4096 |
| `log_dir` | String | 日志存储目录 |
| `log_period` | int | 日志保留周期，单位 小时 |
| `filter_tag` | String | Tag 过滤，逗号分隔多个值；`all` 为不过滤 |
| `filter_level` | String | Level 阈值：`f`/`e`/`w`/`i`/`d`/`v`；默认 `v` 表示不按 Level 丢弃，兼容接受 `all` |
| `filter_package` | String | Package 白名单；逗号分隔；以 `.` 结尾表示前缀匹配；`all` 为不限名单内容 |
| `filter_package_blacklist` | String | Package 黑名单；逗号分隔；以 `.` 结尾表示前缀匹配；不能用空字符串清空 |

`total_size` 写入数据库后会在后续启动、覆盖升级和恢复默认时保留。仅当数据库完全没有配置时，Android 13（API 33，含 Go）和 Android 15（API 35）才会按主存储容量生成首次值：8/16 GB 档为 512 MB，32/64 GB 档为 1024 MB；容量读取失败时为 256 MB。其他 Android 版本首次初始化继续使用 flavor XML。

**示例**：更新过滤配置，只看 TagA 和 TagB 的 Error 及以上日志

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type update_config \
  --es filter_tag "TagA,TagB" \
  --es filter_level "e"
```

**示例**：更新 Package 白名单内容

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type update_config \
  --es filter_package "com.example."
```

**示例**：更新 Package 黑名单内容

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type update_config \
  --es filter_package_blacklist "com.example.noisy,com.example.debug."
```

> **包过滤模式限制**：广播只更新两套名单内容，不能切换 `OFF` / `WHITELIST` / `BLACKLIST`。默认模式为 `OFF`，此时更新名单不会改变采集结果。模式切换和名单清空必须通过 API 4 AAR/AIDL 完成。

> **停用字段**：`filter_tag_blacklist`、`filter_level_blacklist`、`filter_content`、`filter_content_blacklist` 不属于当前广播协议；接收器不会读取这些 Extra。对应历史数据字段仅为数据库、XML 和 Parcelable 兼容而保留。

### 3.5 import_config — 从 XML 文件导入配置

从指定 XML 文件导入配置（部分更新，仅覆盖 XML 中声明的字段）。导入完成后自动删除 XML 源文件。

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type import_config \
  --es config_file_path "/data/local/tmp/new_config.xml"
```

### 3.6 query_status — 查询运行状态

查询当前日志采集是否在运行。

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_status
```

### 3.7 trigger_compress — 触发压缩

触发日志文件压缩。支持全量压缩和按时间范围压缩。

**全量压缩**（对当前快照中的日志按日期分组压缩；可能生成多个 ZIP）：

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type trigger_compress
```

**按时间范围压缩**（只压缩指定时段内的日志）：

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type trigger_compress \
  --es startTime "20260601135000" \
  --es endTime "20260602030000"
```

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `startTime` | String | 建议传入 | 起始时间，建议格式 `yyyyMMddHHmmss` |
| `endTime` | String | 建议传入 | 结束时间，建议格式 `yyyyMMddHHmmss` |

> **时间范围限制**：接收器实际读取 camelCase 的 `startTime`、`endTime`。范围筛选会移除非数字字符后比较；无效值或起始晚于结束时，当前实现不进行范围筛选。范围 ZIP 命名依赖 `startTime`，仅传 `endTime` 且有可压缩文件时可能因空值触发 `NullPointerException`，因此当前应始终同时传入有效且有序的 `startTime` 与 `endTime`。源码尚未执行严格格式、真实日期或 canonical 输出目录校验。

> **注意**：压缩是**异步操作**。`trigger_compress` 的初始结果为 `async_result_pending`，不会发送 `CTRL_RESULT`；压缩完成后才会通过 `CTRL_RESULT` 通知最终结果。无可压缩日志文件时最终结果为成功，但 `compress_state=IDLE`、`zip_files` 为空。

每个生成的 ZIP 都先写入全部日志文件，再建立操作历史固定长度快照，并将 `A_OperationHistory_yyyyMMddHHmmss.txt` 写为最后一个 entry。原操作历史文件不会被移动、截断或删除。

### 3.8 upload_result — 回传上传结果

当外部收到压缩完成且 `compress_state=WAIT_UPLOAD_RESULT`、`zip_files` 非空的 `CTRL_RESULT` 后完成 ZIP 上传，再通过此操作回传上传是否成功。

**上传成功**：

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type upload_result --ez success true
```

**上传失败**：

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type upload_result --ez success false
```

### 3.9 query_compress_status — 查询压缩/上传状态

查询当前的压缩或上传任务状态。

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type query_compress_status
```

### 3.10 cancel_compress — 取消压缩

取消正在进行的压缩或上传任务，并删除已生成的 ZIP 文件。

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type cancel_compress
```

### 3.11 query_files_dir — 查询日志存储路径（v1.2.14）

查询当前日志文件的存储目录。结果通过 `CTRL_RESULT.message` 返回。该查询依赖已初始化的 `ConfigLoader` 缓存；若尚未加载配置，可能返回失败结果。

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type query_files_dir
```

### 3.12 query_zip_dir — 查询压缩输出目录（v1.2.14）

查询压缩包的输出目录（固定为 `/data/xclogger/mobilelog`）。结果通过 `CTRL_RESULT.message` 返回。

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type query_zip_dir
```

---

## 四、响应格式

所有操作的最终结果通过 `com.xcheng.xclogger.CTRL_RESULT` 定向广播返回。初始异步压缩受理结果不会发送；结果仅投递给文档开头列出的内置目标包名。

### 4.1 响应字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 操作是否成功 |
| `message` | String | 结果描述或错误说明 |
| `op_type` | String | 对应的请求操作类型 |
| `running_state` | boolean | 操作完成后日志采集的运行状态 |
| `compress_state` | String | 当前压缩/上传状态 |
| `zip_files` | String | 当前待上传的 ZIP 文件路径（多个逗号分隔） |
| `retry_count` | int | 当前上传失败已重试次数 |
| `max_retry_count` | int | 最大允许重试次数（固定为 3） |

### 4.2 压缩状态取值

| 状态 | 说明 |
|------|------|
| `IDLE` | 空闲，无压缩任务 |
| `COMPRESSING` | 压缩进行中 |
| `WAIT_UPLOAD_RESULT` | 压缩完成，等待外部上传结果 |
| `CANCELLING` | 取消中 |

### 4.3 响应示例

```text
Broadcast received: Intent { act=com.xcheng.xclogger.CTRL_RESULT
  (has extras) }
  success=true
  message=ok
  op_type=start
  running_state=true
  compress_state=IDLE
```

---

## 五、配置更新行为说明

### 5.1 stop → update → start 自动流程

当收到 `update_config` 或 `import_config` 请求时，如果日志采集正在运行，XCLogger 会自动执行：

```
1. 停止当前日志采集
2. 应用新配置（合并部分更新）
3. 重新启动日志采集
```

外部只需发送一次请求，无需关心内部顺序。

### 5.2 配置版本推送

XCLogger 内置配置版本号机制。当 APK 升级后配置版本高于设备上存储的版本时，会自动用 XML 默认配置覆盖现有配置。此机制与 `update_config` / `import_config` 独立运作。

---

## 六、压缩上传完整流程

```
外部发送 trigger_compress（广播或 AIDL）
  │
  ├─ 已有未完成的任务？→ 取消旧任务，清理旧 ZIP
  │
  ├─ 切换当前日志文件（确保已写内容被快照）
  │
  ├─ 筛选日志文件
  │     ├─ 无时间范围 → 当前快照按日期分组，可能生成多个 ZIP
  │     └─ 时间范围压缩 → 与 `[startTime, endTime]` 重叠的文件，生成单一 ZIP
  │
  ├─ 打包为 ZIP（输出到 /data/xclogger/mobilelog/）
  │
  ├─ 状态 → WAIT_UPLOAD_RESULT
  │
  ├─ 发送 CTRL_RESULT（compress_state=WAIT_UPLOAD_RESULT, zip_files=路径）
  │
  └─ 等待外部发送 upload_result
        ├─ success=true  → 删除 ZIP，状态 → IDLE
        └─ success=false → 重试计数 +1
              ├─ 未满 3 次 → 状态保持 WAIT_UPLOAD_RESULT，外部可重试上传
              └─ 第 3 次失败 → 强制删除 ZIP，状态 → IDLE
```

---

## 七、旧版 ADB 命令（兼容）

以下旧接口保持兼容，但推荐使用新的 CTRL_REQUEST 协议。

```bash
# 旧版启动
adb shell am broadcast -a com.xcheng.xclogger.ADB_CMD --es cmd_name start_xc_log

# 旧版停止
adb shell am broadcast -a com.xcheng.xclogger.ADB_CMD --es cmd_name stop_xc_log
```

---

## 八、安全说明

| 安全机制 | 说明 |
|---------|------|
| 来源解析 | 广播来源优先使用 `Intent.getPackage()`；未指定时标记为 `adb`。当前代码会记录来源，但不会调用 `SourceWhitelistGuard` 拒绝请求 |
| 白名单现状 | `SourceWhitelistGuard` 虽已定义，但当前控制执行路径未调用它；发送方不应将该白名单视为访问控制 |
| 系统级保护 | `AndroidRuntime`、`DEBUG`、`libc` 会绕过当前 Tag / Level / PID 包过滤 |

---

## 九、完整示例

### 日常使用流程

```bash
# 1. 启动日志采集
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type start

# 2. 查询状态确认已运行
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_status

# 3. 按需更新配置（如只看某应用的 Error 日志）
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type update_config \
  --es filter_package "com.example.myapp" \
  --es filter_level "e"

# 4. 一段时间后触发压缩
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type trigger_compress

# 5. 收到 `CTRL_RESULT` 且 `compress_state=WAIT_UPLOAD_RESULT`、`zip_files` 非空后完成上传，再回传结果
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type upload_result --ez success true

# 6. 停止日志采集
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type stop
```

---

> **相关文档**：`XCLogger_AAR_API_Reference.md`（AIDL 服务绑定方式） | `INTEGRATION.md`（外部集成总览）
