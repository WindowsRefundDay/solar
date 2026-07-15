package com.solar.launcher;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;

/** Durable, credential-free reason record written immediately before a deliberate reboot. */
final class RebootBreadcrumb {
    private RebootBreadcrumb() {}

    static void record(Context context, String reason, String command) {
        if (context == null) return;
        FileOutputStream output = null;
        try {
            File file = new File(context.getFilesDir(), "last-reboot-reason.txt");
            output = new FileOutputStream(file, false);
            output.write(format(reason, command, System.currentTimeMillis(),
                    android.os.SystemClock.elapsedRealtime()).getBytes("UTF-8"));
            output.flush();
            output.getFD().sync();
        } catch (Exception ignored) {
        } finally {
            try {
                if (output != null) output.close();
            } catch (Exception ignored) {}
        }
    }

    static String format(String reason, String command, long wallMs, long elapsedMs) {
        return "reason=" + sanitize(reason) + "\n"
                + "command=" + sanitize(command) + "\n"
                + "wallMs=" + wallMs + "\n"
                + "elapsedMs=" + elapsedMs + "\n";
    }

    private static String sanitize(String value) {
        if (value == null) return "unknown";
        return value.replace('\n', '_').replace('\r', '_');
    }
}
