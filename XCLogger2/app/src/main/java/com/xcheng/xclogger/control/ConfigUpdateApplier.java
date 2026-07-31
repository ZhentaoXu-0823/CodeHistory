package com.xcheng.xclogger.control;

import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerConfigUpdate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

final class ConfigUpdateApplier {
    private static final int MAX_LIST_ITEMS = 64;
    private static final Pattern TAG_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*\\.?");

    ApplyResult apply(XcLoggerConfig current, XcLoggerConfigUpdate update) {
        return apply(current, update, null);
    }

    ApplyResult apply(XcLoggerConfig current, XcLoggerConfigUpdate update,
                      String packageFilterMode) {
        if (current == null) throw new IllegalArgumentException("current configuration is null");
        if (update == null) throw new IllegalArgumentException("configuration update is null");
        XcLoggerConfig merged = copy(current);

        if (update.hasField(XcLoggerConfigUpdate.FIELD_TOTAL_SIZE_MB)) merged.setTotalSizeMb(update.getTotalSizeMb());
        if (update.hasField(XcLoggerConfigUpdate.FIELD_FILE_SIZE_MB)) merged.setFileSizeMb(update.getFileSizeMb());
        if (update.hasField(XcLoggerConfigUpdate.FIELD_BUFFER_SIZE_BYTES)) merged.setBufferSizeBytes(update.getBufferSizeBytes());
        if (update.hasField(XcLoggerConfigUpdate.FIELD_LOG_DIR)) merged.setLogDir(update.getLogDir());
        if (update.hasField(XcLoggerConfigUpdate.FIELD_LOG_PERIOD_HOURS)) merged.setLogPeriodHours(update.getLogPeriodHours());
        if (update.hasField(XcLoggerConfigUpdate.FIELD_FILTER_LEVEL)) merged.setFilterLevel(normalizeLevel(update.getFilterLevel()));
        if (packageFilterMode != null) merged.setPackageFilterMode(normalizePackageFilterMode(packageFilterMode));

        if (update.getTagWhitelist() != null) {
            merged.setFilterTag(applyList(current.getFilterTag(), update.getTagWhitelist(), true, TAG_PATTERN));
        }
        if (update.getPackageWhitelist() != null) {
            merged.setFilterPackage(applyList(current.getFilterPackage(), update.getPackageWhitelist(), true, PACKAGE_PATTERN));
        }
        if (update.getPackageBlacklist() != null) {
            merged.setFilterPackageBlacklist(applyList(current.getFilterPackageBlacklist(), update.getPackageBlacklist(), false, PACKAGE_PATTERN));
        }

        validateComplete(merged);
        return new ApplyResult(merged, changedFields(current, merged));
    }

    private String applyList(String currentValue, XcLoggerConfigUpdate.ListMutation mutation,
                             boolean whitelist, Pattern pattern) {
        if (!mutation.hasOperations()) return currentValue;
        LinkedHashSet<String> values = mutation.isReplace()
                ? parseValues(mutation.getReplacement(), whitelist, pattern)
                : parseCsv(currentValue, whitelist, pattern);
        boolean wasAll = whitelist && values.contains("all");

        for (String raw : mutation.getRemovals()) {
            String value = validateItem(raw, false, pattern);
            if (!wasAll) values.remove(value);
        }
        for (String raw : mutation.getAdditions()) {
            String value = validateItem(raw, whitelist, pattern);
            if (whitelist && "all".equals(value)) {
                values.clear();
                values.add("all");
            } else {
                if (whitelist && values.contains("all")) values.clear();
                values.add(value);
            }
        }

        if (values.size() > MAX_LIST_ITEMS) throw new IllegalArgumentException("too many list values");
        if (whitelist && values.isEmpty()) values.add("all");
        if (whitelist && values.contains("all") && values.size() > 1) {
            throw new IllegalArgumentException("all must be the only whitelist value");
        }
        return String.join(",", values);
    }

    private LinkedHashSet<String> parseCsv(String value, boolean whitelist, Pattern pattern) {
        if (value == null || value.trim().isEmpty()) {
            LinkedHashSet<String> empty = new LinkedHashSet<>();
            if (whitelist) empty.add("all");
            return empty;
        }
        return parseValues(Arrays.asList(value.split(",", -1)), whitelist, pattern);
    }

