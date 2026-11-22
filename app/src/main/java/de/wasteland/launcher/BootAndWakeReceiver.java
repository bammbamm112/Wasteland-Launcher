package de.wasteland.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootAndWakeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        if (MainActivity.isAmazonBypassActive(context)) {
            return;
        }
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            context.startActivity(launch);
        } catch (Exception ignored) {
        }
    }
}
