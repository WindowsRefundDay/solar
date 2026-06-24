package com.solar.launcher.soulseek;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SoulseekWireRoomTickerTest {

    @Test
    public void packSetRoomTicker_roundTrip() throws Exception {
        byte[] packed = SoulseekWire.packSetRoomTicker("lobby", "hello wall");
        SoulseekWire.Reader r = new SoulseekWire.Reader(packed);
        assertEquals("lobby", r.readString());
        assertEquals("hello wall", r.readString());
    }

    @Test
    public void parseRoomTickers_list() throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        bos.write(SoulseekWire.packString("lobby"));
        bos.write(SoulseekWire.packUInt32(2));
        bos.write(SoulseekWire.packString("alice"));
        bos.write(SoulseekWire.packString("hi"));
        bos.write(SoulseekWire.packString("bob"));
        bos.write(SoulseekWire.packString("yo"));
        SoulseekWire.RoomTickersMessage msg = SoulseekWire.parseRoomTickers(bos.toByteArray());
        assertNotNull(msg);
        assertEquals("lobby", msg.room);
        assertEquals(2, msg.tickers.size());
        assertEquals("alice", msg.tickers.get(0).username);
        assertEquals("hi", msg.tickers.get(0).text);
    }

    @Test
    public void parseRoomTickerAdded_fields() throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        bos.write(SoulseekWire.packString("lobby"));
        bos.write(SoulseekWire.packString("alice"));
        bos.write(SoulseekWire.packString("wall text"));
        assertEquals("lobby", SoulseekWire.parseRoomTickerAddedRoom(bos.toByteArray()));
        SoulseekWire.RoomTickerEntry e = SoulseekWire.parseRoomTickerAdded(bos.toByteArray());
        assertNotNull(e);
        assertEquals("alice", e.username);
        assertEquals("wall text", e.text);
    }
}
