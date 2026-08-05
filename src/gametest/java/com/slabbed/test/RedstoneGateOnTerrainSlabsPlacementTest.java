package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SideShapeType;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Live-reported bug (2026-07-04, "can't place redstone repeater/comparator on TS"): a repeater
 * placed fine on a vanilla bottom slab, but four repeated attempts to place one on a
 * Terrain-Slabs-owned bottom slab all failed.
 *
 * <p>Root cause: {@code AbstractRedstoneGateBlock.canPlaceAbove} (real bytecode, confirmed via
 * {@code javap}) checks {@code state.isSideSolid(world, pos, Direction.UP, SideShapeType.RIGID)}
 * on the block below — NOT {@code isSideSolidFullSquare}. {@code SlabSupportStateMixin} patches
 * BOTH methods for bottom slabs, but the two injections use DIFFERENT gates:
 * {@code slabbed$slabTopSolidFullSquare} (the {@code isSideSolidFullSquare} patch) uses
 * {@code SlabSupport.canTreatAsSolidTopFace}, which correctly recognises a Terrain-Slabs-owned
 * bottom slab via {@code CompatHooks.customSlabSurfaceKind == BOTTOM_LIKE}. But
 * {@code slabbed$slabTopSolid} (the {@code isSideSolid} patch — the one this vanilla method
 * actually calls) used the narrower {@code SlabSupport.isBottomSlab}, which delegates to
 * {@code isSupportingSlab}, which explicitly returns {@code false} for ANY Terrain-Slabs-owned
 * state via {@code CompatHooks.shouldSkipSlabSupport}. So a TS bottom slab's UP face was patched
 * solid for {@code isSideSolidFullSquare} but NOT for plain {@code isSideSolid} — exactly the one
 * redstone gate placement actually calls.
 *
 * <p>Empirically confirmed via a throwaway probe before writing this test: with the pre-fix code,
 * {@code isSideSolid(UP, RIGID)} was {@code true} for a vanilla bottom slab and {@code false} for
 * a Terrain-Slabs-owned one, and {@code Blocks.REPEATER.getDefaultState().canPlaceAt(...)} was
 * {@code true} above the vanilla slab and {@code false} above the Terrain-Slabs one — reproducing
 * the live report exactly, using only the headless {@link TerrainSlabsTestShim} (a plain vanilla
 * {@code SlabBlock} under the {@code terrain_slabs} namespace — the bug is a namespace/mixin-gate
 * issue, not a custom-shape issue, so the shim reproduces it faithfully).
 *
 * <p>Fix: {@code slabbed$slabTopSolid} now uses {@code SlabSupport.canTreatAsSolidTopFace},
 * matching its {@code isSideSolidFullSquare} sibling exactly.
 */
public final class RedstoneGateOnTerrainSlabsPlacementTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void repeaterCanPlaceOnTerrainSlabsBottomSlab(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos vanillaPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos tsPos = vanillaPos.east();

        w.setBlockState(vanillaPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(tsPos, TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);

        ctx.assertTrue(w.getBlockState(vanillaPos).isSideSolid(w, vanillaPos, Direction.UP, SideShapeType.RIGID),
                "setup: vanilla bottom slab must report RIGID UP-solid");

        ctx.assertTrue(
                w.getBlockState(tsPos).isSideSolid(w, tsPos, Direction.UP, SideShapeType.RIGID),
                "a Terrain-Slabs-owned bottom slab must ALSO report RIGID UP-solid, or redstone "
                        + "gates (repeater/comparator) and any other RIGID-gated block cannot be "
                        + "placed on it (live-reported 'can't place redstone repeater/comparator on TS')");

        ctx.assertTrue(Blocks.REPEATER.getDefaultState().canPlaceAt(w, tsPos.up()),
                "a repeater must be placeable directly on top of a Terrain-Slabs-owned bottom slab");
        ctx.assertTrue(Blocks.COMPARATOR.getDefaultState().canPlaceAt(w, tsPos.up()),
                "a comparator must be placeable directly on top of a Terrain-Slabs-owned bottom slab");
        ctx.complete();
    }

    // REGRESSION GUARD: a TOP-type / non-bottom Terrain Slabs slab must not spuriously report
    // solid on a face it does not own (matches canTreatAsSolidTopFace's own scope).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsTopSlabDoesNotClaimBottomSolidity(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 4);
        w.setBlockState(pos, TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(!SlabSupport.isBottomSlab(w.getBlockState(pos)),
                "setup: a TOP-type slab must not be classified as a bottom slab");
        ctx.complete();
    }
}
