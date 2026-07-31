package com.xcheng.xclogger.recorder;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class SystemLogCatcherParserTest {

    @Test
    public void parseThreadtimeUid_extractsUidPidTidLevelAndTag() {
        String line = "07-30 07:54:24.893 10125 7092 7258 D XCStressTag1: "
                + "package=com.xcheng.xcloggertestdemo instance=main";

        SystemLogCatcher.LogLineInfo info = SystemLogCatcher.parseLogLine(line);

        assertEquals(10125, info.uid);
        assertEquals(7092, info.pid);
        assertEquals(7258, info.tid);
        assertEquals("D", info.level);
        assertEquals("XCStressTag1", info.tag);
    }

    @Test
    public void parseThreadtimeUid_handlesAlignedTagSeparator() {
        String line = "07-30 07:47:53.584 10124 9854 9854 W libc    : message";

        SystemLogCatcher.LogLineInfo info = SystemLogCatcher.parseLogLine(line);

        assertEquals(10124, info.uid);
        assertEquals(9854, info.pid);
        assertEquals(9854, info.tid);
        assertEquals("W", info.level);
        assertEquals("libc", info.tag);
    }

    @Test
    public void parseBufferHeader_returnsUnknownIdentity() {
        SystemLogCatcher.LogLineInfo info =
                SystemLogCatcher.parseLogLine("--------- beginning of main");

        assertEquals(-1, info.uid);
        assertEquals(-1, info.pid);
        assertEquals(-1, info.tid);
    }

    @Test
    public void matchesIdentity_acceptsStableUidAcrossPidChanges() {
        assertEquals(true, SystemLogCatcher.matchesIdentity(
                Collections.singleton(10125), Collections.emptySet(), 10125, 9999));
    }

    @Test
    public void matchesIdentity_acceptsFallbackPidWhenUidIsUnavailable() {
        assertEquals(true, SystemLogCatcher.matchesIdentity(
                Collections.emptySet(), Collections.singleton(7092), -1, 7092));
    }

    @Test
    public void matchesIdentity_rejectsUnresolvedIdentity() {
        assertEquals(false, SystemLogCatcher.matchesIdentity(
                new HashSet<>(Collections.singletonList(10125)),
                new HashSet<>(Collections.singletonList(7092)), 10126, 7141));
    }
}
