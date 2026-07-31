# XCLogger AAR API 参考文档

> **版本**：v1.3.6 / 配置协议 API 4
> **更新日期**：2026-07-31
> **适用平台**：Android 10（API 29）及以上

{全局维护逻辑：本文只描述当前 AAR 的可调用能力。新增接口先更新“接口速查”，再更新可移植示例和注意事项；兼容接口不得与推荐接口混排；中英文版必须保持相同章节结构和行为结论。}

---

## 1. 概述

{维护逻辑：本节只保留识别和接入 AAR 所需的稳定信息，不记录工程目录、客户 flavor 或具体安装包名。}

XCLogger 通过预编译 AAR 提供 AIDL 接口。App 连接设备上已安装的 XCLogger 后，可以启停日志、读取和修改配置、压缩日志、接收状态通知，并通过文件管道导出 ZIP。

| 项目 | 值 |
|---|---|
| 通信方式 | Android AIDL 服务绑定 |
| AAR 包名 | `xclogger-api-release.aar` |
| 服务 Action | `com.xcheng.xclogger.REMOTE_BIND` |
| 最低支持 SDK | API 29 |

> **访问提示**：XCLogger 服务允许其他 App 连接，目前没有额外的绑定权限检查。连接时必须指定设备上 XCLogger 的真实包名，避免连到错误应用。连接成功只说明服务存在，不代表调用者身份已经校验；量产版本建议增加签名权限或调用 UID 检查。

---

## 2. 接口速查

{维护逻辑：先展示可直接采用的绑定方式，再按“当前推荐接口 → 兼容接口”排序。新增方法必须写出完整签名、参数和返回语义。}

### 2.1 服务绑定

{维护逻辑：连接示例必须自包含。XCLogger 包名由构造函数传入；Context、ServiceConnection、服务对象、API 版本和解绑状态全部在类内定义。}

下面的 `LoggerServiceConnector` 可以直接复制。只需要把 `com.vendor.logger` 替换成设备上 XCLogger 的真实包名。

```java
package com.example.logclient;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import com.xcheng.xclogger.service.IXcLoggerService;

import java.util.concurrent.Executor;

public final class LoggerServiceConnector implements AutoCloseable {
    public interface ConnectionEvents {
        void onConnected(int apiVersion);
        void onDisconnected();
        void onConnectionError(Exception error);
    }

    private static final String SERVICE_ACTION =
            "com.xcheng.xclogger.REMOTE_BIND";

    private final Context appContext;
    private final String loggerPackageName;
    private final Executor callbackExecutor;
    private final ConnectionEvents events;
    private volatile IXcLoggerService service;
    private volatile int apiVersion = -1;
    private boolean bindRequested;

    public LoggerServiceConnector(
            Context context,
            String loggerPackageName,
            Executor callbackExecutor,
            ConnectionEvents events) {
        this.appContext = context.getApplicationContext();
        this.loggerPackageName = loggerPackageName;
        this.callbackExecutor = callbackExecutor;
        this.events = events;
    }

    public boolean bind() {
        if (bindRequested) return true;
        Intent intent = new Intent(SERVICE_ACTION);
        intent.setPackage(loggerPackageName);
        bindRequested = appContext.bindService(
                intent, connection, Context.BIND_AUTO_CREATE);
        return bindRequested;
    }

    public boolean isBound() {
        return service != null;
    }

    public int getApiVersion() {
        return apiVersion;
    }

    public IXcLoggerService requireService() {
        IXcLoggerService current = service;
        if (current == null) {
            throw new IllegalStateException("Logger service is not bound");
        }
        return current;
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            IXcLoggerService connected = IXcLoggerService.Stub.asInterface(binder);
            try {
                int connectedApiVersion = connected.getApiVersion();
                service = connected;
                apiVersion = connectedApiVersion;
                callbackExecutor.execute(
                        () -> events.onConnected(connectedApiVersion));
            } catch (RemoteException error) {
                service = null;
                apiVersion = -1;
                callbackExecutor.execute(() -> events.onConnectionError(error));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            apiVersion = -1;
            callbackExecutor.execute(events::onDisconnected);
        }
    };

    @Override
    public void close() {
        if (bindRequested) {
            appContext.unbindService(connection);
            bindRequested = false;
        }
        service = null;
        apiVersion = -1;
    }
}
```

`callbackExecutor` 决定连接结果在哪个线程执行。Activity 直接传 `getMainExecutor()`，回调就会回到主线程；`events` 是上面已经定义的 `ConnectionEvents` 实现。

Activity 中的最小调用方式：

```java
package com.example.logclient;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public final class MainActivity extends Activity {
    private LoggerServiceConnector connector;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        connector = new LoggerServiceConnector(
                this,
                "com.vendor.logger", // 替换为设备上 XCLogger 的真实包名
                getMainExecutor(),
                new LoggerServiceConnector.ConnectionEvents() {
                    @Override
                    public void onConnected(int apiVersion) {
                        Log.i("LoggerClient", "Connected, API=" + apiVersion);
                    }

                    @Override
                    public void onDisconnected() {
                        Log.w("LoggerClient", "Disconnected");
                    }

                    @Override
                    public void onConnectionError(Exception error) {
                        Log.e("LoggerClient", "Connection failed", error);
                    }
                });
        if (!connector.bind()) {
            throw new IllegalStateException("Logger service was not found");
        }
    }

    @Override
    protected void onDestroy() {
        connector.close();
        super.onDestroy();
    }
}
```

