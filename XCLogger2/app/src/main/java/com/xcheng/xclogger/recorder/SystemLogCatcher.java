package com.xcheng.xclogger.recorder;

import android.util.Log;

import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.util.XcLoggerConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

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
 */
public class SystemLogCatcher {
    private static final String TAG = "SystemLogCatcher";
    private Process logcatProcess;
    private ExecutorService executor;
    private AtomicBoolean running = new AtomicBoolean(false);
    private OnLogLineListener logLineListener;

    /**
     * 日志行监听器接口
     */
    public interface OnLogLineListener {
        /**
         * 接收日志行
         * @param line 日志行内容
         */
        void onLogLine(String line);
    }

    /**
     * 构造函数，初始化日志捕获器
     */
    public SystemLogCatcher() {
        this.executor = Executors.newSingleThreadExecutor();
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

            logcatProcess = Runtime.getRuntime().exec(logcatCommand);
            running.set(true);

            // 在后台线程中读取日志
            executor.execute(this::readLogcatOutput);

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
            logcatProcess.destroy();
            logcatProcess = null;
        }

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
    }

    /**
     * 构建logcat命令
     * @param config 配置对象
     * @return logcat命令字符串
     */
    private String buildLogcatCommand(XcLoggerConfig config) {
        StringBuilder command = new StringBuilder("logcat");

        if (config != null) {
            // 添加过滤条件
            if (config.getFilterTag() != null && !config.getFilterTag().equals("all")) {
                command.append(" -s ").append(config.getFilterTag());
            }

            if (config.getFilterLevel() != null && !config.getFilterLevel().equals("all")) {
                command.append(" *:").append(config.getFilterLevel());
            }

            if (config.getFilterPackage() != null && !config.getFilterPackage().equals("all")) {
                command.append(" --pid=").append(config.getFilterPackage());
            }
        }

        // 添加时间戳
        command.append(" -v time");

        return command.toString();
    }

    /**
     * 读取logcat输出
     */
    private void readLogcatOutput() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(logcatProcess.getInputStream()))) {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (running.get() && logLineListener != null) {
                    logLineListener.onLogLine(line);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                Log.e(TAG, "Error reading logcat output", e);
            }
        } finally {
            running.set(false);
        }
    }
}