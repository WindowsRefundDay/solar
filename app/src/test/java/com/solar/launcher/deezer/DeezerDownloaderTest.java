package com.solar.launcher.deezer;

import org.junit.Test;

public class DeezerDownloaderTest {

    @Test
    public void shouldFirePartialReady_atTenPercent() {
        if (!DeezerDownloader.shouldFirePartialReady(1_000_000, 10_000_000, false)) {
            throw new AssertionError("10% should fire");
        }
        if (DeezerDownloader.shouldFirePartialReady(900_000, 10_000_000, false)) {
            throw new AssertionError("9% should not fire");
        }
    }

    @Test
    public void shouldFirePartialReady_unknownTotalUsesMinBytes() {
        if (!DeezerDownloader.shouldFirePartialReady(128 * 1024, 0, false)) {
            throw new AssertionError("128KB min should fire");
        }
        if (DeezerDownloader.shouldFirePartialReady(127 * 1024, 0, false)) {
            throw new AssertionError("below min should not fire");
        }
    }

    @Test
    public void shouldFirePartialReady_onlyOnce() {
        if (DeezerDownloader.shouldFirePartialReady(5_000_000, 10_000_000, true)) {
            throw new AssertionError("already fired");
        }
    }
}
