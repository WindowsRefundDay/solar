package com.solar.launcher.deezer;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/** Persists Deezer API metadata for temp and saved files (keyed by absolute path). */
public final class DeezerMetadata {
    private static final String KEY_TITLE = "meta_title_";
    private static final String KEY_ARTIST = "meta_artist_";
    private static final String KEY_ALBUM = "meta_album_";
    private static final String KEY_COVER_URL = "meta_cover_url_";

    private DeezerMetadata() {}

    public static void saveForResult(Context ctx, File file, DeezerResult r) {
        if (ctx == null || file == null || r == null) return;
        SharedPreferences prefs = prefs(ctx);
        String path = file.getAbsolutePath();
        prefs.edit()
                .putString(KEY_TITLE + path, r.title)
                .putString(KEY_ARTIST + path, r.artist)
                .putString(KEY_ALBUM + path, r.album)
                .putString(KEY_COVER_URL + path, r.coverUrl)
                .commit();
    }

    public static void saveForTrackData(Context ctx, File file, DeezerTrackData track) {
        if (ctx == null || file == null || track == null) return;
        SharedPreferences prefs = prefs(ctx);
        String path = file.getAbsolutePath();
        SharedPreferences.Editor ed = prefs.edit()
                .putString(KEY_TITLE + path, track.title)
                .putString(KEY_ARTIST + path, track.artist)
                .putString(KEY_ALBUM + path, track.album);
        if (track.albumPicture != null && !track.albumPicture.isEmpty()) {
            ed.putString(KEY_COVER_URL + path, track.albumPicture);
        }
        ed.commit();
    }

    public static boolean hasMetadata(SharedPreferences prefs, String absolutePath) {
        if (prefs == null || absolutePath == null) return false;
        return prefs.contains(KEY_TITLE + absolutePath)
                || prefs.contains(KEY_ARTIST + absolutePath);
    }

    public static String title(SharedPreferences prefs, String path, String fallback) {
        if (prefs == null || path == null) return fallback;
        return prefs.getString(KEY_TITLE + path, fallback);
    }

    public static String artist(SharedPreferences prefs, String path, String fallback) {
        if (prefs == null || path == null) return fallback;
        return prefs.getString(KEY_ARTIST + path, fallback);
    }

    public static String album(SharedPreferences prefs, String path, String fallback) {
        if (prefs == null || path == null) return fallback;
        return prefs.getString(KEY_ALBUM + path, fallback);
    }

    public static String coverUrl(SharedPreferences prefs, String path) {
        if (prefs == null || path == null) return "";
        return prefs.getString(KEY_COVER_URL + path, "");
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(DeezerAccount.PREFS_NAME, Context.MODE_PRIVATE);
    }
}
