package com.xcheng.xclogger.util;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class XcLoggerConfigUpdaterTest {
    @Test
    public void chainBuildsOrderedMutationsAndSeals() {
        AtomicReference<XcLoggerConfigUpdate> captured = new AtomicReference<>();
        XcLoggerConfigUpdater.Transport transport = new XcLoggerConfigUpdater.Transport() {
            @Override public int getApiVersion() { return 2; }
            @Override public void submit(XcLoggerConfigUpdate update, XcLoggerConfigUpdater.CommitCallback callback) {
                captured.set(update);
                callback.onComplete(new XcLoggerConfigUpdateResult(update.getRequestId(),
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
        assertEquals("Added", captured.get().getTagWhitelist().getReplacement().get(0));
        assertEquals(2, captured.get().getPackageBlacklist().getReplacement().size());
        assertEquals("w", captured.get().getFilterLevel());

        boolean sealed = false;
        try { updater.addTag("late"); } catch (IllegalStateException expected) { sealed = true; }
        assertTrue(sealed);
    }

    @Test
    public void emptyUpdaterCompletesWithoutTransport() {
        AtomicReference<XcLoggerConfigUpdateResult> result = new AtomicReference<>();
        XcLoggerConfigUpdater updater = new XcLoggerConfigUpdater(new XcLoggerConfigUpdater.Transport() {
            @Override public int getApiVersion() { throw new AssertionError("must not call transport"); }
            @Override public void submit(XcLoggerConfigUpdate update, XcLoggerConfigUpdater.CommitCallback callback) {
                throw new AssertionError("must not submit");
            }
        }, Runnable::run);
        updater.commitAsync(result::set);
        assertEquals(XcLoggerConfigUpdateResult.NO_CHANGES, result.get().getStatus());
    }

    @Test
    public void packageModeUsesApi3Envelope() {
        AtomicReference<XcLoggerConfigUpdateV3> captured = new AtomicReference<>();
        XcLoggerConfigUpdater.V3Transport transport = new XcLoggerConfigUpdater.V3Transport() {
            @Override public int getApiVersion() { return 3; }
            @Override public void submit(XcLoggerConfigUpdate update,
                                         XcLoggerConfigUpdater.CommitCallback callback) {
                throw new AssertionError("mode update must use V3");
            }
            @Override public void submitV3(XcLoggerConfigUpdateV3 update,
                                           XcLoggerConfigUpdater.CommitCallback callback) {
                captured.set(update);
                callback.onComplete(new XcLoggerConfigUpdateResult("request",
                        XcLoggerConfigUpdateResult.SUCCESS, "ok", "packageFilterMode", false));
            }
        };

        AtomicReference<XcLoggerConfigUpdateResult> result = new AtomicReference<>();
        new XcLoggerConfigUpdater(transport, Runnable::run)
                .usePackageBlacklist()
                .commitAsync(result::set);

        assertTrue(result.get().isSuccess());
        assertEquals("blacklist", captured.get().getPackageFilterMode());
        assertNotNull(captured.get().getBaseUpdate());
    }
}
