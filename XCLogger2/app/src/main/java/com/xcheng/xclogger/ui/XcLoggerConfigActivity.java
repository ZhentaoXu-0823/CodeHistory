package com.xcheng.xclogger.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.xcheng.xclogger.R;
import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

/**
 * XcLoggerConfigActivity - 配置界面控制器，提供详细的参数配置功能
 *
 * 功能方法：
 * - onCreate() - 初始化UI组件和配置
 * - onResume() - 页面恢复时刷新配置和UI
 * - checkLogRunningState() - 检查日志运行状态
 * - setAllInputsEnabled(boolean) - 设置所有输入控件的启用状态
 * - save() - 保存配置到数据库
 * - buildConfigDetailsString(XcLoggerConfig) - 构建配置详情字符串
 * - initViews() - 初始化UI组件
 * - initData() - 初始化数据
 * - refreshConfigFromLoader() - 从ConfigLoader刷新配置
 */
public class XcLoggerConfigActivity extends AppCompatActivity {
    private EditText etTotalSize, etFileSize, etBufferSize, etLogDir, etLogPeriod, etFilterTag, etFilterLevel, etFilterPackage;
    private Button btnSave;
    private boolean isLogRunning = false;
    private XcLoggerConfig config;
    private XcLoggerDatabase database;
    private ProcessController processController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xclogger_config);

        // 初始化UI组件
        initViews();

        // 初始化数据
        initData();

        // 检查日志运行状态
        checkLogRunningState();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 重新检查日志运行状态
        checkLogRunningState();

        // 从ConfigLoader重新获取最新配置（确保同步）
        refreshConfigFromLoader();
    }

    /**
     * 初始化UI组件
     */
    private void initViews() {
        etTotalSize = findViewById(R.id.et_total_size);
        etFileSize = findViewById(R.id.et_file_size);
        etBufferSize = findViewById(R.id.et_buffer_size);
        etLogDir = findViewById(R.id.et_log_dir);
        etLogPeriod = findViewById(R.id.et_log_period);
        etFilterTag = findViewById(R.id.et_filter_tag);
        etFilterLevel = findViewById(R.id.et_filter_level);
        etFilterPackage = findViewById(R.id.et_filter_package);
        btnSave = findViewById(R.id.btn_save);

        btnSave.setOnClickListener(v -> save());
    }

    /**
     * 初始化数据
     */
    private void initData() {
        database = new XcLoggerDatabase(this);
        processController = ProcessController.getInstance(this);
        config = ConfigLoader.getInstance().getCurrentConfig();
    }

    /**
     * 从ConfigLoader刷新配置
     */
    private void refreshConfigFromLoader() {
        // 重新从ConfigLoader获取最新配置
        this.config = ConfigLoader.getInstance().getCurrentConfig();

        if (config != null) {
            etTotalSize.setText(String.valueOf(config.getTotalSizeGb()));
            etFileSize.setText(String.valueOf(config.getFileSizeMb()));
            etBufferSize.setText(String.valueOf(config.getBufferSizeBytes()));
            etLogDir.setText(config.getLogDir());
            etLogPeriod.setText(String.valueOf(config.getLogPeriodHours()));
            etFilterTag.setText(config.getFilterTag());
            etFilterLevel.setText(config.getFilterLevel());
            etFilterPackage.setText(config.getFilterPackage());
        }
    }

    /**
     * 检查日志运行状态
     */
    private void checkLogRunningState() {
        isLogRunning = database.loadRunningState();
        setAllInputsEnabled(!isLogRunning);

        if (isLogRunning) {
            Toast.makeText(this, "Log is running, configuration modification is disabled", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 设置所有输入控件的启用状态
     * @param enabled 是否启用
     */
    private void setAllInputsEnabled(boolean enabled) {
        etTotalSize.setEnabled(enabled);
        etFileSize.setEnabled(enabled);
        etBufferSize.setEnabled(enabled);
        etLogDir.setEnabled(enabled);
        etLogPeriod.setEnabled(enabled);
        etFilterTag.setEnabled(enabled);
        etFilterLevel.setEnabled(enabled);
        etFilterPackage.setEnabled(enabled);
        btnSave.setEnabled(enabled);

        if (!enabled) {
            etTotalSize.setHint("Log is running, modification disabled");
            etFileSize.setHint("Log is running, modification disabled");
            etBufferSize.setHint("Log is running, modification disabled");
            etLogDir.setHint("Log is running, modification disabled");
            etLogPeriod.setHint("Log is running, modification disabled");
            etFilterTag.setHint("Log is running, modification disabled");
            etFilterLevel.setHint("Log is running, modification disabled");
            etFilterPackage.setHint("Log is running, modification disabled");
        } else {
            etTotalSize.setHint("Total size in GB");
            etFileSize.setHint("File size in MB");
            etBufferSize.setHint("Buffer size in bytes");
            etLogDir.setHint("Log directory path");
            etLogPeriod.setHint("Log period in hours");
            etFilterTag.setHint("Filter tag");
            etFilterLevel.setHint("Filter level");
            etFilterPackage.setHint("Filter package");
        }
    }

    /**
     * 保存配置
     */
    private void save() {
        if (isLogRunning) {
            Toast.makeText(this, "Log is running, please stop log first before modifying configuration", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            // 获取旧配置用于记录变更
            XcLoggerConfig oldConfig = new XcLoggerConfig();
            if (config != null) {
                oldConfig.setTotalSizeGb(config.getTotalSizeGb());
                oldConfig.setFileSizeMb(config.getFileSizeMb());
                oldConfig.setBufferSizeBytes(config.getBufferSizeBytes());
                oldConfig.setLogDir(config.getLogDir());
                oldConfig.setLogPeriodHours(config.getLogPeriodHours());
                oldConfig.setFilterTag(config.getFilterTag());
                oldConfig.setFilterLevel(config.getFilterLevel());
                oldConfig.setFilterPackage(config.getFilterPackage());
            }

            // 创建新配置
            XcLoggerConfig newConfig = new XcLoggerConfig();
            newConfig.setTotalSizeGb(Integer.parseInt(etTotalSize.getText().toString()));
            newConfig.setFileSizeMb(Integer.parseInt(etFileSize.getText().toString()));
            newConfig.setBufferSizeBytes(Integer.parseInt(etBufferSize.getText().toString()));
            newConfig.setLogDir(etLogDir.getText().toString());
            newConfig.setLogPeriodHours(Integer.parseInt(etLogPeriod.getText().toString()));
            newConfig.setFilterTag(etFilterTag.getText().toString());
            newConfig.setFilterLevel(etFilterLevel.getText().toString());
            newConfig.setFilterPackage(etFilterPackage.getText().toString());

            // 保存到数据库
            ConfigLoader.getInstance().updateConfig(this, newConfig);

            // 更新FileManager路径（如果log_dir发生变化）
            if (!oldConfig.getLogDir().equals(newConfig.getLogDir())) {
                processController.getFileManager().updatePaths();
            }

            // 记录配置变更
            String configDetails = buildConfigDetailsString(newConfig);
            processController.getFileManager().appendConfigChangeHistory(configDetails);

            Toast.makeText(this, "Configuration saved successfully", Toast.LENGTH_SHORT).show();
            finish();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format, please check your input", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save configuration: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 构建配置详情字符串
     * @param config 配置对象
     * @return 配置详情字符串
     */
    private String buildConfigDetailsString(XcLoggerConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("total_size=").append(config.getTotalSizeGb()).append(" GB");
        sb.append("; file_size=").append(config.getFileSizeMb()).append(" MB");
        sb.append("; buffer_size=").append(config.getBufferSizeBytes()).append(" bytes");
        sb.append("; log_dir=").append(config.getLogDir());
        sb.append("; log_period=").append(config.getLogPeriodHours()).append(" hours");
        sb.append("; filter_tag=").append(config.getFilterTag());
        sb.append("; filter_level=").append(config.getFilterLevel());
        sb.append("; filter_package=").append(config.getFilterPackage());
        return sb.toString();
    }
}