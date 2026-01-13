package com.xcheng.xclogger.filemanager;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.processctr.ProcessController;
import com.xcheng.xclogger.util.XcLoggerDatabase;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * FileCompressService - 按天压缩日志文件的后台服务
 *
 * 变更点：
 * - 支持新旧命名解析：mainlog_<idx6>_yyyyMMdd_HHmmss_seq.txt 或 mainlog_yyyyMMdd_HHmmss_seq.txt
 * - 对扫描到的所有日期分组压缩；同日期先删除旧 zip，再生成 yyyy_MMdd_<4hex>.zip，仅保留一份
 * - 复制操作历史前删除旧的 A_OperationHistory_*.txt
 * - 输出目录 /data/xclogger/mobilelog（父目录 /data/xclogger），目录/文件均设置可读/可写/可执行
 */
public class FileCompressService extends Service {
    private static final String TAG = "FileCompressService";

    public static final String ACTION_COMPRESS = "com.xcheng.xclogger.FILE_COMPRESS";
    public static final String ACTION_COMPRESSING = "com.xcheng.xclogger.FILE_COMPRESSING";
    public static final String ACTION_SUCCESS = "com.xcheng.xclogger.FILE_COMPRESS_SUCCESS";
    public static final String ACTION_FAILED = "com.xcheng.xclogger.FILE_COMPRESS_FAILED";

    private static final String OUTPUT_DIR = "/data/xclogger/mobilelog";
    private static final String OUTPUT_PARENT = "/data/xclogger";
    private static final String LOG_PREFIX = "mainlog_";
    private static final String LOG_SUFFIX = ".txt";
    private static final String ZIP_DATE_PATTERN = "yyyy_MMdd";
    private static final String OP_HISTORY_NAME = "A_OperationHistory.txt";
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final SecureRandom RANDOM = new SecureRandom();

    // 正则匹配日期（新旧格式）
    private static final Pattern PATTERN_NEW = Pattern.compile("^mainlog_\\d{6}_(\\d{8})_.*\\.txt$");
    private static final Pattern PATTERN_OLD = Pattern.compile("^mainlog_(\\d{8})_.*\\.txt$");

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String targetPackage = intent != null ? intent.getPackage() : null;

