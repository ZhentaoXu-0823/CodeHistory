# XCLogger AAR API Reference

> **Version**: v2.0.0 / Configuration Protocol API 4
> **Updated**: 2026-08-11
> **Platform**: Android 6.0 (API 23) or later

{Global maintenance rule: This document describes only callable capabilities in the current AAR. Add new APIs to Quick Reference first, then update the portable examples and notes. Never mix compatibility APIs with recommended APIs. Keep this document structurally and behaviorally aligned with the Chinese version.}

---

## 1. Overview

{Maintenance rule: Keep only stable information required to identify and integrate the AAR. Do not list source-code directory paths, customer variants, or specific installed package names.}

The prebuilt AAR provides an AIDL API. After connecting to XCLogger installed on the device, an app can start and stop logging, read and change settings, compress logs, receive status notifications, and export a ZIP through a file pipe.

| Item | Value |
|---|---|
| Communication | Android AIDL service binding |
| AAR package | `xclogger-api-release.aar` |
| Service action | `com.xcheng.xclogger.REMOTE_BIND` |
| Minimum SDK | API 23 |

> **Access note**: Other apps can currently connect to the XCLogger service without an additional binding permission. Always set the real XCLogger package name in the Intent so it cannot connect to the wrong app. A successful connection only means the service exists; it does not verify the caller. Production builds should add a signature permission or caller-UID check.

---

## 2. Quick Reference

{Maintenance rule: Show the directly reusable binding pattern first. Order methods as current/recommended followed by compatibility-only. Every method and callback must include its complete signature and parameter semantics.}

### 2.1 Service Binding

{Maintenance rule: The binding example must be self-contained. The XCLogger package name is passed to the constructor; Context, ServiceConnection, service object, API version, and unbind state are declared inside the class.}

Copy the following `LoggerServiceConnector` and replace `com.vendor.logger` with the real XCLogger package name installed on the device.

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

`callbackExecutor` selects the thread used for connection results. An Activity can pass `getMainExecutor()` to receive them on the main thread. `events` is the `ConnectionEvents` implementation defined above.

Minimal Activity usage:

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
                "com.vendor.logger", // Replace with the installed XCLogger package name
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

### 2.2 Methods

{Maintenance rule: Keep API 4 methods in the upper table. Put methods retained only for old clients in the lower table and mark them as not recommended.}

`IXcLoggerService` currently contains 16 methods. The tables enumerate every method individually instead of merging alternate entry points for the same capability.

#### Current and Recommended

| Complete signature | Parameters | Return value and behavior |
|---|---|---|
| `int getApiVersion()` | None | Returns `4`; call immediately after binding |
| `String getPackageFilterMode()` | None | Returns `off`, `whitelist`, or `blacklist` |
| `void updateConfiguration2(XcLoggerConfig2 update, IXcLoggerConfigUpdateCallback callback)` | `update`: directly carries base fields, list replace/add/remove operations, and optional `packageFilterMode`;<br>`callback`: status, failure reason, and changed fields | The only asynchronous structured-update entry point; normally called through `XcLoggerConfigUpdater` |
| `XcLoggerConfig getConfiguration()` | None | Returns a configuration copy; may be `null` before initialization |
| `boolean startLogging()` | None | Starts capture; returns the current control outcome |
| `boolean stopLogging()` | None | Stops capture; returns the current control outcome |
| `boolean isRunning()` | None | Returns the actual capture state (the Service exists and the collector is running) |
| `boolean triggerCompression()` | None | Requests no-range compression; `true` means accepted, not completed |
| `boolean triggerCompressionWithRange(String startTime, String endTime)` | Ordered `yyyyMMddHHmmss` boundaries; currently provide both | Requests range compression; use callback/status for completion |
| `String getCompressStatus()` | None | Returns semicolon-delimited `state`, `zip_files`, `retry_count`, and `max_retry_count` |
| `ParcelFileDescriptor getLogZip()` | None | Available only in `WAIT_UPLOAD_RESULT`; pipes the first pending ZIP |
| `boolean reportUploadResult(boolean success)` | Pass `true` after every required ZIP uploads successfully; pass `false` on failure | `true` deletes pending ZIPs and returns `true`; a `false` report updates retry state but currently returns `false`, so verify with `getCompressStatus()`; the third failure deletes ZIPs |
| `boolean cancelCompressTask()` | None | Cancels active compression or clears a pending-upload task |
| `void registerListener(IXcLoggerListener listener)` | Callback object | Receives capture, operation, and compression results |
| `void unregisterListener(IXcLoggerListener listener)` | The same callback object used to register | Unregister before destroying the Activity or Service |

