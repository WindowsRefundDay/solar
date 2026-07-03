package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;

import com.solar.launcher.DeviceFeatures;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consolidated developer support thread: one local conversation, three wire recipients.
 * Auto diagnostic PMs are never stored here.
 */
public final class SolarDeveloperMessaging {

    private SolarDeveloperMessaging() {}

    /** Per-recipient PM fan-out outcome — all targets must succeed for a clean send. */
    public static final class FanOutResult {
        public final String[] recipients;
        private final boolean[] succeeded;

        FanOutResult(String[] recipients, boolean[] succeeded) {
            this.recipients = recipients != null ? recipients : new String[0];
            this.succeeded = succeeded != null ? succeeded : new boolean[0];
        }

        /** True when every non-empty recipient got the PM. */
        public boolean allSucceeded() {
            if (recipients.length == 0) return false;
            for (int i = 0; i < recipients.length; i++) {
                String r = recipients[i];
                if (r == null || r.isEmpty()) continue;
                if (i >= succeeded.length || !succeeded[i]) return false;
            }
            return true;
        }

        public boolean anySucceeded() {
            if (recipients.length == 0) return false;
            for (int i = 0; i < recipients.length; i++) {
                String r = recipients[i];
                if (r == null || r.isEmpty()) continue;
                if (i < succeeded.length && succeeded[i]) return true;
            }
            return false;
        }

        /** Wire accounts that still need delivery. */
        public String[] failedRecipients() {
            List<String> out = new ArrayList<String>();
            for (int i = 0; i < recipients.length; i++) {
                if (recipients[i] == null || recipients[i].isEmpty()) continue;
                if (i >= succeeded.length || !succeeded[i]) out.add(recipients[i]);
            }
            return out.toArray(new String[out.size()]);
        }

        /** Merge main-client and diag-session attempts (either path counts as success). */
        FanOutResult merge(FanOutResult other) {
            if (other == null) return this;
            boolean[] merged = new boolean[recipients.length];
            for (int i = 0; i < recipients.length; i++) {
                boolean ok = i < succeeded.length && succeeded[i];
                if (!ok && other.recipients != null) {
                    for (int j = 0; j < other.recipients.length; j++) {
                        if (recipients[i] != null
                                && recipients[i].equalsIgnoreCase(other.recipients[j])
                                && j < other.succeeded.length && other.succeeded[j]) {
                            ok = true;
                            break;
                        }
                    }
                }
                merged[i] = ok;
            }
            return new FanOutResult(recipients, merged);
        }

        static FanOutResult from(String[] recipients, boolean[] perRecipient) {
            return new FanOutResult(recipients, perRecipient);
        }

        static FanOutResult allFailed(String[] recipients) {
            boolean[] s = new boolean[recipients != null ? recipients.length : 0];
            return new FanOutResult(recipients, s);
        }
    }

    public static List<SoulseekMessaging.Message> thread(Context ctx, SharedPreferences prefs) {
        List<SoulseekMessaging.Message> raw = SoulseekMessaging.thread(
                ctx, prefs, SolarDeveloperAccounts.VIRTUAL_PEER);
        if (raw == null) return raw;
        java.util.ArrayList<SoulseekMessaging.Message> out = new java.util.ArrayList<SoulseekMessaging.Message>();
        for (SoulseekMessaging.Message m : raw) {
            if (m == null || SolarDeveloperAccounts.isAutoDiagnosticText(m.text)) continue;
            out.add(m);
        }
        return out;
    }

    public static void appendIncoming(Context ctx, SharedPreferences prefs, String fromDev,
            int msgId, int timestamp, String text) {
        if (ctx == null || text == null || SolarDeveloperAccounts.isAutoDiagnosticText(text)) return;
        String stored = SolarDeveloperAccounts.packDevIncoming(fromDev, text);
        SoulseekMessaging.append(ctx, prefs, new SoulseekMessaging.Message(
                msgId, timestamp, SolarDeveloperAccounts.VIRTUAL_PEER, stored, true));
    }

    /** Every Solar Development thread open — fresh hidden diagnostic bundle (not debounced). */
    public static void onThreadOpened(Context ctx, SharedPreferences prefs) {
    }

    public static void sendUserMessage(final Context ctx, final SharedPreferences prefs,
            final SoulseekClient client, final String text,
            final SoulseekClient.MessageSendCallback callback) {
        if (callback != null) callback.onError("Disabled");
    }

    static String formatUserSupportWireBody(Context ctx, SharedPreferences prefs, String text) {
        return text;
    }

    static FanOutResult sendWireFanOut(Context ctx, SharedPreferences prefs, SoulseekClient client,
            String[] recipients, String body) {
        return FanOutResult.allFailed(recipients);
    }

    static FanOutResult sendViaMainClient(final SoulseekClient client, String[] recipients,
            final String body) {
        return FanOutResult.allFailed(recipients);
    }

    static FanOutResult sendViaDiagSession(Context ctx, SharedPreferences prefs,
            String[] recipients, String body) {
        return FanOutResult.allFailed(recipients);
    }

    public static void forwardToOtherDevs(Context ctx, SharedPreferences prefs,
            String fromDev, String text) {
    }

    public static SoulseekMessaging.Message lastVisibleMessage(Context ctx, SharedPreferences prefs) {
        List<SoulseekMessaging.Message> t = thread(ctx, prefs);
        if (t == null || t.isEmpty()) return null;
        return t.get(t.size() - 1);
    }

}
