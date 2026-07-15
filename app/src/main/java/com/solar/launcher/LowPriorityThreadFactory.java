package com.solar.launcher;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Creates named low-priority workers for maintenance work that must yield to playback and UI. */
public final class LowPriorityThreadFactory implements ThreadFactory {
    private final String namePrefix;
    private final AtomicInteger sequence = new AtomicInteger();

    public LowPriorityThreadFactory(String namePrefix) {
        if (namePrefix == null || namePrefix.trim().isEmpty()) {
            throw new IllegalArgumentException("namePrefix");
        }
        this.namePrefix = namePrefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable,
                namePrefix + "-" + sequence.incrementAndGet());
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    }
}
