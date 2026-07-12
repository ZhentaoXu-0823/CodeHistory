package com.xcheng.xclogger.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import com.xcheng.xclogger.control.CommandSerialExecutor;
import com.xcheng.xclogger.control.ControlRequest;
import com.xcheng.xclogger.control.ControlResult;
import com.xcheng.xclogger.control.SourceResolver;
import com.xcheng.xclogger.filemanager.FileCompressService;
import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

public class RemoteBindService extends Service {
    private static final String TAG = "RemoteBindService";

    private final RemoteCallbackList<IXcLoggerListener> mListeners = new RemoteCallbackList<>();
    private SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener;
    private CompressResultReceiver mCompressReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        XcLoggerDatabase db = new XcLoggerDatabase(this);
        mPrefsListener = (prefs, key) -> {
            if (XcLoggerDatabase.K_IS_RUNNING.equals(key)) {
                boolean running = prefs.getBoolean(key, false);
                notifyStatusChanged(running ? 1 : 0);
            }
        };
        db.getPrefs().registerOnSharedPreferenceChangeListener(mPrefsListener);

        mCompressReceiver = new CompressResultReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(FileCompressService.ACTION_CTRL_RESULT);
        registerReceiver(mCompressReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    private final IXcLoggerService.Stub mBinder = new IXcLoggerService.Stub() {
        @Override
        public boolean startLogging() throws RemoteException {
            return submitAndWait("start", null).isSuccess();
        }

        @Override
        public boolean stopLogging() throws RemoteException {
            return submitAndWait("stop", null).isSuccess();
        }

        @Override
        public boolean isRunning() throws RemoteException {
            return new XcLoggerDatabase(getApplicationContext()).loadRunningState();
        }

        @Override
        public XcLoggerConfig getConfiguration() throws RemoteException {
            return ConfigLoader.current();
        }

        @Override
        public boolean updateConfigurationPartial(XcLoggerConfig config) throws RemoteException {
            return submitAndWait("update_config", config).isSuccess();
        }

        @Override
        public boolean triggerCompression() throws RemoteException {
            return triggerCompressionWithRange(null, null);
        }

        @Override
        public boolean triggerCompressionWithRange(String startTime, String endTime) throws RemoteException {
            return submitAndWait("trigger_compress", null, startTime, endTime).isSuccess();
        }

        @Override
        public boolean reportUploadResult(boolean success) throws RemoteException {
            FileCompressService.Result result = FileCompressService.upload(getApplicationContext(), success);
            notifyOperationResult(new ControlResult(result.success, result.message, "upload_result", new XcLoggerDatabase(getApplicationContext()).loadRunningState(), result.state, result.zipFiles, result.retryCount, result.maxRetryCount));
            return result.success;
        }

        @Override
        public String getCompressStatus() throws RemoteException {
            FileCompressService.Result result = FileCompressService.query(getApplicationContext());
            return "state=" + result.state + ";zip_files=" + result.zipFiles + ";retry_count=" + result.retryCount + ";max_retry_count=" + result.maxRetryCount;
        }

        @Override
        public boolean cancelCompressTask() throws RemoteException {
            FileCompressService.Result result = FileCompressService.cancel(getApplicationContext());
            notifyOperationResult(new ControlResult(result.success, result.message, "cancel_compress", new XcLoggerDatabase(getApplicationContext()).loadRunningState(), result.state, result.zipFiles, result.retryCount, result.maxRetryCount));
            return result.success;
        }

        @Override
        public ParcelFileDescriptor getLogZip() {
            XcLoggerDatabase db = new XcLoggerDatabase(getApplicationContext());
            String state = db.getCompressState();
            if (!"WAIT_UPLOAD_RESULT".equals(state)) {
                throw new RuntimeException("No pending zip. State: " + state);
            }
            String zipPath = db.getPendingZipFiles();
            if (zipPath == null || zipPath.isEmpty()) {
                throw new RuntimeException("Zip path empty");
            }
            File f = new File(zipPath.split(",")[0].trim());
            if (!f.exists()) {
                throw new RuntimeException("Zip not found: " + f.getAbsolutePath());
            }
            try {
                // Use pipe to avoid SELinux avc denial on enforcing devices.
                // Pipe transfers file content via XCLogger's system_app context,
                // so the untrusted caller never touches the zip file directly.
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                ParcelFileDescriptor readEnd = pipe[0];
                ParcelFileDescriptor writeEnd = pipe[1];

                final File zipFile = f;
                new Thread("getLogZip-pipe") {
                    @Override
                    public void run() {
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(zipFile);
                             java.io.FileOutputStream fos = new java.io.FileOutputStream(writeEnd.getFileDescriptor())) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = fis.read(buf)) != -1) {
                                fos.write(buf, 0, n);
                            }
                            fos.flush();
                        } catch (IOException e) {
                            Log.e(TAG, "Pipe transfer failed", e);
                        } finally {
                            try { writeEnd.close(); } catch (IOException ignored) {}
                        }
                    }
                }.start();

