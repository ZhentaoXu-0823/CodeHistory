package com.xcheng.xclogger.receiver;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.recorder.SystemLogCatcher;
import com.xcheng.xclogger.util.XcLoggerConfig;


/**
 * PackageEventManager - 包事件管理器
 * 统一处理 PACKAGE_ADDED / PACKAGE_REMOVED 事件及 PID 映射刷新
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
     * 处理包安装事件：重新扫描匹配过滤规则的运行中进程 PID
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
                catcher.addPackagePids(packageName);
                recordHistory(context, "Dynamic package PID refresh for: " + packageName);
            }
        }
    }

    /**
     * 处理包卸载事件：从 PID 缓存中移除该包关联的进程
     */
    public static void handlePackageRemoved(Context context, int uid, String packageName) {
        if (context == null || uid < 0) return;

        ProcessController controller = ProcessController.getInstance(context);
        if (controller != null && controller.isRunning()) {
            SystemLogCatcher catcher = controller.getLogCatcher();
            if (catcher != null) {
                catcher.removePackagePids(packageName);
                recordHistory(context, "Package PID mapping removed for: " + (packageName != null ? packageName : "unknown"));
            }
        }
    }

    /**
     * 定时刷新：重新扫描当前运行进程，维护匹配包名的 PID
     * 由 SystemLogCatcher.readLogcatOutput 每 10 秒调用一次
     */
    public static int refreshPackagePids(Context context) {
        if (context == null) return 0;

        ConfigLoader loader = ConfigLoader.getInstance();
        XcLoggerConfig config = loader.getCurrentConfig();
        if (config == null) return 0;

        String filterStr = config.getFilterPackage();
        if (filterStr == null || filterStr.equals("all")) return 0;

        try {
            ProcessController controller = ProcessController.getInstance(context);
            if (controller == null) return 0;
            SystemLogCatcher catcher = controller.getLogCatcher();
            if (catcher == null) return 0;

            return catcher.refreshPackagePids();
        } catch (Exception e) {
            Log.e(TAG, "refreshPackagePids failed", e);
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
