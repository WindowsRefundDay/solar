package com.solar.launcher.soulseek;

/** Active Reach user from the directory worker. */
public final class ReachDirectoryUser {
    public final String username;
    public final String device;
    public final long lastSeen;
    public final long registeredAt;

    public ReachDirectoryUser(String username, String device, long lastSeen, long registeredAt) {
        this.username = username != null ? username : "";
        this.device = device != null ? device : "Y1";
        this.lastSeen = lastSeen;
        this.registeredAt = registeredAt;
    }
}
