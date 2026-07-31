package com.xcheng.xclogger.control;

import com.xcheng.xclogger.util.XcLoggerConfig;

import java.util.regex.Pattern;

/** Validates externally supplied filter values before they reach logcat. */
public final class FilterConfigValidator {
    private static final int MAX_LIST_LENGTH = 4096;
    private static final int MAX_LIST_ITEMS = 64;
    private static final Pattern TAG_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*\\.?");
    private static final Pattern LEVEL_PATTERN = Pattern.compile("[FEWIDVfewidv]");

    private FilterConfigValidator() {
    }

    public static void validate(XcLoggerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("configuration is null");
        }
        validateTag(config.getFilterTag());
        validateLevel(config.getFilterLevel());
        validatePackage(config.getFilterPackage());
        validatePackageBlacklist(config.getFilterPackageBlacklist());
        validatePackageFilterMode(config.getPackageFilterMode());
    }

    public static void validateTag(String value) {
        validateList(value, "filterTag", TAG_PATTERN, true);
    }

    public static void validatePackage(String value) {
        validateList(value, "filterPackage", PACKAGE_PATTERN, false);
    }

    public static void validatePackageBlacklist(String value) {
        validateOptionalList(value, "filterPackageBlacklist", PACKAGE_PATTERN);
    }

    public static void validateLevel(String value) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if ("all".equals(normalized) || LEVEL_PATTERN.matcher(normalized).matches()) {
            return;
        }
        throw new IllegalArgumentException("invalid filterLevel");
    }

    public static void validatePackageFilterMode(String value) {
        if (!XcLoggerConfig.PACKAGE_FILTER_MODE_OFF.equals(value)
                && !XcLoggerConfig.PACKAGE_FILTER_MODE_WHITELIST.equals(value)
                && !XcLoggerConfig.PACKAGE_FILTER_MODE_BLACKLIST.equals(value)) {
            throw new IllegalArgumentException("invalid packageFilterMode");
        }
    }

    private static void validateList(String value, String fieldName, Pattern itemPattern, boolean allowAll) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (allowAll && "all".equals(normalized)) {
            return;
        }
        if (normalized.isEmpty() || normalized.length() > MAX_LIST_LENGTH) {
            throw new IllegalArgumentException("invalid " + fieldName);
        }

        String[] items = normalized.split(",", -1);
        if (items.length == 0 || items.length > MAX_LIST_ITEMS) {
            throw new IllegalArgumentException("invalid " + fieldName);
        }
        for (String item : items) {
            String trimmed = item.trim();
            if (!item.equals(trimmed) || !itemPattern.matcher(trimmed).matches()) {
                throw new IllegalArgumentException("invalid " + fieldName);
            }
        }
    }

    private static void validateOptionalList(String value, String fieldName, Pattern itemPattern) {
        if (value == null || value.trim().isEmpty()) return;
        validateList(value, fieldName, itemPattern, false);
    }
}
