package com.xcheng.xclogger.filemanager;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
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

public class FileCompressService extends Service {
    private static final String TAG = "FileCompressService";

    public static final String ACTION_COMPRESSING = "com.xcheng.xclogger.FILE_COMPRESSING";
    public static final String ACTION_SUCCESS = "com.xcheng.xclogger.FILE_COMPRESS_SUCCESS";
    public static final String ACTION_FAILED = "com.xcheng.xclogger.FILE_COMPRESS_FAILED";

    private static final String OUTPUT_DIR = "/data/xclogger/mobilelog";
    private static final String OUTPUT_PARENT = "/data/xclogger";
    private static final String LOG_PREFIX = "mainlog_";
    private static final String OP_HISTORY_PREFIX = "A_OperationHistory_";
    private static final String OP_HISTORY_NAME = "A_OperationHistory.txt";
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Pattern PATTERN_NEW = Pattern.compile("^mainlog_\\d{6}_(\\d{8})_.*\\.txt$");
    private static final Pattern PATTERN_OLD = Pattern.compile("^mainlog_(\\d{8})_.*\\.txt$");

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning.compareAndSet(false, true)) {
            sendResultBroadcast(ACTION_COMPRESSING, null);
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

                compressAllDates();
                sendResultBroadcast(ACTION_SUCCESS, null);
            } catch (Exception e) {
                Log.e(TAG, "File compress failed", e);
                sendResultBroadcast(ACTION_FAILED, e.getMessage());
            } finally {
                if (needRestore && originalRunning) {
                    try {
                        ProcessController pc = ProcessController.getInstance(this);
                        if (pc != null) pc.startLogging("file_compress");
                    } catch (Exception ignored) {}
                }
                isRunning.set(false);
                stopSelf(startId);
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void compressAllDates() throws Exception {
        String logDirPath = ConfigLoader.current() != null ? ConfigLoader.current().getLogDir() : null;
        if (logDirPath == null) throw new Exception("Log directory not configured");

        File logDir = new File(logDirPath);
        if (!logDir.exists()) throw new Exception("Log source directory not found");

        File[] files = logDir.listFiles(f -> f.isFile() && f.getName().startsWith(LOG_PREFIX));
        if (files == null || files.length == 0) return;

        Map<String, List<File>> dateMap = new HashMap<>();
        for (File f : files) {
            String date = extractDate(f.getName());
            if (date != null) dateMap.computeIfAbsent(date, k -> new ArrayList<>()).add(f);
        }

        prepareOutputDirs();

        // --- 核心优化：在拷贝新历史文件前，彻底清理旧的历史备份文件 ---
        cleanupOldOperationHistoryCopies();

        for (String date : dateMap.keySet()) {
            cleanupOldZipsForDate(date);
            String zipName = formatDate(date) + "_" + randomHex4() + ".zip";
            File zipFile = new File(OUTPUT_DIR, zipName);
            zipFiles(zipFile, dateMap.get(date));
            ensurePerm(zipFile);
        }

        copyOperationHistory(new File(OUTPUT_DIR));
    }

    private void cleanupOldOperationHistoryCopies() {
        File outDir = new File(OUTPUT_DIR);
        if (!outDir.exists()) return;
        File[] oldHistories = outDir.listFiles(f ->
                f.isFile() && f.getName().startsWith(OP_HISTORY_PREFIX) && f.getName().endsWith(".txt"));
        if (oldHistories != null) {
            for (File h : oldHistories) {
                if (h.delete()) {
                    Log.d(TAG, "Deleted old history file: " + h.getName());
                }
            }
        }
    }

    private void prepareOutputDirs() throws Exception {
        File parent = new File(OUTPUT_PARENT);
        if (!parent.exists() && !parent.mkdirs()) throw new Exception("Failed to create /data/xclogger");
        ensurePerm(parent);
        File out = new File(OUTPUT_DIR);
        if (!out.exists() && !out.mkdirs()) throw new Exception("Failed to create /data/xclogger/mobilelog");
        ensurePerm(out);
    }

    private void cleanupOldZipsForDate(String date) {
        File outDir = new File(OUTPUT_DIR);
        String prefix = formatDate(date);
        File[] zips = outDir.listFiles(f -> f.getName().startsWith(prefix) && f.getName().endsWith(".zip"));
        if (zips != null) for (File z : zips) z.delete();
    }

    private void zipFiles(File zipFile, List<File> files) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            byte[] buffer = new byte[8192];
            for (File f : files) {
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f))) {
                    zos.putNextEntry(new ZipEntry(f.getName()));
                    int len;
                    while ((len = bis.read(buffer)) != -1) zos.write(buffer, 0, len);
                    zos.closeEntry();
                }
            }
        }
    }

    private void copyOperationHistory(File outDir) {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(this);
            File src = new File(db.getOperationHistoryPath(), OP_HISTORY_NAME);
            if (!src.exists()) return;
            String ts = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date());
            File dst = new File(outDir, OP_HISTORY_PREFIX + ts + ".txt");
            try (FileInputStream fis = new FileInputStream(src); FileOutputStream fos = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) != -1) fos.write(buf, 0, len);
            }
            ensurePerm(dst);
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy history", e);
        }
    }

    private String extractDate(String filename) {
        Matcher m1 = PATTERN_NEW.matcher(filename);
        if (m1.matches()) return m1.group(1);
        Matcher m2 = PATTERN_OLD.matcher(filename);
        if (m2.matches()) return m2.group(1);
        return null;
    }

    private String formatDate(String yyyymmdd) {
        if (yyyymmdd.length() != 8) return yyyymmdd;
        return yyyymmdd.substring(0, 4) + "_" + yyyymmdd.substring(4, 8);
    }

    private String randomHex4() { return String.format("%04x", RANDOM.nextInt(0x10000)); }

    private void ensurePerm(File f) {
        f.setReadable(true, false); f.setWritable(true, false); f.setExecutable(true, false);
        try { Os.chmod(f.getAbsolutePath(), 0777); } catch (Exception ignored) {}
    }

    private void sendResultBroadcast(String action, String errorMsg) {
        Intent i = new Intent(action);
        if (errorMsg != null) i.putExtra("error_msg", errorMsg);
        sendBroadcast(i);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}