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
 * LogCaptureService - 日志捕获服务，负责在后台持续捕获系统日志
 *
 * 功能方法：
 * - onCreate() - 服务创建时初始化
 * - onStartCommand() - 服务启动命令处理
 * - onDestroy() - 服务销毁时清理
 * - startLogCapture() - 开始日志捕获
 * - stopLogCapture() - 停止日志捕获
 * - createNotification() - 创建前台通知
 * - createNotificationChannel() - 创建通知渠道
 */
public class LogCaptureService extends Service {
    private static final String TAG = "LogCaptureService";
    private static final String CHANNEL_ID = "LogCaptureServiceChannel";
    private static final int NOTIFICATION_ID = 1; // 通知ID

    // 核心组件
    private ProcessController processController;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "LogCaptureService created");

        // 执行数据库迁移
        DatabaseMigration.migrateIfNeeded(this);

        // 初始化ConfigLoader单例
        ConfigLoader.getInstance().load(this);

        // 初始化ProcessController
        processController = ProcessController.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "LogCaptureService started");

        // 创建前台通知
        createNotification();

        // 开始日志捕获
        startLogCapture();

        // 返回START_STICKY确保服务被系统杀死后重启
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "LogCaptureService destroyed");

        // 停止日志捕获
        stopLogCapture();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 开始日志捕获
     */
    private void startLogCapture() {
        try {
            if (processController != null) {
                processController.startLogging("service");
                Log.i(TAG, "Log capture started");
            } else {
                Log.e(TAG, "ProcessController is null, cannot start log capture");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start log capture", e);
        }
    }

    /**
     * 停止日志捕获
     */
    private void stopLogCapture() {
        try {
            if (processController != null) {
                processController.stopLogging("service");
                Log.i(TAG, "Log capture stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop log capture", e);
        }
    }

    /**
     * 创建前台通知
     */
    private void createNotification() {
        // 创建通知渠道（Android 8.0及以上需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }

        // 创建点击通知时启动MainActivity的Intent
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 创建通知
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("XCLogger")
                .setContentText("XCLogger is running")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        // 启动前台服务
        startForeground(NOTIFICATION_ID, notification);
    }

    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Log Capture Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Channel for Log Capture Service");
            channel.setShowBadge(false);

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}