package com.xcheng.xcloggertestdemo;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Compression full-chain test — trigger, status polling, ZIP fetch, upload result.
 * All AIDL callbacks displayed in real-time in scrollable log area.
 */
public class CompressTestActivity extends Activity {
    private static final String TAG = "XcLoggerDemo";
    private static final String EXPORT_DIRECTORY = "XCLogger";

    private Button btnFull, btnRange, btnStatus, btnFetchDownloads, btnFetchAppDir;
    private Button btnOk, btnFail, btnCancel;
    private EditText etStart, etEnd;
    private TextView tvState, tvLog;
    private ScrollView svLog;
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compress_test);

        btnFull = findViewById(R.id.btn_compress_full);
        btnRange = findViewById(R.id.btn_compress_range);
        btnStatus = findViewById(R.id.btn_compress_status);
        btnFetchDownloads = findViewById(R.id.btn_fetch_downloads);
        btnFetchAppDir = findViewById(R.id.btn_fetch_app_dir);
        btnOk = findViewById(R.id.btn_upload_ok);
        btnFail = findViewById(R.id.btn_upload_fail);
        btnCancel = findViewById(R.id.btn_compress_cancel);
        etStart = findViewById(R.id.et_start_time);
        etEnd = findViewById(R.id.et_end_time);
        tvState = findViewById(R.id.tv_compress_state);
        tvLog = findViewById(R.id.tv_compress_log);
        svLog = findViewById(R.id.sv_compress_log);

        btnFull.setOnClickListener(v -> trig(null, null));
        btnRange.setOnClickListener(v -> trig(etStart.getText().toString(), etEnd.getText().toString()));
        btnStatus.setOnClickListener(v -> queryStatus());
        btnFetchDownloads.setOnClickListener(v -> fetchToDownloads());
        btnFetchAppDir.setOnClickListener(v -> fetchToAppDirectory());
        btnOk.setOnClickListener(v -> report(true));
        btnFail.setOnClickListener(v -> report(false));
        btnCancel.setOnClickListener(v -> cancel());
    }

    @Override
    protected void onResume() {
        super.onResume();
        XcLoggerClient c = XcLoggerTestService.getClient();
        if (c != null) {
            c.setResultListener(this::appendLog);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        XcLoggerClient c = XcLoggerTestService.getClient();
        if (c != null) c.setResultListener(null);
    }

    // ─── Ops ────────────────────────────────────────────────────

    private void trig(String st, String et) {
        XcLoggerClient c = XcLoggerTestService.getClient();
        if (c == null || !c.isBound()) return;
        new Thread(() -> {
            boolean ok = (st == null || st.isEmpty()) ? c.triggerCompression()
                    : c.triggerCompressionWithRange(st, et);
            Log.i(TAG, "[AIDL-CALL] trig compress: " + (ok ? "ACCEPTED" : "REJECTED"));
        }).start();
    }

    private void queryStatus() {
        XcLoggerClient c = XcLoggerTestService.getClient();
        if (c == null || !c.isBound()) return;
        new Thread(() -> {
            String st = c.getCompressStatus();
            String state = parseState(st);
            uiHandler.post(() -> tvState.setText("Status: " + (state != null ? state : "?")));
        }).start();
    }

    private void fetchToDownloads() {
        XcLoggerClient c = XcLoggerTestService.getClient();
        if (c == null || !c.isBound()) return;
        setFetchEnabled(false);
        new Thread(() -> {
            Uri destination = null;
            String fileName = createExportFileName();
            String displayPath = "/sdcard/Download/" + EXPORT_DIRECTORY + "/" + fileName;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + EXPORT_DIRECTORY);
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                destination = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (destination == null) {
                    throw new IllegalStateException("MediaStore insert returned null");
                }

                long size = c.fetchZipToUri(destination);
                if (size <= 0) {
                    getContentResolver().delete(destination, null, null);
                    appendLog("AIDL", "zip_fetched_downloads", false, "Export failed");
                    return;
                }

                ContentValues completed = new ContentValues();
                completed.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(destination, completed, null, null);
                appendLog("AIDL", "zip_fetched_downloads", true,
                        size + " bytes -> " + displayPath);
            } catch (Exception e) {
                if (destination != null) {
                    getContentResolver().delete(destination, null, null);
                }
                appendLog("AIDL", "zip_fetched_downloads", false, errorMessage(e));
            } finally {
                uiHandler.post(() -> setFetchEnabled(true));
            }
        }).start();
    }

    private void fetchToAppDirectory() {
        XcLoggerClient c = XcLoggerTestService.getClient();
        if (c == null || !c.isBound()) return;
        setFetchEnabled(false);
        new Thread(() -> {
            File outputFile = null;
            try {
                File externalFiles = getExternalFilesDir(null);
                if (externalFiles == null) {
                    throw new IllegalStateException("External files directory unavailable");
                }
                File directory = new File(externalFiles, EXPORT_DIRECTORY);
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IllegalStateException("Unable to create app export directory");
                }

                outputFile = new File(directory, createExportFileName());
                long size = c.fetchZipToUri(Uri.fromFile(outputFile));
                if (size <= 0) {
                    if (outputFile.exists()) outputFile.delete();
                    appendLog("AIDL", "zip_fetched_app_dir", false, "Export failed");
                    return;
                }
                appendLog("AIDL", "zip_fetched_app_dir", true,
                        size + " bytes -> " + outputFile.getAbsolutePath());
            } catch (Exception e) {
                if (outputFile != null && outputFile.exists()) outputFile.delete();
                appendLog("AIDL", "zip_fetched_app_dir", false, errorMessage(e));
            } finally {
                uiHandler.post(() -> setFetchEnabled(true));
            }
        }).start();
    }

    private String createExportFileName() {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date());
        return "xclogger_fetch_" + timestamp + ".zip";
    }

    private void setFetchEnabled(boolean enabled) {
        btnFetchDownloads.setEnabled(enabled);
        btnFetchAppDir.setEnabled(enabled);
    }

    private String errorMessage(Exception error) {
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }

    private void report(boolean success) {
        XcLoggerClient c = XcLoggerTestService.getClient();
        if (c == null || !c.isBound()) return;
        new Thread(() -> c.reportUploadResult(success)).start();
    }

    private void cancel() {
        XcLoggerClient c = XcLoggerTestService.getClient();
        if (c == null || !c.isBound()) return;
        new Thread(() -> c.cancelCompressTask()).start();
    }

    private String parseState(String st) {
        if (st == null) return null;
        for (String part : st.split(";")) {
            if (part.trim().startsWith("state=")) {
                return part.trim().substring(6);
            }
        }
        return st;
    }

    // ─── Log ────────────────────────────────────────────────────

    private void appendLog(String source, String op, boolean success, String message) {
        String ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String line = String.format("%s [%s] %s: %s (%s)",
                ts, source, op, success ? "PASS" : "FAIL", message);
        Log.i(TAG, line);
        uiHandler.post(() -> {
            tvLog.append(line + "\n");
            svLog.post(() -> svLog.fullScroll(View.FOCUS_DOWN));
        });
    }
}
