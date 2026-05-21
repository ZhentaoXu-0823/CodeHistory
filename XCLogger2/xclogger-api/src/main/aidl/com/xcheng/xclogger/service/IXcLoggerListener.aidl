package com.xcheng.xclogger.service;

interface IXcLoggerListener {
    // 日志状态变化通知 (0: Stopped, 1: Running)
    void onStatusChanged(int status);

    // 统一操作结果回调
    void onOperationResult(String opType, boolean success, String message, boolean runningState);

    // 压缩任务完成通知
    void onCompressFinished(boolean success, String message);
}
