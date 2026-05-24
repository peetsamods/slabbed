package com.slabbed.test;

import com.slabbed.client.ClientDy;
import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

/**
 * Client-side proof that runtime Terrain Slabs slab blocks use Slabbed's same
 * lowered visual-triad authority as vanilla-supported blocks.
 */
public final class TerrainSlabsVisualTriadClientGameTest implements FabricClientGameTest {
    private static final String MOD_ID = "terrainslabs";
    private static final BlockPos ORIGIN = new BlockPos(24, 200, 0);
    private static final String[] ALLOWED_SLAB_IDS = {
            "grass_slab",
            "sand_slab",
            "terrain_stone_slab"
    };

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            System.out.println("TERRAIN_SLABS_VISUAL_TRIAD terrainslabs_not_loaded");
            return;
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getServer().runOnServer(server -> {
                var world = server.getOverworld();
                for (int i = 0; i < ALLOWED_SLAB_IDS.length; i++) {
                    BlockPos supportPos = ORIGIN.add(i * 4, 0, 0);
                    BlockPos fullPos = supportPos.up();
                    BlockPos slabPos = fullPos.east();
                    world.setBlockState(slabPos.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                    world.setBlockState(supportPos,
                            Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                            Block.NOTIFY_LISTENERS);
                    world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                    world.setBlockState(slabPos, terrainSlabState(ALLOWED_SLAB_IDS[i]), Block.NOTIFY_LISTENERS);
                    world.setBlockState(slabPos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            });

            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new AssertionError("client world missing for Terrain Slabs visual triad proof");
                }

                for (int i = 0; i < ALLOWED_SLAB_IDS.length; i++) {
                    String path = ALLOWED_SLAB_IDS[i];
                    Identifier id = Identifier.of(MOD_ID, path);
                    BlockPos slabPos = ORIGIN.add(i * 4, 1, 0).east();
                    BlockState state = mc.world.getBlockState(slabPos);
                    assertTerrainSlabState(id, state);

                    boolean terrainSkip = TerrainSlabsCompat.shouldSkipOffset(state);
                    boolean compatSkip = CompatHooks.shouldSkipOffset(state);
                    double clientDy = ClientDy.dyFor(mc.world, slabPos, state);
                    double supportDy = SlabSupport.getYOffset(mc.world, slabPos, state);
                    VoxelShape outline = state.getOutlineShape(mc.world, slabPos, ShapeContext.absent());
                    VoxelShape raycast = state.getRaycastShape(mc.world, slabPos);

                    assertFalse("TerrainSlabsCompat should allow " + id, terrainSkip);
                    assertFalse("CompatHooks should allow " + id, compatSkip);
                    assertClose("ClientDy for " + id, -0.5d, clientDy);
                    assertClose("SlabSupport dy for " + id, -0.5d, supportDy);
                    assertShapeMinY("outline", id, outline, -0.5d);
                    if (!raycast.isEmpty()) {
                        assertShapeMinY("raycast", id, raycast, outline.getBoundingBox().minY);
                    }

                    System.out.println("TERRAIN_SLABS_VISUAL_TRIAD id=" + id
                            + " terrainSkip=" + terrainSkip
                            + " compatSkip=" + compatSkip
                            + " clientDy=" + clientDy
                            + " slabSupportDy=" + supportDy
                            + " outlineMinY=" + outline.getBoundingBox().minY
                            + " raycastMinY=" + (raycast.isEmpty() ? "empty" : raycast.getBoundingBox().minY));
                }

                BlockState onTop = Registries.BLOCK.get(Identifier.of(MOD_ID, "short_grass_on_top")).getDefaultState();
                assertTrue("short_grass_on_top should remain skipped",
                        TerrainSlabsCompat.shouldSkipOffset(onTop) && CompatHooks.shouldSkipOffset(onTop));
                System.out.println("TERRAIN_SLABS_VISUAL_TRIAD denied_id=terrainslabs:short_grass_on_top"
                        + " terrainSkip=" + TerrainSlabsCompat.shouldSkipOffset(onTop)
                        + " compatSkip=" + CompatHooks.shouldSkipOffset(onTop));
            });
        }
    }

    private static BlockState terrainSlabState(String path) {
        Block block = Registries.BLOCK.get(Identifier.of(MOD_ID, path));
        BlockState state = block.getDefaultState();
        if (!state.contains(SlabBlock.TYPE)) {
            throw new AssertionError(MOD_ID + ":" + path + " is not a slab state: " + state);
        }
        return state.with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static void assertTerrainSlabState(Identifier id, BlockState state) {
        if (!Registries.BLOCK.getId(state.getBlock()).equals(id)) {
            throw new AssertionError("expected " + id + " but found " + state);
        }
        if (!(state.getBlock() instanceof SlabBlock) || !state.contains(SlabBlock.TYPE)) {
            throw new AssertionError(id + " is not a SlabBlock state: " + state);
        }
    }

    private static void assertShapeMinY(String shapeName, Identifier id, VoxelShape shape, double expected) {
        if (shape.isEmpty()) {
            throw new AssertionError(shapeName + " shape empty for " + id);
        }
        assertClose(shapeName + " minY for " + id, expected, shape.getBoundingBox().minY);
    }

    private static void assertTrue(String message, boolean actual) {
        if (!actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(String message, boolean actual) {
        if (actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertClose(String label, double expected, double actual) {
        if (Math.abs(expected - actual) > 1.0e-6d) {
            throw new AssertionError(label + " expected " + expected + " but was " + actual);
        }
    }
}
