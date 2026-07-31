package com.xcheng.xcloggertestdemo;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Main entry - binds XCLogger, shows live status, and navigates to test pages.
 */
public class MainActivity extends Activity {
    private static final String TAG = "XcLoggerDemo";

    private Spinner spinnerPkg;
    private Button btnStart, btnStop, btnConfig, btnCompress, btnAutoTest, btnStress;
    private TextView tvAppTitle, tvStatus, tvLog;
    private ScrollView svLog;

    private XcLoggerClient client;
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable statusPoller;
    private volatile boolean serviceStarting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        startService();

        statusPoller = new Runnable() {
            @Override public void run() {
                refreshStatus();
                uiHandler.postDelayed(this, 2000);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        client = XcLoggerTestService.getClient();
        if (client != null) {
            client.setResultListener(this::appendLog);
        }
        uiHandler.postDelayed(statusPoller, 500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(statusPoller);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (client != null) client.setResultListener(null);
        uiHandler.removeCallbacks(statusPoller);
    }

    // -- Init --

    private void initViews() {
        spinnerPkg = findViewById(R.id.spinner_pkg);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        btnConfig = findViewById(R.id.btn_config);
        btnCompress = findViewById(R.id.btn_compress);
        btnAutoTest = findViewById(R.id.btn_autotest);
        btnStress = findViewById(R.id.btn_stress);
        tvAppTitle = findViewById(R.id.tv_app_title);
        tvStatus = findViewById(R.id.tv_status);
        tvLog = findViewById(R.id.tv_log);
        svLog = findViewById(R.id.sv_log);
        tvAppTitle.setText("XCLogger Test Demo v" + getVersionName());
        tvLog.setMovementMethod(new ScrollingMovementMethod());

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.target_packages, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPkg.setAdapter(adapter);
        String preferredPackage = XcLoggerTestService.resolveTargetPackage(this);
        for (int i = 0; i < adapter.getCount(); i++) {
            if (preferredPackage.contentEquals(adapter.getItem(i))) {
                spinnerPkg.setSelection(i, false);
                break;
            }
        }

        // Restart service when target package changes
        spinnerPkg.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (serviceStarting) return; // suppress initial trigger
                String targetPackage = parent.getItemAtPosition(pos).toString();
                XcLoggerTestService.saveTargetPackage(MainActivity.this, targetPackage);
                appendLog("DEMO", "switch_pkg", true, "Switching to " + targetPackage);
                restartService();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnStart.setOnClickListener(v -> exec("start", () -> client.startLogging()));
        btnStop.setOnClickListener(v -> exec("stop", () -> client.stopLogging()));
        btnConfig.setOnClickListener(v -> startActivity(new Intent(this, ConfigTestActivity.class)));
        btnCompress.setOnClickListener(v -> startActivity(new Intent(this, CompressTestActivity.class)));
        btnAutoTest.setOnClickListener(v -> startActivity(new Intent(this, AutoTestActivity.class)));
        btnStress.setOnClickListener(v -> startActivity(new Intent(this, StressTestActivity.class)));
    }

    private void startService() {
        serviceStarting = true;
        stopService(new Intent(this, XcLoggerTestService.class));
        Intent i = new Intent(this, XcLoggerTestService.class);
        String pkg = spinnerPkg.getSelectedItem() != null
                ? spinnerPkg.getSelectedItem().toString()
                : XcLoggerTestService.resolveTargetPackage(this);
        XcLoggerTestService.saveTargetPackage(this, pkg);
        i.putExtra(XcLoggerTestService.EXTRA_TARGET_PKG, pkg);
        startService(i);
        appendLog("DEMO", "bind", true, "Starting service -> " + pkg);

        // Poll for binding to complete
        uiHandler.postDelayed(() -> {
            client = XcLoggerTestService.getClient();
            if (client != null) {
                client.setResultListener(this::appendLog);
                appendLog("DEMO", "bound", client.isBound(), client.isBound() ? "AIDL ready" : "waiting");
            }
            serviceStarting = false;
        }, 1500);
    }

    private void restartService() {
        client = null;
        startService();
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }

    private void refreshStatus() {
        if (client == null || !client.isBound()) {
            tvStatus.setText("STATUS: NOT BOUND");
            tvStatus.setTextColor(0xFF888888);
            return;
        }
        try {
            boolean running = client.isRunning();
            tvStatus.setText("STATUS: " + (running ? "* RUNNING" : "o STOPPED"));
            tvStatus.setTextColor(running ? 0xFF00CC00 : 0xFFCC0000);
        } catch (Exception e) {
            tvStatus.setText("STATUS: ERROR");
            tvStatus.setTextColor(0xFF888888);
        }
    }

    // -- AIDL helpers --

    @FunctionalInterface
    private interface VoidCall { void run() throws Exception; }

    private void exec(String label, VoidCall fn) {
        if (client == null || !client.isBound()) {
            appendLog("AIDL", label, false, "not bound");
            return;
        }
        new Thread(() -> {
            try {
                fn.run();
            } catch (Exception e) {
                appendLog("AIDL", label, false, e.getMessage());
            }
        }).start();
    }

    // -- Logging --

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
