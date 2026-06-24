package com.solar.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import com.solar.launcher.deezer.DeezerAccount;
import com.solar.launcher.soulseek.SoulseekAccount;

/** Which backends participate in Get Music / Get more / Find like this. */
public final class GetMusicSources {
    public static final int MODE_REACH_ONLY = 0;
    public static final int MODE_UNIFIED = 1;
    public static final int MODE_DEEZER_ONLY = 2;

    /** Subtitle kind for the Get Music status / hint strip. */
    public static final int SUBTITLE_BOTH = 0;
    public static final int SUBTITLE_SOULSEEK = 1;
    public static final int SUBTITLE_DEEZER = 2;
    public static final int SUBTITLE_NONE = 3;

    private GetMusicSources() {}

    public static boolean reachInGetMusic(SharedPreferences prefs, boolean reachEnabled) {
        return reachEnabled
                && SoulseekAccount.includeInGetMusic(prefs)
                && ConnectivityHelper.isReachPeerOk();
    }

    /** Alias — Reach search in unified Get Music. */
    public static boolean reachSearchInGetMusic(SharedPreferences prefs, boolean reachEnabled) {
        return reachInGetMusic(prefs, reachEnabled);
    }

    /**
     * Deezer opted in for Get Music search (public API — no live session required).
     */
    public static boolean deezerSearchInGetMusic(SharedPreferences prefs, boolean deezerEnabled) {
        return deezerEnabled && DeezerAccount.includeInGetMusic(prefs);
    }

    /** Deezer opted in and ARL saved — session may still be warming up. */
    public static boolean deezerConfiguredForGetMusic(SharedPreferences prefs, boolean deezerEnabled) {
        return deezerEnabled
                && DeezerAccount.includeInGetMusic(prefs)
                && DeezerAccount.hasArl(prefs);
    }

    /** Deezer download / play / queue in Get Music (needs valid session). */
    public static boolean deezerPlayInGetMusic(SharedPreferences prefs, boolean deezerEnabled) {
        return deezerConfiguredForGetMusic(prefs, deezerEnabled)
                && ConnectivityHelper.isDeezerLoginOk();
    }

    /** @deprecated use {@link #deezerPlayInGetMusic} */
    public static boolean deezerInGetMusic(SharedPreferences prefs, boolean deezerEnabled) {
        return deezerPlayInGetMusic(prefs, deezerEnabled);
    }

    public static boolean anyAvailable(SharedPreferences prefs, boolean reachEnabled, boolean deezerEnabled) {
        return reachSearchInGetMusic(prefs, reachEnabled)
                || deezerSearchInGetMusic(prefs, deezerEnabled);
    }

    public static boolean anyAvailable(Context context, SharedPreferences prefs,
            boolean reachEnabled, boolean deezerEnabled) {
        if (context != null && !ConnectivityHelper.isOnline(context)) return false;
        return anyAvailable(prefs, reachEnabled, deezerEnabled);
    }

    public static int resolveMode(SharedPreferences prefs, boolean reachEnabled, boolean deezerEnabled) {
        return resolveGetMusicUiMode(prefs, reachEnabled, deezerEnabled);
    }

    /** Which backends to query for a Get Music search. */
    public static int resolveGetMusicUiMode(SharedPreferences prefs, boolean reachEnabled,
            boolean deezerEnabled) {
        boolean reach = reachSearchInGetMusic(prefs, reachEnabled);
        boolean deezer = deezerSearchInGetMusic(prefs, deezerEnabled);
        if (reach && deezer) return MODE_UNIFIED;
        if (deezer) return MODE_DEEZER_ONLY;
        if (reach) return MODE_REACH_ONLY;
        return MODE_REACH_ONLY;
    }

    /** Dynamic subtitle for Get Music screens based on active search sources. */
    public static int activeSourceSubtitle(SharedPreferences prefs, boolean reachEnabled,
            boolean deezerEnabled) {
        boolean reach = reachSearchInGetMusic(prefs, reachEnabled);
        boolean deezer = deezerSearchInGetMusic(prefs, deezerEnabled);
        if (reach && deezer) return SUBTITLE_BOTH;
        if (reach) return SUBTITLE_SOULSEEK;
        if (deezer) return SUBTITLE_DEEZER;
        return SUBTITLE_NONE;
    }

    public static int activeSourceSubtitleRes(SharedPreferences prefs, boolean reachEnabled,
            boolean deezerEnabled) {
        switch (activeSourceSubtitle(prefs, reachEnabled, deezerEnabled)) {
            case SUBTITLE_BOTH: return R.string.get_music_sources_both;
            case SUBTITLE_SOULSEEK: return R.string.get_music_sources_soulseek;
            case SUBTITLE_DEEZER: return R.string.get_music_sources_deezer;
            default: return R.string.get_music_sources_none;
        }
    }
}
