package com.xcheng.xclogger.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.xcheng.xclogger.filemanager.FileCompressService;
import com.xcheng.xclogger.processctr.LogServiceController;
import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.service.LogCaptureService;
import com.xcheng.xclogger.service.RemoteBindService;
import com.xcheng.xclogger.util.XcLoggerDatabase;

/**
 * XcLoggerBroadcastReceiver - 统一广播接收器
 */
public class XcLoggerBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "XcLoggerBroadcastReceiver";

    // ADB 命令广播
    private static final String ACTION_ADB_CMD = "com.xcheng.xclogger.ADB_CMD";
    private static final String EXTRA_CMD_NAME = "cmd_name";
    private static final String CMD_START = "start_xc_log";
    private static final String CMD_STOP = "stop_xc_log";
    private static final String CMD_FILE_COMPRESS = "file_compress";

    // 文件压缩广播（大写）
    private static final String ACTION_FILE_COMPRESS = "com.xcheng.xclogger.FILE_COMPRESS";

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

            case ACTION_FILE_COMPRESS:
                startFileCompressService(context, intent);
                break;
        }
    }

    private void startRemoteBindService(Context context) {
        Intent intent = new Intent(context, RemoteBindService.class);
        context.startService(intent);
    }

    private void handleBootCompleted(Context context) {
        try {
            XcLoggerDatabase database = new XcLoggerDatabase(context);
            boolean shouldRun = database.loadRunningState();
            if (shouldRun) {
                startLogService(context);
                recordOperationHistory(context, "Service auto-started after boot");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling boot completed", e);
        } finally {
            startRemoteBindService(context);
        }
    }

    private void handleAppUpdated(Context context, Intent intent) {
        String packageName = intent.getDataString();
        if (packageName != null && packageName.contains(context.getPackageName())) {
            handleMyPackageReplaced(context);
        }
    }

    private void handleMyPackageReplaced(Context context) {
        try {
            XcLoggerDatabase database = new XcLoggerDatabase(context);
            boolean shouldRun = database.loadRunningState();
            if (shouldRun) {
                startLogService(context);
                recordOperationHistory(context, "Service restarted after app update");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling package replaced", e);
        } finally {
            startRemoteBindService(context);
        }
    }

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
                startFileCompressService(context, intent);
                break;
            default:
                recordOperationHistory(context, "ADB_CMD unknown cmd_name: " + cmd);
                break;
        }
    }

    private void startFileCompressService(Context context, Intent srcIntent) {
        try {
            Intent serviceIntent = new Intent(context, FileCompressService.class);
            // 保留请求方包名，用于定向回发
            if (srcIntent.getPackage() != null) {
                serviceIntent.setPackage(srcIntent.getPackage());
            }
            context.startService(serviceIntent);
            recordOperationHistory(context, "File compress service requested");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start FileCompressService", e);
            recordOperationHistory(context, "Error: Failed to start FileCompressService - " + e.getMessage());
        }
    }

    private void startLogService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, LogCaptureService.class);
            context.startForegroundService(serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start log service", e);
            recordOperationHistory(context, "Error: Failed to start service - " + e.getMessage());
        }
    }

    private void recordOperationHistory(Context context, String operation) {
        try {
            ProcessController controller = ProcessController.getInstance(context);
            if (controller != null) {
                controller.recordOperationHistory(operation);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to record operation history", e);
        }
    }
}