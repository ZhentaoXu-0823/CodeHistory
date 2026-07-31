package com.xcheng.xcloggertestdemo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import com.xcheng.xclogger.service.IXcLoggerListener;
import com.xcheng.xclogger.service.IXcLoggerService;
import com.xcheng.xclogger.service.IXcLoggerConfigUpdateCallback;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerConfig2;
import com.xcheng.xclogger.util.XcLoggerConfigUpdateResult;
import com.xcheng.xclogger.util.XcLoggerConfigUpdater;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * AIDL client wrapper for XCLogger remote service.
 * Thread-safe singleton. Best-practice reference for customer integration.
 *
 * Usage:
 *   XcLoggerClient client = new XcLoggerClient(context, "com.ko.xclogger");
 *   client.bind();  // binds to XCLogger AIDL service
 *   client.setOnResultListener(...); // optional, for UI
 *   client.startLogging();
 *   ...
 *   client.unbind();
 */
public class XcLoggerClient {
    private static final String TAG = "XcLoggerClient";
    private static final String ACTION_BIND = "com.xcheng.xclogger.REMOTE_BIND";
    private static final int BIND_TIMEOUT_MS = 8000;

    private final Context context;
    private final String targetPackage;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile IXcLoggerService service;
    private IXcLoggerListener listener;
    private volatile boolean bound;
    private volatile int apiVersion = -1;
    private int callbackSeq; // sequence number for callback ordering

    // Callback interface for UI / automation
    public interface ResultListener {
        void onResult(String source, String op, boolean success, String message);
    }
    private ResultListener resultListener;

    public XcLoggerClient(Context context, String targetPackage) {
        this.context = context.getApplicationContext();
        this.targetPackage = targetPackage;
    }

    public void setResultListener(ResultListener l) { this.resultListener = l; }
    public boolean isBound() { return bound && service != null; }
    public IXcLoggerService getService() { return service; }

