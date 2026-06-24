package com.solar.launcher.deezer;

import android.content.SharedPreferences;

/** Stores Deezer ARL cookie and quality preferences (impersonation auth). */
public final class DeezerAccount {
    /** Must match MainActivity {@code PREFS} / {@link com.solar.launcher.LibraryBrowsePrefs}. */
    public static final String PREFS_NAME = "SOLAR_SETTINGS";
    /** Legacy web-setup bug wrote here — migrated on read. */
    private static final String LEGACY_PREFS_NAME = "solar_prefs";

    public static final String PREF_ARL = "deezer_arl";
    public static final String PREF_ENABLED = "deezer_enabled";
    public static final String PREF_QUALITY = "deezer_quality";
    public static final String PREF_USER_NAME = "deezer_user_name";
    public static final String PREF_USER_ID = "deezer_user_id";
    public static final String PREF_PREMIUM = "deezer_premium";
    public static final String PREF_INCLUDE_IN_GET_MUSIC = "deezer_include_in_get_music";

    public static final String QUALITY_MP3 = "mp3";
    public static final String QUALITY_FLAC = "flac";

    private static final int MIN_ARL_LEN = 64;

    private DeezerAccount() {}

    public static boolean hasArl(SharedPreferences prefs) {
        if (prefs == null) return false;
        String arl = prefs.getString(PREF_ARL, "");
        return arl != null && arl.trim().length() >= MIN_ARL_LEN;
    }

    public static String loadArl(SharedPreferences prefs) {
        if (prefs == null) return "";
        String arl = prefs.getString(PREF_ARL, "");
        return arl != null ? arl.trim() : "";
    }

    /** Copy ARL from legacy {@code solar_prefs} if the user saved via web before the store fix. */
    public static void migrateLegacyArl(android.content.Context context, SharedPreferences prefs) {
        if (context == null || prefs == null || hasArl(prefs)) return;
        SharedPreferences legacy = context.getApplicationContext()
                .getSharedPreferences(LEGACY_PREFS_NAME, android.content.Context.MODE_PRIVATE);
        String legacyArl = legacy.getString(PREF_ARL, "");
        if (legacyArl != null && legacyArl.trim().length() >= MIN_ARL_LEN) {
            saveArl(prefs, legacyArl.trim());
            String user = legacy.getString(PREF_USER_NAME, "");
            if (user != null && !user.isEmpty()) {
                saveSessionInfo(prefs, user,
                        legacy.getString(PREF_USER_ID, ""),
                        legacy.getBoolean(PREF_PREMIUM, false));
            }
        }
    }

    public static boolean isEnabled(SharedPreferences prefs) {
        if (prefs == null) return false;
        return prefs.getBoolean(PREF_ENABLED, true);
    }

    public static String loadQuality(SharedPreferences prefs) {
        if (prefs == null) return QUALITY_MP3;
        String q = prefs.getString(PREF_QUALITY, QUALITY_MP3);
        return QUALITY_FLAC.equals(q) ? QUALITY_FLAC : QUALITY_MP3;
    }

    public static void saveArl(SharedPreferences prefs, String arl) {
        if (prefs == null) return;
        prefs.edit().putString(PREF_ARL, arl != null ? arl.trim() : "").commit();
    }

    public static void saveSessionInfo(SharedPreferences prefs, String userName, String userId,
            boolean premium) {
        if (prefs == null) return;
        prefs.edit()
                .putString(PREF_USER_NAME, userName != null ? userName : "")
                .putString(PREF_USER_ID, userId != null ? userId : "")
                .putBoolean(PREF_PREMIUM, premium)
                .commit();
    }

    public static void clear(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit()
                .remove(PREF_ARL)
                .remove(PREF_USER_NAME)
                .remove(PREF_USER_ID)
                .putBoolean(PREF_PREMIUM, false)
                .commit();
    }

    public static String displayUser(SharedPreferences prefs) {
        if (prefs == null) return "";
        String name = prefs.getString(PREF_USER_NAME, "");
        return name != null && !name.isEmpty() ? name : "";
    }

    public static boolean includeInGetMusic(SharedPreferences prefs) {
        if (prefs == null) return true;
        return prefs.getBoolean(PREF_INCLUDE_IN_GET_MUSIC, true);
    }
}
