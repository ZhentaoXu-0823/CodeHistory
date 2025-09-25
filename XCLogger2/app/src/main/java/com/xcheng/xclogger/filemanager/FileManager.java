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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * FileManager - 文件管理模块，负责日志文件的创建、写入和轮转
 *
 * 功能方法：
 * - FileManager(Context) - 构造函数，初始化文件管理器
 * - updatePaths() - 更新文件路径
 * - ensureBaseDir() - 确保基础目录存在
 * - createNewMainLogFile() - 创建新的主日志文件
 * - appendToMainLog(byte[], int) - 追加数据到主日志文件
 * - appendOperationHistory(String) - 追加操作历史记录到A_OperationHistory.txt
 * - appendConfigChangeHistory(String) - 追加配置修改记录（键值对格式）
 * - checkAndCleanOldFiles() - 检查并清理过期文件
 * - isStorageFull() - 检查存储空间是否已满
 * - getTotalLogSize() - 获取日志文件总大小
 * - deleteFilesOlderThan(int) - 删除超过指定小时数的文件
 * - deleteOldestFiles() - 删除最老的文件
 * - getLogFilesSortedByTime() - 获取按时间排序的日志文件列表
 * - generateFileName() - 生成日志文件名
 * - getSequenceNumber() - 获取序列号
 * - shouldRotateFile(int) - 检查是否应该轮转文件
 * - getCurrentMainLogFile() - 获取当前主日志文件
 * - getMainLogDir() - 获取主日志目录
 * - getOperationHistoryFile() - 获取操作历史文件
 */
public class FileManager {
    private static final String TAG = "FileManager";
    private static final String LOG_FILE_PREFIX = "mainlog_";
    private static final String LOG_FILE_EXTENSION = ".txt";
    private static final String OPERATION_HISTORY_FILE = "A_OperationHistory.txt";
    private static final int HASH_LENGTH = 6;
    private static final int SEQUENCE_LENGTH = 4;
    private static final long MIN_FILE_LIFETIME_MS = 10000; // 最小文件生存时间：10秒

    private Context context;
    private File operationHistoryDir;  // 操作历史文件目录（固定不变）
    private File mainLogDir;           // 主日志文件目录（可修改）
    private File currentMainLogFile;
    private File operationHistoryFile;
    private XcLoggerConfig config;
    private String currentDate;
    private int currentSequenceNumber;
    private long lastFileCreationTime = 0;

    /**
     * 构造函数，初始化文件管理器
     * @param ctx Android上下文
     */
    public FileManager(Context ctx) {
        this.context = ctx;
        this.config = ConfigLoader.getInstance().getCurrentConfig();

        // 如果配置为null，尝试重新加载
        if (this.config == null) {
            Log.w(TAG, "Config is null, attempting to reload");
            ConfigLoader.getInstance().load(ctx);
            this.config = ConfigLoader.getInstance().getCurrentConfig();
        }

        // 如果仍然为null，使用默认配置
        if (this.config == null) {
            Log.e(TAG, "Failed to load config, using default values");
            this.config = new XcLoggerConfig();
            this.config.setLogDir("/storage/emulated/0/XcLogger");
            this.config.setFileSizeMb(4);
            this.config.setTotalSizeGb(4);
            this.config.setBufferSizeBytes(1024);
            this.config.setLogPeriodHours(168);
        }

        // 初始化路径
        initializePaths();

        this.currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        this.currentSequenceNumber = getSequenceNumber();

        Log.i(TAG, "FileManager initialized with operation_history_dir=" + operationHistoryDir.getAbsolutePath() +
                ", main_log_dir=" + mainLogDir.getAbsolutePath() + ", current_sequence=" + this.currentSequenceNumber);
    }

