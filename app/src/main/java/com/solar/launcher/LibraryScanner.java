package com.solar.launcher;

import android.content.SharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

/**
 * Parallel library scan worker.
 *
 * <p>Serial filesystem walk + concurrent ID3 extraction via {@link AudioTags}
 * + single-transaction batch SQLite upsert. This targets the actual scan bottleneck
 * ({@link android.media.MediaMetadataRetriever}) while keeping SQLite writes serialized.
 */
public final class LibraryScanner {

    /** Thread count for tag extraction — bounded to avoid thrashing low-end Y1 storage. */
    private static final int TAG_THREADS = Math.max(2, Math.min(4,
            Runtime.getRuntime().availableProcessors()));

    private LibraryScanner() {}

    /** Cancellation and progress hook for the scan worker. */
    public interface Callback {
        /** Return true to abort the scan as soon as possible. */
        boolean isCancelled();

        /** Called roughly every {@code progressInterval} tracks that have been resolved. */
        void onProgress(int resolvedCount);

        /** Number of resolved tracks between progress callbacks. */
        int progressInterval();
    }

    /** Result of a scan: resolved items plus per-phase timing. */
    public static final class ScanResult {
        public final List<MainActivity.SongItem> items;
        /** Files that needed a fresh ID3 read — 0 means the cache fully covered the walk. */
        public final int staleCount;
        public final long totalMs;
        public final long collectMs;
        public final long partitionMs;
        public final long tagReadMs;
        public final long persistMs;
        public final long mergeMs;

        ScanResult(List<MainActivity.SongItem> items, int staleCount, long totalMs, long collectMs,
                long partitionMs, long tagReadMs, long persistMs, long mergeMs) {
            this.items = items;
            this.staleCount = staleCount;
            this.totalMs = totalMs;
            this.collectMs = collectMs;
            this.partitionMs = partitionMs;
            this.tagReadMs = tagReadMs;
            this.persistMs = persistMs;
            this.mergeMs = mergeMs;
        }

        public JSONObject phases() {
            return ScanPerfLog.phases(collectMs, partitionMs, tagReadMs, persistMs, mergeMs);
        }
    }

    /**
     * Scan {@code root} for audio files and return a deduplicated list of library items.
     *
     * @param root         music root directory
     * @param store        music metadata cache (batch upsert only)
     * @param cachedTracks preloaded store rows keyed by absolute path — freshness checks run
     *                     against this map so the walk issues zero per-file SQLite queries
     * @param blacklist    paths to skip
     * @param prefs        shared prefs for tag overlay
     * @param seenPaths    populated with every audio file path found on disk (for stale-row purge)
     * @param cb           cancellation/progress callback
     * @return resolved song items, never null
     */
    public static ScanResult scan(File root, MusicLibraryStore store,
            java.util.Map<String, MusicLibraryStore.Track> cachedTracks,
            Set<String> blacklist, SharedPreferences prefs, Set<String> seenPaths, Callback cb) {
        if (root == null || !root.isDirectory() || cb == null || seenPaths == null) {
            return emptyResult();
        }
        if (cachedTracks == null) cachedTracks = Collections.emptyMap();
        long t0 = System.currentTimeMillis();
        if (cb.isCancelled()) return emptyResult();

        long t1 = System.currentTimeMillis();
        List<File> audioFiles = collectAudioFiles(root, blacklist, seenPaths, cb);
        long collectMs = System.currentTimeMillis() - t1;
        if (audioFiles.isEmpty() || cb.isCancelled()) {
            return emptyResult();
        }

        t1 = System.currentTimeMillis();
        List<MainActivity.SongItem> freshItems = new ArrayList<MainActivity.SongItem>();
        List<File> staleFiles = new ArrayList<File>();
        partitionByFreshness(audioFiles, cachedTracks, freshItems, staleFiles);
        long partitionMs = System.currentTimeMillis() - t1;

        if (cb.isCancelled()) return emptyResult();

        t1 = System.currentTimeMillis();
        List<TagResult> staleResults = readTagsParallel(staleFiles, prefs, cb);
        long tagReadMs = System.currentTimeMillis() - t1;

        t1 = System.currentTimeMillis();
        if (!staleResults.isEmpty()) {
            persistBatch(staleResults, store);
        }
        long persistMs = System.currentTimeMillis() - t1;

        t1 = System.currentTimeMillis();
        List<MainActivity.SongItem> items = mergeAndDedup(freshItems, staleResults, cb);
        long mergeMs = System.currentTimeMillis() - t1;

        long totalMs = System.currentTimeMillis() - t0;
        return new ScanResult(items, staleFiles.size(), totalMs, collectMs, partitionMs,
                tagReadMs, persistMs, mergeMs);
    }

    private static ScanResult emptyResult() {
        return new ScanResult(Collections.<MainActivity.SongItem>emptyList(), 0, 0, 0, 0, 0, 0, 0);
    }

    private static List<File> collectAudioFiles(File root, Set<String> blacklist,
            Set<String> seenPaths, Callback cb) {
        List<File> out = new ArrayList<File>();
        collectRecursive(root, blacklist, seenPaths, out, cb);
        return out;
    }

