package de.wasteland.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

public class HomeInterceptService extends AccessibilityService {

    private long lastRedirectTime = 0L;
    private String lastForegroundPkg = "";
    private BroadcastReceiver screenWakeReceiver;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        registerWakeReceiver();
        bringWastelandToFront(true);
    }

    private void registerWakeReceiver() {
        if (screenWakeReceiver != null) {
            return;
        }
        screenWakeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (MainActivity.isAmazonBypassActive(context)) {
                    return;
                }
                scheduleWakeGuard();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        try {
            registerReceiver(screenWakeReceiver, filter);
        } catch (Exception ignored) {
        }
    }

    private boolean isAmazonHomePackage(String pkg) {
        return "com.amazon.tv.launcher".equals(pkg)
                || "com.amazon.firehomestarter".equals(pkg)
                || "com.amazon.firetv.launcher".equals(pkg);
    }

    private void scheduleWakeGuard() {
        Runnable check = new Runnable() {
            @Override
            public void run() {
                if (MainActivity.isAmazonBypassActive(HomeInterceptService.this)) {
                    return;
                }
                if (lastForegroundPkg == null || lastForegroundPkg.isEmpty()
                        || isAmazonHomePackage(lastForegroundPkg)
                        || "de.wasteland.launcher".equals(lastForegroundPkg)
                        || "com.amazon.ftv.screensaver".equals(lastForegroundPkg)
                        || "com.amazon.bueller.photos".equals(lastForegroundPkg)) {
                    bringWastelandToFront(true);
                }
            }
        };
        handler.post(check);
        handler.postDelayed(check, 900L);
        handler.postDelayed(check, 2200L);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) {
            return;
        }
        String pkg = pkgSeq.toString();
        if (!"android".equals(pkg) && !"com.android.systemui".equals(pkg)) {
            lastForegroundPkg = pkg;
        }
        if (isAmazonHomePackage(pkg)) {
            if (MainActivity.isAmazonBypassActive(this)) {
                return;
            }
            bringWastelandToFront(false);
        }
    }

    private void bringWastelandToFront(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && (now - lastRedirectTime < 350L)) {
            return;
        }
        lastRedirectTime = now;

        Intent launch = new Intent(this, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            startActivity(launch);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (screenWakeReceiver != null) {
            try {
                unregisterReceiver(screenWakeReceiver);
            } catch (Exception ignored) {
            }
            screenWakeReceiver = null;
        }
    }

    @Override
    public void onInterrupt() {
    }
}
