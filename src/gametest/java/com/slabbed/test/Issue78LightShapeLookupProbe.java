package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.test.GameTest;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Native world-aware shape queries must not request a pending chunk from a lighting worker. */
public final class Issue78LightShapeLookupProbe implements ModInitializer {
    public static volatile ServerChunkManager interceptedManager;
    public static final AtomicInteger blockingReads = new AtomicInteger();
    private static Block dynamicBlock;

    @Override
    public void onInitialize() {
        dynamicBlock = Registry.register(Registries.BLOCK,
                Identifier.of("slabbed_gametest", "dynamic_light_shape"),
                new Block(AbstractBlock.Settings.create().dynamicBounds()));
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = "issue78_native_shape_probe")
    public void recognizedWorkerAvoidsBlockingLookup(TestContext ctx) throws InterruptedException {
        runQuery(ctx, "Worker-Main-shape-probe", false, true);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = "issue78_native_shape_probe")
    public void scalableLuxWorkerAvoidsBlockingLookup(TestContext ctx) throws InterruptedException {
        runQuery(ctx, "scalablelux-0", false, true);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = "issue78_native_shape_probe")
    public void directOffsetGuardAvoidsBlockingLookup(TestContext ctx) throws InterruptedException {
        runQuery(ctx, "scalablelux-0", true, true);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = "issue78_native_shape_probe")
    public void lookupInterceptorHasTeeth(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        interceptedManager = world.getChunkManager();
        blockingReads.set(0);
        try {
            world.getChunkManager().getChunk(1024, 1024,
                    net.minecraft.world.chunk.ChunkStatus.FULL, true);
            ctx.assertTrue(blockingReads.get() == 1,
                    "the interceptor must detect and short-circuit the waiting API");
        } finally {
            interceptedManager = null;
        }
        System.out.println("[ISSUE78_PROBE] interceptor_control blocking=1 PASS");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = "issue78_native_shape_probe")
    public void arbitraryWorkerAvoidsBlockingLookup(TestContext ctx) throws InterruptedException {
        runQuery(ctx, "other-lighting-worker", false, true);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = "issue78_native_shape_probe")
    public void serverThreadRetainsPlacementPolicyRead(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(16384, 64, 16384));
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        blockingReads.set(0);
        interceptedManager = world.getChunkManager();
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        try {
            try {
                dynamicBlock.getDefaultState().getCullingFace(world, pos, Direction.NORTH);
            } catch (IllegalStateException interceptedLookup) {
                // The interceptor returns null for a missing chunk, which vanilla rejects.
                ctx.assertTrue(blockingReads.get() > 0,
                        "the server-thread exception must follow the intercepted chunk lookup");
            }
            ctx.assertTrue(blockingReads.get() > 0,
                    "server-thread native shapes must still read the placement policy");
            System.out.println("[ISSUE78_PROBE] server_thread_policy blocking="
                    + blockingReads.get() + " PASS");
        } finally {
            interceptedManager = null;
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        ctx.complete();
    }

    private static void runQuery(TestContext ctx, String workerName, boolean directOffset,
                                 boolean expectNoBlockingLookup) throws InterruptedException {
        ServerWorld world = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(new BlockPos(16384, 64, 16384));
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        blockingReads.set(0);
        interceptedManager = world.getChunkManager();
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        Thread worker = new Thread(() -> {
            try {
                if (directOffset) {
                    SlabSupport.getYOffset(world, pos, Blocks.STONE.getDefaultState());
                } else {
                    dynamicBlock.getDefaultState().getCullingFace(world, pos, Direction.NORTH);
                }
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                finished.countDown();
            }
        }, workerName);
        worker.setDaemon(true);
        try {
            worker.start();
            boolean completed = finished.await(2, TimeUnit.SECONDS);
            int calls = blockingReads.get();
            System.out.println("[ISSUE78_PROBE] thread=" + workerName + " route="
                    + (directOffset ? "direct_offset" : "native_culling")
                    + " blocking=" + calls + " completed=" + completed
                    + " error=" + (failure.get() == null ? "none" : failure.get().getClass().getSimpleName()));
            ctx.assertTrue(completed, "shape query did not finish after its waiting lookup was intercepted");
            ctx.assertTrue(!expectNoBlockingLookup || calls == 0,
                    "background native shape entered the waiting chunk API " + calls + " times");
            ctx.assertTrue(failure.get() == null, "shape query threw " + failure.get());
        } finally {
            interceptedManager = null;
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        ctx.complete();
    }
}
