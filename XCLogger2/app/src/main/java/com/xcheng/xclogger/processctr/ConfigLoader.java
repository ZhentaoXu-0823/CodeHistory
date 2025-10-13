package com.xcheng.xclogger.processctr;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.Log;
import com.xcheng.xclogger.R;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;

/**
 * ConfigLoader - 配置加载器，负责从XML文件和数据库加载配置
 *
 * 功能方法：
 * - getInstance() - 获取单例实例
 * - load(Context) - 加载配置
 * - replaceWith(XcLoggerConfig) - 替换当前配置
 * - getCurrentConfig() - 获取当前配置
 * - current() - 获取当前配置（静态方法）
 * - updateConfig(Context, XcLoggerConfig) - 更新配置并保存到数据库
 * - resetToDefault(Context) - 重置为默认配置
 * - loadFromDatabase(Context) - 从数据库加载配置
 * - loadFromXml(Context) - 从XML文件加载配置
 */
public class ConfigLoader {
    private static final String TAG = "ConfigLoader";

    // 单例相关
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
            // 更新当前配置实例
            this.currentConfig = config;
            Log.i(TAG, "Config updated and saved to database");
            Log.i(TAG, "All components should refresh config from ConfigLoader");
        } catch (Exception e) {
            Log.e(TAG, "Failed to update config", e);
        }
    }

    /**
     * 重置为默认配置
     * @param context Android上下文
     * @return 默认配置对象
     */
    public XcLoggerConfig resetToDefault(Context context) {
        try {
            // 从XML文件加载默认配置
            XcLoggerConfig defaultConfig = loadFromXml(context);

            if (defaultConfig != null) {
                // 保存到数据库
                XcLoggerDatabase db = new XcLoggerDatabase(context);
                db.saveConfig(defaultConfig);

                // 更新当前配置实例
                this.currentConfig = defaultConfig;

                Log.i(TAG, "Config reset to default and saved to database");
                Log.i(TAG, "All components should refresh config from ConfigLoader");
                return defaultConfig;
            } else {
                Log.e(TAG, "Failed to load default config from XML");
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resetting config to default", e);
            return null;
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
            XmlResourceParser parser = context.getResources().getXml(R.xml.default_config);
            XcLoggerConfig config = new XcLoggerConfig();
            boolean inFilteringRules = false;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("filtering_rules".equals(tagName)) {
                        inFilteringRules = true;
                    } else if (inFilteringRules) {
                        // 在filtering_rules内部处理过滤规则
                        switch (tagName) {
                            case "tag":
                                config.setFilterTag(parser.nextText());
                                break;
                            case "level":
                                config.setFilterLevel(parser.nextText());
                                break;
                            case "package_name":
                                config.setFilterPackage(parser.nextText());
                                break;
                        }
                    } else {
                        // 处理其他配置项
                        switch (tagName) {
                            case "total_size":
                                config.setTotalSizeGb(Integer.parseInt(parser.nextText()));
                                break;
                            case "file_size":
                                config.setFileSizeMb(Integer.parseInt(parser.nextText()));
                                break;
                            case "buffer_size":
                                config.setBufferSizeBytes(Integer.parseInt(parser.nextText()));
                                break;
                            case "log_dir":
                                config.setLogDir(parser.nextText());
                                break;
                            case "log_period":
                                config.setLogPeriodHours(Integer.parseInt(parser.nextText()));
                                break;
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    String tagName = parser.getName();
                    if ("filtering_rules".equals(tagName)) {
                        inFilteringRules = false;
                    }
                }
                eventType = parser.next();
            }

            parser.close();
            Log.i(TAG, "Config loaded from XML file");
            return config;
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error parsing XML config file", e);
            return null;
        }
    }
}