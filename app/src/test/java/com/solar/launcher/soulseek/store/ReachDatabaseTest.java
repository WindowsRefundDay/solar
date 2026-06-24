package com.solar.launcher.soulseek.store;

import com.solar.launcher.soulseek.SoulseekChatRooms;
import com.solar.launcher.soulseek.SoulseekMessaging;
import com.solar.launcher.soulseek.SoulseekWire;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JVM-safe tests for Reach persistence helpers (SQLite exercised on device). */
public class ReachDatabaseTest {

    @Test
    public void loadRoomListLegacy_parsesPrefsJson() {
        FakePrefs prefs = new FakePrefs();
        prefs.map.put("soulseek_room_list", "[{\"name\":\"Lobby\",\"users\":12}]");
        List<SoulseekWire.RoomEntry> rooms = SoulseekChatRooms.loadRoomListLegacy(prefs);
        if (rooms.size() != 1 || !"Lobby".equals(rooms.get(0).name) || rooms.get(0).userCount != 12) {
            throw new AssertionError("legacy room parse");
        }
    }

    @Test
    public void loadLegacy_pmInboxParses() {
        FakePrefs prefs = new FakePrefs();
        prefs.map.put("soulseek_pm_inbox",
                "[{\"id\":1,\"ts\":100,\"peer\":\"bob\",\"text\":\"hi\",\"in\":true}]");
        List<SoulseekMessaging.Message> msgs = SoulseekMessaging.loadLegacy(prefs);
        if (msgs.size() != 1 || !"bob".equals(msgs.get(0).peer)) {
            throw new AssertionError("legacy pm parse");
        }
    }

    @Test
    public void peerCacheEntry_freshness() {
        ReachDatabase.PeerCacheEntry fresh = new ReachDatabase.PeerCacheEntry(
                "u", "US", 10, true, System.currentTimeMillis());
        if (!fresh.isFresh()) {
            throw new AssertionError("expected fresh");
        }
        ReachDatabase.PeerCacheEntry stale = new ReachDatabase.PeerCacheEntry(
                "u", "US", 10, true, System.currentTimeMillis() - 600_000L);
        if (stale.isFresh()) {
            throw new AssertionError("expected stale");
        }
    }

    @Test
    public void roomListLegacy_emptyOnBadJson() {
        FakePrefs prefs = new FakePrefs();
        prefs.map.put("soulseek_room_list", "not-json");
        if (!SoulseekChatRooms.loadRoomListLegacy(prefs).isEmpty()) {
            throw new AssertionError("bad json");
        }
    }

    private static final class FakePrefs implements android.content.SharedPreferences {
        final Map<String, Object> map = new HashMap<String, Object>();

        @Override
        public Map<String, ?> getAll() { return map; }

        @Override
        public String getString(String key, String defValue) {
            Object v = map.get(key);
            return v instanceof String ? (String) v : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object v = map.get(key);
            return v instanceof Boolean ? (Boolean) v : defValue;
        }

        @Override
        public Editor edit() {
            return new Editor() {
                @Override
                public Editor putString(String key, String value) {
                    map.put(key, value);
                    return this;
                }

                @Override
                public Editor putBoolean(String key, boolean value) {
                    map.put(key, value);
                    return this;
                }

                @Override
                public boolean commit() { return true; }

                @Override
                public void apply() {}

                @Override
                public Editor putInt(String k, int v) { map.put(k, v); return this; }

                @Override
                public Editor putLong(String k, long v) { map.put(k, v); return this; }

                @Override
                public Editor putFloat(String k, float v) { map.put(k, v); return this; }

                @Override
                public Editor remove(String k) { map.remove(k); return this; }

                @Override
                public Editor clear() { map.clear(); return this; }

                @Override
                public Editor putStringSet(String k, java.util.Set<String> v) {
                    map.put(k, v);
                    return this;
                }
            };
        }

        @Override
        public int getInt(String k, int d) { return d; }
        @Override
        public long getLong(String k, long d) { return d; }
        @Override
        public float getFloat(String k, float d) { return d; }
        @Override
        public boolean contains(String k) { return map.containsKey(k); }
        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override
        public java.util.Set<String> getStringSet(String k, java.util.Set<String> d) { return d; }
    }
}
