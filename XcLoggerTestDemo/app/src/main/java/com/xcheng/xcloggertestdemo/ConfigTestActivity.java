package com.xcheng.xcloggertestdemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerConfig2;
import com.xcheng.xclogger.util.XcLoggerConfigUpdateResult;
import com.xcheng.xclogger.util.XcLoggerConfigUpdater;

import java.util.UUID;

/**
 * Configuration test page — GET current config, edit fields, APPLY partial update.
 * Demonstrates best-practice partial configuration update pattern.
 */
public class ConfigTestActivity extends Activity {
    private static final String TAG = "XcLoggerDemo";
    private static final int MAX_BIND_ATTEMPTS = 25;

    private EditText etTotalSize, etFileSize, etBuffer, etLogDir, etPeriod;
    private EditText etFilterTag, etFilterLevel, etFilterPackage;
    private EditText etPackageBlacklist;
    private Button btnGet, btnApply, btnApplyModeObject;
    private RadioGroup packageFilterModeGroup;
    private TextView tvResult;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_test);

        etTotalSize = findViewById(R.id.et_total_size);
        etFileSize = findViewById(R.id.et_file_size);
        etBuffer = findViewById(R.id.et_buffer);
        etLogDir = findViewById(R.id.et_log_dir);
        etPeriod = findViewById(R.id.et_period);
        etFilterTag = findViewById(R.id.et_filter_tag);
        etFilterLevel = findViewById(R.id.et_filter_level);
        etFilterPackage = findViewById(R.id.et_filter_package);
        etPackageBlacklist = findViewById(R.id.et_package_blacklist);
        packageFilterModeGroup = findViewById(R.id.rg_package_filter_mode);
        btnGet = findViewById(R.id.btn_get);
        btnApply = findViewById(R.id.btn_apply);
        btnApplyModeObject = findViewById(R.id.btn_apply_mode_object);
        tvResult = findViewById(R.id.tv_result);

        btnGet.setOnClickListener(v -> runWhenBound(this::getConfig, 0));
        btnApply.setOnClickListener(v -> runWhenBound(this::applyConfig, 0));
        btnApplyModeObject.setOnClickListener(v -> runWhenBound(this::applyModeObject, 0));

        ensureBridgeServiceStarted();
        runWhenBound(this::getConfig, 0);
    }

    private void getConfig() {
        XcLoggerClient c = XcLoggerTestService.getClient();
        if (c == null || !c.isBound()) return;
        new Thread(() -> {
            XcLoggerConfig cfg = c.getConfiguration();
            String packageFilterMode = c.getPackageFilterMode();
            if (cfg == null) {
                runOnUiThread(() -> tvResult.setText("FAIL: null config"));
                return;
            }
            runOnUiThread(() -> {
                etTotalSize.setText(String.valueOf(cfg.getTotalSizeMb()));
                etFileSize.setText(String.valueOf(cfg.getFileSizeMb()));
                etBuffer.setText(String.valueOf(cfg.getBufferSizeBytes()));
                etLogDir.setText(cfg.getLogDir());
                etPeriod.setText(String.valueOf(cfg.getLogPeriodHours()));
                etFilterTag.setText(cfg.getFilterTag());
                etFilterLevel.setText(cfg.getFilterLevel());
                etFilterPackage.setText(cfg.getFilterPackage());
                etPackageBlacklist.setText(cfg.getFilterPackageBlacklist());
                setPackageFilterMode(packageFilterMode);
                tvResult.setText("GET OK — " + cfg.getTotalSizeMb() + "MB, packageMode="
                        + packageFilterMode + ", " + cfg.getFilterPackage());
            });
        }).start();
    }

    private void applyConfig() {
        XcLoggerClient c = XcLoggerTestService.getClient();
        if (c == null || !c.isBound()) return;
        try {
            XcLoggerConfigUpdater updater = c.configUpdater();
            setIfNotEmpty(etTotalSize, v -> updater.totalSizeMb(Integer.parseInt(v)));
            setIfNotEmpty(etFileSize, v -> updater.fileSizeMb(Integer.parseInt(v)));
            setIfNotEmpty(etBuffer, v -> updater.bufferSizeBytes(Integer.parseInt(v)));
            setIfNotEmpty(etLogDir, updater::logDir);
            setIfNotEmpty(etPeriod, v -> updater.logPeriodHours(Integer.parseInt(v)));
            updater.filterTagsCsv(etFilterTag.getText().toString());
            updater.filterLevel(emptyAsVerbose(etFilterLevel));
            updater.filterPackagesCsv(etFilterPackage.getText().toString());
            updater.blacklistPackagesCsv(etPackageBlacklist.getText().toString());
            updater.packageFilterMode(selectedPackageFilterMode());

            btnApply.setEnabled(false);
            tvResult.setText("APPLY PENDING");
            updater.commitAsync(result -> showUpdateResult(result));
        } catch (Exception e) {
            tvResult.setText("APPLY REJECTED: " + e.getMessage());
            toast("Invalid configuration");
        }
    }

    private void applyModeObject() {
        XcLoggerClient client = XcLoggerTestService.getClient();
        if (client == null || !client.isBound()) return;
        XcLoggerConfig2 update = new XcLoggerConfig2();
        update.setRequestId(UUID.randomUUID().toString());
        update.setPackageFilterMode(selectedPackageFilterMode().wireValue());
        btnApplyModeObject.setEnabled(false);
        client.submitConfiguration2(update, result -> {
            btnApplyModeObject.setEnabled(true);
            showUpdateResult(result);
        });
    }

    @FunctionalInterface
    interface StringConsumer { void accept(String s); }
    private void setIfNotEmpty(EditText et, StringConsumer setter) {
        String val = et.getText().toString().trim();
        if (!TextUtils.isEmpty(val)) {
            try { setter.accept(val); } catch (NumberFormatException e) {
                Log.w(TAG, "Invalid number: " + val);
            }
        }
    }

    private String emptyAsVerbose(EditText editText) {
        String value = editText.getText().toString().trim();
        return TextUtils.isEmpty(value) ? "v" : value;
    }

    private void setPackageFilterMode(String mode) {
        if (XcLoggerConfig2.PACKAGE_FILTER_MODE_WHITELIST.equals(mode)) {
            packageFilterModeGroup.check(R.id.rb_package_filter_whitelist);
        } else if (XcLoggerConfig2.PACKAGE_FILTER_MODE_BLACKLIST.equals(mode)) {
            packageFilterModeGroup.check(R.id.rb_package_filter_blacklist);
        } else {
            packageFilterModeGroup.check(R.id.rb_package_filter_off);
        }
    }

    private XcLoggerConfigUpdater.PackageFilterMode selectedPackageFilterMode() {
        int selectedId = packageFilterModeGroup.getCheckedRadioButtonId();
        if (selectedId == R.id.rb_package_filter_whitelist) {
            return XcLoggerConfigUpdater.PackageFilterMode.WHITELIST;
        }
        if (selectedId == R.id.rb_package_filter_blacklist) {
            return XcLoggerConfigUpdater.PackageFilterMode.BLACKLIST;
        }
        return XcLoggerConfigUpdater.PackageFilterMode.OFF;
    }

    private void ensureBridgeServiceStarted() {
        Intent intent = new Intent(this, XcLoggerTestService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void runWhenBound(Runnable action, int attempt) {
        XcLoggerClient client = XcLoggerTestService.getClient();
        if (client != null && client.isBound()) {
            setActionsEnabled(true);
            action.run();
            return;
        }
        if (attempt == 0) {
            ensureBridgeServiceStarted();
            setActionsEnabled(false);
            tvResult.setText("Binding XCLogger service...");
        }
        if (attempt >= MAX_BIND_ATTEMPTS) {
            setActionsEnabled(true);
            tvResult.setText("FAIL: XCLogger service binding timed out");
            toast("XCLogger service not bound");
            return;
        }
        mainHandler.postDelayed(() -> runWhenBound(action, attempt + 1), 200);
    }

    private void setActionsEnabled(boolean enabled) {
        btnGet.setEnabled(enabled);
        btnApply.setEnabled(enabled);
        btnApplyModeObject.setEnabled(enabled);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void showUpdateResult(XcLoggerConfigUpdateResult result) {
        btnApply.setEnabled(true);
        String text = "status=" + result.getStatus()
                + "\nrequestId=" + result.getRequestId()
                + "\nmessage=" + result.getMessage()
                + "\nchanged=" + result.getChangedFields()
                + "\nserviceRestarted=" + result.isServiceRestarted();
        tvResult.setText(text);
        toast(result.isSuccess() ? "Config updated" : "Update failed");
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}
