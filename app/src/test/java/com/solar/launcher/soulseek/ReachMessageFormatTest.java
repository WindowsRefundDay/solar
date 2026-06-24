package com.solar.launcher.soulseek;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReachMessageFormatTest {

  @Test
  public void parseReply_stripsPrefixForDisplay() {
    ReachMessageFormat.Reply r = ReachMessageFormat.parseReply("Reply to \"hello\": world");
    assertNotNull(r);
    assertEquals("hello", r.quote);
    assertEquals("world", r.body);
    assertEquals("world", ReachMessageFormat.displayText("Reply to \"hello\": world"));
  }

  @Test
  public void parseReactionWire_andDisplay() {
    ReachMessageFormat.ReactionWire w =
        ReachMessageFormat.parseReactionWire("reacted to \"hello\" with \uD83D\uDC4D");
    assertNotNull(w);
    assertEquals("hello", w.quote);
    assertEquals("\uD83D\uDC4D", w.emoji);
    assertEquals("", ReachMessageFormat.displayText(
        "reacted to \"hello\" with \uD83D\uDC4D"));
    assertTrue(ReachMessageFormat.isReactionMessage(
        "reacted to \"hello\" with \uD83D\uDC4D"));
  }

  @Test
  public void formatRoundTrip() {
    String reply = ReachMessageFormat.formatReplyWire("quote", "body");
    assertEquals("Reply to \"quote\": body", reply);
    String react = ReachMessageFormat.formatReactionWire("msg", "\u2764\uFE0F");
    assertEquals("reacted to \"msg\" with \u2764\uFE0F", react);
    assertEquals("You reacted: \u2764\uFE0F",
        ReachMessageFormat.formatReactionDisplay("You", "\u2764\uFE0F"));
    assertEquals("alice reacted: \u2764\uFE0F",
        ReachMessageFormat.formatReactionDisplay("alice", "\u2764\uFE0F"));
  }

  @Test
  public void conversationBuilder_nestsReplyAndReaction() {
    List<SoulseekMessaging.Message> raw = Arrays.asList(
        new SoulseekMessaging.Message(1, 100, "bob", "hello", true),
        new SoulseekMessaging.Message(2, 200, "bob",
            "Reply to \"hello\": testing", false),
        new SoulseekMessaging.Message(3, 300, "bob",
            "reacted to \"hello\" with \uD83D\uDC4D", true));
    List<ConversationDisplayBuilder.Entry> entries =
        ConversationDisplayBuilder.build(raw, "me", "bob");
    assertEquals(2, entries.size());
    assertEquals("hello", entries.get(0).displayText);
    assertEquals(1, entries.get(0).reactionLines.size());
    assertTrue(entries.get(0).reactionLines.get(0).contains("reacted:"));
    assertTrue(entries.get(1).isReply);
    assertEquals("testing", entries.get(1).displayText);
  }

  @Test
  public void conversationBuilder_replyBelowMessageWithReactionAfter() {
    List<SoulseekMessaging.Message> raw = Arrays.asList(
        new SoulseekMessaging.Message(1, 100, "bob", "hello", true),
        new SoulseekMessaging.Message(2, 200, "bob",
            "reacted to \"hello\" with \uD83D\uDC4D", true),
        new SoulseekMessaging.Message(3, 300, "bob",
            "Reply to \"hello\": testing", false));
    List<ConversationDisplayBuilder.Entry> entries =
        ConversationDisplayBuilder.build(raw, "me", "bob");
    assertEquals(2, entries.size());
    assertEquals("hello", entries.get(0).displayText);
    assertEquals(1, entries.get(0).reactionLines.size());
    assertTrue(entries.get(1).isReply);
    assertEquals("testing", entries.get(1).displayText);
  }

  @Test
  public void conversationBuilder_replyBelowMessageWithReactionBefore() {
    List<SoulseekMessaging.Message> raw = Arrays.asList(
        new SoulseekMessaging.Message(1, 100, "bob", "hello", true),
        new SoulseekMessaging.Message(2, 200, "bob",
            "Reply to \"hello\": testing", false),
        new SoulseekMessaging.Message(3, 300, "bob",
            "reacted to \"hello\" with \uD83D\uDC4D", true));
    List<ConversationDisplayBuilder.Entry> entries =
        ConversationDisplayBuilder.build(raw, "me", "bob");
    assertEquals(2, entries.size());
    assertTrue(entries.get(1).isReply);
    assertEquals("testing", entries.get(1).displayText);
    assertEquals(1, entries.get(0).reactionLines.size());
  }

  @Test
  public void conversationBuilder_nestedReplyBelowReactedMessage() {
    List<SoulseekMessaging.Message> raw = Arrays.asList(
        new SoulseekMessaging.Message(1, 100, "bob", "hello", true),
        new SoulseekMessaging.Message(2, 200, "bob",
            "Reply to \"hello\": first reply", false),
        new SoulseekMessaging.Message(3, 300, "bob",
            "reacted to \"hello\" with \uD83D\uDC4D", true),
        new SoulseekMessaging.Message(4, 400, "bob",
            "Reply to \"first reply\": nested", false));
    List<ConversationDisplayBuilder.Entry> entries =
        ConversationDisplayBuilder.build(raw, "me", "bob");
    assertEquals(3, entries.size());
    assertEquals("hello", entries.get(0).displayText);
    assertEquals(1, entries.get(0).reactionLines.size());
    assertTrue(entries.get(1).isReply);
    assertEquals("first reply", entries.get(1).displayText);
    assertTrue(entries.get(2).isReply);
    assertEquals("nested", entries.get(2).displayText);
  }

  @Test
  public void parseRoomReply_includesAuthor() {
    String wire = ReachMessageFormat.formatRoomReplyWire("alice", "hello", "thanks");
    ReachMessageFormat.Reply r = ReachMessageFormat.parseReply(wire);
    assertNotNull(r);
    assertEquals("alice", r.quoteAuthor);
    assertEquals("hello", r.quote);
    assertEquals("thanks", r.body);
    assertEquals("thanks", ReachMessageFormat.displayText(wire));
  }

  @Test
  public void roomConversationBuilder_usesSenderInReaction() {
    List<SoulseekChatRooms.RoomMessage> raw = Arrays.asList(
        new SoulseekChatRooms.RoomMessage("lobby", "alice", "hello", 100, true),
        new SoulseekChatRooms.RoomMessage("lobby", "bob",
            "reacted to \"hello\" with \uD83D\uDC4D", 200, true));
    List<RoomConversationDisplayBuilder.Entry> entries =
        RoomConversationDisplayBuilder.build(raw, "me");
    assertEquals(1, entries.size());
    assertEquals(1, entries.get(0).reactionLines.size());
    assertTrue(entries.get(0).reactionLines.get(0).contains("bob reacted:"));
  }

  @Test
  public void findQuotedMessageIndex_prefersExactOverPrefix() {
    List<SoulseekMessaging.Message> raw = Arrays.asList(
        new SoulseekMessaging.Message(1, 100, "bob", "Hello", true),
        new SoulseekMessaging.Message(2, 200, "bob", "Hello world", true),
        new SoulseekMessaging.Message(3, 300, "bob",
            "Reply to \"Hello\": answer", false));
    int parent = ReachMessageFormat.findQuotedMessageIndex(raw, "Hello", 3);
    assertEquals(0, parent);
  }

  @Test
  public void findQuotedMessageIndex_matchesDisplayText() {
    List<SoulseekMessaging.Message> raw = Arrays.asList(
        new SoulseekMessaging.Message(1, 100, "bob", "hello", true),
        new SoulseekMessaging.Message(2, 200, "bob",
            "Reply to \"hello\": world", false));
    int parent = ReachMessageFormat.findQuotedMessageIndex(raw, "hello", 1);
    assertEquals(0, parent);
  }
}