### 2.2 方法一览

{维护逻辑：API 4 仍在使用的方法放在上半部分；只为旧客户端保留的接口放在下半部分并明确“不推荐”。}

#### 当前推荐接口

| 完整方法签名 | 参数 | 返回值与行为 |
|---|---|---|
| `int getApiVersion()` | 无 | 当前服务返回 `4`；绑定后应首先调用 |
| `String getPackageFilterMode()` | 无 | 返回 `off`、`whitelist` 或 `blacklist` |
| `void updateConfiguration2(XcLoggerConfig2 update, IXcLoggerConfigUpdateCallback callback)` | `update`：直接承载基础字段、名单 replace/add/remove 和可选的 `packageFilterMode`；<br>`callback`：成功状态、失败原因和变更字段 | 唯一的异步结构化更新入口；推荐通过 `XcLoggerConfigUpdater` 调用 |
| `XcLoggerConfig getConfiguration()` | 无 | 返回当前配置副本；配置尚未加载时可能为 `null` |
| `boolean startLogging()` | 无 | 启动采集；`true` 表示启动成功 |
| `boolean stopLogging()` | 无 | 停止采集；`true` 表示停止成功 |
| `boolean isRunning()` | 无 | 返回当前持久化运行状态 |
| `boolean triggerCompression()` | 无 | 请求无时间范围压缩；`true` 仅表示请求已受理 |
| `boolean triggerCompressionWithRange(String startTime, String endTime)` | 时间格式 `yyyyMMddHHmmss`；当前应同时提供有效且有序的两个边界 | 请求范围压缩；最终结果通过回调或状态查询获得 |
| `String getCompressStatus()` | 无 | 返回分号分隔状态，包含 `state`、`zip_files`、`retry_count`、`max_retry_count` |
| `ParcelFileDescriptor getLogZip()` | 无 | 仅在 `WAIT_UPLOAD_RESULT` 可用；输出待上传列表中的第一份 ZIP |
| `boolean reportUploadResult(boolean success)` | 所有需要上传的 ZIP 都成功后传 `true`，上传失败传 `false` | `true` 会删除待上传 ZIP；连续第 3 次失败也会删除 ZIP |
| `boolean cancelCompressTask()` | 无 | 取消当前压缩或清理待上传任务 |
| `void registerListener(IXcLoggerListener listener)` | 回调对象 | 接收状态、操作和压缩结果 |
| `void unregisterListener(IXcLoggerListener listener)` | 注册时使用的同一个回调对象 | Activity 或 Service 销毁前注销 |

#### 兼容接口（不推荐新代码使用）

| 完整方法签名 | 兼容目的 | 不推荐原因 |
|---|---|---|
| ~~`boolean updateConfigurationPartial(XcLoggerConfig config)`~~ | 支持旧客户端的非空 patch 更新 | 无结构化结果，不能原子表达列表 add/remove 和包过滤三态；新代码应使用 `XcLoggerConfigUpdater` |

### 2.3 回调注册与参数

{维护逻辑：先给出完整注册代码，再逐项解释每个回调的所有参数；禁止用省略号代替签名或参数。}

```java
package com.example.logclient;

import com.xcheng.xclogger.service.IXcLoggerListener;

import java.util.concurrent.Executor;

public final class LoggerRemoteListener extends IXcLoggerListener.Stub {
    public interface Events {
        void onStatusChanged(int status);
        void onOperationResult(String opType, boolean success,
                               String message, boolean runningState);
        void onCompressFinished(boolean success, String message);
        void onCompressReady(String zipFiles, int retryCount, int maxRetryCount);
    }

    private final Executor callbackExecutor;
    private final Events events;

    public LoggerRemoteListener(Executor callbackExecutor, Events events) {
        this.callbackExecutor = callbackExecutor;
        this.events = events;
    }

    @Override
    public void onStatusChanged(int status) {
        callbackExecutor.execute(() -> events.onStatusChanged(status));
    }

    @Override
    public void onOperationResult(
            String opType,
            boolean success,
            String message,
            boolean runningState) {
        callbackExecutor.execute(() -> events.onOperationResult(
                opType, success, message, runningState));
    }

    @Override
    public void onCompressFinished(boolean success, String message) {
        callbackExecutor.execute(() -> events.onCompressFinished(success, message));
    }

    @Override
    public void onCompressReady(
            String zipFiles,
            int retryCount,
            int maxRetryCount) {
        callbackExecutor.execute(() -> events.onCompressReady(
                zipFiles, retryCount, maxRetryCount));
    }
}
```

下面展示一个完整的 Activity 片段。`connector`、`remoteListener` 和回调处理器都在类内声明；请在 2.1 的 `ConnectionEvents.onConnected()` 中调用 `registerRemoteListener()`：

