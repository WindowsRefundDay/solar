package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UsbRecoveryStartGateTest {
    @Test public void firstStartBeforeSixtySecondsIsAllowed() {
        UsbRecoveryStartGate gate = new UsbRecoveryStartGate(60000L);
        assertEquals(0, gate.tryAcquire(5000L));
    }

    @Test public void failedStartCanRetryImmediately() {
        UsbRecoveryStartGate gate = new UsbRecoveryStartGate(60000L);
        int token = gate.tryAcquire(5000L);
        gate.complete(token, false, 5001L);
        assertEquals(0, gate.tryAcquire(5002L));
    }

    @Test public void successfulStartIsThrottled() {
        UsbRecoveryStartGate gate = new UsbRecoveryStartGate(60000L);
        int token = gate.tryAcquire(5000L);
        gate.complete(token, true, 5001L);
        assertEquals(-1, gate.tryAcquire(65000L));
        assertEquals(0, gate.tryAcquire(65001L));
    }

    @Test public void successfulStartAtElapsedZeroIsStillThrottled() {
        UsbRecoveryStartGate gate = new UsbRecoveryStartGate(60000L);
        int token = gate.tryAcquire(0L);
        gate.complete(token, true, 0L);
        assertEquals(-1, gate.tryAcquire(1L));
    }

    @Test public void disconnectResetInvalidatesOldStartAndAllowsReconnect() {
        UsbRecoveryStartGate gate = new UsbRecoveryStartGate(60000L);
        int staleToken = gate.tryAcquire(5000L);
        assertTrue(gate.isCurrent(staleToken));
        gate.reset();
        assertFalse(gate.isCurrent(staleToken));
        gate.complete(staleToken, true, 6000L);
        assertEquals(1, gate.tryAcquire(6001L));
    }
}
