package com.xcheng.xcloggertestdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.util.Log;

import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerConfigUpdateResult;
import com.xcheng.xclogger.util.XcLoggerConfigUpdater;

/**
 * Receives AUTO_TEST broadcasts and executes AIDL methods via XcLoggerClient.
 * Returns AUTO_TEST_RESULT broadcast with PASS/FAIL for each op.
 *
 * Usage (adb / bat script):
 *   adb shell am broadcast -a com.xcheng.xcloggertestdemo.AUTO_TEST --es op start
 *   adb logcat -d -s AutoTest | grep "op=start.*result=PASS"
 *
 * All ops return a result broadcast — no silent failures.
 */
public class AutoTestReceiver extends BroadcastReceiver {
    private static final String TAG = "AutoTest";
    static final String ACTION_TEST = "com.xcheng.xcloggertestdemo.AUTO_TEST";
    static final String ACTION_RESULT = "com.xcheng.xcloggertestdemo.AUTO_TEST_RESULT";

    private static AutoTestReceiver instance;

    public static void register(Context ctx) {
        if (instance == null) {
            instance = new AutoTestReceiver();
            ctx.registerReceiver(instance, new IntentFilter(ACTION_TEST));
            Log.i(TAG, "[AUTO] Receiver registered");
        }
    }

