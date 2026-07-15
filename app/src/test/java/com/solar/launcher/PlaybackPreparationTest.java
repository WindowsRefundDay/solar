package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackPreparationTest {

    @Test
    public void onlyCurrentGenerationAndTrackMayCommit() {
        assertTrue(MainActivity.playbackPreparationMatches(
                7L, 7L, "/music/current.mp3", "/music/current.mp3", true));
        assertFalse(MainActivity.playbackPreparationMatches(
                6L, 7L, "/music/current.mp3", "/music/current.mp3", true));
        assertFalse(MainActivity.playbackPreparationMatches(
                7L, 7L, "/music/old.mp3", "/music/current.mp3", true));
        assertFalse(MainActivity.playbackPreparationMatches(
                7L, 7L, "/music/current.mp3", "/music/current.mp3", false));
    }

    @Test
    public void missingPathsNeverMatch() {
        assertFalse(MainActivity.playbackPreparationMatches(1L, 1L, null, null, true));
        assertFalse(MainActivity.playbackPreparationMatches(
                1L, 1L, "/music/current.mp3", null, true));
    }

    @Test
    public void preparationExecutorIsSingleWorkerWithOneLatestQueuedResult() {
        java.util.concurrent.ThreadPoolExecutor executor =
                MainActivity.createMusicPreparationExecutor();
        try {
            assertEquals(1, executor.getCorePoolSize());
            assertEquals(1, executor.getMaximumPoolSize());
            assertEquals(1, executor.getQueue().remainingCapacity());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void cachedCoverReaderAcceptsSmallFilesAndReadsEveryByte() throws Exception {
        java.io.File file = java.io.File.createTempFile("solar-cover", ".jpg");
        byte[] expected = new byte[1024];
        for (int i = 0; i < expected.length; i++) expected[i] = (byte) (i & 0xff);
        java.io.FileOutputStream out = new java.io.FileOutputStream(file);
        try {
            out.write(expected);
        } finally {
            out.close();
        }
        try {
            assertArrayEquals(expected, MainActivity.readCoverJpegBytes(file));
        } finally {
            file.delete();
        }
    }
}
