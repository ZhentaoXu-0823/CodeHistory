package com.xcheng.xclogger;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.processctr.DeveloperActivity;
import com.xcheng.xclogger.processctr.LogServiceController;
import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.service.RemoteBindService;
import com.xcheng.xclogger.ui.XcLoggerConfigActivity;
import com.xcheng.xclogger.util.DatabaseMigration;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

/**
 * MainActivity - 主界面控制器，提供用户交互入口
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.POST_NOTIFICATIONS
    };

    // UI组件
    private TextView txtTitle, btnDetail, txtState, txtPath;
    private ImageView imgState;
    private LinearLayout btnStart;

    // 数据相关
    private boolean running = false;
    private XcLoggerConfig config;
    private XcLoggerDatabase database;
    private ProcessController processController;
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    // 开发者入口相关
    private int titleTapCount = 0;
    private long lastTapTs = 0L;
    private AlertDialog developerDialog;
    private android.os.Handler dialogHandler = new android.os.Handler();
    private static final int PRELOAD_THRESHOLD = 3;
    private static final int TRIGGER_THRESHOLD = 5;
    private static final long DIALOG_TIMEOUT = 1000;

    private final Runnable releaseDialogRunnable = new Runnable() {
        @Override
        public void run() {
            releaseDeveloperDialog();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DatabaseMigration.migrateIfNeeded(this);
        ConfigLoader.getInstance().load(this);
        startInitialAutoStartIfNeeded();

        startService(new Intent(this, RemoteBindService.class));

        initViews();
        initData();
        requestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermissions()) {
            refreshConfigFromLoader();
            refreshStateFromDatabase();
            registerPrefsListener();
        } else {
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseDeveloperDialog();
        unregisterPrefsListener();
    }

    private void startInitialAutoStartIfNeeded() {
        ConfigLoader loader = ConfigLoader.getInstance();
        if (!loader.wasLastLoadInitializedFromXml() || !loader.getLastInitialAutoStartEnabled()) {
            return;
        }

        try {
            LogServiceController.startLogService(this, "initial_auto_start:first_launch");
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.err_toggle_state_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void initViews() {
        txtTitle = findViewById(R.id.txt_title);
        btnDetail = findViewById(R.id.btn_detail);
        btnStart = findViewById(R.id.btn_start);
        imgState = findViewById(R.id.img_state);
        txtState = findViewById(R.id.txt_state);
        txtPath = findViewById(R.id.txt_path);

        btnStart.setOnClickListener(v -> toggleState());
        btnDetail.setOnClickListener(v -> startActivity(new Intent(this, XcLoggerConfigActivity.class)));
        txtPath.setOnClickListener(v -> editPath());
        txtTitle.setOnClickListener(v -> handleSecretTap());
    }

    private void initData() {
        database = new XcLoggerDatabase(this);
        processController = ProcessController.getInstance(this);
        config = ConfigLoader.getInstance().getCurrentConfig();

        // 预先构造 SharedPreferences 监听器
        prefsListener = (prefs, key) -> {
            if (XcLoggerDatabase.K_IS_RUNNING.equals(key)) {
                refreshStateFromDatabase();
            }
        };
    }

    private void refreshConfigFromLoader() {
        this.config = ConfigLoader.getInstance().getCurrentConfig();
        if (config != null) {
            txtPath.setText(config.getLogDir());
        }
    }

    private void refreshStateFromDatabase() {
        running = database.loadRunningState();
        updateStateUi();
    }

    private void registerPrefsListener() {
        if (prefsListener != null) {
            database.getPrefs().registerOnSharedPreferenceChangeListener(prefsListener);
        }
    }

    private void unregisterPrefsListener() {
        if (prefsListener != null) {
            database.getPrefs().unregisterOnSharedPreferenceChangeListener(prefsListener);
        }
    }

    private void requestPermissions() {
        if (!checkPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    private boolean checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (checkPermissions()) {
                refreshStateFromDatabase();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void toggleState() {
        try {
            if (running) {
                LogServiceController.stopLogService(this);
                processController.recordOperationHistory("User stopped logging");
            } else {
                LogServiceController.startLogService(this);
                processController.recordOperationHistory("User started logging");
            }
            refreshStateFromDatabase();
        } catch (Exception e) {
            String errorMsg = getString(R.string.err_toggle_state_failed, e.getMessage());
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            processController.recordOperationHistory("Error: Failed to toggle state - " + e.getMessage());
        }
    }

    private void updateStateUi() {
        if (running) {
            imgState.setImageResource(R.drawable.ic_start);
            txtState.setText(R.string.state_running);
        } else {
            imgState.setImageResource(R.drawable.ic_stop);
            txtState.setText(R.string.state_stopped);
        }
    }

    private void editPath() {
        if (running) {
            Toast.makeText(this, R.string.warn_stop_log_first, Toast.LENGTH_LONG).show();
            return;
        }

        EditText input = new EditText(this);
        input.setText(config.getLogDir());

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_edit_path_title)
                .setView(input)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String oldPath = config.getLogDir();
                    String newPath = input.getText().toString();

                    config.setLogDir(newPath);
                    ConfigLoader.getInstance().updateConfig(this, config);
                    processController.getFileManager().updatePaths();
                    txtPath.setText(config.getLogDir());
                    processController.recordOperationHistory("User modified log directory from " + oldPath + " to " + newPath);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void handleSecretTap() {
        long now = System.currentTimeMillis();
        if (now - lastTapTs > 1000) titleTapCount = 0;
        lastTapTs = now;
        titleTapCount++;

        if (titleTapCount == PRELOAD_THRESHOLD) {
            preloadDeveloperDialog();
        } else if (titleTapCount >= TRIGGER_THRESHOLD) {
            titleTapCount = 0;
            showDeveloperDialog();
        }
    }

    private void preloadDeveloperDialog() {
        if (developerDialog != null) {
            releaseDeveloperDialog();
        }
        createDeveloperDialog();
        dialogHandler.removeCallbacks(releaseDialogRunnable);
        dialogHandler.postDelayed(releaseDialogRunnable, DIALOG_TIMEOUT);
    }

    private void createDeveloperDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);

        developerDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_enter_code)
                .setView(input)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    if ("0000".equals(input.getText().toString())) {
                        processController.recordOperationHistory("User accessed developer mode");
                        startActivity(new Intent(this, DeveloperActivity.class));
                    }
                    releaseDeveloperDialog();
                })
                .setNegativeButton(R.string.cancel, (d, w) -> releaseDeveloperDialog())
                .setOnDismissListener(dialog -> releaseDeveloperDialog())
                .create();
    }

    private void showDeveloperDialog() {
        if (developerDialog != null) {
            dialogHandler.removeCallbacks(releaseDialogRunnable);
            developerDialog.show();
        } else {
            createDeveloperDialog();
            if (developerDialog != null) {
                developerDialog.show();
            }
        }
    }

    private void releaseDeveloperDialog() {
        if (developerDialog != null) {
            if (developerDialog.isShowing()) {
                developerDialog.dismiss();
            }
            developerDialog = null;
        }
        dialogHandler.removeCallbacks(releaseDialogRunnable);
    }
}