package com.slabbed.test;

import net.minecraft.server.world.ServerChunkManager;

/** A scoped unavailable-chunk fixture; never active outside a synchronous loading assertion. */
public final class PendingHangChunkProbe {
    public static ServerChunkManager manager;
    public static int blockingReads;
    public static int nonblockingReads;

    public static void begin(ServerChunkManager value) {
        manager = value;
        blockingReads = 0;
        nonblockingReads = 0;
    }

    public static void end() {
        manager = null;
    }
}
