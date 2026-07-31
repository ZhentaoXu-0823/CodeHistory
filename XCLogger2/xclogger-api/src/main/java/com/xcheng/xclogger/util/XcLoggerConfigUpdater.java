package com.xcheng.xclogger.util;

import android.os.Looper;

import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Fluent, single-use builder for atomic XCLogger configuration updates. */
public final class XcLoggerConfigUpdater {
    public static final int REQUIRED_API_VERSION = 4;
    private static final int MAX_LIST_ITEMS = 64;
    private static final long BLOCKING_TIMEOUT_SECONDS = 15;
    private static final Pattern TAG_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*\\.?");

    public interface CommitCallback {
        void onComplete(XcLoggerConfigUpdateResult result);
    }

    public interface Transport {
        int getApiVersion();
        void submit(XcLoggerConfig2 update, CommitCallback callback);
    }

    public enum LogLevel {
        ALL("all"), FATAL("f"), ERROR("e"), WARN("w"), INFO("i"), DEBUG("d"), VERBOSE("v");
        private final String wireValue;
        LogLevel(String wireValue) { this.wireValue = wireValue; }
        public String wireValue() { return wireValue; }
    }

    public enum PackageFilterMode {
        OFF(XcLoggerConfig2.PACKAGE_FILTER_MODE_OFF),
        WHITELIST(XcLoggerConfig2.PACKAGE_FILTER_MODE_WHITELIST),
        BLACKLIST(XcLoggerConfig2.PACKAGE_FILTER_MODE_BLACKLIST);
        private final String wireValue;
        PackageFilterMode(String wireValue) { this.wireValue = wireValue; }
        public String wireValue() { return wireValue; }
    }

    private final Transport transport;
    private final Executor callbackExecutor;
    private final AtomicBoolean sealed = new AtomicBoolean(false);
    private final String requestId = UUID.randomUUID().toString();
    private long presentFields;
    private int totalSizeMb;
    private int fileSizeMb;
    private int bufferSizeBytes;
    private String logDir;
    private int logPeriodHours;
    private String filterLevel;
    private String packageFilterMode;
    private final MutableListMutation tagWhitelist = new MutableListMutation(true, ValueType.TAG);
    private final MutableListMutation packageWhitelist = new MutableListMutation(true, ValueType.PACKAGE);
    private final MutableListMutation packageBlacklist = new MutableListMutation(false, ValueType.PACKAGE);

    public XcLoggerConfigUpdater(Transport transport, Executor callbackExecutor) {
        if (transport == null) throw new IllegalArgumentException("transport is null");
        this.transport = transport;
        this.callbackExecutor = callbackExecutor != null ? callbackExecutor : Runnable::run;
    }

    public XcLoggerConfigUpdater totalSizeMb(int value) {
        checkMutable();
        if (value <= 0) throw new IllegalArgumentException("totalSizeMb must be > 0");
        totalSizeMb = value;
        presentFields |= XcLoggerConfigUpdate.FIELD_TOTAL_SIZE_MB;
        return this;
    }

    public XcLoggerConfigUpdater fileSizeMb(int value) {
        checkMutable();
        if (value <= 0) throw new IllegalArgumentException("fileSizeMb must be > 0");
        fileSizeMb = value;
        presentFields |= XcLoggerConfigUpdate.FIELD_FILE_SIZE_MB;
        return this;
    }

    public XcLoggerConfigUpdater bufferSizeBytes(int value) {
        checkMutable();
        if (value < 512 || value > 4096 || value % 512 != 0) {
            throw new IllegalArgumentException("bufferSizeBytes must be 512..4096 and aligned to 512");
        }
        bufferSizeBytes = value;
        presentFields |= XcLoggerConfigUpdate.FIELD_BUFFER_SIZE_BYTES;
        return this;
    }

    public XcLoggerConfigUpdater logDir(String value) {
        checkMutable();
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("logDir is empty");
        logDir = value.trim();
        presentFields |= XcLoggerConfigUpdate.FIELD_LOG_DIR;
        return this;
    }

    public XcLoggerConfigUpdater logPeriodHours(int value) {
        checkMutable();
        if (value <= 0) throw new IllegalArgumentException("logPeriodHours must be > 0");
        logPeriodHours = value;
        presentFields |= XcLoggerConfigUpdate.FIELD_LOG_PERIOD_HOURS;
        return this;
    }

    public XcLoggerConfigUpdater filterLevel(LogLevel value) {
        if (value == null) throw new IllegalArgumentException("filterLevel is null");
        return filterLevel(value.wireValue());
    }

    public XcLoggerConfigUpdater filterLevel(String value) {
        checkMutable();
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (!("all".equals(normalized) || normalized.matches("[fewidv]"))) {
            throw new IllegalArgumentException("filterLevel must be all/F/E/W/I/D/V");
        }
        filterLevel = normalized;
        presentFields |= XcLoggerConfigUpdate.FIELD_FILTER_LEVEL;
        return this;
    }

