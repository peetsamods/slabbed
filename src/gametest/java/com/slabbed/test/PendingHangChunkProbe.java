package com.slabbed.test;

import net.minecraft.server.level.ServerChunkCache;

/** A scoped unavailable-chunk fixture; never active outside a synchronous loading assertion. */
public final class PendingHangChunkProbe {
    public static ServerChunkCache manager;
    public static int blockingReads;
    public static int nonblockingReads;
    public static boolean failOnBlockingRead;
    private static volatile Thread ownerThread;

    private PendingHangChunkProbe() {}

    public static void begin(ServerChunkCache value) {
        manager = value;
        blockingReads = 0;
        nonblockingReads = 0;
        failOnBlockingRead = false;
        ownerThread = Thread.currentThread();
    }

    public static boolean activeFor(ServerChunkCache value) {
        return ownerThread == Thread.currentThread() && manager == value;
    }

    public static void end() {
        ownerThread = null;
        manager = null;
        failOnBlockingRead = false;
    }
}
