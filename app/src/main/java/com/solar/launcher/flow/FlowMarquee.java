package com.solar.launcher.flow;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Horizontal marquee for Flow canvas text — ponytail: one-lane scroll when wider than clip.
 */
public final class FlowMarquee {

    private float offsetPx;
    private long lastMs;
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();

    public void reset() {
        offsetPx = 0f;
        lastMs = 0L;
    }

    /** Draw single-line text clipped to [x, x+maxW]; scrolls when text overflows. */
    public boolean draw(Canvas canvas, String text, float x, float y, float maxW, Paint paint) {
        if (text == null || text.isEmpty()) return false;
        float textW = paint.measureText(text);
        paint.getFontMetrics(fontMetrics);
        float top = y + fontMetrics.ascent;
        float bottom = y + fontMetrics.descent;
        canvas.save();
        canvas.clipRect(x, top, x + maxW, bottom);
        if (textW <= maxW) {
            canvas.drawText(text, x, y, paint);
            canvas.restore();
            return false;
        }
        long now = System.currentTimeMillis();
        if (lastMs > 0L) {
            float dt = (now - lastMs) / 1000f;
            offsetPx += 36f * dt;
        }
        lastMs = now;
        float gap = maxW * 0.35f;
        float loop = textW + gap;
        if (offsetPx > loop) offsetPx -= loop;
        canvas.drawText(text, x - offsetPx, y, paint);
        canvas.drawText(text, x - offsetPx + loop, y, paint);
        canvas.restore();
        return true;
    }
}
