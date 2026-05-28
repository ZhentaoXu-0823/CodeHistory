# XCLogger 广播控制使用说明（AIDL 对照）

本文档说明外部 Demo / 自动化脚本如何通过广播控制 XCLogger。

- 控制入口（唯一）：`com.xcheng.xclogger.CTRL_REQUEST`
- 结果出口（唯一）：`com.xcheng.xclogger.CTRL_RESULT`
- 外部 Demo 只需注册监听一个广播 action：`com.xcheng.xclogger.CTRL_RESULT`

---

## 1. 总览

XCLogger 支持两种外部控制方式：

1. **广播方式**：无需绑定，适合 ADB、自动化脚本、简单 Demo。
2. **AIDL 方式**：绑定 `RemoteBindService`，适合 App 内集成和状态回调。

所有操作结果（包括异步压缩结果）统一通过 `CTRL_RESULT` 返回。

`trigger_compress` 的语义是：**强制开始一轮新的压缩上传流程**。

如果当前已有压缩未结束，或已有 zip 正在等待上传结果，XCLogger 会先停止旧流程、清理旧 zip，然后重新开始压缩。中间不返回“调度成功”的中间结果，只在新一轮压缩完成后发送最终 `CTRL_RESULT`。

---

## 2. 统一控制广播

### 2.1 请求 Action

```text
com.xcheng.xclogger.CTRL_REQUEST
```

### 2.2 必填 Extra

```text
op_type (String)
```

### 2.3 支持的 `op_type`

| op_type | 说明 |
|---|---|
| `start` | 启动日志采集 |
| `stop` | 停止日志采集 |
| `restart` | 重启日志采集 |
| `update_config` | 部分更新配置 |
| `query_status` | 查询运行状态 |
| `trigger_compress` | 强制开始新一轮压缩上传流程 |
| `upload_result` | 回传上传结果 |
| `query_compress_status` | 查询压缩/上传状态 |
| `cancel_compress` | 强制取消压缩/上传状态并删除 zip |

---

## 3. 统一结果广播

### 3.1 回执 Action

```text
com.xcheng.xclogger.CTRL_RESULT
```

### 3.2 回执字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `success` | boolean | 操作是否成功 |
| `message` | String | 结果或错误说明 |
| `op_type` | String | 对应请求操作 |
| `running_state` | boolean | 日志采集状态 |
| `compress_state` | String | 压缩/上传状态 |
| `zip_files` | String | 当前待上传 zip，多个用英文逗号分隔 |
| `retry_count` | int | 当前上传失败次数 |
| `max_retry_count` | int | 最大失败次数，当前为 3 |

### 3.3 `compress_state` 取值

| 状态 | 说明 |
|---|---|
| `IDLE` | 空闲，无压缩任务，无待上传 zip |
| `COMPRESSING` | 正在压缩 |
| `WAIT_UPLOAD_RESULT` | 压缩成功，等待外部上传结果 |
| `CANCELLING` | 正在取消压缩/上传状态 |

---

## 4. 压缩流程说明

### 4.1 触发压缩

外部发送：

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type trigger_compress
```

XCLogger 会异步执行压缩。压缩完成后通过 `CTRL_RESULT` 返回最终结果。

压缩成功：

```text
CTRL_RESULT ->
  op_type=trigger_compress
  success=true
  message=compress success, waiting upload result
  compress_state=WAIT_UPLOAD_RESULT
  zip_files=/data/xclogger/mobilelog/2026_0522_163520_ab12.zip
  retry_count=0
  max_retry_count=3
```

压缩失败：

```text
CTRL_RESULT ->
  op_type=trigger_compress
  success=false
  message=失败原因
  compress_state=IDLE
  zip_files=
```

### 4.2 已有流程未结束时再次触发压缩

再次发送 `trigger_compress` 表示强制重开压缩上传流程。

| 当前状态 | 新 `trigger_compress` 行为 |
|---|---|
| `IDLE` | 直接开始压缩 |
| `WAIT_UPLOAD_RESULT` | 删除旧 pending zip，清理上传等待状态，立即开始新压缩 |
| `COMPRESSING` | 请求取消当前压缩，删除已生成/生成中的 zip，当前线程退出后自动开始新压缩 |
| `CANCELLING` | 如果仍有压缩线程运行，则等待取消后自动开始新压缩；如果只是残留状态，则清理后直接开始新压缩 |

注意：这种强制重开流程**不会返回中间成功结果**，只在新一轮压缩完成后返回最终：

```text
CTRL_RESULT ->
  op_type=trigger_compress
  success=true/false
  compress_state=WAIT_UPLOAD_RESULT 或 IDLE
  zip_files=新一轮 zip 或空
```

### 4.3 上传结果回传

上传成功：

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type upload_result \
  --ez success true
```

返回：

