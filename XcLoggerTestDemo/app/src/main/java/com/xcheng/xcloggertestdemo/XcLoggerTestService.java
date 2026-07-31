package com.xcheng.xcloggertestdemo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * Foreground service that bridges UI and XCLogger AIDL.
 * Holds the XcLoggerClient singleton and registers AutoTestReceiver.
 *
 * Best-practice: Service binds XCLogger once and survives UI lifecycle.
 * Activities call getClient() to access AIDL methods directly.
 */
public class XcLoggerTestService extends Service {
    private static final String TAG = "XcLoggerTestService";
    private static final String CHANNEL_ID = "xclogger_test_channel";
    private static final int NOTIF_ID = 3001;

    public static final String EXTRA_TARGET_PKG = "target_package";
    public static final String DEFAULT_PKG = "com.xcheng.xclogger";
    private static final String CUSTOMER_PKG = "com.ko.xclogger";
    private static final String PREFS = "xclogger_demo";
    private static final String PREF_TARGET_PKG = "target_package";

    private static XcLoggerClient client;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        Log.i(TAG, "[SERVICE] Created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String pkg = intent != null && intent.getStringExtra(EXTRA_TARGET_PKG) != null
                ? intent.getStringExtra(EXTRA_TARGET_PKG) : resolveTargetPackage(this);
        saveTargetPackage(this, pkg);

        if (client == null || !client.isBound()) {
            client = new XcLoggerClient(this, pkg);
            // Forward listener results to logcat for automated testing
            client.setResultListener((source, op, success, message) ->
                    Log.i("AutoTest", "[RESULT] source=" + source + " op=" + op
                            + " result=" + (success ? "PASS" : "FAIL") + " message=" + message));
            client.bind();
            Log.i(TAG, "[SERVICE] Bound to " + pkg);
        }

        // Register broadcast receiver for automated testing
        AutoTestReceiver.register(this);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        AutoTestReceiver.unregister(this);
        if (client != null) {
            client.unbind();
            client = null;
        }
        Log.i(TAG, "[SERVICE] Destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    /** Get the shared XcLoggerClient instance. May return null before bind completes. */
    public static XcLoggerClient getClient() { return client; }

    static String resolveTargetPackage(Context context) {
        String saved = context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(PREF_TARGET_PKG, null);
        if (saved != null && isInstalled(context, saved)) return saved;
        if (isInstalled(context, CUSTOMER_PKG) && !isInstalled(context, DEFAULT_PKG)) {
            return CUSTOMER_PKG;
        }
        return DEFAULT_PKG;
    }

    static void saveTargetPackage(Context context, String packageName) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_TARGET_PKG, packageName).apply();
    }

    private static boolean isInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // ─── Notification ───────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "XCLogger Test",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("XCLogger test service running");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("XCLogger Test")
                .setContentText("Test service active")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pi)
                .build();
    }
}
