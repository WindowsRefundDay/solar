package com.solar.launcher.soulseek;

import org.junit.Test;

public class SoulseekSearchQueryTest {

  @Test
  public void stripsPunctuation() {
    String q = SoulseekSearchQuery.sanitize("artist (live)");
    if (q.contains("(") || q.contains(")")) throw new AssertionError(q);
    if (!q.contains("artist")) throw new AssertionError(q);
  }

  @Test
  public void collapsesWhitespace() {
    String q = SoulseekSearchQuery.sanitize("  foo   bar  ");
    if (!"foo bar".equals(q)) throw new AssertionError(q);
  }

  @Test
  public void emptySafe() {
    if (!"".equals(SoulseekSearchQuery.sanitize(null))) throw new AssertionError("null");
    if (!"".equals(SoulseekSearchQuery.sanitize("   "))) throw new AssertionError("blank");
  }
}
