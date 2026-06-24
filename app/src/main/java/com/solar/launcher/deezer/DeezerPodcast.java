package com.solar.launcher.deezer;

import com.solar.launcher.podcast.OpenRssClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Deezer podcast catalog via public API. */
public final class DeezerPodcast {
    public static final String FEED_PREFIX = "deezer://podcast/";

    private DeezerPodcast() {}

    public static boolean isDeezerFeed(String feedUrl) {
        return feedUrl != null && feedUrl.startsWith(FEED_PREFIX);
    }

    public static long podcastIdFromFeed(String feedUrl) {
        if (!isDeezerFeed(feedUrl)) return 0;
        try {
            return Long.parseLong(feedUrl.substring(FEED_PREFIX.length()));
        } catch (Exception e) {
            return 0;
        }
    }

    public static OpenRssClient.Podcast toPodcast(DeezerSearch.DeezerPodcastShow show) {
        if (show == null) return null;
        return new OpenRssClient.Podcast(
                show.title,
                "Deezer",
                FEED_PREFIX + show.id,
                show.pictureUrl);
    }

    public static List<OpenRssClient.Episode> fetchEpisodes(DeezerClient client, long podcastId)
            throws IOException {
        List<OpenRssClient.Episode> out = new ArrayList<OpenRssClient.Episode>();
        try {
            byte[] body = client.getPublic("https://api.deezer.com/podcast/" + podcastId);
            JSONObject root = new JSONObject(new String(body, "UTF-8"));
            JSONArray eps = root.optJSONArray("episodes");
            if (eps == null) {
                JSONObject data = root.optJSONObject("episodes");
                if (data != null) eps = data.optJSONArray("data");
            }
            if (eps == null) return out;
            for (int i = 0; i < eps.length(); i++) {
                JSONObject ep = eps.optJSONObject(i);
                if (ep == null) continue;
                long id = ep.optLong("id", 0);
                String title = ep.optString("title", "");
                String pub = ep.optString("release_date", "");
                int dur = ep.optInt("duration", 0);
                String audio = id > 0 ? (DeezerPodcast.FEED_PREFIX + "episode/" + id) : "";
                out.add(new OpenRssClient.Episode(title, audio, pub, dur));
            }
        } catch (Exception e) {
            throw new IOException(e.getMessage() != null ? e.getMessage() : "Episode fetch failed");
        }
        return out;
    }

    private static java.nio.charset.Charset utf8() {
        try {
            return java.nio.charset.Charset.forName("UTF-8");
        } catch (Exception e) {
            return java.nio.charset.Charset.defaultCharset();
        }
    }
}
