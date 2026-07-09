package com.solar.launcher.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ByteBudgetLruMapTest {

    private static ByteBudgetLruMap<String, Integer> cache(int maxBytes) {
        // Value itself is the byte size, for arithmetic that's easy to reason about in assertions.
        return new ByteBudgetLruMap<>(maxBytes, value -> value);
    }

    @Test
    public void staysUnderBudgetAsEntriesAreAdded() {
        ByteBudgetLruMap<String, Integer> c = cache(10);
        c.put("a", 4);
        c.put("b", 4);
        assertEquals(8, c.currentBytes());
        assertEquals(Integer.valueOf(4), c.get("a"));
        assertEquals(Integer.valueOf(4), c.get("b"));
    }

    @Test
    public void evictsOldestWhenBudgetExceeded() {
        ByteBudgetLruMap<String, Integer> c = cache(10);
        c.put("a", 4);
        c.put("b", 4);
        c.put("c", 4); // 12 > 10 — must evict "a" (least recently used)
        assertNull(c.get("a"));
        assertEquals(Integer.valueOf(4), c.get("b"));
        assertEquals(Integer.valueOf(4), c.get("c"));
        assertTrue(c.currentBytes() <= 10);
    }

    @Test
    public void accessOrderPromotesRecentlyReadEntry() {
        ByteBudgetLruMap<String, Integer> c = cache(10);
        c.put("a", 4);
        c.put("b", 4);
        c.get("a"); // touch "a" — "b" is now the least recently used
        c.put("c", 4); // must evict "b", not "a"
        assertEquals(Integer.valueOf(4), c.get("a"));
        assertNull(c.get("b"));
        assertEquals(Integer.valueOf(4), c.get("c"));
    }

    @Test
    public void replacingAKeySubtractsTheOldSizeFirst() {
        ByteBudgetLruMap<String, Integer> c = cache(10);
        c.put("a", 8);
        c.put("a", 2); // shrink in place — must not double-count the old value
        assertEquals(2, c.currentBytes());
    }

    @Test
    public void removeSubtractsSize() {
        ByteBudgetLruMap<String, Integer> c = cache(10);
        c.put("a", 6);
        c.remove("a");
        assertEquals(0, c.currentBytes());
        assertNull(c.get("a"));
    }

    @Test
    public void evictAllResetsByteCountAndEntries() {
        ByteBudgetLruMap<String, Integer> c = cache(100);
        c.put("a", 10);
        c.put("b", 10);
        c.evictAll();
        assertEquals(0, c.currentBytes());
        assertTrue(c.isEmpty());
        assertFalse(c.containsKey("a"));
    }

    @Test
    public void singleEntryLargerThanBudgetIsEvictedImmediately() {
        // Matches android.util.LruCache's documented behavior: a value whose size alone
        // exceeds the max is trimmed right back out rather than left over-budget.
        ByteBudgetLruMap<String, Integer> c = cache(10);
        c.put("huge", 50);
        assertNull(c.get("huge"));
        assertEquals(0, c.currentBytes());
    }
}
