package com.xcheng.xclogger.util;

import android.content.Context;
import android.util.Log;

import com.xcheng.xclogger.processctr.ConfigLoader;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;

/**
 * DatabaseMigration - 数据库迁移工具，负责处理数据库结构变更
 *
 * 功能方法：
 * - migrateIfNeeded(Context) - 检查并执行必要的数据库迁移
 * - migrateV1ToV2(Context) - 从版本1迁移到版本2
 * - migrateV2ToV3(Context) - 从版本2迁移到版本3
 * - getDefaultOperationHistoryPath(Context) - 获取默认操作历史文件路径
 */
public class DatabaseMigration {
    private static final String TAG = "DatabaseMigration";
    private static final int CURRENT_VERSION = 2;

    /**
     * 检查并执行必要的数据库迁移
     * @param context Android上下文
     */
    public static void migrateIfNeeded(Context context) {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(context);
            int currentVersion = db.getDatabaseVersion();

            Log.i(TAG, "Current database version: " + currentVersion);

            if (currentVersion < CURRENT_VERSION) {
                Log.i(TAG, "Database migration needed from version " + currentVersion + " to " + CURRENT_VERSION);

                // 执行迁移
                if (currentVersion < 2) {
                    migrateV1ToV2(context);
                }

                // 设置新版本
                db.setDatabaseVersion(CURRENT_VERSION);
                Log.i(TAG, "Database migration completed to version " + CURRENT_VERSION);
            } else {
                Log.i(TAG, "Database is up to date, no migration needed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Database migration failed", e);
        }
    }

    /**
     * 从版本1迁移到版本2
     * 添加operation_history_path字段
     * @param context Android上下文
     */
    private static void migrateV1ToV2(Context context) {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(context);

            // 检查operation_history_path字段是否已存在
            if (db.isOperationHistoryPathExists()) {
                Log.i(TAG, "operation_history_path field already exists, skipping migration");
                return;
            }

            // 获取默认操作历史文件路径
            String defaultPath = getDefaultOperationHistoryPath(context);
            if (defaultPath != null && !defaultPath.isEmpty()) {
                db.setOperationHistoryPath(defaultPath);
                Log.i(TAG, "Added operation_history_path field with default value: " + defaultPath);
            } else {
                Log.e(TAG, "Failed to get default operation history path");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to migrate from version 1 to 2", e);
        }
    }

    /**
     * 从版本2迁移到版本3（预留）
     * @param context Android上下文
     */
    private static void migrateV2ToV3(Context context) {
        // 预留未来迁移逻辑
        Log.i(TAG, "Migration from version 2 to 3 not implemented yet");
    }

    /**
     * 获取默认操作历史文件路径
     * @param context Android上下文
     * @return 默认操作历史文件路径
     */
    private static String getDefaultOperationHistoryPath(Context context) {
        try {
            // 从默认配置XML文件读取log_dir
            InputStream inputStream = context.getAssets().open("default_config.xml");
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(inputStream, "UTF-8");

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("log_dir".equals(tagName)) {
                        String logDir = parser.nextText();
                        inputStream.close();
                        // 返回操作历史文件路径：log_dir/A_OperationHistory.txt
                        return logDir + "/A_OperationHistory.txt";
                    }
                }
                eventType = parser.next();
            }

            inputStream.close();
            Log.e(TAG, "log_dir not found in default config XML");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get default operation history path", e);
            return null;
        }
    }
}