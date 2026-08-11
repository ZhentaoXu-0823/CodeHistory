package com.xcheng.xclogger.processctr;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final AtomicBoolean sServiceActive = new AtomicBoolean(false);
    private static final AtomicBoolean sStartRequestInFlight = new AtomicBoolean(false);

    /**
     * 启动日志服务
     */
    /**
     * Mark the service as active (called by LogCaptureService.onCreate).
     * This prevents duplicate start attempts from MY_PACKAGE_REPLACED 
     * when PMS has already triggered startForegroundService.
     */
    public static void setServiceActive(boolean active) {
        sServiceActive.set(active);
        if (!active) {
            sStartRequestInFlight.set(false);
        }
    }

    /**
     * Returns the real in-process capture state, not the persisted desired state.
     */
    public static boolean isActuallyRunning() {
        ProcessController controller = ProcessController.getInstance();
        return sServiceActive.get() && controller != null && controller.isRunning();
    }

    /**
     * Called after LogCaptureService has handled a start request.
     */
    public static void onStartRequestHandled() {
        sStartRequestInFlight.set(false);
    }

    public static void startLogService(Context context) {
        startLogService(context, "user");
    }

    /**
     * 启动日志服务（带来源）
     * @param source 触发来源，如 user/boot/broadcast:<action>/restart/internal
     */
    public static void startLogService(Context context, String source) {
        if (isActuallyRunning()) {
            Log.i(TAG, "Log capture is already running, skipping duplicate start (source:" + source + ")");
            return;
        }
        if (!sStartRequestInFlight.compareAndSet(false, true)) {
            Log.i(TAG, "Log service start is already pending, skipping duplicate request (source:" + source + ")");
            return;
        }
        try {
            Log.i(TAG, "Starting log service (source:" + source + ")");
            recordOperationHistory(context, "Log service start requested (source:" + source + ")");

            // 更新数据库状态
            XcLoggerDatabase database = new XcLoggerDatabase(context);
            database.saveRunningState(true);

            // 启动服务
            Intent serviceIntent = createServiceIntent(context, source);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            Log.i(TAG, "Log service started successfully");

        } catch (Exception e) {
            sStartRequestInFlight.set(false);
            Log.e(TAG, "Failed to start log service", e);
            recordOperationHistory(context, "Log service start failed (source:" + source + "): " + e.getMessage());
            throw new RuntimeException("Failed to start log service", e);
        }
    }

    /**
     * 停止日志服务
     */
    public static void stopLogService(Context context) {
        stopLogService(context, "user");
    }

    /**
     * 停止日志服务（带来源）
     * @param source 触发来源
     */
    public static void stopLogService(Context context, String source) {
        try {
            sServiceActive.set(false);
            sStartRequestInFlight.set(false);
            Log.i(TAG, "Stopping log service");
            recordOperationHistory(context, "Log service stop requested (source:" + source + ")");

            // 更新数据库状态
            XcLoggerDatabase database = new XcLoggerDatabase(context);
            database.saveRunningState(false);

            // 停止服务
            Intent serviceIntent = createServiceIntent(context, source);
            context.stopService(serviceIntent);

            Log.i(TAG, "Log service stopped successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to stop log service", e);
            recordOperationHistory(context, "Log service stop failed (source:" + source + "): " + e.getMessage());
            throw new RuntimeException("Failed to stop log service", e);
        }
    }

    /**
     * 检查服务是否正在运行
     */
    public static boolean isServiceRunning(Context context) {
        return isActuallyRunning();
    }

    /**
     * 重启服务
     */
    public static void restartService(Context context) {
        restartService(context, "restart");
    }

    /**
     * 重启服务（带来源）
     * @param source 触发来源
     */
    public static void restartService(Context context, String source) {
        try {
            Log.i(TAG, "Restarting log service");
            recordOperationHistory(context, "Log service restart requested (source:" + source + ")");

            // 先停止服务
            stopLogService(context, source);

            // 等待一小段时间
            Thread.sleep(1000); // 1秒等待时间

            // 再启动服务
            startLogService(context, source);

            Log.i(TAG, "Log service restarted successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to restart log service", e);
            recordOperationHistory(context, "Log service restart failed (source:" + source + "): " + e.getMessage());
            throw new RuntimeException("Failed to restart log service", e);
        }
    }

    /**
     * 创建服务Intent
     */
    private static Intent createServiceIntent(Context context, String source) {
        Intent intent = new Intent(context, LogCaptureService.class);
        intent.putExtra("source", source);
        return intent;
    }

    /**
     * 记录操作历史
     */
    private static void recordOperationHistory(Context context, String detail) {
        try {
            ProcessController controller = ProcessController.getInstance(context);
            if (controller != null) {
                controller.recordOperationHistory(detail);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to record operation history: " + detail, e);
        }
    }
}
