package com.xcheng.xclogger.control;

import android.content.Context;
import android.util.Log;
import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.processctr.LogServiceController;
import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommandSerialExecutor {
    private static final String TAG = "CommandSerialExecutor";

    public interface ResultCallback {
        void onResult(ControlResult result);
    }

    private static CommandSerialExecutor instance;
    private final ExecutorService singleExecutor = Executors.newSingleThreadExecutor();
    private final SourceWhitelistGuard whitelistGuard = new SourceWhitelistGuard();
    private final PartialConfigMerger configMerger = new PartialConfigMerger();

    private CommandSerialExecutor() {
    }

    public static synchronized CommandSerialExecutor getInstance() {
        if (instance == null) {
            instance = new CommandSerialExecutor();
        }
        return instance;
    }

    public void submit(Context context, ControlRequest request, ResultCallback callback) {
        singleExecutor.execute(() -> {
            ControlResult result = executeInternal(context, request);
            if (callback != null) {
                callback.onResult(result);
            }
        });
    }

    private ControlResult executeInternal(Context context, ControlRequest request) {
        ProcessController controller = ProcessController.getInstance(context);
        XcLoggerDatabase db = new XcLoggerDatabase(context);

        if (controller != null) {
            controller.recordOperationHistory("REQUEST_RECEIVED channel=" + request.getChannel() + ", op=" + request.getOpType());
            controller.recordOperationHistory("SOURCE_RESOLVED source=" + request.getResolvedSource());
        }

        // if (!whitelistGuard.isAllowed(request.getResolvedSource())) {
        //     if (controller != null) {
        //         controller.recordOperationHistory("SOURCE_REJECTED source=" + request.getResolvedSource());
        //     }
        //     return new ControlResult(false, "source not allowed", request.getOpType(), db.loadRunningState());
        // }

        if (controller != null) {
            controller.recordOperationHistory("SOURCE_ACCEPTED source=" + request.getResolvedSource());
            controller.recordOperationHistory("EXECUTE_START op=" + request.getOpType());
        }

        try {
            String opType = request.getOpType();
            switch (opType) {
                case "start":
                    LogServiceController.startLogService(context, request.getChannel() + ":" + request.getResolvedSource());
                    break;
                case "stop":
                    LogServiceController.stopLogService(context, request.getChannel() + ":" + request.getResolvedSource());
                    break;
                case "restart":
                    LogServiceController.restartService(context, request.getChannel() + ":" + request.getResolvedSource());
                    break;
                case "update_config":
                    applyConfigUpdate(context, request, db);
                    break;
                case "trigger_compress":
                    context.startService(new android.content.Intent(context, com.xcheng.xclogger.filemanager.FileCompressService.class));
                    break;
                case "query_status":
                    break;
                default:
                    return new ControlResult(false, "unknown op_type", opType, db.loadRunningState());
            }

            boolean running = db.loadRunningState();
            if (controller != null) {
                controller.recordOperationHistory("EXECUTE_END success=true, op=" + opType);
                controller.recordOperationHistory("STATE_AFTER_EXECUTE running=" + running);
            }
            return new ControlResult(true, "ok", opType, running);
        } catch (Exception e) {
            Log.e(TAG, "execute failed", e);
            boolean running = db.loadRunningState();
            if (controller != null) {
                controller.recordOperationHistory("EXECUTE_END success=false, op=" + request.getOpType() + ", err=" + e.getMessage());
                controller.recordOperationHistory("STATE_AFTER_EXECUTE running=" + running);
            }
            return new ControlResult(false, e.getMessage() == null ? "failed" : e.getMessage(), request.getOpType(), running);
        }
    }

    private void applyConfigUpdate(Context context, ControlRequest request, XcLoggerDatabase db) {
        XcLoggerConfig current = ConfigLoader.getInstance().getCurrentConfig();
        if (current == null) {
            current = ConfigLoader.getInstance().load(context);
        }
        XcLoggerConfig merged = configMerger.merge(current, request.getConfigPatch());

        boolean wasRunning = db.loadRunningState();
        if (wasRunning) {
            LogServiceController.stopLogService(context, request.getChannel() + ":" + request.getResolvedSource() + ":update_config_stop");
        }

        ConfigLoader.getInstance().updateConfig(context, merged);

        if (wasRunning) {
            LogServiceController.startLogService(context, request.getChannel() + ":" + request.getResolvedSource() + ":update_config_start");
        }
    }
}
