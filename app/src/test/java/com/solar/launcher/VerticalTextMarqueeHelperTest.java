package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VerticalTextMarqueeHelperTest {

    @Test
    public void computeCappedRowHeight_shortTextUsesContent() {
        int h = VerticalTextMarqueeHelper.computeCappedRowHeight(40, 8, 120, 30);
        assertEquals(48, h);
    }

    @Test
    public void computeCappedRowHeight_longTextUsesCap() {
        int h = VerticalTextMarqueeHelper.computeCappedRowHeight(200, 8, 120, 50);
        assertEquals(120, h);
    }

    @Test
    public void computeCappedRowHeight_respectsMinimum() {
        int h = VerticalTextMarqueeHelper.computeCappedRowHeight(10, 4, 120, 50);
        assertEquals(50, h);
    }
}
