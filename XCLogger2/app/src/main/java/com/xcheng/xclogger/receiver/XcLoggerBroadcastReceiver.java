package com.xcheng.xclogger.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.xcheng.xclogger.processctr.LogServiceController;
import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.service.LogCaptureService;
import com.xcheng.xclogger.util.XcLoggerDatabase;

/**
 * XcLoggerBroadcastReceiver - 统一广播接收器
 *
 * 功能方法：
 * - onReceive() - 接收广播并分发处理
 * - handleBootCompleted() - 处理开机完成广播
 * - handleAppUpdated() - 处理应用升级广播
 * - handleMyPackageReplaced() - 处理自身应用替换广播
 * - handleAdbCmd() - 处理 ADB 命令广播
 * - startLogService() - 启动日志服务
 * - recordOperationHistory() - 记录操作历史
 */
public class XcLoggerBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "XcLoggerBroadcastReceiver";

    // ADB 命令广播
    private static final String ACTION_ADB_CMD = "com.xcheng.xclogger.ADB_CMD";
    private static final String EXTRA_CMD_NAME = "cmd_name";
    private static final String CMD_START = "start_xc_log";
    private static final String CMD_STOP = "stop_xc_log";
    private static final String CMD_FILE_COMPRESS = "file_compress";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Log.i(TAG, "Received broadcast: " + action);

        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
                handleBootCompleted(context);
                break;

            case Intent.ACTION_PACKAGE_REPLACED:
                handleAppUpdated(context, intent);
                break;

            case Intent.ACTION_MY_PACKAGE_REPLACED:
                handleMyPackageReplaced(context);
                break;

            case ACTION_ADB_CMD:
                handleAdbCmd(context, intent);
                break;
        }
    }

    /**
     * 处理开机完成广播
     */
    private void handleBootCompleted(Context context) {
        Log.i(TAG, "Boot completed, checking if service should start");

        try {
            XcLoggerDatabase database = new XcLoggerDatabase(context);
            boolean shouldRun = database.loadRunningState();

            if (shouldRun) {
                Log.i(TAG, "Starting service after boot");
                startLogService(context);
                recordOperationHistory(context, "Service auto-started after boot");
            } else {
                Log.i(TAG, "Service not configured to run, skipping");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling boot completed", e);
        }
    }

    /**
     * 处理应用升级广播
     */
    private void handleAppUpdated(Context context, Intent intent) {
        String packageName = intent.getDataString();
        if (packageName != null && packageName.contains(context.getPackageName())) {
            Log.i(TAG, "App updated, restarting service if needed");
            handleMyPackageReplaced(context);
        }
    }

    /**
     * 处理自身应用替换广播
     */
    private void handleMyPackageReplaced(Context context) {
        Log.i(TAG, "My package replaced, checking service status");

        try {
            XcLoggerDatabase database = new XcLoggerDatabase(context);
            boolean shouldRun = database.loadRunningState();

            if (shouldRun) {
                Log.i(TAG, "Restarting service after app update");
                startLogService(context);
                recordOperationHistory(context, "Service restarted after app update");
            } else {
                Log.i(TAG, "Service not configured to run after update");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling package replaced", e);
        }
    }

    /**
     * 处理 ADB 命令广播
     */
    private void handleAdbCmd(Context context, Intent intent) {
        String cmd = intent.getStringExtra(EXTRA_CMD_NAME);
        Log.i(TAG, "ADB CMD received: " + cmd);

        if (cmd == null || cmd.isEmpty()) {
            recordOperationHistory(context, "ADB_CMD received with empty cmd_name");
            return;
        }

        switch (cmd) {
            case CMD_START:
                LogServiceController.startLogService(context, "broadcast:adb_cmd");
                recordOperationHistory(context, "ADB_CMD start_xc_log received, service start requested (source:adb_cmd)");
                break;
            case CMD_STOP:
                LogServiceController.stopLogService(context, "broadcast:adb_cmd");
                recordOperationHistory(context, "ADB_CMD stop_xc_log received, service stop requested (source:adb_cmd)");
                break;
            case CMD_FILE_COMPRESS:
                Log.i(TAG, "File compressing.");
                recordOperationHistory(context, "ADB_CMD file_compress received, action=File compressing.");
                break;
            default:
                recordOperationHistory(context, "ADB_CMD unknown cmd_name: " + cmd);
                break;
        }
    }

    /**
     * 启动日志服务（默认来源不区分）
     */
    private void startLogService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, LogCaptureService.class);
            context.startForegroundService(serviceIntent);
            Log.i(TAG, "Log service started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start log service", e);
            recordOperationHistory(context, "Error: Failed to start service - " + e.getMessage());
        }
    }

    /**
     * 记录操作历史
     */
    private void recordOperationHistory(Context context, String operation) {
        try {
            // 通过ProcessController记录操作历史
            ProcessController controller = ProcessController.getInstance(context);
            controller.recordOperationHistory(operation);
        } catch (Exception e) {
            Log.e(TAG, "Failed to record operation history", e);
        }
    }
}