#### Compatibility Only (Not Recommended)

| Complete signature | Compatibility purpose | Why not recommended |
|---|---|---|
| ~~`boolean updateConfigurationPartial(XcLoggerConfig config)`~~ | Supports positive-integer/non-empty-string patches from old clients; buffer is rounded up to 512 and capped at 4096 | Returns only a synchronous boolean; package mode and tag/level/content denylist fields remain unchanged; cannot express list add/remove; use `XcLoggerConfigUpdater` |

### 2.3 Callback Registration and Parameters

{Maintenance rule: Registration code comes first. Document every callback and every parameter explicitly; never abbreviate a signature with placeholder text.}

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

The complete Activity below declares `connector`, `remoteListener`, and the event handler. It registers after binding succeeds and unregisters the same `remoteListener` before the Activity is destroyed:

```java
package com.example.logclient;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

public final class ListenerActivity extends Activity {
    private LoggerServiceConnector connector;
    private LoggerRemoteListener remoteListener;

    private final LoggerRemoteListener.Events eventHandler =
            new LoggerRemoteListener.Events() {
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
                "com.vendor.logger", // Replace with the installed XCLogger package name
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

| Complete callback signature | Parameter details | Intended use |
|---|---|---|
| `void onStatusChanged(int status)` | `status`: `0` means stopped; `1` means running | Update UI or cached state after dispatching away from the Binder thread |
| `void onOperationResult(String opType, boolean success, String message, boolean runningState)` | `opType`: completed control operation;<br>`success`: final operation result;<br>`message`: success details or failure reason;<br>`runningState`: capture state after completion | Confirm start, stop, configuration, upload-result, and cancellation operations |
| `void onCompressFinished(boolean success, String message)` | `success`: whether compression ended successfully;<br>`message`: result, no-file condition, or failure reason | Indicates flow completion; use readiness callback/status to determine whether a ZIP can be read |
| `void onCompressReady(String zipFiles, int retryCount, int maxRetryCount)` | `zipFiles`: comma-delimited internal ZIP paths;<br>`retryCount`: current upload-failure count;<br>`maxRetryCount`: maximum failures, currently `3` | Preferred readiness signal for `getLogZip()`; do not directly open internal paths cross-application |

### 2.4 `XcLoggerConfig` Fields

{Maintenance rule: Active fields belong in the upper table. Compatibility-only or ineffective fields belong in the lower table and must use strikethrough.}

#### Active Fields

| Field | Type | Current use |
|---|---|---|
| `totalSizeMb` | `int` | Total log storage limit in MB |
| `fileSizeMb` | `int` | Per-file limit in MB; cannot exceed total size |
| `bufferSizeBytes` | `int` | Write buffer: 512 through 4096, aligned to 512 |
| `logDir` | `String` | Log cache directory |
| `logPeriodHours` | `int` | Retention in hours |
| `filterTag` | `String` | Tag allowlist; `all` disables tag restriction |
| `filterLevel` | `String` | `f/e/w/i/d/v`; default/recommended no-drop value is `v` |
| `filterPackage` | `String` | Package allowlist, active only in `WHITELIST` mode |
| `filterPackageBlacklist` | `String` | Package denylist, active only in `BLACKLIST` mode |

Package mode is not stored in the old Parcelable. A Parcelable is Android's fixed data format for sending a configuration object between two processes. Read the mode with `getPackageFilterMode()` and change it with `packageFilterMode()`. `OFF` is the default; it preserves both lists without filtering.

#### Compatibility Fields (Not Recommended)

| Field | Why retained | Current behavior |
|---|---|---|
| ~~`filterTagBlacklist`~~ | Parcelable, XML, and database compatibility | Not changed by the updater and not applied during capture |
| ~~`filterLevelBlacklist`~~ | Historical data compatibility | Not applied during capture |
| ~~`filterContent`~~ | Historical data compatibility | No content filtering |
| ~~`filterContentBlacklist`~~ | Historical data compatibility | No content filtering |

#### Public AAR Data Models

| Type | Public contract | Usage requirement |
|---|---|---|
| `XcLoggerConfig` | No-argument constructor; getters/setters for 13 fields; Parcelable `CREATOR`, `describeContents()`, and `writeToParcel()` | Use only for configuration reads and compatibility `updateConfigurationPartial()`; never copy the class or change Parcel order |
| `XcLoggerConfigUpdate` | No-argument constructor; `FIELD_*` bits; getters/setters for request ID, bitmask, six base fields, and three public list mutations; `hasField()`, `hasChanges()`; Parcelable | API 4 request base class; applications normally let the updater create `XcLoggerConfig2` |
| `XcLoggerConfigUpdate.ListMutation` | Constructor parameters `replace/replacement/additions/removals`; matching getters; `hasOperations()`; Parcelable | Collection getters are read-only views; values are validated by both client and service |
| `XcLoggerConfig2` | Extends `XcLoggerConfigUpdate`; adds mode constants, `get/setPackageFilterMode()`, `hasPackageFilterMode()`, and overrides `hasChanges()` and Parcel handling | `packageFilterMode == null` means do not update the mode; it does not mean `off` |
| `XcLoggerConfigUpdateResult` | No-argument/full constructor; getters for status, request ID, message, changed fields, and restart flag; `isSuccess()`; Parcelable | `isSuccess()` is `true` only for `SUCCESS (0)` and `NO_CHANGES (1)` |

### 2.5 Fluent Configuration Updates

{Maintenance rule: This is the only recommended configuration-write path. Keep method inventory, constraints, result codes, and examples synchronized with `XcLoggerConfigUpdater`.}

#### Fluent Methods

| Category | Methods |
|---|---|
| Sizes/path | `totalSizeMb()`, `fileSizeMb()`, `bufferSizeBytes()`, `logDir()`, `logPeriodHours()` |
| Level | `filterLevel(LogLevel)`, `filterLevel(String)` |
| Tag allowlist | `filterTags(String...)`, `filterTags(Collection<String>)`, `filterTagsCsv(String)`, `addTag(String)`, `removeTag(String)` |
| Package allowlist | `filterPackages(String...)`, `filterPackages(Collection<String>)`, `filterPackagesCsv(String)`, `allPackages()`, `addPackage(String)`, `removePackage(String)` |
| Package denylist | `blacklistPackages(String...)`, `blacklistPackages(Collection<String>)`, `blacklistPackagesCsv(String)`, `addBlacklistedPackage(String)`, `removeBlacklistedPackage(String)`, `clearPackageBlacklist()` |
| Package mode | `packageFilterMode(PackageFilterMode)`, `packageFilterMode(String)`; valid values are `OFF/WHITELIST/BLACKLIST` or their lowercase wire values |
| Commit | `commitAsync()`; worker threads may use blocking `commit()` |

`XcLoggerConfigUpdater(Transport, Executor)` is the public construction entry point. `Transport` implements `getApiVersion()` and `submit(XcLoggerConfig2, CommitCallback)`; `CommitCallback` contains only `onComplete(result)`. Both `LogLevel` and `PackageFilterMode` expose `wireValue()`. An updater is single-use; modification or another commit after submission throws `IllegalStateException`.

#### Replace/Add/Remove Semantics

- `filterTags(values)`, `filterPackages(values)`, and `blacklistPackages(values)` replace the complete list.
- `add*()` and `remove*()` are applied against the latest server configuration in its serial queue.
- Removing the last allowlist item restores `all`; removing the last package-denylist item leaves it empty.
- A tag is at most 64 characters; tag/package lists are at most 64 items.
- Packages support exact names and `.`-suffixed prefixes. UID is the primary identity; PID is the fallback.
- An updater is single-use. Changing or submitting it again after commit throws an exception.

#### Recommended Example

This example has three explicit parts: `LoggerClient.java` in section 4.1 connects to XCLogger; `XcLoggerTestService.java` below creates and stores that object; `ConfigActivity.java` then retrieves the same object through `getClient()`. Before copying this section, place `LoggerClient.java` from section 4.1 in the same package.

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

The demo repository calls its equivalent wrapper `XcLoggerClient`; the portable version in this document is named `LoggerClient` and is fully defined in section 4.1. The Activity below shows where the Service starts, where `client` comes from, and where the configuration request is made:

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
                "com.vendor.logger"); // Replace with the installed XCLogger package name
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

`XcLoggerTestService` must also be declared in the Manifest as shown in section 4.5. The example does not stop the Service in `ConfigActivity.onDestroy()` because several screens share the same `client`; stop it from the app's single lifecycle owner when logging is no longer needed.

#### Result Status

| Status | Meaning |
|---|---|
| `SUCCESS (0)` | Configuration committed |
| `NO_CHANGES (1)` | Valid request with no final change; `isSuccess()` is still `true` |
| `INVALID_ARGUMENT (2)` | Size, tag, package, level, or cross-field validation failed |
| `NOT_BOUND (3)` | Service is not bound |
| `UNSUPPORTED_SERVICE_VERSION (4)` | Protocol too old; `updateConfiguration2` requires API 4 |
| `PERSIST_FAILED (5)` | Persistence failed |
| `APPLIED_RESTART_FAILED (6)` | Persisted, but active capture failed to restart |
| `REMOTE_ERROR (7)` | Cross-process call failed |
| `TIMEOUT_PENDING (8)` | Blocking call timed out; request may still finish remotely |
| `INTERNAL_ERROR (9)` | Unclassified internal error |

---

## 3. Add the AAR to an App

{Maintenance rule: Maintain directory, Gradle, and Manifest instructions together. Replace the example package with the real XCLogger package name on the device.}

### 3.1 Directory Layout

{Maintenance rule: Keep the AAR separate from client wrappers. UI modules depend on wrappers and do not own Binder details.}

```text
app/
├── libs/
│   └── xclogger-api-release.aar
└── src/main/
    ├── AndroidManifest.xml
    └── java/com/example/logclient/
        ├── LoggerServiceConnector.java # Use when only basic binding is needed
        ├── LoggerRemoteListener.java   # Standalone remote-listener wrapper
        ├── LoggerClient.java          # Bind, callbacks, configuration, ZIP copy
        ├── XcLoggerTestService.java   # Create and retain the shared LoggerClient
        ├── CaptureController.java     # start/stop/isRunning
        ├── ConfigController.java      # getConfiguration + fluent updater
        ├── CompressionController.java # trigger/status/fetch/report/cancel
        └── MainActivity.java          # Start the Service and assemble controllers