    public static void unregister(Context ctx) {
        if (instance != null) {
            try { ctx.unregisterReceiver(instance); } catch (Exception e) { /* ignore */ }
            instance = null;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String op = intent.getStringExtra("op");
        if (op == null) { sendResult(context, "", "FAIL", "missing op"); return; }

        XcLoggerClient client = XcLoggerTestService.getClient();
        if (client == null || !client.isBound()) {
            sendResult(context, op, "FAIL", "client not bound");
            return;
        }
        if ("config_update_v2".equals(op)) {
            executeConfigUpdate2(context, intent, client, goAsync());
            return;
        }

        long start = System.currentTimeMillis();
        boolean ok = false;
        String msg = "";

        try {
            switch (op) {
                // ── Basic control ──
                case "start":
                    ok = client.startLogging();
                    msg = ok ? "started" : "start failed";
                    break;
                case "stop":
                    ok = client.stopLogging();
                    msg = ok ? "stopped" : "stop failed";
                    break;
                case "query_status":
                    ok = client.isRunning();
                    msg = ok ? "RUNNING" : "STOPPED";
                    break;

                // ── Config ──
                case "config_get": {
                    XcLoggerConfig cfg = client.getConfiguration();
                    ok = cfg != null;
                    msg = ok ? "totalSizeMb=" + cfg.getTotalSizeMb()
                            + ",filterTag=" + cfg.getFilterTag()
                            + ",filterPackage=" + cfg.getFilterPackage()
                            + ",packageBlacklist=" + cfg.getFilterPackageBlacklist()
                            + ",logDir=" + cfg.getLogDir() : "null config";
                    break;
                }
                case "config_update": {
                    XcLoggerConfig patch = new XcLoggerConfig();
                    // Apply extras as config fields
                    if (intent.hasExtra("totalSizeMb")) patch.setTotalSizeMb(intent.getIntExtra("totalSizeMb", 0));
                    if (intent.hasExtra("fileSizeMb")) patch.setFileSizeMb(intent.getIntExtra("fileSizeMb", 0));
                    if (intent.hasExtra("logPeriodHours")) patch.setLogPeriodHours(intent.getIntExtra("logPeriodHours", 0));
                    if (intent.hasExtra("filterTag")) patch.setFilterTag(intent.getStringExtra("filterTag"));
                    if (intent.hasExtra("filterLevel")) patch.setFilterLevel(intent.getStringExtra("filterLevel"));
                    if (intent.hasExtra("filterPackage")) patch.setFilterPackage(intent.getStringExtra("filterPackage"));
                    ok = client.updateConfigurationPartial(patch);
                    msg = ok ? "updated" : "update failed";
                    break;
                }
                case "emit_log": {
                    String marker = intent.getStringExtra("marker");
                    if (marker == null || marker.trim().isEmpty()) marker = "XCLOGGER_DEMO_MARKER";
                    Log.i("XcLoggerPkgProbe", marker);
                    ok = true;
                    msg = "emitted marker=" + marker;
                    break;
                }

                // ── Compress ──
                case "compress_full":
                    ok = client.triggerCompression();
                    msg = ok ? "request accepted (async, wait callback)" : "request rejected";
                    break;
                case "compress_range":
                    String st = intent.getStringExtra("startTime");
                    String et = intent.getStringExtra("endTime");
                    ok = client.triggerCompressionWithRange(st, et);
                    msg = ok ? "request accepted" : "invalid params or rejected";
                    break;
                case "compress_status":
                    msg = client.getCompressStatus();
                    ok = msg != null;
                    break;
                case "upload_ok":
                    ok = client.reportUploadResult(true);
                    msg = ok ? "upload result reported OK" : "report failed";
                    break;
                case "upload_fail":
                    ok = client.reportUploadResult(false);
                    msg = ok ? "upload result reported FAIL" : "report failed";
                    break;
                case "compress_cancel":
                    ok = client.cancelCompressTask();
                    msg = ok ? "cancelled" : "cancel failed";
                    break;
                case "zip_fetch":
                    String contentUri = intent.getStringExtra("contentUri");
                    if (contentUri == null || contentUri.trim().isEmpty()) {
                        ok = false;
                        msg = "contentUri required; use the compression page file picker";
                        break;
                    }
                    long bytes = client.fetchZipToUri(Uri.parse(contentUri));
                    ok = bytes > 0;
                    msg = ok ? bytes + " bytes -> " + contentUri : "fetch failed";
                    break;

                // ── Suite ──
                case "suite_smoke": {
                    ok = client.startLogging() && client.isRunning() && client.stopLogging() && !client.isRunning();
                    msg = ok ? "smoke PASS (start→status→stop→status)" : "smoke FAIL";
                    break;
                }
                case "suite_full": {
                    boolean allOk = true;
                    StringBuilder sb = new StringBuilder();
                    allOk &= check(allOk, sb, "start", client.startLogging());
                    allOk &= check(allOk, sb, "status", client.isRunning());
                    XcLoggerConfig c = client.getConfiguration();
                    allOk &= check(allOk, sb, "config_get", c != null);
                    if (c != null) {
                        c.setFilterTag("TestTag");
                        allOk &= check(allOk, sb, "config_update", client.updateConfigurationPartial(c));
                    }
                    allOk &= check(allOk, sb, "compress_full", client.triggerCompression());
                    String cs = client.getCompressStatus();
                    allOk &= check(allOk, sb, "compress_status", cs != null);
                    allOk &= check(allOk, sb, "stop", client.stopLogging());
                    ok = allOk;
                    msg = sb.toString();
                    break;
                }

                default:
                    sendResult(context, op, "FAIL", "unknown op: " + op);
                    return;
            }
        } catch (Exception e) {
            ok = false;
            msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            Log.e(TAG, "[AUTO] Exception in op=" + op, e);
        }

        long duration = System.currentTimeMillis() - start;
        sendResult(context, op, ok ? "PASS" : "FAIL", msg + " (" + duration + "ms)");

        // Also log to logcat for adb logcat -s AutoTest verification
        Log.i(TAG, "[RESULT] op=" + op + " result=" + (ok ? "PASS" : "FAIL")
                + " message=" + msg + " duration_ms=" + duration);
    }

    private boolean check(boolean acc, StringBuilder sb, String name, boolean result) {
        sb.append(name).append("=").append(result ? "PASS" : "FAIL").append(";");
        return acc && result;
    }

    private void executeConfigUpdate2(Context context, Intent intent, XcLoggerClient client,
                                       PendingResult pendingResult) {
        long start = System.currentTimeMillis();
        try {
            XcLoggerConfigUpdater updater = client.configUpdater();
            if (intent.hasExtra("totalSizeMb")) updater.totalSizeMb(intent.getIntExtra("totalSizeMb", 0));
            if (intent.hasExtra("fileSizeMb")) updater.fileSizeMb(intent.getIntExtra("fileSizeMb", 0));
            if (intent.hasExtra("bufferSizeBytes")) updater.bufferSizeBytes(intent.getIntExtra("bufferSizeBytes", 0));
            if (intent.hasExtra("logDir")) updater.logDir(intent.getStringExtra("logDir"));
            if (intent.hasExtra("logPeriodHours")) updater.logPeriodHours(intent.getIntExtra("logPeriodHours", 0));
            if (intent.hasExtra("filterLevel")) updater.filterLevel(intent.getStringExtra("filterLevel"));
            if (intent.hasExtra("filterTag")) updater.filterTagsCsv(intent.getStringExtra("filterTag"));
            if (intent.hasExtra("addTag")) updater.addTag(intent.getStringExtra("addTag"));
            if (intent.hasExtra("removeTag")) updater.removeTag(intent.getStringExtra("removeTag"));
            if (intent.hasExtra("filterPackage")) updater.filterPackagesCsv(intent.getStringExtra("filterPackage"));
            if (intent.hasExtra("addPackage")) updater.addPackage(intent.getStringExtra("addPackage"));
            if (intent.hasExtra("removePackage")) updater.removePackage(intent.getStringExtra("removePackage"));
            if (intent.hasExtra("packageBlacklist")) updater.blacklistPackagesCsv(intent.getStringExtra("packageBlacklist"));
            if (intent.hasExtra("addBlacklistedPackage")) updater.addBlacklistedPackage(intent.getStringExtra("addBlacklistedPackage"));
            if (intent.hasExtra("removeBlacklistedPackage")) updater.removeBlacklistedPackage(intent.getStringExtra("removeBlacklistedPackage"));
            if (intent.hasExtra("packageFilterMode")) {
                updater.packageFilterMode(intent.getStringExtra("packageFilterMode"));
            }

            updater.commitAsync(result -> {
                long duration = System.currentTimeMillis() - start;
                String message = "status=" + result.getStatus() + ",requestId=" + result.getRequestId()
                        + ",changed=" + result.getChangedFields() + ",restarted="
                        + result.isServiceRestarted() + ",message=" + result.getMessage();
                sendResult(context, "config_update_v2", result.isSuccess() ? "PASS" : "FAIL",
                        message + " (" + duration + "ms)");
                Log.i(TAG, "[RESULT] op=config_update_v2 result="
                        + (result.isSuccess() ? "PASS" : "FAIL") + " " + message);
                pendingResult.finish();
            });
        } catch (Exception e) {
            sendResult(context, "config_update_v2", "FAIL", e.getMessage());
            pendingResult.finish();
        }
    }

    private void sendResult(Context ctx, String op, String result, String message) {
        Intent i = new Intent(ACTION_RESULT);
        i.putExtra("op", op);
        i.putExtra("result", result);
        i.putExtra("message", message);
        ctx.sendBroadcast(i);
    }
}
