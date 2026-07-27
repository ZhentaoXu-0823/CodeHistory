package com.xcheng.xclogger.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.xcheng.xclogger.control.CommandSerialExecutor;
import com.xcheng.xclogger.control.ControlRequest;
import com.xcheng.xclogger.control.ControlResult;
import com.xcheng.xclogger.control.SourceResolver;
import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.service.RemoteBindService;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.processctr.LogServiceController;
import com.xcheng.xclogger.util.XcLoggerDatabase;

/**
 * XcLoggerBroadcastReceiver - 统一广播接收器
 */
public class XcLoggerBroadcastReceiver extends BroadcastReceiver {
    public static final String[] TARGET_PACKAGES = {
        "com.xcheng.mdm",
        "com.xcheng.xcloggertestdemo",
        "com.xcheng.xclogger",
        "com.ko.xclogger"
    };
    private static final String TAG = "XcLoggerBroadcastReceiver";

    private static final String ACTION_ADB_CMD = "com.xcheng.xclogger.ADB_CMD";
    private static final String ACTION_CTRL_REQUEST = "com.xcheng.xclogger.CTRL_REQUEST";
    private static final String ACTION_CTRL_RESULT = "com.xcheng.xclogger.CTRL_RESULT";

    private static final String EXTRA_CMD_NAME = "cmd_name";
    private static final String CMD_START = "start_xc_log";
    private static final String CMD_STOP = "stop_xc_log";

    private static final String EXTRA_OP_TYPE = "op_type";

