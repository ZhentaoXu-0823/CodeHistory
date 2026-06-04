package com.xcheng.xclogger.filemanager;

import android.app.Service;
import android.content.*;
import android.os.IBinder;
import android.system.Os;
import android.util.Log;
import com.xcheng.xclogger.processctr.*;
import com.xcheng.xclogger.util.XcLoggerDatabase;

import java.io.*;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;
import java.util.zip.*;

public class FileCompressService extends Service {
    public static final String ACTION_CTRL_RESULT = "com.xcheng.xclogger.CTRL_RESULT";
    public static final String STATE_IDLE = "IDLE", STATE_COMPRESSING = "COMPRESSING", STATE_WAIT_UPLOAD_RESULT = "WAIT_UPLOAD_RESULT", STATE_CANCELLING = "CANCELLING";
    public static final String EXTRA_ZIP_FILES = "zip_files", EXTRA_RETRY_COUNT = "retry_count", EXTRA_MAX_RETRY_COUNT = "max_retry_count", EXTRA_COMPRESS_STATE = "compress_state";
    private static final String OUT = "/data/xclogger/mobilelog", PARENT = "/data/xclogger", LOG_PREFIX = "mainlog_", H_PREFIX = "A_OperationHistory_", H_NAME = "A_OperationHistory.txt";
    private static final int MAX_RETRY = 3;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final SecureRandom RAND = new SecureRandom();
    private static final Pattern P_NEW = Pattern.compile("^mainlog_(\\d{6})_(\\d{8})_.*\\.txt$"), P_OLD = Pattern.compile("^mainlog_(\\d{8})_.*\\.txt$");

    @Override
    public int onStartCommand(Intent in, int flags, int id) {
        XcLoggerDatabase db = new XcLoggerDatabase(this);
        String st = db.getCompressState();
        if (RUNNING.get()) {
            db.setCancelCompressRequested(true);
            db.setRestartCompressRequested(true);
            db.setCompressState(STATE_CANCELLING);
            delAllZip();
            hist(this, "Force recompress requested while compressing/cancelling, current task will be stopped and restarted");
            stopSelf(id);
            return START_NOT_STICKY;
        }
        if (STATE_COMPRESSING.equals(st) || STATE_CANCELLING.equals(st)) {
            delAllZip();
            db.clearCompressTaskState();
            hist(this, "Stale compress state cleaned before force recompress: " + st);
        }
        if (STATE_WAIT_UPLOAD_RESULT.equals(st)) {
            String old = db.getPendingZipFiles();
            delZip(old);
            db.clearCompressTaskState();
            hist(this, "Force recompress requested while waiting upload, old zip deleted: " + old);
        }
        if (!RUNNING.compareAndSet(false, true)) {
            db.setCancelCompressRequested(true);
            db.setRestartCompressRequested(true);
            db.setCompressState(STATE_CANCELLING);
            delAllZip();
            hist(this, "Force recompress requested after running race, current task will be stopped and restarted");
            stopSelf(id);
            return START_NOT_STICKY;
        }
        cleanStale(this, db);
        db.setCompressState(STATE_COMPRESSING);
        db.setPendingZipFiles("");
        db.setUploadFailCount(0);
        db.setCancelCompressRequested(false);
        db.setRestartCompressRequested(false);
        hist(this, "Compress task started");
        new Thread(() -> {
            boolean restart = false;
            try {
                List<File> z = compress();
                cancelCheck();
                if (z.isEmpty()) {
                    db.clearCompressTaskState();
                    hist(this, "Compress finished: no log files to compress");
                    sendCtrl(this, "trigger_compress", true, "compress finished: no log files to compress", "", 0, STATE_IDLE);
                } else {
                    String paths = join(z);
                    db.setPendingZipFiles(paths);
                    db.setUploadFailCount(0);
                    db.setCompressState(STATE_WAIT_UPLOAD_RESULT);
                    hist(this, "Compress success, waiting upload result: zip_files=" + paths);
                    sendCtrl(this, "trigger_compress", true, "compress success, waiting upload result", paths, 0, STATE_WAIT_UPLOAD_RESULT);
                }
            } catch (Cancelled e) {
                restart = db.isRestartCompressRequested();
                delAllZip();
                db.clearCompressTaskState();
                hist(this, restart ? "Compress task cancelled for force recompress, generated zip files deleted" : "Compress task cancelled, generated zip files deleted");
                if (!restart) sendCtrl(this, "trigger_compress", false, "compress cancelled", "", 0, STATE_IDLE);
            } catch (Exception e) {
                restart = db.isRestartCompressRequested();
                Log.e("FileCompressService", "compress failed", e);
                delAllZip();
                db.clearCompressTaskState();
                hist(this, "Compress failed: " + e.getMessage());
                if (!restart) sendCtrl(this, "trigger_compress", false, e.getMessage(), "", 0, STATE_IDLE);
            } finally {
                RUNNING.set(false);
                if (restart) {
                    db.setCancelCompressRequested(false);
                    db.setRestartCompressRequested(false);
                    hist(this, "Force recompress restart begins");
                    startService(new Intent(this, FileCompressService.class));
                }
                stopSelf(id);
            }
        }, "XCLoggerFileCompress").start();
        return START_NOT_STICKY;
    }

