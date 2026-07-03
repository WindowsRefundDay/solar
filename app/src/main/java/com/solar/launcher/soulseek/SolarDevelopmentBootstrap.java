package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;

import com.solar.launcher.ReachPolicy;

/**
 * One-shot per process: ping developer inboxes when Reach comes online and ensure
 * the virtual Solar Development inbox row exists locally.
 */
public final class SolarDevelopmentBootstrap {
    private static volatile boolean startupDone;

    private SolarDevelopmentBootstrap() {}

    /** After Soulseek login — marker-only Reach intro PM + diagnostic batch (once per app start). */
    public static void onSoulseekConnected(final Context context, final SharedPreferences prefs,
            final SoulseekClient client) {
    }

    static void sendDeveloperOnlinePing(Context context, SharedPreferences prefs,
            SoulseekClient client) {
    }

    public static void ensureVirtualInboxPlaceholder(Context context, SharedPreferences prefs) {
    }

    /** Test hook — reset process guard. */
    static void resetForTest() {
        startupDone = false;
    }
}
