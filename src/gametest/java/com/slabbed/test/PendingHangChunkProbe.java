package com.slabbed.test;

import net.minecraft.server.level.ServerChunkCache;

/** A scoped unavailable-chunk fixture; never active outside a synchronous loading assertion. */
public final class PendingHangChunkProbe {
    public static ServerChunkCache manager;
    public static int blockingReads;
    public static int nonblockingReads;

    private PendingHangChunkProbe() {}

    public static void begin(ServerChunkCache value) {
        manager = value;
        blockingReads = 0;
        nonblockingReads = 0;
    }

    public static void end() {
        manager = null;
    }
}
