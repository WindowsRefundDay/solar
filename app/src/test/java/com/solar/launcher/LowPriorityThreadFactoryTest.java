package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LowPriorityThreadFactoryTest {

    @Test
    public void createsNamedLowPriorityWorkers() {
        LowPriorityThreadFactory factory = new LowPriorityThreadFactory("LibraryMaintenance");

        Thread first = factory.newThread(new Runnable() {
            @Override public void run() {}
        });
        Thread second = factory.newThread(new Runnable() {
            @Override public void run() {}
        });

        assertEquals("LibraryMaintenance-1", first.getName());
        assertEquals("LibraryMaintenance-2", second.getName());
        assertEquals(Thread.MIN_PRIORITY, first.getPriority());
        assertFalse(first.isDaemon());
    }
}
