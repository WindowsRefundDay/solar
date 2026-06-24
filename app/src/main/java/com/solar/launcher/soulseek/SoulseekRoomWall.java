package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;

import com.solar.launcher.soulseek.store.ReachDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persists Soulseek room wall (ticker) posts per chat room. */
public final class SoulseekRoomWall {
    public static final class RoomTicker {
        public final String room;
        public final String username;
        public final String text;
        public final int updatedAt;

        public RoomTicker(String room, String username, String text, int updatedAt) {
            this.room = room != null ? room : "";
            this.username = username != null ? username : "";
            this.text = text != null ? text : "";
            this.updatedAt = updatedAt;
        }
    }

    private SoulseekRoomWall() {}

    public static void replaceTickers(Context ctx, String room, List<SoulseekWire.RoomTickerEntry> tickers) {
        if (ctx == null || room == null || room.isEmpty()) return;
        ReachDatabase.getInstance(ctx).replaceRoomTickers(room, tickers);
    }

    public static void upsertTicker(Context ctx, String room, String username, String text) {
        if (ctx == null || room == null || room.isEmpty()) return;
        ReachDatabase.getInstance(ctx).upsertRoomTicker(room, username, text);
    }

    public static void removeTicker(Context ctx, String room, String username) {
        if (ctx == null || room == null || room.isEmpty()) return;
        ReachDatabase.getInstance(ctx).removeRoomTicker(room, username);
    }

    public static List<RoomTicker> tickersForRoom(Context ctx, String room) {
        if (ctx == null || room == null || room.isEmpty()) {
            return Collections.emptyList();
        }
        List<SoulseekWire.RoomTickerEntry> raw =
                ReachDatabase.getInstance(ctx).tickersForRoomSync(room);
        List<RoomTicker> out = new ArrayList<RoomTicker>();
        int ts = (int) (System.currentTimeMillis() / 1000L);
        for (SoulseekWire.RoomTickerEntry e : raw) {
            if (e == null || e.text.isEmpty()) continue;
            out.add(new RoomTicker(room, e.username, e.text, ts));
        }
        return out;
    }
}
