package com.xcheng.xclogger.processctr;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.xcheng.xclogger.R;
import com.xcheng.xclogger.filemanager.FileManager;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

/**
 * DeveloperActivity - 开发者界面控制器，提供开发者调试功能
 *
 * 功能方法：
 * - onCreate() - 初始化UI组件
 * - printDb() - 打印数据库配置信息
 * - resetDatabase() - 重置数据库为默认配置
 * - checkLogRunningState() - 检查日志运行状态
 */
public class DeveloperActivity extends AppCompatActivity {
    private static final String TAG = "DeveloperActivity";

    // UI组件
    private Button btnPrintDb;
    private Button btnResetDb;

    // 数据相关
    private XcLoggerDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer);

        // 初始化数据库
        database = new XcLoggerDatabase(this);

        // 初始化UI组件
        initViews();
    }

    /**
     * 初始化UI组件
     */
    private void initViews() {
        btnPrintDb = findViewById(R.id.btn_print_db);
        btnResetDb = findViewById(R.id.btn_reset_db);

        btnPrintDb.setOnClickListener(v -> printDb());
        btnResetDb.setOnClickListener(v -> resetDatabase());
    }

    /**
     * 打印数据库配置信息
     */
    private void printDb() {
        XcLoggerConfig config = database.loadConfig();

        if (config == null) {
            Log.i(TAG, "No config in DB");
            return;
        }

        Log.i(TAG, "=== Database Configuration Info ===");
        Log.i(TAG, "total_size=" + config.getTotalSizeGb() + " GB");
        Log.i(TAG, "file_size=" + config.getFileSizeMb() + " MB");
        Log.i(TAG, "buffer_size=" + config.getBufferSizeBytes() + " bytes");
        Log.i(TAG, "log_dir=" + config.getLogDir());
        Log.i(TAG, "log_period=" + config.getLogPeriodHours() + " hours");
        Log.i(TAG, "filter_tag=" + config.getFilterTag());
        Log.i(TAG, "filter_level=" + config.getFilterLevel());
        Log.i(TAG, "filter_package=" + config.getFilterPackage());
        Log.i(TAG, "is_running=" + database.loadRunningState());
        Log.i(TAG, "operation_history_path=" + database.getOperationHistoryPath());
        Log.i(TAG, "=== End of Configuration Info ===");
    }

    /**
     * 重置数据库为默认配置
     */
    private void resetDatabase() {
        // 检查日志运行状态
        if (checkLogRunningState()) {
            Toast.makeText(this, "Please stop XcLogger first before resetting database", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            // 使用ConfigLoader的重置方法
            XcLoggerConfig defaultConfig = ConfigLoader.getInstance().resetToDefault(this);

            if (defaultConfig != null) {
                Log.i(TAG, "Database reset to default configuration");
                try {
                    FileManager fm = new FileManager(this);
                    fm.appendOperationHistory("Developer reset database to default config (source:DeveloperActivity)");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to record operation history for developer reset", e);
                }
                Toast.makeText(this, "Database reset to default configuration successfully. Please refresh other activities.", Toast.LENGTH_LONG).show();
            } else {
                Log.e(TAG, "Failed to reset database to default configuration");
                try {
                    FileManager fm = new FileManager(this);
                    fm.appendOperationHistory("Developer reset database failed (source:DeveloperActivity)");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to record operation history for developer reset failure", e);
                }
                Toast.makeText(this, "Failed to reset database to default configuration", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resetting database", e);
            try {
                FileManager fm = new FileManager(this);
                fm.appendOperationHistory("Developer reset database error (source:DeveloperActivity): " + e.getMessage());
            } catch (Exception ex) {
                Log.w(TAG, "Failed to record operation history for developer reset exception", ex);
            }
            Toast.makeText(this, "Error resetting database: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 检查日志运行状态
     * @return 是否正在运行
     */
    private boolean checkLogRunningState() {
        return database.loadRunningState();
    }
}