```java
package com.example.logclient;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

public final class ListenerActivity extends Activity {
    private LoggerServiceConnector connector;
    private LoggerRemoteListener remoteListener;

    private final LoggerRemoteListener.Events eventHandler = new LoggerRemoteListener.Events() {
    @Override
    public void onStatusChanged(int status) {
        Log.i("LoggerClient", "status=" + status);
    }

    @Override
    public void onOperationResult(String opType, boolean success,
                                  String message, boolean runningState) {
        Log.i("LoggerClient", "op=" + opType + ", success=" + success
                + ", message=" + message + ", running=" + runningState);
    }

    @Override
    public void onCompressFinished(boolean success, String message) {
        Log.i("LoggerClient", "compressFinished=" + success
                + ", message=" + message);
    }

    @Override
    public void onCompressReady(String zipFiles, int retryCount,
                                int maxRetryCount) {
        Log.i("LoggerClient", "zipFiles=" + zipFiles
                + ", retry=" + retryCount + "/" + maxRetryCount);
    }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        connector = new LoggerServiceConnector(
                this,
                "com.vendor.logger", // 替换为设备上 XCLogger 的真实包名
                getMainExecutor(),
                new LoggerServiceConnector.ConnectionEvents() {
                    @Override
                    public void onConnected(int apiVersion) {
                        registerRemoteListener();
                    }

                    @Override
                    public void onDisconnected() {
                        remoteListener = null;
                    }

                    @Override
                    public void onConnectionError(Exception error) {
                        Log.e("LoggerClient", "Connection failed", error);
                    }
                });
        if (!connector.bind()) {
            throw new IllegalStateException("Logger service was not found");
        }
    }

    private void registerRemoteListener() {
        remoteListener = new LoggerRemoteListener(getMainExecutor(), eventHandler);
        try {
            connector.requireService().registerListener(remoteListener);
        } catch (RemoteException error) {
            remoteListener = null;
            Log.e("LoggerClient", "Listener registration failed", error);
        }
    }

    @Override
    protected void onDestroy() {
        if (remoteListener != null && connector.isBound()) {
            try {
                connector.requireService().unregisterListener(remoteListener);
            } catch (RemoteException error) {
                Log.w("LoggerClient", "Listener cleanup failed", error);
            }
        }
        remoteListener = null;
        connector.close();
        super.onDestroy();
    }
}
```

| 完整回调签名 | 参数详细说明 | 使用方式 |
|---|---|---|
| `void onStatusChanged(int status)` | `status`：`0` 表示已停止，`1` 表示运行中 | 更新 UI 或业务状态缓存；不要在 Binder 回调线程直接操作 View |
| `void onOperationResult(String opType, boolean success, String message, boolean runningState)` | `opType`：完成的控制类型；<br>`success`：该操作最终是否成功；<br>`message`：成功说明或错误原因；<br>`runningState`：操作完成后的采集运行状态 | 用于确认启停、配置、上传结果和取消任务等操作 |
| `void onCompressFinished(boolean success, String message)` | `success`：压缩流程是否成功结束；<br>`message`：压缩结果、无文件或失败原因 | 表示压缩流程结束；是否存在可读取 ZIP 仍应结合 `onCompressReady` 或状态查询判断 |
| `void onCompressReady(String zipFiles, int retryCount, int maxRetryCount)` | `zipFiles`：逗号分隔的内部 ZIP 路径列表；<br>`retryCount`：当前上传失败次数；<br>`maxRetryCount`：最大失败次数，当前为 `3` | 这是调用 `getLogZip()` 的首选就绪信号；不要直接跨应用读取 `zipFiles` 路径 |

### 2.4 `XcLoggerConfig` 字段

{维护逻辑：活跃字段必须在上表；兼容或无运行效果的字段必须放在下表并使用删除线标记，禁止把兼容字段描述成可用过滤能力。}

#### 当前活跃字段

| 字段 | 类型 | 当前用途 |
|---|---|---|
| `totalSizeMb` | `int` | 日志总空间上限，单位 MB |
| `fileSizeMb` | `int` | 单文件上限，单位 MB，不能大于总空间 |
| `bufferSizeBytes` | `int` | 写缓冲大小；512～4096，且为 512 的倍数 |
| `logDir` | `String` | 日志缓存目录 |
| `logPeriodHours` | `int` | 日志保留时间，单位小时 |
| `filterTag` | `String` | Tag 白名单；`all` 表示不限制 Tag |
| `filterLevel` | `String` | `f/e/w/i/d/v`；默认和推荐的“不按 Level 丢弃”值为 `v` |
| `filterPackage` | `String` | Package 白名单；仅在 `WHITELIST` 模式生效 |
| `filterPackageBlacklist` | `String` | Package 黑名单；仅在 `BLACKLIST` 模式生效 |

包过滤模式不在旧的 Parcelable 中。Parcelable 是 Android 在两个进程之间传递配置对象时使用的固定数据格式。请通过 `getPackageFilterMode()` 查询模式，并通过 updater 的 `packageFilterMode()` 修改。`OFF` 为默认模式，保留两套名单但不执行包过滤。

#### 兼容字段（不推荐使用）

| 字段 | 保留原因 | 当前行为 |
|---|---|---|
| ~~`filterTagBlacklist`~~ | Parcelable、XML 和数据库兼容 | 不由 updater 修改，不参与采集 |
| ~~`filterLevelBlacklist`~~ | 历史数据兼容 | 不参与采集 |
| ~~`filterContent`~~ | 历史数据兼容 | 不参与内容过滤 |
| ~~`filterContentBlacklist`~~ | 历史数据兼容 | 不参与内容过滤 |