    public XcLoggerConfigUpdater packageFilterMode(PackageFilterMode value) {
        if (value == null) throw new IllegalArgumentException("packageFilterMode is null");
        return packageFilterMode(value.wireValue());
    }

    public XcLoggerConfigUpdater packageFilterMode(String value) {
        checkMutable();
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (!XcLoggerConfig2.PACKAGE_FILTER_MODE_OFF.equals(normalized)
                && !XcLoggerConfig2.PACKAGE_FILTER_MODE_WHITELIST.equals(normalized)
                && !XcLoggerConfig2.PACKAGE_FILTER_MODE_BLACKLIST.equals(normalized)) {
            throw new IllegalArgumentException(
                    "packageFilterMode must be off, whitelist, or blacklist");
        }
        packageFilterMode = normalized;
        return this;
    }

    public XcLoggerConfigUpdater filterTags(String... values) { return filterTags(Arrays.asList(values)); }
    public XcLoggerConfigUpdater filterTags(Collection<String> values) { checkMutable(); tagWhitelist.replace(values); return this; }
    public XcLoggerConfigUpdater filterTagsCsv(String value) { return filterTags(parseCsv(value)); }
    public XcLoggerConfigUpdater addTag(String value) { checkMutable(); tagWhitelist.add(value); return this; }
    public XcLoggerConfigUpdater removeTag(String value) { checkMutable(); tagWhitelist.remove(value); return this; }

    public XcLoggerConfigUpdater filterPackages(String... values) { return filterPackages(Arrays.asList(values)); }
    public XcLoggerConfigUpdater filterPackages(Collection<String> values) { checkMutable(); packageWhitelist.replace(values); return this; }
    public XcLoggerConfigUpdater filterPackagesCsv(String value) { return filterPackages(parseCsv(value)); }
    public XcLoggerConfigUpdater allPackages() { return filterPackages("all"); }
    public XcLoggerConfigUpdater addPackage(String value) { checkMutable(); packageWhitelist.add(value); return this; }
    public XcLoggerConfigUpdater removePackage(String value) { checkMutable(); packageWhitelist.remove(value); return this; }

    public XcLoggerConfigUpdater blacklistPackages(String... values) { return blacklistPackages(Arrays.asList(values)); }
    public XcLoggerConfigUpdater blacklistPackages(Collection<String> values) { checkMutable(); packageBlacklist.replace(values); return this; }
    public XcLoggerConfigUpdater blacklistPackagesCsv(String value) { return blacklistPackages(parseCsv(value)); }
    public XcLoggerConfigUpdater addBlacklistedPackage(String value) { checkMutable(); packageBlacklist.add(value); return this; }
    public XcLoggerConfigUpdater removeBlacklistedPackage(String value) { checkMutable(); packageBlacklist.remove(value); return this; }
    public XcLoggerConfigUpdater clearPackageBlacklist() { return blacklistPackages(new ArrayList<>()); }

    public String commitAsync(CommitCallback callback) {
        submit(callback);
        return requestId;
    }

