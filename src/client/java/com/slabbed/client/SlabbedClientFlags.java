package com.slabbed.client;

public final class SlabbedClientFlags {
    private SlabbedClientFlags() {}

    /** Enable with JVM arg: -Dslabbed.gapfill=true */
    public static final boolean GAP_FILL = Boolean.getBoolean("slabbed.gapfill");

    /**
     * Initial state of the /slabdy target-dy overlay; toggle in-game with /slabdy.
     * Defaults OFF so release/profile launches stay clean unless explicitly enabled.
     */
    public static final boolean TARGET_DY_OVERLAY =
            Boolean.parseBoolean(System.getProperty("slabbed.targetDyOverlay", "false"));
}
