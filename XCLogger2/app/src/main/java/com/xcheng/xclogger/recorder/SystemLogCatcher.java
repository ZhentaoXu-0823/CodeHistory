package com.xcheng.xclogger.recorder;

import android.content.Context;
import android.app.ActivityManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.receiver.PackageEventManager;
import com.xcheng.xclogger.control.FilterConfigValidator;
import com.xcheng.xclogger.util.XcLoggerConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SystemLogCatcher - 系统日志捕获器，执行logcat命令并处理输出
 *
 * 功能方法：
 * - SystemLogCatcher(Context) - 构造函数，初始化日志捕获器
 * - startCapture(XcLoggerConfig) - 启动日志捕获
 * - stopCapture() - 停止日志捕获
 * - isRunning() - 检查运行状态
 * - setOnLogLineListener(OnLogLineListener) - 设置日志行监听器
 * - buildLogcatCommand(XcLoggerConfig) - 构建logcat命令
 * - readLogcatOutput() - 读取logcat输出
 * - readErrorOutput() - 读取logcat错误输出
 * - monitorProcess() - 监控进程状态
 * - isWithinStartupWindow() - 判断是否在开机时间窗口内
 * - calculateTimestampOffset() - 计算时间戳偏移
 * - parseAndUpdateFilterConfig(XcLoggerConfig) - 解析配置并更新过滤数组容器（包括包名到UID的映射）
 * - buildTagFilterCommand() - 构建logcat命令的Tag过滤部分
 * - matchesTagFilter(String) - 验证日志行是否匹配Tag过滤
 * - matchesLevelFilter(String) - 验证日志行是否匹配Level过滤
 * - matchesPidFilter(int) - 验证日志行是否匹配PID过滤（包名动态映射到运行中进程）
 * - parseLogLine(String) - 解析日志行，提取Tag、Level、UID信息
 * - refreshPackagePids() - 根据运行中进程的包名动态维护PID集合
 */
public class SystemLogCatcher {
    private static final String TAG = "SystemLogCatcher";

    // 开机时间窗口（毫秒）- 2分钟
    private static final long STARTUP_TIME_WINDOW_MS = 2 * 60 * 1000;
    // 时间戳偏移秒数 - 往前3秒
    private static final int TIMESTAMP_OFFSET_SECONDS = 3;

    // Level优先级映射（f>e>w>i>d>v，数字越小优先级越高）
    private static final Map<String, Integer> LEVEL_PRIORITY = new HashMap<>();
    static {
        LEVEL_PRIORITY.put("f", 0);
        LEVEL_PRIORITY.put("e", 1);
        LEVEL_PRIORITY.put("w", 2);
        LEVEL_PRIORITY.put("i", 3);
        LEVEL_PRIORITY.put("d", 4);
        LEVEL_PRIORITY.put("v", 5);
    }

    // 核心组件
    private Context context;
    private ActivityManager activityManager;
    private Process logcatProcess;
    private ExecutorService executor;
    private AtomicBoolean running = new AtomicBoolean(false);
    private OnLogLineListener logLineListener;

    // 数据统计
    private AtomicLong totalBytesRead = new AtomicLong(0);

    // 解析后的过滤配置（数组容器，用于性能优化）
    private String[] filterTags;           // Tag数组，用于logcat命令和应用层过滤
    private String filterLevel;             // Level字符串，用于logcat命令和应用层过滤
    private String[] filterPackages;        // Package数组，用于包名匹配
    private Set<Integer> filterPidSet;      // PID Set，由运行中进程的包名动态映射得到
    private final Map<String, Set<Integer>> packagePidMap = new ConcurrentHashMap<>();

    // --- White+Black list fields (v1.2.2) ---
    private Set<String>  tagBlacklistSet;
    private Set<Integer> uidBlacklistSet;
    private Set<Integer> levelBlacklistSet;
    private Set<String>  contentWhitelistSet;
    private Set<String>  contentBlacklistSet;
    private volatile FilterPipeline.FilterState filterState;
    private boolean tagIsAll;

    private volatile boolean stoppedIntentionally = false;
    private int logcatRestartCount = 0;
    private XcLoggerConfig cachedConfig;
    private Handler handler;
    // 前缀匹配定时刷新
    private static final long PREFIX_REFRESH_INTERVAL_MS = 10000; // 10秒刷新一次
    private long lastPrefixRefreshTime = 0;
    private boolean hasPackageFilter = false; // 是否有包过滤配置

