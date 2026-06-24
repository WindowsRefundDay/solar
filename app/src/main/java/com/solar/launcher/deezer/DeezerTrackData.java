package com.solar.launcher.deezer;

import org.json.JSONObject;

/** Scraped track metadata from deezer.com page (authenticated). */
public final class DeezerTrackData {
    public final long sngId;
    public final String trackToken;
    public final String title;
    public final String artist;
    public final String album;
    public final String albumPicture;
    public final String trackNumber;
    public final String diskNumber;
    public final long albumId;
    public DeezerTrackData fallback;

    public DeezerTrackData(long sngId, String trackToken, String title, String artist,
            String album, String albumPicture, String trackNumber, String diskNumber,
            long albumId) {
        this.sngId = sngId;
        this.trackToken = trackToken != null ? trackToken : "";
        this.title = title != null ? title : "";
        this.artist = artist != null ? artist : "";
        this.album = album != null ? album : "";
        this.albumPicture = albumPicture != null ? albumPicture : "";
        this.trackNumber = trackNumber != null ? trackNumber : "";
        this.diskNumber = diskNumber != null ? diskNumber : "";
        this.albumId = albumId;
    }

    public static DeezerTrackData fromSongJson(JSONObject data) {
        if (data == null) return null;
        long sngId = data.optLong("SNG_ID", 0);
        String token = data.optString("TRACK_TOKEN", "");
        String title = data.optString("SNG_TITLE", "");
        String artist = data.optString("ART_NAME", "");
        String album = data.optString("ALB_TITLE", "");
        String pic = data.optString("ALB_PICTURE", "");
        String trackNum = data.optString("TRACK_NUMBER", "");
        String diskNum = data.optString("DISK_NUMBER", "");
        long albId = data.optLong("ALB_ID", 0);
        DeezerTrackData t = new DeezerTrackData(sngId, token, title, artist, album, pic,
                trackNum, diskNum, albId);
        if (data.has("FALLBACK") && !data.isNull("FALLBACK")) {
            t.fallback = fromSongJson(data.optJSONObject("FALLBACK"));
        }
        return t;
    }

    public String displayTitle() {
        if (artist.isEmpty()) return title;
        return artist + " - " + title;
    }
}
