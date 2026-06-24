package com.solar.launcher.soulseek.store;

import android.os.Handler;
import android.os.HandlerThread;

/** Single background thread for Reach SQLite writes and heavy reads. */
public final class ReachDbExecutor {
    private static HandlerThread thread;
    private static Handler handler;

    private ReachDbExecutor() {}

    public static synchronized Handler getHandler() {
        if (thread == null) {
            thread = new HandlerThread("ReachDb");
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        return handler;
    }

    public static void run(Runnable r) {
        getHandler().post(r);
    }

    /** Runs work on the DB thread and blocks until complete (not for UI thread). */
    public static void runSync(Runnable r) {
        if (Thread.currentThread().getName().equals("ReachDb")) {
            r.run();
            return;
        }
        final Object lock = new Object();
        final boolean[] done = new boolean[] { false };
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                try {
                    r.run();
                } finally {
                    synchronized (lock) {
                        done[0] = true;
                        lock.notifyAll();
                    }
                }
            }
        });
        synchronized (lock) {
            while (!done[0]) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