    /**
     * 初始化文件路径
     */
    private void initializePaths() {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(context);

            // 获取操作历史文件路径（固定不变）
            String operationHistoryPath = db.getOperationHistoryPath();
            if (operationHistoryPath == null || operationHistoryPath.isEmpty()) {
                // 如果数据库中没有操作历史文件路径，使用默认路径
                operationHistoryPath = config.getLogDir() + "/" + OPERATION_HISTORY_FILE;
                db.setOperationHistoryPath(operationHistoryPath);
                Log.i(TAG, "Set default operation history path: " + operationHistoryPath);
            }

            this.operationHistoryDir = new File(operationHistoryPath).getParentFile();
            this.operationHistoryFile = new File(operationHistoryPath);

            // 获取主日志文件目录（可修改）
            this.mainLogDir = new File(config.getLogDir());

            Log.i(TAG, "Paths initialized - operation_history_dir: " + operationHistoryDir.getAbsolutePath() +
                    ", main_log_dir: " + mainLogDir.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize paths", e);
            // 使用默认路径
            this.operationHistoryDir = new File(config.getLogDir());
            this.mainLogDir = new File(config.getLogDir());
            this.operationHistoryFile = new File(operationHistoryDir, OPERATION_HISTORY_FILE);
        }
    }

    /**
     * 更新文件路径
     */
    public void updatePaths() {
        try {
            // 重新获取配置
            this.config = ConfigLoader.getInstance().getCurrentConfig();
            if (this.config == null) {
                Log.e(TAG, "Config is null, cannot update paths");
                return;
            }

            // 更新主日志文件目录
            File newMainLogDir = new File(config.getLogDir());
            if (!newMainLogDir.equals(mainLogDir)) {
                String oldPath = mainLogDir.getAbsolutePath();
                String newPath = newMainLogDir.getAbsolutePath();

                this.mainLogDir = newMainLogDir;
                this.currentSequenceNumber = getSequenceNumber(); // 重新计算序列号

                // 记录路径更新操作
                appendOperationHistory("Config updated: log_dir changed from " + oldPath + " to " + newPath);

                Log.i(TAG, "Main log directory updated from " + oldPath + " to " + newPath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update paths", e);
        }
    }

    /**
     * 确保基础目录存在
     * @return 是否成功创建或目录已存在
     */
    public synchronized boolean ensureBaseDir() {
        try {
            // 确保操作历史文件目录存在
            if (!operationHistoryDir.exists()) {
                boolean created = operationHistoryDir.mkdirs();
                Log.i(TAG, "Operation history directory created: " + operationHistoryDir.getPath() + ", success=" + created);
                if (created) {
                    appendOperationHistory("Directory created: " + operationHistoryDir.getAbsolutePath());
                }
            }

            // 确保主日志文件目录存在
            if (!mainLogDir.exists()) {
                boolean created = mainLogDir.mkdirs();
                Log.i(TAG, "Main log directory created: " + mainLogDir.getPath() + ", success=" + created);
                if (created) {
                    appendOperationHistory("Directory created: " + mainLogDir.getAbsolutePath());
                }
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create directories", e);
            return false;
        }
    }

    /**
     * 确保操作历史文件目录存在
     * @return 是否成功创建或目录已存在
     */
    private boolean ensureOperationHistoryDir() {
        try {
            if (!operationHistoryDir.exists()) {
                boolean created = operationHistoryDir.mkdirs();
                Log.i(TAG, "Operation history directory created: " + operationHistoryDir.getPath() + ", success=" + created);
                if (created) {
                    appendOperationHistory("Directory created: " + operationHistoryDir.getAbsolutePath());
                }
                return created;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create operation history directory", e);
            return false;
        }
    }

    /**
     * 创建新的主日志文件
     * @return 是否创建成功
     */
    public synchronized boolean createNewMainLogFile() {
        try {
            if (!ensureBaseDir()) {
                Log.e(TAG, "Failed to ensure directories");
                return false;
            }

            // 检查并清理过期文件
            checkAndCleanOldFiles();

            // 生成文件名
            String fileName = generateFileName();
            currentMainLogFile = new File(mainLogDir, fileName);

            boolean created = currentMainLogFile.createNewFile();
            if (created) {
                lastFileCreationTime = System.currentTimeMillis();
                Log.i(TAG, "Created new log file: " + fileName);
                // 记录文件创建操作
                appendOperationHistory("Log file created: " + currentMainLogFile.getAbsolutePath() + " (size: 0 bytes)");
                // 更新序列号
                currentSequenceNumber++;
            } else {
                Log.w(TAG, "Failed to create log file: " + fileName);
                // 记录文件创建失败操作
                appendOperationHistory("Failed to create log file: " + currentMainLogFile.getAbsolutePath());
            }
            return created;

        } catch (IOException e) {
            Log.e(TAG, "Failed to create main log file", e);
            // 记录文件创建异常操作
            appendOperationHistory("Exception while creating log file: " + e.getMessage());
            return false;
        }
    }

    /**
     * 追加数据到主日志文件
     * @param data 要追加的数据
     * @param len 数据长度
     */
    public synchronized void appendToMainLog(byte[] data, int len) {
        if (currentMainLogFile == null || !currentMainLogFile.exists()) {
            Log.w(TAG, "Current log file is null or doesn't exist, creating new one");
            if (!createNewMainLogFile()) {
                Log.e(TAG, "Failed to create new log file");
                return;
            }
        }

        // 在写入前检查是否需要轮转文件
        if (shouldRotateFile(len)) {
            Log.i(TAG, "File size limit will be exceeded, creating new file before write");
            if (!createNewMainLogFile()) {
                Log.e(TAG, "Failed to create new log file for rotation");
                return;
            }
        }

        try (FileOutputStream fos = new FileOutputStream(currentMainLogFile, true)) {
            fos.write(data, 0, len);
            fos.flush();

            // 写入后再次检查文件大小，确保不超过限制
            long currentSize = currentMainLogFile.length();
            long maxSize = config.getFileSizeMb() * 1024L * 1024L;
            Log.d(TAG, "After write - File size: " + currentSize + " bytes, Max size: " + maxSize + " bytes");

        } catch (IOException e) {
            Log.e(TAG, "Failed to append to main log", e);
        }
    }

    /**
     * 追加操作历史记录到A_OperationHistory.txt
     * @param operation 操作详情
     */
    public void appendOperationHistory(String operation) {
        Log.i(TAG, "appendOperationHistory called with: " + operation);
        Log.i(TAG, "appendOperationHistory - operationHistoryFile path: " + operationHistoryFile.getAbsolutePath());

        try {
            // 确保操作历史文件目录存在
            boolean dirEnsured = ensureOperationHistoryDir();
            Log.d(TAG, "appendOperationHistory - directory ensured: " + dirEnsured);

            if (!dirEnsured) {
                Log.e(TAG, "appendOperationHistory - failed to ensure directory");
                return;
            }

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String logEntry = "[" + timestamp + "] " + operation + "\n";
            Log.d(TAG, "appendOperationHistory - logEntry: " + logEntry);

            // 检查文件是否存在
            boolean fileExists = operationHistoryFile.exists();
            Log.d(TAG, "appendOperationHistory - file exists before write: " + fileExists);

            // 如果文件不存在，尝试创建
            if (!fileExists) {
                try {
                    boolean created = operationHistoryFile.createNewFile();
                    Log.i(TAG, "appendOperationHistory - file created: " + created);
                    if (created) {
                        Log.i(TAG, "appendOperationHistory - file created successfully at: " + operationHistoryFile.getAbsolutePath());
                    } else {
                        Log.e(TAG, "appendOperationHistory - failed to create file");
                        return;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "appendOperationHistory - exception while creating file", e);
                    return;
                }
            }

            // 写入操作历史
            try (FileOutputStream fos = new FileOutputStream(operationHistoryFile, true)) {
                fos.write(logEntry.getBytes("UTF-8"));
                fos.flush();
                Log.i(TAG, "appendOperationHistory - successfully wrote to file");

                // 验证文件是否真的被写入
                boolean fileExistsAfter = operationHistoryFile.exists();
                long fileSize = operationHistoryFile.length();
                Log.i(TAG, "appendOperationHistory - file exists after write: " + fileExistsAfter + ", size: " + fileSize + " bytes");

            } catch (IOException e) {
                Log.e(TAG, "appendOperationHistory - IOException while writing to file", e);
            }

        } catch (Exception e) {
            Log.e(TAG, "appendOperationHistory - unexpected exception", e);
        }
    }

    /**
     * 追加配置修改记录（键值对格式）
     * @param configDetails 配置详情字符串（键-值格式）
     */
    public void appendConfigChangeHistory(String configDetails) {
        Log.i(TAG, "appendConfigChangeHistory called with: " + configDetails);

        try {
            // 确保操作历史文件目录存在
            if (!ensureOperationHistoryDir()) {
                Log.e(TAG, "appendConfigChangeHistory - failed to ensure directory");
                return;
            }

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String logEntry = "[" + timestamp + "] Config changed: " + configDetails + "\n";
            Log.d(TAG, "appendConfigChangeHistory - logEntry: " + logEntry);

            // 如果文件不存在，先创建
            if (!operationHistoryFile.exists()) {
                try {
                    boolean created = operationHistoryFile.createNewFile();
                    Log.i(TAG, "appendConfigChangeHistory - file created: " + created);
                    if (!created) {
                        Log.e(TAG, "appendConfigChangeHistory - failed to create file");
                        return;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "appendConfigChangeHistory - exception while creating file", e);
                    return;
                }
            }

            // 写入配置修改记录
            try (FileOutputStream fos = new FileOutputStream(operationHistoryFile, true)) {
                fos.write(logEntry.getBytes("UTF-8"));
                fos.flush();
                Log.i(TAG, "appendConfigChangeHistory - successfully wrote to file");
            } catch (IOException e) {
                Log.e(TAG, "appendConfigChangeHistory - failed to write to file", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "appendConfigChangeHistory - unexpected exception", e);
        }
    }

    /**
     * 检查是否应该轮转文件
     * @param additionalBytes 即将添加的字节数
     * @return 是否应该轮转
     */
    private boolean shouldRotateFile(int additionalBytes) {
        if (currentMainLogFile == null || !currentMainLogFile.exists()) {
            return false;
        }

        long currentSize = currentMainLogFile.length();
        long maxSize = config.getFileSizeMb() * 1024L * 1024L;
        long currentTime = System.currentTimeMillis();
        long fileAge = currentTime - lastFileCreationTime;

        // 如果即将超限，检查文件年龄
        if (currentSize + additionalBytes > maxSize) {
            if (fileAge < MIN_FILE_LIFETIME_MS) {
                // 文件太年轻但短时间占满，进行轮转并打印提示日志
                Log.w(TAG, "File rotation forced - File too young (" + fileAge + "ms) but size limit reached. " +
                        "Current: " + currentSize + " bytes, Additional: " + additionalBytes + " bytes, Max: " + maxSize + " bytes");
                return true;
            } else {
                // 文件已超过最小生存时间，正常轮转
                Log.i(TAG, "File rotation needed - Current: " + currentSize + " bytes, " +
                        "Additional: " + additionalBytes + " bytes, Max: " + maxSize + " bytes");
                return true;
            }
        }

        // 文件大小未超限，不轮转
        return false;
    }

    /**
     * 检查并清理过期文件
     */
    public synchronized void checkAndCleanOldFiles() {
        try {
            Log.i(TAG, "Checking and cleaning old files");

            // 1. 检查系统存储空间
            if (isStorageFull()) {
                Log.i(TAG, "Storage is full, cleaning oldest files");
                deleteOldestFiles();
                return;
            }

            // 2. 检查日志总大小限制
            long totalLogSize = getTotalLogSize();
            long maxTotalSize = config.getTotalSizeGb() * 1024L * 1024L * 1024L;
            if (totalLogSize > maxTotalSize) {
                Log.i(TAG, "Total log size exceeds limit, cleaning oldest files");
                deleteOldestFiles();
                return;
            }

            // 3. 检查时间限制
            int logPeriodHours = config.getLogPeriodHours();
            if (logPeriodHours > 0) {
                Log.i(TAG, "Checking time limit: " + logPeriodHours + " hours");
                deleteFilesOlderThan(logPeriodHours);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to check and clean old files", e);
        }
    }

    /**
     * 检查存储空间是否已满
     * @return 是否存储空间不足
     */
    private boolean isStorageFull() {
        try {
            StatFs stat = new StatFs(mainLogDir.getPath());
            long availableBytes = stat.getAvailableBytes();
            long totalBytes = stat.getTotalBytes();

            // 如果可用空间小于总空间的5%，认为存储空间不足
            return availableBytes < (totalBytes * 0.05);
        } catch (Exception e) {
            Log.e(TAG, "Failed to check storage space", e);
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
                    if (file.isFile() && file.getName().startsWith(LOG_FILE_PREFIX)) {
                        totalSize += file.length();
                    }
                }
            }
            return totalSize;
        } catch (Exception e) {
            Log.e(TAG, "Failed to calculate total log size", e);
            return 0;
        }
    }

    /**
     * 删除超过指定小时数的文件
     * @param hours 小时数
     */
    private void deleteFilesOlderThan(int hours) {
        try {
            long cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000L);
            File[] files = mainLogDir.listFiles();
            int deletedCount = 0;

            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().startsWith(LOG_FILE_PREFIX)) {
                        if (file.lastModified() < cutoffTime) {
                            long fileSize = file.length();
                            if (file.delete()) {
                                deletedCount++;
                                Log.i(TAG, "Deleted old file: " + file.getName());
                                // 记录文件删除操作
                                appendOperationHistory("Log file deleted (time limit): " + file.getAbsolutePath() + " (size: " + fileSize + " bytes)");
                            }
                        }
                    }
                }
            }

            Log.i(TAG, "Deleted " + deletedCount + " old files");
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete old files", e);
        }
    }

