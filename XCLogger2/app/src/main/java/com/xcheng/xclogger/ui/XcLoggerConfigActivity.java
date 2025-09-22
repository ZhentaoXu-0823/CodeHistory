package com.xcheng.xclogger.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.xcheng.xclogger.R;
import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

/**
 * XcLoggerConfigActivity - 配置界面，提供参数编辑功能
 *
 * 功能方法：
 * - onCreate(Bundle) - 初始化配置界面
 * - checkLogRunningState() - 检查日志运行状态并禁用路径编辑
 * - addRow(LinearLayout, String, String) - 创建普通标签+输入行
 * - addRowWithUnit(LinearLayout, String, String, String, boolean) - 创建带单位的标签+输入行
 * - save() - 保存所有配置到数据库并更新全局缓存
 * - safeInt(String) - 安全字符串转整数
 */
public class XcLoggerConfigActivity extends AppCompatActivity {
    private EditText etTotal;
    private EditText etFile;
    private EditText etBuffer;
    private EditText etDir;
    private EditText etPeriod;
    private EditText etTag;
    private EditText etLevel;
    private EditText etPkg;
    private XcLoggerConfig cfg;
    private XcLoggerDatabase database;

    /**
     * 初始化配置界面
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad16 = (int) (getResources().getDisplayMetrics().density * 16);
        root.setPadding(pad16, pad16, pad16, pad16);

// Top bar with title and Save (fixed) - 3:2 height ratio
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBackgroundResource(R.drawable.bg_rounded_gray);
        int topPad = (int) (getResources().getDisplayMetrics().density * 24); // 3:2 ratio padding
        top.setPadding(topPad, topPad, topPad, topPad);
        LinearLayout.LayoutParams lpTop = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTop.bottomMargin = pad16;
        root.addView(top, lpTop);

        TextView title = new TextView(this);
        title.setText("XcLogger Config");
        title.setTextSize(18); // Larger text for top bar
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(title, lpTitle);

        TextView btnSave = new TextView(this);
        btnSave.setText("Save");
        btnSave.setTextColor(0xFF1976D2);
        btnSave.setTextSize(16);
        btnSave.setGravity(Gravity.END);
        top.addView(btnSave);

// Scroll area for details
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(details, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        cfg = new ConfigLoader().load(this);
        database = new XcLoggerDatabase(this);

// With unit labels
        etTotal = addRowWithUnit(details, "total_size", String.valueOf(cfg.getTotalSizeGb()), "GB", true);
        etFile = addRowWithUnit(details, "file_size", String.valueOf(cfg.getFileSizeMb()), "MB", true);
        etBuffer = addRowWithUnit(details, "buffer_size", String.valueOf(cfg.getBufferSizeBytes()), "B", true);
        etPeriod = addRowWithUnit(details, "log_period", String.valueOf(cfg.getLogPeriodHours()), "Hour", true);

// Plain labels
        etDir = addRow(details, "log_dir", cfg.getLogDir());
        etTag = addRow(details, "filter_tag", cfg.getFilterTag());
        etLevel = addRow(details, "filter_level", cfg.getFilterLevel());
        etPkg = addRow(details, "filter_package", cfg.getFilterPackage());

// Check if log is running and disable path editing
        checkLogRunningState();

        btnSave.setOnClickListener(v -> { save(); setResult(RESULT_OK); finish(); });

        setContentView(root);
    }

    /**
     * 检查日志运行状态并禁用路径编辑
     */
    private void checkLogRunningState() {
        boolean isRunning = database.loadRunningState();
        if (isRunning) {
            etDir.setEnabled(false);
            etDir.setHint("Log is running, please stop log first before modifying file path");
            etDir.setHintTextColor(0xFF999999);
        }
    }

    /**
     * 创建普通标签+输入行
     * @param parent 父容器
     * @param label 标签文本
     * @param value 初始值
     * @return EditText控件
     */
    private EditText addRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.bg_rounded_gray);
        int pad = (int) (getResources().getDisplayMetrics().density * 16); // 2:3 ratio padding
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = pad;
        parent.addView(row, lp);

        TextView tv = new TextView(this);
        tv.setText(label);
        LinearLayout.LayoutParams lpTv = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(tv, lpTv);

        EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(value);
        row.addView(et, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
        return et;
    }

    /**
     * 创建带单位的标签+输入行
     * @param parent 父容器
     * @param label 标签文本
     * @param value 初始值
     * @param unit 单位文本
     * @param numeric 是否为数字输入
     * @return EditText控件
     */
    private EditText addRowWithUnit(LinearLayout parent, String label, String value, String unit, boolean numeric) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.bg_rounded_gray);
        int pad = (int) (getResources().getDisplayMetrics().density * 16); // 2:3 ratio padding
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = pad;
        parent.addView(row, lp);

        TextView tv = new TextView(this);
        tv.setText(label);
        LinearLayout.LayoutParams lpTv = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(tv, lpTv);

        EditText et = new EditText(this);
        et.setSingleLine(true);
        if (numeric) et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(value);
        row.addView(et, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f));

        TextView unitView = new TextView(this);
        unitView.setText(unit);
        row.addView(unitView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.5f));
        return et;
    }

    /**
     * 保存所有配置到数据库并更新全局缓存
     */
    private void save() {
        // Check if log is running and path is being modified
        boolean isRunning = database.loadRunningState();
        if (isRunning && !etDir.getText().toString().equals(cfg.getLogDir())) {
            Toast.makeText(this, "Log is running, please stop log first before modifying file path", Toast.LENGTH_LONG).show();
            return;
        }

        cfg.setTotalSizeGb(safeInt(etTotal.getText().toString()));
        cfg.setFileSizeMb(safeInt(etFile.getText().toString()));
        cfg.setBufferSizeBytes(safeInt(etBuffer.getText().toString()));
        cfg.setLogDir(etDir.getText().toString());
        cfg.setLogPeriodHours(safeInt(etPeriod.getText().toString()));
        cfg.setFilterTag(etTag.getText().toString());
        cfg.setFilterLevel(etLevel.getText().toString());
        cfg.setFilterPackage(etPkg.getText().toString());
        new XcLoggerDatabase(this).saveConfig(cfg);
        ConfigLoader.replaceWith(cfg);
    }

    /**
     * 安全字符串转整数
     * @param s 字符串
     * @return 整数（解析失败返回0）
     */
    private int safeInt(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; } }
}