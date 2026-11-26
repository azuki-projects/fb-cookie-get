package com.dansapk.nodejsrunner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class RunnerService extends Service {
    private static final String CHANNEL = "node_runner";
    private static final int NOTIFICATION_ID = 4107;
    private static volatile boolean runtimeStarted = false;
    private PowerManager.WakeLock wakeLock;

    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("node");
    }

    public native int startNodeWithArguments(String[] args);

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Preparing Node.js runtime"));
        applyWakeLock();
        startRuntimeOnce();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "SET_WAKE".equals(intent.getAction())) {
            applyWakeLock();
        }
        startForeground(NOTIFICATION_ID, buildNotification("Node.js runtime service active"));
        return START_STICKY;
    }

    private void startRuntimeOnce() {
        if (runtimeStarted) return;
        synchronized (RunnerService.class) {
            if (runtimeStarted) return;
            runtimeStarted = true;
        }
        new Thread(() -> {
            try {
                File home = new File(getFilesDir(), "nodejs-project");
                home.mkdirs();
                copyAssetIfMissing("nodejs-project/main.js", new File(home, "main.js"));
                String[] args = new String[] {
                    "node",
                    new File(home, "main.js").getAbsolutePath(),
                    new File(getFilesDir(), "projects").getAbsolutePath(),
                    "38271"
                };
                startNodeWithArguments(args);
            } catch (Exception e) {
                android.util.Log.e("NodeJSRunner", "Unable to start Node.js", e);
            }
        }, "NodeJS-Main").start();
    }

    private void copyAssetIfMissing(String asset, File target) throws Exception {
        if (target.exists()) return;
        target.getParentFile().mkdirs();
        try (InputStream in = getAssets().open(asset);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private void applyWakeLock() {
        SharedPreferences prefs = getSharedPreferences("runner", MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("keep_awake", true);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (enabled) {
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":NodeRunner");
                wakeLock.setReferenceCounted(false);
            }
            if (!wakeLock.isHeld()) wakeLock.acquire();
        } else if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Node.js Runner", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the embedded Node.js runtime available");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL)
                .setContentTitle("NodeJS Runner")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setOngoing(true)
                .build();
        }
        return new Notification.Builder(this)
            .setContentTitle("NodeJS Runner")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setOngoing(true)
            .build();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}
