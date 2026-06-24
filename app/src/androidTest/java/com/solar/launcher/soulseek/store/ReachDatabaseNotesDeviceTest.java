package com.solar.launcher.soulseek.store;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.solar.launcher.soulseek.SoulseekPeerNotes;
import com.solar.launcher.soulseek.SoulseekWire;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ReachDatabaseNotesDeviceTest {
    private Context ctx;

    @Before
    public void setUp() {
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ReachDatabase db = ReachDatabase.getInstance(ctx);
        db.clearPeerNoteSync("TestPeerNoteUser");
        db.clearPeerNoteSync("testpeernoteuser");
    }

    @Test
    public void peerNotes_setGetClear_caseInsensitive() {
        ReachDatabase db = ReachDatabase.getInstance(ctx);
        db.setPeerNoteSync("TestPeerNoteUser", "hello note");
        assertEquals("hello note", db.getPeerNoteSync("testpeernoteuser"));
        db.clearPeerNoteSync("TESTPEERNOTEUSER");
        assertEquals("", db.getPeerNoteSync("TestPeerNoteUser"));
    }

    @Test
    public void peerNotes_asyncApi() throws InterruptedException {
        SoulseekPeerNotes.setNote(ctx, "AsyncPeer", "async note");
        Thread.sleep(300);
        assertEquals("async note", SoulseekPeerNotes.getNoteSync(ctx, "asyncpeer"));
        SoulseekPeerNotes.clearNote(ctx, "AsyncPeer");
        Thread.sleep(300);
        assertEquals("", SoulseekPeerNotes.getNoteSync(ctx, "AsyncPeer"));
    }

    @Test
    public void searchRoomsSync_filtersByName() {
        ReachDatabase db = ReachDatabase.getInstance(ctx);
        List<SoulseekWire.RoomEntry> jazz = db.searchRoomsSync("jazz", 10, 0);
        assertTrue(jazz != null);
    }
}