```

### 3.2 Gradle

{Maintenance rule: Upgrade by replacing the AAR. Do not also copy AIDL/Parcelable source files, which can create duplicate classes or mismatched layouts.}

```gradle
android {
    defaultConfig { minSdk 23 }
}

dependencies {
    implementation files('libs/xclogger-api-release.aar')
}
```

The prebuilt AAR already contains generated Binder classes, so normal consumers do not need `buildFeatures.aidl`. Enable it only when consuming the `xclogger-api` source module or maintaining `.aidl` files directly.

### 3.3 Package Visibility

{Maintenance rule: Android 11+ requires the XCLogger package name in `<queries>`. Replace the placeholder.}

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <queries>
        <package android:name="com.vendor.logger" />
    </queries>
</manifest>
```

---

## 4. Portable Modules

{Maintenance rule: Organize examples along the demo responsibility boundaries: one reusable client, then capture, configuration, compression, and lifecycle usage. All methods that can wait run on a worker executor.}

### 4.1 `LoggerClient`: Connect, Receive Callbacks, and Update Settings

{Maintenance rule: This class connects and disconnects the service, registers callbacks, and calls the configuration APIs. UI pages should not repeat those details.}

The following class depends only on Android SDK types and the AAR:

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

    public boolean isBound() { return service != null; }
    public int getApiVersion() { return apiVersion; }

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

