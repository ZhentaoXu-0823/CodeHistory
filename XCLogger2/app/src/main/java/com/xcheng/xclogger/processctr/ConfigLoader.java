package com.xcheng.xclogger.processctr;

import android.content.Context;
import android.util.Log;

import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;

/**
 * ConfigLoader - 配置加载器，负责从XML文件和数据库加载配置
 *
 * 功能方法：
 * - getInstance() - 获取单例实例
 * - load(Context) - 加载配置
 * - replaceWith(XcLoggerConfig) - 替换当前配置
 * - getCurrentConfig() - 获取当前配置
 * - current() - 获取当前配置（静态方法）
 * - updateConfig(XcLoggerConfig) - 更新配置并保存到数据库
 * - loadFromDatabase(Context) - 从数据库加载配置
 * - loadFromXml(Context) - 从XML文件加载配置
 */
public class ConfigLoader {
    private static final String TAG = "ConfigLoader";
    private static ConfigLoader instance;
    private XcLoggerConfig currentConfig;

    /**
     * 私有构造函数，实现单例模式
     */
    private ConfigLoader() {
        // 私有构造函数
    }

    /**
     * 获取单例实例
     * @return ConfigLoader单例实例
     */
    public static synchronized ConfigLoader getInstance() {
        if (instance == null) {
            instance = new ConfigLoader();
        }
        return instance;
    }

    /**
     * 获取当前配置（静态方法）
     * @return 当前配置对象
     */
    public static XcLoggerConfig current() {
        if (instance != null) {
            return instance.getCurrentConfig();
        }
        return null;
    }

    /**
     * 加载配置
     * @param context Android上下文
     * @return 配置对象
     */
    public XcLoggerConfig load(Context context) {
        try {
            // 首先尝试从数据库加载
            XcLoggerConfig config = loadFromDatabase(context);
            if (config != null) {
                currentConfig = config;
                Log.i(TAG, "Config loaded from database");
                return config;
            }

            // 如果数据库中没有配置，从XML文件加载
            config = loadFromXml(context);
            if (config != null) {
                currentConfig = config;
                // 保存到数据库
                XcLoggerDatabase db = new XcLoggerDatabase(context);
                db.saveConfig(config);
                Log.i(TAG, "Config loaded from XML and saved to database");
                return config;
            }

            Log.e(TAG, "Failed to load config from both database and XML");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error loading config", e);
            return null;
        }
    }

    /**
     * 替换当前配置
     * @param config 新配置
     */
    public void replaceWith(XcLoggerConfig config) {
        this.currentConfig = config;
        Log.i(TAG, "Config replaced with new configuration");
    }

    /**
     * 获取当前配置
     * @return 当前配置对象
     */
    public XcLoggerConfig getCurrentConfig() {
        return currentConfig;
    }

    /**
     * 更新配置并保存到数据库
     * @param context Android上下文
     * @param config 新配置
     */
    public void updateConfig(Context context, XcLoggerConfig config) {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(context);
            db.saveConfig(config);
            this.currentConfig = config;
            Log.i(TAG, "Config updated and saved to database");
        } catch (Exception e) {
            Log.e(TAG, "Failed to update config", e);
        }
    }

    /**
     * 从数据库加载配置
     * @param context Android上下文
     * @return 配置对象
     */
    private XcLoggerConfig loadFromDatabase(Context context) {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(context);
            return db.loadConfig();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load config from database", e);
            return null;
        }
    }

    /**
     * 从XML文件加载配置
     * @param context Android上下文
     * @return 配置对象
     */
    private XcLoggerConfig loadFromXml(Context context) {
        try {
            InputStream inputStream = context.getAssets().open("default_config.xml");
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(inputStream, "UTF-8");

            XcLoggerConfig config = new XcLoggerConfig();
            int eventType = parser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    switch (tagName) {
                        case "total_size_gb":
                            config.setTotalSizeGb(Integer.parseInt(parser.nextText()));
                            break;
                        case "file_size_mb":
                            config.setFileSizeMb(Integer.parseInt(parser.nextText()));
                            break;
                        case "buffer_size_bytes":
                            config.setBufferSizeBytes(Integer.parseInt(parser.nextText()));
                            break;
                        case "log_dir":
                            config.setLogDir(parser.nextText());
                            break;
                        case "log_period_hours":
                            config.setLogPeriodHours(Integer.parseInt(parser.nextText()));
                            break;
                        case "filter_tag":
                            config.setFilterTag(parser.nextText());
                            break;
                        case "filter_level":
                            config.setFilterLevel(parser.nextText());
                            break;
                        case "filter_package":
                            config.setFilterPackage(parser.nextText());
                            break;
                    }
                }
                eventType = parser.next();
            }

            inputStream.close();
            Log.i(TAG, "Config loaded from XML file");
            return config;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load config from XML", e);
            return null;
        }
    }
}