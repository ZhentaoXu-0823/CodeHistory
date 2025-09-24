package com.xcheng.xclogger.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * DatabaseMigration - 数据库迁移工具
 *
 * 功能方法：
 * - migrateIfNeeded() - 检查并执行数据库迁移
 * - getCurrentVersion() - 获取当前数据库版本
 * - setVersion() - 设置数据库版本
 * - migrateV1ToV2() - 版本1到2的迁移
 * - migrateV2ToV3() - 版本2到3的迁移
 */
public class DatabaseMigration {
    private static final String TAG = "DatabaseMigration";
    private static final String VERSION_KEY = "database_version";
    private static final int CURRENT_VERSION = 1;

    /**
     * 检查并执行数据库迁移
     */
    public static void migrateIfNeeded(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(
                    XcLoggerDatabase.PREF, Context.MODE_PRIVATE
            );

            int currentVersion = getCurrentVersion(prefs);
            Log.i(TAG, "Current database version: " + currentVersion);

            if (currentVersion < CURRENT_VERSION) {
                Log.i(TAG, "Database migration needed from version " + currentVersion + " to " + CURRENT_VERSION);
                performMigration(prefs, currentVersion, CURRENT_VERSION);
            } else {
                Log.i(TAG, "Database is up to date");
            }

        } catch (Exception e) {
            Log.e(TAG, "Database migration failed", e);
        }
    }

    /**
     * 获取当前数据库版本
     */
    private static int getCurrentVersion(SharedPreferences prefs) {
        return prefs.getInt(VERSION_KEY, 1); // 默认为版本1
    }

    /**
     * 设置数据库版本
     */
    private static void setVersion(SharedPreferences prefs, int version) {
        prefs.edit().putInt(VERSION_KEY, version).apply();
    }

    /**
     * 执行数据库迁移
     */
    private static void performMigration(SharedPreferences prefs, int fromVersion, int toVersion) {
        try {
            for (int version = fromVersion + 1; version <= toVersion; version++) {
                Log.i(TAG, "Migrating to version " + version);

                switch (version) {
                    case 2:
                        migrateV1ToV2(prefs);
                        break;
                    case 3:
                        migrateV2ToV3(prefs);
                        break;
                    // 添加更多版本迁移...
                }

                setVersion(prefs, version);
                Log.i(TAG, "Migration to version " + version + " completed");
            }

        } catch (Exception e) {
            Log.e(TAG, "Migration failed at version " + (fromVersion + 1), e);
            throw new RuntimeException("Database migration failed", e);
        }
    }

    /**
     * 版本1到2的迁移
     */
    private static void migrateV1ToV2(SharedPreferences prefs) {
        Log.i(TAG, "Migrating from version 1 to 2");

        // 示例：添加新字段
        // 如果新字段不存在，设置默认值
        if (!prefs.contains("new_field")) {
            prefs.edit().putString("new_field", "default_value").apply();
        }

        // 添加更多迁移逻辑...
    }

    /**
     * 版本2到3的迁移
     */
    private static void migrateV2ToV3(SharedPreferences prefs) {
        Log.i(TAG, "Migrating from version 2 to 3");

        // 示例：重构数据结构
        // 重命名字段
        String oldValue = prefs.getString("old_field", "");
        if (!oldValue.isEmpty()) {
            prefs.edit()
                    .putString("new_field_name", oldValue)
                    .remove("old_field")
                    .apply();
        }

        // 添加更多迁移逻辑...
    }
}