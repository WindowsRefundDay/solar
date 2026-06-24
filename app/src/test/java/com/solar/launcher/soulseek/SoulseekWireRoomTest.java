package com.solar.launcher.soulseek;

import org.junit.Test;

import java.util.List;

public class SoulseekWireRoomTest {

    @Test
    public void packJoinRoom_roundTripName() throws Exception {
        byte[] packed = SoulseekWire.packJoinRoom("jazz");
        SoulseekWire.Reader r = new SoulseekWire.Reader(packed);
        if (!"jazz".equals(r.readString())) throw new AssertionError("room");
        if (r.readUInt32() != 0) throw new AssertionError("private flag");
    }

    @Test
    public void packSayChatroom_fields() throws Exception {
        byte[] packed = SoulseekWire.packSayChatroom("lobby", "hello");
        SoulseekWire.Reader r = new SoulseekWire.Reader(packed);
        if (!"lobby".equals(r.readString())) throw new AssertionError("room");
        if (!"hello".equals(r.readString())) throw new AssertionError("msg");
    }

    @Test
    public void parseRoomList_publicRooms() throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        bos.write(SoulseekWire.packUInt32(2));
        bos.write(SoulseekWire.packString("Jazz"));
        bos.write(SoulseekWire.packString("Rock"));
        bos.write(SoulseekWire.packUInt32(2));
        bos.write(SoulseekWire.packUInt32(12));
        bos.write(SoulseekWire.packUInt32(34));
        List<SoulseekWire.RoomEntry> rooms = SoulseekWire.parseRoomList(bos.toByteArray());
        if (rooms.size() < 2) throw new AssertionError("size");
        if (rooms.get(0).userCount != 12) throw new AssertionError("jazz users");
    }
}
