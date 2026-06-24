package com.solar.launcher.soulseek;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Tracks which Soulseek peers support Reach wire-format reactions (via VERSION handshake). */
public final class ReachPeerCapabilities {
    private static final String KEY_REACH = "reach_capable_peers";
    private static final String KEY_LEGACY = "legacy_capable_peers";
    private static final String REACH_MARKER = "Reach for Innioasis";

    private ReachPeerCapabilities() {}

    public static void markFromVersion(SharedPreferences prefs, String peer, String clientDescription) {
        if (prefs == null || peer == null || peer.trim().isEmpty()) return;
        String desc = clientDescription != null ? clientDescription.trim() : "";
        if (desc.isEmpty()) return;
        try {
            JSONObject root = loadRoot(prefs);
            JSONArray reach = root.optJSONArray(KEY_REACH);
            JSONArray legacy = root.optJSONArray(KEY_LEGACY);
            if (reach == null) reach = new JSONArray();
            if (legacy == null) legacy = new JSONArray();
            removeFromArray(reach, peer);
            removeFromArray(legacy, peer);
            if (desc.contains(REACH_MARKER)) {
                reach.put(peer.trim());
            } else {
                legacy.put(peer.trim());
            }
            root.put(KEY_REACH, reach);
            root.put(KEY_LEGACY, legacy);
            prefs.edit().putString(PREFS_KEY, root.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static boolean isReach(SharedPreferences prefs, String peer) {
        if (peer == null || peer.trim().isEmpty()) return false;
        try {
            JSONArray reach = loadRoot(prefs).optJSONArray(KEY_REACH);
            return containsPeer(reach, peer.trim());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean useWireReactions(SharedPreferences prefs, String peer) {
        return isReach(prefs, peer);
    }

    private static final String PREFS_KEY = "reach_peer_capabilities";

    private static JSONObject loadRoot(SharedPreferences prefs) {
        try {
            return new JSONObject(prefs.getString(PREFS_KEY, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static boolean containsPeer(JSONArray arr, String peer) {
        if (arr == null) return false;
        for (int i = 0; i < arr.length(); i++) {
            if (peer.equals(arr.optString(i, ""))) return true;
        }
        return false;
    }

    private static void removeFromArray(JSONArray arr, String peer) {
        if (arr == null) return;
        for (int i = arr.length() - 1; i >= 0; i--) {
            if (peer.equals(arr.optString(i, ""))) arr.remove(i);
        }
    }
}
