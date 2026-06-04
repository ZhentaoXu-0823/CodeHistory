package com.xcheng.xclogger.processctr;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.Log;
import com.xcheng.xclogger.R;
import com.xcheng.xclogger.filemanager.FileManager;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;

/**
 * ConfigLoader - 配置加载器，负责从XML文件和数据库加载配置
 */
public class ConfigLoader {
    private static final String TAG = "ConfigLoader";

    private static ConfigLoader instance;
    private XcLoggerConfig currentConfig;
    private boolean lastInitialAutoStartEnabled;
    private boolean lastLoadInitializedFromXml;

    private ConfigLoader() {
    }

    public static synchronized ConfigLoader getInstance() {
        if (instance == null) {
            instance = new ConfigLoader();
        }
        return instance;
    }

    public static XcLoggerConfig current() {
        if (instance != null) {
            return instance.getCurrentConfig();
        }
        return null;
    }

    public XcLoggerConfig load(Context context) {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(context);
            lastLoadInitializedFromXml = false;

            if (db.isConfigInitialized() || db.hasSavedConfig()) {
                XcLoggerConfig config = loadFromDatabase(context);
                if (config != null) {
                    if (!db.isConfigInitialized()) {
                        db.setConfigInitialized(true);
                    }
                    currentConfig = config;
                    lastInitialAutoStartEnabled = db.loadInitialAutoStartEnabled();
                    Log.i(TAG, "Config loaded from database");
                    return config;
                }
            }

            ConfigXmlResult result = loadFromXml(context);
            if (result != null && result.config != null) {
                // 无论 DB 写入是否成功，先用 XML 中的值设置状态标记和当前配置
                lastInitialAutoStartEnabled = result.initialAutoStartEnabled;
                lastLoadInitializedFromXml = true;
                currentConfig = result.config;

                if (!db.saveInitialConfig(result.config, result.initialAutoStartEnabled)) {
                    Log.e(TAG, "Failed to commit initial config, using XML config directly");
                } else {
                    Log.i(TAG, "Config loaded from XML and saved to database");
                    try {
                        FileManager fm = new FileManager(context);
                        fm.appendOperationHistory("Config initialized from XML and saved to database (first-time load, initial_auto_start=" + result.initialAutoStartEnabled + ")");
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to record operation history for initial XML load", e);
                    }
                }
                return result.config;
            }

            Log.e(TAG, "Failed to load config from both database and XML");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error loading config", e);
            return null;
        }
    }

    public void replaceWith(XcLoggerConfig config) {
        this.currentConfig = config;
        Log.i(TAG, "Config replaced with new configuration");
    }

    public XcLoggerConfig getCurrentConfig() {
        return currentConfig;
    }

    public boolean wasLastLoadInitializedFromXml() {
        return lastLoadInitializedFromXml;
    }

    public boolean getLastInitialAutoStartEnabled() {
        return lastInitialAutoStartEnabled;
    }

    public void updateConfig(Context context, XcLoggerConfig config) {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(context);
            db.saveConfig(config);
            this.currentConfig = config;
            Log.i(TAG, "Config updated and saved to database");
            Log.i(TAG, "All components should refresh config from ConfigLoader");
            try {
                FileManager fm = new FileManager(context);
                fm.appendOperationHistory("Config updated via ConfigLoader.updateConfig (source:ConfigLoader)");
            } catch (Exception e) {
                Log.w(TAG, "Failed to record operation history for updateConfig", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update config", e);
        }
    }

    public XcLoggerConfig resetToDefault(Context context) {
        try {
            ConfigXmlResult result = loadFromXml(context);
            XcLoggerConfig defaultConfig = result != null ? result.config : null;

            if (defaultConfig != null) {
                XcLoggerDatabase db = new XcLoggerDatabase(context);
                if (!db.saveInitialConfig(defaultConfig, result.initialAutoStartEnabled)) {
                    Log.e(TAG, "Failed to commit reset config");
                    return null;
                }

                this.currentConfig = defaultConfig;
                this.lastInitialAutoStartEnabled = result.initialAutoStartEnabled;
                this.lastLoadInitializedFromXml = false;

                Log.i(TAG, "Config reset to default and saved to database");
                Log.i(TAG, "All components should refresh config from ConfigLoader");
                try {
                    FileManager fm = new FileManager(context);
                    fm.appendOperationHistory("Config reset to default (source:ConfigLoader.resetToDefault)");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to record operation history for resetToDefault", e);
                }
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

    private XcLoggerConfig loadFromDatabase(Context context) {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(context);
            return db.loadConfig();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load config from database", e);
            return null;
        }
    }

    private ConfigXmlResult loadFromXml(Context context) {
        XmlResourceParser parser = null;
        try {
            parser = context.getResources().getXml(R.xml.default_config);
            XcLoggerConfig config = new XcLoggerConfig();
            boolean initialAutoStartEnabled = false;
            boolean inFilteringRules = false;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("filtering_rules".equals(tagName)) {
                        inFilteringRules = true;
                    } else if (inFilteringRules) {
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
                            case "auto_start_enabled":
                                initialAutoStartEnabled = Boolean.parseBoolean(parser.nextText());
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

            Log.i(TAG, "Config loaded from XML file");
            return new ConfigXmlResult(config, initialAutoStartEnabled);
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error parsing XML config file", e);
            return null;
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    private static class ConfigXmlResult {
        final XcLoggerConfig config;
        final boolean initialAutoStartEnabled;

        ConfigXmlResult(XcLoggerConfig config, boolean initialAutoStartEnabled) {
            this.config = config;
            this.initialAutoStartEnabled = initialAutoStartEnabled;
        }
    }
}
