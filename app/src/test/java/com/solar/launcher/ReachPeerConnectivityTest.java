package com.solar.launcher;

import com.solar.launcher.soulseek.SoulseekNatpmp;

import org.junit.Test;

public class ReachPeerConnectivityTest {

    @Test
    public void mappedNatMarksAvailable() {
        ReachPeerConnectivity.reset();
        ReachPeerConnectivity.onServerLoginOk(null,
                new SoulseekNatpmp.Result(61000, 61000, "1.2.3.4", "192.168.1.1", "ok"));
        if (ReachPeerConnectivity.state() != ReachPeerConnectivity.State.AVAILABLE) {
            throw new AssertionError("expected available");
        }
    }

    @Test
    public void failedNatMarksUnavailable() {
        ReachPeerConnectivity.reset();
        ReachPeerConnectivity.onNatRetriesExhausted(null,
                new SoulseekNatpmp.Result(61000, 61000, null, "10.0.0.1", "map_failed"));
        if (ReachPeerConnectivity.state() != ReachPeerConnectivity.State.UNAVAILABLE) {
            throw new AssertionError("expected unavailable");
        }
        if (!ReachPeerConnectivity.REASON_NAT_FAILED.equals(ReachPeerConnectivity.reason())) {
            throw new AssertionError("reason=" + ReachPeerConnectivity.reason());
        }
    }
}
