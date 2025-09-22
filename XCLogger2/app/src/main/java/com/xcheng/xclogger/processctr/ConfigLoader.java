package com.xcheng.xclogger.processctr;

import android.content.Context;
import android.content.res.XmlResourceParser;

import com.xcheng.xclogger.R;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

/**
 * ConfigLoader - 配置加载器，从XML和数据库加载配置
 *
 * 功能方法：
 * - load(Context) - 加载配置（优先数据库，回退XML）
 * - replaceWith(XcLoggerConfig) - 替换当前配置实例
 * - current() - 获取当前配置实例
 * - parseXml(Context) - 解析XML配置文件
 * - parseFilterBlock(XmlResourceParser, XcLoggerConfig) - 解析过滤规则配置
 */
public class ConfigLoader {
    private static volatile XcLoggerConfig CURRENT;

    /**
     * 加载配置（优先数据库，回退XML）
     * @param ctx Android上下文
     * @return 配置对象
     */
    public XcLoggerConfig load(Context ctx) {
        XcLoggerDatabase db = new XcLoggerDatabase(ctx);
        XcLoggerConfig config = db.loadConfig();

        if (config == null) {
            config = parseXml(ctx);
            if (config != null) {
                db.saveConfig(config);
            }
        }

        CURRENT = config;
        return CURRENT;
    }

    /**
     * 替换当前配置实例
     * @param cfg 新的配置对象
     */
    public static void replaceWith(XcLoggerConfig cfg) {
        CURRENT = cfg;
    }

    /**
     * 获取当前配置实例
     * @return 当前配置对象
     */
    public static XcLoggerConfig current() {
        return CURRENT;
    }

    /**
     * 解析XML配置文件
     * @param ctx Android上下文
     * @return 解析后的配置对象
     */
    private XcLoggerConfig parseXml(Context ctx) {
        try {
            XmlResourceParser parser = ctx.getResources().getXml(R.xml.default_config);
            XcLoggerConfig config = new XcLoggerConfig();

            int eventType = parser.getEventType();
            while (eventType != XmlResourceParser.END_DOCUMENT) {
                if (eventType == XmlResourceParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("total_size".equals(tagName)) {
                        config.setTotalSizeGb(Integer.parseInt(parser.nextText()));
                    } else if ("file_size".equals(tagName)) {
                        config.setFileSizeMb(Integer.parseInt(parser.nextText()));
                    } else if ("buffer_size".equals(tagName)) {
                        config.setBufferSizeBytes(Integer.parseInt(parser.nextText()));
                    } else if ("log_dir".equals(tagName)) {
                        config.setLogDir(parser.nextText());
                    } else if ("log_period".equals(tagName)) {
                        config.setLogPeriodHours(Integer.parseInt(parser.nextText()));
                    } else if ("filtering_rules".equals(tagName)) {
                        parseFilterBlock(parser, config);
                    }
                }
                eventType = parser.next();
            }
            parser.close();
            return config;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析过滤规则配置
     * @param parser XML解析器
     * @param config 配置对象
     */
    private void parseFilterBlock(XmlResourceParser parser, XcLoggerConfig config) {
        try {
            int eventType = parser.getEventType();
            while (eventType != XmlResourceParser.END_DOCUMENT) {
                if (eventType == XmlResourceParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("tag".equals(tagName)) {
                        config.setFilterTag(parser.nextText());
                    } else if ("level".equals(tagName)) {
                        config.setFilterLevel(parser.nextText());
                    } else if ("package_name".equals(tagName)) {
                        config.setFilterPackage(parser.nextText());
                    }
                } else if (eventType == XmlResourceParser.END_TAG && "filtering_rules".equals(parser.getName())) {
                    break;
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            // 使用默认值
            config.setFilterTag("all");
            config.setFilterLevel("all");
            config.setFilterPackage("all");
        }
    }
}