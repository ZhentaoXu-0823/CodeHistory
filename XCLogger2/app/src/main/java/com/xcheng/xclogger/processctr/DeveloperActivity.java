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
import java.io.File;

/**
 * DeveloperActivity - 开发者界面控制器
 */
public class DeveloperActivity extends AppCompatActivity {
    private static final String TAG = "DeveloperActivity";

    private Button btnPrintDb;
    private Button btnResetDb;
    private Button btnToggleEncryption;
    private Button btnDecrypt;

    private XcLoggerDatabase database;
    private FileManager fileManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer);

        database = new XcLoggerDatabase(this);
        fileManager = new FileManager(this);

        initViews();
        updateEncryptionButtonText();
    }

    private void initViews() {
        btnPrintDb = findViewById(R.id.btn_print_db);
        btnResetDb = findViewById(R.id.btn_reset_db);
        btnToggleEncryption = findViewById(R.id.btn_toggle_encryption);
        btnDecrypt = findViewById(R.id.btn_decrypt);

        btnPrintDb.setOnClickListener(v -> printDb());
        btnResetDb.setOnClickListener(v -> resetDatabase());
        btnToggleEncryption.setOnClickListener(v -> toggleEncryption());
        btnDecrypt.setOnClickListener(v -> decryptLatestFile());
    }

    private void updateEncryptionButtonText() {
        boolean enabled = database.getEncryptionEnabled();
        btnToggleEncryption.setText(enabled ? R.string.btn_encryption_on : R.string.btn_encryption_off);
    }

    private void toggleEncryption() {
        if (checkLogRunningState()) {
            Toast.makeText(this, R.string.warn_stop_before_toggle, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            boolean currentState = database.getEncryptionEnabled();
            boolean newState = !currentState;
            database.setEncryptionEnabled(newState);
            updateEncryptionButtonText();

            int msgRes = newState ? R.string.msg_encryption_enabled : R.string.msg_encryption_disabled;
            Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show();
            fileManager.appendOperationHistory("Encryption toggled to: " + newState);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void decryptLatestFile() {
        if (checkLogRunningState()) {
            Toast.makeText(this, R.string.warn_stop_before_decrypt, Toast.LENGTH_LONG).show();
            return;
        }

        if (!database.getEncryptionEnabled()) {
            Toast.makeText(this, R.string.warn_encryption_not_enabled, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            File decryptedFile = fileManager.decryptLatestLogFile();
            if (decryptedFile != null) {
                String successMsg = getString(R.string.msg_decrypt_success, decryptedFile.getName());
                Toast.makeText(this, successMsg, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.msg_decrypt_failed, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void printDb() {
        XcLoggerConfig config = database.loadConfig();
        if (config == null) return;

        Log.i(TAG, "=== Database Configuration Info ===");
        Log.i(TAG, "is_running=" + database.loadRunningState());
        Log.i(TAG, "encryption_enabled=" + database.getEncryptionEnabled());
        Log.i(TAG, "=== End of Configuration Info ===");
    }

    private void resetDatabase() {
        if (checkLogRunningState()) {
            Toast.makeText(this, R.string.warn_stop_log_first, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            XcLoggerConfig defaultConfig = ConfigLoader.getInstance().resetToDefault(this);
            if (defaultConfig != null) {
                Toast.makeText(this, R.string.msg_db_reset_success, Toast.LENGTH_LONG).show();
                fileManager.appendOperationHistory("Developer reset database to default config");
            }
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean checkLogRunningState() {
        return database.loadRunningState();
    }
}