`Transport` forwards the updater-generated `XcLoggerConfig2` to `updateConfiguration2()` and is already wrapped inside `LoggerClient`. Screens only call `client.configUpdater()`.

### 4.2 `CaptureController`: Start, Stop, and State

{Maintenance rule: Execute synchronous remote calls on a worker thread. Send only final values back to the screen or app code.}

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

### 4.3 `ConfigController`: Read and Atomic Update

{Maintenance rule: Read configuration and package mode as a pair. Write only through a fresh single-use updater.}

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

### 4.4 `CompressionController`: Request, Fetch, and Report

{Maintenance rule: Requesting compression, waiting for readiness, copying the ZIP, uploading it, and reporting the result are separate stages. Never fetch immediately after request acceptance.}

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
                    throw new IllegalStateException("Compression rejected");
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
                    throw new IllegalStateException("Range compression rejected");
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

Provide both times as `yyyyMMddHHmmss`, with the start not later than the end. The final file in each ZIP is `A_OperationHistory_yyyyMMddHHmmss.txt`. `getLogZip()` exports one ZIP at a time. If the status lists several ZIPs, upload all of them before reporting `true`.

### 4.5 Lifecycle Ownership

{Maintenance rule: Exactly one place should connect and disconnect XCLogger. For several screens, keep the client in an Application-level object or a long-running Service.}

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
                "com.vendor.logger"); // Replace with the installed XCLogger package name
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

