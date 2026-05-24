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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client-side proof that runtime Terrain Slabs slab blocks use Slabbed's same
 * lowered visual-triad authority as vanilla-supported blocks.
 */
public final class TerrainSlabsVisualTriadClientGameTest implements FabricClientGameTest {
    private static final String MOD_ID = "terrainslabs";
    private static final BlockPos ORIGIN = new BlockPos(24, 200, 0);
    private static final int GRID_SPACING = 4;
    private static final int GRID_WIDTH = 8;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            System.out.println("TERRAIN_SLABS_VISUAL_TRIAD terrainslabs_not_loaded");
            return;
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            List<Identifier> slabIds = terrainSlabIds();
            List<Identifier> deniedIds = nonSlabTerrainIds();
            if (slabIds.isEmpty()) {
                throw new AssertionError("no Terrain Slabs SlabBlock ids discovered at runtime");
            }

            singleplayer.getServer().runOnServer(server -> {
                var world = server.getOverworld();
                for (int i = 0; i < slabIds.size(); i++) {
                    BlockPos supportPos = supportPosForIndex(i);
                    BlockPos fullPos = supportPos.up();
                    BlockPos slabPos = fullPos.east();
                    world.setBlockState(slabPos.down(),
                            requiresGravitySupport(slabIds.get(i)) ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(),
                            Block.NOTIFY_LISTENERS);
                    world.setBlockState(supportPos,
                            Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                            Block.NOTIFY_LISTENERS);
                    world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                    world.setBlockState(slabPos, terrainSlabState(slabIds.get(i)), Block.NOTIFY_LISTENERS);
                    world.setBlockState(slabPos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            });

            ctx.waitTick();
            ctx.waitTick();
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new AssertionError("client world missing for Terrain Slabs visual triad proof");
                }
                if (mc.player == null) {
                    throw new AssertionError("client player missing for Terrain Slabs visual triad proof");
                }
                ShapeContext shapeContext = ShapeContext.of(mc.player);

                for (int i = 0; i < slabIds.size(); i++) {
                    Identifier id = slabIds.get(i);
                    BlockPos slabPos = supportPosForIndex(i).up().east();
                    BlockState state = mc.world.getBlockState(slabPos);
                    assertTerrainSlabState(id, state);

                    boolean terrainSkip = TerrainSlabsCompat.shouldSkipOffset(state);
                    boolean compatSkip = CompatHooks.shouldSkipOffset(state);
                    double clientDy = ClientDy.dyFor(mc.world, slabPos, state);
                    double supportDy = SlabSupport.getYOffset(mc.world, slabPos, state);
                    VoxelShape outline = state.getOutlineShape(mc.world, slabPos, shapeContext);
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

                    if (shouldProveActualSideRaycast(id)) {
                        Vec3d start = new Vec3d(slabPos.getX() + 1.25d, slabPos.getY() - 0.25d, slabPos.getZ() + 0.5d);
                        Vec3d end = new Vec3d(slabPos.getX() + 0.25d, slabPos.getY() - 0.25d, slabPos.getZ() + 0.5d);
                        BlockHitResult hit = outline.raycast(start, end, slabPos);
                        if (hit == null || !hit.getBlockPos().equals(slabPos)) {
                            throw new AssertionError("side raycast at lowered height for " + id
                                    + " hit " + (hit == null ? "miss" : hit.getBlockPos()) + " instead of " + slabPos);
                        }
                        System.out.println("TERRAIN_SLABS_VISUAL_TRIAD_RAYCAST id=" + id
                                + " source=outlineShape"
                                + " hitPos=" + hit.getBlockPos()
                                + " hitY=" + hit.getPos().y);
                    }
                }

                for (Identifier id : deniedIds) {
                    BlockState deniedState = Registries.BLOCK.get(id).getDefaultState();
                    boolean terrainSkip = TerrainSlabsCompat.shouldSkipOffset(deniedState);
                    boolean compatSkip = CompatHooks.shouldSkipOffset(deniedState);
                    assertTrue(id + " should remain skipped", terrainSkip && compatSkip);
                    System.out.println("TERRAIN_SLABS_VISUAL_TRIAD denied_id=" + id
                            + " terrainSkip=" + terrainSkip
                            + " compatSkip=" + compatSkip);
                }

                System.out.println("TERRAIN_SLABS_VISUAL_TRIAD totals slabIds=" + slabIds.size()
                        + " deniedIds=" + deniedIds.size());
            });
        }
    }

    private static List<Identifier> terrainSlabIds() {
        List<Identifier> ids = new ArrayList<>();
        for (Identifier id : Registries.BLOCK.getIds()) {
            if (!MOD_ID.equals(id.getNamespace())) {
                continue;
            }

            Block block = Registries.BLOCK.get(id);
            BlockState state = block.getDefaultState();
            if (block instanceof SlabBlock && state.contains(SlabBlock.TYPE)) {
                ids.add(id);
            }
        }
        ids.sort(Comparator.comparing(Identifier::toString));
        return ids;
    }

    private static boolean requiresGravitySupport(Identifier id) {
        return Registries.BLOCK.get(id).getClass().getSimpleName().contains("GravityAffected");
    }

    private static boolean shouldProveActualSideRaycast(Identifier id) {
        String path = id.getPath();
        return "grass_slab".equals(path) || "terrain_stone_slab".equals(path);
    }

    private static List<Identifier> nonSlabTerrainIds() {
        List<Identifier> ids = new ArrayList<>();
        for (Identifier id : Registries.BLOCK.getIds()) {
            if (!MOD_ID.equals(id.getNamespace())) {
                continue;
            }

            Block block = Registries.BLOCK.get(id);
            BlockState state = block.getDefaultState();
            if (!(block instanceof SlabBlock && state.contains(SlabBlock.TYPE))) {
                ids.add(id);
            }
        }
        ids.sort(Comparator.comparing(Identifier::toString));
        return ids;
    }

    private static BlockPos supportPosForIndex(int index) {
        int x = (index % GRID_WIDTH) * GRID_SPACING;
        int z = (index / GRID_WIDTH) * GRID_SPACING;
        return ORIGIN.add(x, 0, z);
    }

    private static BlockState terrainSlabState(Identifier id) {
        Block block = Registries.BLOCK.get(id);
        BlockState state = block.getDefaultState();
        if (!state.contains(SlabBlock.TYPE)) {
            throw new AssertionError(id + " is not a slab state: " + state);
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
