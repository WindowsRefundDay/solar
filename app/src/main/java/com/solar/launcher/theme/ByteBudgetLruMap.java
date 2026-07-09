package com.solar.launcher.theme;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Byte-budgeted LRU keyed by an injectable size function. Hand-rolled (not
 * android.util.LruCache) so it stays plain-JUnit testable — the size function is the only
 * android-specific bit (e.g. Bitmap::getByteCount), and tests can swap in a plain one.
 */
public final class ByteBudgetLruMap<K, V> extends LinkedHashMap<K, V> {

    public interface SizeOf<V> {
        int sizeOf(V value);
    }

    private final int maxBytes;
    private final SizeOf<V> sizeOf;
    private int currentBytes;

    public ByteBudgetLruMap(int maxBytes, SizeOf<V> sizeOf) {
        super(16, 0.75f, true);
        this.maxBytes = maxBytes;
        this.sizeOf = sizeOf;
    }

    @Override
    public V put(K key, V value) {
        V old = super.put(key, value);
        if (old != null) currentBytes -= sizeOf.sizeOf(old);
        if (value != null) currentBytes += sizeOf.sizeOf(value);
        trim();
        return old;
    }

    @Override
    public V remove(Object key) {
        V removed = super.remove(key);
        if (removed != null) currentBytes -= sizeOf.sizeOf(removed);
        return removed;
    }

    @Override
    public void clear() {
        super.clear();
        currentBytes = 0;
    }

    public void evictAll() {
        clear();
    }

    public int currentBytes() {
        return currentBytes;
    }

    private void trim() {
        Iterator<Map.Entry<K, V>> it = entrySet().iterator();
        while (currentBytes > maxBytes && it.hasNext()) {
            V v = it.next().getValue();
            it.remove();
            if (v != null) currentBytes -= sizeOf.sizeOf(v);
        }
    }
}