    /**
     * 日志行监听器接口
     */
    public interface OnLogLineListener {
        /**
         * 接收日志行字节数据
         * @param data 日志行字节数据
         * @param length 数据长度
         */
        void onLogLine(byte[] data, int length);
    }

    /**
     * 构造函数，初始化日志捕获器
     * @param context Android上下文，用于查询运行中进程及其包名
     */
    public SystemLogCatcher(Context context) {
        this.context = context;
        this.activityManager = context != null
                ? (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE) : null;
        this.executor = Executors.newCachedThreadPool();
        this.handler = new Handler(Looper.getMainLooper());
        Log.d(TAG, "SystemLogCatcher initialized with context: " + (context != null ? "not null" : "null"));
    }

    /**
     * 启动日志捕获
     * @param config 配置对象（包含包名字符串）
     */
    public void startCapture(XcLoggerConfig config) {
        if (running.get()) {
            Log.w(TAG, "LogCatcher is already running");
            return;
        }

        try {
            // 先校验外部过滤值，再解析包名到当前运行进程 PID
            FilterConfigValidator.validate(config);
            this.stoppedIntentionally = false;
            this.cachedConfig = config;
            this.logcatRestartCount = 0;
            parseAndUpdateFilterConfig(config);

            String[] logcatCommand = buildLogcatCommand(config);
            Log.i(TAG, "Logcat command args: " + Arrays.toString(logcatCommand));

            // 每个参数单独传入，禁止 shell 解释外部过滤值。
            logcatProcess = new ProcessBuilder(logcatCommand).start();
            running.set(true);
            totalBytesRead.set(0);

            // 检查进程是否立即退出
            boolean processAlive = logcatProcess.isAlive();
            Log.i(TAG, "Process started, isAlive: " + processAlive);

            if (!processAlive) {
                // 进程立即退出，检查退出码
                try {
                    int exitValue = logcatProcess.exitValue();
                    Log.e(TAG, "Process exited immediately with exit code: " + exitValue);
                    running.set(false);
                    return;
                } catch (IllegalThreadStateException e) {
                    // 进程还在运行，这是正常的
                    Log.d(TAG, "Process is still running (expected)");
                }
            }

            // 在后台线程中读取日志输出
            executor.execute(this::readLogcatOutput);

            // 在后台线程中读取错误输出
            executor.execute(this::readErrorOutput);

            // 监控进程状态
            executor.execute(this::monitorProcess);

            Log.i(TAG, "LogCatcher started successfully");
        } catch (IOException e) {
            Log.e(TAG, "Failed to start LogCatcher", e);
            running.set(false);
        }
    }

    /**
     * 停止日志捕获
     */
    public void stopCapture() {
        this.stoppedIntentionally = true;
        if (!running.get()) {
            Log.w(TAG, "LogCatcher is not running");
            return;
        }

        running.set(false);

        if (logcatProcess != null) {
            try {
                // 先尝试正常终止
                logcatProcess.destroy();

                // 等待进程结束，最多等待1秒
                boolean terminated = logcatProcess.waitFor(1, TimeUnit.SECONDS);
                if (!terminated) {
                    Log.w(TAG, "Process did not terminate gracefully, forcing destroy");
                    logcatProcess.destroyForcibly();
                }

                int exitValue = logcatProcess.exitValue();
                Log.i(TAG, "Process terminated with exit code: " + exitValue);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping process", e);
            }
            logcatProcess = null;
        }

        long totalBytes = totalBytesRead.get();

        Log.i(TAG, "LogCatcher stopped");
    }

    /**
     * 检查运行状态
     * @return 是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 设置日志行监听器
     * @param listener 日志行监听器
     */
    public void setOnLogLineListener(OnLogLineListener listener) {
        this.logLineListener = listener;
        Log.d(TAG, "LogLineListener set: " + (listener != null ? "not null" : "null"));
    }

