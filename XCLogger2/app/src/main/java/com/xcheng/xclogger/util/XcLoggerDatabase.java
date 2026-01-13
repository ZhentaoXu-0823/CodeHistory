package com.xcheng.xclogger.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class XcLoggerDatabase {
    private static final String TAG = "XcLoggerDatabase";
    private static final String PREFS_NAME = "XcLoggerPrefs";
    private static final String K_VERSION = "database_version";
    private static final String K_OPERATION_HISTORY_PATH = "operation_history_path";

    // 配置键
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
            editor.putInt(K_TOTAL_SIZE, config.getTotalSizeGb());
            editor.putInt(K_FILE_SIZE, config.getFileSizeMb());
            editor.putInt(K_BUFFER_SIZE, config.getBufferSizeBytes());
            editor.putString(K_LOG_DIR, config.getLogDir());
            editor.putInt(K_LOG_PERIOD, config.getLogPeriodHours());
            editor.putString(K_FILTER_TAG, config.getFilterTag());
            editor.putString(K_FILTER_LEVEL, config.getFilterLevel());
            editor.putString(K_FILTER_PACKAGE, config.getFilterPackage());
            editor.apply();
            Log.i(TAG, "Config saved to database");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save config", e);
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

    public int getDatabaseVersion() {
        return prefs.getInt(K_VERSION, 1);
    }

    public void setDatabaseVersion(int version) {
        prefs.edit().putInt(K_VERSION, version).apply();
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