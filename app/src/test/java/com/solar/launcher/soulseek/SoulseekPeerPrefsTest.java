package com.solar.launcher.soulseek;

import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class SoulseekPeerPrefsTest {
    public static void main(String[] args) {
        SharedPreferences prefs = new FakePrefs();
        SoulseekPeerPrefs.setBlocked(prefs, "PeerOne", true);
        if (!SoulseekPeerPrefs.isBlocked(prefs, "peerone")) throw new AssertionError("blocked");
        SoulseekPeerPrefs.setFavorite(prefs, "PeerTwo", true);
        if (!SoulseekPeerPrefs.isFavorite(prefs, "PEERTWO")) throw new AssertionError("favorite");
        SoulseekPeerPrefs.setBlocked(prefs, "PeerOne", false);
        if (SoulseekPeerPrefs.isBlocked(prefs, "PeerOne")) throw new AssertionError("unblocked");
    }

    private static final class FakePrefs implements SharedPreferences {
        private final java.util.HashMap<String, String> map = new java.util.HashMap<String, String>();

        @Override public java.util.Map<String, ?> getAll() { return map; }
        @Override public String getString(String key, String def) {
            return map.containsKey(key) ? map.get(key) : def;
        }
        @Override public int getInt(String key, int def) { return def; }
        @Override public long getLong(String key, long def) { return def; }
        @Override public float getFloat(String key, float def) { return def; }
        @Override public boolean getBoolean(String key, boolean def) { return def; }
        @Override public boolean contains(String key) { return map.containsKey(key); }
        @Override public Editor edit() {
            return new Editor() {
                @Override public Editor putString(String key, String value) {
                    map.put(key, value); return this;
                }
                @Override public Editor putInt(String key, int value) { return this; }
                @Override public Editor putLong(String key, long value) { return this; }
                @Override public Editor putFloat(String key, float value) { return this; }
                @Override public Editor putBoolean(String key, boolean value) { return this; }
                @Override public Editor putStringSet(String key, Set<String> values) { return this; }
                @Override public Editor remove(String key) { map.remove(key); return this; }
                @Override public Editor clear() { map.clear(); return this; }
                @Override public boolean commit() { return true; }
                @Override public void apply() {}
            };
        }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override public Set<String> getStringSet(String key, Set<String> def) {
            return def != null ? def : new HashSet<String>();
        }
    }
}
