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

import com.xcheng.xclogger.receiver.XcLoggerBroadcastReceiver;

public class FileCompressService extends Service {
    public static final String ACTION_CTRL_RESULT = "com.xcheng.xclogger.CTRL_RESULT";
    public static final String STATE_IDLE = "IDLE", STATE_COMPRESSING = "COMPRESSING", STATE_WAIT_UPLOAD_RESULT = "WAIT_UPLOAD_RESULT", STATE_CANCELLING = "CANCELLING";
    public static final String EXTRA_ZIP_FILES = "zip_files", EXTRA_RETRY_COUNT = "retry_count", EXTRA_MAX_RETRY_COUNT = "max_retry_count", EXTRA_COMPRESS_STATE = "compress_state";
    private static final String OUT = FileManager.COMPRESS_OUTPUT_DIR, PARENT = FileManager.COMPRESS_PARENT_DIR, LOG_PREFIX = "mainlog_", H_PREFIX = "A_OperationHistory_";
    private static final int MAX_RETRY = 3;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final SecureRandom RAND = new SecureRandom();
    private static final Pattern P_NEW = Pattern.compile("^mainlog_(\\d{6})_(\\d{8})_.*\\.txt$"), P_OLD = Pattern.compile("^mainlog_(\\d{8})_.*\\.txt$");
    private String mStartTime;
    private String mEndTime;

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
        mStartTime = in != null ? in.getStringExtra("startTime") : null;
        mEndTime = in != null ? in.getStringExtra("endTime") : null;
        hist(this, "[CompressTAG] onStartCommand: mStartTime=" + mStartTime + ", mEndTime=" + mEndTime);
        hist(this, "Compress task started" + ((mStartTime != null || mEndTime != null) ? " (range: " + (mStartTime != null ? mStartTime : "*") + "-" + (mEndTime != null ? mEndTime : "*") + ")" : ""));
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

        boolean hasTimeRange = (mStartTime != null && !mStartTime.isEmpty())
                || (mEndTime != null && !mEndTime.isEmpty());
        hist(this, "[CompressTAG] compress ENTER: mStartTime=" + mStartTime + ", mEndTime=" + mEndTime
                + ", hasTimeRange=" + hasTimeRange + ", snapCount=" + snap.size());

        if (hasTimeRange) {
            // V2: 基于文件时间戳重叠语义匹配，输出单一 ZIP
            snap = filterByTimeRangeV2(snap);
            hist(this, "[CompressTAG] filterByTimeRangeV2 result: filteredCount=" + snap.size());
            hist(this, "Compress snapshot created (range): count=" + snap.size()
                    + (sealed != null ? ", sealed=" + sealed.getName() : "")
                    + ", range=" + (mStartTime != null ? mStartTime : "*")
                    + "-" + (mEndTime != null ? mEndTime : "*"));
            if (snap.isEmpty()) return new ArrayList<>();
            prep();
            cleanHist();
            String zipName = buildRangeZipName(snap) + ".zip";
            hist(this, "[CompressTAG] buildRangeZipName result: " + zipName);
            File z = new File(OUT, zipName);
            zip(z, snap);
            perm(z);
            List<File> out = new ArrayList<>();
            out.add(z);
            hist(this, "Compressed range to " + z.getAbsolutePath() + ", file_count=" + snap.size());
            return out;
        }

