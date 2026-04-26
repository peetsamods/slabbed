package com.slabbed.util;

public final class SlabbedDebug {
    private SlabbedDebug() {}

    /** Enable with JVM arg: -Dslabbed.debug.sbsb=true */
    public static final boolean DEBUG_SBSB = Boolean.getBoolean("slabbed.debug.sbsb");
}
