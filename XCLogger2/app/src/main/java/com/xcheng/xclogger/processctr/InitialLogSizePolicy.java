package com.xcheng.xclogger.processctr;

import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.util.Log;

import com.xcheng.xclogger.filemanager.FileManager;

import java.io.File;

/** Calculates the persisted log quota during the first configuration initialization. */
public final class InitialLogSizePolicy {
    private static final String TAG = "InitialLogSizePolicy";
    private static final int SDK_ANDROID_13 = 33;
    private static final int SDK_ANDROID_15 = 35;
    private static final long DECIMAL_GB = 1_000_000_000L;
    private static final int FALLBACK_TOTAL_SIZE_MB = 256;

    private InitialLogSizePolicy() {
    }

    public static boolean shouldApplyForSdk(int sdkInt) {
        return sdkInt == SDK_ANDROID_13 || sdkInt == SDK_ANDROID_15;
    }

    public static Decision resolve(Context context, String logDir) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                StorageStatsManager statsManager = context.getSystemService(StorageStatsManager.class);
                if (statsManager == null) {
                    throw new IllegalStateException("StorageStatsManager is unavailable");
                }
                long totalBytes = statsManager.getTotalBytes(StorageManager.UUID_DEFAULT);
                long availableBytes = statsManager.getFreeBytes(StorageManager.UUID_DEFAULT);
                Decision decision = createDecision(totalBytes, availableBytes,
                        "primary_storage", false);
                Log.i(TAG, "Initial log size resolved from primary storage: " + decision);
                return decision;
            } catch (Exception primaryError) {
                Log.w(TAG, "Failed to read primary storage size, trying log path", primaryError);
            }
        }

        try {
            File probePath = findExistingPath(logDir);
            StatFs statFs = new StatFs(probePath.getAbsolutePath());
            long totalBytes = statFs.getTotalBytes();
            long availableBytes = statFs.getAvailableBytes();
            Decision decision = createDecision(totalBytes, availableBytes,
                    "statfs:" + probePath.getAbsolutePath(), false);
            Log.i(TAG, "Initial log size resolved from log path: " + decision);
            return decision;
        } catch (Exception e) {
            Decision fallback = new Decision(FALLBACK_TOTAL_SIZE_MB, 0, 0, 0,
                    "fixed_fallback:" + logDir, true);
            Log.e(TAG, "Failed to resolve storage size, using fallback: " + fallback, e);
            return fallback;
        }
    }

    private static Decision createDecision(long totalBytes, long availableBytes,
                                           String source, boolean fallback) {
        int normalizedCapacityGb = normalizeCapacityGb(totalBytes);
        int totalSizeMb = quotaForCapacityGb(normalizedCapacityGb);
        return new Decision(totalSizeMb, normalizedCapacityGb, totalBytes,
                availableBytes, source, fallback);
    }

    static int normalizeCapacityGb(long totalBytes) {
        if (totalBytes <= 0) {
            throw new IllegalArgumentException("totalBytes must be positive");
        }
        if (totalBytes <= 8 * DECIMAL_GB) return 8;
        if (totalBytes <= 16 * DECIMAL_GB) return 16;
        if (totalBytes <= 32 * DECIMAL_GB) return 32;
        return 64;
    }

    static int quotaForCapacityGb(int normalizedCapacityGb) {
        return normalizedCapacityGb <= 16 ? 512 : 1024;
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

    public static final class Decision {
        private final int totalSizeMb;
        private final int normalizedCapacityGb;
        private final long storageTotalBytes;
        private final long storageAvailableBytes;
        private final String source;
        private final boolean fallback;

        private Decision(int totalSizeMb, int normalizedCapacityGb, long storageTotalBytes,
                         long storageAvailableBytes, String source, boolean fallback) {
            this.totalSizeMb = totalSizeMb;
            this.normalizedCapacityGb = normalizedCapacityGb;
            this.storageTotalBytes = storageTotalBytes;
            this.storageAvailableBytes = storageAvailableBytes;
            this.source = source;
            this.fallback = fallback;
        }

        public int getTotalSizeMb() {
            return totalSizeMb;
        }

        @Override
        public String toString() {
            return "sdk=" + Build.VERSION.SDK_INT
                    + ", release=" + Build.VERSION.RELEASE
                    + ", source=" + source
                    + ", storageTotalBytes=" + storageTotalBytes
                    + ", storageAvailableBytes=" + storageAvailableBytes
                    + ", normalizedCapacityGb=" + normalizedCapacityGb
                    + ", totalSizeMb=" + totalSizeMb
                    + ", fallback=" + fallback;
        }
    }
}
