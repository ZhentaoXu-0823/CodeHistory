package com.xcheng.xclogger.processctr;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.xcheng.xclogger.service.LogCaptureService;
import com.xcheng.xclogger.util.XcLoggerDatabase;

/**
 * LogServiceController - 服务控制器
 *
 * 功能方法：
 * - startLogService() - 启动日志服务
 * - stopLogService() - 停止日志服务
 * - isServiceRunning() - 检查服务是否正在运行
 * - restartService() - 重启服务
 * - createServiceIntent() - 创建服务Intent
 */
public class LogServiceController {
    private static final String TAG = "LogServiceController";

    /**
     * 启动日志服务
     */
    public static void startLogService(Context context) {
        try {
            Log.i(TAG, "Starting log service");

            // 更新数据库状态
            XcLoggerDatabase database = new XcLoggerDatabase(context);
            database.saveRunningState(true);

            // 启动服务
            Intent serviceIntent = createServiceIntent(context);
            context.startForegroundService(serviceIntent);

            Log.i(TAG, "Log service started successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to start log service", e);
            throw new RuntimeException("Failed to start log service", e);
        }
    }

    /**
     * 停止日志服务
     */
    public static void stopLogService(Context context) {
        try {
            Log.i(TAG, "Stopping log service");

            // 更新数据库状态
            XcLoggerDatabase database = new XcLoggerDatabase(context);
            database.saveRunningState(false);

            // 停止服务
            Intent serviceIntent = createServiceIntent(context);
            context.stopService(serviceIntent);

            Log.i(TAG, "Log service stopped successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to stop log service", e);
            throw new RuntimeException("Failed to stop log service", e);
        }
    }

    /**
     * 检查服务是否正在运行
     */
    public static boolean isServiceRunning(Context context) {
        try {
            XcLoggerDatabase database = new XcLoggerDatabase(context);
            return database.loadRunningState();
        } catch (Exception e) {
            Log.e(TAG, "Failed to check service status", e);
            return false;
        }
    }

    /**
     * 重启服务
     */
    public static void restartService(Context context) {
        try {
            Log.i(TAG, "Restarting log service");

            // 先停止服务
            stopLogService(context);

            // 等待一小段时间
            Thread.sleep(1000); // 1秒等待时间

            // 再启动服务
            startLogService(context);

            Log.i(TAG, "Log service restarted successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to restart log service", e);
            throw new RuntimeException("Failed to restart log service", e);
        }
    }

    /**
     * 创建服务Intent
     */
    private static Intent createServiceIntent(Context context) {
        return new Intent(context, LogCaptureService.class);
    }
}