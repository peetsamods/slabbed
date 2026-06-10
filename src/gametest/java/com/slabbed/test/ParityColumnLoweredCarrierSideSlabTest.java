package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Parity port (from the 1.21.1 reference {@code SlabSupport}): a vanilla slab placed at the SIDE
 * of a solid full block that is itself lowered <i>only via its column</i> (the top of a stack
 * standing on a bottom slab, with no chunk anchor recorded) must visually inherit the lowered
 * {@code -0.5} dy, so the side slab aligns with the lowered neighbour instead of floating
 * {@code +0.5} above it (the "DODO" side-step gap).
 *
 * <p>Before the port, the target's {@link SlabSupport} only treated a full block as a lowered
 * carrier when it had a bottom slab DIRECTLY below it or an explicit chunk anchor — a full block
 * lowered through a multi-block column (e.g. dirt on dirt on a bottom slab) was NOT a carrier, so
 * the adjacent side slab stayed at dy=0.0. The reference adds a {@code columnLowered} term
 * (slabColumnYOffset &lt; 0 || isDirectCustomSlabSupportedObject) to {@code isLoweredFullBlockCarrier}.
 *
 * <p>Pure dy logic, no client render / anchor-authoring dependency — fully headless.
 */
public final class ParityColumnLoweredCarrierSideSlabTest {

    private static final double EPS = 1.0e-6;

    private static BlockState vanillaSlab(SlabType type) {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    /**
     * Builds: bottom slab at {@code base}; a vertical column of {@code columnHeight} solid full
     * blocks on top of it; then a vanilla bottom slab on the {@code +X} side of the TOP full block.
     * Returns the dy of that side slab.
     */
    private static double sideSlabDyBesideStack(ServerWorld w, BlockPos base, int columnHeight) {
        w.setBlockState(base, vanillaSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        BlockPos top = base;
        for (int i = 0; i < columnHeight; i++) {
            top = base.up(i + 1);
            w.setBlockState(top, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        // side slab beside the TOP full block of the column
        BlockPos sidePos = top.offset(Direction.EAST);
        w.setBlockState(sidePos, vanillaSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        return SlabSupport.getYOffset(w, sidePos, w.getBlockState(sidePos));
    }

    // A side slab beside a single full block resting DIRECTLY on a bottom slab is lowered (control;
    // this already worked via hasBottomSlabBelow).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sideSlabBesideDirectlySupportedBlockIsLowered(TestContext ctx) {
        double dy = sideSlabDyBesideStack(ctx.getWorld(),
                ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3), 1);
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "side slab beside a block directly on a bottom slab should be lowered -0.5, got " + dy);
        ctx.complete();
    }

    // KEYSTONE: a side slab beside the TOP of a 2-high stack on a bottom slab (column-lowered, no
    // anchor) must also be lowered -0.5. This is the ported fix.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sideSlabBesideColumnLoweredStackIsLowered(TestContext ctx) {
        double dy = sideSlabDyBesideStack(ctx.getWorld(),
                ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3), 2);
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "side slab beside a column-lowered stack should be lowered -0.5 (DODO fix), got " + dy);
        ctx.complete();
    }

    // Control: a side slab beside a plain full block (no slab anywhere in its column) stays flush.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sideSlabBesidePlainBlockStaysFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        // dirt on dirt (NO slab in column), side slab beside the top dirt
        w.setBlockState(base, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos top = base.up();
        w.setBlockState(top, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos sidePos = top.offset(Direction.EAST);
        w.setBlockState(sidePos, vanillaSlab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, sidePos, w.getBlockState(sidePos));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "side slab beside a plain (non-slab-backed) block must stay flush at 0.0, got " + dy);
        ctx.complete();
    }
}
