package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QueueMoveWindowTest {

    @Test
    public void ribbonAboveIndex_edgesAndInterior() {
        assertEquals(-1, QueueMoveWindow.ribbonAboveIndex(0));
        assertEquals(0, QueueMoveWindow.ribbonAboveIndex(1));
        assertEquals(4, QueueMoveWindow.ribbonAboveIndex(5));
    }

    @Test
    public void ribbonBelowIndex_edgesAndInterior() {
        assertEquals(-1, QueueMoveWindow.ribbonBelowIndex(0, 1));
        assertEquals(1, QueueMoveWindow.ribbonBelowIndex(0, 3));
        assertEquals(-1, QueueMoveWindow.ribbonBelowIndex(9, 10));
        assertEquals(6, QueueMoveWindow.ribbonBelowIndex(5, 10));
    }

    @Test
    public void nextMoveIndex_oneStepIncludingNowPlaying() {
        assertEquals(1, QueueMoveWindow.nextMoveIndex(0, 1, 3));
        assertEquals(3, QueueMoveWindow.nextMoveIndex(2, 1, 6));
        assertEquals(1, QueueMoveWindow.nextMoveIndex(2, -1, 6));
        assertEquals(2, QueueMoveWindow.nextMoveIndex(2, 0, 6));
        assertEquals(2, QueueMoveWindow.nextMoveIndex(2, 1, 3));
    }

    @Test
    public void canMoveTo_requiresDifferentIndices() {
        assertTrue(QueueMoveWindow.canMoveTo(0, 1));
        assertTrue(QueueMoveWindow.canMoveTo(2, 5));
        assertFalse(QueueMoveWindow.canMoveTo(2, 2));
    }

    @Test
    public void ribbonEnterTranslationSlots_twoTrack() {
        assertEquals(-1, QueueMoveWindow.ribbonEnterTranslationSlots(0, 2));
        assertEquals(0, QueueMoveWindow.ribbonEnterTranslationSlots(1, 2));
    }

    @Test
    public void ribbonEnterTranslationSlots_longQueue() {
        assertEquals(-1, QueueMoveWindow.ribbonEnterTranslationSlots(0, 5));
        assertEquals(1, QueueMoveWindow.ribbonEnterTranslationSlots(4, 5));
        assertEquals(0, QueueMoveWindow.ribbonEnterTranslationSlots(2, 5));
    }
}
