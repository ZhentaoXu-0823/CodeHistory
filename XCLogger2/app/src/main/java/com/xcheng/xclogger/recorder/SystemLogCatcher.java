package com.xcheng.xclogger.recorder;

import android.os.SystemClock;
import android.util.Log;

import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.util.XcLoggerConfig;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SystemLogCatcher - 系统日志捕获器，执行logcat命令并处理输出
 *
 * 功能方法：
 * - SystemLogCatcher() - 构造函数，初始化日志捕获器
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
 */
public class SystemLogCatcher {
    private static final String TAG = "SystemLogCatcher";

    // 开机时间窗口（毫秒）- 2分钟
    private static final long STARTUP_TIME_WINDOW_MS = 2 * 60 * 1000;
    // 时间戳偏移秒数 - 往前3秒
    private static final int TIMESTAMP_OFFSET_SECONDS = 3;

    // 核心组件
    private Process logcatProcess;
    private ExecutorService executor;
    private AtomicBoolean running = new AtomicBoolean(false);
    private OnLogLineListener logLineListener;

    // 数据统计
    private AtomicLong totalBytesRead = new AtomicLong(0);

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
     */
    public SystemLogCatcher() {
        this.executor = Executors.newCachedThreadPool();
    }

    /**
     * 启动日志捕获
     * @param config 配置对象
     */
    public void startCapture(XcLoggerConfig config) {
        if (running.get()) {
            Log.w(TAG, "LogCatcher is already running");
            return;
        }

        try {
            String logcatCommand = buildLogcatCommand(config);

//            Log.i(TAG, "Executing logcat command: " + logcatCommand);
//            Log.i(TAG, "Command length: " + logcatCommand.length());

            // 使用shell执行命令，确保正确处理引号和特殊字符
            String[] shellCommand = new String[]{"sh", "-c", logcatCommand};
            Log.i(TAG, "Shell command: sh -c \"" + logcatCommand + "\"");

            logcatProcess = Runtime.getRuntime().exec(shellCommand);
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
//        Log.i(TAG, "LogCatcher stopped, total bytes read: " + totalBytes);

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

                if (!isAlive && running.get()) {
                    try {
                        int exitValue = logcatProcess.exitValue();
                        Log.e(TAG, "Process exited unexpectedly with exit code: " + exitValue);
                        running.set(false);

                        // 记录操作历史
                        ProcessController processController = ProcessController.getInstance();
                        if (processController != null) {
                            processController.recordOperationHistory("Logcat process exited unexpectedly with code: " + exitValue);
                        }
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
                    if (!isAlive && running.get()) {
                        try {
                            int exitValue = logcatProcess.exitValue();
                            Log.e(TAG, "Process exited during monitoring with exit code: " + exitValue);
                            running.set(false);

                            ProcessController processController = ProcessController.getInstance();
                            if (processController != null) {
                                processController.recordOperationHistory("Logcat process exited during monitoring with code: " + exitValue);
                            }
                        } catch (IllegalThreadStateException e) {
                            // 进程状态变化，忽略
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
     * 构建logcat命令
     *
     * 实现说明：
     * - 根据系统开机时间判断是否需要添加时间戳过滤
     * - 如果开机时间 < 2分钟：不使用 -T 参数（不过滤，读取所有日志）
     * - 如果开机时间 >= 2分钟：使用 -T 参数，传入当前时间往前3秒的时间戳
     * - 时间戳格式：MM-dd HH:mm:ss.SSS
     *
     * @param config 配置对象
     * @return logcat命令字符串
     */
    private String buildLogcatCommand(XcLoggerConfig config) {
        StringBuilder command = new StringBuilder("logcat");

        // 根据开机时间判断是否需要添加时间戳过滤
        boolean isWithinStartup = isWithinStartupWindow();

        if (!isWithinStartup) {
            // 开机时间 >= 2分钟：使用时间戳过滤（往前3秒）
            String timestamp = calculateTimestampOffset();
            if (timestamp != null) {
                // 使用单引号包裹时间戳，避免shell解析问题
                command.append(" -T '").append(timestamp).append("'");
                Log.i(TAG, "Using timestamp filter: " + timestamp);
            } else {
                Log.w(TAG, "Failed to calculate timestamp, continuing without timestamp filter");
            }
        } else {
            // 开机时间 < 2分钟：不使用时间戳过滤（读取所有日志）
            Log.i(TAG, "Within startup window, not using timestamp filter");
        }

        if (config != null) {
            // 添加标签过滤
            if (config.getFilterTag() != null && !config.getFilterTag().equals("all")) {
                command.append(" -s ").append(config.getFilterTag());
            }

            // 添加级别过滤
            if (config.getFilterLevel() != null && !config.getFilterLevel().equals("all")) {
                command.append(" *:").append(config.getFilterLevel());
            }

            // 添加包名过滤（通过PID）
            if (config.getFilterPackage() != null && !config.getFilterPackage().equals("all")) {
                command.append(" --pid=").append(config.getFilterPackage());
            }
        }

        // 添加时间戳格式
        command.append(" -v time");

        String finalCommand = command.toString();
        ProcessController processController = ProcessController.getInstance();
        if (null != processController) {
            processController.recordOperationHistory("Log command: " + finalCommand);
        }
        return finalCommand;
    }

    /**
     * 读取logcat输出
     */
    private void readLogcatOutput() {
        InputStream inputStream = null;
        BufferedInputStream bufferedInputStream = null;

        try {
            if (logcatProcess == null) {
                Log.e(TAG, "logcatProcess is null, cannot read output");
                return;
            }

            inputStream = logcatProcess.getInputStream();
            bufferedInputStream = new BufferedInputStream(inputStream);

            byte[] buffer = new byte[8192]; // 8KB缓冲区
            int bytesRead = -1; // 初始化为-1，避免未初始化错误
            int readCount = 0;
            long lastReadTime = System.currentTimeMillis();

            Log.i(TAG, "Starting to read logcat output...");
            Log.i(TAG, "Process isAlive: " + (logcatProcess != null ? logcatProcess.isAlive() : "null"));

            while (running.get()) {
                // 检查进程是否还存活
                if (logcatProcess != null && !logcatProcess.isAlive()) {
                    try {
                        int exitValue = logcatProcess.exitValue();
                        Log.e(TAG, "Process exited while reading, exit code: " + exitValue);
                        if (readCount == 0) {
                            Log.e(TAG, "Process exited before reading any data!");
                        }
                    } catch (IllegalThreadStateException e) {
                        // 进程状态变化，继续尝试读取
                    }
                }

                // 先检查是否有可用数据
                if (bufferedInputStream.available() > 0) {
                    // 有可用数据，执行读取
                    bytesRead = bufferedInputStream.read(buffer);
                    if (bytesRead == -1) {
                        // 流结束
                        Log.i(TAG, "Stream ended (EOF)");
                        break;
                    }
                } else {
                    // 没有可用数据，尝试阻塞读取
                    bytesRead = bufferedInputStream.read(buffer);
                    if (bytesRead == -1) {
                        // 流结束
                        Log.i(TAG, "Stream ended (EOF)");
                        break;
                    }
                }

                // 处理读取到的数据
                if (bytesRead > 0) {
                    readCount++;
                    totalBytesRead.addAndGet(bytesRead);
                    lastReadTime = System.currentTimeMillis();

                    if (readCount == 1) {
                        Log.i(TAG, "First data received from logcat, bytes: " + bytesRead);
                        // 输出前几个字节用于调试
                        if (bytesRead > 0 && bytesRead <= 100) {
                            String preview = new String(buffer, 0, Math.min(bytesRead, 100));
                            Log.d(TAG, "First data preview: " + preview.replaceAll("[\\r\\n]", " "));
                        }
                    }

                    if (readCount % 1000 == 0) {
                        Log.d(TAG, "Read " + readCount + " times, total bytes: " + totalBytesRead.get());
                    }

                    if (running.get() && logLineListener != null) {
                        // 直接传递原始字节数据，保持logcat的原始格式
                        logLineListener.onLogLine(buffer, bytesRead);
                    } else {
                        Log.w(TAG, "logLineListener is null or not running, data will be lost. bytes: " + bytesRead);
                    }
                } else if (bytesRead == -1) {
                    // 流结束，退出循环
                    break;
                } else {
                    // bytesRead == 0，没有数据，短暂休眠避免CPU占用
                    Thread.sleep(100);

                    // 检查是否长时间没有数据（超过10秒）
                    long timeSinceLastRead = System.currentTimeMillis() - lastReadTime;
                    if (readCount > 0 && timeSinceLastRead > 10000) {
                        Log.w(TAG, "No data received for " + (timeSinceLastRead / 1000) + " seconds");
                    }
                }
            }

            Log.i(TAG, "Finished reading logcat output. Total reads: " + readCount + ", total bytes: " + totalBytesRead.get());

            if (readCount == 0) {
                Log.e(TAG, "WARNING: No data was read from logcat! This may indicate:");
                Log.e(TAG, "  1. Permission issue - app may not have permission to read logs");
                Log.e(TAG, "  2. Process exited immediately");
                Log.e(TAG, "  3. No logs available for the specified time range");
            }

        } catch (IOException e) {
            if (running.get()) {
                Log.e(TAG, "Error reading logcat output", e);
                Log.e(TAG, "Total bytes read before error: " + totalBytesRead.get());
            }
        } catch (InterruptedException e) {
            Log.d(TAG, "Read thread interrupted");
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in readLogcatOutput", e);
        } finally {
            running.set(false);
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
            Log.d(TAG, "readErrorOutput thread exited");
        }
    }
}