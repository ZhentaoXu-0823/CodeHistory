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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * FileManager - 日志文件创建、写入、轮转、清理
 * 重要变更：
 * 1) 日志文件名增加全局 6 位索引：mainlog_<index6>_yyyyMMdd_HHmmss_seq.txt
 * 2) 全局索引保存在 SharedPreferences（K_FILE_INDEX），每次成功创建新文件后自增
 * 3) 支持XOR加密（可选，通过数据库开关控制）
 */
public class FileManager {
    private static final String TAG = "FileManager";
    private static final String LOG_FILE_PREFIX = "mainlog_";
    private static final String LOG_FILE_EXTENSION = ".txt";
    private static final String OPERATION_HISTORY_FILE = "A_OperationHistory.txt";
    private static final long MIN_FILE_LIFETIME_MS = 10 * 1000;

    private static final String DEFAULT_OPERATION_HISTORY_PATH = "/storage/emulated/0/XcLogger";
    private static final String COMPRESS_OUTPUT_DIR = "/data/xclogger/mobilelog";
    private static final SimpleDateFormat ZIP_DATE_FORMAT = new SimpleDateFormat("yyyy_MMdd", Locale.getDefault());

    private Context context;
    private XcLoggerConfig config;

    private File operationHistoryDir;
    private File mainLogDir;
    private File operationHistoryFile;
    private File currentMainLogFile;

    private long currentFileStartTime;
    private String currentDate;

    // XOR加密相关
    private XcXorEncryption xorEncryption;
    private long currentFileId; // 当前文件的ID（index）
    private long currentFileDataOffset; // 当前文件的数据偏移量（不包括header）

    public FileManager(Context context) {
        this.context = context;
        this.config = ConfigLoader.getInstance().getCurrentConfig();
        this.currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        this.xorEncryption = new XcXorEncryption(context);
        initOperationHistoryDir();
        initMainLogDir();
        Log.i(TAG, "FileManager initialized - operationHistoryDir: " + operationHistoryDir.getAbsolutePath() +
                ", mainLogDir: " + mainLogDir.getAbsolutePath());
    }

    // ... existing code ... (保留所有现有方法，不做修改)

    private void initOperationHistoryDir() {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(context);
            String operationHistoryPath = db.getOperationHistoryPath();

            if (operationHistoryPath == null || operationHistoryPath.isEmpty()) {
                operationHistoryPath = getDefaultOperationHistoryPath();
                db.setOperationHistoryPath(operationHistoryPath);
                Log.i(TAG, "Operation history path initialized to default: " + operationHistoryPath);
            } else {
                Log.i(TAG, "Operation history path loaded from database: " + operationHistoryPath);
            }

            this.operationHistoryDir = new File(operationHistoryPath);

            if (!operationHistoryDir.exists()) {
                boolean created = operationHistoryDir.mkdirs();
                if (created) {
                    Log.i(TAG, "Operation history directory created: " + operationHistoryDir.getAbsolutePath());
                    appendOperationHistory("Operation history directory created: " + operationHistoryDir.getAbsolutePath());
                } else {
                    Log.e(TAG, "Failed to create operation history directory: " + operationHistoryDir.getAbsolutePath());
                }
            }

            this.operationHistoryFile = new File(operationHistoryDir, OPERATION_HISTORY_FILE);

            Log.i(TAG, "Operation history directory: " + operationHistoryDir.getAbsolutePath());
            Log.i(TAG, "Operation history file: " + operationHistoryFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error initializing operation history directory", e);
            this.operationHistoryDir = new File(DEFAULT_OPERATION_HISTORY_PATH);
            if (!operationHistoryDir.exists()) {
                operationHistoryDir.mkdirs();
            }
            this.operationHistoryFile = new File(operationHistoryDir, OPERATION_HISTORY_FILE);
        }
    }