Register the Service that stores `client` in the Manifest:

```xml
<service
    android:name=".XcLoggerTestService"
    android:exported="false" />
```

---

## 5. Notes Derived from the Examples

{Maintenance rule: Keep only constraints that apply to the portable modules above. When example behavior changes, update this table in the same change.}

| Topic | Requirement |
|---|---|
| XCLogger package name | Use the package name installed on the device and declare it in Manifest `<queries>` |
| Threads | Run waiting remote calls on a worker; dispatch Binder callbacks through `callbackExecutor` |
| Binding | Call only after `onConnected`; stop submission and rebind after disconnection |
| API version | Read immediately after binding; current recommended version is API 4; structured updates cannot be sent to older services |
| Configuration read | Read `getConfiguration()` and `getPackageFilterMode()` separately; do not infer mode from the Parcelable |
| Updater lifecycle | Create one updater for each settings change and never reuse it after commit |
| Level | `VERBOSE/v` is the default recommended no-drop value; queries do not return `off` |
| Package modes | `OFF` preserves both lists without filtering; `WHITELIST` and `BLACKLIST` are mutually exclusive |
| Disabled fields | Tag denylist, level denylist, and content allow/deny fields are compatibility-only |
| Compression readiness | A `true` request result only means accepted; wait for `onCompressReady` or `WAIT_UPLOAD_RESULT` |
| ZIP pipe | `getLogZip()` exports the first ZIP; the app needs write access to the destination `Uri` and must close every stream |
| Upload result | Saving a local copy is not an upload; report `true` only after every required ZIP uploads successfully |
| Upload failure | Reporting `false` increments retry count; the third failure deletes pending ZIPs |
| Operation history | The final ZIP entry is a fixed-length history snapshot; the original file is never moved, truncated, or deleted |
| Cleanup | Unregister the callback, disconnect the service, then stop the worker thread |