### 2.5 链式配置更新

{维护逻辑：本节是配置更新的唯一推荐入口；接口表、约束、结果码和示例必须随 `XcLoggerConfigUpdater` 源码同步更新。}

#### 可用链式方法

| 类别 | 方法 |
|---|---|
| 数值/路径 | `totalSizeMb()`、`fileSizeMb()`、`bufferSizeBytes()`、`logDir()`、`logPeriodHours()` |
| Level | `filterLevel(LogLevel)`、`filterLevel(String)` |
| Tag 白名单 | `filterTags()`、`filterTagsCsv()`、`addTag()`、`removeTag()` |
| Package 白名单 | `filterPackages()`、`filterPackagesCsv()`、`allPackages()`、`addPackage()`、`removePackage()` |
| Package 黑名单 | `blacklistPackages()`、`blacklistPackagesCsv()`、`addBlacklistedPackage()`、`removeBlacklistedPackage()`、`clearPackageBlacklist()` |
| Package 模式 | `packageFilterMode(OFF)`、`packageFilterMode(WHITELIST)`、`packageFilterMode(BLACKLIST)` |
| 提交 | `commitAsync()`；工作线程可使用阻塞式 `commit()` |

#### replace/add/remove 语义

- `filterTags(values)`、`filterPackages(values)`、`blacklistPackages(values)` 是全量替换。
- `add*()` 和 `remove*()` 在服务端串行队列中基于最新配置执行，不依赖客户端旧快照。
- Tag/Package 白名单删除最后一项后恢复为 `all`；Package 黑名单删除最后一项后为空。
- 单个 Tag 最长 64 字符；Tag/Package 列表最多 64 项。
- Package 支持精确包名和以 `.` 结尾的前缀；服务端使用 UID 主判据和 PID 后备判据。
- updater 是一次性对象；提交后继续修改或再次提交会抛出异常。

#### 推荐示例

本示例由三个明确的部分组成：第 4.1 节的 `LoggerClient.java` 负责连接 XCLogger；下面的 `XcLoggerTestService.java` 创建并保存该对象；随后 `ConfigActivity.java` 通过 `getClient()` 取回同一个对象。复制本节前，应先把第 4.1 节的 `LoggerClient.java` 放入相同包目录。

```java
package com.example.logclient;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public final class XcLoggerTestService extends Service {
    public static final String EXTRA_XCLOGGER_PACKAGE = "xclogger_package";
    private static volatile LoggerClient client;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String xcLoggerPackage = intent == null
                ? null : intent.getStringExtra(EXTRA_XCLOGGER_PACKAGE);
        if (xcLoggerPackage == null || xcLoggerPackage.trim().isEmpty()) {
            throw new IllegalArgumentException("XCLogger package name is required");
        }
        if (client == null) {
            LoggerClient newClient = new LoggerClient(
                    this, xcLoggerPackage, getMainExecutor(), new LogEvents());
            if (!newClient.bind()) {
                newClient.close();
                throw new IllegalStateException("XCLogger service was not found");
            }
            client = newClient;
        }
        return START_NOT_STICKY;
    }

    public static LoggerClient getClient() {
        return client;
    }

    @Override
    public void onDestroy() {
        if (client != null) {
            client.close();
            client = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final class LogEvents implements LoggerClient.Events {
        @Override public void onConnected(int apiVersion) {
            Log.i("LoggerClient", "Connected, API=" + apiVersion);
        }
        @Override public void onDisconnected() {
            Log.w("LoggerClient", "Disconnected");
        }
        @Override public void onStatusChanged(int status) {
            Log.i("LoggerClient", "status=" + status);
        }
        @Override public void onOperationResult(String opType, boolean success,
                                                String message, boolean runningState) {
            Log.i("LoggerClient", "op=" + opType + ", success=" + success
                    + ", message=" + message + ", running=" + runningState);
        }
        @Override public void onCompressFinished(boolean success, String message) {
            Log.i("LoggerClient", "compressFinished=" + success
                    + ", message=" + message);
        }
        @Override public void onCompressReady(String zipFiles, int retryCount,
                                              int maxRetryCount) {
            Log.i("LoggerClient", "zipFiles=" + zipFiles
                    + ", retry=" + retryCount + "/" + maxRetryCount);
        }
    }
}
```

Demo 仓库中的对应封装类名是 `XcLoggerClient`；本文的可移植版本名为 `LoggerClient`，完整源码在第 4.1 节。下面的 Activity 展示 Service 如何启动、`client` 如何取得，以及配置更新如何发起：

