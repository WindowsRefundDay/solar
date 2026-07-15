package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InactivityShutdownPolicyTest {
    @Test public void disabledNeverShutsDown() {
        assertFalse(InactivityShutdownPolicy.shouldShutdown(0, false, false, false, 1000, 0));
    }

    @Test public void activePlaybackInhibitsShutdown() {
        assertFalse(InactivityShutdownPolicy.shouldShutdown(1, true, false, false, 60000, 0));
    }

    @Test public void usbPowerInhibitsShutdown() {
        assertFalse(InactivityShutdownPolicy.shouldShutdown(1, false, true, false, 60000, 0));
    }

    @Test public void usbStorageScreenInhibitsShutdown() {
        assertFalse(InactivityShutdownPolicy.shouldShutdown(1, false, false, true, 60000, 0));
    }

    @Test public void thresholdUsesMonotonicElapsedTime() {
        assertFalse(InactivityShutdownPolicy.shouldShutdown(1, false, false, false, 59999, 0));
        assertTrue(InactivityShutdownPolicy.shouldShutdown(1, false, false, false, 60000, 0));
    }

    @Test public void negativeElapsedTimeNeverShutsDown() {
        assertFalse(InactivityShutdownPolicy.shouldShutdown(1, false, false, false, 1, 2));
    }
}
