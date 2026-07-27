package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;

/**
 * STAYS Phase 3 contract: when a height is stored, the stored value IS the height — everywhere,
 * in both directions, for every block family — and the unstored twin still sees pure geometry.
 *
 * <p>These rows are the law the whole campaign exists to establish:
 * <ul>
 *   <li>stored beats geometry DOWNWARD (−0.375, a value no lane can produce, wins over flat);</li>
 *   <li>stored beats the lanes UPWARD (R3: an explicit +0.0 wins over a lane that says −0.5 —
 *       a slab shoved underneath later pulls nothing down);</li>
 *   <li>a neighbour edit cannot move a stored height (R2 in miniature);</li>
 *   <li>a stored height beats the carpet visual rule;</li>
 *   <li>the kill-switch restores pre-store behaviour exactly;</li>
 *   <li>the non-Level render-view seam reads stored truth (the chunk mesher never hands a
 *       {@code Level} to the dy path);</li>
 *   <li>the writer refuses exactly what the reader skips (guard symmetry — a value the reader
 *       would never consult must never be storable).</li>
 * </ul>
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgeStoredDyAuthorityGameTest {

    @GameTest(template = "empty")
    public void storedValueIsTheHeightAuthority(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos aPos = ctx.absolutePos(new BlockPos(1, 2, 1));
        BlockPos bPos = ctx.absolutePos(new BlockPos(3, 2, 1));
        BlockPos cPos = ctx.absolutePos(new BlockPos(5, 2, 1));
        BlockPos dPos = ctx.absolutePos(new BlockPos(7, 2, 1));
        java.util.function.LongToDoubleFunction priorLookup =
                SlabAnchorAttachment.clientPlacementDyLookup;
        try {
            runRows(ctx, world, aPos, bPos, cPos, dPos);
        } finally {
            // STRIKE THE STORE. Phase 3 made stored entries live height authority, and chunk
            // capabilities key on ABSOLUTE positions that outlive gametest structure
            // re-placement (retries, roster-driven plot reuse). A leaked entry is the F5
            // haunting with teeth. Clear every write even when a row aborts, and restore the
            // seam rather than nulling it — an in-world run on an integrated server must not
            // strip a mirror-installed lookup until relog.
            SlabAnchorAttachment.clientPlacementDyLookup = priorLookup;
            SlabAnchorAttachment.removePlacementDy(world, aPos);
            SlabAnchorAttachment.removePlacementDy(world, bPos);
            SlabAnchorAttachment.removePlacementDy(world, cPos);
            SlabAnchorAttachment.removePlacementDy(world, dPos);
        }
        ctx.succeed();
    }

    private static void runRows(GameTestHelper ctx, ServerLevel world,
                                BlockPos a, BlockPos b, BlockPos c, BlockPos d) {
        // --- 1. stored beats geometry downward; the unstored twin still sees geometry ---
        world.setBlock(a.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
        world.setBlock(a, Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
        SlabAnchorAttachment.writePlacementDy(world, a, -0.375d);
        assertDy(ctx, SlabSupport.getYOffset(world, a, world.getBlockState(a)), -0.375d,
                "stored -0.375 must win over flat geometry");
        assertDy(ctx, SlabSupport.getUnstoredYOffset(world, a, world.getBlockState(a)), 0.0d,
                "getUnstoredYOffset must skip the store and see geometry");

        // --- 2. R3: stored +0.0 beats a lane that says -0.5 — the store wins UPWARD too ---
        world.setBlock(b.below(), Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_NONE);
        world.setBlock(b, Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
        assertDy(ctx, SlabSupport.getUnstoredYOffset(world, b, world.getBlockState(b)), -0.5d,
                "fixture: the lanes must genuinely say -0.5 here, or this row proves nothing");
        SlabAnchorAttachment.writePlacementDy(world, b, 0.0d);
        double storedZero = SlabSupport.getYOffset(world, b, world.getBlockState(b));
        ctx.assertTrue(Double.doubleToRawLongBits(storedZero) == Double.doubleToRawLongBits(0.0d),
                "R3: stored +0.0 must beat the -0.5 lane, raw-bit exact; got " + storedZero);

        // --- 3. R2 in miniature: a neighbour edit cannot move a stored height ---
        world.setBlock(c, Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
        SlabAnchorAttachment.writePlacementDy(world, c, -1.5d);
        world.setBlock(c.below(), Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_NONE); // the attack
        assertDy(ctx, SlabSupport.getYOffset(world, c, world.getBlockState(c)), -1.5d,
                "a slab shoved underneath must not move a stored height");

        // --- 4. stored beats the carpet visual rule; removal restores it ---
        world.setBlock(d.below(), Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_NONE);
        world.setBlock(d, Blocks.RED_CARPET.defaultBlockState(), Block.UPDATE_NONE);
        SlabAnchorAttachment.writePlacementDy(world, d, -1.0d);
        assertDy(ctx, SlabSupport.getYOffset(world, d, world.getBlockState(d)), -1.0d,
                "a stored height must beat the carpet rule");
        SlabAnchorAttachment.removePlacementDy(world, d);
        assertDy(ctx, SlabSupport.getYOffset(world, d, world.getBlockState(d)), -0.5d,
                "with the store cleared, carpet returns to its canonical rule");

        // --- 5. kill-switch: pre-store behaviour, exactly, then back ---
        boolean prior = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        try {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
            assertDy(ctx, SlabSupport.getYOffset(world, a, world.getBlockState(a)), 0.0d,
                    "switch off: stored -0.375 must be invisible");
            SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
            assertDy(ctx, SlabSupport.getYOffset(world, a, world.getBlockState(a)), -0.375d,
                    "switch back on: stored -0.375 must win again");
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = prior;
        }

        // --- 6. the non-Level render-view seam ---
        BlockGetter renderView = new NonLevelView(world);
        try {
            SlabAnchorAttachment.clientPlacementDyLookup =
                    packed -> packed == a.asLong() ? -2.5d : Double.NaN;
            assertDy(ctx, SlabAnchorAttachment.storedPlacementDy(renderView, a), -2.5d,
                    "a non-Level view must read stored truth through the seam");
            assertDy(ctx, SlabSupport.getYOffset(renderView, a, world.getBlockState(a)), -2.5d,
                    "getYOffset through a render view must consult the seam");
            ctx.assertTrue(Double.isNaN(SlabAnchorAttachment.storedPlacementDy(renderView, c)),
                    "the seam must report absent for positions it does not know");
        } finally {
            SlabAnchorAttachment.clientPlacementDyLookup = null;
        }
        ctx.assertTrue(Double.isNaN(SlabAnchorAttachment.storedPlacementDy(renderView, a)),
                "with no seam installed, a non-Level view honestly reads absent");

        // --- 7. guard symmetry: the writer refuses what the reader skips ---
        BlockPos e = ctx.absolutePos(new BlockPos(1, 2, 3));
        world.setBlock(e, Blocks.POWDER_SNOW.defaultBlockState(), Block.UPDATE_NONE);
        ctx.assertTrue(!SlabAnchorAttachment.writePlacementDy(world, e, -0.5d),
                "the reader skips powder snow, so the writer must refuse it");
        ctx.assertTrue(Double.isNaN(SlabAnchorAttachment.storedPlacementDy(world, e)),
                "nothing may be stored at a refused position");
        BlockPos f = ctx.absolutePos(new BlockPos(3, 2, 3)); // air: reader answers 0.0 unconditionally
        ctx.assertTrue(!SlabAnchorAttachment.writePlacementDy(world, f, -0.5d),
                "air is reader-skipped, so the writer must refuse it");
    }

    private static void assertDy(GameTestHelper ctx, double got, double expected, String what) {
        ctx.assertTrue(Double.compare(got, expected) == 0,
                what + ": expected " + expected + " got " + got);
    }

    /** Wraps the level so the dy path sees a BlockGetter that is NOT a Level — the mesher's view. */
    private record NonLevelView(ServerLevel delegate) implements BlockGetter {
        @Override public BlockState getBlockState(BlockPos pos) { return delegate.getBlockState(pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return delegate.getFluidState(pos); }
        @Nullable @Override public BlockEntity getBlockEntity(BlockPos pos) { return delegate.getBlockEntity(pos); }
        @Override public int getHeight() { return delegate.getHeight(); }
        @Override public int getMinBuildHeight() { return delegate.getMinBuildHeight(); }
    }
}
