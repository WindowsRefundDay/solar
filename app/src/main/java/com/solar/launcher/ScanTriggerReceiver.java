package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * ADB-triggerable library scan entry point for performance testing.
 *
 * <p>Usage:
 * <pre>
 *   adb shell am broadcast -a com.solar.launcher.action.START_LIBRARY_SCAN \
 *       -n com.solar.launcher/.ScanTriggerReceiver
 * </pre>
 *
 * The receiver launches MainActivity with a flag that causes it to start a scan.
 * For cold-start/full-scan measurements, clear the music library database first.
 */
public final class ScanTriggerReceiver extends BroadcastReceiver {

    static final String ACTION = "com.solar.launcher.action.START_LIBRARY_SCAN";
    static final String EXTRA_TRIGGER = "solar_trigger_scan";

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launch.putExtra(EXTRA_TRIGGER, true);
        context.startActivity(launch);
    }
}
