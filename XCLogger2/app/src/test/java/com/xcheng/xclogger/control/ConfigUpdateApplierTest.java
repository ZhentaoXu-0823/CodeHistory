package com.xcheng.xclogger.control;

import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerConfigUpdate;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class ConfigUpdateApplierTest {
    private final ConfigUpdateApplier applier = new ConfigUpdateApplier();

    @Test
    public void sequentialAddsUseLatestServerConfiguration() {
        XcLoggerConfig current = baseConfig();
        XcLoggerConfigUpdate addA = updateWithTagAdd("A");
        XcLoggerConfig afterA = applier.apply(current, addA).config;
        XcLoggerConfig afterB = applier.apply(afterA, updateWithTagAdd("B")).config;
        assertEquals("A,B", afterB.getFilterTag());
    }

    @Test
    public void removingLastWhitelistValueRestoresAll() {
        XcLoggerConfig current = baseConfig();
        current.setFilterPackage("com.demo");
        XcLoggerConfigUpdate update = new XcLoggerConfigUpdate();
        update.setPackageWhitelist(new XcLoggerConfigUpdate.ListMutation(false,
                Collections.emptyList(), Collections.emptyList(), Collections.singletonList("com.demo")));
        assertEquals("all", applier.apply(current, update).config.getFilterPackage());
    }

    @Test
    public void packageFilterModeIsAppliedIndependentlyFromBothLists() {
        XcLoggerConfig current = baseConfig();
        current.setFilterPackage("com.allowed");
        current.setFilterPackageBlacklist("com.blocked");
        XcLoggerConfigUpdate emptyUpdate = new XcLoggerConfigUpdate();

        XcLoggerConfig result = applier.apply(current, emptyUpdate, "blacklist").config;

        assertEquals("blacklist", result.getPackageFilterMode());
        assertEquals("com.allowed", result.getFilterPackage());
        assertEquals("com.blocked", result.getFilterPackageBlacklist());
    }

    @Test
    public void packageFilterOffPreservesBothLists() {
        XcLoggerConfig current = baseConfig();
        current.setFilterPackage("com.allowed");
        current.setFilterPackageBlacklist("com.blocked");

        XcLoggerConfig result = applier.apply(
                current, new XcLoggerConfigUpdate(), "off").config;

        assertEquals("off", result.getPackageFilterMode());
        assertEquals("com.allowed", result.getFilterPackage());
        assertEquals("com.blocked", result.getFilterPackageBlacklist());
    }

    private XcLoggerConfigUpdate updateWithTagAdd(String tag) {
        XcLoggerConfigUpdate update = new XcLoggerConfigUpdate();
        update.setTagWhitelist(new XcLoggerConfigUpdate.ListMutation(false,
                Collections.emptyList(), Collections.singletonList(tag), Collections.emptyList()));
        return update;
    }

    private XcLoggerConfig baseConfig() {
        XcLoggerConfig config = new XcLoggerConfig();
        config.setTotalSizeMb(1024);
        config.setFileSizeMb(4);
        config.setBufferSizeBytes(1024);
        config.setLogDir("/storage/emulated/0/XcLogger");
        config.setLogPeriodHours(168);
        config.setFilterTag("all");
        config.setFilterLevel("all");
        config.setFilterPackage("all");
        config.setFilterTagBlacklist("");
        config.setFilterPackageBlacklist("");
        config.setFilterLevelBlacklist("");
        config.setFilterContent("");
        config.setFilterContentBlacklist("");
        config.setPackageFilterMode("whitelist");
        return config;
    }
}
