package com.solar.launcher;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.solar.launcher.deezer.DeezerCache;
import com.solar.launcher.deezer.DeezerDownloader;
import com.solar.launcher.podcast.OpenRssClient;
import com.solar.launcher.soulseek.ReachCache;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Device smoke: unified queue mixing and stream-to-library promotion paths. */
@RunWith(AndroidJUnit4.class)
public class PlayQueueMixingDeviceTest {

    @Test
    public void mixedQueue_musicReachDeezerPodcast() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File cache = ctx.getCacheDir();
        File reach = new File(ReachCache.dir(cache), "mix_q.part");
        reach.getParentFile().mkdirs();
        reach.createNewFile();
        File deezer = new File(DeezerCache.dir(cache), "mix_q.part");
        deezer.getParentFile().mkdirs();
        deezer.createNewFile();

        PlaybackCoordinator pc = new PlaybackCoordinator();
        List<File> pl = new ArrayList<File>();
        pl.add(new File("/Music/a.mp3"));
        pc.activateMusic(pl, 0, false);
        pc.queueReachAfterCurrent(reach, "Reach track");
        pc.queueDeezerAfterCurrent(deezer, "Deezer track", 99L);
        OpenRssClient.Episode ep = new OpenRssClient.Episode("ep", "http://example.com/a.mp3", "");
        pc.queuePodcastAfterCurrent(ep, "Podcast", false);
        if (pc.unifiedQueue().size() != 4) throw new AssertionError("mixed queue size");
        reach.delete();
        deezer.delete();
    }

    @Test
    public void streamQueueHelper_usesAppCacheRoots() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File cache = ctx.getCacheDir();
        File reachPartial = new File(ReachCache.dir(cache), "mix_test.part");
        reachPartial.getParentFile().mkdirs();
        if (!reachPartial.createNewFile() && !reachPartial.isFile()) {
            throw new AssertionError("reach partial");
        }
        File dzPartial = new File(DeezerCache.dir(cache), "mix_test.part");
        dzPartial.getParentFile().mkdirs();
        if (!dzPartial.createNewFile() && !dzPartial.isFile()) {
            throw new AssertionError("deezer partial");
        }
        if (!StreamQueueHelper.isStreamTempFile(cache, reachPartial)) throw new AssertionError("reach temp");
        if (!StreamQueueHelper.isStreamTempFile(cache, dzPartial)) throw new AssertionError("deezer temp");
        reachPartial.delete();
        dzPartial.delete();
    }

    @Test
    public void deezerPartialThreshold_tenPercent() {
        if (DeezerDownloader.EARLY_PLAY_PERCENT != 10) {
            throw new AssertionError("expected 10% early play");
        }
        if (!DeezerDownloader.shouldFirePartialReady(500_000, 5_000_000, false)) {
            throw new AssertionError("10% threshold");
        }
    }
}