    /**
     * 监控进程状态
     */
    private void monitorProcess() {
        try {
            Log.d(TAG, "Starting process monitor...");

            // 等待一小段时间，检查进程是否还在运行
            Thread.sleep(500);

            if (logcatProcess != null) {
                boolean isAlive = logcatProcess.isAlive();
                Log.i(TAG, "Process monitor check (500ms): isAlive=" + isAlive);

                if (!isAlive && running.get() && !stoppedIntentionally) {
                    try {
                        int exitValue = logcatProcess.exitValue();
                        Log.e(TAG, "Process exited unexpectedly with exit code: " + exitValue);

                        ProcessController pc = ProcessController.getInstance();
                        if (pc != null) {
                            pc.recordOperationHistory("Logcat process exited unexpectedly with code: " + exitValue + ", will attempt auto-restart");
                        }

                        scheduleRestart();
                    } catch (IllegalThreadStateException e) {
                        Log.d(TAG, "Process state changed during check");
                    }
                } else if (!isAlive && running.get() && stoppedIntentionally) {
                    try {
                        int exitValue = logcatProcess.exitValue();
                        Log.i(TAG, "Process exited after intentional stop, code: " + exitValue);
                        running.set(false);
                    } catch (IllegalThreadStateException e) {
                        Log.d(TAG, "Process state changed during check");
                    }
                }
            }

            // 持续监控进程
            while (running.get() && logcatProcess != null) {
                Thread.sleep(5000); // 每5秒检查一次

                if (logcatProcess != null) {
                    boolean isAlive = logcatProcess.isAlive();
                                        if (!isAlive && running.get() && !stoppedIntentionally) {
                        try {
                            int exitValue = logcatProcess.exitValue();
                            Log.e(TAG, "Process exited during monitoring with exit code: " + exitValue);

                            ProcessController pc = ProcessController.getInstance();
                            if (pc != null) {
                                pc.recordOperationHistory("Logcat process exited during monitoring with code: " + exitValue + ", will attempt auto-restart");
                            }

                            scheduleRestart();
                        } catch (IllegalThreadStateException e) {
                        }
                        break;
                    } else if (!isAlive && running.get() && stoppedIntentionally) {
                        try {
                            int exitValue = logcatProcess.exitValue();
                            Log.i(TAG, "Process exited after intentional stop, monitor exiting");
                            running.set(false);
                        } catch (IllegalThreadStateException e) {
                        }
                        break;
                    }
                }
            }

            Log.d(TAG, "Process monitor exited");
        } catch (InterruptedException e) {
            Log.d(TAG, "Process monitor interrupted");
        } catch (Exception e) {
            Log.e(TAG, "Error in process monitor", e);
        }
    }

