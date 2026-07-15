package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LibraryScannerConcurrencyTest {

    @Test
    public void y1UsesOneTagReaderToProtectPlaybackAndUi() {
        if (BuildConfig.Y1_ONLY) {
            assertEquals(1, LibraryScanner.tagThreadCount());
        }
    }
}
