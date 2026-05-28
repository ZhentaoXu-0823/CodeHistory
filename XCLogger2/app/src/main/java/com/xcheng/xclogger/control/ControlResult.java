package com.xcheng.xclogger.control;

public class ControlResult {
    private final boolean success;
    private final String message;
    private final String opType;
    private final boolean runningState;
    private final String compressState;
    private final String zipFiles;
    private final int retryCount;
    private final int maxRetryCount;

    public ControlResult(boolean success, String message, String opType, boolean runningState) {
        this(success, message, opType, runningState, "", "", 0, 0);
    }

    public ControlResult(boolean success, String message, String opType, boolean runningState,
                         String compressState, String zipFiles, int retryCount, int maxRetryCount) {
        this.success = success;
        this.message = message;
        this.opType = opType;
        this.runningState = runningState;
        this.compressState = compressState;
        this.zipFiles = zipFiles;
        this.retryCount = retryCount;
        this.maxRetryCount = maxRetryCount;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getOpType() { return opType; }
    public boolean isRunningState() { return runningState; }
    public String getCompressState() { return compressState; }
    public String getZipFiles() { return zipFiles; }
    public int getRetryCount() { return retryCount; }
    public int getMaxRetryCount() { return maxRetryCount; }
}
