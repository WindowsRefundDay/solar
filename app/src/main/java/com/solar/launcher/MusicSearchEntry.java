package com.solar.launcher;

import com.solar.launcher.deezer.DeezerResult;
import com.solar.launcher.soulseek.SoulseekClient;

import java.util.Collections;
import java.util.List;

/** One row in unified Get Music search results. */
public final class MusicSearchEntry {
    public enum Source { DEEZER, REACH }

    /** Flat track, Deezer artist/album container, or Reach folder container. */
    public enum RowKind { TRACK, DEEZER_ARTIST, DEEZER_ALBUM, REACH_FOLDER }

    public final Source source;
    public final RowKind kind;
    public final DeezerResult deezer;
    public final SoulseekClient.Result reach;
    /** Album, artist, or folder display name for container rows. */
    public final String containerLabel;
    /** Tracks inside a container row; empty for lazy-loaded album/artist rows. */
    public final List<MusicSearchEntry> children;
    /** Deezer artist or album id for lazy-loaded containers. */
    public final long deezerContainerId;
    /** Deezer album record type: album, single, ep. */
    public final String deezerRecordType;

    private MusicSearchEntry(Source source, RowKind kind, DeezerResult deezer,
            SoulseekClient.Result reach, String containerLabel, List<MusicSearchEntry> children,
            long deezerContainerId, String deezerRecordType) {
        this.source = source;
        this.kind = kind;
        this.deezer = deezer;
        this.reach = reach;
        this.containerLabel = containerLabel != null ? containerLabel : "";
        this.children = children != null ? children : Collections.<MusicSearchEntry>emptyList();
        this.deezerContainerId = deezerContainerId;
        this.deezerRecordType = deezerRecordType != null ? deezerRecordType : "";
    }

    public static MusicSearchEntry deezer(DeezerResult r) {
        return new MusicSearchEntry(Source.DEEZER, RowKind.TRACK, r, null, "", null, 0, "");
    }

    public static MusicSearchEntry reach(SoulseekClient.Result r) {
        return new MusicSearchEntry(Source.REACH, RowKind.TRACK, null, r, "", null, 0, "");
    }

    public static MusicSearchEntry deezerAlbum(String albumLabel, List<MusicSearchEntry> tracks) {
        return new MusicSearchEntry(Source.DEEZER, RowKind.DEEZER_ALBUM, null, null, albumLabel,
                tracks, 0, "album");
    }

    public static MusicSearchEntry deezerArtist(long artistId, String name) {
        return new MusicSearchEntry(Source.DEEZER, RowKind.DEEZER_ARTIST, null, null, name, null,
                artistId, "");
    }

    /** Album row under an artist; tracks load when opened if {@code tracks} is empty. */
    public static MusicSearchEntry deezerAlbumBrowse(long albumId, String label, String recordType,
            List<MusicSearchEntry> tracks) {
        return new MusicSearchEntry(Source.DEEZER, RowKind.DEEZER_ALBUM, null, null, label, tracks,
                albumId, recordType != null ? recordType : "album");
    }

    public static MusicSearchEntry reachFolder(String folderLabel, List<MusicSearchEntry> tracks) {
        return new MusicSearchEntry(Source.REACH, RowKind.REACH_FOLDER, null, null, folderLabel,
                tracks, 0, "");
    }

    public boolean isContainer() {
        return kind == RowKind.DEEZER_ARTIST || kind == RowKind.DEEZER_ALBUM
                || kind == RowKind.REACH_FOLDER;
    }

    public static MusicSearchEntry withChildren(MusicSearchEntry template,
            List<MusicSearchEntry> children) {
        if (template == null) return null;
        return new MusicSearchEntry(template.source, template.kind, template.deezer, template.reach,
                template.containerLabel, children, template.deezerContainerId, template.deezerRecordType);
    }

    public boolean needsLazyLoad() {
        return (kind == RowKind.DEEZER_ARTIST || kind == RowKind.DEEZER_ALBUM)
                && deezerContainerId > 0 && children.isEmpty();
    }

    public String dedupeKey() {
        if (kind == RowKind.DEEZER_ARTIST) {
            return "artist:" + deezerContainerId;
        }
        if (kind == RowKind.DEEZER_ALBUM || kind == RowKind.REACH_FOLDER) {
            if (deezerContainerId > 0) return "album:" + deezerContainerId;
            return containerLabel.toLowerCase();
        }
        if (source == Source.DEEZER && deezer != null) {
            return normalize(deezer.artist + " " + deezer.title);
        }
        if (source == Source.REACH && reach != null) {
            return normalize(reach.title());
        }
        return "";
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }
}
