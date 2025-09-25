package com.xcheng.xclogger.processctr;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.xcheng.xclogger.R;
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
    private Button btnPrintDb;
    private Button btnResetDb;
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
            Log.i("XcLoggerDev", "No config in DB");
            return;
        }

        Log.i("XcLoggerDev", "=== Database Configuration Info ===");
        Log.i("XcLoggerDev", "total_size=" + config.getTotalSizeGb() + " GB");
        Log.i("XcLoggerDev", "file_size=" + config.getFileSizeMb() + " MB");
        Log.i("XcLoggerDev", "buffer_size=" + config.getBufferSizeBytes() + " bytes");
        Log.i("XcLoggerDev", "log_dir=" + config.getLogDir());
        Log.i("XcLoggerDev", "log_period=" + config.getLogPeriodHours() + " hours");
        Log.i("XcLoggerDev", "filter_tag=" + config.getFilterTag());
        Log.i("XcLoggerDev", "filter_level=" + config.getFilterLevel());
        Log.i("XcLoggerDev", "filter_package=" + config.getFilterPackage());
        Log.i("XcLoggerDev", "is_running=" + database.loadRunningState());
        Log.i("XcLoggerDev", "operation_history_path=" + database.getOperationHistoryPath());
        Log.i("XcLoggerDev", "=== End of Configuration Info ===");
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
                Log.i("XcLoggerDev", "Database reset to default configuration");
                Toast.makeText(this, "Database reset to default configuration successfully. Please refresh other activities.", Toast.LENGTH_LONG).show();
            } else {
                Log.e("XcLoggerDev", "Failed to reset database to default configuration");
                Toast.makeText(this, "Failed to reset database to default configuration", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e("XcLoggerDev", "Error resetting database", e);
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