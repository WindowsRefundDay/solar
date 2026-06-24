package com.solar.launcher.soulseek;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Shared default Soulseek profile picture for all Reach users. */
public final class ReachProfilePicture {
    private static final String ASSET = "logo/soulseek_profile_image.jpg";
    private static volatile byte[] cached;

    private ReachProfilePicture() {}

    public static byte[] load(Context ctx) {
        if (cached != null) return cached;
        synchronized (ReachProfilePicture.class) {
            if (cached != null) return cached;
            if (ctx == null) return null;
            try {
                InputStream in = ctx.getAssets().open(ASSET);
                ByteArrayOutputStream bos = new ByteArrayOutputStream(8192);
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n > 0) bos.write(buf, 0, n);
                }
                in.close();
                cached = bos.toByteArray();
            } catch (Exception ignored) {
                cached = new byte[0];
            }
            return cached.length > 0 ? cached : null;
        }
    }
}
