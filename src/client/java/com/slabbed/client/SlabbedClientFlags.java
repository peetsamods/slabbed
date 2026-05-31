package com.slabbed.client;

public final class SlabbedClientFlags {
    private SlabbedClientFlags() {}

    /** Enable with JVM arg: -Dslabbed.gapfill=true */
    public static final boolean GAP_FILL = Boolean.getBoolean("slabbed.gapfill");

    /** Enable with JVM arg: -Dslabbed.targetDyOverlay=true */
    public static final boolean TARGET_DY_OVERLAY = Boolean.getBoolean("slabbed.targetDyOverlay");
}
