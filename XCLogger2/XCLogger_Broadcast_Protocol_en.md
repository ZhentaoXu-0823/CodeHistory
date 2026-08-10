# XCLogger Broadcast Protocol Reference

> **Version**: v2.0.0  
> **Updated**: 2026-08-11  
> **Target**: Developers integrating XCLogger control through system broadcasts  
> **Platform**: Android 10+, system-signed or ADB-capable environment

---

## 1. Quick Start

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type start
```

All requests use `com.xcheng.xclogger.CTRL_REQUEST` with an `op_type` extra.

> **Result-delivery constraint**: The request receiver is exported and declares no broadcast permission. However, `CTRL_RESULT` is an explicit broadcast sent only to the APK target list: `com.xcheng.mdm`, `com.xcheng.xcloggertestdemo`, `com.xcheng.xclogger`, and `com.ko.xclogger`. An integration app outside that list cannot directly receive results unless the APK is rebuilt with its package added.

---

## 2. Request Format

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
    --es op_type <operation> [--es <key> <value> ...]
```

| Parameter | Type | Required | Description |
|---|---|---|---|
| `op_type` | String | No | Operation to execute. Missing or empty value defaults to `query_status`. |

### 2.1 Optional Extras

| Extra | Type | Applies to | Current behavior |
|---|---|---|---|
| `total_size` | int | `update_config` | Total storage limit in MB |
| `file_size` | int | `update_config` | Per-file limit in MB |
| `buffer_size` | int | `update_config` | Buffer size in bytes; rounded up to 512, capped at 4096 |
| `log_dir` | String | `update_config` | Log directory |
| `log_period` | int | `update_config` | Retention period in hours |
| `filter_tag` | String | `update_config` | Active tag filter |
| `filter_level` | String | `update_config` | Active level threshold |
| `filter_package` | String | `update_config` | Package allowlist; exact/prefix entries map to UIDs/PIDs and apply in `WHITELIST` mode |
| `filter_package_blacklist` | String | `update_config` | Package denylist; exact/prefix entries map to UIDs/PIDs and apply in `BLACKLIST` mode |
| `startTime` | String | `trigger_compress` | Range start; use `yyyyMMddHHmmss` |
| `endTime` | String | `trigger_compress` | Range end; use `yyyyMMddHHmmss` |
| `success` | boolean | `upload_result` | Upload result (`true` / `false`) |
| `config_file_path` | String | `import_config` | Absolute XML configuration-file path |

---

## 3. Available Operations

| op_type | Purpose | Extra Params | Mode |
|---|---|---|---|
| `start` | Start log capture | — | Final result is asynchronous through `CTRL_RESULT` |
| `stop` | Stop log capture | — | Final result is asynchronous through `CTRL_RESULT` |
| `restart` | Restart log capture | — | Final result is asynchronous through `CTRL_RESULT` |
| `update_config` | Partially update basic configuration | See section 4 | Final result is asynchronous through `CTRL_RESULT` |
| `import_config` | Import configuration from XML | `config_file_path` | Final result is asynchronous through `CTRL_RESULT` |
| `query_status` | Query capture state | — | Final result is asynchronous through `CTRL_RESULT` |
| `trigger_compress` | Trigger compression | `startTime`, `endTime` | Asynchronous |
| `upload_result` | Report upload outcome | `success` | Final result is asynchronous through `CTRL_RESULT` |
| `query_compress_status` | Query compression state | — | Final result is asynchronous through `CTRL_RESULT` |
| `cancel_compress` | Cancel compression/upload and clean ZIPs | — | Final result is asynchronous through `CTRL_RESULT` |
| `query_files_dir` | Query log directory | — | Final result is asynchronous through `CTRL_RESULT` |
| `query_zip_dir` | Query ZIP output directory | — | Final result is asynchronous through `CTRL_RESULT` |

### 3.1 Basic Commands

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type start
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type stop
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type restart
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_status
```

### 3.2 Import Configuration

The XML file is partially merged: only fields declared in the XML can replace existing basic fields. A successfully processed import attempts to delete the source XML file.

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type import_config \
  --es config_file_path "/data/local/tmp/new_config.xml"
```

### 3.3 Path Queries

