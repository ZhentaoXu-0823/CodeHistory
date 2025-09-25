package com.xcheng.xclogger.filemanager;

import android.content.Context;
import android.os.StatFs;
import android.util.Log;

import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.util.XcLoggerConfig;
import com.xcheng.xclogger.util.XcLoggerDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * FileManager - 文件管理器，负责日志文件的创建、写入、轮转和清理
 *
 * 功能方法：
 * - FileManager(Context) - 构造函数，初始化文件管理器
 * - updatePaths() - 动态更新文件路径
 * - createNewMainLogFile() - 创建新的主日志文件
 * - appendToMainLog(byte[], int) - 追加数据到主日志文件
 * - appendOperationHistory(String) - 追加操作历史记录
 * - appendConfigChangeHistory(String) - 追加配置变更历史
 * - checkAndCleanBeforeNewFile(long) - 检查并清理文件（基于新文件时间）
 * - needSpaceForNewFile() - 检查是否需要为新文件腾出空间
 * - deleteOldestFileForSpace() - 删除最旧文件以腾出空间
 * - deleteFilesExceedingTimeLimit(long) - 删除超出时间限制的文件
 * - isStorageFull() - 检查系统存储空间是否已满
 * - getTotalLogSize() - 获取日志文件总大小
 * - getLogFilesSortedByTime() - 获取按时间排序的日志文件列表
 * - shouldRotateFile() - 检查是否需要轮转文件
 * - generateFileName() - 生成日志文件名
 * - getSequenceNumber() - 获取序列号
 * - getCurrentMainLogFile() - 获取当前主日志文件
 * - getMainLogDir() - 获取主日志目录
 * - getOperationHistoryFile() - 获取操作历史文件
 */
public class FileManager {
    private static final String TAG = "FileManager";
    private static final String LOG_FILE_PREFIX = "mainlog_";
    private static final String LOG_FILE_EXTENSION = ".txt";
    private static final String OPERATION_HISTORY_FILE = "A_OperationHistory.txt";
    private static final long MIN_FILE_LIFETIME_MS = 10 * 1000; // 10秒最小文件生存时间

    private Context context;
    private XcLoggerConfig config;
    private File operationHistoryDir;
    private File mainLogDir;
    private File operationHistoryFile;
    private File currentMainLogFile;
    private long currentFileStartTime;
    private String currentDate;

    /**
     * 构造函数，初始化文件管理器
     * @param context Android上下文
     */
    public FileManager(Context context) {
        this.context = context;
        this.config = ConfigLoader.getInstance().getCurrentConfig();
        this.currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());

        // 初始化操作历史目录（固定路径）
        initOperationHistoryDir();

        // 初始化主日志目录（动态路径）
        initMainLogDir();

