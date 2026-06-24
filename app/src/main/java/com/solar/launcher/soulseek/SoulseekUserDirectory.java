package com.solar.launcher.soulseek;

/** Tracks watched Soulseek users and their last-known status. */
public final class SoulseekUserDirectory {

    public interface Callback {
        void onUserInfo(UserInfo info);
        void onError(String reason);
    }

    public static final class UserInfo {
        public final String username;
        public final boolean exists;
        public final int status;
        public final int avgSpeed;
        public final int uploadCount;
        public final int files;
        public final int dirs;
        public final String country;
        public final boolean privileged;

        UserInfo(String username, boolean exists, int status, int avgSpeed, int uploadCount,
                 int files, int dirs, String country, boolean privileged) {
            this.username = username;
            this.exists = exists;
            this.status = status;
            this.avgSpeed = avgSpeed;
            this.uploadCount = uploadCount;
            this.files = files;
            this.dirs = dirs;
            this.country = country != null ? country : "";
            this.privileged = privileged;
        }

        public boolean isOnline() {
            return exists && status == SoulseekWire.STATUS_ONLINE;
        }
    }

    private SoulseekUserDirectory() {}

    public static void watch(final SoulseekClient client, final String username,
                             final Callback callback) {
        if (client == null || username == null || username.trim().isEmpty()) {
            if (callback != null) callback.onError("No user");
            return;
        }
        client.watchUser(username.trim(), callback);
    }
}
