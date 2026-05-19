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
import com.xcheng.xclogger.filemanager.FileCompressService;
import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.processctr.LogServiceController;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

public class RemoteBindService extends Service {
    private static final String TAG = "RemoteBindService";

    // 定义与 AIDL 契约一致的状态码
    public static final int COMPRESS_RES_SUCCESS = 0;
    public static final int COMPRESS_RES_FAILED = 1;
    public static final int COMPRESS_RES_ERROR = 2;

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
        public boolean startLogging(String source) throws RemoteException {
            LogServiceController.startLogService(getApplicationContext(), "aidl:" + source);
            return true;
        }

        @Override
        public void stopLogging() throws RemoteException {
            LogServiceController.stopLogService(getApplicationContext(), "aidl");
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
        public void updateConfiguration(XcLoggerConfig config) throws RemoteException {
            ConfigLoader.getInstance().updateConfig(getApplicationContext(), config);
        }

        @Override
        public void triggerCompression() throws RemoteException {
            Intent intent = new Intent(getApplicationContext(), FileCompressService.class);
            startService(intent);
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

    /**
     * 推送压缩结果给所有 AIDL 客户端
     */
    private void notifyCompressFinished(int result, String message) {
        int n = mListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                // 此处调用的是自动生成的接口，必须确保 AIDL 里的定义是 int
                mListeners.getBroadcastItem(i).onCompressFinished(result, message);
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
                notifyCompressFinished(COMPRESS_RES_SUCCESS, "Success");
            } else if (FileCompressService.ACTION_FAILED.equals(action)) {
                String error = intent.getStringExtra("error_msg");
                notifyCompressFinished(COMPRESS_RES_FAILED, error != null ? error : "Failed");
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