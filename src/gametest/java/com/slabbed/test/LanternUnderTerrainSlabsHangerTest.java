package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Live-reported bug (2026-07-04, confirmed in-game via the /slabdy overlay with cache==fresh,
 * ruling out staleness): a hanging LANTERN under a cantilevered Terrain-Slabs (TS) bottom slab
 * rendered with a visible GAP between the lantern top and the slab underside. A hanging SIGN in
 * the exact same position correctly stayed flush; with a VANILLA slab the lantern was correct.
 *
 * <p>Root cause (adversarially verified by a 7-agent workflow): a REGRESSION from THIS session's
 * own L8/L10 anchor-widening, which made cantilevered TS slabs anchorable for the first time.
 * The lantern's dy lane ({@code isLoweredUndersideHangerOwner}, getYOffsetInner ~line 1083) calls
 * {@code loweredSlabUndersideSupportDy}, which returns -0.5 for any anchored slab with NO
 * {@code shouldSkipOffset} guard. TS renders its slabs FLUSH (it owns its own vertical offset),
 * so the lantern dropped -0.5 below the flush underside -> the gap. The hanging SIGN avoids this
 * because {@code ceilingHungDecorationDy} wraps its identical lowering in
 * {@code if (!CompatHooks.shouldSkipOffset(above))} -- the sign is the already-correct reference.
 *
 * <p>Fix (the shared-predicate-correct layer, per this project's own half-fix lesson): fold the
 * invariant INTO {@code loweredSlabUndersideSupportDy} (return flush for a {@code shouldSkipOffset}
 * slab), BEFORE its cache short-circuit -- so it fixes BOTH unguarded consumers (the hanger lane
 * AND the object-standing-on-a-TOP/DOUBLE-slab lane at ~line 1180), not just the one the user hit.
 */
public final class LanternUnderTerrainSlabsHangerTest {

    private static final double EPS = 1.0e-6;

    // THE FIX: a hanging lantern under an anchored, flush-rendered Terrain-Slabs bottom slab must
    // read flush (0.0), matching how a hanging sign in the same spot already behaves.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lanternUnderAnchoredTerrainSlabsBottomSlabStaysFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos vanillaBottomSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos dirtPos = vanillaBottomSlabPos.up();
        BlockPos tsSlabPos = dirtPos.east();      // TS bottom slab, cantilevered, anchors via adjacency to dirt
        BlockPos lanternPos = tsSlabPos.down();   // lantern hangs from the TS slab underside, air below

        w.setBlockState(vanillaBottomSlabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, dirtPos), "setup: dirt must anchor on the bottom slab");

        w.setBlockState(tsSlabPos, TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, tsSlabPos, w.getBlockState(tsSlabPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, tsSlabPos),
                "setup: the cantilevered TS bottom slab must anchor (the L8/L10 anchor-widening that "
                        + "caused this regression) -- otherwise this test proves nothing");

        w.setBlockState(lanternPos, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true),
                Block.NOTIFY_LISTENERS);
        double lanternDy = SlabSupport.getYOffset(w, lanternPos, w.getBlockState(lanternPos));
        ctx.assertTrue(Math.abs(lanternDy) <= EPS,
                "a lantern hanging under a flush-rendered Terrain-Slabs slab must stay FLUSH (0.0), "
                        + "matching the hanging sign -- Terrain Slabs owns its own offset; got " + lanternDy);
        ctx.complete();
    }

    // REGRESSION GUARD (the anti-jam behaviour the lane exists for): a lantern under a lowered
    // VANILLA bottom slab must STILL follow it down to -0.5, or the lowered slab underside clips
    // into the lantern top. The fix must change ONLY the Terrain-Slabs case.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lanternUnderLoweredVanillaBottomSlabStillFollows(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos vanillaBottomSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 4);
        BlockPos dirtPos = vanillaBottomSlabPos.up();
        BlockPos supportSlabPos = dirtPos.east();   // VANILLA bottom slab, cantilevered, anchored via adjacency
        BlockPos lanternPos = supportSlabPos.down();

        w.setBlockState(vanillaBottomSlabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));

        w.setBlockState(supportSlabPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, supportSlabPos, w.getBlockState(supportSlabPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, supportSlabPos),
                "setup: the vanilla support slab must anchor");
        double supportDy = SlabSupport.getYOffset(w, supportSlabPos, w.getBlockState(supportSlabPos));
        ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS,
                "setup: the vanilla support slab must itself render -0.5 (Slabbed lowers it, unlike TS); got " + supportDy);

        w.setBlockState(lanternPos, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true),
                Block.NOTIFY_LISTENERS);
        double lanternDy = SlabSupport.getYOffset(w, lanternPos, w.getBlockState(lanternPos));
        ctx.assertTrue(Math.abs(lanternDy + 0.5) <= EPS,
                "regression: a lantern under a lowered VANILLA slab must STILL follow it to -0.5 "
                        + "(anti-jam) -- the fix must only change the Terrain-Slabs case; got " + lanternDy);
        ctx.complete();
    }
}
