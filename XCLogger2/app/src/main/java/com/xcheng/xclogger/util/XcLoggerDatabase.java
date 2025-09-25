package com.xcheng.xclogger.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * XcLoggerDatabase - 数据库管理模块，负责配置和状态的持久化存储
 *
 * 功能方法：
 * - XcLoggerDatabase(Context) - 构造函数，初始化数据库连接
 * - saveConfig(XcLoggerConfig) - 保存配置到数据库
 * - loadConfig() - 从数据库加载配置
 * - saveRunningState(boolean) - 保存运行状态
 * - loadRunningState() - 加载运行状态
 * - getDatabaseVersion() - 获取数据库版本
 * - setDatabaseVersion(int) - 设置数据库版本
 * - getOperationHistoryPath() - 获取操作历史文件路径
 * - setOperationHistoryPath(String) - 设置操作历史文件路径
 * - isOperationHistoryPathExists() - 检查操作历史文件路径字段是否存在
 */
public class XcLoggerDatabase {
    private static final String TAG = "XcLoggerDatabase";
    private static final String PREFS_NAME = "XcLoggerPrefs";
    private static final String K_VERSION = "database_version";
    private static final String K_OPERATION_HISTORY_PATH = "operation_history_path";

    // 配置相关键名
    private static final String K_TOTAL_SIZE_GB = "total_size_gb";
    private static final String K_FILE_SIZE_MB = "file_size_mb";
    private static final String K_BUFFER_SIZE_BYTES = "buffer_size_bytes";
    private static final String K_LOG_DIR = "log_dir";
    private static final String K_LOG_PERIOD_HOURS = "log_period_hours";
    private static final String K_FILTER_TAG = "filter_tag";
    private static final String K_FILTER_LEVEL = "filter_level";
    private static final String K_FILTER_PACKAGE = "filter_package";
    private static final String K_IS_RUNNING = "is_running";

    private SharedPreferences prefs;

    /**
     * 构造函数，初始化数据库连接
     * @param context Android上下文
     */
    public XcLoggerDatabase(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 保存配置到数据库
     * @param config 配置对象
     */
    public void saveConfig(XcLoggerConfig config) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(K_TOTAL_SIZE_GB, config.getTotalSizeGb());
            editor.putInt(K_FILE_SIZE_MB, config.getFileSizeMb());
            editor.putInt(K_BUFFER_SIZE_BYTES, config.getBufferSizeBytes());
            editor.putString(K_LOG_DIR, config.getLogDir());
            editor.putInt(K_LOG_PERIOD_HOURS, config.getLogPeriodHours());
            editor.putString(K_FILTER_TAG, config.getFilterTag());
            editor.putString(K_FILTER_LEVEL, config.getFilterLevel());
            editor.putString(K_FILTER_PACKAGE, config.getFilterPackage());
            editor.apply();
            Log.i(TAG, "Config saved to database");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save config", e);
        }
    }

    /**
     * 从数据库加载配置
     * @return 配置对象，如果加载失败返回null
     */
    public XcLoggerConfig loadConfig() {
        try {
            XcLoggerConfig config = new XcLoggerConfig();
            config.setTotalSizeGb(prefs.getInt(K_TOTAL_SIZE_GB, 4));
            config.setFileSizeMb(prefs.getInt(K_FILE_SIZE_MB, 4));
            config.setBufferSizeBytes(prefs.getInt(K_BUFFER_SIZE_BYTES, 1024));
            config.setLogDir(prefs.getString(K_LOG_DIR, "/storage/emulated/0/XcLogger"));
            config.setLogPeriodHours(prefs.getInt(K_LOG_PERIOD_HOURS, 168));
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

    /**
     * 保存运行状态
     * @param running 运行状态
     */
    public void saveRunningState(boolean running) {
        try {
            prefs.edit().putBoolean(K_IS_RUNNING, running).apply();
            Log.i(TAG, "Running state saved: " + running);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save running state", e);
        }
    }

    /**
     * 加载运行状态
     * @return 运行状态
     */
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

    /**
     * 获取数据库版本
     * @return 数据库版本号
     */
    public int getDatabaseVersion() {
        return prefs.getInt(K_VERSION, 1);
    }

    /**
     * 设置数据库版本
     * @param version 版本号
     */
    public void setDatabaseVersion(int version) {
        prefs.edit().putInt(K_VERSION, version).apply();
    }

    /**
     * 获取操作历史文件路径
     * @return 操作历史文件路径
     */
    public String getOperationHistoryPath() {
        return prefs.getString(K_OPERATION_HISTORY_PATH, "");
    }

    /**
     * 设置操作历史文件路径
     * @param path 操作历史文件路径
     */
    public void setOperationHistoryPath(String path) {
        prefs.edit().putString(K_OPERATION_HISTORY_PATH, path).apply();
        Log.i(TAG, "Operation history path set: " + path);
    }

    /**
     * 检查操作历史文件路径字段是否存在
     * @return 是否存在
     */
    public boolean isOperationHistoryPathExists() {
        return prefs.contains(K_OPERATION_HISTORY_PATH);
    }
}