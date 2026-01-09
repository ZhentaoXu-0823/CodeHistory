package com.xcheng.xclogger.processctr;

import android.content.Context;
import android.util.Log;
import com.xcheng.xclogger.filemanager.FileManager;
import com.xcheng.xclogger.recorder.LogBuffer;
import com.xcheng.xclogger.recorder.SystemLogCatcher;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

import java.io.File;

/**
 * ProcessController - 进程控制器，负责协调日志捕获的各个组件
 *
 * 功能方法：
 * - getInstance(Context) - 获取单例实例
 * - startLogging() - 开始日志记录
 * - stopLogging() - 停止日志记录
 * - appendOperateSafe(String) - 安全地追加操作记录
 * - recordOperationHistory(String) - 记录操作历史
 * - getFileManager() - 获取文件管理器实例
 */
public class ProcessController {
    private static final String TAG = "ProcessController";

    // 单例相关
    private static ProcessController instance;

    // 核心组件
    private Context context;
    private FileManager fileManager;
    private SystemLogCatcher logCatcher;
    private LogBuffer logBuffer;
    private XcLoggerDatabase database;
    private XcLoggerConfig config;

    /**
     * 私有构造函数，实现单例模式
     * @param ctx Android上下文
     */
    private ProcessController(Context ctx) {
        this.context = ctx;
        this.database = new XcLoggerDatabase(ctx);
        this.config = ConfigLoader.getInstance().getCurrentConfig();
        this.fileManager = new FileManager(ctx);
        this.logBuffer = new LogBuffer();
        this.logCatcher = new SystemLogCatcher();

        // 设置日志缓冲区监听器
        this.logBuffer.setOnFlushListener(new LogBuffer.OnFlushListener() {
            @Override
            public void onFlush(byte[] data, int len) {
                fileManager.appendToMainLog(data, len);
            }
        });

        // 设置日志捕获器监听器
        this.logCatcher.setOnLogLineListener(new SystemLogCatcher.OnLogLineListener() {
            @Override
            public void onLogLine(byte[] data, int length) {
                logBuffer.append(data, length);
            }
        });
    }

    public static synchronized ProcessController getInstance() {
        if (instance != null) {
            return instance;
        } else {
            return null;
        }
    }

    /**
     * 获取单例实例
     * @param context Android上下文
     * @return ProcessController单例实例
     */
    public static synchronized ProcessController getInstance(Context context) {
        if (instance == null) {
            instance = new ProcessController(context);
        }
        return instance;
    }

    /**
     * 开始日志记录
     *
     * 修复说明：
     * - 在启动日志前，先更新FileManager路径
     * - 每次启动日志时都创建新文件，确保每次开关日志都有独立的文件
     * - 记录启动操作历史，便于追踪
     */
    public void startLogging() {
        try {
            Log.i(TAG, "Starting logging process");

            // 更新配置
            this.config = ConfigLoader.getInstance().getCurrentConfig();
            if (config == null) {
                Log.e(TAG, "Config is null, cannot start logging");
                recordOperationHistory("Error: Failed to start logging - config is null");
                return;
            }

            // 更新FileManager路径（关键：确保路径是最新的）
            fileManager.updatePaths();

            // 每次启动日志时都创建新文件
            File newFile = fileManager.createNewMainLogFile();
            if (newFile != null) {
                Log.i(TAG, "Created new log file: " + newFile.getAbsolutePath());
            } else {
                Log.w(TAG, "Failed to create log file, will create when first data arrives");
            }

            // 开始日志捕获
            logCatcher.startCapture(config);

            // 更新数据库状态
            database.saveRunningState(true);

            Log.i(TAG, "Logging process started successfully");
            recordOperationHistory("Logging started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start logging process", e);
            recordOperationHistory("Error: Failed to start logging - " + e.getMessage());
        }
    }

    /**
     * 停止日志记录
     */
    public void stopLogging() {
        try {
            Log.i(TAG, "Stopping logging process");

            // 停止日志捕获
            logCatcher.stopCapture();

            // 刷新缓冲区（确保所有数据都写入文件）
            logBuffer.flush();

            // 重置文件状态，确保下次启动时创建新文件
            fileManager.resetCurrentLogFile();

            // 更新数据库状态
            database.saveRunningState(false);

            Log.i(TAG, "Logging process stopped successfully");
            recordOperationHistory("Logging stopped successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop logging process", e);
            recordOperationHistory("Error: Failed to stop logging - " + e.getMessage());
        }
    }

    /**
     * 安全地追加操作记录
     * @param operation 操作记录
     */
    public void appendOperateSafe(String operation) {
        try {
            // 使用新的操作历史记录方法
            fileManager.appendOperationHistory(operation);
        } catch (Exception e) {
            Log.e(TAG, "Failed to append operation record", e);
        }
    }

    /**
     * 记录操作历史
     * @param optDetail 操作详情
     */
    public void recordOperationHistory(String optDetail) {
        try {
            fileManager.appendOperationHistory(optDetail);
        } catch (Exception e) {
            Log.e(TAG, "Failed to record operation history", e);
        }
    }

    /**
     * 获取文件管理器实例
     * @return FileManager实例
     */
    public FileManager getFileManager() {
        return fileManager;
    }
}