    private void initMainLogDir() {
        if (config != null && config.getLogDir() != null) {
            this.mainLogDir = new File(config.getLogDir());
        } else {
            this.mainLogDir = new File("/storage/emulated/0/XcLogger");
        }

        if (!mainLogDir.exists()) {
            boolean created = mainLogDir.mkdirs();
            if (created) {
                Log.i(TAG, "Main log directory created: " + mainLogDir.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to create main log directory: " + mainLogDir.getAbsolutePath());
            }
        }
    }

    private String getDefaultOperationHistoryPath() {
        return DEFAULT_OPERATION_HISTORY_PATH;
    }

    public void updatePaths() {
        try {
            this.config = ConfigLoader.getInstance().getCurrentConfig();

            String oldLogDir = mainLogDir != null ? mainLogDir.getAbsolutePath() : "null";

            if (config != null && config.getLogDir() != null) {
                File newMainLogDir = new File(config.getLogDir());

                if (!newMainLogDir.equals(mainLogDir)) {
                    String newLogDir = newMainLogDir.getAbsolutePath();

                    this.mainLogDir = newMainLogDir;

                    if (!mainLogDir.exists()) {
                        boolean created = mainLogDir.mkdirs();
                        if (created) {
                            appendOperationHistory("Main log directory created: " + mainLogDir.getAbsolutePath());
                        } else {
                            Log.e(TAG, "Failed to create main log directory: " + mainLogDir.getAbsolutePath());
                            appendOperationHistory("Failed to create main log directory: " + mainLogDir.getAbsolutePath());
                        }
                    }

                    this.currentMainLogFile = null;
                    this.currentFileStartTime = 0;
                    this.currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());

                    appendOperationHistory("Main log directory path changed: " + oldLogDir + " -> " + newLogDir);

                    Log.i(TAG, "Main log directory updated to: " + mainLogDir.getAbsolutePath());
                } else {
                    Log.d(TAG, "Main log directory path unchanged: " + mainLogDir.getAbsolutePath());
                }
            } else {
                Log.w(TAG, "Config or log_dir is null, cannot update paths");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating paths", e);
            appendOperationHistory("Error updating paths: " + e.getMessage());
        }
    }

    public synchronized File createNewMainLogFile() {
        try {
            long newFileTime = System.currentTimeMillis();

            checkAndCleanBeforeNewFile(newFileTime);

            String fileName = generateFileName();
            File newFile = new File(mainLogDir, fileName);

            if (newFile.createNewFile()) {
                this.currentMainLogFile = newFile;
                this.currentFileStartTime = newFileTime;
                this.currentFileDataOffset = 0;

                // 获取当前文件ID（index - 1，因为创建后才会自增）
                XcLoggerDatabase db = new XcLoggerDatabase(context);
                SharedPreferencesHelper helper = new SharedPreferencesHelper(db.getPrefs());
                int nextIndex = helper.getIntSafe(XcLoggerDatabase.K_FILE_INDEX, 1);
                this.currentFileId = nextIndex - 1;

                // 检查加密开关
                boolean encryptionEnabled = db.getEncryptionEnabled();
                if (encryptionEnabled) {
                    // 初始化加密会话并写入文件头
                    byte[] nonce = xorEncryption.initFileSession(currentFileId);
                    try (FileOutputStream fos = new FileOutputStream(currentMainLogFile, false)) {
                        xorEncryption.writeFileHeader(fos, currentFileId, nonce);
                        Log.i(TAG, "Created encrypted log file with header: " + fileName);
                        appendOperationHistory("Encrypted log file created: " + currentMainLogFile.getAbsolutePath() + " (fileId: " + currentFileId + ")");
                    }
                } else {
                    Log.i(TAG, "Created unencrypted log file: " + fileName);
                    appendOperationHistory("Log file created: " + currentMainLogFile.getAbsolutePath() + " (size: 0 bytes)");
                }

                // 成功创建后递增全局索引
                incrementGlobalIndex();
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

    public synchronized void resetCurrentLogFile() {
        this.currentMainLogFile = null;
        this.currentFileStartTime = 0;
        this.currentFileDataOffset = 0;
        this.currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        appendOperationHistory("Current log file state reset (reason: stop logging)");
    }

    public synchronized void appendToMainLog(byte[] data, int len) {
        try {
            if (data == null || len <= 0) {
                return;
            }

            if (currentMainLogFile == null || !currentMainLogFile.exists()) {
                File newFile = createNewMainLogFile();
                if (newFile == null) {
                    Log.e(TAG, "Cannot append data: failed to create log file");
                    return;
                }
            }

            if (!currentMainLogFile.getParentFile().equals(mainLogDir)) {
                Log.w(TAG, "Current log file is not in the current log directory, creating new file");
                File newFile = createNewMainLogFile();
                if (newFile == null) {
                    Log.e(TAG, "Cannot append data: failed to create log file in new directory");
                    return;
                }
            }

            if (shouldRotateFile()) {
                Log.i(TAG, "Rotating log file due to size limit");
                appendOperationHistory("Log file rotated due to size limit: " + currentMainLogFile.getName());
                File newFile = createNewMainLogFile();
                if (newFile == null) {
                    Log.e(TAG, "Cannot append data: failed to create new file after rotation");
                    return;
                }
            }

            // 检查加密开关，决定是否加密
            XcLoggerDatabase db = new XcLoggerDatabase(context);
            boolean encryptionEnabled = db.getEncryptionEnabled();

            byte[] dataToWrite = data;
            int lenToWrite = len;

            if (encryptionEnabled && currentMainLogFile != null) {
                // 需要加密：复制数据并加密（避免修改原始数据）
                byte[] encryptedData = new byte[len];
                System.arraycopy(data, 0, encryptedData, 0, len);
                xorEncryption.encryptInPlace(encryptedData, len, currentFileDataOffset);
                dataToWrite = encryptedData;
            }

            try (FileOutputStream fos = new FileOutputStream(currentMainLogFile, true)) {
                fos.write(dataToWrite, 0, lenToWrite);
                fos.flush();
            }

            // 更新数据偏移量
            currentFileDataOffset += lenToWrite;

        } catch (IOException e) {
            Log.e(TAG, "Error writing to log file", e);
            appendOperationHistory("Error writing to log file: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error appending to log file", e);
            appendOperationHistory("Unexpected error appending to log file: " + e.getMessage());
        }
    }

    /**
     * 解密最新的日志文件
     * @return 解密后的文件，如果失败返回null
     */
    public File decryptLatestLogFile() {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(context);

            // 检查加密开关
            if (!db.getEncryptionEnabled()) {
                Log.w(TAG, "Encryption is not enabled, cannot decrypt");
                return null;
            }

            // 获取最新文件的index（当前index - 1）
            SharedPreferencesHelper helper = new SharedPreferencesHelper(db.getPrefs());
            int currentIndex = helper.getIntSafe(XcLoggerDatabase.K_FILE_INDEX, 1);
            int latestIndex = currentIndex - 1;

            if (latestIndex < 0) {
                Log.w(TAG, "No log file found (index < 0)");
                return null;
            }

            // 格式化index为6位字符串
            String indexStr = String.format(Locale.getDefault(), "%06d", latestIndex);

            // 查找匹配的文件
            File[] files = mainLogDir.listFiles();
            File latestFile = null;

            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    // 匹配格式：mainlog_<index6>_...
                    if (name.startsWith(LOG_FILE_PREFIX + indexStr + "_") &&
                            name.endsWith(LOG_FILE_EXTENSION) &&
                            !name.contains("_decrypted")) {
                        // 检查是否为加密文件
                        if (xorEncryption.isFileEncrypted(file)) {
                            latestFile = file;
                            break;
                        }
                    }
                }
            }

            if (latestFile == null) {
                Log.w(TAG, "Latest encrypted log file not found for index: " + latestIndex);
                return null;
            }

            // 生成解密后的文件名
            String originalName = latestFile.getName();
            String baseName = originalName.substring(0, originalName.length() - LOG_FILE_EXTENSION.length());
            String decryptedName = baseName + "_decrypted" + LOG_FILE_EXTENSION;
            File decryptedFile = new File(mainLogDir, decryptedName);

            // 执行解密
            boolean success = xorEncryption.decryptFile(latestFile, decryptedFile);
            if (success) {
                appendOperationHistory("Latest log file decrypted: " + latestFile.getName() + " -> " + decryptedFile.getName());
                Log.i(TAG, "Decrypted file saved: " + decryptedFile.getAbsolutePath());
                return decryptedFile;
            } else {
                Log.e(TAG, "Failed to decrypt file: " + latestFile.getName());
                if (decryptedFile.exists()) {
                    decryptedFile.delete();
                }
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error decrypting latest log file", e);
            appendOperationHistory("Error decrypting latest log file: " + e.getMessage());
            return null;
        }
    }

    // ... existing code ... (保留所有其他现有方法，不变)

    public void appendOperationHistory(String operation) {
        try {
            if (operationHistoryFile == null) {
                initOperationHistoryDir();
            }

            if (operationHistoryFile != null && operationHistoryFile.getParentFile() != null) {
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

    public void appendConfigChangeHistory(String configDetails) {
        appendOperationHistory("Config updated: " + configDetails);
    }

    private void checkAndCleanBeforeNewFile(long newFileTime) {
        try {
            if (needSpaceForNewFile()) {
                deleteOldestFileForSpace();
            }
            deleteFilesExceedingTimeLimit(newFileTime);
            deleteZipExceedingTimeLimit(newFileTime);
        } catch (Exception e) {
            Log.e(TAG, "Error checking and cleaning files", e);
        }
    }

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
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting files exceeding time limit", e);
        }
    }

    private void deleteZipExceedingTimeLimit(long newFileTime) {
        if (config == null) {
            return;
        }
        try {
            long timeLimitMs = config.getLogPeriodHours() * 60L * 60L * 1000L;
            File zipDir = new File(COMPRESS_OUTPUT_DIR);
            if (!zipDir.exists() || !zipDir.isDirectory()) {
                return;
            }
            File[] files = zipDir.listFiles(f -> f.isFile() && f.getName().endsWith(".zip"));
            if (files == null || files.length == 0) {
                return;
            }
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (File file : files) {
                long baseTime = getZipDateStartOfDay(file.getName());
                if (baseTime < 0) {
                    baseTime = file.lastModified();
                }
                long timeDiff = newFileTime - baseTime;
                if (timeDiff > timeLimitMs) {
                    long fileSize = file.length();
                    if (file.delete()) {
                        Log.i(TAG, "Deleted zip exceeding time limit: " + file.getName() + " (time diff: " + (timeDiff / 1000 / 60) + " minutes)");
                        appendOperationHistory("Log zip deleted (time limit): " + file.getAbsolutePath() + " (size: " + fileSize + " bytes)");
                    } else {
                        Log.w(TAG, "Failed to delete zip exceeding time limit: " + file.getName());
                    }
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting zip files exceeding time limit", e);
        }
    }

    private long getZipDateStartOfDay(String name) {
        try {
            String[] parts = name.split("_");
            if (parts.length < 2) {
                return -1;
            }
            String yyyy = parts[0];
            String mmddPart = parts[1];
            if (yyyy.length() != 4 || mmddPart.length() != 4) {
                return -1;
            }
            String dateStr = yyyy + "_" + mmddPart;
            Date date = ZIP_DATE_FORMAT.parse(dateStr);
            if (date == null) return -1;
            return date.getTime();
        } catch (ParseException e) {
            return -1;
        }
    }

    private boolean isStorageFull() {
        try {
            StatFs stat = new StatFs(mainLogDir.getAbsolutePath());
            long availableBytes = stat.getAvailableBytes();
            long totalBytes = stat.getTotalBytes();
            return (availableBytes * 100 / totalBytes) < 5;
        } catch (Exception e) {
            Log.e(TAG, "Error checking storage space", e);
            return false;
        }
    }

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

    private File[] getLogFilesSortedByTime() {
        try {
            File[] files = mainLogDir.listFiles();
            if (files == null) {
                return new File[0];
            }

            File[] logFiles = Arrays.stream(files)
                    .filter(file -> file.getName().startsWith(LOG_FILE_PREFIX) && file.getName().endsWith(LOG_FILE_EXTENSION))
                    .toArray(File[]::new);

            Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));

            return logFiles;
        } catch (Exception e) {
            Log.e(TAG, "Error getting log files sorted by time", e);
            return new File[0];
        }
    }

    private boolean shouldRotateFile() {
        if (currentMainLogFile == null || !currentMainLogFile.exists() || config == null) {
            return false;
        }

        try {
            long currentTime = System.currentTimeMillis();
            long fileAge = currentTime - currentFileStartTime;
            long fileSize = currentMainLogFile.length();
            long maxFileSize = config.getFileSizeMb() * 1024L * 1024L;

            if (fileSize > maxFileSize) {
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

    private String generateFileName() {
        try {
            String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
            String time = new SimpleDateFormat("HHmmss", Locale.getDefault()).format(new Date());
            int sequence = getSequenceNumber();
            String indexStr = String.format(Locale.getDefault(), "%06d", getCurrentGlobalIndex());
            return LOG_FILE_PREFIX + indexStr + "_" + today + "_" + time + "_" + String.format(Locale.getDefault(), "%04d", sequence) + LOG_FILE_EXTENSION;
        } catch (Exception e) {
            Log.e(TAG, "Error generating file name", e);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String indexStr = String.format(Locale.getDefault(), "%06d", getCurrentGlobalIndex());
            return LOG_FILE_PREFIX + indexStr + "_" + timestamp + "_0001" + LOG_FILE_EXTENSION;
        }
    }

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
                            String[] parts = name.split("_");
                            if (parts.length >= 5) {
                                String seqStr = parts[4].replace(LOG_FILE_EXTENSION, "");
                                if (seqStr.matches("\\d+")) {
                                    int sequence = Integer.parseInt(seqStr);
                                    maxSequence = Math.max(maxSequence, sequence);
                                }
                            } else if (parts.length >= 3) {
                                String seqStr = parts[3].replace(LOG_FILE_EXTENSION, "");
                                if (seqStr.matches("\\d+")) {
                                    int sequence = Integer.parseInt(seqStr);
                                    maxSequence = Math.max(maxSequence, sequence);
                                }
                            }
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Failed to parse sequence from filename: " + name);
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

    private int getCurrentGlobalIndex() {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(context);
            SharedPreferencesHelper helper = new SharedPreferencesHelper(db.getPrefs());
            return helper.getIntSafe(XcLoggerDatabase.K_FILE_INDEX, 1);
        } catch (Exception e) {
            Log.w(TAG, "getCurrentGlobalIndex failed, default 1", e);
            return 1;
        }
    }

    private void incrementGlobalIndex() {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(context);
            SharedPreferencesHelper helper = new SharedPreferencesHelper(db.getPrefs());
            int current = helper.getIntSafe(XcLoggerDatabase.K_FILE_INDEX, 1);
            helper.putIntSafe(XcLoggerDatabase.K_FILE_INDEX, current + 1);
        } catch (Exception e) {
            Log.w(TAG, "incrementGlobalIndex failed", e);
        }
    }

    private static class SharedPreferencesHelper {
        private final android.content.SharedPreferences prefs;

        SharedPreferencesHelper(android.content.SharedPreferences prefs) {
            this.prefs = prefs;
        }

        int getIntSafe(String key, int def) {
            try {
                return prefs.getInt(key, def);
            } catch (Exception e) {
                return def;
            }
        }

        void putIntSafe(String key, int value) {
            try {
                prefs.edit().putInt(key, value).apply();
            } catch (Exception ignored) {
            }
        }
    }

    public File getCurrentMainLogFile() {
        return currentMainLogFile;
    }

    public File getMainLogDir() {
        return mainLogDir;
    }

    public File getOperationHistoryFile() {
        return operationHistoryFile;
    }
}