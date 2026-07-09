package com.solar.launcher.deezer;

import android.content.SharedPreferences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Parallel background Deezer downloads. */
public final class DeezerBackgroundQueue {
    public static final class Job {
        public final DeezerResult track;
        public final File dest;
        public final String qualityOverride;
        public volatile int progress = 0;
        public volatile String status = "queued"; // "queued", "downloading", "complete", "failed"
        public volatile String error = null;

        public Job(DeezerResult track, File dest) {
            this(track, dest, null);
        }

        public Job(DeezerResult track, File dest, String qualityOverride) {
            this.track = track;
            this.dest = dest;
            this.qualityOverride = qualityOverride;
        }
    }

    public interface Listener {
        void onTrackProgress(Job job, int percent);
        void onTrackComplete(Job job);
        void onTrackFailed(Job job, String error);
        void onAlbumComplete();
    }

    private final SharedPreferences prefs;
    private final Listener listener;
    private final List<Job> jobs = new ArrayList<Job>();
    private ExecutorService executor;

    public DeezerBackgroundQueue(SharedPreferences prefs, Listener listener) {
        this.prefs = prefs;
        this.listener = listener;
    }

    private synchronized void ensureExecutor() {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newFixedThreadPool(2); // ponytail: 2 concurrent downloads is optimal for Y1 CPU/RAM limits
        }
    }

    public void start(List<Job> queueJobs) {
        enqueue(queueJobs);
    }

    public synchronized List<Job> getJobs() {
        return new ArrayList<Job>(jobs);
    }

    /** Append jobs and start execution in parallel. */
    public synchronized void enqueue(List<Job> queueJobs) {
        if (queueJobs == null || queueJobs.isEmpty()) return;
        ensureExecutor();
        jobs.addAll(queueJobs);
        for (final Job job : queueJobs) {
            job.status = "queued";
            executor.submit(new Runnable() {
                @Override public void run() {
                    processJob(job);
                }
            });
        }
    }

    public synchronized void cancel() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        for (Job job : jobs) {
            if ("queued".equals(job.status) || "downloading".equals(job.status)) {
                job.status = "failed";
                job.error = "Cancelled";
            }
        }
        jobs.clear();
    }

    private void processJob(final Job job) {
        synchronized (this) {
            if (executor == null || executor.isShutdown()) return;
            job.status = "downloading";
        }

        String ext = "mp3";
        if (job.qualityOverride != null) {
            ext = "flac".equalsIgnoreCase(job.qualityOverride) || "FLAC".equals(job.qualityOverride) ? "flac" : "mp3";
        } else {
            try {
                DeezerClient probe = new DeezerClient(prefs);
                if (!probe.isSessionValid()) probe.initSession();
                ext = probe.fileExtension();
            } catch (Exception ignored) {}
        }

        DeezerDownloadRunner.Progress progress = new DeezerDownloadRunner.Progress() {
            @Override public void onProgress(int percent, long done, long total) {
                int pct = percent;
                if (pct < 0 && total > 0) pct = (int) (done * 100 / total);
                if (pct < 0 && done > 0) pct = 1;
                pct = Math.min(99, Math.max(0, pct));
                job.progress = pct;
                if (listener != null) {
                    listener.onTrackProgress(job, pct);
                }
            }
        };

        String err = DeezerDownloadRunner.downloadWithFallback(
                prefs, job.track, job.dest, ext, job.qualityOverride, progress);

        synchronized (this) {
            if (err != null) {
                job.status = "failed";
                job.error = err;
                if (listener != null) {
                    listener.onTrackFailed(job, err);
                }
            } else {
                job.status = "complete";
                job.progress = 100;
                if (listener != null) {
                    listener.onTrackComplete(job);
                }
            }
            limitFinishedJobs();

            // Fire onAlbumComplete if all queued/downloading jobs are done
            boolean anyActive = false;
            for (Job j : jobs) {
                if ("queued".equals(j.status) || "downloading".equals(j.status)) {
                    anyActive = true;
                    break;
                }
            }
            if (!anyActive && listener != null) {
                listener.onAlbumComplete();
            }
        }
    }

    private synchronized void limitFinishedJobs() {
        int finishedCount = 0;
        for (Job j : jobs) {
            if ("complete".equals(j.status) || "failed".equals(j.status)) {
                finishedCount++;
            }
        }
        if (finishedCount > 50) {
            int toRemove = finishedCount - 50;
            for (int i = 0; i < jobs.size() && toRemove > 0; i++) {
                Job j = jobs.get(i);
                if ("complete".equals(j.status) || "failed".equals(j.status)) {
                    jobs.remove(i);
                    i--;
                    toRemove--;
                }
            }
        }
    }

    public static File reservePlaceholder(File cacheDir, DeezerResult result, String ext)
            throws IOException {
        if (cacheDir != null && !cacheDir.isDirectory()) cacheDir.mkdirs();
        String safe = result.filenameBase() + "." + ext;
        File dest = new File(cacheDir, safe);
        int n = 1;
        while (dest.exists()) {
            dest = new File(cacheDir, result.filenameBase() + " (" + n + ")." + ext);
            n++;
        }
        if (!dest.createNewFile()) throw new IOException("mkdir placeholder");
        return dest;
    }
}