    private LinkedHashSet<String> parseValues(List<String> source, boolean whitelist, Pattern pattern) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (source != null) {
            for (String raw : source) values.add(validateItem(raw, whitelist, pattern));
        }
        if (values.size() > MAX_LIST_ITEMS) throw new IllegalArgumentException("too many list values");
        if (whitelist && values.isEmpty()) values.add("all");
        if (whitelist && values.contains("all") && values.size() > 1) {
            throw new IllegalArgumentException("all must be the only whitelist value");
        }
        return values;
    }

    private String validateItem(String raw, boolean allowAll, Pattern pattern) {
        String value = raw == null ? "" : raw.trim();
        if (allowAll && "all".equals(value)) return value;
        if (value.isEmpty() || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid list value");
        }
        return value;
    }

    private String normalizeLevel(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (!("all".equals(normalized) || normalized.matches("[fewidv]"))) {
            throw new IllegalArgumentException("invalid filterLevel");
        }
        return normalized;
    }

    private String normalizePackageFilterMode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (!XcLoggerConfig.PACKAGE_FILTER_MODE_OFF.equals(normalized)
                && !XcLoggerConfig.PACKAGE_FILTER_MODE_WHITELIST.equals(normalized)
                && !XcLoggerConfig.PACKAGE_FILTER_MODE_BLACKLIST.equals(normalized)) {
            throw new IllegalArgumentException("invalid packageFilterMode");
        }
        return normalized;
    }

    private void validateComplete(XcLoggerConfig value) {
        if (value.getTotalSizeMb() <= 0) throw new IllegalArgumentException("totalSizeMb must be > 0");
        if (value.getFileSizeMb() <= 0 || value.getFileSizeMb() > value.getTotalSizeMb()) {
            throw new IllegalArgumentException("fileSizeMb must be > 0 and <= totalSizeMb");
        }
        int buffer = value.getBufferSizeBytes();
        if (buffer < 512 || buffer > 4096 || buffer % 512 != 0) {
            throw new IllegalArgumentException("bufferSizeBytes must be 512..4096 and aligned to 512");
        }
        if (value.getLogDir() == null || value.getLogDir().trim().isEmpty()) {
            throw new IllegalArgumentException("logDir is empty");
        }
        if (value.getLogPeriodHours() <= 0) throw new IllegalArgumentException("logPeriodHours must be > 0");
        FilterConfigValidator.validate(value);
    }

    private XcLoggerConfig copy(XcLoggerConfig source) {
        XcLoggerConfig copy = new XcLoggerConfig();
        copy.setTotalSizeMb(source.getTotalSizeMb());
        copy.setFileSizeMb(source.getFileSizeMb());
        copy.setBufferSizeBytes(source.getBufferSizeBytes());
        copy.setLogDir(source.getLogDir());
        copy.setLogPeriodHours(source.getLogPeriodHours());
        copy.setFilterTag(source.getFilterTag());
        copy.setFilterLevel(source.getFilterLevel());
        copy.setFilterPackage(source.getFilterPackage());
        copy.setFilterTagBlacklist(source.getFilterTagBlacklist());
        copy.setFilterPackageBlacklist(source.getFilterPackageBlacklist());
        copy.setFilterLevelBlacklist(source.getFilterLevelBlacklist());
        copy.setFilterContent(source.getFilterContent());
        copy.setFilterContentBlacklist(source.getFilterContentBlacklist());
        copy.setPackageFilterMode(source.getPackageFilterMode());
        return copy;
    }

    private List<String> changedFields(XcLoggerConfig oldValue, XcLoggerConfig newValue) {
        List<String> changed = new ArrayList<>();
        if (oldValue.getTotalSizeMb() != newValue.getTotalSizeMb()) changed.add("totalSizeMb");
        if (oldValue.getFileSizeMb() != newValue.getFileSizeMb()) changed.add("fileSizeMb");
        if (oldValue.getBufferSizeBytes() != newValue.getBufferSizeBytes()) changed.add("bufferSizeBytes");
        if (!Objects.equals(oldValue.getLogDir(), newValue.getLogDir())) changed.add("logDir");
        if (oldValue.getLogPeriodHours() != newValue.getLogPeriodHours()) changed.add("logPeriodHours");
        if (!Objects.equals(oldValue.getFilterTag(), newValue.getFilterTag())) changed.add("filterTag");
        if (!Objects.equals(oldValue.getFilterLevel(), newValue.getFilterLevel())) changed.add("filterLevel");
        if (!Objects.equals(oldValue.getFilterPackage(), newValue.getFilterPackage())) changed.add("filterPackage");
        if (!Objects.equals(oldValue.getFilterPackageBlacklist(), newValue.getFilterPackageBlacklist())) changed.add("filterPackageBlacklist");
        if (!Objects.equals(oldValue.getPackageFilterMode(), newValue.getPackageFilterMode())) changed.add("packageFilterMode");
        return changed;
    }

    static final class ApplyResult {
        final XcLoggerConfig config;
        final List<String> changedFields;
        ApplyResult(XcLoggerConfig config, List<String> changedFields) {
            this.config = config;
            this.changedFields = changedFields;
        }
    }
}
