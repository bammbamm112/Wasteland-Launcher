package de.wasteland.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Leitet den Amazon Fire TV Home-Button sicher auf Wasteland TV Launcher um,
 * solange der Nutzer nicht im PIP-OS Menue die Amazon-Startseite angefordert hat.
 */
public class HomeInterceptService extends AccessibilityService {

    private long lastRedirectTime = 0L;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence pkg = event.getPackageName();
        if (pkg == null) {
            return;
        }
        if ("com.amazon.tv.launcher".equals(pkg.toString())) {
            if (MainActivity.isAmazonBypassActive(this)) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastRedirectTime < 750L) {
                return;
            }
            lastRedirectTime = now;

            Intent launch = new Intent(this, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(launch);
        }
    }

    @Override
    public void onInterrupt() {
    }
}
