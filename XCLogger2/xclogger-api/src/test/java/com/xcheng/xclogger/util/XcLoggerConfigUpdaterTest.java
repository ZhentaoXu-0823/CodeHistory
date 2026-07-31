package com.xcheng.xclogger.util;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class XcLoggerConfigUpdaterTest {
    @Test
    public void chainBuildsOrderedMutationsAndSeals() {
        AtomicReference<XcLoggerConfig2> captured = new AtomicReference<>();
        XcLoggerConfigUpdater.Transport transport = new XcLoggerConfigUpdater.Transport() {
            @Override public int getApiVersion() { return 4; }
            @Override public void submit(XcLoggerConfig2 update,
                                         XcLoggerConfigUpdater.CommitCallback callback) {
                captured.set(update);
                callback.onComplete(new XcLoggerConfigUpdateResult(
                        update.getRequestId(),
                        XcLoggerConfigUpdateResult.SUCCESS, "ok", "filterTag", false));
            }
        };
        XcLoggerConfigUpdater updater = new XcLoggerConfigUpdater(transport, Runnable::run)
                .filterTags("Base")
                .addTag("Added")
                .removeTag("Base")
                .blacklistPackages("com.blocked")
                .addBlacklistedPackage("com.blocked.second")
                .filterLevel(XcLoggerConfigUpdater.LogLevel.WARN);

        AtomicReference<XcLoggerConfigUpdateResult> result = new AtomicReference<>();
        updater.commitAsync(result::set);

        assertNotNull(result.get());
        assertTrue(result.get().isSuccess());
        XcLoggerConfig2 update = captured.get();
        assertNull(captured.get().getPackageFilterMode());
        assertEquals("Added", update.getTagWhitelist().getReplacement().get(0));
        assertEquals(2, update.getPackageBlacklist().getReplacement().size());
        assertEquals("w", update.getFilterLevel());

        boolean sealed = false;
        try { updater.addTag("late"); } catch (IllegalStateException expected) { sealed = true; }
        assertTrue(sealed);
    }

    @Test
    public void emptyUpdaterCompletesWithoutTransport() {
        AtomicReference<XcLoggerConfigUpdateResult> result = new AtomicReference<>();
        XcLoggerConfigUpdater updater = new XcLoggerConfigUpdater(new XcLoggerConfigUpdater.Transport() {
            @Override public int getApiVersion() { throw new AssertionError("must not call transport"); }
            @Override public void submit(XcLoggerConfig2 update,
                                         XcLoggerConfigUpdater.CommitCallback callback) {
                throw new AssertionError("must not submit");
            }
        }, Runnable::run);
        updater.commitAsync(result::set);
        assertEquals(XcLoggerConfigUpdateResult.NO_CHANGES, result.get().getStatus());
    }

    @Test
    public void packageModeUsesFlatConfig2() {
        AtomicReference<XcLoggerConfig2> captured = new AtomicReference<>();
        XcLoggerConfigUpdater.Transport transport = new XcLoggerConfigUpdater.Transport() {
            @Override public int getApiVersion() { return 4; }
            @Override public void submit(XcLoggerConfig2 update,
                                         XcLoggerConfigUpdater.CommitCallback callback) {
                captured.set(update);
                callback.onComplete(new XcLoggerConfigUpdateResult("request",
                        XcLoggerConfigUpdateResult.SUCCESS, "ok", "packageFilterMode", false));
            }
        };

        AtomicReference<XcLoggerConfigUpdateResult> result = new AtomicReference<>();
        new XcLoggerConfigUpdater(transport, Runnable::run)
                .packageFilterMode(XcLoggerConfigUpdater.PackageFilterMode.BLACKLIST)
                .commitAsync(result::set);

        assertTrue(result.get().isSuccess());
        assertEquals("blacklist", captured.get().getPackageFilterMode());
        assertNotNull(captured.get());
    }

    @Test
    public void packageOffRequiresApi4AndUsesSingleModeField() {
        AtomicReference<XcLoggerConfig2> captured = new AtomicReference<>();
        XcLoggerConfigUpdater.Transport transport = new XcLoggerConfigUpdater.Transport() {
            @Override public int getApiVersion() { return 4; }
            @Override public void submit(XcLoggerConfig2 update,
                                         XcLoggerConfigUpdater.CommitCallback callback) {
                captured.set(update);
                callback.onComplete(new XcLoggerConfigUpdateResult("request",
                        XcLoggerConfigUpdateResult.SUCCESS, "ok", "packageFilterMode", false));
            }
        };

        AtomicReference<XcLoggerConfigUpdateResult> result = new AtomicReference<>();
        new XcLoggerConfigUpdater(transport, Runnable::run)
                .packageFilterMode(XcLoggerConfigUpdater.PackageFilterMode.OFF)
                .commitAsync(result::set);

        assertTrue(result.get().isSuccess());
        assertEquals("off", captured.get().getPackageFilterMode());
    }

    @Test
    public void packageOffIsRejectedByApi3Service() {
        XcLoggerConfigUpdater.Transport transport = new XcLoggerConfigUpdater.Transport() {
            @Override public int getApiVersion() { return 3; }
            @Override public void submit(XcLoggerConfig2 update,
                                         XcLoggerConfigUpdater.CommitCallback callback) {
                throw new AssertionError("OFF must not be sent to API 3");
            }
        };

        AtomicReference<XcLoggerConfigUpdateResult> result = new AtomicReference<>();
        new XcLoggerConfigUpdater(transport, Runnable::run)
                .packageFilterMode(XcLoggerConfigUpdater.PackageFilterMode.OFF)
                .commitAsync(result::set);

        assertEquals(XcLoggerConfigUpdateResult.UNSUPPORTED_SERVICE_VERSION,
                result.get().getStatus());
    }
}
