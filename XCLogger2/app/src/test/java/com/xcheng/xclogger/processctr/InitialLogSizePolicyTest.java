package com.xcheng.xclogger.processctr;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InitialLogSizePolicyTest {
    private static final long GB = 1_000_000_000L;

    @Test
    public void adaptivePolicyOnlyAppliesToAndroid13And15() {
        assertFalse(InitialLogSizePolicy.shouldApplyForSdk(32));
        assertTrue(InitialLogSizePolicy.shouldApplyForSdk(33));
        assertFalse(InitialLogSizePolicy.shouldApplyForSdk(34));
        assertTrue(InitialLogSizePolicy.shouldApplyForSdk(35));
        assertFalse(InitialLogSizePolicy.shouldApplyForSdk(36));
    }

    @Test
    public void capacityIsNormalizedToFourSupportedStandards() {
        assertEquals(8, InitialLogSizePolicy.normalizeCapacityGb(6 * GB));
        assertEquals(8, InitialLogSizePolicy.normalizeCapacityGb(8_000_000_000L));
        assertEquals(16, InitialLogSizePolicy.normalizeCapacityGb(8_000_000_001L));
        assertEquals(16, InitialLogSizePolicy.normalizeCapacityGb(16_000_000_000L));
        assertEquals(32, InitialLogSizePolicy.normalizeCapacityGb(19_972_194_304L));
        assertEquals(32, InitialLogSizePolicy.normalizeCapacityGb(32_000_000_000L));
        assertEquals(64, InitialLogSizePolicy.normalizeCapacityGb(32_000_000_001L));
        assertEquals(64, InitialLogSizePolicy.normalizeCapacityGb(100 * GB));
    }

    @Test
    public void quotaMatchesNormalizedCapacity() {
        assertEquals(512, InitialLogSizePolicy.quotaForCapacityGb(8));
        assertEquals(512, InitialLogSizePolicy.quotaForCapacityGb(16));
        assertEquals(1024, InitialLogSizePolicy.quotaForCapacityGb(32));
        assertEquals(1024, InitialLogSizePolicy.quotaForCapacityGb(64));
    }
}