`query_files_dir` returns the current `logDir` in `CTRL_RESULT.message`; it depends on an initialized `ConfigLoader` cache and can fail before configuration loads. `query_zip_dir` returns the fixed `/data/xclogger/mobilelog` path.

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_files_dir
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_zip_dir
```

---

## 4. Config Update Parameters

For `op_type=update_config`, send only fields that need to change. The eight base fields and a non-empty package denylist are currently merged. Positive integers and non-empty strings replace current values; a patch cannot set an integer to zero or clear a string.

| Param | Type | Description |
|---|---|---|
| `total_size` | int | Storage limit in MB |
| `file_size` | int | Per-file limit in MB |
| `buffer_size` | int | Bytes; rounded up to a multiple of 512 and capped at 4096 |
| `log_dir` | String | Log directory |
| `log_period` | int | Retention hours |
| `filter_tag` | String | Comma-delimited tag filter; `all` disables it |
| `filter_level` | String | `f`/`e`/`w`/`i`/`d`/`v`; default `v` means no level-based drops; legacy `all` is accepted |
| `filter_package` | String | Comma-delimited package allowlist; `.` suffix is prefix matching; `all` means unrestricted list content |
| `filter_package_blacklist` | String | Comma-delimited package denylist; `.` suffix is prefix matching; an empty string cannot clear it |

Once `total_size` is stored in the database, it is preserved across later starts, in-place upgrades, and default resets. Only when the database has no configuration do Android 13 (API 33, including Go) and Android 15 (API 35) derive the initial value from primary-storage capacity: 512 MB for the 8/16 GB tiers and 1024 MB for the 32/64 GB tiers, with a 256 MB fallback when capacity cannot be read. Other Android versions continue to use the flavor XML for first-time initialization.

When capture is running, an effective update follows stop → update → start. Broadcasts update list content but cannot switch `OFF` / `WHITELIST` / `BLACKLIST`. The default is `OFF`, so list updates do not change capture until an API 4 AAR/AIDL client selects `WHITELIST` or `BLACKLIST`. Use the AAR updater to switch modes or clear a list.

`filter_tag_blacklist`, `filter_level_blacklist`, `filter_content`, and `filter_content_blacklist` are not part of the current broadcast protocol and are ignored by the receiver. Their historical data fields remain only for database, XML, and Parcelable compatibility.

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type update_config \
  --es filter_tag "MyTag,AnotherTag" \
  --es filter_level "e"
```

---

## 5. Compression Commands

**No-range compression** snapshots current logs and groups them by date; it can create multiple ZIPs.

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type trigger_compress
```

**Time-range compression** creates one ZIP from files whose estimated content intervals overlap the range.

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type trigger_compress \
  --es startTime "20260701080000" \
  --es endTime "20260702080000"
```

> **Range constraints**: The receiver reads camelCase `startTime` and `endTime`, not `start_time` or `end_time`. Parsing removes non-numeric characters. Invalid timestamps or a start later than the end cause current code to use the unfiltered snapshot. Range ZIP naming depends on `startTime`; only supplying `endTime` can fail with a null-value exception when files are selected. The implementation has no strict timestamp or canonical output-directory validation; always provide valid, ordered `yyyyMMddHHmmss` values.

> **Asynchronous result**: The initial `trigger_compress` result is `async_result_pending` and is not sent as `CTRL_RESULT`. The final compression result is sent later. If no files are available, the final result is successful but has `compress_state=IDLE` and empty `zip_files`.

Every generated ZIP writes all log entries first, snapshots a fixed prefix of the append-only operation history, and writes `A_OperationHistory_yyyyMMddHHmmss.txt` as the final ZIP entry. The original history file is never moved, truncated, or deleted.

**Upload result**:

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type upload_result --ez success true
```

Send `success=true` only after upload succeeds. It deletes all pending ZIPs. A failed report increments the retry count; the third failure force-deletes ZIPs and returns the state to `IDLE`.

---

## 6. Response Format

Final results are returned through explicit `com.xcheng.xclogger.CTRL_RESULT` broadcasts to the target packages listed in section 1. The initial asynchronous compression acceptance result is not broadcast.

| Field | Type | Description |
|---|---|---|
| `success` | boolean | Operation outcome |
| `message` | String | Result or error details; directory query output is here |
| `op_type` | String | Matching operation |
| `running_state` | boolean | Capture state after the operation |
| `compress_state` | String | `IDLE`, `COMPRESSING`, `WAIT_UPLOAD_RESULT`, or `CANCELLING` |
| `zip_files` | String | Comma-delimited pending ZIP paths |
| `retry_count` | int | Upload failure count |
| `max_retry_count` | int | Fixed at 3 |

---

## 7. Complete Daily Workflow

```bash
# 1. Start capture
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type start

# 2. Query state
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_status

# 3. Update active filters
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type update_config --es filter_tag "MyTag" --es filter_level "e"

# 4. Start compression
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type trigger_compress

# 5. Wait for CTRL_RESULT with WAIT_UPLOAD_RESULT and non-empty zip_files,
#    upload the ZIPs, then report the outcome
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type upload_result --ez success true

# 6. Stop capture
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type stop
```

---

## 8. Legacy ADB Commands

```bash
adb shell am broadcast -a com.xcheng.xclogger.ADB_CMD --es cmd_name start_xc_log
adb shell am broadcast -a com.xcheng.xclogger.ADB_CMD --es cmd_name stop_xc_log
```

---

## 9. Security Notes

| Mechanism | Current behavior |
|---|---|
| Source resolution | Broadcast source uses `Intent.getPackage()`; if absent, it is recorded as `adb` |
| Whitelist status | `SourceWhitelistGuard` exists but is not called by the current execution path; do not treat it as access control |
| Critical logs | `AndroidRuntime`, `DEBUG`, and `libc` bypass current Tag / Level / PID package filtering |

---

> **Related**: `XCLogger_AAR_API_Reference_en.md` (AIDL binding) | `INTEGRATION.md` (Integration Guide)
