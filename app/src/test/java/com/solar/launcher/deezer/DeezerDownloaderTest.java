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

    @Test
    public void shouldNotifyProgress_throttlesFastBlocksBeforeUi() {
        if (DeezerDownloader.shouldNotifyProgress(64 * 1024, 1024 * 1024,
                0, 0, 249, 0)) {
            throw new AssertionError("must not notify faster than the time interval");
        }
        if (!DeezerDownloader.shouldNotifyProgress(64 * 1024, 1024 * 1024,
                0, 0, 250, 0)) {
            throw new AssertionError("meaningful progress after the interval should notify");
        }
    }

    @Test
    public void shouldNotifyProgress_unknownLengthUsesBytesAndTime() {
        if (DeezerDownloader.shouldNotifyProgress(32 * 1024, 0,
                0, -1, 1000, 0)) {
            throw new AssertionError("small unknown-length chunks should be coalesced");
        }
        if (!DeezerDownloader.shouldNotifyProgress(64 * 1024, 0,
                0, -1, 1000, 0)) {
            throw new AssertionError("unknown-length progress should still be delivered");
        }
    }

    @Test
    public void shouldNotifyProgress_alwaysDeliversKnownLengthCompletion() {
        if (!DeezerDownloader.shouldNotifyProgress(1000, 1000,
                0, 0, 1, 0)) {
            throw new AssertionError("completion must not wait for throttling");
        }
    }
}
