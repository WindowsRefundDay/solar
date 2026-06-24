package com.solar.launcher;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.solar.launcher.theme.ThemeManager;

/** Compact capped text panel with optional auto vertical marquee for long content. */
public final class VerticalTextMarqueeHelper {
    private static final int TICK_MS = 50;
    private static final int PAUSE_TICKS = 40;
    private static final int TAG_MARQUEE = 0x70cc0001;
    public static final int TAG_SCROLL = 0x70cc0002;
    public static final int TAG_BODY = 0x70cc0003;

    private static Handler marqueeHandler;

    private static Handler marqueeHandler() {
        if (marqueeHandler == null) {
            marqueeHandler = new Handler(Looper.getMainLooper());
        }
        return marqueeHandler;
    }

    private VerticalTextMarqueeHelper() {}

    public static int measureTextHeight(Activity activity, CharSequence text, float textPx, int widthPx) {
        if (activity == null || widthPx <= 0) return 0;
        TextView measure = new TextView(activity);
        measure.setTypeface(ThemeManager.getCustomFont());
        measure.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
        measure.setText(text != null ? text : "");
        int specW = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY);
        int specH = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        measure.measure(specW, specH);
        return measure.getMeasuredHeight();
    }

    /** Max height ~3 menu lines (matches context-menu detail cap). */
    public static int defaultMaxHeightPx(Activity activity) {
        float menuTextPx = activity.getResources().getDimension(R.dimen.y1_menu_text_size);
        float linePx = menuTextPx * 0.85f;
        float density = activity.getResources().getDisplayMetrics().density;
        return (int) (linePx * 1.18f * 3 + 14 * density);
    }

    public static float defaultLineTextPx(Activity activity) {
        return activity.getResources().getDimension(R.dimen.y1_menu_text_size) * 0.85f;
    }

    /** Shrink-to-fit capped row height (testable without Activity). */
    public static int computeCappedRowHeight(int contentH, int pad, int maxHeightPx, int minH) {
        return Math.min(maxHeightPx, Math.max(minH, contentH + pad));
    }

    public static int computePanelHeight(Activity activity, CharSequence text, int maxHeightPx, int innerWidthPx) {
        float linePx = defaultLineTextPx(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (8 * density);
        int minH = (int) (linePx * 1.18f + pad);
        int contentH = measureTextHeight(activity, text, linePx, innerWidthPx);
        return computeCappedRowHeight(contentH, pad, maxHeightPx, minH);
    }

    public static FrameLayout createCappedPanel(Activity activity, CharSequence text, int maxHeightPx) {
        float density = activity.getResources().getDisplayMetrics().density;
        int textPad = (int) activity.getResources().getDimension(R.dimen.y1_menu_text_pad_left);
        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int innerW = Math.max(1, screenW - textPad * 2 - (int) (8 * density));
        int rowH = computePanelHeight(activity, text, maxHeightPx, innerW);

        FrameLayout row = new FrameLayout(activity);
        ScrollView scroll = new ScrollView(activity);
        scroll.setTag(TAG_SCROLL);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        FrameLayout.LayoutParams scrollLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, rowH);
        scrollLp.leftMargin = textPad;
        scrollLp.rightMargin = textPad;
        scrollLp.topMargin = (int) (4 * density);
        scrollLp.bottomMargin = (int) (4 * density);
        row.addView(scroll, scrollLp);

        TextView body = new TextView(activity);
        body.setTag(TAG_BODY);
        bindBodyText(activity, body, text, innerW);
        scroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        row.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, rowH));

        maybeStartVerticalMarquee(scroll, body, text, maxHeightPx, innerW, activity);
        return row;
    }

    public static void updateCappedPanel(Activity activity, FrameLayout row, CharSequence text,
            int maxHeightPx, int panelWidthPx) {
        if (activity == null || row == null) return;
        ScrollView scroll = row.findViewWithTag(TAG_SCROLL);
        TextView body = row.findViewWithTag(TAG_BODY);
        if (scroll == null || body == null) return;
        stop(scroll);

        float density = activity.getResources().getDisplayMetrics().density;
        int textPad = (int) activity.getResources().getDimension(R.dimen.y1_menu_text_pad_left);
        int innerW = panelWidthPx > 0
                ? Math.max(1, panelWidthPx - textPad * 2 - (int) (8 * density))
                : Math.max(1, activity.getResources().getDisplayMetrics().widthPixels
                        - textPad * 2 - (int) (8 * density));
        int rowH = computePanelHeight(activity, text, maxHeightPx, innerW);

        bindBodyText(activity, body, text, innerW);
        FrameLayout.LayoutParams scrollLp = (FrameLayout.LayoutParams) scroll.getLayoutParams();
        scrollLp.height = rowH;
        scroll.setLayoutParams(scrollLp);
        ViewGroup.LayoutParams rowLp = row.getLayoutParams();
        if (rowLp != null) {
            rowLp.height = rowH;
            row.setLayoutParams(rowLp);
        }
        maybeStartVerticalMarquee(scroll, body, text, maxHeightPx, innerW, activity);
    }

    private static void bindBodyText(Activity activity, TextView body, CharSequence text, int innerW) {
        float linePx = defaultLineTextPx(activity);
        body.setTypeface(ThemeManager.getCustomFont());
        body.setTextSize(TypedValue.COMPLEX_UNIT_PX, linePx);
        body.setGravity(Gravity.START | Gravity.TOP);
        body.setText(text != null ? text : "");
        ThemeManager.applyThemedTextStyle(body, ThemeManager.getTextColorPrimary());
        applyHorizontalMarqueeIfNeeded(body, text, linePx, innerW);
    }

    private static void applyHorizontalMarqueeIfNeeded(TextView body, CharSequence text,
            float linePx, int innerW) {
        if (body == null || text == null) return;
        String s = text.toString();
        if (s.contains("\n")) {
            body.setSingleLine(false);
            body.setEllipsize(null);
            body.setHorizontallyScrolling(false);
            body.setSelected(false);
            return;
        }
        body.setSingleLine(true);
        body.setHorizontallyScrolling(true);
        float textW = body.getPaint().measureText(s);
        if (textW > innerW) {
            body.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            body.setMarqueeRepeatLimit(-1);
            body.setSelected(true);
        } else {
            body.setEllipsize(TextUtils.TruncateAt.END);
            body.setSelected(false);
        }
    }

    private static void maybeStartVerticalMarquee(ScrollView scroll, TextView body, CharSequence text,
            int maxHeightPx, int innerW, Activity activity) {
        if (text == null || text.toString().contains("\n")) {
            float linePx = defaultLineTextPx(activity);
            int contentH = measureTextHeight(activity, text, linePx, innerW);
            float density = activity.getResources().getDisplayMetrics().density;
            int pad = (int) (8 * density);
            if (contentH + pad > maxHeightPx) {
                scroll.post(new Runnable() {
                    @Override
                    public void run() {
                        start(scroll, body);
                    }
                });
            }
        } else {
            float linePx = defaultLineTextPx(activity);
            int contentH = measureTextHeight(activity, text, linePx, innerW);
            float density = activity.getResources().getDisplayMetrics().density;
            int pad = (int) (8 * density);
            if (contentH + pad > maxHeightPx) {
                scroll.post(new Runnable() {
                    @Override
                    public void run() {
                        start(scroll, body);
                    }
                });
            }
        }
    }

    public static void start(final ScrollView scroll, final TextView body) {
        stop(scroll);
        if (scroll == null || body == null) return;
        scroll.post(new Runnable() {
            @Override
            public void run() {
                final int contentH = body.getHeight();
                final int viewH = scroll.getHeight();
                if (contentH <= viewH + 4) return;
                Runnable tick = new Runnable() {
                    private int pauseTicks;

                    @Override
                    public void run() {
                        if (scroll.getTag(TAG_MARQUEE) != this) return;
                        int maxScroll = Math.max(0, contentH - viewH);
                        int y = scroll.getScrollY();
                        if (pauseTicks > 0) {
                            pauseTicks--;
                        } else if (y >= maxScroll) {
                            scroll.scrollTo(0, 0);
                            pauseTicks = PAUSE_TICKS;
                        } else {
                            scroll.scrollBy(0, 1);
                        }
                        marqueeHandler().postDelayed(this, TICK_MS);
                    }
                };
                scroll.setTag(TAG_MARQUEE, tick);
                marqueeHandler().postDelayed(tick, 1500);
            }
        });
    }

    public static void stop(ScrollView scroll) {
        if (scroll == null) return;
        Object tag = scroll.getTag(TAG_MARQUEE);
        if (tag instanceof Runnable) {
            marqueeHandler().removeCallbacks((Runnable) tag);
            scroll.setTag(TAG_MARQUEE, null);
        }
        scroll.scrollTo(0, 0);
    }

    public static void stopPanel(FrameLayout row) {
        if (row == null) return;
        ScrollView scroll = row.findViewWithTag(TAG_SCROLL);
        stop(scroll);
    }

    public static void resetPanelScroll(FrameLayout panel) {
        if (panel == null) return;
        ScrollView scroll = panel.findViewWithTag(TAG_SCROLL);
        if (scroll != null) scroll.scrollTo(0, 0);
    }

    public static boolean isPanelScrolledToTop(FrameLayout panel) {
        ScrollView scroll = panel != null ? panel.findViewWithTag(TAG_SCROLL) : null;
        return scroll == null || scroll.getScrollY() <= 0;
    }

    public static boolean isPanelScrolledToBottom(FrameLayout panel) {
        ScrollView scroll = panel != null ? panel.findViewWithTag(TAG_SCROLL) : null;
        if (scroll == null) return true;
        View child = scroll.getChildCount() > 0 ? scroll.getChildAt(0) : null;
        if (child == null) return true;
        int max = Math.max(0, child.getHeight() - scroll.getHeight());
        return scroll.getScrollY() >= max - 2;
    }

    public static boolean scrollPanelByStep(Activity activity, FrameLayout panel, int direction) {
        if (panel == null || direction == 0) return false;
        ScrollView scroll = panel.findViewWithTag(TAG_SCROLL);
        if (scroll == null) return false;
        stop(scroll);
        int step = (int) (defaultLineTextPx(activity) * 0.9f);
        if (step < 8) step = 8;
        int y = scroll.getScrollY() + (direction > 0 ? step : -step);
        View child = scroll.getChildCount() > 0 ? scroll.getChildAt(0) : null;
        int max = child != null ? Math.max(0, child.getHeight() - scroll.getHeight()) : 0;
        if (y < 0) y = 0;
        if (y > max) y = max;
        scroll.scrollTo(0, y);
        return true;
    }

}
