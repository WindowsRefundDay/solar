package com.solar.launcher.deezer;

/** Public search result from api.deezer.com. */
public final class DeezerResult {
    public final long id;
    public final String title;
    public final String artist;
    public final String album;
    public final long albumId;
    public final int durationSec;
    public final String previewUrl;
    public final String coverUrl;

    public DeezerResult(long id, String title, String artist, String album, long albumId,
            int durationSec, String previewUrl, String coverUrl) {
        this.id = id;
        this.title = title != null ? title : "";
        this.artist = artist != null ? artist : "";
        this.album = album != null ? album : "";
        this.albumId = albumId;
        this.durationSec = durationSec;
        this.previewUrl = previewUrl != null ? previewUrl : "";
        this.coverUrl = coverUrl != null ? coverUrl : "";
    }

    public String displayTitle() {
        if (artist.isEmpty()) return title;
        return artist + " - " + title;
    }

    public String filenameBase() {
        String base = displayTitle();
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (base.isEmpty()) base = "track_" + id;
        return base;
    }
}