    @WorkerThread
    public XcLoggerConfigUpdateResult commit() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("commit() must not run on the main thread; use commitAsync()");
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<XcLoggerConfigUpdateResult> resultRef = new AtomicReference<>();
        submit(result -> {
            resultRef.set(result);
            latch.countDown();
        });
        try {
            if (!latch.await(BLOCKING_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return result(XcLoggerConfigUpdateResult.TIMEOUT_PENDING,
                        "Timed out; request may still complete", "", false);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return result(XcLoggerConfigUpdateResult.TIMEOUT_PENDING,
                    "Interrupted; request may still complete", "", false);
        }
        XcLoggerConfigUpdateResult result = resultRef.get();
        return result != null ? result : result(XcLoggerConfigUpdateResult.INTERNAL_ERROR,
                "Missing callback result", "", false);
    }

    private void submit(CommitCallback callback) {
        if (!sealed.compareAndSet(false, true)) throw new IllegalStateException("updater is already committed");
        validateCrossFields();
        XcLoggerConfig2 update = buildUpdate();
        AtomicBoolean delivered = new AtomicBoolean(false);
        CommitCallback once = value -> {
            if (!delivered.compareAndSet(false, true)) return;
            XcLoggerConfigUpdateResult safe = value != null ? value
                    : result(XcLoggerConfigUpdateResult.INTERNAL_ERROR, "Null result", "", false);
            callbackExecutor.execute(() -> { if (callback != null) callback.onComplete(safe); });
        };
        if (!update.hasChanges()) {
            once.onComplete(result(XcLoggerConfigUpdateResult.NO_CHANGES, "No changes", "", false));
            return;
        }
        try {
            int apiVersion = transport.getApiVersion();
            if (apiVersion < 0) {
                once.onComplete(result(XcLoggerConfigUpdateResult.NOT_BOUND,
                        "XCLogger service is not bound", "", false));
                return;
            }
            if (apiVersion < REQUIRED_API_VERSION) {
                once.onComplete(result(XcLoggerConfigUpdateResult.UNSUPPORTED_SERVICE_VERSION,
                        "XCLogger service does not support updateConfiguration2", "", false));
                return;
            }
            transport.submit(update, once);
        } catch (RuntimeException e) {
            once.onComplete(result(XcLoggerConfigUpdateResult.REMOTE_ERROR,
                    e.getMessage() == null ? "Transport failed" : e.getMessage(), "", false));
        }
    }

    private XcLoggerConfig2 buildUpdate() {
        XcLoggerConfig2 update = new XcLoggerConfig2();
        update.setRequestId(requestId);
        update.setPresentFields(presentFields);
        update.setTotalSizeMb(totalSizeMb);
        update.setFileSizeMb(fileSizeMb);
        update.setBufferSizeBytes(bufferSizeBytes);
        update.setLogDir(logDir);
        update.setLogPeriodHours(logPeriodHours);
        update.setFilterLevel(filterLevel);
        update.setTagWhitelist(tagWhitelist.build());
        update.setPackageWhitelist(packageWhitelist.build());
        update.setPackageBlacklist(packageBlacklist.build());
        update.setPackageFilterMode(packageFilterMode);
        return update;
    }

    private void validateCrossFields() {
        if ((presentFields & XcLoggerConfigUpdate.FIELD_TOTAL_SIZE_MB) != 0
                && (presentFields & XcLoggerConfigUpdate.FIELD_FILE_SIZE_MB) != 0
                && fileSizeMb > totalSizeMb) {
            throw new IllegalArgumentException("fileSizeMb must not exceed totalSizeMb");
        }
    }

    private XcLoggerConfigUpdateResult result(int status, String message,
                                               String changedFields, boolean restarted) {
        return new XcLoggerConfigUpdateResult(requestId, status, message, changedFields, restarted);
    }

    private void checkMutable() {
        if (sealed.get()) throw new IllegalStateException("updater is already committed");
    }

    private static List<String> parseCsv(String value) {
        if (value == null || value.trim().isEmpty()) return new ArrayList<>();
        return Arrays.asList(value.split(",", -1));
    }

    private enum ValueType { TAG, PACKAGE }

    private static final class MutableListMutation {
        private final boolean whitelist;
        private final ValueType type;
        private boolean replace;
        private final LinkedHashSet<String> replacement = new LinkedHashSet<>();
        private final LinkedHashSet<String> additions = new LinkedHashSet<>();
        private final LinkedHashSet<String> removals = new LinkedHashSet<>();

        MutableListMutation(boolean whitelist, ValueType type) {
            this.whitelist = whitelist;
            this.type = type;
        }

        void replace(Collection<String> values) {
            replace = true;
            replacement.clear();
            additions.clear();
            removals.clear();
            if (values != null) for (String value : values) replacement.add(normalize(value));
            normalizeReplacement();
        }

        void add(String value) {
            String normalized = normalize(value);
            if (whitelist && "all".equals(normalized)) {
                replace(Arrays.asList("all"));
                return;
            }
            if (replace) {
                if (whitelist && replacement.contains("all")) replacement.clear();
                replacement.add(normalized);
            } else {
                removals.remove(normalized);
                additions.add(normalized);
            }
            normalizeReplacement();
        }

        void remove(String value) {
            String normalized = normalize(value);
            if (whitelist && "all".equals(normalized)) return;
            if (replace) {
                replacement.remove(normalized);
            } else {
                additions.remove(normalized);
                removals.add(normalized);
            }
            normalizeReplacement();
        }

        private String normalize(String value) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException(type + " value is empty");
            if (whitelist && "all".equals(normalized)) return normalized;
            Pattern pattern = type == ValueType.TAG ? TAG_PATTERN : PACKAGE_PATTERN;
            if (!pattern.matcher(normalized).matches()) {
                throw new IllegalArgumentException("Invalid " + type.name().toLowerCase(Locale.US) + ": " + normalized);
            }
            return normalized;
        }

        private void normalizeReplacement() {
            if (replacement.size() > MAX_LIST_ITEMS) throw new IllegalArgumentException("Too many list values");
            if (whitelist) {
                if (replacement.isEmpty()) replacement.add("all");
                if (replacement.contains("all") && replacement.size() > 1) {
                    throw new IllegalArgumentException("all must be the only whitelist value");
                }
            }
        }

        XcLoggerConfigUpdate.ListMutation build() {
            if (!replace && additions.isEmpty() && removals.isEmpty()) return null;
            return new XcLoggerConfigUpdate.ListMutation(replace, new ArrayList<>(replacement),
                    new ArrayList<>(additions), new ArrayList<>(removals));
        }
    }
}
