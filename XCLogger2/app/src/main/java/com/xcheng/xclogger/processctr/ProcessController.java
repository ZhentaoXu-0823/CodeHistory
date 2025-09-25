package com.xcheng.xclogger.processctr;

import android.content.Context;
import android.util.Log;

import com.xcheng.xclogger.filemanager.FileManager;
import com.xcheng.xclogger.recorder.SystemLogCatcher;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

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
 * - isRunning() - 检查运行状态
 */
public class ProcessController {
    private static final String TAG = "ProcessController";
    private static volatile ProcessController instance;

    private FileManager fileManager;
    private SystemLogCatcher systemLogCatcher;
    private Context context;
    private boolean isRunning = false;

    /**
     * 私有构造函数，初始化流程控制器
     * @param ctx Android上下文
     */
    private ProcessController(Context ctx) {
        this.context = ctx;
        this.fileManager = new FileManager(ctx);
        this.systemLogCatcher = new SystemLogCatcher();

        Log.i(TAG, "ProcessController initialized");
    }

    /**
     * 获取单例实例
     * @param ctx Android上下文
     * @return ProcessController实例
     */
    public static ProcessController getInstance(Context ctx) {
        if (instance == null) {
            synchronized (ProcessController.class) {
                if (instance == null) {
                    instance = new ProcessController(ctx);
                }
            }
        }
        return instance;
    }

    /**
     * 启动日志记录流程
     */
    public void startLogging() {
        if (isRunning) {
            Log.w(TAG, "Logging is already running");
            return;
        }

        try {
            // 检查配置是否可用
            XcLoggerConfig config = ConfigLoader.current();
            if (config == null) {
                Log.e(TAG, "Configuration is not available");
                return;
            }

            // 确保基础目录存在
            if (!fileManager.ensureBaseDir()) {
                Log.e(TAG, "Failed to ensure base directory");
                return;
            }

            // 创建新的主日志文件
            if (!fileManager.createNewMainLogFile()) {
                Log.e(TAG, "Failed to create main log file");
                return;
            }

            // 设置日志行监听器
            systemLogCatcher.setOnLogLineListener(line -> {
                try {
                    fileManager.appendToMainLog(line.getBytes("UTF-8"), line.length());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to append log line", e);
                }
            });

            // 启动系统日志捕获
            systemLogCatcher.start();
            isRunning = true;

            // 保存运行状态到数据库
            XcLoggerDatabase database = new XcLoggerDatabase(context);
            database.saveRunningState(true);

            // 记录操作历史
            recordOperationHistory("Logging started successfully");

            Log.i(TAG, "Logging started successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to start logging", e);
            isRunning = false;
        }
    }

    /**
     * 停止日志记录流程
     */
    public void stopLogging() {
        if (!isRunning) {
            Log.w(TAG, "Logging is not running");
            return;
        }

        try {
            // 停止系统日志捕获
            systemLogCatcher.stop();
            isRunning = false;

            // 保存运行状态到数据库
            XcLoggerDatabase database = new XcLoggerDatabase(context);
            database.saveRunningState(false);

            // 记录操作历史
            recordOperationHistory("Logging stopped successfully");

            Log.i(TAG, "Logging stopped successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to stop logging", e);
        }
    }

    /**
     * 安全地追加操作记录
     * @param operation 操作记录
     */
    public void appendOperateSafe(String operation) {
        try {
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            String logEntry = "[" + timestamp + "] " + operation + "\n";

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

    /**
     * 获取日志捕获器实例
     * @return SystemLogCatcher实例
     */
    public SystemLogCatcher getSystemLogCatcher() {
        return systemLogCatcher;
    }

    /**
     * 检查运行状态
     * @return 是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
    }
}