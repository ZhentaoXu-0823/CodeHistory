package com.xcheng.xclogger.service;

interface IXcLoggerListener {
    // 日志状态变化通知 (0: Stopped, 1: Running)
    void onStatusChanged(int status);

    /**
     * 压缩任务完成通知
     * @param result 结果代码 (0: 成功, 1: 失败, 2: 异常)
     * @param message 详细描述信息
     */
    void onCompressFinished(int result, String message);
}