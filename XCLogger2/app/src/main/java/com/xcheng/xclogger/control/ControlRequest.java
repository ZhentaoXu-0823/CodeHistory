package com.xcheng.xclogger.control;

import com.xcheng.xclogger.util.XcLoggerConfig;

public class ControlRequest {
    private final String channel;
    private final String opType;
    private final String resolvedSource;
    private final XcLoggerConfig configPatch;
    private final boolean uploadSuccess;

    public ControlRequest(String channel, String opType, String resolvedSource, XcLoggerConfig configPatch) {
        this(channel, opType, resolvedSource, configPatch, false);
    }

    public ControlRequest(String channel, String opType, String resolvedSource, XcLoggerConfig configPatch, boolean uploadSuccess) {
        this.channel = channel;
        this.opType = opType;
        this.resolvedSource = resolvedSource;
        this.configPatch = configPatch;
        this.uploadSuccess = uploadSuccess;
    }

    public String getChannel() { return channel; }
    public String getOpType() { return opType; }
    public String getResolvedSource() { return resolvedSource; }
    public XcLoggerConfig getConfigPatch() { return configPatch; }
    public boolean isUploadSuccess() { return uploadSuccess; }
}
