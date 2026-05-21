package com.xcheng.xclogger.control;

public class ControlResult {
    private final boolean success;
    private final String message;
    private final String opType;
    private final boolean runningState;

    public ControlResult(boolean success, String message, String opType, boolean runningState) {
        this.success = success;
        this.message = message;
        this.opType = opType;
        this.runningState = runningState;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getOpType() {
        return opType;
    }

    public boolean isRunningState() {
        return runningState;
    }
}