    @Override
    public void onReceive(Context context, Intent intent) {
        // ==== VERSION MARKER v1.2.14-TARGETFIX ==== 
        // TARGET_PACKAGES: com.xcheng.mdm, com.xcheng.xcloggertestdemo, com.xcheng.xclogger, com.ko.xclogger
        // If you see this line in logcat, you have the FIXED version (compress callbacks work).
        Log.d(TAG, "==== XCLOGGER v1.2.14-TARGETFIX ACTIVE | TARGET_PACKAGES: mdmdemo/common/pinelabs ====");
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Log.i(TAG, "Received broadcast: " + action);
        recordOperationHistory(context, "Broadcast received: " + action);

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
            case Intent.ACTION_PACKAGE_ADDED:
                handlePackageAdded(context, intent);
                break;
            case Intent.ACTION_PACKAGE_REMOVED:
                handlePackageRemoved(context, intent);
                break;
            case ACTION_ADB_CMD:
                handleAdbCmd(context, intent);
                break;
            case ACTION_CTRL_REQUEST:
                handleControlRequest(context, intent);
                break;
            default:
                break;
        }
    }

    private Intent buildSimpleIntent(String opType, Intent src) {
        Intent i = new Intent(src);
        i.putExtra(EXTRA_OP_TYPE, opType);
        return i;
    }

    private void startRemoteBindService(Context context) {
        Intent intent = new Intent(context, RemoteBindService.class);
        context.startService(intent);
    }

    private void handleBootCompleted(Context context) {
        try {
            boolean shouldRun = resolveStartupState(context);
            if (shouldRun) {
                LogServiceController.startLogService(context, "upgrade");
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
            boolean shouldRun = resolveStartupState(context);
            if (shouldRun) {
                LogServiceController.startLogService(context, "upgrade");
                recordOperationHistory(context, "Service restarted after app update");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling package replaced", e);
        } finally {
            startRemoteBindService(context);
        }
    }

    private boolean resolveStartupState(Context context) {
        ConfigLoader loader = ConfigLoader.getInstance();
        loader.load(context);
        if (loader.wasLastLoadInitializedFromXml()) {
            boolean initialAutoStart = loader.getLastInitialAutoStartEnabled();
            recordOperationHistory(context, "Initial config imported, initial_auto_start=" + initialAutoStart);
            return initialAutoStart;
        }
        XcLoggerDatabase database = new XcLoggerDatabase(context);
        return database.loadRunningState();
    }

    private Intent buildControlIntent(String opType) {
        Intent i = new Intent(ACTION_CTRL_REQUEST);
        i.putExtra(EXTRA_OP_TYPE, opType);
        return i;
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
                handleControlRequest(context, buildControlIntent("start"));
                break;
            case CMD_STOP:
                handleControlRequest(context, buildControlIntent("stop"));
                break;
            default:
                recordOperationHistory(context, "ADB_CMD unknown cmd_name: " + cmd);
                break;
        }
    }

    private void handleControlRequest(Context context, Intent intent) {
        String opType = intent.getStringExtra(EXTRA_OP_TYPE);
        if (opType == null || opType.trim().isEmpty()) {
            opType = "query_status";
        }

        SourceResolver resolver = new SourceResolver();
        String resolvedSource = resolver.resolveFromBroadcast(context, intent);
        XcLoggerConfig patch = buildConfigPatch(intent);
        boolean uploadSuccess = intent.getBooleanExtra("success", false);
        String startTime = intent.getStringExtra("startTime");
        String endTime = intent.getStringExtra("endTime");
        String configFilePath = intent.getStringExtra("config_file_path");
        // [CompressTAG] trace
        if ("trigger_compress".equals(opType) || "import_config".equals(opType)) {
            android.util.Log.i("XcLoggerBroadcastReceiver", "[CompressTAG] RX broadcast: opType=" + opType
                    + ", startTime=" + startTime + ", endTime=" + endTime
                    + ", file=" + configFilePath);
        }
        ControlRequest request = new ControlRequest("broadcast", opType, resolvedSource, patch, uploadSuccess, startTime, endTime, configFilePath);

        CommandSerialExecutor.getInstance().submit(context, request, result -> sendControlResult(context, result));
    }

    private XcLoggerConfig buildConfigPatch(Intent intent) {
        XcLoggerConfig patch = new XcLoggerConfig();

        if (intent.hasExtra("total_size")) patch.setTotalSizeMb(intent.getIntExtra("total_size", 0));
        if (intent.hasExtra("file_size")) patch.setFileSizeMb(intent.getIntExtra("file_size", 0));
        if (intent.hasExtra("buffer_size")) patch.setBufferSizeBytes(intent.getIntExtra("buffer_size", 0));
        if (intent.hasExtra("log_dir")) patch.setLogDir(intent.getStringExtra("log_dir"));
        if (intent.hasExtra("log_period")) patch.setLogPeriodHours(intent.getIntExtra("log_period", 0));
        if (intent.hasExtra("filter_tag")) patch.setFilterTag(intent.getStringExtra("filter_tag"));
        if (intent.hasExtra("filter_level")) patch.setFilterLevel(intent.getStringExtra("filter_level"));
        if (intent.hasExtra("filter_package")) patch.setFilterPackage(intent.getStringExtra("filter_package"));
        // v1.2.2: white+black list extensions
        if (intent.hasExtra("filter_tag_blacklist")) patch.setFilterTagBlacklist(intent.getStringExtra("filter_tag_blacklist"));
        if (intent.hasExtra("filter_package_blacklist")) patch.setFilterPackageBlacklist(intent.getStringExtra("filter_package_blacklist"));
        if (intent.hasExtra("filter_level_blacklist")) patch.setFilterLevelBlacklist(intent.getStringExtra("filter_level_blacklist"));
        if (intent.hasExtra("filter_content")) patch.setFilterContent(intent.getStringExtra("filter_content"));
        if (intent.hasExtra("filter_content_blacklist")) patch.setFilterContentBlacklist(intent.getStringExtra("filter_content_blacklist"));

        return patch;
    }

    private void sendControlResult(Context context, ControlResult result) {
        if (result == null || "async_result_pending".equals(result.getMessage())) {
            return;
        }
        Intent ret = new Intent(ACTION_CTRL_RESULT);
        ret.putExtra("success", result.isSuccess());
        ret.putExtra("message", result.getMessage());
        ret.putExtra("op_type", result.getOpType());
        ret.putExtra("running_state", result.isRunningState());
        ret.putExtra("compress_state", result.getCompressState());
        ret.putExtra("zip_files", result.getZipFiles());
        ret.putExtra("retry_count", result.getRetryCount());
        ret.putExtra("max_retry_count", result.getMaxRetryCount());
        for (String pkg : TARGET_PACKAGES) {
            ret.setPackage(pkg);
            context.sendBroadcast(ret);
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

    private void handlePackageAdded(Context context, Intent intent) {
        String packageName = PackageEventManager.extractPackageName(intent);
        if (packageName == null) return;
        Log.i(TAG, "Package added: " + packageName);
        PackageEventManager.handlePackageInstalled(context, packageName);
    }

    private void handlePackageRemoved(Context context, Intent intent) {
        String packageName = PackageEventManager.extractPackageName(intent);
        if (packageName == null) return;
        Log.i(TAG, "Package removed: " + packageName);

        boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
        if (replacing) {
            Log.d(TAG, "Package is being replaced (update), skip UID removal");
            return;
        }

        int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
        PackageEventManager.handlePackageRemoved(context, uid, packageName);
    }
}
