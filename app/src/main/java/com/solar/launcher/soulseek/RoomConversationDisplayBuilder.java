package com.solar.launcher.soulseek;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds chat room thread rows with replies nested and reactions attached. */
public final class RoomConversationDisplayBuilder {

  public static final class Entry {
    public final SoulseekChatRooms.RoomMessage message;
    public final String displayText;
    public final boolean isReply;
    public final List<String> reactionLines;
    public final String timestamp;

    Entry(SoulseekChatRooms.RoomMessage message, String displayText, boolean isReply,
        List<String> reactionLines, String timestamp) {
      this.message = message;
      this.displayText = displayText != null ? displayText : "";
      this.isReply = isReply;
      this.reactionLines = reactionLines != null ? reactionLines : new ArrayList<String>();
      this.timestamp = timestamp != null ? timestamp : "";
    }

    public boolean isLongBody() {
      if (displayText.contains("\n")) return true;
      return displayText.length() > 42;
    }

    public int bodyLineCount(boolean selected) {
      List<String> lines = ReachMessageFormat.splitDisplayLines(displayText, 8);
      if (lines.isEmpty()) return 1;
      if (!selected) return isLongBody() ? 2 : 1;
      return Math.min(lines.size(), 2);
    }
  }

  private RoomConversationDisplayBuilder() {}

  public static List<Entry> build(List<SoulseekChatRooms.RoomMessage> messages,
      String selfUsername) {
    List<Entry> out = new ArrayList<Entry>();
    if (messages == null || messages.isEmpty()) return out;

    Map<Integer, List<String>> reactionsByParent = new HashMap<Integer, List<String>>();
    Set<Integer> skipIndices = new HashSet<Integer>();
    Map<Integer, List<Integer>> repliesByParent = new HashMap<Integer, List<Integer>>();
    Set<Integer> replyIndices = new HashSet<Integer>();

    for (int i = 0; i < messages.size(); i++) {
      SoulseekChatRooms.RoomMessage m = messages.get(i);
      if (m == null || m.text == null || m.statusEvent) continue;
      if (ReachIntroMessage.isIntro(m.text)) {
        skipIndices.add(i);
        continue;
      }

      ReachMessageFormat.ReactionWire wire = ReachMessageFormat.parseReactionWire(m.text);
      if (wire != null) {
        int parent = ReachMessageFormat.findQuotedRoomMessageIndex(
                messages, wire.quote, null, i);
        if (parent >= 0) {
          String who = reactionActorLabel(m, selfUsername);
          addReaction(reactionsByParent, parent,
              ReachMessageFormat.formatReactionDisplay(who, wire.emoji));
        }
        skipIndices.add(i);
        continue;
      }

      ReachMessageFormat.ReactionDisplay disp = ReachMessageFormat.parseReactionDisplay(m.text);
      if (disp != null) {
        skipIndices.add(i);
        continue;
      }

      ReachMessageFormat.Reply reply = ReachMessageFormat.parseReply(m.text);
      if (reply != null) {
        int parent = ReachMessageFormat.findQuotedRoomMessageIndex(
                messages, reply.quote, reply.quoteAuthor, i);
        if (parent >= 0) {
          List<Integer> list = repliesByParent.get(parent);
          if (list == null) {
            list = new ArrayList<Integer>();
            repliesByParent.put(parent, list);
          }
          list.add(i);
          replyIndices.add(i);
        }
      }
    }

    Set<Integer> emitted = new HashSet<Integer>();
    for (int i = 0; i < messages.size(); i++) {
      if (skipIndices.contains(i) || replyIndices.contains(i)) continue;
      SoulseekChatRooms.RoomMessage m = messages.get(i);
      if (m == null) continue;
      if (m.statusEvent) {
        out.add(newEntry(m, m.text != null ? m.text : "", false, reactionsFor(reactionsByParent, i)));
        emitted.add(i);
        continue;
      }
      if (ReachIntroMessage.isIntro(m.text)) continue;

      String display = ReachMessageFormat.displayText(m.text);
      if (display.isEmpty() && !ReachIntroMessage.isIntro(m.text)) {
        display = m.text != null ? m.text.trim() : "";
      }
      if (ReachIntroMessage.isIntro(m.text)) continue;
      out.add(newEntry(m, display, false, reactionsFor(reactionsByParent, i)));
      emitted.add(i);
      appendReplyChain(out, messages, repliesByParent, reactionsByParent, emitted, i, selfUsername);
    }

    for (int i = 0; i < messages.size(); i++) {
      if (skipIndices.contains(i) || !replyIndices.contains(i) || emitted.contains(i)) continue;
      SoulseekChatRooms.RoomMessage m = messages.get(i);
      ReachMessageFormat.Reply rp = ReachMessageFormat.parseReply(m.text);
      if (rp == null) continue;
      out.add(newEntry(m, rp.body, true, reactionsFor(reactionsByParent, i)));
      emitted.add(i);
      appendReplyChain(out, messages, repliesByParent, reactionsByParent, emitted, i, selfUsername);
    }

    return out;
  }

  private static void appendReplyChain(List<Entry> out, List<SoulseekChatRooms.RoomMessage> messages,
      Map<Integer, List<Integer>> repliesByParent,
      Map<Integer, List<String>> reactionsByParent, Set<Integer> emitted, int parentIndex,
      String selfUsername) {
    List<Integer> replyList = repliesByParent.get(parentIndex);
    if (replyList == null) return;
    for (int ri : replyList) {
      if (emitted.contains(ri)) continue;
      SoulseekChatRooms.RoomMessage rm = messages.get(ri);
      if (rm == null) continue;
      ReachMessageFormat.Reply rp = ReachMessageFormat.parseReply(rm.text);
      String body = rp != null ? rp.body : ReachMessageFormat.displayText(rm.text);
      out.add(newEntry(rm, body, true, reactionsFor(reactionsByParent, ri)));
      emitted.add(ri);
      appendReplyChain(out, messages, repliesByParent, reactionsByParent, emitted, ri, selfUsername);
    }
  }

  private static List<String> reactionsFor(Map<Integer, List<String>> reactionsByParent, int index) {
    return reactionsByParent.containsKey(index)
        ? new ArrayList<String>(reactionsByParent.get(index))
        : new ArrayList<String>();
  }

  private static Entry newEntry(SoulseekChatRooms.RoomMessage m, String display, boolean isReply,
      List<String> reactions) {
    return new Entry(m, display, isReply, reactions,
        SoulseekChatRooms.formatTimestamp(m.timestamp));
  }

  private static String reactionActorLabel(SoulseekChatRooms.RoomMessage m, String selfUsername) {
    if (!m.incoming) return "You";
    if (m.sender != null && !m.sender.isEmpty()) return m.sender;
    return "User";
  }

  private static void addReaction(Map<Integer, List<String>> map, int parent, String line) {
    List<String> list = map.get(parent);
    if (list == null) {
      list = new ArrayList<String>();
      map.put(parent, list);
    }
    if (!list.contains(line)) list.add(line);
  }
}
