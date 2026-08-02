package com.slabbed.devtools.client;

public final class SlabbedClientFlags {
    private SlabbedClientFlags() {}

    /** Enable with JVM arg: -Dslabbed.gapfill=true */
    public static final boolean GAP_FILL = Boolean.getBoolean("slabbed.gapfill");

    /** TEST/addon launch defaults. Core-only release launches never load this class. */
    public static final boolean TARGET_DY_OVERLAY = true;
    public static final boolean RECORDER = true;
}
