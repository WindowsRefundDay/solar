package com.solar.launcher.soulseek;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Short-lived in-memory cache for remote peer bios and interests. */
public final class SoulseekPeerProfileCache {
    private static final long TTL_MS = 5L * 60L * 1000L;
    private static final Map<String, Entry> CACHE = new HashMap<String, Entry>();

    public static final class Entry {
        public final String bio;
        public final List<String> likes;
        public final List<String> dislikes;
        public final long fetchedAt;

        Entry(String bio, List<String> likes, List<String> dislikes, long fetchedAt) {
            this.bio = bio != null ? bio : "";
            this.likes = likes != null ? new ArrayList<String>(likes) : new ArrayList<String>();
            this.dislikes = dislikes != null ? new ArrayList<String>(dislikes) : new ArrayList<String>();
            this.fetchedAt = fetchedAt;
        }
    }

    private SoulseekPeerProfileCache() {}

    public static Entry get(String username) {
        if (username == null) return null;
        String key = username.trim().toLowerCase(Locale.US);
        synchronized (CACHE) {
            Entry e = CACHE.get(key);
            if (e == null) return null;
            if (System.currentTimeMillis() - e.fetchedAt > TTL_MS) {
                CACHE.remove(key);
                return null;
            }
            return e;
        }
    }

    public static void put(String username, String bio, List<String> likes, List<String> dislikes) {
        if (username == null) return;
        String key = username.trim().toLowerCase(Locale.US);
        synchronized (CACHE) {
            CACHE.put(key, new Entry(bio, likes, dislikes, System.currentTimeMillis()));
        }
    }

    static void clearForTest() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }
}
