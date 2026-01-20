package com.xcheng.xclogger.service;

interface IXcLoggerListener {
    // 日志状态变化通知 (0: Stopped, 1: Running)
    void onStatusChanged(int status);
    // 压缩任务完成通知
    void onCompressFinished(boolean success, String path);
}