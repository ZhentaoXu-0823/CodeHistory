package com.xcheng.xclogger.control;

public class SourceWhitelistGuard {
    private static final String SOURCE_ADB = "adb";
    private static final String SOURCE_TEST_DEMO = "com.xcheng.xcloggertestdemo";

    public boolean isAllowed(String source) {
        if (source == null) {
            return false;
        }
        return SOURCE_ADB.equals(source) || SOURCE_TEST_DEMO.equals(source);
    }
}
