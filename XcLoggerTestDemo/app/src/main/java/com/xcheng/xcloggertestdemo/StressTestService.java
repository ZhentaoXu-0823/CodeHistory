package com.xcheng.xcloggertestdemo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/** Foreground service that emits evenly paced log lines independently of the UI lifecycle. */
public class StressTestService extends Service {
    public static final int MIN_RATE = 10;
    public static final int MAX_RATE = 100;
    public static final int DEFAULT_RATE = 50;

    public static final String ACTION_STATUS =
            "com.xcheng.xcloggertestdemo.STRESS_STATUS";

    private static final String ACTION_START =
            "com.xcheng.xcloggertestdemo.STRESS_START";
    private static final String ACTION_STOP =
            "com.xcheng.xcloggertestdemo.STRESS_STOP";
    private static final String ACTION_SET_RATE =
            "com.xcheng.xcloggertestdemo.STRESS_SET_RATE";
    private static final String EXTRA_RATE = "rate";
    private static final String EXTRA_START_AT = "start_at_elapsed_ms";
    private static final String EXTRA_SESSION_ID = "session_id";

    private static final String PREFS = "stress_test_state";
    private static final String PREF_RATE = "rate";
    private static final String PREF_RUN_REQUESTED = "run_requested";
    private static final String CHANNEL_ID = "xclogger_stress_channel";
    private static final int NOTIFICATION_ID = 3101;
    private static final String[] LOG_TAGS = {
            "XCStressTag1",
            "XCStressTag2",
            "XCStressTag3",
            "XCStressTag4",
            "XCStressTag5",
            "XCStressTag6"
    };
    private static final int[] LOG_PRIORITIES = {
            Log.VERBOSE,
            Log.DEBUG,
            Log.INFO,
            Log.WARN,
            Log.ERROR,
            Log.ASSERT
    };
    private static final String[] LOG_LEVELS = {"V", "D", "I", "W", "E", "F"};

    private static volatile StressTestService instance;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger ratePerSecond = new AtomicInteger(DEFAULT_RATE);
    private final AtomicLong emittedLines = new AtomicLong(0);
    private volatile long startedAtElapsedMs;
    private volatile Thread worker;
    private volatile String sessionId = "local";

    public static final class Status {
        public final boolean running;
        public final int ratePerSecond;
        public final long emittedLines;
        public final long elapsedMs;

        Status(boolean running, int ratePerSecond, long emittedLines, long elapsedMs) {
            this.running = running;
            this.ratePerSecond = ratePerSecond;
            this.emittedLines = emittedLines;
            this.elapsedMs = elapsedMs;
        }
    }

    public static void start(Context context, int rate) {
        start(context, rate, SystemClock.elapsedRealtime(), "local");
    }

    public static void start(Context context, int rate, long startAtElapsedMs, String sessionId) {
        Intent intent = new Intent(context, StressTestService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RATE, clampRate(rate))
                .putExtra(EXTRA_START_AT, startAtElapsedMs)
                .putExtra(EXTRA_SESSION_ID, sessionId);
        context.startForegroundService(intent);
    }

    public static void stop(Context context) {
        context.startService(new Intent(context, StressTestService.class).setAction(ACTION_STOP));
    }

    public static void setRate(Context context, int rate) {
        int safeRate = clampRate(rate);
        preferences(context).edit().putInt(PREF_RATE, safeRate).apply();
        StressTestService service = instance;
        if (service != null && service.isWorkerRunning()) {
            context.startService(new Intent(context, StressTestService.class)
                    .setAction(ACTION_SET_RATE)
                    .putExtra(EXTRA_RATE, safeRate));
        }
    }

    public static Status getStatus(Context context) {
        StressTestService service = instance;
        if (service != null) return service.snapshot();
        return new Status(false, getSavedRate(context), 0, 0);
    }

