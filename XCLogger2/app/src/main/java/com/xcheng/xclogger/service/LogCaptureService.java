package com.xcheng.xclogger.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.xcheng.xclogger.MainActivity;
import com.xcheng.xclogger.R;
import com.xcheng.xclogger.processctr.LogServiceController;
import com.xcheng.xclogger.processctr.ProcessController;

public class LogCaptureService extends Service {
    private static final String TAG = "LogCaptureService";
    private static final String CHANNEL_ID = "LogCaptureServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private ProcessController processController;

    @Override
    public void onCreate() {
        super.onCreate();
        // 极致启动优化：onCreate 第一行立即调用 startForeground 防止超时报错
        createNotification();
        processController = ProcessController.getInstance(this);
        LogServiceController.setServiceActive(true);
        Log.i(TAG, "Worker Service created and foreground assigned.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String source = intent != null ? intent.getStringExtra("source") : null;
        if (source == null || source.trim().isEmpty()) {
            source = "direct";
        }
        try {
            if (LogServiceController.isActuallyRunning()) {
                Log.i(TAG, "Log capture is already running, skipping duplicate start command (source:"
                        + source + ")");
            } else if (processController != null) {
                Log.i(TAG, "Starting log capture via source: " + source);
                processController.startLogging(source);
            }
        } finally {
            LogServiceController.onStartRequestHandled();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "LogCaptureService stopping...");
        try {
            if (processController != null) {
                processController.stopLogging("service_destroyed");
            }
        } finally {
            LogServiceController.setServiceActive(false);
            super.onDestroy();
        }
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Log Capture Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_content))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
