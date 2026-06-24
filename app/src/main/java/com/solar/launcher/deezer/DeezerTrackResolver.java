package com.solar.launcher.deezer;

import org.json.JSONObject;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scrape TRACK_TOKEN from deezer.com track pages. */
public final class DeezerTrackResolver {
    private static final Pattern DATA_JSON = Pattern.compile("(\\{\"DATA\":.*)");

    private final DeezerClient client;

    public DeezerTrackResolver(DeezerClient client) {
        this.client = client;
    }

    public DeezerTrackData resolveTrack(long trackId) throws IOException {
        return resolvePage("track", trackId);
    }

    public DeezerTrackData resolveEpisode(long episodeId) throws IOException {
        return resolvePage("episode", episodeId);
    }

    private DeezerTrackData resolvePage(String type, long id) throws IOException {
        String url = "https://www.deezer.com/us/" + type + "/" + id;
        String html = client.getAuthenticatedText(url);
        if (html.contains("MD5_ORIGIN") == false && !html.contains("TRACK_TOKEN")) {
            throw new IOException("Not logged in — update ARL cookie");
        }
        Matcher m = DATA_JSON.matcher(html);
        while (m.find()) {
            try {
                String jsonStr = m.group(1);
                int end = findJsonEnd(jsonStr);
                if (end > 0) jsonStr = jsonStr.substring(0, end);
                JSONObject root = new JSONObject(jsonStr);
                JSONObject data = root.optJSONObject("DATA");
                if (data == null) continue;
                String dataType = data.optString("__TYPE__", "");
                if ("song".equals(dataType) || "episode".equals(dataType)) {
                    return DeezerTrackData.fromSongJson(data);
                }
            } catch (Exception ignored) {}
        }
        throw new IOException("Could not parse track data");
    }

    private static int findJsonEnd(String s) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i + 1;
            }
        }
        return s.length();
    }
}
