package com.xcheng.xclogger.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class XcLoggerDatabase {
    private static final String TAG = "XcLoggerDatabase";
    private static final String PREFS_NAME = "XcLoggerPrefs";
    private static final String K_VERSION = "database_version";
    private static final String K_CONFIG_VERSION = "config_version";
    private static final String K_OPERATION_HISTORY_PATH = "operation_history_path";

    private static final String K_TOTAL_SIZE = "total_size";
    private static final String K_FILE_SIZE = "file_size";
    private static final String K_BUFFER_SIZE = "buffer_size";
    private static final String K_LOG_DIR = "log_dir";
    private static final String K_LOG_PERIOD = "log_period";
    private static final String K_FILTER_TAG = "filter_tag";
    private static final String K_FILTER_LEVEL = "filter_level";
    private static final String K_FILTER_PACKAGE = "filter_package";

    public static final String K_IS_RUNNING = "is_running";
    public static final String K_FILE_INDEX = "file_index"; // 全局日志文件序号
    private static final String K_CONFIG_INITIALIZED = "config_initialized";
    private static final String K_INITIAL_AUTO_START_ENABLED = "initial_auto_start_enabled";

    private static final String K_ENCRYPTION_ENABLED = "encryption_enabled";
    private static final String K_COMPRESS_STATE = "compress_state";
    private static final String K_PENDING_ZIP_FILES = "pending_zip_files";
    private static final String K_UPLOAD_FAIL_COUNT = "upload_fail_count";
    private static final String K_CANCEL_COMPRESS_REQUESTED = "cancel_compress_requested";
    private static final String K_RESTART_COMPRESS_REQUESTED = "restart_compress_requested";

    private final SharedPreferences prefs;

    public SharedPreferences getPrefs() {
        return prefs;
    }

    public XcLoggerDatabase(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveConfig(XcLoggerConfig config) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            putConfig(editor, config);
            if (!editor.commit()) {
                Log.w(TAG, "Config commit returned false");
            }
            Log.i(TAG, "Config saved to database");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save config", e);
        }
    }

    public boolean saveInitialConfig(XcLoggerConfig config, boolean initialAutoStartEnabled) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            putConfig(editor, config);
            editor.putBoolean(K_INITIAL_AUTO_START_ENABLED, initialAutoStartEnabled);
            editor.putBoolean(K_CONFIG_INITIALIZED, true);
            boolean success = editor.commit();
            Log.i(TAG, "Initial config saved: success=" + success + ", initialAutoStart=" + initialAutoStartEnabled);
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save initial config", e);
            return false;
        }
    }

    private void putConfig(SharedPreferences.Editor editor, XcLoggerConfig config) {
        editor.putInt(K_TOTAL_SIZE, config.getTotalSizeGb());
        editor.putInt(K_FILE_SIZE, config.getFileSizeMb());
        editor.putInt(K_BUFFER_SIZE, config.getBufferSizeBytes());
        editor.putString(K_LOG_DIR, config.getLogDir());
        editor.putInt(K_LOG_PERIOD, config.getLogPeriodHours());
        editor.putString(K_FILTER_TAG, config.getFilterTag());
        editor.putString(K_FILTER_LEVEL, config.getFilterLevel());
        editor.putString(K_FILTER_PACKAGE, config.getFilterPackage());
    }

    public boolean hasSavedConfig() {
        try {
            return prefs.contains(K_TOTAL_SIZE)
                    || prefs.contains(K_FILE_SIZE)
                    || prefs.contains(K_BUFFER_SIZE)
                    || prefs.contains(K_LOG_DIR)
                    || prefs.contains(K_LOG_PERIOD)
                    || prefs.contains(K_FILTER_TAG)
                    || prefs.contains(K_FILTER_LEVEL)
                    || prefs.contains(K_FILTER_PACKAGE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to check saved config", e);
            return false;
        }
    }

    public XcLoggerConfig loadConfig() {
        try {
            XcLoggerConfig config = new XcLoggerConfig();
            config.setTotalSizeGb(prefs.getInt(K_TOTAL_SIZE, 4));
            config.setFileSizeMb(prefs.getInt(K_FILE_SIZE, 4));
            config.setBufferSizeBytes(prefs.getInt(K_BUFFER_SIZE, 1024));
            config.setLogDir(prefs.getString(K_LOG_DIR, "/storage/emulated/0/XcLogger"));
            config.setLogPeriodHours(prefs.getInt(K_LOG_PERIOD, 168));
            config.setFilterTag(prefs.getString(K_FILTER_TAG, "all"));
            config.setFilterLevel(prefs.getString(K_FILTER_LEVEL, "all"));
            config.setFilterPackage(prefs.getString(K_FILTER_PACKAGE, "all"));
            Log.i(TAG, "Config loaded from database");
            return config;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load config", e);
            return null;
        }
    }

    public void saveRunningState(boolean running) {
        try {
            prefs.edit().putBoolean(K_IS_RUNNING, running).apply();
            Log.i(TAG, "Running state saved: " + running);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save running state", e);
        }
    }

    public boolean loadRunningState() {
        try {
            boolean running = prefs.getBoolean(K_IS_RUNNING, false);
            Log.i(TAG, "Running state loaded: " + running);
            return running;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load running state", e);
            return false;
        }
    }

    public boolean isConfigInitialized() {
        try {
            return prefs.getBoolean(K_CONFIG_INITIALIZED, false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load config initialized state", e);
            return false;
        }
    }

    public void setConfigInitialized(boolean initialized) {
        try {
            prefs.edit().putBoolean(K_CONFIG_INITIALIZED, initialized).apply();
            Log.i(TAG, "Config initialized state saved: " + initialized);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save config initialized state", e);
        }
    }

    public boolean loadInitialAutoStartEnabled() {
        try {
            return prefs.getBoolean(K_INITIAL_AUTO_START_ENABLED, false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load initial auto start state", e);
            return false;
        }
    }

    public void saveInitialAutoStartEnabled(boolean enabled) {
        try {
            prefs.edit().putBoolean(K_INITIAL_AUTO_START_ENABLED, enabled).apply();
            Log.i(TAG, "Initial auto start state saved: " + enabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save initial auto start state", e);
        }
    }

    public boolean getEncryptionEnabled() {
        try {
            return prefs.getBoolean(K_ENCRYPTION_ENABLED, false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load encryption enabled state", e);
            return false;
        }
    }

    public void setEncryptionEnabled(boolean enabled) {
        try {
            prefs.edit().putBoolean(K_ENCRYPTION_ENABLED, enabled).apply();
            Log.i(TAG, "Encryption enabled state saved: " + enabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save encryption enabled state", e);
        }
    }

    public String getCompressState() {
        return prefs.getString(K_COMPRESS_STATE, "IDLE");
    }

    public void setCompressState(String state) {
        prefs.edit().putString(K_COMPRESS_STATE, state).apply();
        Log.i(TAG, "Compress state saved: " + state);
    }

    public String getPendingZipFiles() {
        return prefs.getString(K_PENDING_ZIP_FILES, "");
    }

    public void setPendingZipFiles(String zipFiles) {
        prefs.edit().putString(K_PENDING_ZIP_FILES, zipFiles == null ? "" : zipFiles).apply();
        Log.i(TAG, "Pending zip files saved: " + zipFiles);
    }

    public int getUploadFailCount() {
        return prefs.getInt(K_UPLOAD_FAIL_COUNT, 0);
    }

    public void setUploadFailCount(int count) {
        prefs.edit().putInt(K_UPLOAD_FAIL_COUNT, count).apply();
        Log.i(TAG, "Upload fail count saved: " + count);
    }

    public boolean isCancelCompressRequested() {
        return prefs.getBoolean(K_CANCEL_COMPRESS_REQUESTED, false);
    }

    public void setCancelCompressRequested(boolean requested) {
        prefs.edit().putBoolean(K_CANCEL_COMPRESS_REQUESTED, requested).apply();
        Log.i(TAG, "Cancel compress requested saved: " + requested);
    }

    public boolean isRestartCompressRequested() {
        return prefs.getBoolean(K_RESTART_COMPRESS_REQUESTED, false);
    }

    public void setRestartCompressRequested(boolean requested) {
        prefs.edit().putBoolean(K_RESTART_COMPRESS_REQUESTED, requested).apply();
        Log.i(TAG, "Restart compress requested saved: " + requested);
    }

    public void clearCompressTaskState() {
        prefs.edit()
                .putString(K_COMPRESS_STATE, "IDLE")
                .putString(K_PENDING_ZIP_FILES, "")
                .putInt(K_UPLOAD_FAIL_COUNT, 0)
                .putBoolean(K_CANCEL_COMPRESS_REQUESTED, false)
                .putBoolean(K_RESTART_COMPRESS_REQUESTED, false)
                .apply();
        Log.i(TAG, "Compress task state cleared");
    }

    public int getDatabaseVersion() {
        return prefs.getInt(K_CONFIG_VERSION, 1);
    }

    public void setDatabaseVersion(int version) {
        prefs.edit().putInt(K_CONFIG_VERSION, version).apply();
    }

    public String getOperationHistoryPath() {
        String path = prefs.getString(K_OPERATION_HISTORY_PATH, "");
        Log.d(TAG, "Operation history path from database: " + (path.isEmpty() ? "empty" : path));
        return path;
    }

    public void setOperationHistoryPath(String path) {
        prefs.edit().putString(K_OPERATION_HISTORY_PATH, path).apply();
        Log.i(TAG, "Operation history path set: " + path);
    }

    public boolean isOperationHistoryPathExists() {
        boolean exists = prefs.contains(K_OPERATION_HISTORY_PATH);
        Log.d(TAG, "Operation history path field exists: " + exists);
        return exists;
    }
}
