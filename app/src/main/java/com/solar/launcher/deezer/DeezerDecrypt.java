package com.solar.launcher.deezer;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Blowfish BF_CBC_STRIPE decryption for Deezer CDN streams. */
public final class DeezerDecrypt {
    private static final int BLOCK_SIZE = 2048;
    private static final byte[] IV = hexToBytes("0001020304050607");
    private static final String KEY_SEED = "g4el58wc0zvf9na1";

    private DeezerDecrypt() {}

    public static String calcBfKey(String songId) {
        if (songId == null) songId = "";
        byte[] md5Hex = md5HexBytes(songId.getBytes(utf8()));
        byte[] seed = KEY_SEED.getBytes(utf8());
        char[] out = new char[16];
        for (int i = 0; i < 16; i++) {
            out[i] = (char) ((md5Hex[i] & 0xff) ^ (md5Hex[i + 16] & 0xff) ^ (seed[i] & 0xff));
        }
        return new String(out);
    }

    public static void decryptStream(InputStream in, OutputStream out, String songId) throws Exception {
        String keyStr = calcBfKey(songId);
        Cipher cipher = Cipher.getInstance("Blowfish/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(keyStr.getBytes(utf8()), "Blowfish"),
                new IvParameterSpec(IV));
        byte[] buf = new byte[BLOCK_SIZE];
        int blockIndex = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            byte[] chunk = buf;
            if (n < BLOCK_SIZE) {
                chunk = new byte[n];
                System.arraycopy(buf, 0, chunk, 0, n);
            }
            if ((blockIndex % 3) == 0 && chunk.length == BLOCK_SIZE) {
                chunk = cipher.doFinal(chunk);
            }
            out.write(chunk);
            blockIndex++;
        }
        out.flush();
    }

    private static byte[] md5HexBytes(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input);
            return bytesToHex(digest).getBytes(utf8());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }

    private static java.nio.charset.Charset utf8() {
        try {
            return java.nio.charset.Charset.forName("UTF-8");
        } catch (Exception e) {
            return java.nio.charset.Charset.defaultCharset();
        }
    }
}
