# XcLogger 外部集成指南

> **最后更新**：2026-06-15  
> 本文档说明外部应用如何集成和控制 XcLogger，包含广播控制协议、AIDL 远程接口和 AAR 接入方式。

---

## 一、集成方式总览

XcLogger 支持两种外部控制方式：

| 方式 | 适用场景 | 特点 |
|------|---------|------|
| **广播协议** | ADB 命令、自动化脚本、简单 Demo | 无需绑定，一次性的控制操作 |
| **AIDL 绑定** | App 内集成、需要状态回调 | 双向通信，支持实时状态监听 |

所有操作结果（包括异步压缩结果）统一通过 `CTRL_RESULT` 广播返回。

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

**必填 Extra**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `op_type` | String | 操作类型，见下表 |

### 2.3 支持的 op_type

| op_type | 说明 | 额外参数 |
|---------|------|---------|
| `start` | 启动日志采集 | — |
| `stop` | 停止日志采集 | — |
| `restart` | 重启日志采集 | — |
| `update_config` | 部分更新配置 | 各配置字段见下方 |
| `query_status` | 查询运行状态 | — |
| `trigger_compress` | 触发压缩（可选时间范围） | `start_time` / `end_time`（格式 `yyyyMMddHHmmss`） |
| `upload_result` | 回传上传结果 | `success`（boolean） |
| `query_compress_status` | 查询压缩/上传状态 | — |
| `cancel_compress` | 取消压缩/上传并删除 zip | — |

### 2.4 配置更新参数

`update_config` 支持部分更新，只传需要修改的字段：

| Extra 字段 | 类型 | 对应配置 |
|-----------|------|---------|
| `total_size` | int | 总空间上限（GB） |
| `file_size` | int | 单文件上限（MB） |
| `buffer_size` | int | 缓冲区大小（byte） |
| `log_dir` | String | 日志存储目录 |
| `log_period` | int | 保留周期（小时） |
| `filter_tag` | String | Tag 过滤（逗号分隔，`all` 为不过滤） |
| `filter_level` | String | Level 过滤（`all` 为不过滤） |
| `filter_package` | String | 包名过滤（逗号分隔；以 `.` 结尾为前缀匹配；`all` 为不过滤） |

### 2.5 结果广播

**结果 Action**：`com.xcheng.xclogger.CTRL_RESULT`

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 操作是否成功 |
| `message` | String | 结果或错误说明 |
| `op_type` | String | 对应请求操作 |
| `running_state` | boolean | 日志采集当前状态 |
| `compress_state` | String | 压缩/上传状态 |
| `zip_files` | String | 当前待上传 zip（多个逗号分隔） |
| `retry_count` | int | 当前上传失败次数 |
| `max_retry_count` | int | 最大失败次数（当前为 3） |

**`compress_state` 取值**：

| 状态 | 说明 |
|------|------|
| `IDLE` | 空闲 |
| `COMPRESSING` | 压缩中 |
| `WAIT_UPLOAD_RESULT` | 等待上传结果 |
| `CANCELLING` | 取消中 |

### 2.6 常用示例

```bash
# 启动日志
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type start

# 停止日志
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type stop

# 查询状态
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_status

# 触发全量压缩
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type trigger_compress

# 按时间范围压缩（格式 yyyyMMddHHmmss）
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type trigger_compress \
  --es start_time "20260601135000" \
  --es end_time "20260602030000"

# 更新过滤配置
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type update_config \
  --es filter_tag "TagA,TagB" \
  --es filter_level "e"

# 更新包名过滤（前缀匹配）
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type update_config \
  --es filter_package "com.e2scorp.d300."

# 取消压缩
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type cancel_compress

# 回传上传结果（成功）
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type upload_result --ez success true
```

### 2.7 旧版 ADB 命令（兼容）

```bash
# 启动
adb shell am broadcast -a com.xcheng.xclogger.ADB_CMD --es cmd_name start_xc_log

# 停止
adb shell am broadcast -a com.xcheng.xclogger.ADB_CMD --es cmd_name stop_xc_log
```

---

## 三、AIDL 远程接口

### 3.1 绑定服务

XcLogger 暴露绑定 action：

```
com.xcheng.xclogger.REMOTE_BIND
```

绑定代码示例：

```java
Intent intent = new Intent("com.xcheng.xclogger.REMOTE_BIND");
intent.setPackage("com.xcheng.xclogger");
bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
```

### 3.2 AIDL 接口与 op_type 对照

| AIDL 方法 | 对应 op_type | 说明 |
|-----------|-------------|------|
| `startLogging()` | `start` | 启动日志采集 |
| `stopLogging()` | `stop` | 停止日志采集 |
| `isRunning()` | `query_status` | 查询运行状态 |
| `getConfiguration()` | — | 获取当前配置（直接读缓存） |
| `updateConfigurationPartial(config)` | `update_config` | 部分更新配置 |
| `triggerCompression()` | `trigger_compress` | 全量压缩 |
| `triggerCompressionWithRange(startTime, endTime)` | `trigger_compress` + `start_time`/`end_time` | 按时间范围压缩 |
| `reportUploadResult(success)` | `upload_result` | 回传上传结果 |
| `getCompressStatus()` | `query_compress_status` | 查询压缩/上传状态 |
| `cancelCompressTask()` | `cancel_compress` | 取消压缩/上传并删除 zip |
| `registerListener(listener)` | — | 注册状态监听器 |
| `unregisterListener(listener)` | — | 反注册监听器 |

### 3.3 IXcLoggerListener 回调

