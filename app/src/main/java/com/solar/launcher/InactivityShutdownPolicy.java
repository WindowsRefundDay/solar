package com.solar.launcher;

/** Pure decision helper for the Y1 inactivity power-off policy. */
final class InactivityShutdownPolicy {
    private InactivityShutdownPolicy() {}

    static boolean shouldShutdown(int minutes, boolean playing, boolean usbPowered,
            boolean usbStorageScreen, long nowElapsedMs, long lastInteractionElapsedMs) {
        if (minutes <= 0 || playing || usbPowered || usbStorageScreen) return false;
        long idleMs = nowElapsedMs - lastInteractionElapsedMs;
        return idleMs >= 0L && idleMs >= InactivityShutdownConfig.shutdownDelayMs(minutes);
    }
}
