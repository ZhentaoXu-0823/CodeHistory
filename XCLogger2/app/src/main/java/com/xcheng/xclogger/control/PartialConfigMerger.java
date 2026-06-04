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
        merged.setTotalSizeGb(patch.getTotalSizeGb() > 0 ? patch.getTotalSizeGb() : current.getTotalSizeGb());
        merged.setFileSizeMb(patch.getFileSizeMb() > 0 ? patch.getFileSizeMb() : current.getFileSizeMb());
        merged.setBufferSizeBytes(patch.getBufferSizeBytes() > 0 ? patch.getBufferSizeBytes() : current.getBufferSizeBytes());
        merged.setLogDir(isNotEmpty(patch.getLogDir()) ? patch.getLogDir() : current.getLogDir());
        merged.setLogPeriodHours(patch.getLogPeriodHours() > 0 ? patch.getLogPeriodHours() : current.getLogPeriodHours());
        merged.setFilterTag(isNotEmpty(patch.getFilterTag()) ? patch.getFilterTag() : current.getFilterTag());
        merged.setFilterLevel(isNotEmpty(patch.getFilterLevel()) ? patch.getFilterLevel() : current.getFilterLevel());
        merged.setFilterPackage(isNotEmpty(patch.getFilterPackage()) ? patch.getFilterPackage() : current.getFilterPackage());
        return merged;
    }

    private boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
