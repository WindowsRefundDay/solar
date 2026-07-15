package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UsbPowerPolicyTest {
    @Test public void normalUsbRestoresMtpAndAdb() {
        assertEquals("mtp,adb", UmsEnabler.usbConfigFor(false));
    }

    @Test public void massStorageKeepsAdbConfigured() {
        assertEquals("mass_storage,adb", UmsEnabler.usbConfigFor(true));
    }

    @Test public void recoveryOnlyRunsForDeclinedHostWithoutMassStorage() {
        assertTrue(UsbRecoveryAgent.shouldRun(true, true, false));
        assertFalse(UsbRecoveryAgent.shouldRun(false, true, false));
        assertFalse(UsbRecoveryAgent.shouldRun(true, false, false));
        assertFalse(UsbRecoveryAgent.shouldRun(true, true, true));
    }
}
