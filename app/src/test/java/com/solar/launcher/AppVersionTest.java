package com.solar.launcher;

import org.junit.Test;

public class AppVersionTest {
    @Test
    public void isNightlyName() {
        if (!AppVersion.isNightlyName("nightly-31")) throw new AssertionError("legacy nightly");
        if (!AppVersion.isNightlyName("nightly-20240622-1530")) throw new AssertionError("timestamp nightly");
        if (AppVersion.isNightlyName("0.2.1")) throw new AssertionError("stable");
        if (AppVersion.isNightlyName("v0.2.1")) throw new AssertionError("prefixed stable");
    }
}
