package com.xcheng.xclogger.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
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
        filter.addAction(FileCompressService.ACTION_SUCCESS);
        filter.addAction(FileCompressService.ACTION_FAILED);
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
            return submitAndWait("trigger_compress", null).isSuccess();
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
        final Object lock = new Object();
        final ControlResult[] holder = new ControlResult[1];

        SourceResolver resolver = new SourceResolver();
        String source = resolver.resolveFromAidl(getApplicationContext());
        ControlRequest request = new ControlRequest("aidl", opType, source, configPatch);

        CommandSerialExecutor.getInstance().submit(getApplicationContext(), request, result -> {
            holder[0] = result;
            notifyOperationResult(result);
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

    private class CompressResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (FileCompressService.ACTION_SUCCESS.equals(action)) {
                notifyCompressFinished(true, "Success");
            } else if (FileCompressService.ACTION_FAILED.equals(action)) {
                String error = intent.getStringExtra("error_msg");
                notifyCompressFinished(false, error != null ? error : "Failed");
            }
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