```java
interface IXcLoggerListener {
    // 日志状态变化通知 (0: Stopped, 1: Running)
    void onStatusChanged(int status);

    // 统一操作结果回调
    void onOperationResult(String opType, boolean success, String message, boolean runningState);

    // 压缩任务完成通知
    void onCompressFinished(boolean success, String message);

    // 压缩包已准备好，等待外部上传
    void onCompressReady(String zipFiles, int retryCount, int maxRetryCount);
}
```

### 3.4 使用示例

```java
// 绑定服务
private IXcLoggerService service;
private ServiceConnection connection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = IXcLoggerService.Stub.asInterface(binder);
    }
};

Intent intent = new Intent("com.xcheng.xclogger.REMOTE_BIND");
intent.setPackage("com.xcheng.xclogger");
bindService(intent, connection, Context.BIND_AUTO_CREATE);

// 调用控制
service.startLogging();
service.stopLogging();
boolean running = service.isRunning();
XcLoggerConfig config = service.getConfiguration();
service.updateConfigurationPartial(partialConfig);
service.triggerCompression();
service.reportUploadResult(true);

// 注册监听
service.registerListener(new IXcLoggerListener.Stub() {
    @Override public void onStatusChanged(int status) { }
    @Override public void onOperationResult(String opType, boolean success, String message, boolean runningState) { }
    @Override public void onCompressFinished(boolean success, String message) { }
    @Override public void onCompressReady(String zipFiles, int retryCount, int maxRetryCount) { }
});
```

### 3.5 AIDL 文件路径

AIDL 接口在 `app` 模块和 `xclogger-api` 模块中各有一份同名定义：

```
app/src/main/aidl/com/xcheng/xclogger/service/IXcLoggerService.aidl
app/src/main/aidl/com/xcheng/xclogger/service/IXcLoggerListener.aidl
app/src/main/aidl/com/xcheng/xclogger/util/XcLoggerConfig.aidl
```

---

## 四、AAR 集成方式

### 4.1 接入步骤

将 `xclogger_aar/xclogger-api-release.aar` 复制到外部 Demo 工程：

```text
app/libs/xclogger-api-release.aar
```

配置 Gradle：

```gradle
android {
    buildFeatures {
        aidl true
    }
}

dependencies {
    implementation files('libs/xclogger-api-release.aar')
}
```

AAR 内包含：

- `com.xcheng.xclogger.service.IXcLoggerService`
- `com.xcheng.xclogger.service.IXcLoggerListener`
- `com.xcheng.xclogger.util.XcLoggerConfig`（Parcelable）

### 4.2 XcLoggerConfig 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalSizeGb` | int | 日志总空间上限（GB） |
| `fileSizeMb` | int | 单文件上限（MB） |
| `bufferSizeBytes` | int | 缓冲区大小（byte） |
| `logDir` | String | 日志存储目录 |
| `logPeriodHours` | int | 保留周期（小时） |
| `filterTag` | String | Tag 过滤（逗号分隔，`all` 不过滤） |
| `filterLevel` | String | Level 过滤（`all` 不过滤） |
| `filterPackage` | String | 包名过滤（逗号分隔；`.` 结尾为前缀匹配；`all` 不过滤） |

### 4.3 压缩流程说明

`triggerCompression()` 是**强制重开**语义：

```
外部 Demo 调用 triggerCompression()
  → 若旧流程未结束：先取消旧流程并清理旧 zip
  → 切换当前日志文件（封口快照）
  → 压缩旧日志快照
  → 输出到 /data/xclogger/mobilelog/yyyy_MMdd_HHmmss_xxxx.zip
  → 通知 onCompressReady(zipFiles, retryCount, maxRetryCount)
  → 等待 reportUploadResult()
    → true: XCLogger 删除 zip，流程结束
    → false（未满 3 次）: 外部重试上传
    → false（第 3 次）: XCLogger 强制删除 zip
```

### 4.4 时间范围压缩

`triggerCompressionWithRange(startTime, endTime)` 参数说明：

| 参数 | 行为 |
|------|------|
| 只传 `startTime` | 从该时间点的最后一个文件到最新文件 |
| 只传 `endTime` | 从最旧文件到该时间点的最后一个文件 |
| 均传 | 取覆盖区间的最小连续文件段 |
| 均不传 | 全量压缩（兼容旧行为） |

---

## 五、常见问题

### 5.1 为什么收不到 PACKAGE_ADDED 广播？

`PACKAGE_ADDED` / `PACKAGE_REMOVED` / `PACKAGE_REPLACED` 三个广播携带 `package:` scheme 的 Intent data。Manifest 中必须声明 `<data android:scheme="package" />` 才能匹配。三个 action 可以合并在同一个 `<intent-filter>` 中。

### 5.2 新安装的包日志没有记录？

XcLogger 的包过滤在启动时全量扫描已安装应用。新安装的应用会通过以下方式被发现：

1. **PACKAGE_ADDED 广播**（设备支持时即刻触发）
2. **10 秒轮询**（readLogcatOutput 线程自动刷新，不受设备限制）

两种方式都即时生效，无需重启服务。

### 5.3 压缩结果如何确认？

`triggerCompression()` 返回 `true` 仅表示请求受理，不代表压缩已完成。最终结果通过以下方式获取：

- **广播方式**：监听 `CTRL_RESULT` 回执
- **AIDL 方式**：`IXcLoggerListener.onCompressReady()` / `onCompressFinished()` 回调



## 六、版本信息

- **文档版本**：1.0
- **AIDL 接口版本**：v2（含 `triggerCompressionWithRange`、`cancelCompressTask`）
- **最低 SDK**：API 29 (Android 10)
- **目标 SDK**：API 33 (Android 13)