    /**
     * Delay-restart logcat after unexpected process exit.
     * Uses exponential backoff: 10s, 20s, 40s, 80s (max).
     */
    private void scheduleRestart() {
        int delaySeconds = 10 * (int) Math.pow(2, Math.min(logcatRestartCount, 3));
        logcatRestartCount++;

        ProcessController pc = ProcessController.getInstance();
        if (pc != null) {
            pc.recordOperationHistory(
                "Logcat restart attempt " + logcatRestartCount + ", scheduled in " + delaySeconds + "s");
        }
        Log.w(TAG, "Scheduling logcat restart attempt " + logcatRestartCount
            + " in " + delaySeconds + "s");

        running.set(false);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ProcessController pc2 = ProcessController.getInstance();
                if (pc2 != null) {
                    pc2.recordOperationHistory(
                        "Logcat restart attempt " + logcatRestartCount + " executing");
                }
                if (stoppedIntentionally) {
                    Log.i(TAG, "Restart skipped: user stopped logging during restart delay");
                    return;
                }
                if (cachedConfig == null) {
                    Log.e(TAG, "Restart failed: no cached config available");
                    return;
                }
                Log.i(TAG, "Executing logcat restart attempt " + logcatRestartCount);
                startCapture(cachedConfig);
                if (!isRunning()) {
                    Log.e(TAG, "Restart attempt " + logcatRestartCount + " failed: startCapture did not result in running state");
                    if (pc2 != null) {
                        pc2.recordOperationHistory(
                            "Logcat restart attempt " + logcatRestartCount + " failed");
                    }
                    scheduleRestart();
                }
            }
        }, delaySeconds * 1000L);
    }


    /**
     * Periodically refresh package-matched PIDs.
     * Runs every 10s independently of the logcat reader thread.
     */
    private void prefixPollingLoop() {
        while (running.get()) {
            try {
                Thread.sleep(PREFIX_REFRESH_INTERVAL_MS);
                if (!running.get()) break;
                refreshPackagePidSet();
            } catch (InterruptedException e) {
                break;
            }
        }
        Log.d(TAG, "prefixPollingLoop exited");
    }

    /**
     * 判断是否在开机时间窗口内
     *
     * 判断逻辑：
     * - 使用 SystemClock.uptimeMillis() 获取系统运行时间
     * - 如果运行时间 < 2分钟，认为在开机时间窗口内
     *
     * @return 是否在开机时间窗口内（true：开机2分钟内，false：超过2分钟）
     */
    private boolean isWithinStartupWindow() {
        long uptimeMs = SystemClock.uptimeMillis();
        boolean isWithinWindow = uptimeMs < STARTUP_TIME_WINDOW_MS;

        if (isWithinWindow) {
            Log.d(TAG, "Within startup window: system uptime = " + (uptimeMs / 1000) + " seconds");
        } else {
            Log.d(TAG, "Beyond startup window: system uptime = " + (uptimeMs / 1000) + " seconds");
        }

        return isWithinWindow;
    }

    /**
     * 计算时间戳偏移
     *
     * 计算逻辑：
     * - 当前时间 - 偏移秒数（3秒）
     * - 格式化为 logcat -T 参数要求的格式：MM-dd HH:mm:ss.SSS
     *
     * @return 格式化的时间戳字符串，如果计算失败返回null
     */
    private String calculateTimestampOffset() {
        try {
            long targetTime = System.currentTimeMillis() - (TIMESTAMP_OFFSET_SECONDS * 1000L);
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
            String timestamp = sdf.format(new Date(targetTime));
            Log.d(TAG, "Calculated timestamp offset: " + timestamp + " (offset: " + TIMESTAMP_OFFSET_SECONDS + " seconds)");
            return timestamp;
        } catch (Exception e) {
            Log.e(TAG, "Error calculating timestamp offset", e);
            return null;
        }
    }

    /**
     * 重新扫描当前运行进程，将匹配配置包名的 PID 放入过滤缓存。
     * 通过进程包名而不是 UID 匹配，避免多个共享 UID 的包相互串日志。
     *
     * @return 新缓存中的 PID 数量；查询失败返回 -1
     */
    public int refreshPackagePids() {
        if (!hasPackageFilter || filterPackages == null || filterPackages.length == 0) {
            filterPidSet.clear();
            packagePidMap.clear();
            return 0;
        }
        if (activityManager == null) {
            Log.w(TAG, "ActivityManager is null, cannot refresh package PIDs");
            return -1;
        }

        List<ActivityManager.RunningAppProcessInfo> runningProcesses = activityManager.getRunningAppProcesses();
        if (runningProcesses == null) {
            Log.w(TAG, "Running process list is unavailable");
            return -1;
        }

        Map<String, Set<Integer>> refreshedMap = new HashMap<>();
        Set<Integer> refreshedPids = ConcurrentHashMap.newKeySet();
        for (ActivityManager.RunningAppProcessInfo process : runningProcesses) {
            if (process == null || process.pid <= 0 || process.pkgList == null) {
                continue;
            }
            for (String packageName : process.pkgList) {
                if (!isPackageMatched(packageName)) {
                    continue;
                }
                refreshedMap.computeIfAbsent(packageName, key -> ConcurrentHashMap.newKeySet()).add(process.pid);
                refreshedPids.add(process.pid);
            }
        }

        packagePidMap.clear();
        packagePidMap.putAll(refreshedMap);
        filterPidSet.clear();
        filterPidSet.addAll(refreshedPids);
        Log.i(TAG, "Package PID cache refreshed: packages=" + packagePidMap.keySet()
                + ", PIDs=" + filterPidSet);
        return filterPidSet.size();
    }

    /** Refresh after a package installation or configuration change. */
    public void addPackagePids(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        refreshPackagePids();
    }

    /** Remove all PIDs associated with an uninstalled package. */
    public void removePackagePids(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        packagePidMap.remove(packageName);
        filterPidSet.clear();
        for (Set<Integer> pids : packagePidMap.values()) {
            filterPidSet.addAll(pids);
        }
        Log.i(TAG, "Package removed from PID filter: " + packageName + ", PIDs=" + filterPidSet);
    }

    private boolean isPackageMatched(String packageName) {
        if (packageName == null || filterPackages == null) {
            return false;
        }
        for (String entry : filterPackages) {
            if (entry.endsWith(".")) {
                if (packageName.startsWith(entry)) {
                    return true;
                }
            } else if (entry.equals(packageName)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Check whether the logcat subprocess is ACTUALLY alive (OS-level PID check).
     * Unlike running.get() which is an in-memory flag, this verifies the real process.
     */
    public boolean isLogcatAlive() {
        return logcatProcess != null && logcatProcess.isAlive();
    }

    /** Refresh the dynamic PID cache through the shared package event manager. */
    private void refreshPackagePidSet() {
        if (!hasPackageFilter) return;
        int refreshed = PackageEventManager.refreshPackagePids(context);
        try {
            ProcessController ctrl = ProcessController.getInstance(context);
            if (ctrl != null) {
                ctrl.recordOperationHistory(refreshed >= 0
                        ? "Package PID cache refreshed, total " + filterPidSet.size() + " PIDs cached"
                        : "Package PID cache refresh failed");
            }
        } catch (Exception ignore) {}
    }

    /**
     * 解析配置并更新过滤数组容器。
     * 对于Package过滤，动态扫描运行中进程，将匹配包名映射为PID集合。
     * @param config 配置对象（包含包名字符串）
     */
    private void parseAndUpdateFilterConfig(XcLoggerConfig config) {
        Log.d(TAG, "Starting to parse filter config...");
        this.hasPackageFilter = false;

        // 解析Filter Tag
        if (config != null && config.getFilterTag() != null && !config.getFilterTag().equals("all")) {
            String[] tags = config.getFilterTag().split(",");
            List<String> tagList = new ArrayList<>();
            for (String tag : tags) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    tagList.add(trimmed);
                }
            }
            filterTags = tagList.toArray(new String[0]);
            Log.d(TAG, "Filter Tags parsed: " + Arrays.toString(filterTags));
        } else {
            filterTags = new String[0];
            Log.d(TAG, "No Filter Tag configured (all)");
        }

        // 解析Filter Level
        if (config != null && config.getFilterLevel() != null && !config.getFilterLevel().equals("all")) {
            filterLevel = config.getFilterLevel().trim().toLowerCase();
            Log.d(TAG, "Filter Level parsed: " + filterLevel);
        } else {
            filterLevel = null;
            Log.d(TAG, "No Filter Level configured (all)");
        }

        // 解析Filter Package并动态查询对应的PID
        filterPackages = new String[0];
        filterPidSet = ConcurrentHashMap.newKeySet();
        packagePidMap.clear();

        if (config != null && config.getFilterPackage() != null && !config.getFilterPackage().equals("all")) {
            String filterPackageStr = config.getFilterPackage();
            Log.d(TAG, "Filter Package string from config: " + filterPackageStr);

            String[] packages = filterPackageStr.split(",");
            List<String> packageList = new ArrayList<>();

            for (String pkg : packages) {
                String trimmed = pkg.trim();
                if (!trimmed.isEmpty()) {
                    packageList.add(trimmed);
                    Log.d(TAG, "Processing package: " + trimmed);
                    if (trimmed.endsWith(".")) {
                        this.hasPackageFilter = true;
                        Log.d(TAG, "Package entry ends with '.', treating as prefix: " + trimmed);
                    } else {
                        this.hasPackageFilter = true;
                    }
                }
            }

            filterPackages = packageList.toArray(new String[0]);
            refreshPackagePids();
        } else {
            Log.d(TAG, "No Filter Package configured (all)");
        }

        Log.i(TAG, "Filter config parsed - Tags: " + Arrays.toString(filterTags) +
                ", Level: " + filterLevel + ", Packages: " + Arrays.toString(filterPackages) +
                ", PIDs: " + filterPidSet);
    }

    /**
     * 构建logcat命令的Tag过滤部分
     * 使用严格过滤模式：*:S 先屏蔽所有，然后启用需要的tag
     * @param command 参数列表；每个 filter spec 独立传参，禁止 shell 解释
     */
    private void appendTagFilterCommand(List<String> command) {
        if (filterTags == null || filterTags.length == 0) {
            return;
        }

        // 使用严格过滤模式：先屏蔽所有，再启用需要的tag
        command.add("*:S");

        // 为每个tag添加level过滤
        for (String tag : filterTags) {
            if (filterLevel != null) {
                command.add(tag + ":" + filterLevel.toUpperCase());
            } else {
                // 如果没有level，使用V级别（最低级别，输出所有）
                command.add(tag + ":V");
            }
        }

        // Append CRITICAL_TAGS (crash logs) to bypass *:S filter
        for (String ct : FilterPipeline.CRITICAL_TAGS) {
            command.add(ct + ":V");
        }
    }

    /**
     * 验证日志行是否匹配Tag过滤（应用层过滤）
     * @param tag 日志行的tag
     * @return 是否匹配
     */
    private boolean matchesTagFilter(String tag) {
        if (filterTags == null || filterTags.length == 0) {
            return true; // 没有Tag过滤，全部通过
        }
        // Tag 已在 logcat 层由 *:S 过滤过，仅当存在 UID 过滤时才需 app 层二次确认
        if (filterPackages == null || filterPackages.length == 0) {
            return true;
        }
        if (tag == null) {
            return false;
        }
        // 检查tag是否在过滤列表中
        for (String filterTag : filterTags) {
            if (filterTag.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证日志行是否匹配Level过滤（应用层过滤）
     * @param level 日志行的level
     * @return 是否匹配
     */
    private boolean matchesLevelFilter(String level) {
        if (filterLevel == null) {
            return true; // 没有Level过滤，全部通过
        }
        if (level == null) {
            return false;
        }

        String logLevel = level.toLowerCase();
        Integer configPriority = LEVEL_PRIORITY.get(filterLevel);
        Integer logPriority = LEVEL_PRIORITY.get(logLevel);

        if (configPriority == null || logPriority == null) {
            return false;
        }

        // logLevel的优先级 <= filterLevel的优先级（数字越小优先级越高）
        // 例如：filterLevel=i(3)，则logLevel可以是i(3), w(2), e(1), f(0)
        return logPriority <= configPriority;
    }

    /**
     * 验证日志行是否匹配UID过滤（应用层过滤）
     * 通过Package查询到的UID进行过滤
     * @param pid 日志行的PID
     * @return 是否匹配
     */
    private boolean matchesPidFilter(int pid) {
        if (filterPackages == null || filterPackages.length == 0) {
            return true; // 没有包过滤，全部通过
        }
        // 使用Set快速查找（O(1)时间复杂度）。没有匹配到运行中 PID 时全部拒绝。
        boolean matched = filterPidSet != null && filterPidSet.contains(pid);
        return matched;
    }

    /**
     * 解析日志行，提取Tag、Level、UID信息
     * 日志格式（threadtime,uid）：MM-DD HH:MM:SS.mmm PID TID UID LEVEL TAG: message
     * @param line 日志行
     * @return LogLineInfo对象，包含解析后的信息
     */
    private static class LogLineInfo {
        String tag;
        String level;
        int pid = -1;
    }

    private LogLineInfo parseLogLine(String line) {
        LogLineInfo info = new LogLineInfo();

        if (line == null || line.trim().isEmpty()) {
            return info;
        }

        try {
            String[] parts = line.split("\\s+");

            if (parts.length >= 7) {
                if (filterPackages != null && filterPackages.length > 0) {
                    String pidPart = parts[2];
                    if (pidPart != null && pidPart.matches("\\d+")) {
                        info.pid = Integer.parseInt(pidPart);
                    }
                }

                info.level = parts[5];

                String tagPart = parts[6];
                if (tagPart.endsWith(":")) {
                    tagPart = tagPart.substring(0, tagPart.length() - 1);
                }
                info.tag = tagPart;
            }
        } catch (Exception e) {
            Log.d(TAG, "Ignored unparsable log line");
        }

        return info;
    }

    /**
     * 构建logcat命令
     *
     * 实现说明：
     * - 根据系统开机时间判断是否需要添加时间戳过滤
     * - 如果开机时间 < 2分钟：不使用 -T 参数（不过滤，读取所有日志）
     * - 如果开机时间 >= 2分钟：使用 -T 参数，传入当前时间往前3秒的时间戳
     * - 时间戳格式：MM-dd HH:mm:ss.SSS
     * - 使用严格过滤模式：*:S 先屏蔽所有，然后启用需要的tag
     *
     * @param config 配置对象
     * @return logcat命令参数列表
     */
    private String[] buildLogcatCommand(XcLoggerConfig config) {
        List<String> command = new ArrayList<>();
        command.add("logcat");

        // 添加输出格式（threadtime格式包含UID信息）
        command.add("-v");
        command.add("threadtime,uid");

        // 根据开机时间判断是否需要添加时间戳过滤
        boolean isWithinStartup = isWithinStartupWindow();

        if (!isWithinStartup) {
            // 开机时间 >= 2分钟：使用时间戳过滤（往前3秒）
            String timestamp = calculateTimestampOffset();
            if (timestamp != null) {
                command.add("-T");
                command.add(timestamp);
                Log.i(TAG, "Using timestamp filter: " + timestamp);
            } else {
                Log.w(TAG, "Failed to calculate timestamp, continuing without timestamp filter");
            }
        } else {
            // 开机时间 < 2分钟：不使用时间戳过滤（读取所有日志）
            Log.i(TAG, "Within startup window, not using timestamp filter");
        }

        // 添加Tag和Level过滤（使用严格过滤模式：*:S tag:level）
        int commandSizeBeforeFilter = command.size();
        appendTagFilterCommand(command);
        if (command.size() > commandSizeBeforeFilter) {
            Log.i(TAG, "Added tag filter: " + Arrays.toString(filterTags) + ", level: " + filterLevel);
        }

        String[] finalCommand = command.toArray(new String[0]);
        ProcessController processController = ProcessController.getInstance();
        if (null != processController) {
            processController.recordOperationHistory("Log command args: " + Arrays.toString(finalCommand));
        }
        return finalCommand;
    }

    /**
     * 读取logcat输出
     * 改为按行读取，添加应用层过滤（Tag、Level、UID）
     */
    private void readLogcatOutput() {
        InputStream inputStream = null;
        BufferedReader reader = null;

        try {
            if (logcatProcess == null) {
                Log.e(TAG, "logcatProcess is null, cannot read output");
                return;
            }

            inputStream = logcatProcess.getInputStream();
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            int lineCount = 0;
            int filteredCount = 0;
            int tagFilteredCount = 0;
            int levelFilteredCount = 0;
            int uidFilteredCount = 0;
            int savedCount = 0;
            long lastReadTime = System.currentTimeMillis();

            Log.i(TAG, "Starting to read logcat output...");
            Log.i(TAG, "Process isAlive: " + (logcatProcess != null ? logcatProcess.isAlive() : "null"));
            Log.i(TAG, "Filter config - Tags: " + Arrays.toString(filterTags) +
                    ", Level: " + filterLevel + ", PIDs: " + filterPidSet);

            while (running.get()) {
                // 检查进程是否还存活
                if (logcatProcess != null && !logcatProcess.isAlive()) {
                    try {
                        int exitValue = logcatProcess.exitValue();
                        Log.e(TAG, "Process exited while reading, exit code: " + exitValue);
                        if (lineCount == 0) {
                            Log.e(TAG, "Process exited before reading any data!");
                        }
                    } catch (IllegalThreadStateException e) {
                        // 进程状态变化，继续尝试读取
                    }
                }

                // 按行读取
                line = reader.readLine();
                if (line == null) {
                    // 流结束
                    Log.i(TAG, "Stream ended (EOF)");
                    break;
                }

                lineCount++;
                lastReadTime = System.currentTimeMillis();

                if (lineCount == 1) {
                    Log.i(TAG, "First line received from logcat: " + line.substring(0, Math.min(line.length(), 100)));
                }

                if (lineCount % 1000 == 0) {
                    Log.d(TAG, "Read " + lineCount + " lines, filtered: " + filteredCount +
                            " (tag:" + tagFilteredCount + ", level:" + levelFilteredCount +
                            ", uid:" + uidFilteredCount + "), saved: " + savedCount +
                            ", total bytes: " + totalBytesRead.get());
                }

                // 解析日志行
                LogLineInfo logInfo = parseLogLine(line);

                // 定时刷新前缀匹配的 UID（仅当有前缀配置时生效）
                if (filterPackages != null && System.currentTimeMillis() - lastPrefixRefreshTime > PREFIX_REFRESH_INTERVAL_MS) {
                    refreshPackagePidSet();
                    lastPrefixRefreshTime = System.currentTimeMillis();
                }

                // 应用层过滤：Tag、Level、UID
                // Critical crash tags bypass all filters (AndroidRuntime/DEBUG/libc)
                boolean isCritical = FilterPipeline.CRITICAL_TAGS.contains(logInfo.tag);
                boolean tagMatch = matchesTagFilter(logInfo.tag);
                boolean levelMatch = matchesLevelFilter(logInfo.level);
                boolean pidMatch = matchesPidFilter(logInfo.pid);

                // 调试日志：前10行详细记录过滤过程
                if (lineCount <= 10) {
                    Log.d(TAG, "Line " + lineCount + " - PID: " + logInfo.pid +
                            ", Level: " + logInfo.level + ", Tag: " + logInfo.tag +
                            " | TagMatch: " + tagMatch + ", LevelMatch: " + levelMatch +
                            ", PidMatch: " + pidMatch);
                }

                // 全部通过才保留（崩溃日志跳过 UID 检查）
                if (isCritical || (tagMatch && levelMatch && pidMatch)) {
                    // 匹配所有过滤条件，保留该行
                    savedCount++;
                    byte[] lineBytes = line.getBytes();
                    int lineLength = lineBytes.length;
                    totalBytesRead.addAndGet(lineLength + 1); // +1 for newline

                    if (running.get() && logLineListener != null) {
                        // 传递原始字节数据（包含换行符）
                        byte[] lineWithNewline = new byte[lineLength + 1];
                        System.arraycopy(lineBytes, 0, lineWithNewline, 0, lineLength);
                        lineWithNewline[lineLength] = '\n';
                        logLineListener.onLogLine(lineWithNewline, lineLength + 1);
                    } else {
                        Log.w(TAG, "Line matched but logLineListener is null or not running. Line: " +
                                (line.length() > 100 ? line.substring(0, 100) + "..." : line));
                    }
                } else {
                    // 不匹配过滤条件，过滤掉
                    filteredCount++;
                    if (!tagMatch) tagFilteredCount++;
                    if (!levelMatch) levelFilteredCount++;
                    if (!pidMatch) uidFilteredCount++;

                    // 调试日志：前10行详细记录为什么被过滤
                    if (lineCount <= 10) {
                        Log.d(TAG, "Line " + lineCount + " filtered - TagMatch: " + tagMatch +
                                ", LevelMatch: " + levelMatch + ", PidMatch: " + pidMatch);
                    }
                }

                // 检查是否长时间没有数据（超过10秒）
                long timeSinceLastRead = System.currentTimeMillis() - lastReadTime;
                if (lineCount > 0 && timeSinceLastRead > 10000) {
                    Log.w(TAG, "No data received for " + (timeSinceLastRead / 1000) + " seconds");
                }
            }

            Log.i(TAG, "Finished reading logcat output. Total lines: " + lineCount +
                    ", filtered: " + filteredCount + " (tag:" + tagFilteredCount +
                    ", level:" + levelFilteredCount + ", uid:" + uidFilteredCount +
                    "), saved: " + savedCount + ", total bytes: " + totalBytesRead.get());

            if (lineCount == 0) {
                Log.e(TAG, "WARNING: No data was read from logcat! This may indicate:");
                Log.e(TAG, "  1. Permission issue - app may not have permission to read logs");
                Log.e(TAG, "  2. Process exited immediately");
                Log.e(TAG, "  3. No logs available for the specified time range");
            } else if (savedCount == 0 && lineCount > 0) {
                Log.e(TAG, "WARNING: Read " + lineCount + " lines but none were saved! Filter may be too strict.");
                Log.e(TAG, "Filter config - Tags: " + Arrays.toString(filterTags) +
                        ", Level: " + filterLevel + ", PIDs: " + filterPidSet);
            }

        } catch (IOException e) {
            if (running.get()) {
                Log.e(TAG, "Error reading logcat output", e);
                Log.e(TAG, "Total bytes read before error: " + totalBytesRead.get());
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in readLogcatOutput", e);
        } finally {
            Log.i(TAG, "readLogcatOutput thread exited");
        }
    }

    /**
     * 读取logcat错误输出
     */
    private void readErrorOutput() {
        InputStream errorStream = null;
        BufferedReader reader = null;

        try {
            if (logcatProcess == null) {
                Log.d(TAG, "logcatProcess is null, cannot read error output");
                return;
            }

            errorStream = logcatProcess.getErrorStream();
            reader = new BufferedReader(new InputStreamReader(errorStream));

            String line;
            boolean hasError = false;
            int errorLineCount = 0;

            Log.d(TAG, "Starting to read logcat error output...");

            while (running.get()) {
                if (reader.ready()) {
                    line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    hasError = true;
                    errorLineCount++;
                    Log.e(TAG, "Logcat error output [" + errorLineCount + "]: " + line);
                } else {
                    // 没有可用数据，检查进程是否还存活
                    if (logcatProcess != null && !logcatProcess.isAlive()) {
                        Log.d(TAG, "Process exited, stopping error output reading");
                        break;
                    }
                    Thread.sleep(100);
                }
            }

            if (!hasError) {
                Log.d(TAG, "No error output from logcat");
            } else {
                Log.w(TAG, "Total error lines read: " + errorLineCount);
            }
        } catch (IOException e) {
            if (running.get()) {
                Log.e(TAG, "Error reading logcat error output", e);
            }
        } catch (InterruptedException e) {
            Log.d(TAG, "Error output reading thread interrupted");
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in readErrorOutput", e);
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) { }
            if (errorStream != null) try { errorStream.close(); } catch (IOException ignored) { }
            Log.d(TAG, "readErrorOutput thread exited");
        }
    }
}
