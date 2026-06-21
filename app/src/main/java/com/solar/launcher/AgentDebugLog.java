package com.solar.launcher;

import org.json.JSONObject;

/** Session debug log — NDJSON on SD card for adb pull to .cursor/debug-81a426.log */
final class AgentDebugLog {
    private static final String PATH = "/sdcard/solar-debug-81a426.log";

    private AgentDebugLog() {}

    static void log(String location, String message, String hypothesisId, JSONObject data) {
        // #region agent log
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", "81a426");
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            if (data != null) o.put("data", data);
            java.io.FileWriter fw = new java.io.FileWriter(PATH, true);
            fw.write(o.toString() + "\n");
            fw.close();
        } catch (Throwable ignored) {}
        // #endregion
    }
}