```text
CTRL_RESULT ->
  op_type=upload_result
  success=true
  message=upload success, compressed files deleted
  compress_state=IDLE
  zip_files=
```

上传失败：

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type upload_result \
  --ez success false
```

未满 3 次失败：

```text
CTRL_RESULT ->
  op_type=upload_result
  success=false
  message=upload failed, please retry
  compress_state=WAIT_UPLOAD_RESULT
  zip_files=/data/xclogger/mobilelog/2026_0522_163520_ab12.zip
  retry_count=1
  max_retry_count=3
```

外部 Demo 看到 `success=false` 且 `compress_state=WAIT_UPLOAD_RESULT` 且 `zip_files` 非空时，应重新上传同一个 zip。

第 3 次失败：

```text
CTRL_RESULT ->
  op_type=upload_result
  success=false
  message=upload failed 3 times, compressed files deleted
  compress_state=IDLE
  zip_files=
  retry_count=3
  max_retry_count=3
```

### 4.4 查询压缩状态

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_compress_status
```

返回示例：

```text
CTRL_RESULT ->
  op_type=query_compress_status
  success=true
  message=compress_state=WAIT_UPLOAD_RESULT
  compress_state=WAIT_UPLOAD_RESULT
  zip_files=/data/xclogger/mobilelog/2026_0522_163520_ab12.zip
  retry_count=1
  max_retry_count=3
```

### 4.5 取消压缩/上传

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type cancel_compress
```

返回示例：

```text
CTRL_RESULT ->
  op_type=cancel_compress
  success=true
  message=compress upload task cancelled, zip files deleted
  compress_state=IDLE
  zip_files=
```

---

## 5. zip 命名规则

压缩按日志日期分组，同一天日志生成一个 zip。文件名格式：

```text
yyyy_MMdd_HHmmss_hash.zip
```

规则：

- 已结束日期的日志：`HHmmss` 固定为 `235959`
- 当前设备日期的日志：`HHmmss` 使用触发压缩时的当前设备时间

示例（当前设备时间 `2026-05-22 16:35:20`）：

```text
2026_0520_235959_ab12.zip
2026_0521_235959_cd34.zip
2026_0522_163520_ef56.zip
```

---

## 6. AIDL 与广播能力对照

| AIDL 接口 | 广播 `op_type` | 说明 |
|---|---|---|
| `startLogging()` | `start` | 启动日志采集 |
| `stopLogging()` | `stop` | 停止日志采集 |
| `isRunning()` | `query_status` | 查询运行状态 |
| `updateConfigurationPartial(config)` | `update_config` | 部分更新配置 |
| `triggerCompression()` | `trigger_compress` | 强制开始新一轮压缩上传流程 |
| `reportUploadResult(success)` | `upload_result` | 回传上传结果 |
| `getCompressStatus()` | `query_compress_status` | 查询压缩/上传状态 |
| `cancelCompressTask()` | `cancel_compress` | 取消压缩/上传并删除 zip |
| （无直接接口） | `restart` | 重启采集服务 |

---

## 7. 外部 Demo 推荐广播接入流程

1. 注册监听 `com.xcheng.xclogger.CTRL_RESULT`。
2. 需要压缩时直接发送 `CTRL_REQUEST op_type=trigger_compress`。
3. 等待 `CTRL_RESULT op_type=trigger_compress`：
   - `success=true` 且 `compress_state=WAIT_UPLOAD_RESULT`：读取 `zip_files`，上传 zip。
   - `success=false`：压缩失败，查看 `message`。
4. 上传完成后发送 `CTRL_REQUEST op_type=upload_result --ez success true/false`。
5. 收到 `CTRL_RESULT op_type=upload_result`：
   - `success=true`：上传成功，zip 已删除，流程结束。
   - `success=false` 且 `compress_state=WAIT_UPLOAD_RESULT` 且 `zip_files` 非空：重试上传同一个 zip。
   - `success=false` 且 `compress_state=IDLE`：失败 3 次，zip 已强制删除，流程结束。
6. 如果想强制重新压缩，不需要先 query/cancel，直接再次发送 `trigger_compress`。

---

## 8. 行为约束

- 压缩时不会停止日志采集。
- 如果日志正在运行，XCLogger 会先封口当前日志文件，再新建下一个文件继续写。
- 本次压缩只压缩封口前的日志快照，不压缩新文件。
- 同一时间只允许一个实际压缩线程。
- 再次发送 `trigger_compress` 会强制停止旧压缩上传流程并重新开始。
- 新压缩开始前会清理旧 zip。
- 上传成功后删除本次 zip。
- 上传失败未满 3 次时，外部 Demo 应主动重试上传。
- 上传失败达到 3 次后强制删除 zip。
- `cancel_compress` 会强制清理当前压缩/上传状态并删除 zip。
- 关键操作会记录到 `A_OperationHistory.txt`。
