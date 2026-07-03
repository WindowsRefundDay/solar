package com.solar.launcher;

import org.junit.Test;

public class DeviceFeaturesTest {

    @Test
    public void mt6582MapsToY2() {
        String family = DeviceFeatures.detectFamilyForTest("MT6582", "mt6582", 17, "Y1");
        if (!"y2".equals(family)) throw new AssertionError("expected y2 got " + family);
    }

    @Test
    public void mt6572MapsToY1() {
        String family = DeviceFeatures.detectFamilyForTest("MT6572", "mt6572", 19, "Y2");
        if (!"y1".equals(family)) throw new AssertionError("expected y1 got " + family);
    }

    @Test
    public void sdkFallbackY2() {
        String family = DeviceFeatures.detectFamilyForTest("", "", 19, "");
        if (!"y2".equals(family)) throw new AssertionError("expected y2 got " + family);
    }

    @Test
    public void modelFallbackY1() {
        String family = DeviceFeatures.detectFamilyForTest("", "", 17, "Innioasis Y1");
        if (!"y1".equals(family)) throw new AssertionError("expected y1 got " + family);
    }

    @Test
    public void y1HasRootAccess() {
        DeviceFeatures.setCachedFamilyForTest("y1");
        if (!DeviceFeatures.hasRootAccess()) throw new AssertionError("expected Y1 to have root access");
        DeviceFeatures.resetCacheForTest();
    }

    @Test
    public void y2DoesNotHaveRootAccess() {
        DeviceFeatures.setCachedFamilyForTest("y2");
        if (DeviceFeatures.hasRootAccess()) throw new AssertionError("expected Y2 to not have root access");
        DeviceFeatures.resetCacheForTest();
    }
}
