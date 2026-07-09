package com.solar.launcher;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * RIFF/WAVE header probe — reads only the fmt/data chunk headers, never the audio payload.
 * API 17 StageFright decodes 8/16-bit integer PCM WAV only; 24-bit, 32-bit and IEEE-float
 * files are rejected as corrupted, so {@link #needsTranscode()} flags them for WavFallback.
 */
final class WavFormat {

    static final int ENC_PCM = 1;
    static final int ENC_IEEE_FLOAT = 3;
    private static final int ENC_EXTENSIBLE = 0xFFFE;
    private static final int MAX_CHUNKS = 64;

    final int encoding;       // ENC_PCM or ENC_IEEE_FLOAT (WAVE_FORMAT_EXTENSIBLE resolved)
    final int channels;
    final int sampleRate;
    final int bitsPerSample;
    final long dataOffset;
    final long dataLength;

    private WavFormat(int encoding, int channels, int sampleRate, int bitsPerSample,
            long dataOffset, long dataLength) {
        this.encoding = encoding;
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.bitsPerSample = bitsPerSample;
        this.dataOffset = dataOffset;
        this.dataLength = dataLength;
    }

    boolean needsTranscode() {
        if (encoding == ENC_IEEE_FLOAT) return true;
        return bitsPerSample != 8 && bitsPerSample != 16;
    }

    int bytesPerFrame() {
        return (bitsPerSample / 8) * channels;
    }

    long frameCount() {
        int bpf = bytesPerFrame();
        return bpf > 0 ? dataLength / bpf : 0;
    }

    /** null when the file is not a parseable PCM/float RIFF/WAVE. */
    static WavFormat probe(File f) {
        if (f == null || !f.isFile() || f.length() < 44) return null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
            long fileLen = raf.length();
            byte[] hdr = new byte[12];
            raf.readFully(hdr);
            if (!tagEquals(hdr, 0, 'R', 'I', 'F', 'F') || !tagEquals(hdr, 8, 'W', 'A', 'V', 'E')) {
                return null;
            }

            int encoding = -1, channels = 0, sampleRate = 0, bits = 0;
            long dataOffset = -1, dataLength = -1;
            byte[] chunkHdr = new byte[8];
            byte[] fmt = new byte[40];
            long pos = 12;
            for (int i = 0; i < MAX_CHUNKS && pos + 8 <= fileLen; i++) {
                raf.seek(pos);
                raf.readFully(chunkHdr);
                long chunkSize = readLE32(chunkHdr, 4) & 0xFFFFFFFFL;
                if (tagEquals(chunkHdr, 0, 'f', 'm', 't', ' ')) {
                    int toRead = (int) Math.min(fmt.length, Math.min(chunkSize, fileLen - pos - 8));
                    if (toRead < 16) return null;
                    raf.readFully(fmt, 0, toRead);
                    encoding = readLE16(fmt, 0);
                    channels = readLE16(fmt, 2);
                    sampleRate = readLE32(fmt, 4);
                    bits = readLE16(fmt, 14);
                    if (encoding == ENC_EXTENSIBLE) {
                        // cbSize(16) + validBits(18) + channelMask(20) + subformat GUID(24..)
                        if (toRead >= 26) encoding = readLE16(fmt, 24);
                        else return null;
                    }
                } else if (tagEquals(chunkHdr, 0, 'd', 'a', 't', 'a')) {
                    dataOffset = pos + 8;
                    dataLength = Math.min(chunkSize, fileLen - dataOffset);
                }
                if (encoding >= 0 && dataOffset >= 0) break;
                if (chunkSize <= 0) break;
                pos += 8 + chunkSize + (chunkSize & 1);
            }

            if (dataOffset < 0 || dataLength <= 0 || channels <= 0 || channels > 8
                    || sampleRate < 8000 || sampleRate > 384000) {
                return null;
            }
            if (encoding == ENC_PCM) {
                if (bits != 8 && bits != 16 && bits != 24 && bits != 32) return null;
            } else if (encoding == ENC_IEEE_FLOAT) {
                if (bits != 32 && bits != 64) return null;
            } else {
                return null;
            }
            return new WavFormat(encoding, channels, sampleRate, bits, dataOffset, dataLength);
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            if (raf != null) try { raf.close(); } catch (IOException ignored) {}
        }
    }

    private static boolean tagEquals(byte[] b, int off, char a, char c, char d, char e) {
        return b[off] == a && b[off + 1] == c && b[off + 2] == d && b[off + 3] == e;
    }

    private static int readLE16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int readLE32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }
}