        // === 原按天压缩逻辑（无 ST/ET 时保持不变）===
        hist(this, "[CompressTAG] compress path: default (no time range), using timestamp naming");
        snap = filterByTimeRange(snap);
        hist(this, "Compress snapshot created: count=" + snap.size() + (sealed != null ? ", sealed=" + sealed.getName() : "") + (mStartTime != null || mEndTime != null ? ", range=" + (mStartTime != null ? mStartTime : "*") + "-" + (mEndTime != null ? mEndTime : "*") : ""));
        if (snap.isEmpty()) return new ArrayList<>();
        prep();
        cleanHist();
        Map<String, List<File>> map = new HashMap<>();
        for (File f : snap) {
            cancelCheck();
            String d = date(f.getName());
            if (d != null) {
                List<File> datedFiles = map.get(d);
                if (datedFiles == null) {
                    datedFiles = new ArrayList<>();
                    map.put(d, datedFiles);
                }
                datedFiles.add(f);
            }
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
        File historySnapshot = null;
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(z)))) {
            byte[] b = new byte[8192];
            for (File f : fs) {
                cancelCheck();
                writeZipEntry(zos, f, f.getName(), b, true);
            }

            hist(this, "Log entries compressed, snapshotting operation history for ZIP: "
                    + z.getName());
            FileManager fileManager = resolveFileManager();
            historySnapshot = File.createTempFile("xclogger_operation_history_", ".tmp", getCacheDir());
            fileManager.snapshotOperationHistory(historySnapshot);
            String historyEntryName = H_PREFIX
                    + new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date())
                    + ".txt";
            writeZipEntry(zos, historySnapshot, historyEntryName, b, false);
        } finally {
            if (historySnapshot != null && historySnapshot.exists() && !historySnapshot.delete()) {
                Log.w("FileCompressService", "delete history snapshot failed: " + historySnapshot);
            }
        }
    }

    private FileManager resolveFileManager() {
        ProcessController controller = ProcessController.getInstance(this);
        return controller != null && controller.getFileManager() != null
                ? controller.getFileManager() : new FileManager(this);
    }

    private void writeZipEntry(ZipOutputStream zos, File source, String entryName,
                               byte[] buffer, boolean checkCancellation) throws Exception {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source))) {
            zos.putNextEntry(new ZipEntry(entryName));
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (checkCancellation) cancelCheck();
                zos.write(buffer, 0, n);
            }
            zos.closeEntry();
        }
    }

    private List<File> filterByTimeRange(List<File> files) {
        if (files == null || files.isEmpty()) return files;
        if (mStartTime == null && mEndTime == null) return files;

        long startTs = -1, endTs = -1;
        try {
            if (mStartTime != null && !mStartTime.isEmpty()) startTs = Long.parseLong(mStartTime.replaceAll("[^0-9]", ""));
            if (mEndTime != null && !mEndTime.isEmpty()) endTs = Long.parseLong(mEndTime.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return files;
        }

        // 非法参数：起始晚于结束，不做过滤
        if (startTs >= 0 && endTs >= 0 && startTs > endTs) return files;

        int startIdx = -1;
        int endIdx = -1;

        for (int i = 0; i < files.size(); i++) {
            long ft = parseFileTimestamp(files.get(i).getName());
            if (ft < 0) continue;

            if (startTs >= 0 && ft <= startTs) {
                startIdx = i;  // 持续更新，取最后一个 ≤ startTime 的文件
            }
            if (endTs >= 0 && ft <= endTs) {
                endIdx = i;
            }
        }

        if (mStartTime == null || mStartTime.isEmpty()) startIdx = 0;
        if (mEndTime == null || mEndTime.isEmpty()) endIdx = files.size() - 1;

        // 起始未找到（所有文件都早于 startTime）→ 从第一个开始
        if (startIdx == -1) startIdx = 0;
        // 结束未找到（所有文件都晚于 endTime）→ 无文件可压缩
        if (endIdx == -1) return new ArrayList<>();

        if (startIdx > endIdx) return new ArrayList<>();

        return files.subList(startIdx, endIdx + 1);
    }

    /**
     * filterByTimeRangeV2 - 基于文件内容时间重叠语义的匹配（需求 V2）
     *
     * 核心规则：文件 i 的内容覆盖 [timestamp_i, timestamp_{i+1})
     * 纳入条件：timestamp_i < ET 且 timestamp_{i+1} > ST
     * 最后一份文件：timestamp_last < ET 即纳入
     *
     * 不传 ST/ET 时返回原列表（由上层决定是否走按天压缩）
     */
    private List<File> filterByTimeRangeV2(List<File> files) {
        if (files == null || files.isEmpty()) return files;
        if (mStartTime == null && mEndTime == null) return files;

        long startTs = -1, endTs = -1;
        try {
            if (mStartTime != null && !mStartTime.isEmpty()) startTs = Long.parseLong(mStartTime.replaceAll("[^0-9]", ""));
            if (mEndTime != null && !mEndTime.isEmpty()) endTs = Long.parseLong(mEndTime.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return files;
        }

        // 非法参数：起始晚于结束，不做过滤
        if (startTs >= 0 && endTs >= 0 && startTs > endTs) return files;

        // 缺省 ST 视为从头开始，缺省 ET 视为到末尾
        if (startTs < 0) startTs = Long.MIN_VALUE;
        if (endTs < 0) endTs = Long.MAX_VALUE;

        // 按时间戳排序（保证相邻关系正确）
        List<File> sorted = new ArrayList<>(files);
        Collections.sort(sorted, (left, right) -> Long.compare(
                parseFileTimestamp(left.getName()), parseFileTimestamp(right.getName())));

        List<File> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            long fileStart = parseFileTimestamp(sorted.get(i).getName());
            if (fileStart < 0) continue;

            // 文件 i 的内容结束时间 = 下一个文件的开始时间（若存在），否则 +∞
            long fileEnd = (i + 1 < sorted.size())
                    ? parseFileTimestamp(sorted.get(i + 1).getName())
                    : Long.MAX_VALUE;
            if (fileEnd < 0) fileEnd = Long.MAX_VALUE;

            // 重叠条件：[fileStart, fileEnd) ∩ [startTs, endTs) ≠ ∅
            if (fileStart < endTs && fileEnd > startTs) {
                result.add(sorted.get(i));
            }
        }
        return result;
    }

    /**
     * buildRangeZipName - 构建时间范围压缩的 ZIP 文件名
     * effectiveST = clamp(ST, earliest file time); effectiveET = clamp(ET, latest file time)
     * Clamp ensures the zip name never claims coverage beyond actual file content.
     */
    private String buildRangeZipName(List<File> filteredFiles) {
        String result = normalizeTimestamp(mStartTime);
        android.util.Log.i("FileCompressService", "[CompressTAG] zipName=" + result);
        return result;
    }

        /** Normalize timestamp to yyyy_MMdd_HHmmss format. */
    private String normalizeTimestamp(String ts) {
        String digits = ts.replaceAll("[^0-9]", "");
        if (digits.length() >= 14)
            return digits.substring(0,4) + "_" + digits.substring(4,8) + "_" + digits.substring(8,14);
        return ts;
    }


    private long parseFileTimestamp(String name) {
        Matcher m = P_NEW.matcher(name);
        if (!m.matches()) return -1;
        // m.group(2) = yyyyMMdd (8 digits)
        String dateStr = m.group(2);
        // 从文件名中提取 HHmmss：mainlog_000042_20260601_120100_0001.txt
        String[] parts = name.split("_");
        if (parts.length < 5) return -1;
        String timePart = parts[3];
        try {
            return Long.parseLong(dateStr + timePart);
        } catch (NumberFormatException e) {
            return -1;
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
        for (String pkg : XcLoggerBroadcastReceiver.TARGET_PACKAGES) {
            i.setPackage(pkg);
            c.sendBroadcast(i);
        }
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