    private static void collectRecursive(File folder, Set<String> blacklist,
            Set<String> seenPaths, List<File> out, Callback cb) {
        if (cb.isCancelled()) return;
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (cb.isCancelled()) return;
            if (f.isDirectory()) {
                collectRecursive(f, blacklist, seenPaths, out, cb);
            } else if (MainActivity.isAudioFile(f) && !blacklist.contains(f.getAbsolutePath())) {
                seenPaths.add(f.getAbsolutePath());
                out.add(f);
            }
        }
    }

    private static void partitionByFreshness(List<File> files,
            java.util.Map<String, MusicLibraryStore.Track> cachedTracks,
            List<MainActivity.SongItem> freshOut, List<File> staleOut) {
        for (File f : files) {
            MusicLibraryStore.Track cached = cachedTracks.get(f.getAbsolutePath());
            // lastModified()==0 means the file vanished mid-walk — treat as stale.
            long mtime = cached != null ? f.lastModified() : 0;
            if (cached != null && mtime != 0
                    && cached.mtime == mtime && cached.size == f.length()) {
                freshOut.add(songItemFromTrack(cached, f));
            } else {
                staleOut.add(f);
            }
        }
    }

    private static List<TagResult> readTagsParallel(List<File> files, SharedPreferences prefs,
            Callback cb) {
        if (files.isEmpty()) return Collections.emptyList();

        ExecutorService executor = Executors.newFixedThreadPool(TAG_THREADS,
                new java.util.concurrent.ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "LibraryTagReader");
                        t.setPriority(Thread.MIN_PRIORITY);
                        return t;
                    }
                });

        List<Future<TagResult>> futures = new ArrayList<Future<TagResult>>(files.size());
        for (File f : files) {
            if (cb.isCancelled()) break;
            futures.add(executor.submit(new TagReader(f, prefs)));
        }
        executor.shutdown();

        List<TagResult> out = new ArrayList<TagResult>(futures.size());
        for (Future<TagResult> future : futures) {
            if (cb.isCancelled()) break;
            try {
                TagResult r = future.get();
                if (r != null) out.add(r);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                // Ignore individual tag-read failures; original scan did the same.
            }
        }

        // Don't let orphan readers extend worker lifetime.
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }

        return out;
    }

    private static void persistBatch(List<TagResult> results, MusicLibraryStore store) {
        List<MusicLibraryStore.Upsert> upserts = new ArrayList<MusicLibraryStore.Upsert>(results.size());
        for (TagResult r : results) {
            upserts.add(new MusicLibraryStore.Upsert(r.file, r.title, r.artist, r.album,
                    r.genre, r.albumArtist, r.durationMs, r.trackNumber));
        }
        store.upsertBatch(upserts);
    }

    private static List<MainActivity.SongItem> mergeAndDedup(List<MainActivity.SongItem> fresh,
            List<TagResult> stale, Callback cb) {
        int interval = Math.max(1, cb.progressInterval());
        Set<String> metaKeys = new HashSet<String>();
        List<MainActivity.SongItem> out = new ArrayList<MainActivity.SongItem>(
                fresh.size() + stale.size());

        int resolved = 0;
        for (MainActivity.SongItem item : fresh) {
            if (cb.isCancelled()) break;
            String key = metaKey(item.title, item.artist, "");
            if (!metaKeys.add(key)) continue;
            out.add(item);
            if (++resolved % interval == 0) cb.onProgress(resolved);
        }

        for (TagResult r : stale) {
            if (cb.isCancelled()) break;
            String key = metaKey(r.title, r.artist, r.durationMs);
            if (!metaKeys.add(key)) continue;
            out.add(new MainActivity.SongItem(r.file, r.title, r.artist, r.album,
                    r.genre, r.albumArtist, r.trackNumber));
            if (++resolved % interval == 0) cb.onProgress(resolved);
        }

        if (resolved % interval != 0) cb.onProgress(resolved);
        return out;
    }

    private static String metaKey(String title, String artist, String durationMs) {
        return (title + "\0" + artist + "\0" + durationMs).toLowerCase(Locale.US);
    }

    private static MainActivity.SongItem songItemFromTrack(MusicLibraryStore.Track t, File f) {
        String genre = t.genre != null && t.genre.trim().length() > 0
                ? t.genre.trim() : "Unknown Genre";
        return new MainActivity.SongItem(f, t.title, t.artist, t.album, genre,
                t.albumArtist, t.trackNumber);
    }

    private static final class TagReader implements Callable<TagResult> {
        private final File file;
        private final SharedPreferences prefs;

        TagReader(File file, SharedPreferences prefs) {
            this.file = file;
            this.prefs = prefs;
        }

        @Override
        public TagResult call() {
            try {
                AudioTags.Info tags = AudioTags.read(file, prefs, AudioTags.READ_SKIP_EMBEDDED_ART);
                String artist = tags.artist;
                String album = tags.album;
                if (artist.isEmpty()) artist = "Unknown Artist";
                if (album.isEmpty()) album = "Unknown Album";
                return new TagResult(file, tags.title, artist, album, tags.genre,
                        tags.albumArtist, tags.durationMs, tags.trackNumber);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static final class TagResult {
        final File file;
        final String title;
        final String artist;
        final String album;
        final String genre;
        final String albumArtist;
        final String durationMs;
        final int trackNumber;

        TagResult(File file, String title, String artist, String album, String genre,
                String albumArtist, String durationMs, int trackNumber) {
            this.file = file;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.genre = genre;
            this.albumArtist = albumArtist;
            this.durationMs = durationMs;
            this.trackNumber = trackNumber;
        }
    }
}
