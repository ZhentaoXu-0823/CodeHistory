package com.xcheng.xclogger.filemanager;

import android.content.Context;

import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.util.XcLoggerConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * FileManager - 文件管理模块，负责日志文件的创建、写入和轮转
 *
 * 功能方法：
 * - FileManager(Context) - 构造函数，初始化文件管理器
 * - ensureBaseDir() - 确保基础目录存在
 * - createNewMainLogFile() - 创建新的主日志文件
 * - appendToMainLog(byte[], int) - 追加数据到主日志文件
 * - appendOperateHistory(String) - 追加操作历史记录
 * - rotateIfNeeded(int) - 检查并执行文件轮转
 * - getCurrentMainLogFile() - 获取当前主日志文件
 * - getBaseDir() - 获取基础目录
 */
public class FileManager {
    private Context context;
    private File baseDir;
    private File currentMainLogFile;
    private File historyFile;
    private XcLoggerConfig config;

    /**
     * 构造函数，初始化文件管理器
     * @param ctx Android上下文
     */
    public FileManager(Context ctx) {
        this.context = ctx;
        this.config = ConfigLoader.current();
        if (this.config == null) {
            this.config = new XcLoggerConfig();
            this.config.setLogDir("/storage/emulated/0/sdcard/XcLogger");
        }
        this.baseDir = new File(this.config.getLogDir());
        this.historyFile = new File(this.baseDir, "XcLoggerOperateHistory.txt");
    }

    /**
     * 确保基础目录存在
     * @return 是否成功创建或目录已存在
     */
    public boolean ensureBaseDir() {
        try {
            if (!baseDir.exists()) {
                return baseDir.mkdirs();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 创建新的主日志文件
     * @return 是否创建成功
     */
    public boolean createNewMainLogFile() {
        try {
            if (!ensureBaseDir()) {
                return false;
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "main_log_" + timestamp + ".txt";
            currentMainLogFile = new File(baseDir, fileName);

            return currentMainLogFile.createNewFile();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 追加数据到主日志文件
     * @param data 要追加的数据
     * @param len 数据长度
     */
    public void appendToMainLog(byte[] data, int len) {
        if (currentMainLogFile == null || !currentMainLogFile.exists()) {
            return;
        }

        try (FileOutputStream fos = new FileOutputStream(currentMainLogFile, true)) {
            fos.write(data, 0, len);
            fos.flush();

            // 检查是否需要轮转
            rotateIfNeeded(len);
        } catch (IOException e) {
            // 记录错误但不抛出异常
        }
    }

    /**
     * 追加操作历史记录
     * @param operation 操作记录
     */
    public void appendOperateHistory(String operation) {
        try {
            if (!ensureBaseDir()) {
                return;
            }

            try (FileOutputStream fos = new FileOutputStream(historyFile, true)) {
                fos.write(operation.getBytes("UTF-8"));
                fos.flush();
            }
        } catch (IOException e) {
            // 静默处理
        }
    }

    /**
     * 检查并执行文件轮转
     * @param additionalBytes 即将添加的字节数
     */
    private void rotateIfNeeded(int additionalBytes) {
        if (currentMainLogFile == null || !currentMainLogFile.exists()) {
            return;
        }

        long currentSize = currentMainLogFile.length();
        long maxSize = config.getFileSizeMb() * 1024 * 1024; // 转换为字节

        if (currentSize + additionalBytes > maxSize) {
            // 创建新文件
            createNewMainLogFile();
        }
    }

    /**
     * 获取当前主日志文件
     * @return 当前主日志文件
     */
    public File getCurrentMainLogFile() {
        return currentMainLogFile;
    }

    /**
     * 获取基础目录
     * @return 基础目录
     */
    public File getBaseDir() {
        return baseDir;
    }
}