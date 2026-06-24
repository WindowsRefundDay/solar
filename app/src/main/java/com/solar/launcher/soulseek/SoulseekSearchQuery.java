package com.solar.launcher.soulseek;

import java.util.Locale;
import java.util.regex.Pattern;

/** Nicotine+-style search term cleanup before sending to the Soulseek server. */
public final class SoulseekSearchQuery {
    private static final Pattern STRIP_PUNCT = Pattern.compile("[\\[\\](){}:;]");

    private SoulseekSearchQuery() {}

    /** Returns a trimmed query safe to transmit on MSG_FILE_SEARCH / MSG_USER_SEARCH. */
    public static String sanitize(String query) {
        if (query == null) return "";
        String q = query.trim();
        if (q.isEmpty()) return q;
        q = STRIP_PUNCT.matcher(q).replaceAll(" ");
        q = q.replace('\u2018', '\'').replace('\u2019', '\'');
        q = q.replace('\u201c', '"').replace('\u201d', '"');
        StringBuilder sb = new StringBuilder(q.length());
        boolean prevSpace = false;
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (Character.isISOControl(c)) continue;
            if (c == ' ' || c == '\t') {
                if (!prevSpace) {
                    sb.append(' ');
                    prevSpace = true;
                }
            } else {
                sb.append(c);
                prevSpace = false;
            }
        }
        return sb.toString().trim();
    }
}
