package com.solar.launcher;

import com.solar.launcher.deezer.DeezerCache;
import com.solar.launcher.soulseek.ReachCache;

import java.io.File;

/** Shared helpers for stream vs library paths in the unified play queue. */
public final class StreamQueueHelper {
    private StreamQueueHelper() {}

    public static boolean isStreamTempFile(File appCacheRoot, File f) {
        if (f == null || appCacheRoot == null) return false;
        return ReachCache.isTempFile(appCacheRoot, f) || DeezerCache.isTempFile(appCacheRoot, f);
    }

    public static boolean isLibraryMusicFile(File musicRoot, File appCacheRoot, File f) {
        if (f == null || !f.isFile() || musicRoot == null) return false;
        if (isStreamTempFile(appCacheRoot, f)) return false;
        String root = musicRoot.getAbsolutePath();
        String path = f.getAbsolutePath();
        return path.startsWith(root + File.separator);
    }
}
