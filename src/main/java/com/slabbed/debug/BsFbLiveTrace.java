package com.slabbed.debug;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * TEMPORARY live diagnostic for BS-FB regression investigation.
 *
 * <p>Enable with: -Dslabbed.bsfb.live.trace=true
 *
 * <p>This diagnostic captures actual live world state at specific moments
 * to diagnose Julia's reported BS-FB regression:
 * - breaking original bottom slab appears to make the BS-FB system move upward
 * - placing intended 0.5S afterward appears to jump into 1S
 *
 * <p>DO NOT COMMIT unless explicitly approved. Default OFF. No gameplay changes.
 */
public final class BsFbLiveTrace {
    private BsFbLiveTrace() {}

    /** Enable with JVM arg: -Dslabbed.bsfb.live.trace=true */
    public static final boolean ENABLED = Boolean.getBoolean("slabbed.bsfb.live.trace");

    private static long tickCounter = 0;

    /**
     * Client-side visual-triad capture hook. Functional interface (not a record) installed
     * by {@code BsFbLiveTraceClient.init()} during client initialization. Null on dedicated
     * server. Server-thread code calls {@link #captureClient} which delegates here.
     */
    @FunctionalInterface
    public interface ClientCaptureHook {
        void capture(BlockPos supportPos, BlockPos fullPos, String label);
    }

    public static volatile ClientCaptureHook clientCaptureHook = null;

    public static void tick() {
        if (!ENABLED) return;
        tickCounter++;
    }

    public static long getTick() {
        return tickCounter;
    }

    /**
     * Requests a client-side capture of the visual triad for {@code fullPos}.
     * Delegates to {@link #clientCaptureHook} if installed; otherwise no-op.
     * Safe to call from any thread; the hook itself dispatches to the render thread.
     */
    public static void captureClient(BlockPos supportPos, BlockPos fullPos, String label) {
        if (!ENABLED) return;
        ClientCaptureHook hook = clientCaptureHook;
        if (hook == null) return;
        try {
            hook.capture(supportPos, fullPos, label);
        } catch (Throwable t) {
            Slabbed.LOGGER.warn("[BSFB-LIVE-TRACE] client capture hook threw {}", t.toString());
        }
    }

    /**
     * Captures comprehensive state for a BS-FB assembly.
     *
     * @param world the world
     * @param supportPos position of the bottom slab (support)
     * @param fullPos position of the full block above the slab
     * @param label trace label (e.g., "BEFORE_BREAK", "AFTER_BREAK", etc.)
     */
    public static void capture(World world, BlockPos supportPos, BlockPos fullPos, String label) {
        if (!ENABLED) return;
        if (world == null || supportPos == null || fullPos == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("[BSFB-LIVE-TRACE] ").append(label)
          .append(" tick=").append(tickCounter)
          .append(" side=").append(world.isClient() ? "CLIENT" : "SERVER");

        // 1. supportPos state
        BlockState supportState = world.getBlockState(supportPos);
        sb.append(" | supportPos=").append(supportPos.toShortString())
          .append(" state=").append(supportState);

        // 2. fullPos state
        BlockState fullState = world.getBlockState(fullPos);
        sb.append(" | fullPos=").append(fullPos.toShortString())
          .append(" state=").append(fullState);

        // 3. slabPos state (supportPos)
        String slabTypeAtSupport = "none";
        if (supportState.getBlock() instanceof SlabBlock && supportState.contains(SlabBlock.TYPE)) {
            slabTypeAtSupport = supportState.get(SlabBlock.TYPE).toString();
        }
        sb.append(" | slabTypeAtSupport=").append(slabTypeAtSupport);

        // 4. slabPos.up state (fullPos) - if it's a slab
        String slabTypeAtFull = "none";
        if (fullState.getBlock() instanceof SlabBlock && fullState.contains(SlabBlock.TYPE)) {
            slabTypeAtFull = fullState.get(SlabBlock.TYPE).toString();
        }
        sb.append(" | slabTypeAtFull=").append(slabTypeAtFull);

        // 5. anchor status at fullPos
        boolean anchored = SlabAnchorAttachment.isAnchored(world, fullPos);
        sb.append(" | anchored=").append(anchored);

        // 6. dy for fullPos
        double fullDy = SlabSupport.getYOffset(world, fullPos, fullState);
        sb.append(" | fullDy=").append(fullDy);

        // 7. dy for supportPos (if it has a block)
        double supportDy = SlabSupport.getYOffset(world, supportPos, supportState);
        sb.append(" | supportDy=").append(supportDy);

        // 8. isAir check for fullPos (distinguishes anchor-without-block)
        boolean fullIsAir = fullState.isAir();
        sb.append(" | fullIsAir=").append(fullIsAir);

        Slabbed.LOGGER.info(sb.toString());
    }

    /**
     * Lightweight log for simple events.
     */
    public static void log(String message) {
        if (!ENABLED) return;
        Slabbed.LOGGER.info("[BSFB-LIVE-TRACE] " + message + " tick=" + tickCounter);
    }
}