```java
package com.example.logclient;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.xcheng.xclogger.util.XcLoggerConfigUpdater;

public final class ConfigActivity extends Activity {
    private static final int MAX_BIND_CHECKS = 25;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent serviceIntent = new Intent(this, XcLoggerTestService.class);
        serviceIntent.putExtra(
                XcLoggerTestService.EXTRA_XCLOGGER_PACKAGE,
                "com.vendor.logger"); // 替换为设备上 XCLogger 的真实包名
        startService(serviceIntent);
        waitForClient(0);
    }

    private void waitForClient(int checkCount) {
        LoggerClient client = XcLoggerTestService.getClient();
        if (client != null && client.isBound()) {
            applyConfiguration(client);
            return;
        }
        if (checkCount >= MAX_BIND_CHECKS) {
            Log.e("LoggerConfig", "XCLogger connection timed out");
            return;
        }
        mainHandler.postDelayed(() -> waitForClient(checkCount + 1), 200);
    }

    private void applyConfiguration(LoggerClient client) {
        XcLoggerConfigUpdater updater = client.configUpdater()
                .totalSizeMb(1024)
                .fileSizeMb(4)
                .bufferSizeBytes(2048)
                .logPeriodHours(168)
                .filterTags("ActivityManager", "WindowManager")
                .addTag("VendorService")
                .filterLevel(XcLoggerConfigUpdater.LogLevel.VERBOSE)
                .filterPackages("com.vendor.payment", "com.vendor.agent.")
                .blacklistPackages("com.vendor.noisy")
                .packageFilterMode(
                        XcLoggerConfigUpdater.PackageFilterMode.WHITELIST);

        String requestId = updater.commitAsync(result -> {
            if (result.isSuccess()) {
                Log.i("LoggerConfig", "request=" + result.getRequestId()
                        + ", changed=" + result.getChangedFields()
                        + ", restarted=" + result.isServiceRestarted());
            } else {
                Log.e("LoggerConfig", "request=" + result.getRequestId()
                        + ", status=" + result.getStatus()
                        + ", message=" + result.getMessage());
            }
        });
        Log.d("LoggerConfig", "submitted request=" + requestId);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
```

`XcLoggerTestService` 还必须按第 4.5 节写入 Manifest。示例没有在 `ConfigActivity.onDestroy()` 中停止 Service，因为该 Service 用于让多个页面共用同一个 `client`；App 不再需要日志功能时，再由统一的生命周期管理处停止它。

#### 结果状态

| 状态 | 含义 |
|---|---|
| `SUCCESS (0)` | 配置已提交 |
| `NO_CHANGES (1)` | 请求合法，但最终值没有变化；`isSuccess()` 仍为 `true` |
| `INVALID_ARGUMENT (2)` | 数值、Tag、Package、Level 或交叉字段校验失败 |
| `NOT_BOUND (3)` | 服务未绑定 |
| `UNSUPPORTED_SERVICE_VERSION (4)` | 服务协议版本不足；`updateConfiguration2` 要求 API 4 |
| `PERSIST_FAILED (5)` | 配置持久化失败 |
| `APPLIED_RESTART_FAILED (6)` | 配置已落盘，但运行中服务重启失败 |
| `REMOTE_ERROR (7)` | 跨进程调用失败 |
| `TIMEOUT_PENDING (8)` | 阻塞提交超时，请求仍可能在服务端完成 |
| `INTERNAL_ERROR (9)` | 未分类内部错误 |

---

## 3. 集成到 App

{维护逻辑：目录、Gradle、Manifest 三部分必须同时维护；示例包名仅作占位，必须替换为设备上 XCLogger 的真实包名。}

### 3.1 推荐目录结构

{维护逻辑：AAR 与封装代码分离；业务页面只依赖封装层，不直接持有 Binder 细节。}

```text
app/
├── libs/
│   └── xclogger-api-release.aar
└── src/main/
    ├── AndroidManifest.xml
    └── java/com/example/logclient/
        ├── LoggerServiceConnector.java # 仅需要基础绑定时使用
        ├── LoggerRemoteListener.java   # 独立的远程回调封装
        ├── LoggerClient.java          # 连接服务、接收回调、更新配置、复制 ZIP
        ├── XcLoggerTestService.java   # 创建并保存全局共用的 LoggerClient
        ├── CaptureController.java     # start/stop/isRunning
        ├── ConfigController.java      # getConfiguration + fluent updater
        ├── CompressionController.java # trigger/status/fetch/report/cancel
        └── MainActivity.java          # 启动 Service 并组装三个 Controller
```

### 3.2 Gradle

{维护逻辑：版本升级只替换 AAR 文件；不要同时复制同名 AIDL/Parcelable 源码，避免重复类或布局不一致。}

```gradle
android {
    defaultConfig {
        minSdk 29
    }
    buildFeatures {
        aidl true
    }
}

dependencies {
    implementation files('libs/xclogger-api-release.aar')
}
```

### 3.3 Manifest 包可见性

{维护逻辑：Android 11 及以上必须声明设备上 XCLogger 的包名；示例值必须替换。}

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <queries>
        <package android:name="com.vendor.logger" />
    </queries>
</manifest>
```

---

## 4. 可移植完整示例

{维护逻辑：本章按 Demo 的职责边界组织。公共客户端封装必须先可独立复制，再展示启停、配置和压缩模块；代码变化后注意事项必须同步。}

### 4.1 `LoggerClient`：连接服务、接收回调和更新配置

{维护逻辑：该类统一负责连接和断开服务、注册回调、调用配置接口；页面不要重复处理这些细节。}

下面代码省略了 UI，仅依赖 Android SDK 和 AAR，可作为集成封装的主体：

```java
package com.example.logclient;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import com.xcheng.xclogger.service.IXcLoggerConfigUpdateCallback;
import com.xcheng.xclogger.service.IXcLoggerListener;
import com.xcheng.xclogger.service.IXcLoggerService;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerConfigUpdateResult;
import com.xcheng.xclogger.util.XcLoggerConfig2;
import com.xcheng.xclogger.util.XcLoggerConfigUpdater;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;

