package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight scan-performance logger.
 *
 * <p>Writes JSON lines to {@code /storage/sdcard0/solar/scan-perf.log} and logcat tag
 * {@code SolarScanPerf} so the creator can measure library-scan timing on real Y1 hardware.
 * The file is append-only and rotated when it exceeds 256 KB.</p>
 */
public final class ScanPerfLog {

    private static final String TAG = "SolarScanPerf";
    private static File getLogFile() {
        return new File(com.solar.launcher.DeviceFeatures.getPrimaryStorageRoot(), "solar/scan-perf.log");
    }
    private static final long MAX_SIZE = 256 * 1024;

    private static final AtomicReference<LastScan> LAST = new AtomicReference<LastScan>();

    private ScanPerfLog() {}

    /** In-memory snapshot of the most recent scan. */
    public static final class LastScan {
        public final long timestamp;
        public final int trackCount;
        public final long totalMs;
        public final String phaseBreakdown;

        public LastScan(long timestamp, int trackCount, long totalMs, String phaseBreakdown) {
            this.timestamp = timestamp;
            this.trackCount = trackCount;
            this.totalMs = totalMs;
            this.phaseBreakdown = phaseBreakdown;
        }
    }

    /**
     * Record a scan timing event.
     *
     * @param tracks   number of tracks resolved
     * @param totalMs  total scan time in milliseconds
     * @param phases   JSON object with per-phase milliseconds
     */
    public static void record(int tracks, long totalMs, JSONObject phases) {
        long now = System.currentTimeMillis();
        LastScan snapshot = new LastScan(now, tracks, totalMs,
                phases != null ? phases.toString() : "{}");
        LAST.set(snapshot);

        try {
            JSONObject o = new JSONObject();
            o.put("timestamp", now);
            o.put("tracks", tracks);
            o.put("totalMs", totalMs);
            o.put("phases", phases != null ? phases : new JSONObject());

            String line = o.toString();
            Log.i(TAG, line);
            appendLine(line);
        } catch (Exception ignored) {
            // Never crash the scan path for perf logging.
        }
    }

    /** Snapshot of the most recent scan; null until a scan completes. */
    public static LastScan last() {
        return LAST.get();
    }

    /** Reset the in-memory snapshot (e.g. after a settings reset). */
    public static void clear() {
        LAST.set(null);
    }

    private static void appendLine(String line) {
        try {
            File f = getLogFile();
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (f.length() > MAX_SIZE) {
                File backup = new File(getLogFile().getAbsolutePath() + ".old");
                if (backup.exists()) backup.delete();
                f.renameTo(backup);
            }
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(f, true)));
            pw.println(line);
            pw.close();
        } catch (IOException e) {
            Log.w(TAG, "append failed: " + e.getMessage());
        }
    }

    /** Build a phase timing object for LibraryScanner. */
    public static JSONObject phases(long collectMs, long partitionMs, long tagReadMs,
            long persistMs, long mergeMs) {
        JSONObject p = new JSONObject();
        try {
            p.put("collectMs", collectMs);
            p.put("partitionMs", partitionMs);
            p.put("tagReadMs", tagReadMs);
            p.put("persistMs", persistMs);
            p.put("mergeMs", mergeMs);
        } catch (JSONException ignored) {}
        return p;
    }
}
