package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Persists developer-support PMs that failed socket send; flushed on Wi‑Fi with backoff.
 * ponytail: prefs JSON queue — upgrade path is a SQLite outbox if volume grows.
 */
public final class SolarDeveloperOutbox {
    static final String PREF_OUTBOX = "solar_dev_outbox_v1";
    private static final long INTERVAL_WIFI_MS = 15L * 60L * 1000L;
    private static final long INTERVAL_CHARGING_MS = 2L * 60L * 1000L;
    private static final long FLUSH_SOON_DEBOUNCE_MS = 300_000L;
    private static volatile long lastFlushMs;
    private static volatile long lastSoonMs;
    private static volatile boolean soonScheduled;

    private SolarDeveloperOutbox() {}

    /** Queue a body for all three dev accounts (legacy full fan-out). */
    static void enqueue(Context ctx, String body) {
    }

    static void enqueue(Context ctx, String body, String[] recipients) {
    }

    static void flushSoon(final Context context, final SharedPreferences prefs,
            final SoulseekClient mainClient) {
    }

    public static void flushIfDue(final Context context, final SharedPreferences prefs,
            final SoulseekClient mainClient, final boolean onWifi, final boolean onCharger) {
    }

    static int flushAll(Context ctx, SharedPreferences prefs, SoulseekClient mainClient) {
        return 0;
    }

    private static String[] parseRecipients(JSONObject item) {
        JSONArray rec = item.optJSONArray("recipients");
        if (rec == null || rec.length() == 0) {
            return SolarDeveloperAccounts.developerUsernames();
        }
        String[] out = new String[rec.length()];
        for (int i = 0; i < rec.length(); i++) {
            out[i] = rec.optString(i, "");
        }
        return out;
    }

    private static JSONArray load(SharedPreferences prefs) {
        try {
            String raw = prefs.getString(PREF_OUTBOX, "[]");
            return new JSONArray(raw != null ? raw : "[]");
        } catch (Exception e) {
            return new JSONArray();
        }
    }
}