    // ─── ServiceConnection ───────────────────────────────────────

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IXcLoggerService.Stub.asInterface(binder);
            bound = true;
            try {
                apiVersion = service.getApiVersion();
            } catch (RemoteException incompatibleService) {
                apiVersion = 1;
            }
            Log.i(TAG, "[AIDL] Service bound to " + targetPackage + ", apiVersion=" + apiVersion);
            // Auto-register listener
            registerListener();
            // Timeout fallback
            mainHandler.postDelayed(() -> {
                if (bound && service == null) {
                    Log.w(TAG, "[AIDL] Bind timeout, retrying...");
                    rebind();
                }
            }, BIND_TIMEOUT_MS);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
            apiVersion = -1;
            Log.w(TAG, "[AIDL] Service disconnected");
            notifyResult("AIDL", "disconnect", false, "Service disconnected");
        }
    };

    // ─── Public API ─────────────────────────────────────────────

    public void bind() {
        Intent intent = new Intent(ACTION_BIND);
        intent.setPackage(targetPackage);
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        Log.i(TAG, "[AIDL] Binding to " + targetPackage);
    }

    public void rebind() {
        unbind();
        bind();
    }

    public void unbind() {
        if (listener != null) {
            try { service.unregisterListener(listener); } catch (Exception e) { /* ignore */ }
            listener = null;
        }
        try { context.unbindService(connection); } catch (Exception e) { /* ignore */ }
        service = null;
        bound = false;
        apiVersion = -1;
    }

    // ─── All 13 AIDL methods ────────────────────────────────────

    public boolean startLogging() {
        return call("startLogging", () -> service.startLogging());
    }

    public boolean stopLogging() {
        return call("stopLogging", () -> service.stopLogging());
    }

    public boolean isRunning() {
        return call("isRunning", () -> service.isRunning());
    }

    public XcLoggerConfig getConfiguration() {
        try {
            if (service == null) return null;
            XcLoggerConfig cfg = service.getConfiguration();
            Log.i(TAG, "[AIDL-RETURN] getConfiguration: " + (cfg != null ? cfg.getTotalSizeMb() + "MB" : "null"));
            notifyResult("AIDL", "config_get", cfg != null, cfg != null ? "ok" : "null");
            return cfg;
        } catch (RemoteException e) {
            Log.e(TAG, "[AIDL-ERROR] getConfiguration", e);
            notifyResult("AIDL", "config_get", false, e.getMessage());
            return null;
        }
    }

    public String getPackageFilterMode() {
        try {
            return service != null && apiVersion >= 3 ? service.getPackageFilterMode() : "off";
        } catch (RemoteException e) {
            Log.e(TAG, "[AIDL-ERROR] getPackageFilterMode", e);
            return "off";
        }
    }

    public boolean updateConfigurationPartial(XcLoggerConfig patch) {
        return call("config_update", () -> service.updateConfigurationPartial(patch));
    }

    public XcLoggerConfigUpdater configUpdater() {
        return new XcLoggerConfigUpdater(new XcLoggerConfigUpdater.Transport() {
            @Override
            public int getApiVersion() {
                return isBound() ? apiVersion : -1;
            }

            @Override
            public void submit(XcLoggerConfig2 update,
                               XcLoggerConfigUpdater.CommitCallback callback) {
                submitConfiguration2(update, callback);
            }
        }, command -> mainHandler.post(command));
    }

    public void submitConfiguration2(XcLoggerConfig2 update,
                                      XcLoggerConfigUpdater.CommitCallback callback) {
        String requestId = update != null ? update.getRequestId() : "";
        IXcLoggerService current = service;
        if (!bound || current == null) {
            callback.onComplete(new XcLoggerConfigUpdateResult(requestId,
                    XcLoggerConfigUpdateResult.NOT_BOUND, "Service not bound", "", false));
            return;
        }
        try {
            current.updateConfiguration2(update, new IXcLoggerConfigUpdateCallback.Stub() {
                @Override
                public void onComplete(XcLoggerConfigUpdateResult result) {
                    mainHandler.post(() -> callback.onComplete(result));
                }
            });
        } catch (RemoteException e) {
            callback.onComplete(new XcLoggerConfigUpdateResult(requestId,
                    XcLoggerConfigUpdateResult.REMOTE_ERROR,
                    e.getMessage() == null
                            ? "Remote updateConfiguration2 failed" : e.getMessage(), "", false));
        }
    }

    public boolean triggerCompression() {
        return call("compress_full", () -> service.triggerCompression());
    }

    public boolean triggerCompressionWithRange(String st, String et) {
        return call("compress_range", () -> service.triggerCompressionWithRange(st, et));
    }

    public String getCompressStatus() {
        try {
            if (service == null) return null;
            String st = service.getCompressStatus();
            Log.i(TAG, "[AIDL-RETURN] getCompressStatus: " + st);
            notifyResult("AIDL", "compress_status", st != null, st);
            return st;
        } catch (RemoteException e) {
            Log.e(TAG, "[AIDL-ERROR] getCompressStatus", e);
            notifyResult("AIDL", "compress_status", false, e.getMessage());
            return null;
        }
    }

    public boolean reportUploadResult(boolean success) {
        return call("upload_" + (success ? "ok" : "fail"), () -> service.reportUploadResult(success));
    }

    public boolean cancelCompressTask() {
        return call("compress_cancel", () -> service.cancelCompressTask());
    }

    public long fetchZipToUri(Uri destination) {
        if (destination == null) {
            notifyResult("AIDL", "zip_fetch", false, "Destination URI is null");
            return -1;
        }
        try {
            IXcLoggerService current = service;
            if (current == null) return -1;

            try (OutputStream out = context.getContentResolver().openOutputStream(destination, "w")) {
                if (out == null) {
                    throw new IllegalStateException("Unable to open destination URI");
                }

                ParcelFileDescriptor pfd = current.getLogZip();
                if (pfd == null) {
                    throw new IllegalStateException("getLogZip returned null");
                }

                try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int length;
                    while ((length = in.read(buffer)) != -1) {
                        out.write(buffer, 0, length);
                        total += length;
                    }
                    out.flush();
                    Log.i(TAG, "[AIDL-RETURN] zip_fetch: " + total + " bytes -> " + destination);
                    notifyResult("AIDL", "zip_fetch", true, total + " bytes");
                    return total;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[AIDL-ERROR] zip_fetch", e);
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            notifyResult("AIDL", "zip_fetch", false, message);
            return -1;
        }
    }

    // ─── Listener management ────────────────────────────────────

    private void registerListener() {
        if (service == null) return;
        listener = new IXcLoggerListener.Stub() {
            @Override
            public void onStatusChanged(int status) {
                callbackSeq++;
                Log.i(TAG, "[AIDL-CALLBACK#" + callbackSeq + "] onStatusChanged: " + (status == 1 ? "RUNNING" : "STOPPED") + " (" + status + ")");
                notifyResult("CALLBACK", "statusChanged", true, status == 1 ? "RUNNING" : "STOPPED");
            }
            @Override
            public void onOperationResult(String op, boolean ok, String msg, boolean running) {
                callbackSeq++;
                Log.i(TAG, "[AIDL-CALLBACK#" + callbackSeq + "] onOperationResult: op=" + op + " ok=" + ok + " msg=" + msg + " running=" + running);
                notifyResult("CALLBACK", "opResult:" + op, ok, msg + " running=" + running);
            }
            @Override
            public void onCompressFinished(boolean ok, String msg) {
                callbackSeq++;
                Log.i(TAG, "[AIDL-CALLBACK#" + callbackSeq + "] onCompressFinished: ok=" + ok + " msg=" + msg);
                notifyResult("CALLBACK", "compressFinished", ok, msg);
            }
            @Override
            public void onCompressReady(String zipFiles, int retry, int maxRetry) {
                callbackSeq++;
                Log.i(TAG, "[AIDL-CALLBACK#" + callbackSeq + "] onCompressReady: zip=" + zipFiles + " retry=" + retry + "/" + maxRetry);
                notifyResult("CALLBACK", "compressReady", true, "zip=" + zipFiles + " retry=" + retry + "/" + maxRetry);
            }
        };
        try {
            service.registerListener(listener);
            Log.i(TAG, "[AIDL] Listener registered");
        } catch (RemoteException e) {
            Log.e(TAG, "[AIDL-ERROR] registerListener failed", e);
        }
    }

    // ─── Internal helpers ───────────────────────────────────────

    @FunctionalInterface
    private interface AidlCall<T> { T run() throws RemoteException; }

    private boolean call(String op, AidlCall<Boolean> fn) {
        try {
            if (service == null) {
                Log.w(TAG, "[AIDL-WARN] " + op + ": service not bound");
                notifyResult("AIDL", op, false, "not bound");
                return false;
            }
            boolean result = fn.run();
            Log.i(TAG, "[AIDL-RETURN] " + op + ": " + (result ? "OK" : "FAIL"));
            notifyResult("AIDL", op, result, result ? "OK" : "FAIL");
            return result;
        } catch (RemoteException e) {
            Log.e(TAG, "[AIDL-ERROR] " + op, e);
            notifyResult("AIDL", op, false, e.getMessage());
            return false;
        }
    }

    private void notifyResult(String source, String op, boolean success, String message) {
        if (resultListener != null) {
            mainHandler.post(() -> resultListener.onResult(source, op, success, message));
        }
    }
}
