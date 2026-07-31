package com.xcheng.xcloggertestdemo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates the local stress service and five separately installed stress agents. */
public final class StressFleetController {
    public static final String CONTROL_PERMISSION =
            "com.xcheng.xcloggertestdemo.permission.CONTROL_STRESS";
    public static final String ACTION_STATUS =
            "com.xcheng.xcloggertestdemo.action.STRESS_AGENT_STATUS";

    private static final String ACTION_START =
            "com.xcheng.xcloggertestdemo.action.STRESS_AGENT_START";
    private static final String ACTION_STOP =
            "com.xcheng.xcloggertestdemo.action.STRESS_AGENT_STOP";
    private static final String ACTION_SET_RATE =
            "com.xcheng.xcloggertestdemo.action.STRESS_AGENT_SET_RATE";
    private static final String AGENT_SERVICE =
            "com.xcheng.xcloggerstressagent.StressAgentService";
    private static final long SYNCHRONIZED_START_DELAY_MS = 1200;
    private static final long STATUS_STALE_MS = 3500;

    public static final String[] AGENT_PACKAGES = {
            "com.xcheng.xcloggertestdemo.fork1",
            "com.xcheng.xcloggertestdemo.fork2",
            "com.xcheng.xcloggertestdemo.fork3",
            "com.xcheng.xcloggertestdemo.fork4",
            "com.xcheng.xcloggertestdemo.fork5"
    };

    private static final Map<String, AgentStatus> statuses = new ConcurrentHashMap<>();

    private StressFleetController() {
    }

    public static ControlResult startAll(Context context, int rate) {
        Context app = context.getApplicationContext();
        String sessionId = UUID.randomUUID().toString();
        long startAt = SystemClock.elapsedRealtime() + SYNCHRONIZED_START_DELAY_MS;
        int commanded = 1;
        int installedAgents = 0;
        int failures = 0;

        StressTestService.start(app, rate, startAt, sessionId);
        for (String packageName : AGENT_PACKAGES) {
            if (!isInstalled(app, packageName)) continue;
            installedAgents++;
            try {
                Intent command = command(packageName, ACTION_START)
                        .putExtra("rate", rate)
                        .putExtra("start_at_elapsed_ms", startAt)
                        .putExtra("session_id", sessionId);
                app.startForegroundService(command);
                commanded++;
            } catch (RuntimeException e) {
                failures++;
                statuses.put(packageName, AgentStatus.failed(packageName, e));
            }
        }
        return new ControlResult(commanded, installedAgents, failures);
    }

    public static ControlResult stopAll(Context context) {
        Context app = context.getApplicationContext();
        StressTestService.stop(app);
        int commanded = 1;
        int installedAgents = 0;
        int failures = 0;
        for (String packageName : AGENT_PACKAGES) {
            if (!isInstalled(app, packageName)) continue;
            installedAgents++;
            try {
                app.startService(command(packageName, ACTION_STOP));
                commanded++;
            } catch (RuntimeException e) {
                failures++;
                statuses.put(packageName, AgentStatus.failed(packageName, e));
            }
        }
        return new ControlResult(commanded, installedAgents, failures);
    }

    public static ControlResult setRateAll(Context context, int rate) {
        Context app = context.getApplicationContext();
        StressTestService.setRate(app, rate);
        int commanded = 1;
        int installedAgents = 0;
        int failures = 0;
        for (String packageName : AGENT_PACKAGES) {
            if (!isInstalled(app, packageName)) continue;
            installedAgents++;
            try {
                app.startService(command(packageName, ACTION_SET_RATE).putExtra("rate", rate));
                commanded++;
            } catch (RuntimeException e) {
                failures++;
                statuses.put(packageName, AgentStatus.failed(packageName, e));
            }
        }
        return new ControlResult(commanded, installedAgents, failures);
    }

