package com.solar.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.StatFs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Software fallback for WAV encodings API 17 StageFright rejects (24-bit, 32-bit int and
 * IEEE-float PCM). Transcodes to a cached 16-bit PCM WAV on one background thread and hands
 * MediaPlayer the growing file once a head start is buffered, so playback itself stays on the
 * native path — equalizer, visualizer, AVRCP and scrubbing keep working unchanged. The header
 * is written with final sizes up front, so duration and seek targets are correct immediately;
 * seeks are capped to converted bytes via {@link #bufferedSeekLimitMs()} while a job runs.
 *
 * CPU/battery: conversion is a single I/O-bound pass at THREAD_PRIORITY_BACKGROUND with two
 * fixed buffers (no allocation inside the loop), typically done within seconds; after that the
 * device plays a plain 16-bit WAV exactly as cheaply as a native one. Cached results are keyed
 * on path+size+mtime, so replays and prev/next cost nothing.
 */
final class WavFallback {

    interface Listener {
        /** Main thread. playable is safe to hand to MediaPlayer (may still be growing). */
        void onReady(File playable);

        /** Main thread. Caller should fall through to the native MediaPlayer attempt. */
        void onFailed();
    }

    private static final int MAX_OUT_SAMPLE_RATE = 48000;
    private static final int HEAD_START_SECONDS = 2;
    private static final int MIN_HEAD_START_BYTES = 128 * 1024;
    private static final long FREE_SPACE_MARGIN_BYTES = 16L * 1024 * 1024;
    /** Completed transcodes kept besides the one being written — covers prev/next replays. */
    private static final int CACHE_KEEP_COMPLETED = 1;
    private static final int IN_BUFFER_TARGET_BYTES = 128 * 1024;
    private static final int WAV_HEADER_BYTES = 44;

    private final File cacheDir;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile Job activeJob;

    WavFallback(Context context) {
        File base = context.getExternalCacheDir();
        if (base == null) base = context.getCacheDir();
        cacheDir = new File(base, "wav16");
    }

    /** Cheap: name check, then a header-only probe (a few small reads). */
    static boolean needsFallback(File f) {
        if (f == null || !f.getName().toLowerCase(java.util.Locale.US).endsWith(".wav")) return false;
        WavFormat fmt = WavFormat.probe(f);
        return fmt != null && fmt.needsTranscode();
    }

    void prepare(File src, Listener listener) {
        cancel();
        Job job = new Job(src, listener);
        activeJob = job;
        Thread t = new Thread(job, "wav16-transcode");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    void cancel() {
        Job job = activeJob;
        activeJob = null;
        if (job != null) job.cancelled = true;
    }

    /**
     * Furthest ms the player may seek to while the current transcode is still writing,
     * or -1 when no incomplete transcode is active (no cap).
     */
    int bufferedSeekLimitMs() {
        Job job = activeJob;
        if (job == null || job.complete || !job.readyPosted) return -1;
        long bytesPerSec = job.outByteRate;
        if (bytesPerSec <= 0) return -1;
        return (int) Math.min(Integer.MAX_VALUE, job.outBytesWritten * 1000L / bytesPerSec);
    }

    private final class Job implements Runnable {
        final File src;
        final Listener listener;
        volatile boolean cancelled;
        volatile boolean complete;
        volatile boolean readyPosted;
        volatile long outBytesWritten;
        volatile int outByteRate;

        Job(File src, Listener listener) {
            this.src = src;
            this.listener = listener;
        }

        @Override
        public void run() {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            } catch (Exception ignored) {}
            try {
                transcode();
            } catch (Exception e) {
                postFailedOnce();
            }
        }

        private void transcode() throws IOException {
            WavFormat fmt = WavFormat.probe(src);
            if (fmt == null || !fmt.needsTranscode()) {
                postFailedOnce();
                return;
            }

            int decim = 1;
            while (fmt.sampleRate / decim > MAX_OUT_SAMPLE_RATE) decim *= 2;
            final int outChannels = Math.min(fmt.channels, 2);
            final int outRate = fmt.sampleRate / decim;
            final long outFrames = fmt.frameCount() / decim;
            final long outDataLen = outFrames * outChannels * 2;
            if (outFrames <= 0 || outDataLen > 0xFFFFFFF0L) {
                postFailedOnce();
                return;
            }
            outByteRate = outRate * outChannels * 2;

            if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) {
                postFailedOnce();
                return;
            }

            final File done = new File(cacheDir, cacheKey(src));
            if (done.isFile() && done.length() == WAV_HEADER_BYTES + outDataLen) {
                done.setLastModified(System.currentTimeMillis());
                outBytesWritten = outDataLen;
                complete = true;
                postReadyOnce(done);
                return;
            }
            cleanCacheDir();

            if (freeBytes(cacheDir) < WAV_HEADER_BYTES + outDataLen + FREE_SPACE_MARGIN_BYTES) {
                postFailedOnce();
                return;
            }

            final File part = new File(cacheDir, done.getName() + ".part");
            final int bytesPerSample = fmt.bitsPerSample / 8;
            final int frameIn = fmt.bytesPerFrame();
            final int chunkFrames = Math.max(decim,
                    (IN_BUFFER_TARGET_BYTES / (frameIn * decim)) * decim);
            final byte[] inBuf = new byte[chunkFrames * frameIn];
            final byte[] outBuf = new byte[(chunkFrames / decim) * outChannels * 2];
            final long headStartBytes = Math.max(MIN_HEAD_START_BYTES,
                    (long) outByteRate * HEAD_START_SECONDS);

            FileInputStream in = null;
            FileOutputStream out = null;
            boolean finished = false;
            try {
                in = new FileInputStream(src);
                skipFully(in, fmt.dataOffset);
                out = new FileOutputStream(part);
                out.write(buildHeader(outChannels, outRate, outDataLen));

                long framesLeft = outFrames * decim;
                final boolean isFloat = fmt.encoding == WavFormat.ENC_IEEE_FLOAT;
                final boolean is64 = fmt.bitsPerSample == 64;
                while (framesLeft > 0 && !cancelled) {
                    int wantFrames = (int) Math.min(chunkFrames, framesLeft);
                    // Frames past the last full decimation group would desync the header sizes.
                    wantFrames = (wantFrames / decim) * decim;
                    if (wantFrames <= 0) break;
                    int got = readFully(in, inBuf, wantFrames * frameIn);
                    int gotFrames = (got / frameIn / decim) * decim;
                    if (gotFrames <= 0) break;

                    int outPos = 0;
                    for (int f = 0; f < gotFrames; f += decim) {
                        int frameBase = f * frameIn;
                        for (int c = 0; c < outChannels; c++) {
                            int acc = 0;
                            int off = frameBase + c * bytesPerSample;
                            for (int d = 0; d < decim; d++) {
                                acc += isFloat
                                        ? (is64 ? sampleFloat64(inBuf, off) : sampleFloat32(inBuf, off))
                                        : (bytesPerSample == 3 ? samplePcm24(inBuf, off) : samplePcm32(inBuf, off));
                                off += frameIn;
                            }
                            int v = acc / decim;
                            outBuf[outPos++] = (byte) v;
                            outBuf[outPos++] = (byte) (v >> 8);
                        }
                    }
                    out.write(outBuf, 0, outPos);
                    outBytesWritten += outPos;
                    framesLeft -= gotFrames;

                    if (!readyPosted && outBytesWritten >= headStartBytes) {
                        postReadyOnce(part);
                    }
                    if (got < wantFrames * frameIn) break; // source truncated
                }
                out.getFD().sync();
                finished = !cancelled;
            } finally {
                if (in != null) try { in.close(); } catch (IOException ignored) {}
                if (out != null) try { out.close(); } catch (IOException ignored) {}
                if (!finished && !readyPosted) part.delete();
            }

            if (cancelled) {
                part.delete();
                return;
            }
            if (!finished || outBytesWritten < outDataLen) {
                // Short write (truncated source): a partial file must not poison the cache.
                if (readyPosted) complete = true; // stop capping seeks; playback plays what exists
                else postFailedOnce();
                return;
            }
            // Rename under the player's open fd is safe — the descriptor follows the inode.
            if (!part.renameTo(done)) {
                complete = true;
                if (!readyPosted) postReadyOnce(part);
                return;
            }
            complete = true;
            postReadyOnce(done);
        }

        private void postReadyOnce(final File playable) {
            if (readyPosted) return;
            readyPosted = true;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!cancelled) listener.onReady(playable);
                }
            });
        }

        private void postFailedOnce() {
            if (readyPosted) return;
            readyPosted = true;
            complete = true;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!cancelled) listener.onFailed();
                }
            });
        }
    }

    // --- sample extraction: little-endian, scaled+rounded to 16-bit, clamped ---

    static int samplePcm24(byte[] b, int off) {
        int s = (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | (b[off + 2] << 16);
        s = (s + 128) >> 8;
        return s > 32767 ? 32767 : s;
    }

    static int samplePcm32(byte[] b, int off) {
        long s = (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((long) b[off + 3] << 24);
        s = (s + 32768) >> 16;
        return s > 32767 ? 32767 : (int) s;
    }

    static int sampleFloat32(byte[] b, int off) {
        int bits = (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
        return clampFloat(Float.intBitsToFloat(bits));
    }

    static int sampleFloat64(byte[] b, int off) {
        long bits = 0;
        for (int i = 7; i >= 0; i--) bits = (bits << 8) | (b[off + i] & 0xFFL);
        return clampFloat((float) Double.longBitsToDouble(bits));
    }

    private static int clampFloat(float f) {
        if (f != f) return 0; // NaN
        int v = Math.round(f * 32767f);
        if (v > 32767) return 32767;
        if (v < -32768) return -32768;
        return v;
    }

    // --- plumbing ---

    static byte[] buildHeader(int channels, int sampleRate, long dataLen) {
        byte[] h = new byte[WAV_HEADER_BYTES];
        int byteRate = sampleRate * channels * 2;
        putTag(h, 0, "RIFF");
        putLE32(h, 4, 36 + dataLen);
        putTag(h, 8, "WAVE");
        putTag(h, 12, "fmt ");
        putLE32(h, 16, 16);
        putLE16(h, 20, 1); // PCM
        putLE16(h, 22, channels);
        putLE32(h, 24, sampleRate);
        putLE32(h, 28, byteRate);
        putLE16(h, 32, channels * 2); // block align
        putLE16(h, 34, 16); // bits
        putTag(h, 36, "data");
        putLE32(h, 40, dataLen);
        return h;
    }

    private static void putTag(byte[] b, int off, String tag) {
        for (int i = 0; i < 4; i++) b[off + i] = (byte) tag.charAt(i);
    }

    private static void putLE16(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >> 8);
    }

    private static void putLE32(byte[] b, int off, long v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >> 8);
        b[off + 2] = (byte) (v >> 16);
        b[off + 3] = (byte) (v >> 24);
    }

    static String cacheKey(File src) {
        return Integer.toHexString(src.getAbsolutePath().hashCode())
                + "_" + src.length() + "_" + src.lastModified() + ".wav";
    }

    /** Drop stale .part files and all but the newest completed transcodes. */
    private void cleanCacheDir() {
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        File newest = null;
        for (File f : files) {
            if (f.getName().endsWith(".part")) {
                f.delete();
            } else if (CACHE_KEEP_COMPLETED > 0
                    && (newest == null || f.lastModified() > newest.lastModified())) {
                newest = f;
            }
        }
        for (File f : files) {
            if (!f.getName().endsWith(".part") && f != newest) f.delete();
        }
    }

    @SuppressWarnings("deprecation")
    private static long freeBytes(File dir) {
        try {
            StatFs fs = new StatFs(dir.getAbsolutePath());
            return (long) fs.getAvailableBlocks() * fs.getBlockSize();
        } catch (Exception e) {
            return Long.MAX_VALUE; // unknown fs — let the write itself fail if space runs out
        }
    }

    private static void skipFully(InputStream in, long bytes) throws IOException {
        long left = bytes;
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped <= 0) {
                if (in.read() < 0) throw new IOException("EOF while skipping to data chunk");
                skipped = 1;
            }
            left -= skipped;
        }
    }

    private static int readFully(InputStream in, byte[] buf, int want) throws IOException {
        int got = 0;
        while (got < want) {
            int n = in.read(buf, got, want - got);
            if (n < 0) break;
            got += n;
        }
        return got;
    }
}
