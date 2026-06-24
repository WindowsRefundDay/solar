package com.solar.launcher.deezer;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/** Debug session 172597 — Deezer login diagnostics (no secrets). */
public final class DeezerDebugLog {
    private static final String TAG = "DeezerDbg";
    private static final String SESSION = "172597";
    private static final String FILE = "debug-172597.log";

    private DeezerDebugLog() {}

    public static void log(Context ctx, String location, String message, String hypothesisId,
            JSONObject data) {
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            if (ctx != null) {
                try {
                    FileWriter w = new FileWriter(new File(ctx.getFilesDir(), FILE), true);
                    w.write(line);
                    w.write('\n');
                    w.close();
                } catch (Exception ignored) {}
                try {
                    FileWriter w = new FileWriter(new File("/storage/sdcard0", FILE), true);
                    w.write(line);
                    w.write('\n');
                    w.close();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }
}
