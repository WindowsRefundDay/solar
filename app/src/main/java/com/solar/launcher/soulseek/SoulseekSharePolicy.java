package com.solar.launcher.soulseek;

/**
 * Wi‑Fi + NAT-PMP gates for Soulseek sharing and peer profile/browse exchange.
 * When both are OK, sharing stays active on battery (Reach open or closed).
 * Without Wi‑Fi or a mapped listen port, sharing and uploads are off so Reach
 * does not keep the Soulseek client running.
 */
public final class SoulseekSharePolicy {
    public enum State { OFF, ACTIVE, DRAINING }

    static final long DRAIN_TIMEOUT_MS = 30_000;

    private volatile State state = State.OFF;
    private volatile long drainStartedAt;
    private volatile boolean userEnabled = true;
    private volatile boolean reachMasterEnabled = true;
    private volatile boolean wifi;
    private volatile boolean peerConnectivityOk;

    public State state() {
        return state;
    }

    public boolean isUserEnabled() {
        return userEnabled;
    }

    public void setUserEnabled(boolean enabled) {
        userEnabled = enabled;
    }

    public boolean isReachMasterEnabled() {
        return reachMasterEnabled;
    }

    public void setReachMasterEnabled(boolean enabled) {
        reachMasterEnabled = enabled;
    }

    /**
     * @param peerConnectivityOk true when NAT-PMP mapped the listen port ({@code ReachPeerConnectivity.AVAILABLE})
     * @param uploadActive true while an outbound upload transfer is in progress
     */
    public void update(boolean wifi, boolean peerConnectivityOk, boolean uploadActive) {
        this.wifi = wifi;
        this.peerConnectivityOk = peerConnectivityOk;
        if (!reachMasterEnabled || !userEnabled || !wifi) {
            state = State.OFF;
            drainStartedAt = 0;
            return;
        }
        if (!peerConnectivityOk) {
            if (uploadActive) {
                state = State.DRAINING;
                if (drainStartedAt == 0) {
                    drainStartedAt = System.currentTimeMillis();
                }
            } else if (state == State.DRAINING && drainStartedAt > 0
                    && System.currentTimeMillis() - drainStartedAt >= DRAIN_TIMEOUT_MS) {
                state = State.OFF;
                drainStartedAt = 0;
            } else if (!uploadActive) {
                state = State.OFF;
                drainStartedAt = 0;
            }
            return;
        }
        state = State.ACTIVE;
        drainStartedAt = 0;
    }

    public void onUploadQueueEmpty() {
        if (state == State.DRAINING) {
            state = State.OFF;
            drainStartedAt = 0;
        }
    }

    /** Publish share counts and allow inbound browse/profile when Wi‑Fi + NAT-PMP are OK. */
    public boolean announceShares() {
        return reachMasterEnabled && userEnabled && wifi && peerConnectivityOk;
    }

    public boolean acceptNewUploads() {
        return state == State.ACTIVE;
    }

    public boolean processUploadQueue() {
        return state == State.ACTIVE || state == State.DRAINING;
    }

    private volatile boolean messagingEnabled;

    public void setMessagingEnabled(boolean enabled) {
        messagingEnabled = enabled;
    }

    /** Keep server connection only on Wi‑Fi with a mapped listen port. */
    public boolean shouldKeepClientAlive() {
        if (!reachMasterEnabled || !wifi || !peerConnectivityOk) return false;
        if (messagingEnabled) return true;
        return userEnabled || state == State.DRAINING;
    }

    /** True while NAT-PMP probe has not finished (client may connect once to test). */
    public boolean isPeerConnectivityOk() {
        return peerConnectivityOk;
    }
}
