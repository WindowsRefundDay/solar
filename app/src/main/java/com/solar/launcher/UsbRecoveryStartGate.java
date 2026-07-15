package com.solar.launcher;

/** Thread-safe start throttle with explicit USB-session reset semantics. */
final class UsbRecoveryStartGate {
    private final long minIntervalMs;
    private long lastSuccessfulStartMs;
    private boolean hasSuccessfulStart;
    private boolean startInFlight;
    private int generation;

    UsbRecoveryStartGate(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

    synchronized int tryAcquire(long nowElapsedMs) {
        if (startInFlight) return -1;
        if (hasSuccessfulStart
                && nowElapsedMs - lastSuccessfulStartMs < minIntervalMs) return -1;
        startInFlight = true;
        return generation;
    }

    synchronized boolean isCurrent(int token) {
        return startInFlight && token == generation;
    }

    synchronized void complete(int token, boolean success, long nowElapsedMs) {
        if (token != generation) return;
        if (success) {
            lastSuccessfulStartMs = nowElapsedMs;
            hasSuccessfulStart = true;
        }
        startInFlight = false;
    }

    synchronized void reset() {
        generation++;
        startInFlight = false;
        lastSuccessfulStartMs = 0L;
        hasSuccessfulStart = false;
    }
}
