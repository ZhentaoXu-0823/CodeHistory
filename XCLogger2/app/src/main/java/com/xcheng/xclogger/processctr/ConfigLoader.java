package com.xcheng.xclogger.processctr;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.Log;
import com.xcheng.xclogger.R;
import com.xcheng.xclogger.filemanager.FileManager;
import com.xcheng.xclogger.control.PartialConfigMerger;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * ConfigLoader - 配置加载器，负责从XML文件和数据库加载配置
 */
public class ConfigLoader {
    private static final String TAG = "ConfigLoader";
    private static final int APK_CONFIG_VERSION = 2;

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

            // 版本陈旧检查：DB 版本 < APK 版本时，用 XML 全量刷新
            if (db.getDatabaseVersion() < APK_CONFIG_VERSION) {
                XcLoggerConfig oldConfig = loadFromDatabase(context);
                ConfigXmlResult result = loadFromXml(context);
                if (result != null && result.config != null) {
                    lastInitialAutoStartEnabled = result.initialAutoStartEnabled;
                    lastLoadInitializedFromXml = true;
                    currentConfig = result.config;

                    if (db.saveInitialConfig(result.config, result.initialAutoStartEnabled)) {
                        db.setDatabaseVersion(APK_CONFIG_VERSION);
                        String diff = buildConfigDiff(oldConfig, result.config);
                        Log.i(TAG, "Config upgraded from v" + (APK_CONFIG_VERSION - 1)
                                + " to v" + APK_CONFIG_VERSION
                                + ": " + diff);
                        try {
                            FileManager fm = new FileManager(context);
                            fm.appendOperationHistory("Config upgraded from v" + (APK_CONFIG_VERSION - 1)
                                    + " to v" + APK_CONFIG_VERSION
                                    + " (" + diff + ")");
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to record operation history for config upgrade", e);
                        }
                    } else {
                        Log.e(TAG, "Failed to save config during version upgrade");
                    }
                    return result.config;
                }
                // XML 解析失败 => fallthrough 到 DB 兜底，不阻塞启动
                Log.w(TAG, "XML parse failed during version upgrade, falling back to DB");
            }

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
            XcLoggerConfig oldConfig = this.currentConfig;
            XcLoggerDatabase db = new XcLoggerDatabase(context);
            db.saveConfig(config);
            this.currentConfig = config;
            Log.i(TAG, "Config updated and saved to database");
            Log.i(TAG, "All components should refresh config from ConfigLoader");
            try {
                FileManager fm = new FileManager(context);
                String diff = buildConfigDiff(oldConfig, config);
                fm.appendConfigChangeHistory(diff);
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

                // 恢复出厂默认后，同步将 DB 版本对齐到当前 APK 版本
                db.setDatabaseVersion(APK_CONFIG_VERSION);

                XcLoggerConfig oldConfig = this.currentConfig;
                this.currentConfig = defaultConfig;
                this.lastInitialAutoStartEnabled = result.initialAutoStartEnabled;
                this.lastLoadInitializedFromXml = false;

                Log.i(TAG, "Config reset to default and saved to database");
                Log.i(TAG, "All components should refresh config from ConfigLoader");
                try {
                    FileManager fm = new FileManager(context);
                    String diff = buildConfigDiff(oldConfig, defaultConfig);
                    fm.appendConfigChangeHistory("reset to default (" + diff + ")");
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

    /**
     * Parse XcLoggerConfig fields from an XmlPullParser (shared by loadFromXml and loadFromXmlFile).
     * If outAutoStart is non-null, also captures auto_start_enabled value.
     */
    private static XcLoggerConfig parseConfigFromParser(XmlPullParser parser, boolean[] outAutoStart)
            throws XmlPullParserException, IOException {
        XcLoggerConfig config = new XcLoggerConfig();
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
                            config.setTotalSizeMb(Integer.parseInt(parser.nextText()));
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
                        case "tag_blacklist":
                            config.setFilterTagBlacklist(parser.nextText());
                            break;
                        case "package_blacklist":
                            config.setFilterPackageBlacklist(parser.nextText());
                            break;
                        case "level_blacklist":
                            config.setFilterLevelBlacklist(parser.nextText());
                            break;
                        case "content":
                            config.setFilterContent(parser.nextText());
                            break;
                        case "content_blacklist":
                            config.setFilterContentBlacklist(parser.nextText());
                            break;
                        default:
                            if (outAutoStart != null && "auto_start_enabled".equals(tagName)) {
                                outAutoStart[0] = Boolean.parseBoolean(parser.nextText());
                            }
                            break;
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if ("filtering_rules".equals(parser.getName())) {
                    inFilteringRules = false;
                }
            }
            eventType = parser.next();
        }

        return config;
    }

