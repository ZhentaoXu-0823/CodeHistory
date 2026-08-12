package com.xcheng.xclogger.processctr;

import android.content.Context;
import android.os.Build;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.app.usage.StorageStatsManager;
import android.util.Log;

import com.xcheng.xclogger.filemanager.FileManager;

import java.io.File;

/**
 * LogQuotaPolicy - 日志配额与存储空间保底策略
 *
 * 职责：
 * 1. total_size 门限计算（下限 = 首次约定值，上限 = 设备总存储 x 90%）
 * 2. 设备存储空间探测（总存储 / 可用空间）
 * 3. 绝对保底判断（可用 <= 1GB）与相对保底判断（可用 <= 总存储 10%）
 *
 * 说明：复用 InitialLogSizePolicy 的容量归一化逻辑（8/16/32/64GB -> 512/1024MB），
 * 保持档位表一致（不含 4/6GB）。
 */
public final class LogQuotaPolicy {
    private static final String TAG = "LogQuotaPolicy";

    /** 绝对保底阈值：可用空间 <= 1GB 触发清理 */
    private static final long ABSOLUTE_FLOOR_BYTES = 1024L * 1024L * 1024L;

    /** 相对保底阈值：可用空间 <= 总存储 x 10% 触发清理 */
    private static final long RELATIVE_FLOOR_PERCENT = 10;

    /** total_size 上限占设备总存储的百分比（90%） */
    private static final int MAX_PERCENT = 90;

    /** 空间探测结果缓存时长（新建文件高频调用，避免 StatFs 开销） */
    private static final long STORAGE_CACHE_MS = 30_000L;

    /** 门限计算缓存时长（设置 total_size 时实时探测，缓存 30s 内同值） */
    private static final long BOUNDS_CACHE_MS = 30_000L;

    /** 空间探测结果缓存 */
    private static volatile StorageSnapshot sStorageCache;
    private static volatile long sStorageCacheTime;

    /** 门限缓存 */
    private static volatile Bounds sBoundsCache;
    private static volatile long sBoundsCacheTime;

    private LogQuotaPolicy() {
    }

    /** 存储空间快照 */
    public static final class StorageSnapshot {
        public final long totalBytes;
        public final long availableBytes;
        public final String source;

        StorageSnapshot(long totalBytes, long availableBytes, String source) {
            this.totalBytes = totalBytes;
            this.availableBytes = availableBytes;
            this.source = source;
        }
    }

    /** total_size 门限范围 */
    public static final class Bounds {
        public final int minMb;
        public final int maxMb;
        public final int normalizedCapacityGb;
        public final String source;

        Bounds(int minMb, int maxMb, int normalizedCapacityGb, String source) {
            this.minMb = minMb;
            this.maxMb = maxMb;
            this.normalizedCapacityGb = normalizedCapacityGb;
            this.source = source;
        }

        @Override
        public String toString() {
            return "Bounds{minMb=" + minMb + ", maxMb=" + maxMb
                    + ", capacityGb=" + normalizedCapacityGb + ", source=" + source + "}";
        }
    }

    /**
     * 探测设备存储空间（主存储优先，回退日志目录所在分区）
     */
    public static StorageSnapshot probeStorage(Context context, String logDir) {
        long now = System.currentTimeMillis();
        if (sStorageCache != null && now - sStorageCacheTime < STORAGE_CACHE_MS) {
            return sStorageCache;
        }

        StorageSnapshot snapshot = doProbe(context, logDir);
        sStorageCache = snapshot;
        sStorageCacheTime = now;
        return snapshot;
    }

    private static StorageSnapshot doProbe(Context context, String logDir) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                StorageStatsManager statsManager = context.getSystemService(StorageStatsManager.class);
                if (statsManager != null) {
                    long total = statsManager.getTotalBytes(StorageManager.UUID_DEFAULT);
                    long available = statsManager.getFreeBytes(StorageManager.UUID_DEFAULT);
                    if (total > 0) {
                        return new StorageSnapshot(total, available, "primary_storage");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to read primary storage size, trying log path", e);
            }
        }