    public static void recordStatus(Intent intent) {
        if (intent == null || !ACTION_STATUS.equals(intent.getAction())) return;
        String packageName = intent.getStringExtra("agent_package");
        if (!isKnownAgent(packageName)) return;
        statuses.put(packageName, new AgentStatus(
                packageName,
                intent.getStringExtra("instance_id"),
                intent.getBooleanExtra("running", false),
                intent.getIntExtra("rate", 0),
                intent.getLongExtra("emitted_lines", 0),
                intent.getLongExtra("elapsed_ms", 0),
                SystemClock.elapsedRealtime(),
                null));
    }

    public static String formatFleetStatus(Context context, StressTestService.Status localStatus) {
        int installedAgents = 0;
        int runningAgents = 0;
        long totalLines = localStatus.emittedLines;
        StringBuilder details = new StringBuilder();
        long now = SystemClock.elapsedRealtime();

        for (int i = 0; i < AGENT_PACKAGES.length; i++) {
            String packageName = AGENT_PACKAGES[i];
            String name = "fork" + (i + 1);
            if (!isInstalled(context, packageName)) {
                append(details, name, "not installed");
                continue;
            }
            installedAgents++;
            AgentStatus status = statuses.get(packageName);
            if (status == null) {
                append(details, name, "waiting for status");
            } else if (status.error != null) {
                append(details, name, "command failed");
            } else if (!status.running) {
                append(details, name, "stopped");
            } else if (now - status.receivedAtElapsedMs > STATUS_STALE_MS) {
                append(details, name, "status stale");
            } else {
                runningAgents++;
                totalLines += status.emittedLines;
                append(details, name, status.rate + "/s, " + status.emittedLines + " lines");
            }
        }

        int runningTotal = runningAgents + (localStatus.running ? 1 : 0);
        return String.format(Locale.US,
                "Fleet: %d/6 running | agents installed: %d/5 | total emitted: %,d\n%s",
                runningTotal, installedAgents, totalLines, details.toString());
    }

    public static int getInstalledAgentCount(Context context) {
        int count = 0;
        for (String packageName : AGENT_PACKAGES) {
            if (isInstalled(context, packageName)) count++;
        }
        return count;
    }

    private static Intent command(String packageName, String action) {
        return new Intent(action).setComponent(new ComponentName(packageName, AGENT_SERVICE));
    }

    private static boolean isInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static boolean isKnownAgent(String packageName) {
        if (packageName == null) return false;
        for (String known : AGENT_PACKAGES) {
            if (known.equals(packageName)) return true;
        }
        return false;
    }

    private static void append(StringBuilder builder, String name, String state) {
        if (builder.length() > 0) builder.append(" | ");
        builder.append(name).append(':').append(state);
    }

    public static final class ControlResult {
        public final int commandedInstances;
        public final int installedAgents;
        public final int failures;

        ControlResult(int commandedInstances, int installedAgents, int failures) {
            this.commandedInstances = commandedInstances;
            this.installedAgents = installedAgents;
            this.failures = failures;
        }
    }

    private static final class AgentStatus {
        final String packageName;
        final String instanceId;
        final boolean running;
        final int rate;
        final long emittedLines;
        final long elapsedMs;
        final long receivedAtElapsedMs;
        final String error;

        AgentStatus(String packageName, String instanceId, boolean running, int rate,
                    long emittedLines, long elapsedMs, long receivedAtElapsedMs, String error) {
            this.packageName = packageName;
            this.instanceId = instanceId;
            this.running = running;
            this.rate = rate;
            this.emittedLines = emittedLines;
            this.elapsedMs = elapsedMs;
            this.receivedAtElapsedMs = receivedAtElapsedMs;
            this.error = error;
        }

        static AgentStatus failed(String packageName, RuntimeException error) {
            return new AgentStatus(packageName, "", false, 0, 0, 0,
                    SystemClock.elapsedRealtime(), error.getClass().getSimpleName());
        }
    }
}
