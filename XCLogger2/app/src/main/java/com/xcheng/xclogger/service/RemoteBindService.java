package com.xcheng.xclogger.service;

import android.app.Service;
import android.content.Intent;
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
    private final RemoteCallbackList<IXcLoggerListener> mListeners = new RemoteCallbackList<>();
    private SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "AIDL Control Service created.");

        // 核心亮点：通过监听 SharedPreferences 实现 UI/AIDL/Broadcast 数据实时同步通知
        XcLoggerDatabase db = new XcLoggerDatabase(this);
        mPrefsListener = (prefs, key) -> {
            if (XcLoggerDatabase.K_IS_RUNNING.equals(key)) {
                boolean running = prefs.getBoolean(key, false);
                notifyStatusChanged(running ? 1 : 0);
            }
        };
        db.getPrefs().registerOnSharedPreferenceChangeListener(mPrefsListener);
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
        public void triggerCompression(String targetPackage) throws RemoteException {
            Intent intent = new Intent(getApplicationContext(), FileCompressService.class);
            intent.setPackage(targetPackage);
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

    private void notifyStatusChanged(int status) {
        int n = mListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try { mListeners.getBroadcastItem(i).onStatusChanged(status); }
            catch (RemoteException e) { Log.e(TAG, "Callback failed", e); }
        }
        mListeners.finishBroadcast();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "External client binding to AIDL interface...");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        new XcLoggerDatabase(this).getPrefs().unregisterOnSharedPreferenceChangeListener(mPrefsListener);
        super.onDestroy();
    }
}