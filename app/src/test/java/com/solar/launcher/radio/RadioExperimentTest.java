package com.solar.launcher.radio;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RadioExperimentTest {

    private SharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = new MemPrefs();
        com.solar.launcher.DeviceFeatures.setCachedFamilyForTest("y2");
    }

    @Test
    public void disabledByDefaultOnY2() {
        if (RadioExperiment.isEnabled(prefs)) {
            throw new AssertionError("radio experiment should default off on Y2");
        }
    }

    @Test
    public void enabledWhenPrefOnOnY2() {
        prefs.edit().putBoolean(RadioExperiment.PREF_RADIO_EXPERIMENT, true).commit();
        if (!RadioExperiment.isEnabled(prefs)) {
            throw new AssertionError("radio experiment should be on on Y2 when pref is set");
        }
    }

    @Test
    public void alwaysEnabledOnY1() {
        com.solar.launcher.DeviceFeatures.setCachedFamilyForTest("y1");
        if (!RadioExperiment.isEnabled(prefs)) {
            throw new AssertionError("radio experiment should be enabled by default on Y1");
        }
        prefs.edit().putBoolean(RadioExperiment.PREF_RADIO_EXPERIMENT, false).commit();
        if (!RadioExperiment.isEnabled(prefs)) {
            throw new AssertionError("radio experiment should remain enabled on Y1 even if pref is false");
        }
    }

    private static final class MemPrefs implements SharedPreferences {
        final Map<String, Object> map = new HashMap<String, Object>();

        @Override public Map<String, ?> getAll() { return map; }
        @Override public String getString(String key, String def) {
            Object v = map.get(key);
            return v instanceof String ? (String) v : def;
        }
        @Override public int getInt(String key, int def) {
            Object v = map.get(key);
            return v instanceof Integer ? (Integer) v : def;
        }
        @Override public long getLong(String key, long def) { return def; }
        @Override public float getFloat(String key, float def) { return def; }
        @Override public boolean getBoolean(String key, boolean def) {
            Object v = map.get(key);
            return v instanceof Boolean ? (Boolean) v : def;
        }
        @Override public Set<String> getStringSet(String key, Set<String> def) { return def; }
        @Override public boolean contains(String key) { return map.containsKey(key); }
        @Override public Editor edit() {
            return new Editor() {
                @Override public Editor putString(String key, String value) { map.put(key, value); return this; }
                @Override public Editor putInt(String key, int value) { map.put(key, value); return this; }
                @Override public Editor putLong(String key, long value) { map.put(key, value); return this; }
                @Override public Editor putFloat(String key, float value) { map.put(key, value); return this; }
                @Override public Editor putBoolean(String key, boolean value) { map.put(key, value); return this; }
                @Override public Editor putStringSet(String key, Set<String> values) { return this; }
                @Override public Editor remove(String key) { map.remove(key); return this; }
                @Override public Editor clear() { map.clear(); return this; }
                @Override public boolean commit() { return true; }
                @Override public void apply() { commit(); }
            };
        }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    }
}