        try {
            File probePath = findExistingPath(logDir);
            StatFs statFs = new StatFs(probePath.getAbsolutePath());
            return new StorageSnapshot(statFs.getTotalBytes(), statFs.getAvailableBytes(),
                    "statfs:" + probePath.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve storage size", e);
            return new StorageSnapshot(0, 0, "unavailable");
        }
    }

    /**
     * 计算 total_size 门限：
     * 下限 = 首次约定值（复用 InitialLogSizePolicy 归一化 8/16/32/64 -> 512/1024MB）
     * 上限 = 归一化档位 x 90%（档位按 GB 整数值计算）
     */
    public static Bounds resolveBounds(Context context, String logDir) {
        long now = System.currentTimeMillis();
        if (sBoundsCache != null && now - sBoundsCacheTime < BOUNDS_CACHE_MS) {
            return sBoundsCache;
        }

        StorageSnapshot snapshot = probeStorage(context, logDir);
        Bounds bounds;
        if (snapshot.totalBytes > 0) {
            int normalizedGb = InitialLogSizePolicy.normalizeCapacityGb(snapshot.totalBytes);
            int minMb = InitialLogSizePolicy.quotaForCapacityGb(normalizedGb);
            int maxMb = computeMaxMb(normalizedGb);
            bounds = new Bounds(minMb, maxMb, normalizedGb, snapshot.source);
        } else {
            // 探测失败：下限用固定 fallback 256MB，上限不限制
            bounds = new Bounds(256,
                    Integer.MAX_VALUE, 0, "fallback");
        }

        sBoundsCache = bounds;
        sBoundsCacheTime = now;
        return bounds;
    }

    /**
     * 检查 total_size 是否在门限范围内
     *
     * @return null 表示在范围内；否则返回拒绝原因
     */
    public static String checkTotalSizeBounds(Context context, String logDir, int totalSizeMb) {
        Bounds bounds = resolveBounds(context, logDir);
        if (totalSizeMb < bounds.minMb) {
            return "total_size " + totalSizeMb + "MB below floor " + bounds.minMb
                    + "MB (capacity " + bounds.normalizedCapacityGb + "GB)";
        }
        if (totalSizeMb > bounds.maxMb) {
            return "total_size " + totalSizeMb + "MB above ceiling " + bounds.maxMb
                    + "MB (capacity " + bounds.normalizedCapacityGb + "GB x 90%)";
        }
        return null;
    }

    /**
     * 绝对保底：可用空间 <= 1GB
     */
    public static boolean isAbsoluteFloorLow(Context context, String logDir) {
        StorageSnapshot snapshot = probeStorage(context, logDir);
        return snapshot.availableBytes > 0
                && snapshot.availableBytes <= ABSOLUTE_FLOOR_BYTES;
    }

    /**
     * 相对保底：可用空间 <= 总存储 x 10%
     */
    public static boolean isRelativeFloorLow(Context context, String logDir) {
        StorageSnapshot snapshot = probeStorage(context, logDir);
        if (snapshot.totalBytes <= 0 || snapshot.availableBytes <= 0) {
            return false;
        }
        // 避免溢出：availableBytes * 100 <= totalBytes * 10 改用 double 比较
        double availablePercent = (double) snapshot.availableBytes * 100.0 / snapshot.totalBytes;
        return availablePercent <= RELATIVE_FLOOR_PERCENT;
    }

    private static File findExistingPath(String logDir) {
        String safeLogDir = logDir == null || logDir.trim().isEmpty()
                ? FileManager.DEFAULT_LOG_DIR : logDir;
        File candidate = new File(safeLogDir);
        while (candidate != null && !candidate.exists()) {
            candidate = candidate.getParentFile();
        }
        if (candidate == null) {
            throw new IllegalStateException("No existing parent for log directory: " + safeLogDir);
        }
        return candidate;
    }

    /**
     * 计算 total_size 上限：归一化档位(GB) x 90%，换算为 MB。
     * 纯计算，便于单元测试。
     */
    static int computeMaxMb(int normalizedCapacityGb) {
        return (int) ((normalizedCapacityGb * 1_000_000_000L * MAX_PERCENT)
                / 100L / (1024L * 1024L));
    }

    /** 测试辅助：清空缓存 */
    static void clearCacheForTest() {
        sStorageCache = null;
        sStorageCacheTime = 0;
        sBoundsCache = null;
        sBoundsCacheTime = 0;
    }
}
