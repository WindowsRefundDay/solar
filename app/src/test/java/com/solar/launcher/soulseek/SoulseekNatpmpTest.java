package com.solar.launcher.soulseek;

import org.junit.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SoulseekNatpmpTest {

    @Test
    public void mappedResult() {
        SoulseekNatpmp.Result r = new SoulseekNatpmp.Result(61001, 61001, "1.2.3.4", "192.168.1.1", "ok");
        assertTrue(r.mapped());
        SoulseekNatpmp.Result bad = new SoulseekNatpmp.Result(61001, 61001, null, null, "map_failed");
        assertFalse(bad.mapped());
    }

    @Test
    public void subnetGatewayUsesLastOctet() throws Exception {
        InetAddress local = InetAddress.getByName("192.168.5.42");
        InetAddress gw = SoulseekNatpmp.subnetGateway(local, 1);
        assertNotNull(gw);
        assertEquals("192.168.5.1", gw.getHostAddress());
    }

    @Test
    public void gatewayCandidatesPreferDistinctAddresses() throws Exception {
        InetAddress local = InetAddress.getByName("10.0.0.50");
        List<InetAddress> gws = SoulseekNatpmp.gatewayCandidates(null, local);
        assertTrue(gws.size() >= 2);
        assertEquals("10.0.0.1", gws.get(gws.size() - 2).getHostAddress());
    }

    @Test
    public void portParseFromResponseBytes() {
        byte[] mapOk = new byte[16];
        mapOk[1] = (byte) (2 + 128);
        mapOk[10] = (byte) 0xEE;
        mapOk[11] = (byte) 0x11;
        int port = ((mapOk[10] & 0xff) << 8) | (mapOk[11] & 0xff);
        assertEquals(0xEE11, port);
    }
}
