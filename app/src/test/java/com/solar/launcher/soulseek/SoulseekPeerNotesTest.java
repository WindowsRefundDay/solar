package com.solar.launcher.soulseek;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SoulseekPeerNotesTest {

    @Test
    public void normalizeUsername_trimsAndLowercases() {
        assertEquals("alice", SoulseekPeerNotes.normalizeUsername("  Alice  "));
        assertEquals("bob", SoulseekPeerNotes.normalizeUsername("BOB"));
    }

    @Test
    public void normalizeUsername_emptyOnNull() {
        assertEquals("", SoulseekPeerNotes.normalizeUsername(null));
        assertEquals("", SoulseekPeerNotes.normalizeUsername("   "));
    }
}
