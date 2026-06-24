package com.solar.launcher;

import android.os.Handler;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ScrollView;

/** Animated scroll-to-focus for single-axis hardware lists. */
public final class FocusScrollHelper {
    private static final int LIST_IDLE_FOCUS_DELAY_MS = 80;

    private FocusScrollHelper() {}

    public static void smoothScrollListToPosition(final ListView list, final int position) {
        if (list == null || position < 0) return;
        list.smoothScrollToPosition(position);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            private boolean scrolling;

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    if (scrolling) {
                        scrolling = false;
                        list.setOnScrollListener(null);
                        focusListPosition(list, position);
                    }
                } else {
                    scrolling = true;
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                    int totalItemCount) {}
        });
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                focusListPosition(list, position);
            }
        }, 220);
    }

    public static void focusListPosition(ListView list, int position) {
        if (list == null || position < 0) return;
        list.setSelection(position);
        View child = list.getChildAt(position - list.getFirstVisiblePosition());
        if (child != null) {
            child.requestFocus();
        } else {
            list.post(new Runnable() {
                @Override
                public void run() {
                    View c = list.getChildAt(position - list.getFirstVisiblePosition());
                    if (c != null) c.requestFocus();
                }
            });
        }
    }

    public static void scrollToChildBottom(ScrollView scroll, View child) {
        if (scroll == null || child == null) return;
        scroll.post(new Runnable() {
            @Override
            public void run() {
                int y = child.getBottom() - scroll.getHeight();
                if (y < 0) y = 0;
                scroll.smoothScrollTo(0, y);
            }
        });
    }
}
