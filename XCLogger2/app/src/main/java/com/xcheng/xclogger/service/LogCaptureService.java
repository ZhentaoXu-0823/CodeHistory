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
import com.xcheng.xclogger.filemanager.FileManager;
import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.recorder.SystemLogCatcher;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

/**
 * LogCaptureService - 前台日志捕获服务
 *
 * 功能方法：
 * - onCreate() - 服务创建，初始化组件
 * - onStartCommand() - 启动服务，开始日志捕获
 * - onDestroy() - 服务销毁，清理资源
 * - onBind() - 绑定服务接口
 * - createNotificationChannel() - 创建通知渠道
 * - createNotification() - 创建前台通知
 * - startLogCapture() - 启动日志捕获
 * - stopLogCapture() - 停止日志捕获
 * - checkServiceStatus() - 检查服务状态
 */
public class LogCaptureService extends Service {
    private static final String TAG = "LogCaptureService";
    private static final String CHANNEL_ID = "XcLoggerChannel";
    private static final int NOTIFICATION_ID = 1001;

    private ProcessController processController;
    private XcLoggerDatabase database;
    private XcLoggerConfig config;
    private boolean isServiceRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "LogCaptureService created");

        // 初始化组件
        processController = ProcessController.getInstance(this);
        database = new XcLoggerDatabase(this);
        config = ConfigLoader.current();

        // 创建通知渠道
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "LogCaptureService started");

        // 检查数据库状态
        if (!checkServiceStatus()) {
            Log.w(TAG, "Service should not be running, stopping");
            stopSelf();
            return START_NOT_STICKY;
        }

        // 创建前台通知
        startForeground(NOTIFICATION_ID, createNotification());

        // 启动日志捕获
        startLogCapture();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "LogCaptureService destroyed");
        stopLogCapture();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "XcLogger Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("XcLogger log capture service");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 创建前台通知
     */
    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("XCLogger")
                .setContentText("XCLogger is running")
                .setSmallIcon(R.drawable.xcloggericon)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * 启动日志捕获
     */
    private void startLogCapture() {
        try {
            if (isServiceRunning) {
                Log.w(TAG, "Log capture already running");
                return;
            }

            // 使用ProcessController启动日志捕获
            processController.startLogging();
            isServiceRunning = true;

            Log.i(TAG, "Log capture started successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to start log capture", e);
            recordError("Service start failed: " + e.getMessage());
            stopSelf();
        }
    }

    /**
     * 停止日志捕获
     */
    private void stopLogCapture() {
        try {
            if (isServiceRunning) {
                processController.stopLogging();
                isServiceRunning = false;
                Log.i(TAG, "Log capture stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping log capture", e);
        }
    }

    /**
     * 检查服务状态
     */
    private boolean checkServiceStatus() {
        return database.loadRunningState();
    }

    /**
     * 记录错误信息
     */
    private void recordError(String error) {
        try {
            processController.recordOperationHistory("Error: " + error);
        } catch (Exception e) {
            Log.e(TAG, "Failed to record error", e);
        }
    }
}