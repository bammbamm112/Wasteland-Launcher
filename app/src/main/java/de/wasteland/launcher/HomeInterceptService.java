package de.wasteland.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

public class HomeInterceptService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!LauncherPrefs.isHomeGuardEnabled(this)) return;
        if (LauncherPrefs.isAmazonAllowedTemporarily(this)) return;

        String pkg = event.getPackageName().toString();
        if ("com.amazon.tv.launcher".equals(pkg) || "com.amazon.firehomestarter".equals(pkg)) {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        }
    }

    @Override
    public void onInterrupt() {
    }
}
