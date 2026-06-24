package com.solar.launcher.soulseek;

import org.junit.Test;

public class SoulseekInterestsTest {
    @Test
    public void systemInterests_blockedFromUserLists() {
        assert SoulseekInterests.isSystemInterest("innioasis");
        assert SoulseekInterests.isSystemInterest("Reach Client");
        assert SoulseekInterests.systemLikes().contains("reach client");
    }
}
