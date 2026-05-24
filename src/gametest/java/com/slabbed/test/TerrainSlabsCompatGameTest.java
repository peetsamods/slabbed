package com.slabbed.test;

import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

public final class TerrainSlabsCompatGameTest {
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void customTerrainSlabsUseSlabbedSlabPath(TestContext ctx) {
        if (!FabricLoader.getInstance().isModLoaded(TerrainSlabsCompat.MOD_ID)) {
            ctx.complete();
            return;
        }

        assertTerrainSlabAllowed(ctx, "grass_slab");
        assertTerrainSlabAllowed(ctx, "sand_slab");
        assertTerrainSlabAllowed(ctx, "terrain_stone_slab");
        assertOnTopBlockStillSkipped(ctx, "short_grass_on_top");

        ServerWorld world = ctx.getWorld();
        BlockPos fullPos = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos supportPos = fullPos.down();
        BlockPos customSlabPos = fullPos.east();

        BlockState grassSlab = block(ctx, "grass_slab").getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM);
        world.setBlockState(supportPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);
        world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(customSlabPos, grassSlab, Block.NOTIFY_ALL);

        double dy = SlabSupport.getYOffset(world, customSlabPos, grassSlab);
        ctx.assertTrue(dy == -0.5,
                "Terrain Slabs grass_slab should inherit lowered side-slab dy; dy=" + dy);

        VoxelShape outline = grassSlab.getOutlineShape(world, customSlabPos, ShapeContext.absent());
        ctx.assertTrue(outline.getBoundingBox().minY == -0.5,
                "Terrain Slabs grass_slab outline should lower to minY=-0.5, got "
                        + outline.getBoundingBox().minY);

        VoxelShape raycast = grassSlab.getRaycastShape(world, customSlabPos);
        if (!raycast.isEmpty()) {
            ctx.assertTrue(raycast.getBoundingBox().minY == outline.getBoundingBox().minY,
                    "Terrain Slabs grass_slab raycast/outline parity broken: raycast minY="
                            + raycast.getBoundingBox().minY
                            + ", outline minY=" + outline.getBoundingBox().minY);
        }

        ctx.complete();
    }

    private static void assertTerrainSlabAllowed(TestContext ctx, String path) {
        BlockState state = block(ctx, path).getDefaultState();
        ctx.assertTrue(state.getBlock() instanceof SlabBlock,
                path + " must be a SlabBlock to use Slabbed slab semantics");
        ctx.assertTrue(state.contains(SlabBlock.TYPE),
                path + " must expose vanilla SlabBlock.TYPE");
        ctx.assertTrue(!TerrainSlabsCompat.shouldSkipOffset(state),
                path + " should not be blanket-skipped by TerrainSlabsCompat");
        ctx.assertTrue(!CompatHooks.shouldSkipOffset(state),
                path + " should not be blanket-skipped by CompatHooks");
        ctx.assertTrue(SlabSupport.isSupportingSlab(state),
                path + " should be recognized as a Slabbed supporting slab");
    }

    private static void assertOnTopBlockStillSkipped(TestContext ctx, String path) {
        BlockState state = block(ctx, path).getDefaultState();
        ctx.assertTrue(!(state.getBlock() instanceof SlabBlock),
                path + " should remain outside slab allow semantics");
        ctx.assertTrue(TerrainSlabsCompat.shouldSkipOffset(state),
                path + " should remain skipped by TerrainSlabsCompat");
        ctx.assertTrue(CompatHooks.shouldSkipOffset(state),
                path + " should remain skipped by CompatHooks");
    }

    private static Block block(TestContext ctx, String path) {
        Identifier id = Identifier.of(TerrainSlabsCompat.MOD_ID, path);
        for (Identifier candidate : Registries.BLOCK.getIds()) {
            if (candidate.equals(id)) {
                return Registries.BLOCK.get(id);
            }
        }
        ctx.assertTrue(false, "Missing Terrain Slabs block " + id);
        return Blocks.AIR;
    }
}
