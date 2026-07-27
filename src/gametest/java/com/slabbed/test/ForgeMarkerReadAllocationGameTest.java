package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.management.ManagementFactory;

/**
 * Phase 1 fix: marker reads must be allocation-free.
 *
 * <p>Before the fix, EVERY boolean marker query ({@code isAnchored},
 * {@code isPersistentLoweredSlabCarrier}, the compound-visible family…) deep-copied the whole
 * per-chunk bucket ({@code SlabAnchorStore.copy}) and allocated an {@code Optional} per capability
 * resolve — on the server AND on the client outline/raycast/overlay path, which runs per frame.
 * The zero-copy {@code SlabAnchorStore.contains} existed with ZERO callers.
 *
 * <p>This is the automation the project's PERF hygiene gate calls for: an allocation-regression
 * assertion on the hot read path, because this bug class (per-block work on a render path) has
 * shipped twice before and logging hygiene does not catch it.
 *
 * <p>Method: {@code com.sun.management.ThreadMXBean.getThreadAllocatedBytes} around a fixed count
 * of {@code isAnchored} calls. The chunk deliberately carries a REAL anchor marker at a different
 * position, so the pre-fix path copies a non-empty bucket — an allocation escape analysis cannot
 * elide. The measured position itself is unmarked: the miss path is the hot path.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgeMarkerReadAllocationGameTest {

    private static final int WARMUP_CALLS = 20_000;
    private static final int MEASURED_CALLS = 200_000;

    /**
     * Post-fix the read is chunk lookup + capability orElse + EnumMap get + hash probe: zero
     * allocation. Pre-fix it is an Optional plus a bucket copy (~190+ bytes on a 1-entry bucket).
     * 16 bytes/call of headroom tolerates JIT/measurement noise without letting the copy back in.
     */
    private static final double MAX_BYTES_PER_CALL = 16.0d;

    @GameTest(template = "empty")
    public void markerMembershipReadAllocatesNothing(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();

        // A real anchor in the chunk: stone on a bottom slab through the real qualification path.
        BlockPos slabPos = ctx.absolutePos(new BlockPos(1, 1, 1));
        BlockPos anchorPos = slabPos.above();
        world.setBlock(slabPos, Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(anchorPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        SlabAnchorAttachment.addAnchor(world, anchorPos, world.getBlockState(anchorPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, anchorPos),
                "fixture: the anchor marker must exist so the bucket is non-empty");

        // The measured position: same chunk, unmarked. The miss path is the hot path.
        BlockPos probePos = ctx.absolutePos(new BlockPos(3, 1, 3));
        world.setBlock(probePos, Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, probePos),
                "fixture: the probe position must be unmarked");

        com.sun.management.ThreadMXBean tm =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        ctx.assertTrue(tm.isThreadAllocatedMemorySupported(),
                "JVM must support per-thread allocation accounting");
        if (!tm.isThreadAllocatedMemoryEnabled()) {
            tm.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().getId();

        // Warmup so the measured window sees steady-state JIT, then measure.
        boolean sink = false;
        for (int i = 0; i < WARMUP_CALLS; i++) {
            sink ^= SlabAnchorAttachment.isAnchored(world, probePos);
        }
        long before = tm.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < MEASURED_CALLS; i++) {
            sink ^= SlabAnchorAttachment.isAnchored(world, probePos);
        }
        long after = tm.getThreadAllocatedBytes(threadId);

        double perCall = (after - before) / (double) MEASURED_CALLS;
        com.slabbed.Slabbed.LOGGER.info(
                "[MARKER_READ_ALLOC] calls={} bytesTotal={} bytesPerCall={} sink={}",
                MEASURED_CALLS, after - before, String.format("%.2f", perCall), sink);

        ctx.assertTrue(perCall <= MAX_BYTES_PER_CALL,
                "marker membership read must be allocation-free: measured "
                        + String.format("%.2f", perCall) + " bytes/call over " + MEASURED_CALLS
                        + " calls (budget " + MAX_BYTES_PER_CALL + "). The read path is copying "
                        + "buckets again — the per-block-work-on-a-hot-path class that has "
                        + "shipped twice before.");
        ctx.succeed();
    }
}
