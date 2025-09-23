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
import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

/**
 * XcLoggerConfigActivity - 配置界面，提供参数编辑功能
 *
 * 功能方法：
 * - onCreate(Bundle) - 初始化配置界面
 * - checkLogRunningState() - 检查日志运行状态并禁用所有配置编辑
 * - addRow(LinearLayout, String, String) - 创建普通标签+输入行
 * - addRowWithUnit(LinearLayout, String, String, String, boolean) - 创建带单位的标签+输入行
 * - save() - 保存所有配置到数据库并更新全局缓存
 * - safeInt(String) - 安全字符串转整数
 * - buildConfigDetailsString(XcLoggerConfig) - 构建配置详情字符串
 * - setAllInputsEnabled(boolean) - 设置所有输入框的启用状态
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
    private ProcessController processController;
    private TextView btnSave;
    private boolean isLogRunning = false;

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

        btnSave = new TextView(this);
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
        processController = ProcessController.getInstance(this);

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

// Check if log is running and disable all editing
        checkLogRunningState();

        btnSave.setOnClickListener(v -> {
            if (!isLogRunning) {
                save();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "Log is running, please stop log first before modifying configuration", Toast.LENGTH_LONG).show();
            }
        });

        setContentView(root);
    }

    /**
     * 检查日志运行状态并禁用所有配置编辑
     */
    private void checkLogRunningState() {
        isLogRunning = database.loadRunningState();
        if (isLogRunning) {
            // 禁用所有输入框
            setAllInputsEnabled(false);
            btnSave.setTextColor(0xFF999999);
            btnSave.setText("Save");

            // 显示提示信息
            Toast.makeText(this, "Log is running, all configuration is disabled", Toast.LENGTH_LONG).show();
        } else {
            // 启用所有输入框
            setAllInputsEnabled(true);

            // 启用保存按钮
            btnSave.setEnabled(true);
            btnSave.setTextColor(0xFF1976D2);
            btnSave.setText("Save");
        }
    }

    /**
     * 设置所有输入框的启用状态
     * @param enabled 是否启用
     */
    private void setAllInputsEnabled(boolean enabled) {
        etTotal.setEnabled(enabled);
        etFile.setEnabled(enabled);
        etBuffer.setEnabled(enabled);
        etDir.setEnabled(enabled);
        etPeriod.setEnabled(enabled);
        etTag.setEnabled(enabled);
        etLevel.setEnabled(enabled);
        etPkg.setEnabled(enabled);

        if (!enabled) {
            // 设置禁用状态的提示文本
            etDir.setHint("Log is running, please stop log first before modifying file path");
            etDir.setHintTextColor(0xFF999999);

            // 为其他输入框也设置提示
            etTotal.setHint("Log is running, configuration disabled");
            etFile.setHint("Log is running, configuration disabled");
            etBuffer.setHint("Log is running, configuration disabled");
            etPeriod.setHint("Log is running, configuration disabled");
            etTag.setHint("Log is running, configuration disabled");
            etLevel.setHint("Log is running, configuration disabled");
            etPkg.setHint("Log is running, configuration disabled");

            // 设置提示文本颜色
            etTotal.setHintTextColor(0xFF999999);
            etFile.setHintTextColor(0xFF999999);
            etBuffer.setHintTextColor(0xFF999999);
            etPeriod.setHintTextColor(0xFF999999);
            etTag.setHintTextColor(0xFF999999);
            etLevel.setHintTextColor(0xFF999999);
            etPkg.setHintTextColor(0xFF999999);
        } else {
            // 清除提示文本
            etTotal.setHint("");
            etFile.setHint("");
            etBuffer.setHint("");
            etDir.setHint("");
            etPeriod.setHint("");
            etTag.setHint("");
            etLevel.setHint("");
            etPkg.setHint("");
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
        // 再次检查运行状态（双重保险）
        if (isLogRunning) {
            Toast.makeText(this, "Log is running, please stop log first before modifying configuration", Toast.LENGTH_LONG).show();
            return;
        }

        // 保存配置
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

        // 记录配置修改历史
        String configDetails = buildConfigDetailsString(cfg);
        processController.getFileManager().appendConfigChangeHistory(configDetails);
    }

    /**
     * 构建配置详情字符串（键-值格式）
     * @param config 配置对象
     * @return 配置详情字符串
     */
    private String buildConfigDetailsString(XcLoggerConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("total_size-").append(config.getTotalSizeGb()).append("; ");
        sb.append("file_size-").append(config.getFileSizeMb()).append("; ");
        sb.append("buffer_size-").append(config.getBufferSizeBytes()).append("; ");
        sb.append("log_dir-").append(config.getLogDir()).append("; ");
        sb.append("log_period-").append(config.getLogPeriodHours()).append("; ");
        sb.append("filter_tag-").append(config.getFilterTag()).append("; ");
        sb.append("filter_level-").append(config.getFilterLevel()).append("; ");
        sb.append("filter_package-").append(config.getFilterPackage());
        return sb.toString();
    }

    /**
     * 安全字符串转整数
     * @param s 字符串
     * @return 整数（解析失败返回0）
     */
    private int safeInt(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; } }
}