package com.solar.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;

/** WAV header probe + 16-bit conversion math for the API 17 StageFright fallback. */
public class WavFallbackTest {

    // --- probe ---

    @Test
    public void probe16BitPcmNeedsNoTranscode() throws Exception {
        File f = writeWav(1, 2, 44100, 16, new byte[400]);
        WavFormat fmt = WavFormat.probe(f);
        assertNotNull(fmt);
        assertFalse(fmt.needsTranscode());
        assertEquals(2, fmt.channels);
        assertEquals(44100, fmt.sampleRate);
        assertEquals(400, fmt.dataLength);
        assertEquals(100, fmt.frameCount());
        f.delete();
    }

    @Test
    public void probe24BitPcmNeedsTranscode() throws Exception {
        File f = writeWav(1, 2, 96000, 24, new byte[600]);
        WavFormat fmt = WavFormat.probe(f);
        assertNotNull(fmt);
        assertTrue(fmt.needsTranscode());
        assertEquals(24, fmt.bitsPerSample);
        assertEquals(6, fmt.bytesPerFrame());
        f.delete();
    }

    @Test
    public void probeFloat32NeedsTranscode() throws Exception {
        File f = writeWav(3, 1, 48000, 32, new byte[400]);
        WavFormat fmt = WavFormat.probe(f);
        assertNotNull(fmt);
        assertTrue(fmt.needsTranscode());
        assertEquals(WavFormat.ENC_IEEE_FLOAT, fmt.encoding);
        f.delete();
    }

    @Test
    public void probeRejectsNonWav() throws Exception {
        File f = File.createTempFile("notwav", ".wav");
        FileOutputStream out = new FileOutputStream(f);
        out.write(new byte[128]);
        out.close();
        assertNull(WavFormat.probe(f));
        f.delete();
    }

    @Test
    public void probeBuiltFallbackHeaderRoundTrips() throws Exception {
        File f = File.createTempFile("hdr", ".wav");
        FileOutputStream out = new FileOutputStream(f);
        out.write(WavFallback.buildHeader(2, 48000, 4 * 25));
        out.write(new byte[4 * 25]);
        out.close();
        WavFormat fmt = WavFormat.probe(f);
        assertNotNull(fmt);
        assertFalse(fmt.needsTranscode());
        assertEquals(2, fmt.channels);
        assertEquals(48000, fmt.sampleRate);
        assertEquals(16, fmt.bitsPerSample);
        assertEquals(25, fmt.frameCount());
        f.delete();
    }

    // --- conversion math ---

    @Test
    public void pcm24Conversion() {
        assertEquals(32767, WavFallback.samplePcm24(le24(0x7FFFFF), 0)); // full scale clamps
        assertEquals(-32768, WavFallback.samplePcm24(le24(-0x800000), 0));
        assertEquals(0, WavFallback.samplePcm24(le24(0), 0));
        assertEquals(1, WavFallback.samplePcm24(le24(0x100), 0)); // 256/256, exact
        assertEquals(1, WavFallback.samplePcm24(le24(0x80), 0)); // 128 rounds up
        assertEquals(0, WavFallback.samplePcm24(le24(0x7F), 0)); // 127 rounds down
    }

    @Test
    public void pcm32Conversion() {
        assertEquals(32767, WavFallback.samplePcm32(le32(Integer.MAX_VALUE), 0));
        assertEquals(-32768, WavFallback.samplePcm32(le32(Integer.MIN_VALUE), 0));
        assertEquals(0, WavFallback.samplePcm32(le32(0), 0));
        assertEquals(1, WavFallback.samplePcm32(le32(0x10000), 0));
    }

    @Test
    public void float32Conversion() {
        assertEquals(32767, WavFallback.sampleFloat32(leFloat(1.0f), 0));
        assertEquals(-32767, WavFallback.sampleFloat32(leFloat(-1.0f), 0));
        assertEquals(0, WavFallback.sampleFloat32(leFloat(0f), 0));
        assertNear(16384, WavFallback.sampleFloat32(leFloat(0.5f), 0), 1);
        assertEquals(32767, WavFallback.sampleFloat32(leFloat(2.5f), 0)); // over-range clamps
        assertEquals(-32768, WavFallback.sampleFloat32(leFloat(-2.5f), 0));
        assertEquals(0, WavFallback.sampleFloat32(leFloat(Float.NaN), 0));
    }

    @Test
    public void float64Conversion() {
        byte[] b = new byte[8];
        long bits = Double.doubleToLongBits(0.25);
        for (int i = 0; i < 8; i++) b[i] = (byte) (bits >> (8 * i));
        assertNear(8192, WavFallback.sampleFloat64(b, 0), 1);
    }

    @Test
    public void cacheKeyChangesWithFile() {
        File a = new File("/music/a.wav");
        File b = new File("/music/b.wav");
        assertFalse(WavFallback.cacheKey(a).equals(WavFallback.cacheKey(b)));
    }

    private static void assertNear(int expected, int actual, int tolerance) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError("expected " + expected + "±" + tolerance + " got " + actual);
        }
    }

    // --- fixtures ---

    private static byte[] le24(int v) {
        return new byte[] { (byte) v, (byte) (v >> 8), (byte) (v >> 16) };
    }

    private static byte[] le32(int v) {
        return new byte[] { (byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24) };
    }

    private static byte[] leFloat(float f) {
        return le32(Float.floatToIntBits(f));
    }

    /** Minimal RIFF/WAVE with a junk LIST chunk before data — probe must skip unknown chunks. */
    private static File writeWav(int encoding, int channels, int rate, int bits, byte[] data)
            throws Exception {
        File f = File.createTempFile("probe", ".wav");
        FileOutputStream out = new FileOutputStream(f);
        int fmtLen = 16;
        int listLen = 10;
        long riff = 4 + (8 + fmtLen) + (8 + listLen) + (8 + data.length);
        out.write(new byte[] { 'R', 'I', 'F', 'F' });
        writeLE32(out, riff);
        out.write(new byte[] { 'W', 'A', 'V', 'E' });
        out.write(new byte[] { 'f', 'm', 't', ' ' });
        writeLE32(out, fmtLen);
        writeLE16(out, encoding);
        writeLE16(out, channels);
        writeLE32(out, rate);
        writeLE32(out, (long) rate * channels * (bits / 8));
        writeLE16(out, channels * (bits / 8));
        writeLE16(out, bits);
        out.write(new byte[] { 'L', 'I', 'S', 'T' });
        writeLE32(out, listLen);
        out.write(new byte[listLen]);
        out.write(new byte[] { 'd', 'a', 't', 'a' });
        writeLE32(out, data.length);
        out.write(data);
        out.close();
        return f;
    }

    private static void writeLE16(FileOutputStream out, int v) throws Exception {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void writeLE32(FileOutputStream out, long v) throws Exception {
        out.write((int) (v & 0xFF));
        out.write((int) ((v >> 8) & 0xFF));
        out.write((int) ((v >> 16) & 0xFF));
        out.write((int) ((v >> 24) & 0xFF));
    }
}