    private ConfigXmlResult loadFromXml(Context context) {
        XmlResourceParser parser = null;
        try {
            // Dynamically select config XML based on build flavor
            String configName = context.getString(R.string.flavor_config);
            int resId = context.getResources().getIdentifier(configName, "xml", context.getPackageName());
            if (resId == 0) {
                resId = R.xml.default_config; // fallback
            }
            parser = context.getResources().getXml(resId);
            boolean[] autoStartHolder = new boolean[1];
            XcLoggerConfig config = parseConfigFromParser(parser, autoStartHolder);
            Log.i(TAG, "Config loaded from XML file");
            return new ConfigXmlResult(config, autoStartHolder[0]);
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error parsing XML config file", e);
            return null;
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    private String buildConfigDiff(XcLoggerConfig old, XcLoggerConfig newCfg) {
        if (old == null || newCfg == null) return "unknown";
        StringBuilder sb = new StringBuilder();
        appendDiff(sb, "total_size", old.getTotalSizeMb(), newCfg.getTotalSizeMb());
        appendDiff(sb, "file_size", old.getFileSizeMb(), newCfg.getFileSizeMb());
        appendDiff(sb, "buffer_size", old.getBufferSizeBytes(), newCfg.getBufferSizeBytes());
        appendDiff(sb, "log_dir", old.getLogDir(), newCfg.getLogDir());
        appendDiff(sb, "log_period", old.getLogPeriodHours(), newCfg.getLogPeriodHours());
        appendDiff(sb, "filter_tag", old.getFilterTag(), newCfg.getFilterTag());
        appendDiff(sb, "filter_level", old.getFilterLevel(), newCfg.getFilterLevel());
        appendDiff(sb, "filter_package", old.getFilterPackage(), newCfg.getFilterPackage());
        return sb.length() == 0 ? "no effective changes" : sb.toString();
    }

    private void appendDiff(StringBuilder sb, String name, int oldV, int newV) {
        if (oldV != newV) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(name).append(": ").append(oldV).append(" -> ").append(newV);
        }
    }

    private void appendDiff(StringBuilder sb, String name, String oldV, String newV) {
        if (oldV == null ? newV != null : !oldV.equals(newV)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(name).append(": ").append(oldV).append(" -> ").append(newV);
        }
    }

    /**
     * loadFromXmlFile - 从磁盘文件解析 XML 配置
     * 缺省标签保持 Java 默认值（int=0, String=null），
     * 由 PartialConfigMerger 决定是否覆盖当前值
     * @param file XML 配置文件
     * @return 解析后的 XcLoggerConfig，失败返回 null
     */
    public XcLoggerConfig loadFromXmlFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(fis, "UTF-8");

            XcLoggerConfig config = parseConfigFromParser(parser, null);

            Log.i(TAG, "Config loaded from XML file: " + file.getAbsolutePath());
            return config;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load config from XML file: " + file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * importFromXmlFile - 从文件导入配置，合并后写入 DB 并记录变更
     *
     * 只修改 DB 中的配置字段（不影响 database_version），
     * 不干扰 APK OTA (APK_CONFIG_VERSION) 或 AIDL updateConfigurationPartial 路径。
     *
     * @param context  Android Context
     * @param filePath XML 配置文件路径
     * @return true 导入成功（含无变更场景），false 失败
     */
    public boolean importFromXmlFile(Context context, String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                Log.e(TAG, "Config file not found: " + filePath);
                return false;
            }

            XcLoggerConfig patch = loadFromXmlFile(file);
            if (patch == null) {
                Log.e(TAG, "Failed to parse config file: " + filePath);
                return false;
            }

            XcLoggerConfig current = this.currentConfig;
            if (current == null) {
                current = load(context);
            }
            if (current == null) {
                Log.e(TAG, "Cannot import config: no current config available");
                return false;
            }

            // 合并：缺省字段保持当前值
            XcLoggerConfig merged = new PartialConfigMerger().merge(current, patch);

            // 构建差异日志
            String diff = buildConfigDiff(current, merged);
            Log.i(TAG, "Import config diff: " + diff);

            // 记录操作历史
            try {
                FileManager fm = new FileManager(context);
                fm.appendOperationHistory("Config import from file: " + filePath + " (" + diff + ")");
            } catch (Exception ignored) {
            }

            // 无有效变更则跳过重启
            if ("no effective changes".equals(diff)) {
                // 仍尝试删除文件（若存在则清理）
                if (file.exists()) file.delete();
                Log.i(TAG, "Import config: no effective changes, skipping restart");
                return true;
            }

            // stop → update → start（与 AIDL updateConfigurationPartial 相同模式）
            XcLoggerDatabase db = new XcLoggerDatabase(context);
            boolean wasRunning = db.loadRunningState();
            if (wasRunning) {
                LogServiceController.stopLogService(context, "import_config");
            }

            updateConfig(context, merged);

            if (wasRunning) {
                LogServiceController.startLogService(context, "import_config");
            }

            // 关键操作全部成功后删除配置文件，防止重复导入
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Failed to delete config file after import: " + filePath);
            }

            Log.i(TAG, "Config imported from file: " + filePath + " (" + diff + ")");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to import config from file: " + filePath, e);
            try {
                new FileManager(context).appendOperationHistory("Config import failed: " + filePath + " - " + e.getMessage());
            } catch (Exception ignored) {
            }
            return false;
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
