package com.solar.launcher.deezer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DeezerDecryptTest {
    @Test
    public void calcBfKey_knownSongId() {
        String key = DeezerDecrypt.calcBfKey("3135556");
        assertEquals(16, key.length());
        // Stable across runs for same input
        String key2 = DeezerDecrypt.calcBfKey("3135556");
        assertEquals(key, key2);
    }

    @Test
    public void calcBfKey_empty() {
        assertEquals(16, DeezerDecrypt.calcBfKey("").length());
    }
}
