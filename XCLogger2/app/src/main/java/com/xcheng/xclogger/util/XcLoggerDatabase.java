package com.xcheng.xclogger.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * XcLoggerDatabase - SharedPreferences数据库管理类
 *
 * 功能方法：
 * - XcLoggerDatabase(Context) - 构造函数，初始化数据库管理器
 * - saveConfig(XcLoggerConfig) - 保存配置到数据库
 * - loadConfig() - 从数据库加载配置
 * - saveRunningState(boolean) - 保存运行状态
 * - loadRunningState() - 加载运行状态
 * - getDatabaseVersion() - 获取数据库版本
 * - setDatabaseVersion(int) - 设置数据库版本
 */
public class XcLoggerDatabase {
    public static final String PREF = "xc_logger_pref";
    private static final String K_TOTAL = "total_size_gb";
    private static final String K_FILE = "file_size_mb";
    private static final String K_BUFFER = "buffer_size_bytes";
    private static final String K_DIR = "log_dir";
    private static final String K_PERIOD = "log_period_hours";
    private static final String K_TAG = "filter_tag";
    private static final String K_LEVEL = "filter_level";
    private static final String K_PKG = "filter_pkg";
    private static final String K_RUNNING = "is_running";
    private static final String K_VERSION = "database_version";

    private final SharedPreferences sp;

    /**
     * 构造函数，初始化数据库管理器
     * @param ctx Android上下文
     */
    public XcLoggerDatabase(Context ctx) {
        this.sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /**
     * 保存配置到数据库
     * @param c 配置对象
     */
    public void saveConfig(XcLoggerConfig c) {
        SharedPreferences.Editor e = sp.edit();
        e.putInt(K_TOTAL, c.getTotalSizeGb());
        e.putInt(K_FILE, c.getFileSizeMb());
        e.putInt(K_BUFFER, c.getBufferSizeBytes());
        e.putString(K_DIR, c.getLogDir());
        e.putInt(K_PERIOD, c.getLogPeriodHours());
        e.putString(K_TAG, c.getFilterTag());
        e.putString(K_LEVEL, c.getFilterLevel());
        e.putString(K_PKG, c.getFilterPackage());
        e.apply();
    }

    /**
     * 从数据库加载配置
     * @return 配置对象，如果不存在则返回null
     */
    public XcLoggerConfig loadConfig() {
        if (!sp.contains(K_DIR)) return null;
        XcLoggerConfig c = new XcLoggerConfig();
        c.setTotalSizeGb(sp.getInt(K_TOTAL, 0));
        c.setFileSizeMb(sp.getInt(K_FILE, 0));
        c.setBufferSizeBytes(sp.getInt(K_BUFFER, 0));
        c.setLogDir(sp.getString(K_DIR, ""));
        c.setLogPeriodHours(sp.getInt(K_PERIOD, 0));
        c.setFilterTag(sp.getString(K_TAG, ""));
        c.setFilterLevel(sp.getString(K_LEVEL, ""));
        c.setFilterPackage(sp.getString(K_PKG, ""));
        return c;
    }

    /**
     * 保存运行状态
     * @param running 运行状态
     */
    public void saveRunningState(boolean running) {
        sp.edit().putBoolean(K_RUNNING, running).apply();
    }

    /**
     * 加载运行状态
     * @return 运行状态
     */
    public boolean loadRunningState() {
        return sp.getBoolean(K_RUNNING, false);
    }

    /**
     * 获取数据库版本
     * @return 数据库版本
     */
    public int getDatabaseVersion() {
        return sp.getInt(K_VERSION, 1);
    }

    /**
     * 设置数据库版本
     * @param version 版本号
     */
    public void setDatabaseVersion(int version) {
        sp.edit().putInt(K_VERSION, version).apply();
    }
}