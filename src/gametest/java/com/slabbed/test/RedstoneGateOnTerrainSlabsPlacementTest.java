package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SideShapeType;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Coverage gap-fill ported from the 1.21.11 sibling branch's {@code
 * RedstoneGateOnTerrainSlabsPlacementTest} (live bug report, 2026-07-04: "can't place redstone
 * repeater/comparator on Terrain Slabs").
 *
 * <p>On the 1.21.11 sibling, {@code SlabSupportStateMixin}'s {@code isSideSolid} injection
 * ({@code slabbed$slabTopSolid}) used the narrower {@code SlabSupport.isBottomSlab} gate while its
 * sibling {@code isSideSolidFullSquare} injection ({@code slabbed$slabTopSolidFullSquare}) used the
 * wider {@code SlabSupport.canTreatAsSolidTopFace} (which also recognises a Terrain-Slabs-owned
 * bottom-like surface via {@code isDirectCustomBottomLikeSurface}). Since
 * {@code AbstractRedstoneGateBlock.canPlaceAbove} calls {@code isSideSolid} (not
 * {@code isSideSolidFullSquare}), a repeater/comparator could not be placed above a TS bottom slab.
 *
 * <p>On THIS branch (1.21.1), {@code SlabSupportStateMixin.slabbed$slabTopSolid} and
 * {@code slabbed$slabTopSolidFullSquare} were already found (by static read) to share the exact
 * same gate: {@code SlabSupport.isBottomSlab(self) || SlabSupport.isDirectCustomBottomLikeSurface(self)}.
 * This test empirically PROVES that parity (or disproves it, if the code has drifted) rather than
 * relying on the static read. It is a pinning test for coverage — see the commit message for
 * whether it passed on the first run (no behavior change) or required a fix.
 */
public final class RedstoneGateOnTerrainSlabsPlacementTest {
    private static final Identifier TEST_TERRAIN_SLAB_ID = Identifier.of("terrain_slabs", "redstone_gate_test_slab");
    private static final Block TEST_TERRAIN_SLAB = new SlabBlock(AbstractBlock.Settings.copy(Blocks.STONE_SLAB));

    public static final class TerrainSlabsRedstoneGateTestEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!Registries.BLOCK.containsId(TEST_TERRAIN_SLAB_ID)) {
                Registry.register(Registries.BLOCK, TEST_TERRAIN_SLAB_ID, TEST_TERRAIN_SLAB);
            }
        }
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void repeaterAndComparatorCanPlaceOnTerrainSlabsBottomSlab(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos tsSlab = new BlockPos(2, 1, 2);
        BlockPos tsSlabAbs = ctx.getAbsolutePos(tsSlab);
        BlockPos above = ctx.getAbsolutePos(tsSlab.up());

        ctx.setBlockState(tsSlab, TEST_TERRAIN_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        BlockState tsSlabState = world.getBlockState(tsSlabAbs);

        // Setup sanity: confirm this shim is actually recognised as a TS-owned bottom-like
        // surface (and NOT generic Slabbed slab support), matching the standard checklist's
        // terrainSlabsBottomLikeSurfaceExposesPlacementTopFace row.
        ctx.assertTrue(SlabSupport.isDirectCustomBottomLikeSurface(tsSlabState),
                "test terrain slab must classify as a direct bottom-like compat surface: " + tsSlabState);
        ctx.assertFalse(SlabSupport.isSupportingSlab(tsSlabState),
                "test terrain slab must stay out of generic Slabbed support: " + tsSlabState);

        ctx.assertTrue(
                tsSlabState.isSideSolid(world, tsSlabAbs, Direction.UP, SideShapeType.RIGID),
                "a Terrain-Slabs-owned bottom slab must report RIGID UP-solid via isSideSolid, or "
                        + "redstone gates (repeater/comparator) cannot be placed on it "
                        + "(live-reported 'can't place redstone repeater/comparator on TS')");

        ctx.assertTrue(Blocks.REPEATER.getDefaultState().canPlaceAt(world, above),
                "a repeater must be placeable directly on top of a Terrain-Slabs-owned bottom slab");
        ctx.assertTrue(Blocks.COMPARATOR.getDefaultState().canPlaceAt(world, above),
                "a comparator must be placeable directly on top of a Terrain-Slabs-owned bottom slab");
        ctx.complete();
    }

    // REGRESSION GUARD: a plain vanilla bottom slab must keep working exactly as before.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void repeaterAndComparatorCanPlaceOnVanillaBottomSlab(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos slabAbs = ctx.getAbsolutePos(slab);
        BlockPos above = ctx.getAbsolutePos(slab.up());

        ctx.setBlockState(slab, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        BlockState slabState = world.getBlockState(slabAbs);

        ctx.assertTrue(slabState.isSideSolid(world, slabAbs, Direction.UP, SideShapeType.RIGID),
                "setup: vanilla bottom slab must report RIGID UP-solid");

        ctx.assertTrue(Blocks.REPEATER.getDefaultState().canPlaceAt(world, above),
                "a repeater must be placeable directly on top of a plain vanilla bottom slab");
        ctx.assertTrue(Blocks.COMPARATOR.getDefaultState().canPlaceAt(world, above),
                "a comparator must be placeable directly on top of a plain vanilla bottom slab");
        ctx.complete();
    }

    // REGRESSION GUARD: a TOP-type TS slab must NOT spuriously classify as a bottom/bottom-like
    // surface via the Slabbed/TS compat predicates — guards against over-widening the gate
    // (matches canTreatAsSolidTopFace's own scope, and mirrors the 1.21.11 sibling's own-scope
    // regression guard). NOTE: this deliberately does NOT assert isSideSolid(UP, RIGID) is false —
    // a TOP slab's UP face genuinely IS a rigid/full square by vanilla's own cull-shape geometry
    // (its top surface coincides with the block's top boundary), completely independent of the
    // Slabbed/TS bottom-slab mixin gate. That is expected vanilla behavior (you can place a
    // repeater flush on top of any top slab, same as on a full block), not something this gate
    // controls or should suppress.
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void terrainSlabsTopSlabDoesNotClaimBottomSolidity(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockPos posAbs = ctx.getAbsolutePos(pos);

        ctx.setBlockState(pos, TEST_TERRAIN_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
        BlockState topState = world.getBlockState(posAbs);

        ctx.assertTrue(!SlabSupport.isBottomSlab(topState),
                "setup: a TOP-type slab must not be classified as a bottom slab");
        ctx.assertTrue(!SlabSupport.isDirectCustomBottomLikeSurface(topState),
                "a TOP-type Terrain Slabs slab must NOT classify as a bottom-like surface — "
                        + "over-widening guard");
        ctx.assertTrue(!SlabSupport.canTreatAsSolidTopFace(world, posAbs),
                "a TOP-type Terrain Slabs slab must NOT be treated as a Slabbed solid-top-face "
                        + "support source — over-widening guard");
        ctx.complete();
    }
}
