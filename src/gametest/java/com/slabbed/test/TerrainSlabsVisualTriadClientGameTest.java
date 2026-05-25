package com.slabbed.test;

import com.slabbed.client.ClientDy;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
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

                    if (shouldProveLiveCrosshairTarget(id)) {
                        Vec3d eye = new Vec3d(slabPos.getX() + 1.25d, slabPos.getY() - 0.25d, slabPos.getZ() + 0.5d);
                        Vec3d target = new Vec3d(slabPos.getX() + 0.25d, slabPos.getY() - 0.25d, slabPos.getZ() + 0.5d);
                        aimPlayerRaycastFromEye(mc, eye, target);
                        mc.player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
                        mc.gameRenderer.updateCrosshairTarget(0.0f);
                        HitResult crosshair = mc.crosshairTarget;
                        if (!(crosshair instanceof BlockHitResult hit) || !hit.getBlockPos().equals(slabPos)) {
                            throw new AssertionError("live crosshair target at lowered height for " + id
                                    + " hit " + describeHit(crosshair) + " instead of " + slabPos);
                        }
                        System.out.println("TERRAIN_SLABS_VISUAL_TRIAD_CROSSHAIR id=" + id
                                + " source=GameRendererCrosshairRetargetMixin"
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

            boolean missingFaceOnly = Boolean.getBoolean("slabbed.terrainSlabsMissingFaceRedOnly");
            if (!missingFaceOnly) {
                proveGrassSlabSupportedObjectModelDy(ctx, singleplayer, slabIds);
            }
            proveGrassSlabMissingFaceRed(ctx, slabIds);
        }
    }

    private static void proveGrassSlabMissingFaceRed(ClientGameTestContext ctx, List<Identifier> slabIds) {
        Identifier supportId = Identifier.of(MOD_ID, "grass_slab");
        int supportIndex = slabIds.indexOf(supportId);
        if (supportIndex < 0) {
            throw new AssertionError("terrainslabs:grass_slab missing from Terrain Slabs slab ids");
        }

        BlockPos slabPos = supportPosForIndex(supportIndex).up().east();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new AssertionError("client world missing for Terrain Slabs missing-face red proof");
            }

            BlockState state = mc.world.getBlockState(slabPos);
            assertTerrainSlabState(supportId, state);
            double dy = SlabSupport.getYOffset(mc.world, slabPos, state);
            assertClose("SlabSupport dy for missing-face red proof", -0.5d, dy);

            BlockStateModel model = mc.getBlockRenderManager().getModel(state);
            if (!(model instanceof OffsetBlockStateModel)) {
                throw new AssertionError("expected OffsetBlockStateModel wrapper for " + supportId
                        + " but found " + model.getClass().getName());
            }
            if (!(model instanceof FabricBlockStateModel fabricModel)) {
                throw new AssertionError("expected FabricBlockStateModel for " + supportId
                        + " but found " + model.getClass().getName());
            }

            QuadProbe noCull = collectGrassSlabQuads(fabricModel, mc.world, slabPos, state, direction -> false);
            QuadProbe westCulled = collectGrassSlabQuads(fabricModel, mc.world, slabPos, state, direction -> direction == Direction.WEST);

            String fields = " supportId=" + supportId
                    + " pos=" + slabPos.toShortString()
                    + " dy=" + dy
                    + " noCullTotal=" + noCull.totalQuads
                    + " westCullTotal=" + westCulled.totalQuads
                    + " noCullWestShifted=" + noCull.westShiftedQuads
                    + " westCullWestShifted=" + westCulled.westShiftedQuads
                    + " noCullWestMinY=" + noCull.westMinY
                    + " noCullWestMaxY=" + noCull.westMaxY
                    + " westCullWestMinY=" + westCulled.westMinY
                    + " westCullWestMaxY=" + westCulled.westMaxY;

            if (noCull.westShiftedQuads <= 0) {
                System.out.println("TERRAIN_SLABS_MISSING_FACE_RED reason=no_unculled_shifted_west_face" + fields);
                throw new AssertionError("Terrain Slabs missing-face red proof could not observe unculled shifted west face" + fields);
            }
            if (westCulled.westShiftedQuads < noCull.westShiftedQuads) {
                System.out.println("TERRAIN_SLABS_MISSING_FACE_RED reason=pre_offset_cull_dropped_exposed_shifted_face" + fields);
                throw new AssertionError("Terrain Slabs grass_slab exposed shifted west face was culled before dy shift" + fields);
            }

            System.out.println("TERRAIN_SLABS_MISSING_FACE_GREEN" + fields);
        });
    }

    private static QuadProbe collectGrassSlabQuads(
            FabricBlockStateModel model,
            net.minecraft.world.BlockRenderView world,
            BlockPos pos,
            BlockState state,
            java.util.function.Predicate<Direction> cullTest
    ) {
        Renderer renderer = Renderer.get();
        if (renderer == null) {
            throw new AssertionError("Fabric renderer missing for Terrain Slabs missing-face red proof");
        }

        MutableMesh mesh = renderer.mutableMesh();
        model.emitQuads(mesh.emitter(), world, pos, state, Random.create(0x51abbEDL), cullTest);
        QuadProbe probe = new QuadProbe(mesh.size());
        mesh.forEach(quad -> probe.accept(quad));
        return probe;
    }

    private static final class QuadProbe {
        final int totalQuads;
        int westShiftedQuads;
        double westMinY = Double.NaN;
        double westMaxY = Double.NaN;

        QuadProbe(int totalQuads) {
            this.totalQuads = totalQuads;
        }

        void accept(QuadView quad) {
            Direction face = quad.cullFace() != null ? quad.cullFace() : quad.nominalFace();
            if (face != Direction.WEST) {
                return;
            }

            double minY = minY(quad);
            double maxY = maxY(quad);
            if (maxY > 1.0e-6d) {
                return;
            }

            westShiftedQuads++;
            westMinY = Double.isNaN(westMinY) ? minY : Math.min(westMinY, minY);
            westMaxY = Double.isNaN(westMaxY) ? maxY : Math.max(westMaxY, maxY);
        }

        private static double minY(QuadView quad) {
            return Math.min(Math.min(quad.y(0), quad.y(1)), Math.min(quad.y(2), quad.y(3)));
        }

        private static double maxY(QuadView quad) {
            return Math.max(Math.max(quad.y(0), quad.y(1)), Math.max(quad.y(2), quad.y(3)));
        }
    }

    private static void proveGrassSlabSupportedObjectModelDy(
            ClientGameTestContext ctx, TestSingleplayerContext singleplayer, List<Identifier> slabIds
    ) {
        Identifier supportId = Identifier.of(MOD_ID, "grass_slab");
        int supportIndex = slabIds.indexOf(supportId);
        if (supportIndex < 0) {
            throw new AssertionError("terrainslabs:grass_slab missing from Terrain Slabs slab ids");
        }

        BlockPos supportPos = supportPosForIndex(supportIndex).up().east();
        BlockPos objectPos = supportPos.up();
        proveSupportedObjectModelDy(
                ctx,
                singleplayer,
                supportId,
                supportPos,
                objectPos,
                "chain_control",
                Blocks.IRON_CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y),
                -1.0d,
                false);
        proveSupportedObjectModelDy(
                ctx,
                singleplayer,
                supportId,
                supportPos,
                objectPos,
                "oak_fence",
                Blocks.OAK_FENCE.getDefaultState(),
                -1.0d,
                true);
    }

    private static void proveSupportedObjectModelDy(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Identifier supportId,
            BlockPos supportPos,
            BlockPos objectPos,
            String lane,
            BlockState objectToPlace,
            double expectedDy,
            boolean requireLiveTarget
    ) {
        singleplayer.getServer().runOnServer(server -> server.getOverworld().setBlockState(
                objectPos,
                Blocks.AIR.getDefaultState(),
                Block.NOTIFY_LISTENERS));
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            System.setProperty("slabbed.render.offset.trace", "true");
            OffsetBlockStateModel.resetRenderOffsetTrace(objectPos);
        });
        singleplayer.getServer().runOnServer(server -> server.getOverworld().setBlockState(
                objectPos,
                objectToPlace,
                Block.NOTIFY_LISTENERS));
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new AssertionError("client world missing for Terrain Slabs object model dy proof");
            }
            if (mc.player == null) {
                throw new AssertionError("client player missing for Terrain Slabs object model dy proof");
            }

            OffsetBlockStateModel.RenderOffsetTrace modelTrace;
            try {
                modelTrace = OffsetBlockStateModel.snapshotRenderOffsetTrace();
            } finally {
                System.clearProperty("slabbed.render.offset.trace");
            }

            BlockState supportState = mc.world.getBlockState(supportPos);
            BlockState objectState = mc.world.getBlockState(objectPos);
            assertTerrainSlabState(supportId, supportState);
            if (!objectState.isOf(objectToPlace.getBlock())) {
                throw new AssertionError("expected " + Registries.BLOCK.getId(objectToPlace.getBlock())
                        + " object at " + objectPos + " but found " + objectState);
            }

            ShapeContext shapeContext = ShapeContext.of(mc.player);
            double supportDy = SlabSupport.getYOffset(mc.world, supportPos, supportState);
            double worldDy = SlabSupport.getYOffset(mc.world, objectPos, objectState);
            double clientDy = ClientDy.dyFor(mc.world, objectPos, objectState);
            VoxelShape outline = objectState.getOutlineShape(mc.world, objectPos, shapeContext);
            VoxelShape raycast = objectState.getRaycastShape(mc.world, objectPos);
            double outlineMinY = outline.isEmpty() ? Double.NaN : outline.getBoundingBox().minY;
            double raycastMinY = raycast.isEmpty() ? Double.NaN : raycast.getBoundingBox().minY;
            double targetY = objectPos.getY() + expectedDy + 0.5d;
            BlockHitResult outlineHit = outline.isEmpty() ? null : outline.raycast(
                    new Vec3d(objectPos.getX() + 0.5d, targetY, objectPos.getZ() + 2.5d),
                    new Vec3d(objectPos.getX() + 0.5d, targetY, objectPos.getZ() - 0.5d),
                    objectPos);
            Vec3d eye = new Vec3d(objectPos.getX() + 0.5d, targetY, objectPos.getZ() + 2.5d);
            Vec3d target = new Vec3d(objectPos.getX() + 0.5d, targetY, objectPos.getZ() + 0.5d);
            aimPlayerRaycastFromEye(mc, eye, target);
            mc.player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult liveTarget = mc.crosshairTarget;

            String fields = " lane=" + lane
                    + " supportId=" + supportId
                    + " supportPos=" + supportPos.toShortString()
                    + " objectId=" + Registries.BLOCK.getId(objectState.getBlock())
                    + " objectPos=" + objectPos.toShortString()
                    + " expectedDy=" + expectedDy
                    + " supportDy=" + supportDy
                    + " worldDy=" + worldDy
                    + " clientDy=" + clientDy
                    + " modelSeen=" + modelTrace.seen()
                    + " modelView=" + modelTrace.viewClass()
                    + " modelRenderRegion=" + modelTrace.viewClass().contains("ChunkRendererRegion")
                    + " modelDy=" + modelTrace.modelDy()
                    + " modelClientDy=" + modelTrace.clientDy()
                    + " modelSlabSupportDy=" + modelTrace.slabSupportDy()
                    + " modelExcluded=" + modelTrace.excludedByWrapper()
                    + " outlineMinY=" + outlineMinY
                    + " raycastMinY=" + (raycast.isEmpty() ? "empty" : Double.toString(raycastMinY))
                    + " requireLiveTarget=" + requireLiveTarget
                    + " outlineTargetHit=" + describeHit(outlineHit)
                    + " liveTargetHit=" + describeHit(liveTarget);

            String redReason = null;
            if (Math.abs(supportDy - (-0.5d)) > 1.0e-6d) {
                redReason = "support_slab_dy_mismatch";
            } else if (Math.abs(worldDy - expectedDy) > 1.0e-6d) {
                redReason = "world_dy_mismatch";
            } else if (Math.abs(clientDy - expectedDy) > 1.0e-6d) {
                redReason = "client_dy_mismatch";
            } else if (outline.isEmpty() || Math.abs(outlineMinY - expectedDy) > 1.0e-6d) {
                redReason = "outline_dy_mismatch";
            } else if (!raycast.isEmpty() && Math.abs(raycastMinY - expectedDy) > 1.0e-6d) {
                redReason = "raycast_dy_mismatch";
            } else if (outlineHit == null || !outlineHit.getBlockPos().equals(objectPos)) {
                redReason = "outline_target_mismatch";
            } else if (requireLiveTarget
                    && (!(liveTarget instanceof BlockHitResult liveHit) || !liveHit.getBlockPos().equals(objectPos))) {
                redReason = "live_target_mismatch";
            } else if (!modelTrace.seen()) {
                redReason = "model_trace_missing";
            } else if (!modelTrace.viewClass().contains("ChunkRendererRegion")) {
                redReason = "model_view_not_render_region";
            } else if (Math.abs(modelTrace.modelDy() - expectedDy) > 1.0e-6d) {
                redReason = "model_dy_mismatch";
            } else if (Math.abs(modelTrace.clientDy() - clientDy) > 1.0e-6d) {
                redReason = "model_client_world_disagree";
            } else if (Math.abs(modelTrace.slabSupportDy() - worldDy) > 1.0e-6d) {
                redReason = "model_world_slab_support_disagree";
            } else if (Math.abs(modelTrace.clientDy() - modelTrace.slabSupportDy()) > 1.0e-6d) {
                redReason = "model_client_slab_support_disagree";
            }

            if (redReason != null) {
                System.out.println("TERRAIN_SLABS_OBJECT_MODEL_DY_RED reason=" + redReason + fields);
                throw new AssertionError("Terrain Slabs object visual triad split: " + redReason + fields);
            }

            System.out.println("TERRAIN_SLABS_OBJECT_MODEL_DY_GREEN" + fields);
        });
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

    private static boolean shouldProveLiveCrosshairTarget(Identifier id) {
        return "grass_slab".equals(id.getPath());
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

    private static void aimPlayerRaycastFromEye(
            net.minecraft.client.MinecraftClient mc, Vec3d eye, Vec3d target
    ) {
        Vec3d delta = target.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horizontal)));
        double feetY = eye.y - mc.player.getStandingEyeHeight();
        mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
    }

    private static String describeHit(HitResult hit) {
        if (hit == null) {
            return "null";
        }
        if (!(hit instanceof BlockHitResult blockHit)) {
            return hit.getType().toString();
        }
        return hit.getType() + " " + blockHit.getBlockPos() + " hitY=" + blockHit.getPos().y;
    }
}
