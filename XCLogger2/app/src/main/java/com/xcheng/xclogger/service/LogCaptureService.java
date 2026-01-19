package com.xcheng.xclogger.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.xcheng.xclogger.MainActivity;
import com.xcheng.xclogger.R;
import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.util.DatabaseMigration;

/**
 * LogCaptureService - 日志捕获服务
 */
public class LogCaptureService extends Service {
    private static final String TAG = "LogCaptureService";
    private static final String CHANNEL_ID = "LogCaptureServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private ProcessController processController;

    @Override
    public void onCreate() {
        super.onCreate();
        DatabaseMigration.migrateIfNeeded(this);
        ConfigLoader.getInstance().load(this);
        processController = ProcessController.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotification();
        startLogCapture();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLogCapture();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startLogCapture() {
        if (processController != null) {
            processController.startLogging("service");
        }
    }

    private void stopLogCapture() {
        if (processController != null) {
            processController.stopLogging("service");
        }
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_content))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Log Capture Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}