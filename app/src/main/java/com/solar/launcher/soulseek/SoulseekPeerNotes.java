package com.solar.launcher.soulseek;

import android.content.Context;

import com.solar.launcher.soulseek.store.ReachDatabase;
import com.solar.launcher.soulseek.store.ReachDbExecutor;

import java.util.Locale;

/** Local per-peer notes (SoulseekQT / nicotine+ style). */
public final class SoulseekPeerNotes {
    private SoulseekPeerNotes() {}

    public static String getNoteSync(Context ctx, String username) {
        if (ctx == null || username == null || username.trim().isEmpty()) return "";
        return ReachDatabase.getInstance(ctx).getPeerNoteSync(username.trim());
    }

    public static void setNote(final Context ctx, final String username, final String note) {
        if (ctx == null || username == null || username.trim().isEmpty()) return;
        final String peer = username.trim();
        final String text = note != null ? note.trim() : "";
        ReachDbExecutor.run(new Runnable() {
            @Override
            public void run() {
                if (text.isEmpty()) {
                    ReachDatabase.getInstance(ctx).clearPeerNoteSync(peer);
                } else {
                    ReachDatabase.getInstance(ctx).setPeerNoteSync(peer, text);
                }
            }
        });
    }

    public static void clearNote(Context ctx, String username) {
        setNote(ctx, username, "");
    }

    public static String normalizeUsername(String username) {
        if (username == null) return "";
        return username.trim().toLowerCase(Locale.US);
    }
}
