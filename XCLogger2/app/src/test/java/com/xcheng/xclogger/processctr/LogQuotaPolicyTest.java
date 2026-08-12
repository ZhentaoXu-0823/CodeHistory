package com.xcheng.xclogger.processctr;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * LogQuotaPolicy 纯计算部分单元测试（不依赖 Android Context）。
 * resolveBounds / checkTotalSizeBounds / isAbsoluteFloorLow / isRelativeFloorLow
 * 依赖 Context 存储探测，属 instrumented 场景，此处只测可纯计算的公式。
 */
public class LogQuotaPolicyTest {

    @Test
    public void maxMbIsNinetyPercentOfNormalizedCapacity() {
        // 8GB 档：8 x 90% = 7.2GB = 7_200_000_000 bytes / 1048576 = 6866 MB
        assertEquals(6866, LogQuotaPolicy.computeMaxMb(8));
        // 16GB 档：16 x 90% = 14.4GB = 14_400_000_000 / 1048576 = 13732 MB
        assertEquals(13732, LogQuotaPolicy.computeMaxMb(16));
        // 32GB 档：32 x 90% = 28.8GB = 28_800_000_000 / 1048576 = 27465 MB
        assertEquals(27465, LogQuotaPolicy.computeMaxMb(32));
        // 64GB 档：64 x 90% = 57.6GB = 57_600_000_000 / 1048576 = 54931 MB
        assertEquals(54931, LogQuotaPolicy.computeMaxMb(64));
    }

    @Test
    public void maxMbGrowsMonotonicallyWithCapacity() {
        assertTrue(LogQuotaPolicy.computeMaxMb(16) > LogQuotaPolicy.computeMaxMb(8));
        assertTrue(LogQuotaPolicy.computeMaxMb(32) > LogQuotaPolicy.computeMaxMb(16));
        assertTrue(LogQuotaPolicy.computeMaxMb(64) > LogQuotaPolicy.computeMaxMb(32));
    }

    @Test
    public void boundsRelationMatchesFirstRunQuota() {
        // 下限(quotaForCapacityGb) 恒小于 上限(computeMaxMb)
        for (int gb : new int[]{8, 16, 32, 64}) {
            int minMb = InitialLogSizePolicy.quotaForCapacityGb(gb);
            int maxMb = LogQuotaPolicy.computeMaxMb(gb);
            assertTrue("min should be < max for " + gb + "GB", minMb < maxMb);
        }
    }
}
