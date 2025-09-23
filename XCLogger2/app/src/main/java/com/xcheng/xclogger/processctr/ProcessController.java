package com.xcheng.xclogger.processctr;

import android.content.Context;

import com.xcheng.xclogger.filemanager.FileManager;
import com.xcheng.xclogger.recorder.SystemLogCatcher;
import com.xcheng.xclogger.util.XcLoggerConfig;

/**
 * ProcessController - 流程控制器，协调各模块工作并记录操作历史
 *
 * 功能方法：
 * - ProcessController(Context) - 私有构造函数，初始化流程控制器
 * - getInstance(Context) - 获取单例实例
 * - startLogging() - 启动日志记录流程
 * - stopLogging() - 停止日志记录流程
 * - appendOperateSafe(String) - 安全地追加操作记录
 * - recordOperationHistory(String) - 记录操作历史
 * - getFileManager() - 获取文件管理器实例
 * - getSystemLogCatcher() - 获取日志捕获器实例
 * - isRunning() - 检查是否正在运行
 */
public class ProcessController {
    private static ProcessController instance;
    private FileManager fileManager;
    private SystemLogCatcher systemLogCatcher;
    private Context context;
    private boolean isRunning = false;

    /**
     * 私有构造函数，初始化流程控制器
     * @param ctx Android上下文
     */
    private ProcessController(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.fileManager = new FileManager(context);
        this.systemLogCatcher = new SystemLogCatcher();
    }

    /**
     * 获取单例实例
     * @param ctx Android上下文
     * @return ProcessController实例
     */
    public static synchronized ProcessController getInstance(Context ctx) {
        if (instance == null) {
            instance = new ProcessController(ctx);
        }
        return instance;
    }

    /**
     * 启动日志记录流程
     */
    public void startLogging() {
        if (isRunning) {
            appendOperateSafe("Start logging requested but already running");
            recordOperationHistory("Start logging requested but already running");
            return;
        }

        try {
            XcLoggerConfig config = ConfigLoader.current();
            if (config == null) {
                appendOperateSafe("Start logging failed: No configuration available");
                recordOperationHistory("Start logging failed: No configuration available");
                return;
            }

            // 确保基础目录存在
            if (!fileManager.ensureBaseDir()) {
                appendOperateSafe("Start logging failed: Cannot create base directory");
                recordOperationHistory("Start logging failed: Cannot create base directory");
                return;
            }

            // 创建新的主日志文件
            if (!fileManager.createNewMainLogFile()) {
                appendOperateSafe("Start logging failed: Cannot create main log file");
                recordOperationHistory("Start logging failed: Cannot create main log file");
                return;
            }

            // 启动系统日志捕获
            systemLogCatcher.setOnLogLineListener(line -> {
                fileManager.appendToMainLog(line.getBytes(), line.length());
            });

            systemLogCatcher.start();
            isRunning = true;
            appendOperateSafe("Logging started successfully");
            recordOperationHistory("Logging started successfully");
        } catch (Exception e) {
            appendOperateSafe("Start logging failed with exception: " + e.getMessage());
            recordOperationHistory("Start logging failed with exception: " + e.getMessage());
        }
    }

    /**
     * 停止日志记录流程
     */
    public void stopLogging() {
        if (!isRunning) {
            appendOperateSafe("Stop logging requested but not running");
            recordOperationHistory("Stop logging requested but not running");
            return;
        }

        try {
            systemLogCatcher.stop();
            isRunning = false;
            appendOperateSafe("Logging stopped successfully");
            recordOperationHistory("Logging stopped successfully");
        } catch (Exception e) {
            appendOperateSafe("Stop logging failed with exception: " + e.getMessage());
            recordOperationHistory("Stop logging failed with exception: " + e.getMessage());
        }
    }

    /**
     * 安全地追加操作记录
     * @param operation 操作描述
     */
    public void appendOperateSafe(String operation) {
        try {
            String timestamp = java.text.DateFormat.getDateTimeInstance().format(new java.util.Date());
            String logEntry = "[" + timestamp + "] " + operation + "\n";
            fileManager.appendOperateHistory(logEntry);
        } catch (Exception e) {
            // 静默处理，避免递归错误
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
            // 静默处理，避免影响主功能
        }
    }

    /**
     * 获取文件管理器实例
     * @return FileManager实例
     */
    public FileManager getFileManager() {
        return fileManager;
    }

    /**
     * 获取日志捕获器实例
     * @return SystemLogCatcher实例
     */
    public SystemLogCatcher getSystemLogCatcher() {
        return systemLogCatcher;
    }

    /**
     * 检查是否正在运行
     * @return 运行状态
     */
    public boolean isRunning() {
        return isRunning;
    }
}