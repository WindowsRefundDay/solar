package com.solar.launcher.soulseek;

import org.junit.Test;

import java.util.List;

public class SoulseekWireInterestsTest {

    @Test
    public void parseUserInterestsResponse_roundTrip() throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        bos.write(SoulseekWire.packString("peer"));
        bos.write(SoulseekWire.packUInt32(2));
        bos.write(SoulseekWire.packString("jazz"));
        bos.write(SoulseekWire.packString("rock"));
        bos.write(SoulseekWire.packUInt32(1));
        bos.write(SoulseekWire.packString("dubstep"));
        SoulseekWire.UserInterestsResponse resp =
                SoulseekWire.parseUserInterestsResponse(bos.toByteArray());
        if (!"peer".equals(resp.username)) throw new AssertionError("user");
        if (resp.likes.size() != 2 || resp.dislikes.size() != 1) {
            throw new AssertionError("counts");
        }
        if (!"jazz".equals(resp.likes.get(0)) || !"dubstep".equals(resp.dislikes.get(0))) {
            throw new AssertionError("items");
        }
    }

    @Test
    public void packInterestString_matchesPackString() throws Exception {
        byte[] a = SoulseekWire.packInterestString("test");
        byte[] b = SoulseekWire.packString("test");
        if (a.length != b.length) throw new AssertionError("len");
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) throw new AssertionError("byte " + i);
        }
    }
}
