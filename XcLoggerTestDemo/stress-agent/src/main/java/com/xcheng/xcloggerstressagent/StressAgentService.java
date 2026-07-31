package com.xcheng.xcloggerstressagent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
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

/** Headless foreground service controlled by the main XCLoggerTestDemo package. */
public final class StressAgentService extends Service {
    private static final String CONTROL_PERMISSION =
            "com.xcheng.xcloggertestdemo.permission.CONTROL_STRESS";
    private static final String CONTROLLER_PACKAGE = "com.xcheng.xcloggertestdemo";
    private static final String ACTION_START =
            "com.xcheng.xcloggertestdemo.action.STRESS_AGENT_START";
    private static final String ACTION_STOP =
            "com.xcheng.xcloggertestdemo.action.STRESS_AGENT_STOP";
    private static final String ACTION_SET_RATE =
            "com.xcheng.xcloggertestdemo.action.STRESS_AGENT_SET_RATE";
    private static final String ACTION_STATUS =
            "com.xcheng.xcloggertestdemo.action.STRESS_AGENT_STATUS";
    private static final String EXTRA_RATE = "rate";
    private static final String EXTRA_START_AT = "start_at_elapsed_ms";
    private static final String EXTRA_SESSION_ID = "session_id";

    private static final String PREFS = "stress_agent_state";
    private static final String PREF_RATE = "rate";
    private static final String PREF_RUN_REQUESTED = "run_requested";
    private static final String PREF_SESSION_ID = "session_id";
    private static final int DEFAULT_RATE = 50;
    private static final int MIN_RATE = 10;
    private static final int MAX_RATE = 100;
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
    private static final String CHANNEL_ID = "xclogger_stress_agent";
    private static final int NOTIFICATION_ID = 3201;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger ratePerSecond = new AtomicInteger(DEFAULT_RATE);
    private final AtomicLong emittedLines = new AtomicLong(0);
    private volatile Thread worker;
    private volatile long startedAtElapsedMs;
    private volatile String sessionId = "";
    private volatile String instanceId = "";

    @Override
    public void onCreate() {
        super.onCreate();
        instanceId = getString(R.string.stress_instance_id);
        SharedPreferences prefs = preferences();
        ratePerSecond.set(clampRate(prefs.getInt(PREF_RATE, DEFAULT_RATE)));
        sessionId = prefs.getString(PREF_SESSION_ID, "");
        createNotificationChannel();
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
            applyRate(intent.getIntExtra(EXTRA_RATE, ratePerSecond.get()));
            if (!running.get()) stopSelf();
            return running.get() ? START_STICKY : START_NOT_STICKY;
        }

        boolean resume = intent == null && preferences().getBoolean(PREF_RUN_REQUESTED, false);
        if (!ACTION_START.equals(action) && !resume) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int rate = intent != null
                ? intent.getIntExtra(EXTRA_RATE, ratePerSecond.get())
                : preferences().getInt(PREF_RATE, DEFAULT_RATE);
        applyRate(rate);
        if (intent != null) {
            sessionId = intent.getStringExtra(EXTRA_SESSION_ID);
            if (sessionId == null) sessionId = "";
        }
        preferences().edit().putString(PREF_SESSION_ID, sessionId).apply();

