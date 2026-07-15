package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RebootBreadcrumbTest {
    @Test public void formatsCredentialFreeSingleLineFields() {
        assertEquals("reason=ota_bad\ncommand=reboot_now\nwallMs=12\nelapsedMs=34\n",
                RebootBreadcrumb.format("ota\nbad", "reboot\rnow", 12, 34));
    }

    @Test public void nullFieldsBecomeUnknown() {
        assertEquals("reason=unknown\ncommand=unknown\nwallMs=1\nelapsedMs=2\n",
                RebootBreadcrumb.format(null, null, 1, 2));
    }
}
