package de.wasteland.launcher;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;

import java.util.List;

/**
 * High-speed Home-Button & Anti-Amazon Guard for Fire OS 6:
 * 1. Listens directly to ACTION_CLOSE_SYSTEM_DIALOGS (reason="homekey") -> 0ms reaction!
 * 2. Polls UsageEvents every 95ms (plus ActivityManager fallback) to catch com.amazon.tv.launcher
 *    and com.amazon.firehomestarter immediately and bring Wasteland TV Launcher back to front.
 * 3. Instant wake-up guard on ACTION_SCREEN_ON / ACTION_USER_PRESENT.
 */
public class HomeGuardService extends Service {
    private static final String CHANNEL_ID = "wasteland_home_guard_v62";
    private static final int NOTIF_ID = 6002;
    private static final long POLL_INTERVAL_MS = 95L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastHandledAmazonEventTs = 0L;
    private long lastBringFrontTime = 0L;
    private boolean receiversRegistered = false;

    private final BroadcastReceiver systemEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (!LauncherPrefs.isHomeGuardEnabled(context)) return;
            if (LauncherPrefs.isAmazonAllowedTemporarily(context)) return;

            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                String reason = intent.getStringExtra("reason");
                if ("homekey".equals(reason) || "dream".equals(reason)) {
                    // Home button pressed! Immediately pull Wasteland Launcher forward
                    bringWastelandToFront(true);
                    // Second safety pulse 140ms later in case FireHomeStarter fires right after
                    handler.postDelayed(() -> {
                        if (LauncherPrefs.isHomeGuardEnabled(context)
                                && !LauncherPrefs.isAmazonAllowedTemporarily(context)) {
                            bringWastelandToFront(true);
                        }
                    }, 140);
                }
            } else if (Intent.ACTION_SCREEN_ON.equals(action)
                    || Intent.ACTION_USER_PRESENT.equals(action)) {
                bringWastelandToFront(true);
                handler.postDelayed(() -> {
                    if (LauncherPrefs.isHomeGuardEnabled(context)
                            && !LauncherPrefs.isAmazonAllowedTemporarily(context)) {
                        bringWastelandToFront(true);
                    }
                }, 220);
            }
        }
    };

    private final Runnable fastPollRunnable = new Runnable() {
        @Override
        public void run() {
            checkForegroundAppFast();
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public static boolean hasUsageStatsPermission(Context ctx) {
        try {
            AppOpsManager appOps = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), ctx.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startAsForeground();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(systemEventReceiver, filter);
        receiversRegistered = true;

        handler.post(fastPollRunnable);
    }

    private void startAsForeground() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "Wasteland Home-Schutz", NotificationManager.IMPORTANCE_MIN);
                nm.createNotificationChannel(ch);
            }
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        Notification notif = builder
                .setContentTitle("Wasteland TV Launcher v6.2")
                .setContentText("Sofort-Home-Schutz aktiv")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, notif);
    }

    private void checkForegroundAppFast() {
        if (!LauncherPrefs.isHomeGuardEnabled(this)) return;
        if (LauncherPrefs.isAmazonAllowedTemporarily(this)) return;

        boolean caughtAmazon = false;

        if (hasUsageStatsPermission(this)) {
            try {
                UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                if (usm != null) {
                    long now = System.currentTimeMillis();
                    UsageEvents events = usm.queryEvents(now - 2000, now);
                    String latestPkg = null;
                    long latestTs = 0L;
                    UsageEvents.Event ev = new UsageEvents.Event();
                    while (events.hasNextEvent()) {
                        events.getNextEvent(ev);
                        if (ev.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                            if (ev.getTimeStamp() >= latestTs) {
                                latestTs = ev.getTimeStamp();
                                latestPkg = ev.getPackageName();
                            }
                        }
                    }
                    if (latestPkg != null && isAmazonHomePackage(latestPkg) && latestTs > lastHandledAmazonEventTs) {
                        lastHandledAmazonEventTs = latestTs;
                        caughtAmazon = true;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Secondary fallback via ActivityManager running tasks
        if (!caughtAmazon) {
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                    if (procs != null && !procs.isEmpty()) {
                        ActivityManager.RunningAppProcessInfo top = procs.get(0);
                        if (top.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                                && top.processName != null && isAmazonHomePackage(top.processName)) {
                            caughtAmazon = true;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (caughtAmazon) {
            bringWastelandToFront(false);
        }
    }

    private boolean isAmazonHomePackage(String pkg) {
        return "com.amazon.tv.launcher".equals(pkg)
                || "com.amazon.firehomestarter".equals(pkg);
    }

    private void bringWastelandToFront(boolean forceImmediate) {
        long now = System.currentTimeMillis();
        if (!forceImmediate && (now - lastBringFrontTime < 220)) {
            return;
        }
        lastBringFrontTime = now;
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(i);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(fastPollRunnable);
        if (receiversRegistered) {
            try {
                unregisterReceiver(systemEventReceiver);
            } catch (Exception ignored) {
            }
            receiversRegistered = false;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