        if (!isRunning.compareAndSet(false, true)) {
            sendResultBroadcast(ACTION_COMPRESSING, targetPackage);
            recordHistory("File compress requested but already running; replying COMPRESSING");
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        new Thread(() -> {
            boolean originalRunning = false;
            boolean needRestore = false;
            try {
                XcLoggerDatabase db = new XcLoggerDatabase(this);
                originalRunning = db.loadRunningState();
                if (originalRunning) {
                    ProcessController pc = ProcessController.getInstance(this);
                    if (pc != null) {
                        pc.stopLogging("file_compress");
                        needRestore = true;
                    }
                }
                recordHistory("File compress started (original running: " + originalRunning + ")");

                compressAllDates();

                sendResultBroadcast(ACTION_SUCCESS, targetPackage);
                recordHistory("File compress succeeded");
            } catch (Exception e) {
                Log.e(TAG, "File compress failed", e);
                recordHistory("File compress failed: " + e.getMessage());
                sendResultBroadcast(ACTION_FAILED, targetPackage);
            } finally {
                if (needRestore && originalRunning) {
                    try {
                        ProcessController pc = ProcessController.getInstance(this);
                        if (pc != null) {
                            pc.startLogging("file_compress");
                        }
                        recordHistory("File compress finished, logging restored to running");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to restore logging state", e);
                        recordHistory("Failed to restore logging state: " + e.getMessage());
                    }
                } else {
                    recordHistory("File compress finished, logging state kept stopped");
                }
                isRunning.set(false);
                stopSelf(startId);
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void compressAllDates() throws Exception {
        String logDirPath = ConfigLoader.current() != null ? ConfigLoader.current().getLogDir() : null;
        if (logDirPath == null || logDirPath.isEmpty()) {
            throw new IllegalStateException("Log dir is null or empty");
        }
        File logDir = new File(logDirPath);
        if (!logDir.exists() || !logDir.isDirectory()) {
            throw new IllegalStateException("Log dir not found: " + logDirPath);
        }

        File[] files = logDir.listFiles(f ->
                f.isFile() && f.getName().startsWith(LOG_PREFIX) && f.getName().endsWith(LOG_SUFFIX));
        if (files == null || files.length == 0) {
            recordHistory("No log files to compress");
            return;
        }

        // 分组：日期 -> 文件列表
        Map<String, List<File>> dateMap = new HashMap<>();
        for (File f : files) {
            String date = extractDate(f.getName());
            if (date == null) {
                continue;
            }
            dateMap.computeIfAbsent(date, k -> new ArrayList<>()).add(f);
        }
        if (dateMap.isEmpty()) {
            recordHistory("No valid dated log files to compress");
            return;
        }

        // 按日期排序，逐个日期压缩；同日期先删旧 zip
        List<String> dates = new ArrayList<>(dateMap.keySet());
        Collections.sort(dates, Comparator.naturalOrder());

        prepareOutputDirs();

        for (String date : dates) {
            List<File> dayFiles = dateMap.get(date);
            if (dayFiles == null || dayFiles.isEmpty()) continue;

            // 删除同日期旧 zip
            cleanupOldZipsForDate(date);

            // 生成新 zip 名：yyyy_MMdd_<4hex>.zip
            String zipName = formatDate(date, ZIP_DATE_PATTERN) + "_" + randomHex4() + ".zip";
            File zipFile = new File(OUTPUT_DIR, zipName);

            zipFiles(zipFile, dayFiles);
            ensurePerm(zipFile);

            recordHistory("Compressed date " + date + " to " + zipFile.getAbsolutePath());
        }

        // 复制操作历史：先删旧的 A_OperationHistory_*.txt，再复制最新
        cleanupOldOperationHistoryCopies();
        copyOperationHistory(new File(OUTPUT_DIR));
    }

    private void prepareOutputDirs() throws Exception {
        File parentDir = new File(OUTPUT_PARENT);
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IllegalStateException("Failed to create parent output dir: " + OUTPUT_PARENT);
        }
        ensurePerm(parentDir);

        File outDir = new File(OUTPUT_DIR);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("Failed to create output dir: " + OUTPUT_DIR);
        }
        ensurePerm(outDir);
    }

    private void cleanupOldZipsForDate(String dateYyyyMMdd) {
        File outDir = new File(OUTPUT_DIR);
        File[] zips = outDir.listFiles(f ->
                f.isFile() && f.getName().startsWith(formatDate(dateYyyyMMdd, ZIP_DATE_PATTERN) + "_") && f.getName().endsWith(".zip"));
        if (zips != null) {
            for (File z : zips) {
                if (!z.delete()) {
                    Log.w(TAG, "Failed to delete old zip: " + z.getAbsolutePath());
                    tryChmodAndDelete(z);
                }
            }
        }
    }

    private void cleanupOldOperationHistoryCopies() {
        File outDir = new File(OUTPUT_DIR);
        File[] histories = outDir.listFiles(f ->
                f.isFile() && f.getName().startsWith("A_OperationHistory_") && f.getName().endsWith(".txt"));
        if (histories != null) {
            for (File h : histories) {
                if (!h.delete()) {
                    Log.w(TAG, "Failed to delete old history copy: " + h.getAbsolutePath());
                    tryChmodAndDelete(h);
                }
            }
        }
    }

    private void zipFiles(File zipFile, List<File> files) throws Exception {
        byte[] buffer = new byte[8192];
        // 直接覆盖写入，不依赖 delete，避免删除失败阻塞
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile, false)))) {
            for (File f : files) {
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f))) {
                    ZipEntry entry = new ZipEntry(f.getName());
                    zos.putNextEntry(entry);
                    int len;
                    while ((len = bis.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    private void copyOperationHistory(File outDir) {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(this);
            String historyDir = db.getOperationHistoryPath();
            if (historyDir == null || historyDir.isEmpty()) {
                return;
            }
            File src = new File(historyDir, OP_HISTORY_NAME);
            if (!src.exists() || !src.isFile()) {
                return;
            }
            String ts = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date());
            File dst = new File(outDir, "A_OperationHistory_" + ts + ".txt");

            try (FileInputStream fis = new FileInputStream(src);
                 FileOutputStream fos = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
                fos.flush();
            }
            ensurePerm(dst);
        } catch (Exception e) {
            Log.w(TAG, "copyOperationHistory failed", e);
        }
    }

    private String formatDate(String yyyymmdd, String pattern) {
        if (yyyymmdd == null || yyyymmdd.length() != 8) return yyyymmdd;
        String yyyy = yyyymmdd.substring(0, 4);
        String mm = yyyymmdd.substring(4, 6);
        String dd = yyyymmdd.substring(6, 8);
        if ("yyyy_MMdd".equals(pattern)) {
            return yyyy + "_" + mm + dd;
        }
        return yyyymmdd;
    }

    private String extractDate(String filename) {
        Matcher m1 = PATTERN_NEW.matcher(filename);
        if (m1.matches()) {
            return m1.group(1); // yyyyMMdd
        }
        Matcher m2 = PATTERN_OLD.matcher(filename);
        if (m2.matches()) {
            return m2.group(1); // yyyyMMdd
        }
        return null;
    }

    private String randomHex4() {
        int val = RANDOM.nextInt(0x10000);
        return String.format(Locale.getDefault(), "%04x", val);
    }

    private void ensurePerm(File f) {
        try {
            f.setReadable(true, false);
            f.setWritable(true, false);
            f.setExecutable(true, false);
            // 双保险：若 set* 失败，可尝试 Os.chmod
            try {
                Os.chmod(f.getAbsolutePath(), 0777);
            } catch (ErrnoException ignored) {
            }
        } catch (Exception e) {
            Log.w(TAG, "ensurePerm failed for " + f.getAbsolutePath(), e);
        }
    }

    private void tryChmodAndDelete(File f) {
        try {
            Os.chmod(f.getAbsolutePath(), 0777);
            if (!f.delete()) {
                Log.w(TAG, "Still failed to delete: " + f.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.w(TAG, "tryChmodAndDelete failed for " + f.getAbsolutePath(), e);
        }
    }

    private void sendResultBroadcast(String action, String targetPackage) {
        Intent i = new Intent(action);
        if (targetPackage != null && !targetPackage.isEmpty()) {
            i.setPackage(targetPackage);
        }
        sendBroadcast(i);
    }

    private void recordHistory(String msg) {
        try {
            ProcessController pc = ProcessController.getInstance(this);
            if (pc != null) {
                pc.recordOperationHistory(msg);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to record operation history: " + msg, e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}