    public static Result upload(Context c, boolean ok) {
        XcLoggerDatabase db = new XcLoggerDatabase(c);
        String z = db.getPendingZipFiles();
        if (!STATE_WAIT_UPLOAD_RESULT.equals(db.getCompressState()) || z == null || z.isEmpty()) {
            hist(c, "Upload result ignored: no pending compressed files, success=" + ok);
            return new Result(false, "no pending compressed files", db.getCompressState(), "", 0, MAX_RETRY);
        }
        if (ok) {
            delZip(z);
            db.clearCompressTaskState();
            hist(c, "Upload success, compressed zip files deleted: " + z);
            return new Result(true, "upload success, compressed files deleted", STATE_IDLE, "", 0, MAX_RETRY);
        }
        int n = db.getUploadFailCount() + 1;
        if (n >= MAX_RETRY) {
            delZip(z);
            db.clearCompressTaskState();
            hist(c, "Upload failed " + n + " times, compressed zip files force deleted: " + z);
            return new Result(false, "upload failed 3 times, compressed files deleted", STATE_IDLE, "", n, MAX_RETRY);
        }
        db.setUploadFailCount(n);
        db.setCompressState(STATE_WAIT_UPLOAD_RESULT);
        hist(c, "Upload failed, waiting external retry: retry_count=" + n + "/" + MAX_RETRY + ", zip_files=" + z);
        return new Result(false, "upload failed, please retry", STATE_WAIT_UPLOAD_RESULT, z, n, MAX_RETRY);
    }

    public static Result query(Context c) {
        XcLoggerDatabase db = new XcLoggerDatabase(c);
        return new Result(true, "compress_state=" + db.getCompressState(), db.getCompressState(), db.getPendingZipFiles(), db.getUploadFailCount(), MAX_RETRY);
    }

    public static Result cancel(Context c) {
        XcLoggerDatabase db = new XcLoggerDatabase(c);
        String st = db.getCompressState(), z = db.getPendingZipFiles();
        if (STATE_COMPRESSING.equals(st)) {
            db.setCancelCompressRequested(true);
            db.setRestartCompressRequested(false);
            db.setCompressState(STATE_CANCELLING);
            delZip(z);
            hist(c, "Compress cancel requested while compressing");
            return new Result(true, "cancel requested", STATE_CANCELLING, z, db.getUploadFailCount(), MAX_RETRY);
        }
        if (STATE_WAIT_UPLOAD_RESULT.equals(st) || STATE_CANCELLING.equals(st)) {
            delZip(z);
            db.clearCompressTaskState();
            hist(c, "Compress/upload wait cancelled, zip files deleted: " + z);
            return new Result(true, "compress upload task cancelled, zip files deleted", STATE_IDLE, "", 0, MAX_RETRY);
        }
        delAllZip();
        db.clearCompressTaskState();
        hist(c, "No active compress task, stale zip files cleaned");
        return new Result(true, "no active compress task, stale zip files cleaned", STATE_IDLE, "", 0, MAX_RETRY);
    }

