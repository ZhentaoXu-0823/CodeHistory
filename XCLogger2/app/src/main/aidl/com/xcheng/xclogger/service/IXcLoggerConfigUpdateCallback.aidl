package com.xcheng.xclogger.service;

import com.xcheng.xclogger.util.XcLoggerConfigUpdateResult;

oneway interface IXcLoggerConfigUpdateCallback {
    void onComplete(in XcLoggerConfigUpdateResult result);
}
