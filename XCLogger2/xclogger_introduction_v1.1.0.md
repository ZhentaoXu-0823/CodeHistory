# Introduction to XcLogger Usage

- [Overview of Functions and Principles](#overview-of-functions-and-principles)
- [How to use the application](#how-to-use-the-application)

## Ⅰ. Overview of Functions and Principles

### 1. Application Role and Overall position

XCLogger is a system level log collection and management tool that supports filtering logs by configuration, rotating by file shards, automatic clean by time length, compressing backups by day, and writing key operations into the operation history file for easy audit and problem tacking.

### 2. Start/Stop and Foreground Service

**Start/Stop:** You can switch the status by clicking the 'Start/Stop' button on the homepage(MainActivity). After the XcLogger service is successfully started, the system will show the foreground service notification on the status bar, indicating that logs are being collected.

**Auto Recovery:** After application restarts or the device boots up, it will automatically restore to its last running state (if it was running previously).

**Broadcast Control:** It can also be controlled via system broadcast commands:

- **Start:**
  ```bash
  adb shell am broadcast -a com.xcheng.xclogger.ADB_CMD --es cmd_name start_xc_log
  ```

- **Stop:**
  ```bash
  adb shell am broadcast -a com.xcheng.xclogger.ADB_CMD --es cmd_name stop_xc_log
  ```

Status changes are written to the operation history in real time, and the UI automatically refreshes when it is in the foreground.

### 3. Log Collection and Log Filtering

**Log Source:** system logcat command (`logcat -v threadtime,uid`), including time, UID, etc.

**Filter Configurations:** They can be viewed/modified in the configuration page (XcLogger Configuration):

| Configuration | Description | Default Value |
|---------------|-------------|---------------|
| `filter_tag` | Specify tags; supports multiple values (separated by commas) | `all` (no filtering applied) |
| `filter_level` | Log level (options: f/e/w/i/d/v) | `all` |
| `filter_package` | Package name filtering (internally converted to UID) | `all` |

**Runtime Filtering:** Perform secondary matching of Tag/Level/UID within the capture thread. Only logs that meet all conditions will be written to files, reducing noise.

### 4. Log File Management

**File Naming:** The naming format is `mainlog_<Global 6-digit Index>_<yyyyMMdd>_<HHmmss>_<Daily Sequence Number>.txt`, ensuring uniqueness and traceability of generation order.

**Log Rotation:** Automatic rotation based on the configured single-file size (MB). New files will be generated and the operation history will be updated accordingly.

**Storage Path:** The default path is `/storage/emulated/0/XcLogger`, which can be modified in the Configuration Interface. After modification, new logs will be written to the new path, while the operation history path remains unchanged.

**Running State Persistence:** The current running status (active/inactive) is stored in SharedPreferences, which is read by both the foreground UI and background services.

### 5. Automatic Cleanup (Expiration & Capacity-Based)

**Time-based Cleanup:** Based on the retention period set by `log_period` (in hours), expired files in the main log directory will be deleted. This cleanup is triggered before new log files are generated.

**Capacity-based Cleanup:** If the total storage limit is about to be exceeded, the oldest log files will be deleted first.

**Compressed Directory Cleanup:** For zip packages under `/data/xclogger/mobilelog`, expiration is determined by the file name date (based on 00:00:00.000 of the current day) and the `log_period` setting. Expired zip packages will be deleted; operation history backups will not be affected.

### 6. Compression & Backup Service

**Trigger Method:** Send the broadcast `com.xcheng.xclogger.FILE_COMPRESS`. If a compression task is already in progress, a `COMPRESSING` response will be returned upon re-triggering.

**Compression Scope:** Log files are grouped by date. One zip file will be generated per date, named in the format `yyyy_MMdd_<4-digit Hexadecimal Random Number>.zip`. Only the latest zip file is retained for the same date, and the original log files will be preserved.

**Backup Operation History:** After compression is completed, the latest `A_OperationHistory.txt` will be copied to the compression directory and renamed as `A_OperationHistory_<Timestamp>.txt`. Old backups will be cleaned up first.

## Ⅱ. How to use the application

### Start Logging

**Method 1:** Tap Start on the Main Interface

**Method 2:** Send the broadcast command:
```bash
adb shell am broadcast -a com.xcheng.xclogger.ADB_CMD --es cmd_name start_xc_log
```

### Stop Logging

**Method 1:** Tap Stop on the Main Interface

**Method 2:** Send the broadcast command:
```bash
adb shell am broadcast -a com.xcheng.xclogger.ADB_CMD --es cmd_name stop_xc_log
```

### View/Modify Configuration

Open the Configuration Interface (Config), adjust the storage capacity, single file size, retention duration, filter rules and log path, then save the changes.

### Configuration Parameter Description

| Parameter | Description |
|-----------|-------------|
| **Total Size (GB)** | The upper limit of the total file size for the current log storage path |
| **File Size (MB)** | The upper limit of the size for a single log file |
| **Buffer Size (Byte)** | The size of the log buffer area |
| **Log Directory** | The storage path of current log files |
| **Log Period (Hours)** | The maximum retention period of log files and log compressed packages |
| **Filter Tag** | Filters the TAG of each log line to determine whether to save the line. Multiple TAGs are supported, separated by an English half-width comma (no spaces before or after the comma). The relationship between multiple TAGs is **OR** |
| **Filter Level** | Filters the level of each log line to determine whether to save the line. (Priority order: f>e>w>i>d>v). Only one log level can be selected, and all logs with a level greater than or equal to the selected one will be retained |
| **Filter Package** | Filters the package name of each log line to determine whether to save the line. Multiple package names are supported, separated by an English half-width comma (no spaces before or after the comma). The relationship between multiple package names is **OR** |

**Filter Relationship:** The relationship among Filter Tag, Filter Level and Filter Package is **AND**. A log line will only be recorded when it meets the Filter Tag condition, the Filter Level condition and the Filter Package condition simultaneously.

### View Current Configuration

Open the Developer Interface (Developer) and tap Print Configuration.

### Reset to Default Configuration

Tap Reset on the Developer Interface.

### Manual Log Compression

Send the broadcast command:
```bash
adb shell am broadcast -a com.xcheng.xclogger.FILE_COMPRESS
```

> **Note:** A `COMPRESSING` response will be returned if compression is in progress.

### View Compression Results

Check the latest file named `yyyy_MMdd_<hash>.zip` and `A_OperationHistory_<ts>.txt` in the directory `/data/xclogger/mobilelog`.

### View Raw Logs

Open the log file named `mainlog_<index6>_yyyyMMdd_HHmmss_seq.txt` under the directory `/storage/emulated/0/XcLogger`.

### Audit Operation History

View the file `A_OperationHistory.txt` in `/storage/emulated/0/XcLogger` (or the historical backups in the compression directory).

### Force Restore Running State

If the service is abnormally stopped, tap Start again or send the start broadcast; the service will restart with the latest configuration applied.

### Check Expired Cleanup

After setting the parameter `log_period`, expired log files and expired zip packages will be automatically deleted upon the next new file generation (judgment is based on the date in the zip file name).
