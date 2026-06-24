package com.solar.launcher.soulseek;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Loads small country flag PNGs from assets/flags/{cc}.png (Nicotine+ derived). */
public final class SoulseekCountryFlags {
    private static final int CACHE_MAX = 64;
    private static final Map<String, Bitmap> CACHE = new LinkedHashMap<String, Bitmap>(CACHE_MAX, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > CACHE_MAX;
        }
    };
    private static final Map<String, Drawable> DRAWABLE_CACHE =
            new LinkedHashMap<String, Drawable>(CACHE_MAX, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Drawable> eldest) {
            return size() > CACHE_MAX;
        }
    };

    private SoulseekCountryFlags() {}

    public static String normalizeCode(String country) {
        if (country == null) return null;
        String c = country.trim().toUpperCase(Locale.US);
        if (c.length() != 2) return null;
        for (int i = 0; i < 2; i++) {
            char ch = c.charAt(i);
            if (ch < 'A' || ch > 'Z') return null;
        }
        return c;
    }

    public static Bitmap loadFlag(Context ctx, String countryCode) {
        String cc = normalizeCode(countryCode);
        if (cc == null || ctx == null) return null;
        String key = cc.toLowerCase(Locale.US);
        synchronized (CACHE) {
            Bitmap cached = CACHE.get(key);
            if (cached != null && !cached.isRecycled()) return cached;
        }
        try {
            InputStream in = ctx.getAssets().open("flags/" + key + ".png");
            Bitmap bmp = BitmapFactory.decodeStream(in);
            in.close();
            if (bmp != null) {
                synchronized (CACHE) {
                    CACHE.put(key, bmp);
                }
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    public static Drawable loadFlagDrawable(Context ctx, String countryCode) {
        String cc = normalizeCode(countryCode);
        if (cc == null || ctx == null) return null;
        String key = cc.toLowerCase(Locale.US);
        synchronized (DRAWABLE_CACHE) {
            Drawable cached = DRAWABLE_CACHE.get(key);
            if (cached != null) return cached;
        }
        Bitmap bmp = loadFlag(ctx, countryCode);
        if (bmp == null) return null;
        BitmapDrawable d = new BitmapDrawable(ctx.getResources(), bmp);
        synchronized (DRAWABLE_CACHE) {
            DRAWABLE_CACHE.put(key, d);
        }
        return d;
    }

    static void clearCacheForTest() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        synchronized (DRAWABLE_CACHE) {
            DRAWABLE_CACHE.clear();
        }
    }
}
