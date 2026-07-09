package com.xcheng.xclogger.recorder;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * White-list + Black-list filter pipeline.
 *
 * CHECK 0: CRITICAL — always pass
 * CHECK 1-3: WHITELIST (converge scope)
 * CHECK 4-7: BLACKLIST (precise exclusion)
 *
 * Equals old behavior when all new fields are empty/default.
 */
public final class FilterPipeline {

    /** Crash-related tags — always pass, bypass all filtering. Hardcoded, not configurable. */
    public static final Set<String> CRITICAL_TAGS = Set.of(
        "AndroidRuntime",   // Java crash (LOG_ID_CRASH, RuntimeInit.java)
        "DEBUG",            // Native crash header (tombstone.cpp)
        "libc"              // Native crash fallback (debuggerd fallback handler)
    );

    /** Priority map: lower number = higher severity */
    static final Map<String, Integer> LEVEL_PRIORITY = Map.of(
        "f", 0, "e", 1, "w", 2, "i", 3, "d", 4, "v", 5
    );

    /** Sentinel value meaning "level filter not active" */
    public static final int LEVEL_ALL = Integer.MAX_VALUE;

    /**
     * Decide whether a log line should be kept.
     *
     * @param tag      log tag (e.g. "WifiHAL")
     * @param priority 0=F .. 5=V (from LEVEL_PRIORITY), or 99 if unknown
     * @param uid      Android UID
     * @param message  log message body
     * @param state    current filter configuration snapshot (non-null)
     * @return true = keep, false = drop
     */
    public static boolean shouldAccept(
            String tag, int priority, int uid, String message,
            FilterState state) {

        // === CHECK 0: CRITICAL — unconditional pass ===
        if (CRITICAL_TAGS.contains(tag)) {
            return true;
        }

        // === WHITELIST (converge first) ===

        // CHECK 1: PACKAGE / UID WHITELIST
        if (!state.uidWhitelist.isEmpty() && !state.uidWhitelist.contains(uid)) {
            return false;
        }

        // CHECK 2: LEVEL WHITELIST
        // Only active when there is NO tag whitelist (tag=all).
        // When tag whitelist IS active, level is already enforced at logcat layer via FILTERSPEC.
        if (!state.levelIsAll && priority > state.minLevel) {
            return false;
        }

        // CHECK 3: CONTENT WHITELIST
        if (!state.contentWhitelist.isEmpty()) {
            boolean anyMatch = false;
            for (String kw : state.contentWhitelist) {
                if (message.contains(kw)) {
                    anyMatch = true;
                    break;
                }
            }
            if (!anyMatch) {
                return false;
            }
        }

        // === BLACKLIST (exclude from whitelist results) ===

        // CHECK 4: TAG BLACKLIST
        if (!state.tagBlacklist.isEmpty() && state.tagBlacklist.contains(tag)) {
            return false;
        }

        // CHECK 5: PACKAGE / UID BLACKLIST
        if (!state.uidBlacklist.isEmpty() && state.uidBlacklist.contains(uid)) {
            return false;
        }

        // CHECK 6: LEVEL BLACKLIST
        if (!state.levelBlacklist.isEmpty() && state.levelBlacklist.contains(priority)) {
            return false;
        }

        // CHECK 7: CONTENT BLACKLIST
        if (!state.contentBlacklist.isEmpty()) {
            for (String kw : state.contentBlacklist) {
                if (message.contains(kw)) {
                    return false;
                }
            }
        }

        return true;
    }

    // ──────────────────────────────────────────────
    // Filter state snapshot (mutable sets by reference)
    // ──────────────────────────────────────────────

    public static class FilterState {
        /** true = tag filter is "all" → skip tag whitelist check */
        public boolean tagIsAll = true;

        /** true = level filter is "all" OR already enforced at logcat layer */
        public boolean levelIsAll = true;

        /** Minimum priority (0-5), meaningful only when levelIsAll=false */
        public int minLevel = LEVEL_ALL;

        // Whitelist sets (empty = skip)
        public Set<Integer> uidWhitelist = Collections.emptySet();
        public Set<String>  contentWhitelist = Collections.emptySet();

        // Blacklist sets (empty = skip)
        public Set<String>  tagBlacklist = Collections.emptySet();
        public Set<Integer> uidBlacklist = Collections.emptySet();
        public Set<Integer> levelBlacklist = Collections.emptySet();
        public Set<String>  contentBlacklist = Collections.emptySet();
    }
}
