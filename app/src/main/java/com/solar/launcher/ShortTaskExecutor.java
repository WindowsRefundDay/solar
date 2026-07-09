package com.solar.launcher;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Shared bounded pool for short one-shot I/O jobs — not for long-lived socket/reader loops. */
public final class ShortTaskExecutor {
    public static final ExecutorService POOL = Executors.newFixedThreadPool(2);

    private ShortTaskExecutor() {}
}
