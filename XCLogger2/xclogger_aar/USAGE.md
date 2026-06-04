# XCLogger AAR 使用说明

本文档说明外部 Android Demo 如何集成 `xclogger-api-release.aar`，并通过 AIDL 控制 XCLogger。

## 1. 产物说明

当前目录包含：

- `xclogger-api-release.aar`：AIDL 接口库
- `USAGE.md`：本文档

AAR 内包含：

- `com.xcheng.xclogger.service.IXcLoggerService`
- `com.xcheng.xclogger.service.IXcLoggerListener`
- `com.xcheng.xclogger.util.XcLoggerConfig`（Parcelable）

---

## 2. 外部工程接入

将 `xclogger-api-release.aar` 复制到外部 Demo 工程模块：

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

---

## 3. 绑定 XCLogger 服务

XCLogger 暴露绑定 action：

```text
com.xcheng.xclogger.REMOTE_BIND
```

外部 Demo 使用显式 Intent 绑定：

```java
Intent intent = new Intent("com.xcheng.xclogger.REMOTE_BIND");
intent.setPackage("com.xcheng.xclogger");
bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
```

在 `onServiceConnected` 中获取接口：

```java
IXcLoggerService service = IXcLoggerService.Stub.asInterface(iBinder);
```

---

## 4. AIDL 接口说明

### 4.1 日志控制

```java
boolean startLogging();
boolean stopLogging();
boolean isRunning();
```

### 4.2 配置控制

```java
XcLoggerConfig getConfiguration();
boolean updateConfigurationPartial(XcLoggerConfig config);
```

### 4.3 压缩与上传确认

```java
boolean triggerCompression();
boolean reportUploadResult(boolean success);
String getCompressStatus();
boolean cancelCompressTask();
```

| 方法 | 说明 |
|---|---|
| `triggerCompression()` | 强制开始新一轮压缩上传流程。返回 `true` 表示请求已提交，不代表压缩已完成。实际结果通过 `onCompressReady` / `onCompressFinished` 回调获取。 |
| `reportUploadResult(true)` | 通知上传成功，XCLogger 删除本次 zip。 |
| `reportUploadResult(false)` | 通知上传失败。失败未满 3 次时外部 Demo 应重试上传；第 3 次失败后 XCLogger 强制删除 zip。 |
| `getCompressStatus()` | 查询压缩/上传状态。 |
| `cancelCompressTask()` | 强制取消当前压缩/上传状态，并删除已生成或待上传 zip。 |

`triggerCompression()` 的语义是强制重开：如果当前已有压缩未结束，或已有 zip 正在等待上传结果，XCLogger 会先停止旧流程、清理旧 zip，然后重新开始压缩。

---

## 5. Listener 回调

注册监听：

```java
service.registerListener(listener);
service.unregisterListener(listener);
```

回调方法：

```java
void onStatusChanged(int status);
void onOperationResult(String opType, boolean success, String message, boolean runningState);
void onCompressFinished(boolean success, String message);
void onCompressReady(String zipFiles, int retryCount, int maxRetryCount);
```

| 回调 | 说明 |
|---|---|
| `onStatusChanged` | 日志状态变化，`0=Stopped`，`1=Running`。 |
| `onOperationResult` | AIDL 操作结果，例如上传结果处理、取消压缩等。 |
| `onCompressFinished` | 压缩动作完成/失败通知。 |
| `onCompressReady` | zip 已生成，等待外部 Demo 上传。 |

---

## 6. 压缩上传推荐流程

```text
外部 Demo 调用 triggerCompression()
  -> 如果旧流程未结束，XCLogger 先取消旧流程并清理旧 zip
  -> XCLogger 切换当前日志文件，压缩旧日志快照
  -> XCLogger 回调 onCompressReady(zipFiles, 0, 3)
  -> Demo 上传 zipFiles
  -> 上传成功：调用 reportUploadResult(true)
  -> 上传失败：调用 reportUploadResult(false)
```

上传失败处理：

```text
第 1 次失败：Demo 收到 onOperationResult(op_type=upload_result, success=false, ...)
            可调用 getCompressStatus() 确认 state=WAIT_UPLOAD_RESULT 后重试上传
第 2 次失败：同上
第 3 次失败：Demo 收到 onOperationResult(op_type=upload_result, success=false, ...)
            此时 state=IDLE，zip_files 为空，XCLogger 已删除 zip
```

查询状态：

```java
String status = service.getCompressStatus();
```

返回示例：

```text
state=WAIT_UPLOAD_RESULT;zip_files=/data/xclogger/mobilelog/2026_0521_235959_ab12.zip;retry_count=1;max_retry_count=3
```

强制退出：

```java
service.cancelCompressTask();
```

---

## 7. 压缩 zip 命名规则

XCLogger 按日志日期分组压缩，同一天日志生成一个 zip。文件名格式：

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

## 8. Demo 代码骨架

```java
private IXcLoggerService service;

private final IXcLoggerListener listener = new IXcLoggerListener.Stub() {
    @Override
    public void onStatusChanged(int status) {
        // 0=Stopped, 1=Running
    }

    @Override
    public void onOperationResult(String opType, boolean success, String message, boolean runningState) {
        // upload_result / cancel_compress 等结果
    }

    @Override
    public void onCompressFinished(boolean success, String message) {
        // 压缩动作完成/失败通知
    }

    @Override
    public void onCompressReady(String zipFiles, int retryCount, int maxRetryCount) {
        boolean uploadOk = uploadFiles(zipFiles);
        try {
            service.reportUploadResult(uploadOk);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
};
```

触发压缩：

```java
service.triggerCompression();
```

如果再次调用 `triggerCompression()`，会强制取消旧压缩/上传流程并重新开始。

---

## 9. 行为约束

- 压缩时不会停止日志采集。
- 如果日志正在运行，会先封口当前日志文件，再新建下一个日志文件继续写。
- 本次压缩只压缩封口前的日志快照，不压缩新文件。
- 同一时间只允许一个实际压缩线程。
- 再次调用 `triggerCompression()` 会强制停止旧压缩上传流程并重新开始。
- 新压缩开始前会清理旧 zip。
- 上传成功后 XCLogger 删除 zip。
- 上传失败未满 3 次时，外部 Demo 应主动重试上传。
- 上传失败达到 3 次后 XCLogger 强制删除 zip。
- `cancelCompressTask()` 会删除已生成或待上传 zip，并清理状态。
- 关键操作会记录到 `A_OperationHistory.txt`。
