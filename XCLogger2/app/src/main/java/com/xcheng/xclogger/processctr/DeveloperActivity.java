package com.xcheng.xclogger.processctr;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

/**
 * DeveloperActivity - 开发者调试页面，提供数据库信息查看
 *
 * 功能方法：
 * - onCreate(Bundle) - 初始化调试界面
 * - printDb() - 打印数据库配置信息到日志
 */
public class DeveloperActivity extends AppCompatActivity {
    /**
     * 初始化调试界面
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Button btn = new Button(this);
        btn.setText("PrintDBInfo");
        btn.setOnClickListener(v -> printDb());
        setContentView(btn);
    }

    /**
     * 打印数据库配置信息到日志
     */
    private void printDb() {
        XcLoggerDatabase db = new XcLoggerDatabase(this);
        XcLoggerConfig config = db.loadConfig();

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
        Log.i("XcLoggerDev", "is_running=" + db.loadRunningState());
        Log.i("XcLoggerDev", "=== End of Configuration Info ===");
    }
}