package com.xcheng.xclogger.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.xcheng.xclogger.R;
import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;
import java.util.ArrayList;
import java.util.List;

/**
 * XcLoggerConfigActivity - 配置界面控制器
 */
public class XcLoggerConfigActivity extends AppCompatActivity {
    private static final String TAG = "XcLoggerConfigActivity";

    private EditText etTotalSize, etFileSize, etBufferSize, etLogDir, etLogPeriod;
    private EditText etFilterTag, etFilterLevel, etFilterPackage;
    // v1.2.2: white+black list extensions
    private EditText etFilterTagBlacklist, etFilterPackageBlacklist, etFilterLevelBlacklist;
    private EditText etFilterContent, etFilterContentBlacklist;
    private Button btnSave;

    private boolean isLogRunning = false;
    private XcLoggerConfig config;
    private XcLoggerDatabase database;
    private ProcessController processController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xclogger_config);

        initViews();
        initData();
        checkLogRunningState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkLogRunningState();
        refreshConfigFromLoader();
    }

    private void initViews() {
        etTotalSize = findViewById(R.id.et_total_size);
        etFileSize = findViewById(R.id.et_file_size);
        etBufferSize = findViewById(R.id.et_buffer_size);
        etLogDir = findViewById(R.id.et_log_dir);
        etLogPeriod = findViewById(R.id.et_log_period);
        etFilterTag = findViewById(R.id.et_filter_tag);
        etFilterLevel = findViewById(R.id.et_filter_level);
        etFilterPackage = findViewById(R.id.et_filter_package);
        etFilterTagBlacklist = findViewById(R.id.et_filter_tag_blacklist);
        etFilterPackageBlacklist = findViewById(R.id.et_filter_package_blacklist);
        etFilterLevelBlacklist = findViewById(R.id.et_filter_level_blacklist);
        etFilterContent = findViewById(R.id.et_filter_content);
        etFilterContentBlacklist = findViewById(R.id.et_filter_content_blacklist);
        btnSave = findViewById(R.id.btn_save);

        btnSave.setOnClickListener(v -> save());
    }

    private void initData() {
        database = new XcLoggerDatabase(this);
        processController = ProcessController.getInstance(this);
        config = ConfigLoader.getInstance().getCurrentConfig();
    }

    private void refreshConfigFromLoader() {
        this.config = ConfigLoader.getInstance().getCurrentConfig();
        if (config != null) {
            etTotalSize.setText(String.valueOf(config.getTotalSizeMb()));
            etFileSize.setText(String.valueOf(config.getFileSizeMb()));
            etBufferSize.setText(String.valueOf(config.getBufferSizeBytes()));
            etLogDir.setText(config.getLogDir());
            etLogPeriod.setText(String.valueOf(config.getLogPeriodHours()));
            etFilterTag.setText(config.getFilterTag());
            etFilterLevel.setText(config.getFilterLevel());
            etFilterPackage.setText(config.getFilterPackage());
            etFilterTagBlacklist.setText(config.getFilterTagBlacklist());
            etFilterPackageBlacklist.setText(config.getFilterPackageBlacklist());
            etFilterLevelBlacklist.setText(config.getFilterLevelBlacklist());
            etFilterContent.setText(config.getFilterContent());
            etFilterContentBlacklist.setText(config.getFilterContentBlacklist());
        }
    }

    private void checkLogRunningState() {
        isLogRunning = database.loadRunningState();
        setAllInputsEnabled(!isLogRunning);

        if (isLogRunning) {
            Toast.makeText(this, R.string.warn_config_disabled, Toast.LENGTH_LONG).show();
        }
    }

    private void setAllInputsEnabled(boolean enabled) {
        etTotalSize.setEnabled(enabled);
        etFileSize.setEnabled(enabled);
        etBufferSize.setEnabled(enabled);
        etLogDir.setEnabled(enabled);
        etLogPeriod.setEnabled(enabled);
        etFilterTag.setEnabled(enabled);
        etFilterLevel.setEnabled(enabled);
        etFilterPackage.setEnabled(enabled);
        etFilterTagBlacklist.setEnabled(enabled);
        etFilterPackageBlacklist.setEnabled(enabled);
        etFilterLevelBlacklist.setEnabled(enabled);
        etFilterContent.setEnabled(enabled);
        etFilterContentBlacklist.setEnabled(enabled);
        btnSave.setEnabled(enabled);

        if (!enabled) {
            String disabledHint = getString(R.string.warn_config_disabled);
            etTotalSize.setHint(disabledHint);
            etFileSize.setHint(disabledHint);
            etBufferSize.setHint(disabledHint);
            etLogDir.setHint(disabledHint);
            etLogPeriod.setHint(disabledHint);
            etFilterTag.setHint(disabledHint);
            etFilterLevel.setHint(disabledHint);
            etFilterPackage.setHint(disabledHint);
            etFilterTagBlacklist.setHint(disabledHint);
            etFilterPackageBlacklist.setHint(disabledHint);
            etFilterLevelBlacklist.setHint(disabledHint);
            etFilterContent.setHint(disabledHint);
            etFilterContentBlacklist.setHint(disabledHint);
        } else {
            etTotalSize.setHint(R.string.hint_total_size);
            etFileSize.setHint(R.string.hint_file_size);
            etBufferSize.setHint(R.string.hint_buffer_size);
            etLogDir.setHint(R.string.hint_log_dir);
            etLogPeriod.setHint(R.string.hint_log_period);
            etFilterTag.setHint(R.string.hint_filter_tag);
            etFilterLevel.setHint(R.string.hint_filter_level);
            etFilterPackage.setHint(R.string.hint_filter_package);
            etFilterTagBlacklist.setHint(R.string.hint_filter_tag_blacklist);
            etFilterPackageBlacklist.setHint(R.string.hint_filter_package_blacklist);
            etFilterLevelBlacklist.setHint(R.string.hint_filter_level_blacklist);
            etFilterContent.setHint(R.string.hint_filter_content);
            etFilterContentBlacklist.setHint(R.string.hint_filter_content_blacklist);
        }
    }

    private boolean isValidFilterLevel(String level) {
        if (level == null || level.trim().isEmpty() || level.equalsIgnoreCase("all")) {
            return true;
        }
        String normalized = level.trim().toLowerCase();
        return normalized.equals("f") || normalized.equals("e") ||
                normalized.equals("w") || normalized.equals("i") ||
                normalized.equals("d") || normalized.equals("v");
    }

    private String normalizeMultiValue(String value) {
        if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("all")) {
            return "all";
        }
        String[] parts = value.split(",");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.isEmpty() ? "all" : String.join(",", result);
    }

    private void save() {
        if (isLogRunning) {
            Toast.makeText(this, R.string.warn_stop_log_first, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            String filterLevel = etFilterLevel.getText().toString().trim();
            if (!isValidFilterLevel(filterLevel)) {
                String error = getString(R.string.err_invalid_filter_level, filterLevel);
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                return;
            }

            String filterTag = normalizeMultiValue(etFilterTag.getText().toString());
            String filterPackage = normalizeMultiValue(etFilterPackage.getText().toString());

            XcLoggerConfig newConfig = new XcLoggerConfig();
            newConfig.setTotalSizeMb(Integer.parseInt(etTotalSize.getText().toString()));
            newConfig.setFileSizeMb(Integer.parseInt(etFileSize.getText().toString()));
            newConfig.setBufferSizeBytes(Integer.parseInt(etBufferSize.getText().toString()));
            newConfig.setLogDir(etLogDir.getText().toString());
            newConfig.setLogPeriodHours(Integer.parseInt(etLogPeriod.getText().toString()));
            newConfig.setFilterTag(filterTag);
            newConfig.setFilterLevel(filterLevel);
            newConfig.setFilterPackage(filterPackage);
            newConfig.setFilterTagBlacklist(etFilterTagBlacklist.getText().toString().trim());
            newConfig.setFilterPackageBlacklist(etFilterPackageBlacklist.getText().toString().trim());
            newConfig.setFilterLevelBlacklist(etFilterLevelBlacklist.getText().toString().trim());
            newConfig.setFilterContent(etFilterContent.getText().toString().trim());
            newConfig.setFilterContentBlacklist(etFilterContentBlacklist.getText().toString().trim());

            ConfigLoader.getInstance().updateConfig(this, newConfig);
            processController.getFileManager().updatePaths();

            Toast.makeText(this, R.string.msg_config_save_success, Toast.LENGTH_SHORT).show();
            finish();

        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.msg_invalid_number_format, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            String error = getString(R.string.msg_config_save_failed, e.getMessage());
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        }
    }
}