    /**
     * 删除最老的文件
     */
    private void deleteOldestFiles() {
        try {
            List<File> logFiles = getLogFilesSortedByTime();
            int deletedCount = 0;

            for (File file : logFiles) {
                long fileSize = file.length();
                if (file.delete()) {
                    deletedCount++;
                    Log.i(TAG, "Deleted oldest file: " + file.getName());
                    // 记录文件删除操作
                    appendOperationHistory("Log file deleted (storage/size limit): " + file.getAbsolutePath() + " (size: " + fileSize + " bytes)");
                }
            }

            Log.i(TAG, "Deleted " + deletedCount + " oldest files");
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete oldest files", e);
        }
    }

    /**
     * 获取按时间排序的日志文件列表
     * @return 文件列表
     */
    private List<File> getLogFilesSortedByTime() {
        List<File> logFiles = new ArrayList<>();
        File[] files = mainLogDir.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().startsWith(LOG_FILE_PREFIX)) {
                    logFiles.add(file);
                }
            }
        }

        Collections.sort(logFiles, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return Long.compare(f1.lastModified(), f2.lastModified());
            }
        });

        return logFiles;
    }

    /**
     * 生成日志文件名
     * @return 文件名
     */
    private String generateFileName() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String sequence = String.format(Locale.getDefault(), "%04d", currentSequenceNumber);
        String fileName = LOG_FILE_PREFIX + timestamp + "_" + sequence + LOG_FILE_EXTENSION;
        Log.d(TAG, "Generated filename: " + fileName + ", sequence: " + currentSequenceNumber);
        return fileName;
    }

    /**
     * 生成哈希值（已废弃，保留方法以防其他地方调用）
     * @return 6位十六进制哈希值
     */
    @Deprecated
    private String generateHash() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, HASH_LENGTH);
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