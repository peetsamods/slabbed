package com.slabbed.test;

import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

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
}
