package com.solar.launcher.soulseek;

import org.junit.Before;
import org.junit.Test;

public class SoulseekCountryFlagsTest {

    @Before
    public void setUp() {
        SoulseekCountryFlags.clearCacheForTest();
    }

    @Test
    public void normalizeCode_acceptsValidIso() {
        if (!"US".equals(SoulseekCountryFlags.normalizeCode(" us "))) {
            throw new AssertionError("normalize");
        }
        if (SoulseekCountryFlags.normalizeCode("USA") != null) {
            throw new AssertionError("too long");
        }
        if (SoulseekCountryFlags.normalizeCode("1A") != null) {
            throw new AssertionError("invalid chars");
        }
    }

    @Test
    public void loadFlag_missingInputsReturnNull() {
        if (SoulseekCountryFlags.loadFlag(null, "US") != null) {
            throw new AssertionError("null ctx");
        }
        if (SoulseekCountryFlags.loadFlag(null, null) != null) {
            throw new AssertionError("null code");
        }
        if (SoulseekCountryFlags.loadFlagDrawable(null, "US") != null) {
            throw new AssertionError("null drawable");
        }
    }
}
