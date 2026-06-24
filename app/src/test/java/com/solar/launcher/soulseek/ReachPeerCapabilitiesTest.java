package com.solar.launcher.soulseek;

import android.content.SharedPreferences;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ReachPeerCapabilitiesTest {

    @Test
    public void markReachFromVersion() {
        FakePrefs prefs = new FakePrefs();
        ReachPeerCapabilities.markFromVersion(prefs, "alice",
                "Reach for Innioasis Y1");
        if (!ReachPeerCapabilities.isReach(prefs, "alice")) throw new AssertionError("reach");
        if (!ReachPeerCapabilities.useWireReactions(prefs, "alice")) throw new AssertionError("wire");
    }

    @Test
    public void markLegacyFromVersion() {
        FakePrefs prefs = new FakePrefs();
        ReachPeerCapabilities.markFromVersion(prefs, "bob", "SoulseekQt 2024.6.1");
        if (ReachPeerCapabilities.isReach(prefs, "bob")) throw new AssertionError("not reach");
        if (ReachPeerCapabilities.useWireReactions(prefs, "bob")) throw new AssertionError("no wire");
    }

    @Test
    public void unknownPeerDefaultsToLegacy() {
        FakePrefs prefs = new FakePrefs();
        if (ReachPeerCapabilities.useWireReactions(prefs, "stranger")) {
            throw new AssertionError("unknown should not use wire");
        }
    }

    private static final class FakePrefs implements SharedPreferences {
        private final Map<String, String> map = new HashMap<String, String>();

        @Override
        public String getString(String key, String defValue) {
            return map.containsKey(key) ? map.get(key) : defValue;
        }

        @Override
        public Editor edit() {
            return new Editor() {
                private final Map<String, String> pending = new HashMap<String, String>(map);

                @Override
                public Editor putString(String key, String value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public void apply() {
                    map.clear();
                    map.putAll(pending);
                }

                @Override public Editor putInt(String key, int value) { return this; }
                @Override public Editor putLong(String key, long value) { return this; }
                @Override public Editor putFloat(String key, float value) { return this; }
                @Override public Editor putBoolean(String key, boolean value) { return this; }
                @Override public Editor putStringSet(String key, Set<String> values) { return this; }
                @Override public Editor remove(String key) { pending.remove(key); return this; }
                @Override public Editor clear() { pending.clear(); return this; }
                @Override public boolean commit() { apply(); return true; }
            };
        }

        @Override public Map<String, ?> getAll() { return map; }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) { return defValues; }
        @Override public int getInt(String key, int defValue) { return defValue; }
        @Override public long getLong(String key, long defValue) { return defValue; }
        @Override public float getFloat(String key, float defValue) { return defValue; }
        @Override public boolean getBoolean(String key, boolean defValue) { return defValue; }
        @Override public boolean contains(String key) { return map.containsKey(key); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    }
}
