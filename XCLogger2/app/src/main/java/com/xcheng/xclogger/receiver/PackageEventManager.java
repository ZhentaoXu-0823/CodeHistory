package com.xcheng.xclogger.receiver;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.recorder.SystemLogCatcher;
import com.xcheng.xclogger.util.XcLoggerConfig;

import java.util.List;

/**
 * PackageEventManager - 包事件管理器
 * 统一处理 PACKAGE_ADDED / PACKAGE_REMOVED 事件及 UID 映射刷新
 * 可被 BroadcastReceiver 和 SystemLogCatcher 共用
 */
public class PackageEventManager {
    private static final String TAG = "PackageEventManager";

    /**
     * 从 PACKAGE_ADDED / PACKAGE_REMOVED 广播 Intent 中提取包名
     */
    public static String extractPackageName(Intent intent) {
        String packageName = intent.getDataString();
        if (packageName != null && packageName.startsWith("package:")) {
            packageName = packageName.substring(8);
        }
        return packageName;
    }

    /**
     * 检查包名是否匹配当前配置的过滤规则（前缀匹配或精确匹配）
     */
    public static boolean isPackageMatched(XcLoggerConfig config, String packageName) {
        if (config == null) return false;
        String filterPackageStr = config.getFilterPackage();
        if (filterPackageStr == null || filterPackageStr.equals("all")) return false;

        String[] filterEntries = filterPackageStr.split(",");
        for (String entry : filterEntries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.endsWith(".")) {
                // 前缀匹配
                if (packageName.startsWith(trimmed)) return true;
            } else {
                // 精确匹配
                if (trimmed.equals(packageName)) return true;
            }
        }
        return false;
    }

    /**
     * 处理包安装事件：将匹配过滤规则的新安装包 UID 追加到 filterUidSet
     * 可被 BroadcastReceiver 和定时刷新共同调用
     */
    public static void handlePackageInstalled(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) return;

        ConfigLoader loader = ConfigLoader.getInstance();
        XcLoggerConfig config = loader.getCurrentConfig();
        if (config == null) return;

        if (!isPackageMatched(config, packageName)) return;

        ProcessController controller = ProcessController.getInstance(context);
        if (controller != null && controller.isRunning()) {
            SystemLogCatcher catcher = controller.getLogCatcher();
            if (catcher != null) {
                catcher.addPackageUid(packageName);
                recordHistory(context, "Dynamic UID added for: " + packageName);
            }
        }
    }

    /**
     * 处理包卸载事件：从 filterUidSet 中移除对应 UID
     */
    public static void handlePackageRemoved(Context context, int uid, String packageName) {
        if (context == null || uid < 0) return;

        ProcessController controller = ProcessController.getInstance(context);
        if (controller != null && controller.isRunning()) {
            SystemLogCatcher catcher = controller.getLogCatcher();
            if (catcher != null) {
                catcher.removePackageUid(uid);
                recordHistory(context, "UID " + uid + " removed for: " + (packageName != null ? packageName : "unknown"));
            }
        }
    }

    /**
     * 定时刷新：重新扫描已安装应用，增量添加匹配前缀的新 UID
     * 由 SystemLogCatcher.readLogcatOutput 每 10 秒调用一次
     */
    public static int refreshPrefixUids(Context context) {
        if (context == null) return 0;

        ConfigLoader loader = ConfigLoader.getInstance();
        XcLoggerConfig config = loader.getCurrentConfig();
        if (config == null) return 0;

        String filterStr = config.getFilterPackage();
        if (filterStr == null || filterStr.equals("all")) return 0;

        // 检查是否有前缀配置
        boolean hasPrefix = false;
        for (String entry : filterStr.split(",")) {
            if (entry.trim().endsWith(".")) {
                hasPrefix = true;
                break;
            }
        }
        if (!hasPrefix) return 0;

        try {
            ProcessController controller = ProcessController.getInstance(context);
            if (controller == null) return 0;
            SystemLogCatcher catcher = controller.getLogCatcher();
            if (catcher == null) return 0;

            int addedCount = 0;
            String[] entries = filterStr.split(",");
            for (String entry : entries) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty() || !trimmed.endsWith(".")) continue;

                List<ApplicationInfo> apps = context.getPackageManager().getInstalledApplications(0);
                for (ApplicationInfo app : apps) {
                    if (app.packageName.startsWith(trimmed)) {
                        if (catcher.addPackageUidIfNew(app.uid)) {
                            addedCount++;
                        }
                    }
                }
            }

            if (addedCount > 0) {
                Log.i(TAG, "refreshPrefixUids: added " + addedCount + " new UID(s)");
            }
            return addedCount;
        } catch (Exception e) {
            Log.e(TAG, "refreshPrefixUids failed", e);
            return -1;
        }
    }

    private static void recordHistory(Context context, String operation) {
        try {
            ProcessController controller = ProcessController.getInstance(context);
            if (controller != null) {
                controller.recordOperationHistory("PackageEvent: " + operation);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to record history", e);
        }
    }
}