        long startAt = intent != null
                ? intent.getLongExtra(EXTRA_START_AT, SystemClock.elapsedRealtime())
                : SystemClock.elapsedRealtime();
        startForeground(NOTIFICATION_ID, buildNotification(true));
        startGenerating(startAt);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopGenerating(false);
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
        preferences().edit().putBoolean(PREF_RUN_REQUESTED, true).apply();
        worker = new Thread(() -> runEmitter(startedAtElapsedMs),
                "xclogger-stress-" + instanceId);
        worker.start();
        publishStatus();
    }

    private void stopGenerating(boolean explicitStop) {
        running.set(false);
        Thread current = worker;
        worker = null;
        if (current != null) current.interrupt();
        if (explicitStop) {
            preferences().edit().putBoolean(PREF_RUN_REQUESTED, false).apply();
        }
        publishStatus();
    }

    private void runEmitter(long startAtElapsedMs) {
        waitUntil(startAtElapsedMs);
        long nextLineNs = System.nanoTime();
        long nextStatusMs = SystemClock.elapsedRealtime() + 1000;
        while (running.get()) {
            int currentRate = ratePerSecond.get();
            long sequence = emittedLines.incrementAndGet();
            emitStressLog(sequence, currentRate);

            long intervalNs = 1_000_000_000L / currentRate;
            nextLineNs += intervalNs;
            long waitNs = nextLineNs - System.nanoTime();
            if (waitNs > 0) LockSupport.parkNanos(waitNs);
            else if (waitNs < -1_000_000_000L) nextLineNs = System.nanoTime();

            long nowMs = SystemClock.elapsedRealtime();
            if (nowMs >= nextStatusMs) {
                publishStatus();
                nextStatusMs = nowMs + 1000;
            }
            if (Thread.interrupted() && !running.get()) break;
        }
    }

    private void emitStressLog(long sequence, int rate) {
        int instanceOffset = getInstanceOffset();
        long zeroBasedSequence = sequence - 1;
        int tagIndex = (int) ((zeroBasedSequence + instanceOffset) % LOG_TAGS.length);
        int levelIndex = (int) (((zeroBasedSequence / LOG_TAGS.length) + instanceOffset)
                % LOG_LEVELS.length);
        String tag = LOG_TAGS[tagIndex];
        String level = LOG_LEVELS[levelIndex];
        String message = String.format(Locale.US,
                "package=%s instance=%s tag=%s level=%s session=%s seq=%d rate=%d elapsed_ms=%d",
                getPackageName(), instanceId, tag, level, sessionId, sequence, rate,
                SystemClock.elapsedRealtime() - startedAtElapsedMs);
        Log.println(LOG_PRIORITIES[levelIndex], tag, message);
    }

    private int getInstanceOffset() {
        if (instanceId == null || !instanceId.startsWith("fork")) return 0;
        try {
            return Math.max(0, Integer.parseInt(instanceId.substring(4))) % LOG_TAGS.length;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void waitUntil(long startAtElapsedMs) {
        while (running.get()) {
            long remainingMs = startAtElapsedMs - SystemClock.elapsedRealtime();
            if (remainingMs <= 0) return;
            LockSupport.parkNanos(Math.min(remainingMs, 100) * 1_000_000L);
            if (Thread.interrupted() && !running.get()) return;
        }
    }

    private void applyRate(int rate) {
        int safeRate = clampRate(rate);
        ratePerSecond.set(safeRate);
        preferences().edit().putInt(PREF_RATE, safeRate).apply();
        if (running.get()) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(true));
        }
        publishStatus();
    }

    private void publishStatus() {
        Intent status = new Intent(ACTION_STATUS).setPackage(CONTROLLER_PACKAGE);
        status.putExtra("agent_package", getPackageName());
        status.putExtra("instance_id", instanceId);
        status.putExtra("running", running.get());
        status.putExtra("rate", ratePerSecond.get());
        status.putExtra("emitted_lines", emittedLines.get());
        status.putExtra("elapsed_ms", running.get()
                ? Math.max(0, SystemClock.elapsedRealtime() - startedAtElapsedMs) : 0);
        status.putExtra(EXTRA_SESSION_ID, sessionId);
        sendBroadcast(status, CONTROL_PERMISSION);
    }

    private Notification buildNotification(boolean active) {
        String text = active
                ? instanceId + " running at " + ratePerSecond.get() + " lines/s"
                : instanceId + " preparing";
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("XCLogger stress agent")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(active)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "XCLogger stress agents", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private SharedPreferences preferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private static int clampRate(int rate) {
        return Math.max(MIN_RATE, Math.min(MAX_RATE, rate));
    }
}
