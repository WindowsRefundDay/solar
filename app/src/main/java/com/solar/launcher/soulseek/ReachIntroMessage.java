package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;

/** Auto-intro PM/room message visible only to non-Reach Soulseek clients. */
public final class ReachIntroMessage {
    public static final String MARKER = "\u0001REACH_INTRO\u0001";

    private ReachIntroMessage() {}

    public static String build(Context ctx, String username, String modelLabel) {
        if (ctx == null || username == null || username.isEmpty()) return "";
        String body = ctx.getString(
                com.solar.launcher.R.string.reach_intro_message, username, modelLabel);
        return MARKER + body;
    }

    public static boolean isIntro(String text) {
        return text != null && text.contains(MARKER);
    }

    public static String strip(String text) {
        if (text == null) return "";
        String t = text;
        while (t.contains(MARKER)) {
            int idx = t.indexOf(MARKER);
            int end = t.indexOf('\n', idx);
            if (end < 0) {
                t = t.substring(0, idx).trim();
            } else {
                t = (t.substring(0, idx) + t.substring(end + 1)).trim();
            }
        }
        return t.trim();
    }

    public static String stripFromQuote(String quote) {
        return strip(quote != null ? quote : "");
    }

    public static boolean shouldPersistForReachClient(String text, String sender, String selfUsername,
            SharedPreferences prefs) {
        if (!isIntro(text)) return true;
        return !shouldHideIncoming(text, sender, selfUsername, prefs);
    }

    public static boolean shouldHideIncoming(String text, String sender, String selfUsername,
            SharedPreferences prefs) {
        if (!isIntro(text)) return false;
        if (sender != null && selfUsername != null
                && sender.equalsIgnoreCase(selfUsername)) {
            return true;
        }
        return sender != null && ReachPeerCapabilities.isReach(prefs, sender);
    }

    public static boolean shouldHideOutgoing(String text) {
        return isIntro(text);
    }
}
