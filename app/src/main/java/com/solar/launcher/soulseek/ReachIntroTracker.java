package com.solar.launcher.soulseek;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Tracks whether Reach intro was sent to a peer or room. */
public final class ReachIntroTracker {
    private static final String PREFS_KEY = "reach_intro_tracker";
    private static final String KEY_PEERS = "peers";
    private static final String KEY_ROOMS = "rooms";

    private ReachIntroTracker() {}

    public static boolean needsIntroToPeer(SharedPreferences prefs, String peer) {
        return !contains(loadArray(prefs, KEY_PEERS), peer);
    }

    public static boolean needsIntroToRoom(SharedPreferences prefs, String room) {
        return !contains(loadArray(prefs, KEY_ROOMS), room);
    }

    public static void markIntroSentToPeer(SharedPreferences prefs, String peer) {
        add(prefs, KEY_PEERS, peer);
    }

    public static void markIntroSentToRoom(SharedPreferences prefs, String room) {
        add(prefs, KEY_ROOMS, room);
    }

    private static JSONArray loadArray(SharedPreferences prefs, String key) {
        try {
            JSONObject root = new JSONObject(prefs.getString(PREFS_KEY, "{}"));
            return root.optJSONArray(key);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static boolean contains(JSONArray arr, String value) {
        if (arr == null || value == null || value.trim().isEmpty()) return false;
        String v = value.trim();
        for (int i = 0; i < arr.length(); i++) {
            if (v.equalsIgnoreCase(arr.optString(i, ""))) return true;
        }
        return false;
    }

    private static void add(SharedPreferences prefs, String key, String value) {
        if (prefs == null || value == null || value.trim().isEmpty()) return;
        try {
            JSONObject root = new JSONObject(prefs.getString(PREFS_KEY, "{}"));
            JSONArray arr = root.optJSONArray(key);
            if (arr == null) arr = new JSONArray();
            String v = value.trim();
            if (contains(arr, v)) return;
            arr.put(v);
            root.put(key, arr);
            prefs.edit().putString(PREFS_KEY, root.toString()).apply();
        } catch (Exception ignored) {}
    }
}
