package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Pinning coverage ported from the 1.21.11 sibling branch's {@code
 * RedstoneGateOnTerrainSlabsPlacementTest} (live bug report, 2026-07-04: "can't place redstone
 * repeater/comparator on Terrain Slabs" — failure mode L9 in the cross-port matrix).
 *
 * <p>On the 1.21.11 sibling, the {@code isFaceSturdy} injection gating redstone-gate placement on a
 * bottom slab used a narrower predicate than its {@code isFaceSturdy(4-arg)} sibling, so a
 * Terrain-Slabs-owned bottom slab (a {@code SlabBlock} subclass registered under the
 * {@code terrain_slabs}/{@code terrainslabs} namespace) failed to report UP-solid for
 * {@code SupportType.RIGID}, and {@code DiodeBlock.canSurviveOn} (which every repeater/comparator
 * routes through) rejected placement.
 *
 * <p>On THIS branch (26.2), {@code SlabSupportStateMixin.slabbed$slabTopSolid} (the 4-arg
 * {@code isFaceSturdy(BlockGetter,BlockPos,Direction,SupportType)} injection) gates purely on
 * {@code direction == UP && SlabSupport.isBottomSlab(self)} — and {@code SlabSupport.isBottomSlab}
 * is {@code isSupportingSlab(state) && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM}, where
 * {@code isSupportingSlab} is {@code state.getBlock() instanceof SlabBlock && state.hasProperty(TYPE)}
 * — a generic shape/type check with NO namespace or mod-id exclusion. Any {@code SlabBlock}-derived
 * custom slab (including one registered under the {@code terrain_slabs} namespace, i.e. Terrain
 * Slabs' own slab class) qualifies identically to a vanilla slab. This test EMPIRICALLY PROVES that
 * (rather than relying on the static read) — a pinning test for coverage, not a fix, since static
 * analysis found no narrowing gate on this branch to begin with.
 */
public final class RedstoneGateOnTerrainSlabsPlacementTest {
    private static final Identifier TEST_TERRAIN_SLAB_ID =
            Identifier.fromNamespaceAndPath("terrain_slabs", "redstone_gate_test_slab");
    private static final ResourceKey<Block> TEST_TERRAIN_SLAB_KEY =
            ResourceKey.create(Registries.BLOCK, TEST_TERRAIN_SLAB_ID);
    private static final Block TEST_TERRAIN_SLAB =
            new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(TEST_TERRAIN_SLAB_KEY));

    /** Registers the TS-namespaced stand-in slab block used to prove the gate is namespace-agnostic. */
    public static final class TerrainSlabsRedstoneGateTestEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!BuiltInRegistries.BLOCK.containsKey(TEST_TERRAIN_SLAB_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, TEST_TERRAIN_SLAB_ID, TEST_TERRAIN_SLAB);
            }
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void repeaterAndComparatorCanPlaceOnTerrainSlabsBottomSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tsSlab = new BlockPos(2, 1, 2);
        BlockPos tsSlabAbs = helper.absolutePos(tsSlab);
        BlockPos above = helper.absolutePos(tsSlab.above());

        helper.setBlock(tsSlab, TEST_TERRAIN_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        BlockState tsSlabState = level.getBlockState(tsSlabAbs);

        // Setup sanity: this test double really is registered under the terrain_slabs namespace and
        // really does classify as a generic Slabbed-supporting bottom slab (the gate under test).
        if (!"terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(TEST_TERRAIN_SLAB).getNamespace())) {
            throw helper.assertionException(tsSlab, "test slab must be registered under the terrain_slabs namespace");
        }
        if (!SlabSupport.isBottomSlab(tsSlabState)) {
            throw helper.assertionException(tsSlab, "test terrain slab must classify as a bottom slab: " + tsSlabState);
        }

        if (!tsSlabState.isFaceSturdy(level, tsSlabAbs, Direction.UP, SupportType.RIGID)) {
            throw helper.assertionException(tsSlab,
                    "a Terrain-Slabs-namespaced bottom slab must report RIGID UP-solid via isFaceSturdy, or "
                            + "redstone gates (repeater/comparator) cannot be placed on it "
                            + "(live-reported 'can't place redstone repeater/comparator on TS')");
        }

        BlockState repeater = Blocks.REPEATER.defaultBlockState();
        BlockState comparator = Blocks.COMPARATOR.defaultBlockState();
        if (!repeater.canSurvive(level, above)) {
            throw helper.assertionException(above, "a repeater must be placeable directly on top of a Terrain-Slabs-owned bottom slab");
        }
        if (!comparator.canSurvive(level, above)) {
            throw helper.assertionException(above, "a comparator must be placeable directly on top of a Terrain-Slabs-owned bottom slab");
        }
        helper.succeed();
    }

    // REGRESSION GUARD: a plain vanilla bottom slab must keep working exactly as before.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void repeaterAndComparatorCanPlaceOnVanillaBottomSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos slabAbs = helper.absolutePos(slab);
        BlockPos above = helper.absolutePos(slab.above());

        helper.setBlock(slab, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        BlockState slabState = level.getBlockState(slabAbs);

        if (!slabState.isFaceSturdy(level, slabAbs, Direction.UP, SupportType.RIGID)) {
            throw helper.assertionException(slab, "setup: vanilla bottom slab must report RIGID UP-solid");
        }

        if (!Blocks.REPEATER.defaultBlockState().canSurvive(level, above)) {
            throw helper.assertionException(above, "a repeater must be placeable directly on top of a plain vanilla bottom slab");
        }
        if (!Blocks.COMPARATOR.defaultBlockState().canSurvive(level, above)) {
            throw helper.assertionException(above, "a comparator must be placeable directly on top of a plain vanilla bottom slab");
        }
        helper.succeed();
    }

    // REGRESSION GUARD: a TOP-type TS-namespaced slab must not spuriously classify as a bottom slab
    // via the Slabbed gate — guards against over-widening. NOTE: this deliberately does NOT assert
    // isFaceSturdy(UP, RIGID) is false — a TOP slab's UP face genuinely IS a rigid/full square by
    // vanilla's own cull-shape geometry (its top surface coincides with the block's top boundary),
    // completely independent of the Slabbed bottom-slab mixin gate. That is expected vanilla
    // behavior (you can place a repeater flush on top of any top slab), not something this gate
    // controls or should suppress.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsTopSlabDoesNotClaimBottomSolidity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockPos posAbs = helper.absolutePos(pos);

        helper.setBlock(pos, TEST_TERRAIN_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        BlockState topState = level.getBlockState(posAbs);

        if (SlabSupport.isBottomSlab(topState)) {
            throw helper.assertionException(pos, "setup: a TOP-type slab must not be classified as a bottom slab");
        }
        helper.succeed();
    }
}
