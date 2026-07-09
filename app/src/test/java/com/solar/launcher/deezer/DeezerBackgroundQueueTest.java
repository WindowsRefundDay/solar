package com.solar.launcher.deezer;

import org.junit.Test;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DeezerBackgroundQueueTest {

    @Test
    public void testJobCreationAndProperties() {
        DeezerResult track = new DeezerResult(12345, "Title", "Artist", "Album", 6789, 210, "preview", "cover");
        File dest = new File("test.mp3");
        DeezerBackgroundQueue.Job job = new DeezerBackgroundQueue.Job(track, dest, "FLAC");

        if (job.track.id != 12345) throw new AssertionError("track ID mismatch");
        if (!"FLAC".equals(job.qualityOverride)) throw new AssertionError("quality mismatch");
        if (!"queued".equals(job.status)) throw new AssertionError("status mismatch");
        if (job.progress != 0) throw new AssertionError("progress mismatch");
    }

    @Test
    public void testEnqueueAndCancel() {
        DeezerBackgroundQueue queue = new DeezerBackgroundQueue(null, new DeezerBackgroundQueue.Listener() {
            @Override public void onTrackProgress(DeezerBackgroundQueue.Job job, int percent) {}
            @Override public void onTrackComplete(DeezerBackgroundQueue.Job job) {}
            @Override public void onTrackFailed(DeezerBackgroundQueue.Job job, String error) {}
            @Override public void onAlbumComplete() {}
        });

        DeezerResult track = new DeezerResult(12345, "Title", "Artist", "Album", 6789, 210, "preview", "cover");
        File dest = new File("test.mp3");
        List<DeezerBackgroundQueue.Job> jobs = new ArrayList<DeezerBackgroundQueue.Job>();
        jobs.add(new DeezerBackgroundQueue.Job(track, dest));

        queue.enqueue(jobs);
        if (queue.getJobs().size() != 1) {
            throw new AssertionError("job not enqueued");
        }

        queue.cancel();
        if (!queue.getJobs().isEmpty()) {
            throw new AssertionError("queue should be empty after cancel");
        }
    }
}
