package com.xcheng.xclogger.control;

import com.xcheng.xclogger.util.XcLoggerConfig;

public class ControlRequest {
    private final String channel;
    private final String opType;
    private final String resolvedSource;
    private final XcLoggerConfig configPatch;

    public ControlRequest(String channel, String opType, String resolvedSource, XcLoggerConfig configPatch) {
        this.channel = channel;
        this.opType = opType;
        this.resolvedSource = resolvedSource;
        this.configPatch = configPatch;
    }

    public String getChannel() {
        return channel;
    }

    public String getOpType() {
        return opType;
    }

    public String getResolvedSource() {
        return resolvedSource;
    }

    public XcLoggerConfig getConfigPatch() {
        return configPatch;
    }
}
