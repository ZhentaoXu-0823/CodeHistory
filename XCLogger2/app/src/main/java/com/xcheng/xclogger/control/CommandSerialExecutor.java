package com.xcheng.xclogger.control;

import android.content.Context;
import android.util.Log;
import com.xcheng.xclogger.filemanager.FileCompressService;
import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.processctr.LogServiceController;
import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommandSerialExecutor {
    private static final String TAG = "CommandSerialExecutor";
    public interface ResultCallback { void onResult(ControlResult result); }
    private static CommandSerialExecutor instance;
    private final ExecutorService singleExecutor = Executors.newSingleThreadExecutor();
    private final PartialConfigMerger configMerger = new PartialConfigMerger();
    private CommandSerialExecutor() {}
    public static synchronized CommandSerialExecutor getInstance() { if (instance == null) instance = new CommandSerialExecutor(); return instance; }
    public void submit(Context context, ControlRequest request, ResultCallback callback) { singleExecutor.execute(() -> { ControlResult result = executeInternal(context, request); if (callback != null) callback.onResult(result); }); }

    private ControlResult executeInternal(Context context, ControlRequest request) {
        ProcessController controller = ProcessController.getInstance(context);
        XcLoggerDatabase db = new XcLoggerDatabase(context);
        if (controller != null) {
            controller.recordOperationHistory("REQUEST_RECEIVED channel=" + request.getChannel() + ", op=" + request.getOpType());
            controller.recordOperationHistory("SOURCE_RESOLVED source=" + request.getResolvedSource());
            controller.recordOperationHistory("SOURCE_ACCEPTED source=" + request.getResolvedSource());
            controller.recordOperationHistory("EXECUTE_START op=" + request.getOpType());
        }
        try {
            String opType = request.getOpType();
            switch (opType) {
                case "start": LogServiceController.startLogService(context, request.getChannel() + ":" + request.getResolvedSource()); break;
                case "stop": LogServiceController.stopLogService(context, request.getChannel() + ":" + request.getResolvedSource()); break;
                case "restart": LogServiceController.restartService(context, request.getChannel() + ":" + request.getResolvedSource()); break;
                case "update_config": applyConfigUpdate(context, request, db); break;
                case "trigger_compress":
                    context.startService(new android.content.Intent(context, FileCompressService.class));
                    if (controller != null) controller.recordOperationHistory("EXECUTE_END async_pending, op=" + opType);
                    return new ControlResult(true, "async_result_pending", opType, db.loadRunningState(), db.getCompressState(), db.getPendingZipFiles(), db.getUploadFailCount(), 3);
                case "upload_result": return buildCompressResult(request.getOpType(), FileCompressService.upload(context, request.isUploadSuccess()), db.loadRunningState(), controller);
                case "query_compress_status": return buildCompressResult(request.getOpType(), FileCompressService.query(context), db.loadRunningState(), controller);
                case "cancel_compress": return buildCompressResult(request.getOpType(), FileCompressService.cancel(context), db.loadRunningState(), controller);
                case "query_status": break;
                default: return new ControlResult(false, "unknown op_type", opType, db.loadRunningState());
            }
            boolean running = db.loadRunningState();
            if (controller != null) {
                controller.recordOperationHistory("EXECUTE_END success=true, op=" + opType);
                controller.recordOperationHistory("STATE_AFTER_EXECUTE running=" + running);
            }
            return new ControlResult(true, "ok", opType, running, db.getCompressState(), db.getPendingZipFiles(), db.getUploadFailCount(), 3);
        } catch (Exception e) {
            Log.e(TAG, "execute failed", e);
            boolean running = db.loadRunningState();
            if (controller != null) {
                controller.recordOperationHistory("EXECUTE_END success=false, op=" + request.getOpType() + ", err=" + e.getMessage());
                controller.recordOperationHistory("STATE_AFTER_EXECUTE running=" + running);
            }
            return new ControlResult(false, e.getMessage() == null ? "failed" : e.getMessage(), request.getOpType(), running, db.getCompressState(), db.getPendingZipFiles(), db.getUploadFailCount(), 3);
        }
    }

    private ControlResult buildCompressResult(String opType, FileCompressService.Result r, boolean running, ProcessController controller) {
        if (controller != null) controller.recordOperationHistory("EXECUTE_END success=" + r.success + ", op=" + opType + ", msg=" + r.message);
        return new ControlResult(r.success, r.message, opType, running, r.state, r.zipFiles, r.retryCount, r.maxRetryCount);
    }

    private void applyConfigUpdate(Context context, ControlRequest request, XcLoggerDatabase db) {
        XcLoggerConfig current = ConfigLoader.getInstance().getCurrentConfig();
        if (current == null) current = ConfigLoader.getInstance().load(context);
        XcLoggerConfig merged = configMerger.merge(current, request.getConfigPatch());
        ProcessController controller = ProcessController.getInstance(context);
        if (controller != null) {
            controller.recordOperationHistory("Config update diff: " + buildConfigDiff(current, merged));
        }
        boolean wasRunning = db.loadRunningState();
        if (wasRunning) LogServiceController.stopLogService(context, request.getChannel() + ":" + request.getResolvedSource() + ":update_config_stop");
        ConfigLoader.getInstance().updateConfig(context, merged);
        if (wasRunning) LogServiceController.startLogService(context, request.getChannel() + ":" + request.getResolvedSource() + ":update_config_start");
    }

    private String buildConfigDiff(XcLoggerConfig oldConfig, XcLoggerConfig newConfig) {
        if (oldConfig == null || newConfig == null) return "unknown";
        StringBuilder diff = new StringBuilder();
        appendIntDiff(diff, "total_size_gb", oldConfig.getTotalSizeGb(), newConfig.getTotalSizeGb());
        appendIntDiff(diff, "file_size_mb", oldConfig.getFileSizeMb(), newConfig.getFileSizeMb());
        appendIntDiff(diff, "buffer_size_bytes", oldConfig.getBufferSizeBytes(), newConfig.getBufferSizeBytes());
        appendStringDiff(diff, "log_dir", oldConfig.getLogDir(), newConfig.getLogDir());
        appendIntDiff(diff, "log_period_hours", oldConfig.getLogPeriodHours(), newConfig.getLogPeriodHours());
        appendStringDiff(diff, "filter_tag", oldConfig.getFilterTag(), newConfig.getFilterTag());
        appendStringDiff(diff, "filter_level", oldConfig.getFilterLevel(), newConfig.getFilterLevel());
        appendStringDiff(diff, "filter_package", oldConfig.getFilterPackage(), newConfig.getFilterPackage());
        return diff.length() == 0 ? "no effective changes" : diff.toString();
    }

    private void appendIntDiff(StringBuilder diff, String name, int oldValue, int newValue) {
        if (oldValue != newValue) appendDiff(diff, name, String.valueOf(oldValue), String.valueOf(newValue));
    }

    private void appendStringDiff(StringBuilder diff, String name, String oldValue, String newValue) {
        if (!Objects.equals(oldValue, newValue)) appendDiff(diff, name, oldValue, newValue);
    }

    private void appendDiff(StringBuilder diff, String name, String oldValue, String newValue) {
        if (diff.length() > 0) diff.append(", ");
        diff.append(name).append(": ").append(oldValue).append(" -> ").append(newValue);
    }
}
