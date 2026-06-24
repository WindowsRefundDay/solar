package com.solar.launcher;

import com.solar.launcher.deezer.DeezerResult;
import com.solar.launcher.deezer.DeezerSearch;
import com.solar.launcher.soulseek.SoulseekClient;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class GetMusicSearchTest {

    @Test
    public void organize_includesArtistsAndMixedContainers() {
        List<DeezerSearch.DeezerArtist> artists = new ArrayList<DeezerSearch.DeezerArtist>();
        artists.add(new DeezerSearch.DeezerArtist(1L, "Oasis", ""));

        List<MusicSearchEntry> flat = new ArrayList<MusicSearchEntry>();
        flat.add(MusicSearchEntry.deezer(new DeezerResult(10, "Wonderwall", "Oasis",
                "(What's the Story) Morning Glory?", 100, 258, "", "")));
        flat.add(MusicSearchEntry.deezer(new DeezerResult(11, "Don't Look Back in Anger", "Oasis",
                "(What's the Story) Morning Glory?", 100, 282, "", "")));
        flat.add(MusicSearchEntry.reach(new SoulseekClient.Result("peer", "Oasis\\Wonderwall.mp3",
                5000000, 258, 320, true, true, 100, 0)));

        List<MusicSearchEntry> out = GetMusicSearch.organizeWithContainers(artists, flat);
        if (out.isEmpty()) throw new AssertionError("empty");
        if (out.get(0).kind != MusicSearchEntry.RowKind.DEEZER_ARTIST) {
            throw new AssertionError("artist first");
        }
        boolean hasAlbum = false;
        boolean hasReach = false;
        for (MusicSearchEntry e : out) {
            if (e.kind == MusicSearchEntry.RowKind.DEEZER_ALBUM) hasAlbum = true;
            if (e.source == MusicSearchEntry.Source.REACH) hasReach = true;
        }
        if (!hasAlbum) throw new AssertionError("album container");
        if (!hasReach) throw new AssertionError("reach folder or track");
    }

    @Test
    public void formatDeezerReleaseLabel_types() {
        if (!GetMusicSearch.formatDeezerReleaseLabel("single", "Live Forever").startsWith("Single")) {
            throw new AssertionError("single");
        }
        if (!GetMusicSearch.formatDeezerReleaseLabel("ep", "Some EP").startsWith("EP")) {
            throw new AssertionError("ep");
        }
    }
}
