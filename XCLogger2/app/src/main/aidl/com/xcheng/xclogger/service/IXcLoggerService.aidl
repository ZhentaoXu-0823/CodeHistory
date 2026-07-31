package com.xcheng.xclogger.service;

import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.service.IXcLoggerListener;
import com.xcheng.xclogger.service.IXcLoggerConfigUpdateCallback;
import com.xcheng.xclogger.util.XcLoggerConfigUpdate;
import com.xcheng.xclogger.util.XcLoggerConfigUpdateV3;
import android.os.ParcelFileDescriptor;

interface IXcLoggerService {
    // 业务控制
    boolean startLogging();
    boolean stopLogging();
    boolean isRunning();

    // 配置控制（部分更新语义：未设置字段保持现有值）
    XcLoggerConfig getConfiguration();
    boolean updateConfigurationPartial(in XcLoggerConfig config);

    // 触发按天压缩并导出到 /data/xclogger/mobilelog
    boolean triggerCompression();
    // 触发按时间范围压缩，startTime/endTime 格式 yyyyMMddHHmmss，传空或不传表示不限制
    boolean triggerCompressionWithRange(String startTime, String endTime);
    boolean reportUploadResult(boolean success);
    String getCompressStatus();
    boolean cancelCompressTask();

    // 监听器注册
    void registerListener(IXcLoggerListener listener);
    void unregisterListener(IXcLoggerListener listener);

    ParcelFileDescriptor getLogZip();

    // V2 methods are append-only to preserve transaction codes used by old AAR clients.
    int getApiVersion();
    oneway void updateConfigurationV2(in XcLoggerConfigUpdate update,
            IXcLoggerConfigUpdateCallback callback);

    // API 3 methods are append-only; API 1/2 transaction codes remain unchanged.
    String getPackageFilterMode();
    oneway void updateConfigurationV3(in XcLoggerConfigUpdateV3 update,
            IXcLoggerConfigUpdateCallback callback);
}
