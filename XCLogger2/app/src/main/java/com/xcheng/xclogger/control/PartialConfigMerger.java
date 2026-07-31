package com.xcheng.xclogger.control;

import com.xcheng.xclogger.util.XcLoggerConfig;

public class PartialConfigMerger {
    public XcLoggerConfig merge(XcLoggerConfig current, XcLoggerConfig patch) {
        if (current == null) {
            current = new XcLoggerConfig();
        }
        if (patch == null) {
            return current;
        }

        XcLoggerConfig merged = new XcLoggerConfig();
        merged.setTotalSizeMb(patch.getTotalSizeMb() > 0 ? patch.getTotalSizeMb() : current.getTotalSizeMb());
        merged.setFileSizeMb(patch.getFileSizeMb() > 0 ? patch.getFileSizeMb() : current.getFileSizeMb());
        merged.setBufferSizeBytes(validateBufferSize(patch.getBufferSizeBytes() > 0 ? patch.getBufferSizeBytes() : current.getBufferSizeBytes()));
        merged.setLogDir(isNotEmpty(patch.getLogDir()) ? patch.getLogDir() : current.getLogDir());
        merged.setLogPeriodHours(patch.getLogPeriodHours() > 0 ? patch.getLogPeriodHours() : current.getLogPeriodHours());
        merged.setFilterTag(isNotEmpty(patch.getFilterTag()) ? patch.getFilterTag() : current.getFilterTag());
        merged.setFilterLevel(isNotEmpty(patch.getFilterLevel()) ? patch.getFilterLevel() : current.getFilterLevel());
        merged.setFilterPackage(isNotEmpty(patch.getFilterPackage()) ? patch.getFilterPackage() : current.getFilterPackage());
        merged.setFilterTagBlacklist(current.getFilterTagBlacklist());
        merged.setFilterPackageBlacklist(isNotEmpty(patch.getFilterPackageBlacklist()) ? patch.getFilterPackageBlacklist() : current.getFilterPackageBlacklist());
        merged.setPackageFilterMode(current.getPackageFilterMode());
        merged.setFilterLevelBlacklist(current.getFilterLevelBlacklist());
        merged.setFilterContent(current.getFilterContent());
        merged.setFilterContentBlacklist(current.getFilterContentBlacklist());
        return merged;
    }

    private int validateBufferSize(int size) {
        if (size <= 0) return 512;
        // 对齐到 512 的倍数
        int aligned = ((size + 511) / 512) * 512;
        // 限制最大 4096
        return Math.min(aligned, 4096);
    }

    private boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
