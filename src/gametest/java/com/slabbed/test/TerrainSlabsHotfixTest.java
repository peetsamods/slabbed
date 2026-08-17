package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * 0.3.0-beta.2 hotfix verification: the Terrain Slabs compat must activate against the MODERN
 * {@code terrain_slabs} mod id (not only the legacy {@code terrainslabs}). The
 * {@link TerrainSlabsTestShim} registers {@code terrain_slabs:test_slab} and the gametest mod
 * provides {@code terrain_slabs}, so these exercise the real detection + classification + lowering
 * chain for a modern-namespace surface. Before the fix these would all read NONE / dy 0.
 */
public final class TerrainSlabsHotfixTest {

    private static final double EPS = 1.0e-6;

    private static BlockState tsBottomSlab() {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    // Detection + classification: a terrain_slabs:* bottom slab classifies BOTTOM_LIKE.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void modernTerrainSlabsClassifiesBottomLike(TestContext ctx) {
        CompatSlabSurfaceKind kind = CompatHooks.customSlabSurfaceKind(tsBottomSlab());
        ctx.assertTrue(kind == CompatSlabSurfaceKind.BOTTOM_LIKE,
                "terrain_slabs:test_slab[type=bottom] must classify BOTTOM_LIKE (mod-id fix), got " + kind);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsObjectOffsetCapabilityUsesUpstreamAuthority(TestContext ctx) {
        ctx.assertTrue(CompatHooks.terrainSlabsHandlesObjectOffset(Blocks.DANDELION.getDefaultState()),
                "PlantBlock family must use Terrain Slabs' own on-top offset authority");
        ctx.assertTrue(CompatHooks.terrainSlabsHandlesObjectOffset(Blocks.SNOW.getDefaultState()),
                "registry-owned snow must use Terrain Slabs' own on-top offset authority");
        ctx.assertTrue(!CompatHooks.terrainSlabsHandlesObjectOffset(Blocks.TORCH.getDefaultState()),
                "torch is not Terrain-owned and must use Slabbed's floor-seat transaction");
        ctx.assertTrue(!CompatHooks.terrainSlabsHandlesObjectOffset(Blocks.STONE.getDefaultState()),
                "ordinary full blocks are player-authored transaction subjects, not OnTop objects");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainCapabilityDoesNotStealPlantPlacementOnOrdinarySupport(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 2, 2);
        BlockPos plant = ground.up();
        w.setBlockState(ground, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        PlayerEntity player = PlacementHarness.mockPlayerHolding(
                ctx, ground.north(3), new ItemStack(Blocks.DANDELION.asItem(), 1));
        Vec3d hit = new Vec3d(ground.getX() + 0.5d, ground.getY() + 1.0d, ground.getZ() + 0.5d);

        ActionResult result = PlacementHarness.useHeldItem(w, player, ground, Direction.UP, hit);

        ctx.assertTrue(result.isAccepted(), "dandelion placement on ordinary dirt must succeed");
        ctx.assertTrue(w.getBlockState(plant).isOf(Blocks.DANDELION), "the dandelion must be placed");
        double stored = SlabPlacementDyAttachment.storedDy(w, plant);
        ctx.assertTrue(Double.doubleToRawLongBits(stored) == Double.doubleToRawLongBits(0.0d),
                "Terrain capability must not steal a non-Terrain placement transaction; got " + stored);
        ctx.complete();
    }

    // Terrain stays flush: a generic opaque full terrain cube (stone/dirt/grass) on a modern
    // terrain_slabs BOTTOM_LIKE surface must NOT be lowered. Lowering an opaque full cube -0.5
    // onto a Terrain Slabs surface tears see-through world holes (the chunk mesher culls at the
    // un-shifted voxel; the only cull-redraw hook is renderer-specific/Indigo-only). This is the
    // "DODO" world-hole bug — its root cause was CompatHooks ignoring the legacy `terrainslabs`
    // mod-id, so TS slabs were treated as vanilla bottom slabs and the column walk lowered terrain.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainCubeStaysFlushOnModernTerrainSlab(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(base, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        BlockPos objPos = base.up();
        w.setBlockState(objPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dy = SlabSupport.getYOffset(w, objPos, w.getBlockState(objPos));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "stone (generic terrain) on a modern terrain_slabs slab must stay FLUSH (0.0) — "
                        + "lowering opaque terrain cubes tears world holes; got " + dy);
        ctx.complete();
    }

    // GH #22 (temporary RED-first probe, ported from fix/1211-slab-log-dodo-log @ 5c5f3ba4):
    // if an oak log is lowered onto a Terrain Slabs bottom surface while another oak log is
    // already above it, the vertical log stack must share one visual dy, or the lower log
    // drops to -0.5 while the upper log stays at 0, leaving a visible slab-log DODO gap.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void verticalOakLogStackOnTsSurfaceSharesLoweredDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos lowerLog = slab.up();
        BlockPos upperLog = slab.up(2);

        w.setBlockState(slab, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(upperLog, net.minecraft.block.Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(lowerLog, net.minecraft.block.Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);

        double lowerDy = SlabSupport.getYOffset(w, lowerLog, w.getBlockState(lowerLog));
        double upperDy = SlabSupport.getYOffset(w, upperLog, w.getBlockState(upperLog));
        ctx.assertTrue(Math.abs(lowerDy + 0.5) <= EPS,
                "setup: lower oak log on a modern terrain_slabs slab should lower to -0.5, got " + lowerDy);
        ctx.assertTrue(Math.abs(upperDy - lowerDy) <= EPS,
                "slab-log-dodo-log: upper oak log must share lower log dy " + lowerDy
                        + " to keep the vertical stack connected; got " + upperDy);
        ctx.complete();
    }

    // GH #22: an ordinary full block on a lowered full-height carrier (log) must share that
    // carrier's visual dy. Covers the live command-created TS slab -> oak log -> grass block
    // fixture from the issue's repro; command-authored blocks do not get placement anchors.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void grassBlockOnTsLoweredOakLogSharesLoweredDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        BlockPos lowerLog = slab.up();
        BlockPos grass = slab.up(2);

        w.setBlockState(slab, tsBottomSlab(), Block.NOTIFY_LISTENERS);
        w.setBlockState(lowerLog, net.minecraft.block.Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(grass, net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);

        double lowerDy = SlabSupport.getYOffset(w, lowerLog, w.getBlockState(lowerLog));
        double grassDy = SlabSupport.getYOffset(w, grass, w.getBlockState(grass));
        ctx.assertTrue(Math.abs(lowerDy + 0.5) <= EPS,
                "setup: lower oak log on a modern terrain_slabs slab should lower to -0.5, got " + lowerDy);
        ctx.assertTrue(Math.abs(grassDy - lowerDy) <= EPS,
                "grass-on-lowered-log: grass block must share lower log dy " + lowerDy
                        + " to keep the stack connected; got " + grassDy);
        ctx.complete();
    }
}
