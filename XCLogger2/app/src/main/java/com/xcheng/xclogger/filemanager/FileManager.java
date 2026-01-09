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
    private static final long MIN_FILE_LIFETIME_MS = 10 * 1000; // 10秒最小文件生存时间，防止过度轮转

    // 操作历史路径的固定默认值（首次安装时使用）
    private static final String DEFAULT_OPERATION_HISTORY_PATH = "/storage/emulated/0/XcLogger";

    // 核心组件
    private Context context;
    private XcLoggerConfig config;

    // 文件路径相关
    private File operationHistoryDir; // 操作历史目录（固定路径，首次初始化后不再变更）
    private File mainLogDir; // 主日志目录（动态路径，可随配置变更）
    private File operationHistoryFile;
    private File currentMainLogFile;

    // 文件状态
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

        // 初始化操作历史目录（固定路径，首次初始化后不再变更）
        initOperationHistoryDir();

        // 初始化主日志目录（动态路径，可随配置变更）
        initMainLogDir();

        Log.i(TAG, "FileManager initialized - operationHistoryDir: " + operationHistoryDir.getAbsolutePath() +
                ", mainLogDir: " + mainLogDir.getAbsolutePath());
    }

    /**
     * 初始化操作历史目录（固定路径）
     *
     * 说明：
     * - 操作历史路径在首次初始化时确定，之后不再变更
     * - 如果数据库中没有保存操作历史路径，使用固定的默认路径
     * - 一旦保存到数据库，即使日志保存路径变更，操作历史路径也保持不变
     */
    private void initOperationHistoryDir() {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(context);
            String operationHistoryPath = db.getOperationHistoryPath();

            if (operationHistoryPath == null || operationHistoryPath.isEmpty()) {
                // 首次初始化：使用固定的默认路径（不从当前配置获取）
                operationHistoryPath = getDefaultOperationHistoryPath();
                db.setOperationHistoryPath(operationHistoryPath);
                Log.i(TAG, "Operation history path initialized to default: " + operationHistoryPath);
            } else {
                Log.i(TAG, "Operation history path loaded from database: " + operationHistoryPath);
            }

            // 使用目录路径创建操作历史目录
            this.operationHistoryDir = new File(operationHistoryPath);

            // 确保目录存在
            if (!operationHistoryDir.exists()) {
                boolean created = operationHistoryDir.mkdirs();
                if (created) {
                    Log.i(TAG, "Operation history directory created: " + operationHistoryDir.getAbsolutePath());
                    appendOperationHistory("Operation history directory created: " + operationHistoryDir.getAbsolutePath());
                } else {
                    Log.e(TAG, "Failed to create operation history directory: " + operationHistoryDir.getAbsolutePath());
                }
            }

            // 构造操作历史文件路径
            this.operationHistoryFile = new File(operationHistoryDir, OPERATION_HISTORY_FILE);

            Log.i(TAG, "Operation history directory: " + operationHistoryDir.getAbsolutePath());
            Log.i(TAG, "Operation history file: " + operationHistoryFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error initializing operation history directory", e);
            // 降级处理：使用固定默认路径
            this.operationHistoryDir = new File(DEFAULT_OPERATION_HISTORY_PATH);
            if (!operationHistoryDir.exists()) {
                operationHistoryDir.mkdirs();
            }
            this.operationHistoryFile = new File(operationHistoryDir, OPERATION_HISTORY_FILE);
        }
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
            boolean created = mainLogDir.mkdirs();
            if (created) {
                Log.i(TAG, "Main log directory created: " + mainLogDir.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to create main log directory: " + mainLogDir.getAbsolutePath());
            }
        }
    }

    /**
     * 获取默认操作历史路径（固定默认值）
     *
     * 修复说明：
     * - 返回固定的默认路径，不从当前配置获取
     * - 确保操作历史路径在首次初始化时使用原始路径，之后不再变更
     *
     * @return 默认操作历史目录路径
     */
    private String getDefaultOperationHistoryPath() {
        // 返回固定的默认路径，不从当前配置获取
        // 这样即使配置已经变更，首次初始化时也会使用原始路径
        return DEFAULT_OPERATION_HISTORY_PATH;
    }

    /**
     * 动态更新文件路径
     *
     * 修复说明：
     * - 只更新主日志目录路径（mainLogDir）
     * - 操作历史目录路径保持不变（operationHistoryDir）
     * - 重置 currentMainLogFile 为 null，确保下次写入时创建新文件
     * - 重置 currentFileStartTime，避免文件时间统计错误
     * - 重置 currentDate，确保序列号计算正确
     * - 记录路径变更操作历史，便于追踪
     */
    public void updatePaths() {
        try {
            // 更新配置
            this.config = ConfigLoader.getInstance().getCurrentConfig();

            String oldLogDir = mainLogDir != null ? mainLogDir.getAbsolutePath() : "null";

            // 更新主日志目录（动态路径）
            if (config != null && config.getLogDir() != null) {
                File newMainLogDir = new File(config.getLogDir());

                // 检查路径是否发生变化
                if (!newMainLogDir.equals(mainLogDir)) {
                    String newLogDir = newMainLogDir.getAbsolutePath();

                    // 更新主日志目录
                    this.mainLogDir = newMainLogDir;

                    // 确保新目录存在
                    if (!mainLogDir.exists()) {
                        boolean created = mainLogDir.mkdirs();
                        if (created) {
                            appendOperationHistory("Main log directory created: " + mainLogDir.getAbsolutePath());
                        } else {
                            Log.e(TAG, "Failed to create main log directory: " + mainLogDir.getAbsolutePath());
                            appendOperationHistory("Failed to create main log directory: " + mainLogDir.getAbsolutePath());
                        }
                    }

                    // 重置文件状态（关键修复点）
                    this.currentMainLogFile = null;
                    this.currentFileStartTime = 0;
                    this.currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());

                    // 记录路径变更操作历史（记录到操作历史文件，该文件路径保持不变）
                    appendOperationHistory("Main log directory path changed: " + oldLogDir + " -> " + newLogDir);

                    Log.i(TAG, "Main log directory updated to: " + mainLogDir.getAbsolutePath());
                    Log.i(TAG, "File state reset: currentMainLogFile=null, currentFileStartTime=0");
                    Log.i(TAG, "Operation history directory remains unchanged: " + operationHistoryDir.getAbsolutePath());
                } else {
                    Log.d(TAG, "Main log directory path unchanged: " + mainLogDir.getAbsolutePath());
                }
            } else {
                Log.w(TAG, "Config or log_dir is null, cannot update paths");
            }

            // 操作历史目录保持不变（固定路径，不随配置变更）
            // 这是设计上的要求：操作历史文件路径在首次初始化后不再变更
        } catch (Exception e) {
            Log.e(TAG, "Error updating paths", e);
            appendOperationHistory("Error updating paths: " + e.getMessage());
        }
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

                // 记录文件创建操作（记录到操作历史文件）
                appendOperationHistory("Log file created: " + currentMainLogFile.getAbsolutePath() + " (size: 0 bytes)");

                return currentMainLogFile;
            } else {
                Log.e(TAG, "Failed to create new log file: " + fileName);
                appendOperationHistory("Failed to create log file: " + fileName + " (file may already exist)");
                return null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error creating new log file", e);
            appendOperationHistory("Error creating new log file: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error creating new log file", e);
            appendOperationHistory("Unexpected error creating new log file: " + e.getMessage());
            return null;
        }
    }

    /**
     * 追加数据到主日志文件
     * @param data 要追加的数据
     * @param len 数据长度
     */
    public synchronized void appendToMainLog(byte[] data, int len) {
        try {
            if (data == null || len <= 0) {
//                Log.w(TAG, "appendToMainLog called with null data or zero length");
                return;
            }

            // 确保文件存在，如果不存在则创建
            if (currentMainLogFile == null || !currentMainLogFile.exists()) {
                File newFile = createNewMainLogFile();
                if (newFile == null) {
                    Log.e(TAG, "Cannot append data: failed to create log file");
                    return;
                }
            }

            // 检查文件是否在新路径下（防止路径变更后仍写入旧文件）
            if (!currentMainLogFile.getParentFile().equals(mainLogDir)) {
                Log.w(TAG, "Current log file is not in the current log directory, creating new file");
                File newFile = createNewMainLogFile();
                if (newFile == null) {
                    Log.e(TAG, "Cannot append data: failed to create log file in new directory");
                    return;
                }
            }

            // 检查是否需要轮转文件
            if (shouldRotateFile()) {
                Log.i(TAG, "Rotating log file due to size limit");
                appendOperationHistory("Log file rotated due to size limit: " + currentMainLogFile.getName());
                File newFile = createNewMainLogFile();
                if (newFile == null) {
                    Log.e(TAG, "Cannot append data: failed to create new file after rotation");
                    return;
                }
            }

            // 写入数据
            long fileSizeBefore = currentMainLogFile.length();
            try (FileOutputStream fos = new FileOutputStream(currentMainLogFile, true)) {
                fos.write(data, 0, len);
                fos.flush();
            }
            long fileSizeAfter = currentMainLogFile.length();

//            Log.d(TAG, "Data written to file: " + currentMainLogFile.getName() +
//                  ", bytes written: " + len +
//                  ", file size: " + fileSizeBefore + " -> " + fileSizeAfter);
        } catch (IOException e) {
            Log.e(TAG, "Error writing to log file", e);
            appendOperationHistory("Error writing to log file: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error appending to log file", e);
            appendOperationHistory("Unexpected error appending to log file: " + e.getMessage());
        }
    }

    /**
     * 追加操作历史记录
     *
     * 说明：
     * - 操作历史文件路径在首次初始化后保持不变
     * - 所有操作历史都记录到固定的操作历史文件中
     *
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
                    boolean created = operationHistoryFile.getParentFile().mkdirs();
                    if (!created) {
                        Log.e(TAG, "Failed to create operation history directory: " + operationHistoryFile.getParentFile().getAbsolutePath());
                        return;
                    }
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
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error writing operation history", e);
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
        try {
            // 1. 检查空间限制
            if (needSpaceForNewFile()) {
                deleteOldestFileForSpace();
            }

            // 2. 检查时间限制
            deleteFilesExceedingTimeLimit(newFileTime);
        } catch (Exception e) {
            Log.e(TAG, "Error checking and cleaning files", e);
        }
    }

    /**
     * 检查是否需要为新文件腾出空间
     * @return 是否需要腾出空间
     */
    private boolean needSpaceForNewFile() {
        if (config == null) {
            return false;
        }

        try {
            long currentSize = getTotalLogSize();
            long maxFileSize = config.getFileSizeMb() * 1024L * 1024L;
            long totalSizeLimit = config.getTotalSizeGb() * 1024L * 1024L * 1024L;

            return (currentSize + maxFileSize) > totalSizeLimit;
        } catch (Exception e) {
            Log.e(TAG, "Error checking space for new file", e);
            return false;
        }
    }

    /**
     * 删除最旧文件以腾出空间
     */
    private void deleteOldestFileForSpace() {
        try {
            File[] files = getLogFilesSortedByTime();
            if (files != null && files.length > 0) {
                File oldestFile = files[0];
                long fileSize = oldestFile.length();
                if (oldestFile.delete()) {
                    Log.i(TAG, "Deleted oldest file for space: " + oldestFile.getName() + " (size: " + fileSize + " bytes)");
                    appendOperationHistory("Log file deleted (storage/size limit): " + oldestFile.getAbsolutePath() + " (size: " + fileSize + " bytes)");
                } else {
                    Log.w(TAG, "Failed to delete oldest file: " + oldestFile.getName());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting oldest file for space", e);
        }
    }

    /**
     * 删除超出时间限制的文件
     * @param newFileTime 新文件创建时间
     */
    private void deleteFilesExceedingTimeLimit(long newFileTime) {
        if (config == null) {
            return;
        }

        try {
            long timeLimitMs = config.getLogPeriodHours() * 60L * 60L * 1000L;
            File[] files = getLogFilesSortedByTime();

            if (files != null) {
                for (File file : files) {
                    long timeDiff = newFileTime - file.lastModified();
                    if (timeDiff > timeLimitMs) {
                        long fileSize = file.length();
                        if (file.delete()) {
                            Log.i(TAG, "Deleted file exceeding time limit: " + file.getName() + " (time diff: " + (timeDiff / 1000 / 60) + " minutes)");
                            appendOperationHistory("Log file deleted (time limit): " + file.getAbsolutePath() + " (size: " + fileSize + " bytes)");
                        } else {
                            Log.w(TAG, "Failed to delete file exceeding time limit: " + file.getName());
                        }
                    } else {
                        break; // 找到第一个满足时间限制的文件就停止
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting files exceeding time limit", e);
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
            return (availableBytes * 100 / totalBytes) < 5; // 5%阈值
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
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error getting total log size", e);
            return 0;
        }
    }

    /**
     * 获取按时间排序的日志文件列表
     * @return 按时间排序的文件数组
     */
    private File[] getLogFilesSortedByTime() {
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error getting log files sorted by time", e);
            return new File[0];
        }
    }

    /**
     * 检查是否需要轮转文件
     * @return 是否需要轮转
     */
    private boolean shouldRotateFile() {
        if (currentMainLogFile == null || !currentMainLogFile.exists() || config == null) {
            return false;
        }

        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error checking if file should rotate", e);
            return false;
        }
    }

    /**
     * 生成日志文件名
     * @return 文件名
     */
    private String generateFileName() {
        try {
            String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
            String time = new SimpleDateFormat("HHmmss", Locale.getDefault()).format(new Date());
            int sequence = getSequenceNumber();

            return LOG_FILE_PREFIX + today + "_" + time + "_" + String.format("%04d", sequence) + LOG_FILE_EXTENSION;
        } catch (Exception e) {
            Log.e(TAG, "Error generating file name", e);
            // 降级处理：使用时间戳作为文件名
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            return LOG_FILE_PREFIX + timestamp + "_0001" + LOG_FILE_EXTENSION;
        }
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
                return 1; // 新的一天，序列号重置为1
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