                return readEnd;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void registerListener(IXcLoggerListener listener) throws RemoteException {
            if (listener != null) mListeners.register(listener);
        }

        @Override
        public void unregisterListener(IXcLoggerListener listener) throws RemoteException {
            if (listener != null) mListeners.unregister(listener);
        }
    };

    private ControlResult submitAndWait(String opType, XcLoggerConfig configPatch) {
        return submitAndWait(opType, configPatch, null, null);
    }

    private ControlResult submitAndWait(String opType, XcLoggerConfig configPatch, String startTime, String endTime) {
        final Object lock = new Object();
        final ControlResult[] holder = new ControlResult[1];

        SourceResolver resolver = new SourceResolver();
        String source = resolver.resolveFromAidl(getApplicationContext());
        ControlRequest request = new ControlRequest("aidl", opType, source, configPatch, false, startTime, endTime);

        CommandSerialExecutor.getInstance().submit(getApplicationContext(), request, result -> {
            holder[0] = result;
            if (result != null && !"async_result_pending".equals(result.getMessage())) {
                notifyOperationResult(result);
            }
            synchronized (lock) {
                lock.notifyAll();
            }
        });

        synchronized (lock) {
            if (holder[0] == null) {
                try {
                    lock.wait(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (holder[0] == null) {
            return new ControlResult(false, "timeout", opType, new XcLoggerDatabase(this).loadRunningState());
        }
        return holder[0];
    }

    private void notifyOperationResult(ControlResult result) {
        int n = mListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                mListeners.getBroadcastItem(i).onOperationResult(
                        result.getOpType(),
                        result.isSuccess(),
                        result.getMessage(),
                        result.isRunningState()
                );
            } catch (RemoteException e) {
                Log.e(TAG, "Operation callback failed", e);
            }
        }
        mListeners.finishBroadcast();
    }

    private void notifyCompressFinished(boolean success, String message) {
        int n = mListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                mListeners.getBroadcastItem(i).onCompressFinished(success, message);
            } catch (RemoteException e) {
                Log.e(TAG, "Compress callback failed", e);
            }
        }
        mListeners.finishBroadcast();
    }

    private void notifyCompressReady(String zipFiles, int retryCount, int maxRetryCount) {
        int n = mListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                mListeners.getBroadcastItem(i).onCompressReady(zipFiles, retryCount, maxRetryCount);
            } catch (RemoteException e) {
                Log.e(TAG, "Compress ready callback failed", e);
            }
        }
        mListeners.finishBroadcast();
    }

    private class CompressResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String opType = intent.getStringExtra("op_type");
            if (!"trigger_compress".equals(opType)) {
                return;
            }
            boolean success = intent.getBooleanExtra("success", false);
            String message = intent.getStringExtra("message");
            String state = intent.getStringExtra(FileCompressService.EXTRA_COMPRESS_STATE);
            String zipFiles = intent.getStringExtra(FileCompressService.EXTRA_ZIP_FILES);
            int retryCount = intent.getIntExtra(FileCompressService.EXTRA_RETRY_COUNT, 0);
            int maxRetryCount = intent.getIntExtra(FileCompressService.EXTRA_MAX_RETRY_COUNT, 3);
            if (success && FileCompressService.STATE_WAIT_UPLOAD_RESULT.equals(state)) {
                notifyCompressReady(zipFiles != null ? zipFiles : "", retryCount, maxRetryCount);
            }
            notifyCompressFinished(success, message != null ? message : (success ? "Success" : "Failed"));
        }
    }

    private void notifyStatusChanged(int status) {
        int n = mListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try { mListeners.getBroadcastItem(i).onStatusChanged(status); }
            catch (RemoteException e) { Log.e(TAG, "Status callback failed", e); }
        }
        mListeners.finishBroadcast();
    }

    @Override
    public IBinder onBind(Intent intent) { return mBinder; }

    @Override
    public void onDestroy() {
        if (mPrefsListener != null) {
            new XcLoggerDatabase(this).getPrefs().unregisterOnSharedPreferenceChangeListener(mPrefsListener);
        }
        if (mCompressReceiver != null) unregisterReceiver(mCompressReceiver);
        super.onDestroy();
    }
}