    private List<File> compress() throws Exception {
        ConfigLoader.getInstance().load(this);
        String p = ConfigLoader.current() != null ? ConfigLoader.current().getLogDir() : null;
        if (p == null) throw new Exception("Log directory not configured");
        File dir = new File(p);
        if (!dir.exists()) throw new Exception("Log source directory not found");
        XcLoggerDatabase db = new XcLoggerDatabase(this);
        File sealed = null;
        if (db.loadRunningState()) {
            ProcessController pc = ProcessController.getInstance(this);
            if (pc != null && pc.isRunning()) sealed = pc.rotateLogFileForCompress();
        }
        List<File> snap = snapshot(dir, sealed);
        hist(this, "Compress snapshot created: count=" + snap.size() + (sealed != null ? ", sealed=" + sealed.getName() : ""));
        if (snap.isEmpty()) return new ArrayList<>();
        prep();
        cleanHist();
        Map<String, List<File>> map = new HashMap<>();
        for (File f : snap) {
            cancelCheck();
            String d = date(f.getName());
            if (d != null) map.computeIfAbsent(d, k -> new ArrayList<>()).add(f);
        }
        List<File> out = new ArrayList<>();
        for (String d : map.keySet()) {
            cancelCheck();
            cleanDate(d);
            File z = new File(OUT, fmt(d) + "_" + zipTime(d) + "_" + hex() + ".zip");
            zip(z, map.get(d));
            perm(z);
            out.add(z);
            hist(this, "Compressed log date " + d + " to " + z.getAbsolutePath() + ", file_count=" + map.get(d).size());
        }
        copyHistory(new File(OUT));
        return out;
    }

    private List<File> snapshot(File dir, File sealed) {
        File[] fs = dir.listFiles(f -> f.isFile() && f.getName().startsWith(LOG_PREFIX));
        List<File> r = new ArrayList<>();
        if (fs == null) return r;
        int max = sealed != null ? idx(sealed.getName()) : -1;
        for (File f : fs) {
            if (date(f.getName()) == null) continue;
            int i = idx(f.getName());
            if (max < 0 || (i >= 0 && i <= max)) r.add(f);
        }
        return r;
    }

    private void prep() throws Exception {
        File p = new File(PARENT);
        if (!p.exists() && !p.mkdirs()) throw new Exception("Failed to create /data/xclogger");
        perm(p);
        File o = new File(OUT);
        if (!o.exists() && !o.mkdirs()) throw new Exception("Failed to create /data/xclogger/mobilelog");
        perm(o);
    }

    private void cleanHist() {
        File[] a = new File(OUT).listFiles(f -> f.isFile() && f.getName().startsWith(H_PREFIX) && f.getName().endsWith(".txt"));
        if (a != null) for (File f : a) f.delete();
    }

    private static void cleanStale(Context c, XcLoggerDatabase db) {
        if (STATE_IDLE.equals(db.getCompressState())) {
            delAllZip();
            hist(c, "Stale compressed zip files cleaned before new compress task");
        }
    }

    private void cleanDate(String d) {
        File[] a = new File(OUT).listFiles(f -> f.getName().startsWith(fmt(d)) && f.getName().endsWith(".zip"));
        if (a != null) for (File f : a) f.delete();
    }

