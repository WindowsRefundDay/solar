package com.solar.launcher.flow;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlowViewReflectionTest {

    @Test
    public void reflectionsOnUnlessDebugPref() {
        assertFalse(FlowView.shouldSkipCoverReflection(false));
        assertTrue(FlowView.shouldSkipCoverReflection(true));
    }

    @Test
    public void sideCoverReflectionsOnByDefault() {
        assertFalse(FlowView.shouldSkipCoverReflection(false, false));
    }

    @Test
    public void reflectionAlphaIsUniformLowOpacity() {
        assertEquals(0.18f, FlowView.reflectionAlphaForSlot(1f, true), 0.001f);
        assertEquals(0.18f, FlowView.reflectionAlphaForSlot(1f, false), 0.001f);
        assertEquals(0.09f, FlowView.reflectionAlphaForSlot(0.5f, false), 0.001f);
        assertEquals(0.045f, FlowView.reflectionAlphaForDraw(0.25f, 1f, false), 0.001f);
        assertEquals(0.18f, FlowView.reflectionAlphaForDraw(1f, 1f, true), 0.001f);
    }

    @Test
    public void scrollingDoesNotSuppressReflections() {
        assertFalse(FlowView.shouldSkipCoverReflection(false, false, true, true));
        assertFalse(FlowView.shouldSkipCoverReflection(false, false, true, false));
    }

    @Test
    public void handoffDoesNotSuppressReflections() {
        assertFalse(FlowView.shouldSkipCoverReflection(false, true, false, true));
        assertFalse(FlowView.shouldSkipCoverReflection(false, true, false, false));
        assertFalse(FlowView.shouldSkipCoverReflection(false, false, false, true, 0.5f));
    }

    @Test
    public void noReflectionsOverridesAllCovers() {
        assertTrue(FlowView.shouldSkipCoverReflection(true, false, false, true));
        assertTrue(FlowView.shouldSkipCoverReflection(true, false, true, false));
    }
}
