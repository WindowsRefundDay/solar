package com.solar.launcher;

import android.content.SharedPreferences;

import com.solar.launcher.deezer.DeezerAccount;
import com.solar.launcher.soulseek.SoulseekAccount;

/** Which backends participate in Get Music / Get more / Find like this. */
public final class GetMusicSources {
    public static final int MODE_REACH_ONLY = 0;
    public static final int MODE_UNIFIED = 1;
    public static final int MODE_DEEZER_ONLY = 2;

    private GetMusicSources() {}

    public static boolean reachInGetMusic(SharedPreferences prefs, boolean reachEnabled) {
        return reachEnabled
                && SoulseekAccount.includeInGetMusic(prefs)
                && ConnectivityHelper.isReachPeerOk();
    }

    /** Deezer opted in and ARL saved — session may still be warming up. */
    public static boolean deezerConfiguredForGetMusic(SharedPreferences prefs, boolean deezerEnabled) {
        return deezerEnabled
                && DeezerAccount.includeInGetMusic(prefs)
                && DeezerAccount.hasArl(prefs);
    }

    public static boolean deezerInGetMusic(SharedPreferences prefs, boolean deezerEnabled) {
        return deezerConfiguredForGetMusic(prefs, deezerEnabled)
                && ConnectivityHelper.isDeezerLoginOk();
    }

    public static boolean anyAvailable(SharedPreferences prefs, boolean reachEnabled, boolean deezerEnabled) {
        return reachInGetMusic(prefs, reachEnabled)
                || deezerConfiguredForGetMusic(prefs, deezerEnabled);
    }

    public static int resolveMode(SharedPreferences prefs, boolean reachEnabled, boolean deezerEnabled) {
        boolean reach = reachInGetMusic(prefs, reachEnabled);
        boolean deezer = deezerConfiguredForGetMusic(prefs, deezerEnabled);
        if (reach && deezer) return MODE_UNIFIED;
        if (deezer) return MODE_DEEZER_ONLY;
        if (reach) return MODE_REACH_ONLY;
        return MODE_REACH_ONLY;
    }
}