        Log.i(TAG, "FileManager initialized - operationHistoryDir: " + operationHistoryDir.getAbsolutePath() +
                ", mainLogDir: " + mainLogDir.getAbsolutePath());
    }

    /**
     * 初始化操作历史目录（固定路径）
     */
    private void initOperationHistoryDir() {
        XcLoggerDatabase db = new XcLoggerDatabase(context);
        String operationHistoryPath = db.getOperationHistoryPath();

        if (operationHistoryPath == null || operationHistoryPath.isEmpty()) {
            // 从默认配置获取路径
            operationHistoryPath = getDefaultOperationHistoryPath();
            db.setOperationHistoryPath(operationHistoryPath);
        }

        // 使用目录路径创建操作历史目录
        this.operationHistoryDir = new File(operationHistoryPath);

        // 确保目录存在
        if (!operationHistoryDir.exists()) {
            operationHistoryDir.mkdirs();
        }

        // 构造操作历史文件路径
        this.operationHistoryFile = new File(operationHistoryDir, OPERATION_HISTORY_FILE);

        Log.i(TAG, "Operation history directory: " + operationHistoryDir.getAbsolutePath());
        Log.i(TAG, "Operation history file: " + operationHistoryFile.getAbsolutePath());
    }

    /**
     * 初始化主日志目录（动态路径）
     */
    private void initMainLogDir() {
        if (config != null && config.getLogDir() != null) {
            this.mainLogDir = new File(config.getLogDir());
        } else {
            // 使用默认路径
            this.mainLogDir = new File("/storage/emulated/0/XcLogger");
        }

        // 确保目录存在
        if (!mainLogDir.exists()) {
            mainLogDir.mkdirs();
        }
    }

    /**
     * 获取默认操作历史路径（目录路径）
     * @return 默认操作历史目录路径
     */
    private String getDefaultOperationHistoryPath() {
        if (config != null && config.getLogDir() != null) {
            return config.getLogDir(); // 返回目录路径，不包含文件名
        }
        return "/storage/emulated/0/XcLogger"; // 返回目录路径
    }

    /**
     * 动态更新文件路径
     */
    public void updatePaths() {
        // 更新配置
        this.config = ConfigLoader.getInstance().getCurrentConfig();

        // 更新主日志目录
        if (config != null && config.getLogDir() != null) {
            File newMainLogDir = new File(config.getLogDir());
            if (!newMainLogDir.equals(mainLogDir)) {
                this.mainLogDir = newMainLogDir;
                if (!mainLogDir.exists()) {
                    mainLogDir.mkdirs();
                }
                Log.i(TAG, "Main log directory updated to: " + mainLogDir.getAbsolutePath());
            }
        }

        // 操作历史目录保持不变（固定路径）
    }

    /**
     * 创建新的主日志文件
     * @return 新创建的主日志文件
     */
    public synchronized File createNewMainLogFile() {
        try {
            long newFileTime = System.currentTimeMillis();

            // 检查并清理旧文件
            checkAndCleanBeforeNewFile(newFileTime);

            // 生成新文件名
            String fileName = generateFileName();
            currentMainLogFile = new File(mainLogDir, fileName);

            // 创建新文件
            if (currentMainLogFile.createNewFile()) {
                currentFileStartTime = newFileTime;
                Log.i(TAG, "Created new log file: " + fileName);

                // 记录文件创建操作
                appendOperationHistory("Log file created: " + currentMainLogFile.getAbsolutePath() + " (size: 0 bytes)");

                return currentMainLogFile;
            } else {
                Log.e(TAG, "Failed to create new log file: " + fileName);
                return null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error creating new log file", e);
            return null;
        }
    }

    /**
     * 追加数据到主日志文件
     * @param data 要追加的数据
     * @param len 数据长度
     */
    public synchronized void appendToMainLog(byte[] data, int len) {
        if (currentMainLogFile == null || !currentMainLogFile.exists()) {
            createNewMainLogFile();
        }

        // 检查是否需要轮转文件
        if (shouldRotateFile()) {
            Log.i(TAG, "Rotating log file due to size limit");
            createNewMainLogFile();
        }

        // 写入数据
        try (FileOutputStream fos = new FileOutputStream(currentMainLogFile, true)) {
            fos.write(data, 0, len);
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to log file", e);
        }
    }

    /**
     * 追加操作历史记录
     * @param operation 操作记录
     */
    public void appendOperationHistory(String operation) {
        try {
            // 确保操作历史文件路径正确
            if (operationHistoryFile == null) {
                initOperationHistoryDir();
            }

            // 检查文件路径是否有效
            if (operationHistoryFile != null && operationHistoryFile.getParentFile() != null) {
                // 确保父目录存在
                if (!operationHistoryFile.getParentFile().exists()) {
                    operationHistoryFile.getParentFile().mkdirs();
                }

                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                String logEntry = "[" + timestamp + "] " + operation + "\n";

                try (FileOutputStream fos = new FileOutputStream(operationHistoryFile, true)) {
                    fos.write(logEntry.getBytes());
                    fos.flush();
                }
            } else {
                Log.e(TAG, "Invalid operation history file path");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error writing operation history", e);
        }
    }

    /**
     * 追加配置变更历史
     * @param configDetails 配置详情
     */
    public void appendConfigChangeHistory(String configDetails) {
        appendOperationHistory("Config updated: " + configDetails);
    }

    /**
     * 检查并清理文件（基于新文件时间）
     * @param newFileTime 新文件创建时间
     */
    private void checkAndCleanBeforeNewFile(long newFileTime) {
        // 1. 检查空间限制
        if (needSpaceForNewFile()) {
            deleteOldestFileForSpace();
        }

        // 2. 检查时间限制
        deleteFilesExceedingTimeLimit(newFileTime);
    }

    /**
     * 检查是否需要为新文件腾出空间
     * @return 是否需要腾出空间
     */
    private boolean needSpaceForNewFile() {
        long currentSize = getTotalLogSize();
        long maxFileSize = config.getFileSizeMb() * 1024L * 1024L;
        long totalSizeLimit = config.getTotalSizeGb() * 1024L * 1024L * 1024L;

        return (currentSize + maxFileSize) > totalSizeLimit;
    }

    /**
     * 删除最旧文件以腾出空间
     */
    private void deleteOldestFileForSpace() {
        File[] files = getLogFilesSortedByTime();
        if (files.length > 0) {
            File oldestFile = files[0];
            long fileSize = oldestFile.length();
            if (oldestFile.delete()) {
                Log.i(TAG, "Deleted oldest file for space: " + oldestFile.getName() + " (size: " + fileSize + " bytes)");
                appendOperationHistory("Log file deleted (storage/size limit): " + oldestFile.getAbsolutePath() + " (size: " + fileSize + " bytes)");
            }
        }
    }

    /**
     * 删除超出时间限制的文件
     * @param newFileTime 新文件创建时间
     */
    private void deleteFilesExceedingTimeLimit(long newFileTime) {
        long timeLimitMs = config.getLogPeriodHours() * 60L * 60L * 1000L;
        File[] files = getLogFilesSortedByTime();

        for (File file : files) {
            long timeDiff = newFileTime - file.lastModified();
            if (timeDiff > timeLimitMs) {
                long fileSize = file.length();
                if (file.delete()) {
                    Log.i(TAG, "Deleted file exceeding time limit: " + file.getName() + " (time diff: " + (timeDiff / 1000 / 60) + " minutes)");
                    appendOperationHistory("Log file deleted (time limit): " + file.getAbsolutePath() + " (size: " + fileSize + " bytes)");
                }
            } else {
                break; // 找到第一个满足时间限制的文件就停止
            }
        }
    }

    /**
     * 检查系统存储空间是否已满
     * @return 是否已满
     */
    private boolean isStorageFull() {
        try {
            StatFs stat = new StatFs(mainLogDir.getAbsolutePath());
            long availableBytes = stat.getAvailableBytes();
            long totalBytes = stat.getTotalBytes();

            // 如果可用空间小于总空间的5%，认为存储已满
            return (availableBytes * 100 / totalBytes) < 5;
        } catch (Exception e) {
            Log.e(TAG, "Error checking storage space", e);
            return false;
        }
    }

    /**
     * 获取日志文件总大小
     * @return 总大小（字节）
     */
    private long getTotalLogSize() {
        long totalSize = 0;
        File[] files = mainLogDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().startsWith(LOG_FILE_PREFIX) && file.getName().endsWith(LOG_FILE_EXTENSION)) {
                    totalSize += file.length();
                }
            }
        }
        return totalSize;
    }

    /**
     * 获取按时间排序的日志文件列表
     * @return 按时间排序的文件数组
     */
    private File[] getLogFilesSortedByTime() {
        File[] files = mainLogDir.listFiles();
        if (files == null) {
            return new File[0];
        }

        // 过滤出日志文件
        File[] logFiles = Arrays.stream(files)
                .filter(file -> file.getName().startsWith(LOG_FILE_PREFIX) && file.getName().endsWith(LOG_FILE_EXTENSION))
                .toArray(File[]::new);

        // 按修改时间排序（最旧的在前）
        Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));

        return logFiles;
    }

    /**
     * 检查是否需要轮转文件
     * @return 是否需要轮转
     */
    private boolean shouldRotateFile() {
        if (currentMainLogFile == null || !currentMainLogFile.exists()) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long fileAge = currentTime - currentFileStartTime;
        long fileSize = currentMainLogFile.length();
        long maxFileSize = config.getFileSizeMb() * 1024L * 1024L;

        // 如果文件大小超过限制
        if (fileSize > maxFileSize) {
            // 如果文件生存时间小于最小生存时间，记录警告
            if (fileAge < MIN_FILE_LIFETIME_MS) {
                Log.w(TAG, "File size limit reached within minimum lifetime, forcing rotation");
            } else {
                Log.i(TAG, "File size limit reached, rotating file");
            }
            return true;
        }

        return false;
    }

    /**
     * 生成日志文件名
     * @return 文件名
     */
    private String generateFileName() {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String time = new SimpleDateFormat("HHmmss", Locale.getDefault()).format(new Date());
        int sequence = getSequenceNumber();

        return LOG_FILE_PREFIX + today + "_" + time + "_" + String.format("%04d", sequence) + LOG_FILE_EXTENSION;
    }

    /**
     * 获取序列号
     * @return 序列号
     */
    private int getSequenceNumber() {
        try {
            String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
            if (!today.equals(currentDate)) {
                currentDate = today;
                return 1;
            }

            File[] files = mainLogDir.listFiles();
            int maxSequence = 0;

            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    if (name.startsWith(LOG_FILE_PREFIX) && name.contains(today)) {
                        try {
                            // 正确的解析逻辑：mainlog_yyyyMMdd_HHmmss_sequence.txt
                            String[] parts = name.split("_");
                            if (parts.length >= 4) { // 应该是4个部分
                                String sequenceStr = parts[3].replace(LOG_FILE_EXTENSION, "");
                                // 检查是否是数字
                                if (sequenceStr.matches("\\d+")) {
                                    int sequence = Integer.parseInt(sequenceStr);
                                    maxSequence = Math.max(maxSequence, sequence);
                                } else {
                                    Log.w(TAG, "Invalid sequence format in filename: " + name + ", sequence part: " + sequenceStr);
                                }
                            }
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Failed to parse sequence from filename: " + name);
                            // 忽略解析错误
                        }
                    }
                }
            }

            Log.d(TAG, "Max sequence found: " + maxSequence + ", next sequence: " + (maxSequence + 1));
            return maxSequence + 1;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get sequence number", e);
            return 1;
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
     * 获取主日志目录
     * @return 主日志目录
     */
    public File getMainLogDir() {
        return mainLogDir;
    }

    /**
     * 获取操作历史文件
     * @return 操作历史文件
     */
    public File getOperationHistoryFile() {
        return operationHistoryFile;
    }
}