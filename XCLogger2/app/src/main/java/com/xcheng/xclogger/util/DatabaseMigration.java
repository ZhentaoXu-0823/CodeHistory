package com.xcheng.xclogger.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.xcheng.xclogger.filemanager.FileManager;
import com.xcheng.xclogger.processctr.ConfigLoader;

/**
 * DatabaseMigration - 数据库迁移工具类，处理SharedPreferences的版本升级
 *
 * 功能方法：
 * - migrateIfNeeded(Context) - 检查并执行必要的数据库迁移
 * - getCurrentVersion(Context) - 获取当前数据库版本
 * - setCurrentVersion(Context, int) - 设置数据库版本
 * - migrateV1ToV2(Context) - 执行V1到V2的迁移
 * - migrateV2ToV3(Context) - 执行V2到V3的迁移
 * - getDefaultOperationHistoryPath() - 获取默认操作历史路径（目录路径）
 */
public class DatabaseMigration {
    private static final String TAG = "DatabaseMigration";
    private static final String PREFS_NAME = "XcLoggerPrefs";
    private static final String KEY_DATABASE_VERSION = "database_version";
    private static final String KEY_OPERATION_HISTORY_PATH = "operation_history_path";

    private static final int CURRENT_VERSION = 3; // 当前数据库版本

    /**
     * 检查并执行必要的数据库迁移
     * @param context Android上下文
     */
    public static void migrateIfNeeded(Context context) {
        int currentVersion = getCurrentVersion(context);
        Log.i(TAG, "Current database version: " + currentVersion + ", target version: " + CURRENT_VERSION);

        if (currentVersion < 2) {
            migrateV1ToV2(context);
        }

        if (currentVersion < 3) {
            migrateV2ToV3(context);
        }
    }

    /**
     * 获取当前数据库版本
     * @param context Android上下文
     * @return 当前版本号
     */
    private static int getCurrentVersion(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_DATABASE_VERSION, 1); // 默认版本1
    }

    /**
     * 设置数据库版本
     * @param context Android上下文
     * @param version 版本号
     */
    private static void setCurrentVersion(Context context, int version) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_DATABASE_VERSION, version).apply();
        Log.i(TAG, "Database version updated to: " + version);
    }

    /**
     * 执行V1到V2的迁移
     * @param context Android上下文
     */
    private static void migrateV1ToV2(Context context) {
        Log.i(TAG, "Executing migration V1 to V2");

        // 检查operation_history_path字段是否存在
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_OPERATION_HISTORY_PATH)) {
            // 设置默认操作历史路径（目录路径）
            String defaultPath = getDefaultOperationHistoryPath();
            prefs.edit().putString(KEY_OPERATION_HISTORY_PATH, defaultPath).apply();
            Log.i(TAG, "Added operation_history_path: " + defaultPath);
            try {
                FileManager fm = new FileManager(context);
                fm.appendOperationHistory("DB migrated V1 -> V2 (operation_history_path added: " + defaultPath + ")");
            } catch (Exception e) {
                Log.w(TAG, "Failed to record operation history for migration V1->V2", e);
            }
        }

        setCurrentVersion(context, 2);
    }

    /**
     * 执行V2到V3的迁移
     * @param context Android上下文
     */
    private static void migrateV2ToV3(Context context) {
        Log.i(TAG, "Executing migration V2 to V3");

        // 检查operation_history_path字段是否存在
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_OPERATION_HISTORY_PATH)) {
            // 设置默认操作历史路径（目录路径）
            String defaultPath = getDefaultOperationHistoryPath();
            prefs.edit().putString(KEY_OPERATION_HISTORY_PATH, defaultPath).apply();
            Log.i(TAG, "Added operation_history_path: " + defaultPath);
            try {
                FileManager fm = new FileManager(context);
                fm.appendOperationHistory("DB migrated V2 -> V3 (operation_history_path added: " + defaultPath + ")");
            } catch (Exception e) {
                Log.w(TAG, "Failed to record operation history for migration V2->V3", e);
            }
        }

        setCurrentVersion(context, 3);
    }

    /**
     * 获取默认操作历史路径（目录路径）
     * @return 默认操作历史目录路径
     */
    private static String getDefaultOperationHistoryPath() {
        try {
            // 从默认配置获取log_dir作为操作历史目录
            XcLoggerConfig config = ConfigLoader.getInstance().getCurrentConfig();
            if (config != null && config.getLogDir() != null) {
                return config.getLogDir(); // 返回目录路径，不包含文件名
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get config for default operation history path", e);
        }

        // 如果无法获取配置，使用默认目录路径
        return com.xcheng.xclogger.filemanager.FileManager.DEFAULT_LOG_DIR;
    }
}