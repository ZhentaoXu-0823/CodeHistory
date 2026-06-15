package com.xcheng.xclogger.control;

import com.xcheng.xclogger.util.XcLoggerConfig;

public class ControlRequest {
    private final String channel;
    private final String opType;
    private final String resolvedSource;
    private final XcLoggerConfig configPatch;
    private final boolean uploadSuccess;
    private final String startTime;
    private final String endTime;

    public ControlRequest(String channel, String opType, String resolvedSource, XcLoggerConfig configPatch) {
        this(channel, opType, resolvedSource, configPatch, false, null, null);
    }

    public ControlRequest(String channel, String opType, String resolvedSource, XcLoggerConfig configPatch, boolean uploadSuccess) {
        this(channel, opType, resolvedSource, configPatch, uploadSuccess, null, null);
    }

    public ControlRequest(String channel, String opType, String resolvedSource, XcLoggerConfig configPatch, boolean uploadSuccess, String startTime, String endTime) {
        this.channel = channel;
        this.opType = opType;
        this.resolvedSource = resolvedSource;
        this.configPatch = configPatch;
        this.uploadSuccess = uploadSuccess;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getChannel() { return channel; }
    public String getOpType() { return opType; }
    public String getResolvedSource() { return resolvedSource; }
    public XcLoggerConfig getConfigPatch() { return configPatch; }
    public boolean isUploadSuccess() { return uploadSuccess; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public boolean hasTimeRange() { return (startTime != null && !startTime.isEmpty()) || (endTime != null && !endTime.isEmpty()); }
}