public final class LoggerClient implements AutoCloseable {
    public interface Events {
        void onConnected(int apiVersion);
        void onDisconnected();
        void onStatusChanged(int status);
        void onOperationResult(String opType, boolean success,
                               String message, boolean runningState);
        void onCompressFinished(boolean success, String message);
        void onCompressReady(String zipFiles, int retryCount, int maxRetryCount);
    }

    private static final String SERVICE_ACTION = "com.xcheng.xclogger.REMOTE_BIND";

    private final Context appContext;
    private final String loggerPackageName;
    private final Executor callbackExecutor;
    private final Events events;
    private volatile IXcLoggerService service;
    private volatile int apiVersion = -1;
    private boolean bindRequested;

    public LoggerClient(Context context, String loggerPackageName,
                        Executor callbackExecutor, Events events) {
        this.appContext = context.getApplicationContext();
        this.loggerPackageName = loggerPackageName;
        this.callbackExecutor = callbackExecutor;
        this.events = events;
    }

    public boolean bind() {
        if (bindRequested) return true;
        Intent intent = new Intent(SERVICE_ACTION).setPackage(loggerPackageName);
        bindRequested = appContext.bindService(
                intent, connection, Context.BIND_AUTO_CREATE);
        return bindRequested;
    }

    public boolean isBound() {
        return service != null;
    }

    public int getApiVersion() {
        return apiVersion;
    }

    public boolean startLogging() throws RemoteException {
        return requireService().startLogging();
    }

    public boolean stopLogging() throws RemoteException {
        return requireService().stopLogging();
    }

    public boolean isRunning() throws RemoteException {
        return requireService().isRunning();
    }

    public XcLoggerConfig getConfiguration() throws RemoteException {
        return requireService().getConfiguration();
    }

    public String getPackageFilterMode() throws RemoteException {
        return requireService().getPackageFilterMode();
    }

    public boolean triggerCompression() throws RemoteException {
        return requireService().triggerCompression();
    }

    public boolean triggerCompressionWithRange(String startTime, String endTime)
            throws RemoteException {
        return requireService().triggerCompressionWithRange(startTime, endTime);
    }

    public String getCompressStatus() throws RemoteException {
        return requireService().getCompressStatus();
    }

    public boolean reportUploadResult(boolean success) throws RemoteException {
        return requireService().reportUploadResult(success);
    }

    public boolean cancelCompressTask() throws RemoteException {
        return requireService().cancelCompressTask();
    }

    public long fetchZipToUri(Uri destination) throws Exception {
        try (OutputStream output = appContext.getContentResolver()
                     .openOutputStream(destination, "w")) {
            if (output == null) throw new IllegalStateException("Cannot open destination");
            ParcelFileDescriptor descriptor = requireService().getLogZip();
            if (descriptor == null) throw new IllegalStateException("ZIP pipe is null");
            try (InputStream input =
                         new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    total += count;
                }
                output.flush();
                return total;
            }
        }
    }

    public XcLoggerConfigUpdater configUpdater() {
        return new XcLoggerConfigUpdater(new XcLoggerConfigUpdater.Transport() {
            @Override
            public int getApiVersion() {
                return LoggerClient.this.apiVersion;
            }

            @Override
            public void submit(XcLoggerConfig2 update,
                               XcLoggerConfigUpdater.CommitCallback callback) {
                String requestId = update.getRequestId();
                try {
                    requireService().updateConfiguration2(update,
                            new IXcLoggerConfigUpdateCallback.Stub() {
                                @Override
                                public void onComplete(XcLoggerConfigUpdateResult result) {
                                    callback.onComplete(result);
                                }
                            });
                } catch (Exception error) {
                    callback.onComplete(remoteError(requestId, error));
                }
            }
        }, callbackExecutor);
    }

    private XcLoggerConfigUpdateResult remoteError(String requestId, Exception error) {
        String message = error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage();
        return new XcLoggerConfigUpdateResult(requestId,
                XcLoggerConfigUpdateResult.REMOTE_ERROR,
                message, "", false);
    }

    private IXcLoggerService requireService() {
        IXcLoggerService current = service;
        if (current == null) throw new IllegalStateException("Logger service is not bound");
        return current;
    }

    private final IXcLoggerListener remoteListener = new IXcLoggerListener.Stub() {
        @Override
        public void onStatusChanged(int status) {
            callbackExecutor.execute(() -> events.onStatusChanged(status));
        }

        @Override
        public void onOperationResult(String opType, boolean success,
                                      String message, boolean runningState) {
            callbackExecutor.execute(() -> events.onOperationResult(
                    opType, success, message, runningState));
        }

        @Override
        public void onCompressFinished(boolean success, String message) {
            callbackExecutor.execute(() -> events.onCompressFinished(success, message));
        }

        @Override
        public void onCompressReady(String zipFiles, int retryCount,
                                    int maxRetryCount) {
            callbackExecutor.execute(() -> events.onCompressReady(
                    zipFiles, retryCount, maxRetryCount));
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IXcLoggerService.Stub.asInterface(binder);
            try {
                int connectedApiVersion = service.getApiVersion();
                apiVersion = connectedApiVersion;
                service.registerListener(remoteListener);
                callbackExecutor.execute(
                        () -> events.onConnected(connectedApiVersion));
            } catch (RemoteException error) {
                service = null;
                apiVersion = -1;
                callbackExecutor.execute(events::onDisconnected);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            apiVersion = -1;
            callbackExecutor.execute(events::onDisconnected);
        }
    };

    @Override
    public void close() {
        IXcLoggerService current = service;
        if (current != null) {
            try {
                current.unregisterListener(remoteListener);
            } catch (RemoteException ignored) {
            }
        }
        if (bindRequested) {
            appContext.unbindService(connection);
            bindRequested = false;
        }
        service = null;
        apiVersion = -1;
    }
}
```

`Transport` 只负责把 updater 生成的 `XcLoggerConfig2` 转给 `updateConfiguration2()`，已经封装在 `LoggerClient` 内。页面只需要调用 `client.configUpdater()`。

### 4.2 `CaptureController`：启停与状态

{维护逻辑：所有可能等待服务端的同步调用都在工作线程执行，UI 只接收结果。}

```java
package com.example.logclient;