    public static int getSavedRate(Context context) {
        return clampRate(preferences(context).getInt(PREF_RATE, DEFAULT_RATE));
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private static int clampRate(int rate) {
        return Math.max(MIN_RATE, Math.min(MAX_RATE, rate));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ratePerSecond.set(getSavedRate(this));
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification(false));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopGenerating(true);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_SET_RATE.equals(action)) {
            applyRate(intent.getIntExtra(EXTRA_RATE, getSavedRate(this)));
            if (!isWorkerRunning()) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                return START_NOT_STICKY;
            }
            return START_STICKY;
        }

        boolean shouldRun = ACTION_START.equals(action)
                || preferences(this).getBoolean(PREF_RUN_REQUESTED, false);
        if (!shouldRun) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        int requestedRate = intent != null
                ? intent.getIntExtra(EXTRA_RATE, getSavedRate(this))
                : getSavedRate(this);
        applyRate(requestedRate);
        if (intent != null) {
            String requestedSession = intent.getStringExtra(EXTRA_SESSION_ID);
            sessionId = requestedSession == null ? "local" : requestedSession;
        }
        long startAtElapsedMs = intent != null
                ? intent.getLongExtra(EXTRA_START_AT, SystemClock.elapsedRealtime())
                : SystemClock.elapsedRealtime();
        startGenerating(startAtElapsedMs);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopGenerating(false);
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startGenerating(long startAtElapsedMs) {
        if (!running.compareAndSet(false, true)) {
            publishStatus();
            return;
        }
        emittedLines.set(0);
        startedAtElapsedMs = Math.max(startAtElapsedMs, SystemClock.elapsedRealtime());
        preferences(this).edit().putBoolean(PREF_RUN_REQUESTED, true).apply();
        worker = new Thread(() -> runEmitter(startedAtElapsedMs), "xclogger-stress-emitter");
        worker.start();
        updateNotification();
        publishStatus();
    }

    private void stopGenerating(boolean explicitStop) {
        running.set(false);
        Thread currentWorker = worker;
        worker = null;
        if (currentWorker != null) currentWorker.interrupt();
        if (explicitStop) {
            preferences(this).edit().putBoolean(PREF_RUN_REQUESTED, false).apply();
        }
        publishStatus();
    }

    private void applyRate(int rate) {
        int safeRate = clampRate(rate);
        ratePerSecond.set(safeRate);
        preferences(this).edit().putInt(PREF_RATE, safeRate).apply();
        updateNotification();
        publishStatus();
    }

    private void runEmitter(long startAtElapsedMs) {
        waitUntil(startAtElapsedMs);
        long nextLineNs = System.nanoTime();
        while (running.get()) {
            int currentRate = ratePerSecond.get();
            long sequence = emittedLines.incrementAndGet();
            emitStressLog(sequence, currentRate);

            long intervalNs = 1_000_000_000L / currentRate;
            nextLineNs += intervalNs;
            long waitNs = nextLineNs - System.nanoTime();
            if (waitNs > 0) {
                LockSupport.parkNanos(waitNs);
            } else if (waitNs < -1_000_000_000L) {
                nextLineNs = System.nanoTime();
            }
            if (Thread.interrupted() && !running.get()) break;
        }
    }

    private void emitStressLog(long sequence, int rate) {
        long zeroBasedSequence = sequence - 1;
        int tagIndex = (int) (zeroBasedSequence % LOG_TAGS.length);
        int levelIndex = (int) ((zeroBasedSequence / LOG_TAGS.length) % LOG_LEVELS.length);
        String tag = LOG_TAGS[tagIndex];
        String level = LOG_LEVELS[levelIndex];
        String message = String.format(Locale.US,
                "package=%s instance=main tag=%s level=%s session=%s seq=%d rate=%d elapsed_ms=%d",
                getPackageName(), tag, level, sessionId, sequence, rate,
                SystemClock.elapsedRealtime() - startedAtElapsedMs);
        Log.println(LOG_PRIORITIES[levelIndex], tag, message);
    }

    private void waitUntil(long startAtElapsedMs) {
        while (running.get()) {
            long remainingMs = startAtElapsedMs - SystemClock.elapsedRealtime();
            if (remainingMs <= 0) return;
            LockSupport.parkNanos(Math.min(remainingMs, 100) * 1_000_000L);
            if (Thread.interrupted() && !running.get()) return;
        }
    }

    private boolean isWorkerRunning() {
        Thread currentWorker = worker;
        return running.get() && currentWorker != null && currentWorker.isAlive();
    }

    private Status snapshot() {
        boolean active = isWorkerRunning();
        long elapsed = active ? SystemClock.elapsedRealtime() - startedAtElapsedMs : 0;
        return new Status(active, ratePerSecond.get(), emittedLines.get(), elapsed);
    }

    private void publishStatus() {
        Status status = snapshot();
        Intent intent = new Intent(ACTION_STATUS).setPackage(getPackageName());
        intent.putExtra("running", status.running);
        intent.putExtra("rate", status.ratePerSecond);
        intent.putExtra("emitted_lines", status.emittedLines);
        intent.putExtra("elapsed_ms", status.elapsedMs);
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "日志输出压测", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("XCLogger Demo 日志输出压测服务");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(isWorkerRunning()));
    }

    private Notification buildNotification(boolean active) {
        Intent pageIntent = new Intent(this, StressTestActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, pageIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = active
                ? "运行中，" + ratePerSecond.get() + " 行/秒"
                : "正在准备压测服务";
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("XCLogger 日志压测")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .setOngoing(active)
                .build();
    }
}
