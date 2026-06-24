package com.solar.launcher.soulseek;

import org.json.JSONObject;

public final class ReachDirectoryClientTest {
    public static void main(String[] args) {
        JSONObject o = new JSONObject();
        try {
            o.put("username", "Y1-plume-wave-42");
            o.put("device", "Y1");
            o.put("lastSeen", 1000L);
            o.put("registeredAt", 500L);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        ReachDirectoryUser u = ReachDirectoryClient.parseUser(o);
        if (u == null || !"Y1-plume-wave-42".equals(u.username)) throw new AssertionError("user");
        if (!"Y1".equals(u.device)) throw new AssertionError("device");
        if (u.lastSeen != 1000L) throw new AssertionError("lastSeen");
        if (ReachDirectoryClient.parseUser(null) != null) throw new AssertionError("null");
    }
}
