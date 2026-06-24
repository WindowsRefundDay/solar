package com.solar.launcher.soulseek;

import android.content.SharedPreferences;

import org.json.JSONObject;

/** Maps Reach temp file paths to the Soulseek peer who shared them. */
public final class ReachTrackProvenance {
    private static final String PREFS = "reach_track_provenance";

    private ReachTrackProvenance() {}

    public static void record(SharedPreferences prefs, String absolutePath, String peerUsername) {
        if (prefs == null || absolutePath == null || absolutePath.isEmpty()
                || peerUsername == null || peerUsername.trim().isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(prefs.getString(PREFS, "{}"));
            root.put(absolutePath, peerUsername.trim());
            prefs.edit().putString(PREFS, root.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static String peerForPath(SharedPreferences prefs, String absolutePath) {
        if (prefs == null || absolutePath == null || absolutePath.isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(prefs.getString(PREFS, "{}"));
            String peer = root.optString(absolutePath, "");
            return peer.isEmpty() ? null : peer;
        } catch (Exception e) {
            return null;
        }
    }

    public static void removePath(SharedPreferences prefs, String absolutePath) {
        if (prefs == null || absolutePath == null || absolutePath.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(prefs.getString(PREFS, "{}"));
            if (!root.has(absolutePath)) return;
            root.remove(absolutePath);
            prefs.edit().putString(PREFS, root.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void renamePath(SharedPreferences prefs, String oldPath, String newPath) {
        if (prefs == null || oldPath == null || newPath == null || oldPath.equals(newPath)) return;
        String peer = peerForPath(prefs, oldPath);
        removePath(prefs, oldPath);
        if (peer != null) record(prefs, newPath, peer);
    }
}