import java.util.concurrent.Executor;

public final class CaptureController {
    private final LoggerClient client;
    private final Executor worker;
    private final Executor callbackExecutor;

    public CaptureController(LoggerClient client, Executor worker,
                             Executor callbackExecutor) {
        this.client = client;
        this.worker = worker;
        this.callbackExecutor = callbackExecutor;
    }

    public void start(java.util.function.Consumer<Boolean> callback) {
        worker.execute(() -> runBoolean(client::startLogging, callback));
    }

    public void stop(java.util.function.Consumer<Boolean> callback) {
        worker.execute(() -> runBoolean(client::stopLogging, callback));
    }

    public void query(java.util.function.Consumer<Boolean> callback) {
        worker.execute(() -> runBoolean(client::isRunning, callback));
    }

    private void runBoolean(RemoteBooleanCall call,
                            java.util.function.Consumer<Boolean> callback) {
        boolean result;
        try {
            result = call.run();
        } catch (Exception error) {
            result = false;
        }
        boolean finalResult = result;
        callbackExecutor.execute(() -> callback.accept(finalResult));
    }

    private interface RemoteBooleanCall {
        boolean run() throws Exception;
    }
}
```

### 4.3 `ConfigController`：读取与原子更新

{维护逻辑：读取配置与读取包模式必须成对；写入统一使用一次性 updater，不直接构造兼容 patch。}

```java
package com.example.logclient;

import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerConfigUpdateResult;
import com.xcheng.xclogger.util.XcLoggerConfigUpdater;

import java.util.concurrent.Executor;

public final class ConfigController {
    private final LoggerClient client;
    private final Executor worker;
    private final Executor callbackExecutor;

    public ConfigController(LoggerClient client, Executor worker,
                            Executor callbackExecutor) {
        this.client = client;
        this.worker = worker;
        this.callbackExecutor = callbackExecutor;
    }

    public void loadConfig(
            java.util.function.BiConsumer<XcLoggerConfig, String> callback) {
        worker.execute(() -> {
            try {
                XcLoggerConfig config = client.getConfiguration();
                String mode = client.getPackageFilterMode();
                callbackExecutor.execute(() -> callback.accept(config, mode));
            } catch (Exception error) {
                callbackExecutor.execute(() -> callback.accept(null, "off"));
            }
        });
    }

    public String applyConfig(
            XcLoggerConfigUpdater.PackageFilterMode mode,
            java.util.function.Consumer<XcLoggerConfigUpdateResult> callback) {
        return client.configUpdater()
                .filterTags("ActivityManager", "WindowManager")
                .filterLevel(XcLoggerConfigUpdater.LogLevel.VERBOSE)
                .filterPackages("com.vendor.payment", "com.vendor.agent.")
                .blacklistPackages("com.vendor.noisy")
                .packageFilterMode(mode)
                .commitAsync(callback::accept);
    }
}
```

### 4.4 `CompressionController`：压缩、获取和确认

{维护逻辑：请求压缩、等待就绪、复制 ZIP、业务上传、回传结果必须是五个独立阶段；禁止在请求返回后立即读取。}

```java
package com.example.logclient;

import android.net.Uri;
import android.util.Log;

import java.util.concurrent.Executor;

public final class CompressionController {
    private final LoggerClient client;
    private final Executor worker;
    private final Executor callbackExecutor;

    public CompressionController(LoggerClient client, Executor worker,
                                 Executor callbackExecutor) {
        this.client = client;
        this.worker = worker;
        this.callbackExecutor = callbackExecutor;
    }

    public void requestFullCompression() {
        worker.execute(() -> {
            try {
                if (!client.triggerCompression()) {
                    throw new IllegalStateException("Compression was rejected");
                }
            } catch (Exception error) {
                Log.e("Compression", "request failed", error);
            }
        });
    }

    public void requestRangeCompression(String startTime, String endTime) {
        worker.execute(() -> {
            try {
                if (!client.triggerCompressionWithRange(startTime, endTime)) {
                    throw new IllegalStateException("Compression was rejected");
                }
            } catch (Exception error) {
                Log.e("Compression", "range request failed", error);
            }
        });
    }