    private void zip(File z, List<File> fs) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(z)))) {
            byte[] b = new byte[8192];
            for (File f : fs) {
                cancelCheck();
                try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(f))) {
                    zos.putNextEntry(new ZipEntry(f.getName()));
                    int n;
                    while ((n = in.read(b)) != -1) {
                        cancelCheck();
                        zos.write(b, 0, n);
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    private void copyHistory(File out) {
        try {
            XcLoggerDatabase db = new XcLoggerDatabase(this);
            File src = new File(db.getOperationHistoryPath(), H_NAME);
            if (!src.exists()) return;
            File dst = new File(out, H_PREFIX + new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date()) + ".txt");
            try (FileInputStream in = new FileInputStream(src); FileOutputStream os = new FileOutputStream(dst)) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) != -1) os.write(b, 0, n);
            }
            perm(dst);
        } catch (Exception e) {
            Log.e("FileCompressService", "copy history failed", e);
        }
    }

    private String date(String n) {
        Matcher m = P_NEW.matcher(n);
        if (m.matches()) return m.group(2);
        m = P_OLD.matcher(n);
        return m.matches() ? m.group(1) : null;
    }

    private int idx(String n) {
        Matcher m = P_NEW.matcher(n);
        if (!m.matches()) return -1;
        try {
            return Integer.parseInt(m.group(1));
        } catch (Exception e) {
            return -1;
        }
    }

    private String fmt(String d) {
        return d.length() == 8 ? d.substring(0, 4) + "_" + d.substring(4, 8) : d;
    }

    private String zipTime(String d) {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        return today.equals(d) ? new SimpleDateFormat("HHmmss", Locale.getDefault()).format(new Date()) : "235959";
    }

    private String hex() {
        return String.format(Locale.getDefault(), "%04x", RAND.nextInt(0x10000));
    }

    private void perm(File f) {
        f.setReadable(true, false);
        f.setWritable(true, false);
        f.setExecutable(true, false);
        try {
            Os.chmod(f.getAbsolutePath(), 0777);
        } catch (Exception ignored) {
        }
    }

    private void cancelCheck() throws Cancelled {
        if (new XcLoggerDatabase(this).isCancelCompressRequested()) throw new Cancelled();
    }

    private static void sendCtrl(Context c, String op, boolean ok, String msg, String z, int r, String st) {
        Intent i = new Intent(ACTION_CTRL_RESULT);
        i.putExtra("success", ok);
        i.putExtra("message", msg == null ? "" : msg);
        i.putExtra("op_type", op);
        i.putExtra("running_state", new XcLoggerDatabase(c).loadRunningState());
        i.putExtra(EXTRA_COMPRESS_STATE, st == null ? STATE_IDLE : st);
        i.putExtra(EXTRA_ZIP_FILES, z == null ? "" : z);
        i.putExtra(EXTRA_RETRY_COUNT, r);
        i.putExtra(EXTRA_MAX_RETRY_COUNT, MAX_RETRY);
        i.setPackage("com.xcheng.mdm");
        c.sendBroadcast(i);
    }

    private static String join(List<File> fs) {
        StringBuilder s = new StringBuilder();
        for (File f : fs) {
            if (s.length() > 0) s.append(',');
            s.append(f.getAbsolutePath());
        }
        return s.toString();
    }

    private static void delZip(String z) {
        if (z == null || z.trim().isEmpty()) return;
        for (String p : z.split(",")) {
            File f = new File(p.trim());
            if (f.exists() && f.isFile() && f.getName().endsWith(".zip") && !f.delete())
                Log.w("FileCompressService", "delete zip failed: " + f);
        }
        delHistoryFiles();
    }

    private static void delAllZip() {
        File[] a = new File(OUT).listFiles(f -> f.isFile() && f.getName().endsWith(".zip"));
        if (a != null) for (File f : a) if (!f.delete()) Log.w("FileCompressService", "delete zip failed: " + f);
        delHistoryFiles();
    }

    private static void delHistoryFiles() {
        File[] a = new File(OUT).listFiles(f -> f.isFile() && f.getName().startsWith(H_PREFIX) && f.getName().endsWith(".txt"));
        if (a != null) for (File f : a) if (!f.delete()) Log.w("FileCompressService", "delete history failed: " + f);
    }

    private static void hist(Context c, String op) {
        try {
            ProcessController pc = ProcessController.getInstance(c);
            if (pc != null) pc.recordOperationHistory(op);
            else new FileManager(c).appendOperationHistory(op);
        } catch (Exception e) {
            Log.e("FileCompressService", "history failed: " + op, e);
        }
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    public static class Result {
        public final boolean success;
        public final String message, state, zipFiles;
        public final int retryCount, maxRetryCount;

        public Result(boolean s, String m, String st, String z, int r, int mr) {
            success = s;
            message = m;
            state = st;
            zipFiles = z;
            retryCount = r;
            maxRetryCount = mr;
        }
    }

    private static class Cancelled extends Exception {
    }
}
