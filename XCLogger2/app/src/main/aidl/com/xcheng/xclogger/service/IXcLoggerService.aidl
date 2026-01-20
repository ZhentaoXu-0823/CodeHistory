package com.xcheng.xclogger.service;

import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.service.IXcLoggerListener;

interface IXcLoggerService {
    // 业务控制
    boolean startLogging(String source);
    void stopLogging();
    boolean isRunning();

    // 配置控制
    XcLoggerConfig getConfiguration();
    void updateConfiguration(in XcLoggerConfig config);

    // 压缩任务
    void triggerCompression(String targetPackage);

    // 监听器注册
    void registerListener(IXcLoggerListener listener);
    void unregisterListener(IXcLoggerListener listener);
}