    public void fetchReadyZip(
            Uri destination,
            java.util.function.LongConsumer callback) {
        worker.execute(() -> {
            long bytes = -1;
            try {
                String status = client.getCompressStatus();
                if (status != null && status.contains("state=WAIT_UPLOAD_RESULT")) {
                    bytes = client.fetchZipToUri(destination);
                }
            } catch (Exception error) {
                Log.e("Compression", "fetch failed", error);
            }
            long finalBytes = bytes;
            callbackExecutor.execute(() -> callback.accept(finalBytes));
        });
    }

    public void reportUploadResult(boolean success) {
        worker.execute(() -> {
            try {
                client.reportUploadResult(success);
            } catch (Exception error) {
                Log.e("Compression", "report failed", error);
            }
        });
    }

    public void cancel() {
        worker.execute(() -> {
            try {
                client.cancelCompressTask();
            } catch (Exception error) {
                Log.e("Compression", "cancel failed", error);
            }
        });
    }
}
```

开始和结束时间都应使用 `yyyyMMddHHmmss`，并保证开始时间不晚于结束时间。每个 ZIP 的最后一个文件是 `A_OperationHistory_yyyyMMddHHmmss.txt`。`getLogZip()` 一次只导出第一份 ZIP；如果状态中有多份 ZIP，应全部上传成功后再调用 `reportUploadResult(true)`。

### 4.5 生命周期接入

{维护逻辑：只能由一个地方负责连接和断开 XCLogger；多个页面共用时，放到 Application 级对象或一个长期运行的 Service 中。}

```java
package com.example.logclient;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int MAX_BIND_CHECKS = 25;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private CaptureController captureController;
    private ConfigController configController;
    private CompressionController compressionController;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent serviceIntent = new Intent(this, XcLoggerTestService.class);
        serviceIntent.putExtra(
                XcLoggerTestService.EXTRA_XCLOGGER_PACKAGE,
                "com.vendor.logger"); // 替换为设备上 XCLogger 的真实包名
        startService(serviceIntent);
        waitForClient(0);
    }

    private void waitForClient(int checkCount) {
        LoggerClient client = XcLoggerTestService.getClient();
        if (client != null && client.isBound()) {
            captureController = new CaptureController(
                    client, worker, getMainExecutor());
            configController = new ConfigController(
                    client, worker, getMainExecutor());
            compressionController = new CompressionController(
                    client, worker, getMainExecutor());
            return;
        }
        if (checkCount >= MAX_BIND_CHECKS) {
            throw new IllegalStateException("XCLogger connection timed out");
        }
        mainHandler.postDelayed(() -> waitForClient(checkCount + 1), 200);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        worker.shutdown();
        stopService(new Intent(this, XcLoggerTestService.class));
        super.onDestroy();
    }
}
```

Manifest 中注册保存 `client` 的 Service：

```xml
<service
    android:name=".XcLoggerTestService"
    android:exported="false" />
```

---

## 5. 注意事项

{维护逻辑：本节只记录上述可移植示例的使用边界；示例行为变化时必须同步修订，不重复罗列接口表。}

| 主题 | 基于示例的要求 |
|---|---|
| XCLogger 包名 | 填写设备上实际安装的 XCLogger 包名，并在 Manifest `<queries>` 中声明 |
| 线程 | `start/stop/isRunning/getConfiguration/trigger/report/cancel/getLogZip` 在工作线程调用；Binder listener 通过 `callbackExecutor` 切回 UI/业务线程 |
| 绑定状态 | 只有 `onConnected` 后才能调用；`onServiceDisconnected` 后停止提交并重新绑定 |
| API 版本 | 绑定后先读 `getApiVersion()`；当前推荐 API 4；结构化更新不能发送给低于 API 4 的服务 |
| 配置读取 | `getConfiguration()` 与 `getPackageFilterMode()` 分开读取，不能从 Parcelable 推断模式 |
| updater 生命周期 | 每次业务更新创建一个新 updater；`commitAsync()` 后不复用 |
| Level | 默认及推荐“不按 Level 丢弃”值为 `VERBOSE/v`，查询不返回 `off` |
| Package 模式 | `OFF` 保留两套名单但不生效；`WHITELIST` 和 `BLACKLIST` 互斥 |
| 停用字段 | Tag 黑名单、Level 黑名单、Content 白/黑名单仅为兼容保留，业务代码不得依赖 |
| 压缩就绪 | `triggerCompression*()` 的 `true` 只表示受理；等待 `onCompressReady` 或 `WAIT_UPLOAD_RESULT` |
| ZIP 读取 | `getLogZip()` 通过文件管道输出第一份 ZIP；App 必须有目标 `Uri` 的写权限，并在完成后关闭输入输出流 |
| 上传确认 | 复制到本地不等于上传成功；完成全部业务上传后才调用 `reportUploadResult(true)` |
| 上传失败 | `reportUploadResult(false)` 会增加重试计数，第 3 次失败会删除待上传 ZIP |
| 操作历史 | ZIP 最后一个 entry 是固定长度历史快照；原历史文件不会被移动、截断或删除 |
| 资源释放 | 长期持有者销毁时先 `unregisterListener()`，再 `unbindService()`，最后停止工作线程 |
