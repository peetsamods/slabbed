package com.slabbed.test;

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
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

import java.lang.reflect.Method;

/**
 * Default-off MC 1.21.11 proof for Terrain Slabs direct custom support.
 */
public final class TerrainSlabsDirectSupportClientGameTest implements FabricClientGameTest {
    private static final String COMPAT_DUMP_PROPERTY = "slabbed.terrainSlabsCompatDump";
    private static final String DIRECT_SUPPORT_RED_ONLY_PROPERTY = "slabbed.terrainSlabsDirectSupportRedOnly";
    private static final BlockPos SUPPORT_POS = new BlockPos(24, 200, 0);
    private static final BlockPos VANILLA_SUPPORT_POS = SUPPORT_POS.add(4, 0, 0);

    @Override
    public void runTest(ClientGameTestContext ctx) {
        boolean dump = Boolean.getBoolean(COMPAT_DUMP_PROPERTY);
        boolean proof = Boolean.getBoolean(DIRECT_SUPPORT_RED_ONLY_PROPERTY);
        if (!dump && !proof) {
            return;
        }

        if (!FabricLoader.getInstance().isModLoaded(TerrainSlabsCompat.MOD_ID)) {
            System.out.println("TERRAIN_SLABS_COMPAT_DUMP_BEGIN terrainslabs_not_loaded");
            System.out.println("TERRAIN_SLABS_COMPAT_DUMP_END total=0");
            if (proof) {
                throw new AssertionError("Countered Terrain Slabs mod is not loaded");
            }
            return;
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            ctx.waitTick();
            if (dump) {
                ctx.runOnClient(mc -> dumpTerrainSlabsCompatFacts(mc.world));
            }
            if (proof) {
                runDirectSupportProof(ctx, singleplayer);
            }
        }
    }

    private static void dumpTerrainSlabsCompatFacts(net.minecraft.world.BlockView world) {
        var mod = FabricLoader.getInstance().getModContainer(TerrainSlabsCompat.MOD_ID);
        if (mod.isPresent()) {
            var meta = mod.get().getMetadata();
            System.out.println("TERRAIN_SLABS_COMPAT_DUMP_BEGIN"
                    + " modId=" + meta.getId()
                    + " version=" + meta.getVersion().getFriendlyString()
                    + " name=" + meta.getName()
                    + " worldReady=" + (world != null));
        } else {
            System.out.println("TERRAIN_SLABS_COMPAT_DUMP_BEGIN"
                    + " modId=" + TerrainSlabsCompat.MOD_ID
                    + " version=<unknown> name=<unknown>"
                    + " worldReady=" + (world != null));
        }

        int total = 0;
        for (Identifier id : Registries.BLOCK.getIds()) {
            if (!TerrainSlabsCompat.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            Block block = Registries.BLOCK.get(id);
            for (BlockState state : block.getStateManager().getStates()) {
                total++;
                System.out.println("TERRAIN_SLABS_COMPAT_DUMP_ROW"
                        + " blockId=" + id
                        + " blockClass=" + block.getClass().getName()
                        + " state=" + state
                        + " properties=" + describeProperties(state)
                        + " slabBlock=" + (block instanceof SlabBlock)
                        + " containsSlabType=" + state.contains(SlabBlock.TYPE)
                        + " slabType=" + (state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE).asString() : "none")
                        + " fluidEmpty=" + state.getFluidState().isEmpty()
                        + " shouldSkipOffset=" + TerrainSlabsCompat.shouldSkipOffset(state)
                        + " compatSkipOffset=" + CompatHooks.shouldSkipOffset(state)
                        + " compatSkipSlabSupport=" + compatSkipSlabSupport(state)
                        + " isSupportingSlab=" + SlabSupport.isSupportingSlab(state)
                        + " selfDy=" + (world == null ? Double.NaN : SlabSupport.getYOffset(world, BlockPos.ORIGIN, state))
                        + " candidateSurfaceKind=" + candidateSurfaceKind(state));
            }
        }
        System.out.println("TERRAIN_SLABS_COMPAT_DUMP_END total=" + total);
    }

    private static void runDirectSupportProof(ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        Block terrainSlabBlock = terrainBlock("grass_slab");
        BlockState terrainSlab = terrainSlabBlock.getDefaultState();
        if (!terrainSlab.contains(SlabBlock.TYPE)) {
            throw new AssertionError("terrainslabs:grass_slab has no SlabBlock.TYPE: " + terrainSlab);
        }
        terrainSlab = terrainSlab.with(SlabBlock.TYPE, SlabType.BOTTOM);

        BlockPos objectPos = SUPPORT_POS.up();
        BlockState finalTerrainSlab = terrainSlab;
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(SUPPORT_POS.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(SUPPORT_POS, finalTerrainSlab, Block.NOTIFY_LISTENERS);
            world.setBlockState(objectPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(objectPos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(VANILLA_SUPPORT_POS, Blocks.STONE_SLAB.getDefaultState()
                    .with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
            world.setBlockState(VANILLA_SUPPORT_POS.up(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(VANILLA_SUPPORT_POS.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new AssertionError("client world/player missing for Terrain Slabs direct support proof");
            }

            BlockState supportState = mc.world.getBlockState(SUPPORT_POS);
            BlockState objectState = mc.world.getBlockState(objectPos);
            if (!objectState.isOf(Blocks.STONE)) {
                throw new AssertionError("expected minecraft:stone at " + objectPos + ", found " + objectState);
            }

            boolean skipOffset = CompatHooks.shouldSkipOffset(supportState);
            CompatSkipResult skipSupport = compatSkipSlabSupport(supportState);
            boolean genericSupport = SlabSupport.isSupportingSlab(supportState);
            double supportDy = SlabSupport.getYOffset(mc.world, SUPPORT_POS, supportState);
            double objectDy = SlabSupport.getYOffset(mc.world, objectPos, objectState);
            double modelMinY = modelMinY(mc.world, objectPos, objectState);
            VoxelShape outline = objectState.getOutlineShape(mc.world, objectPos, ShapeContext.of(mc.player));
            VoxelShape raycastShape = objectState.getRaycastShape(mc.world, objectPos);
            double outlineMinY = outline.isEmpty() ? Double.NaN : outline.getBoundingBox().minY;
            double raycastMinY = raycastShape.isEmpty() ? Double.NaN : raycastShape.getBoundingBox().minY;
            double targetY = objectPos.getY() - 0.25d;
            BlockHitResult outlineHit = outline.isEmpty() ? null : outline.raycast(
                    new Vec3d(objectPos.getX() + 0.5d, targetY, objectPos.getZ() + 2.5d),
                    new Vec3d(objectPos.getX() + 0.5d, targetY, objectPos.getZ() - 0.5d),
                    objectPos);
            HitResult worldHit = mc.world.raycast(new RaycastContext(
                    new Vec3d(objectPos.getX() + 0.5d, targetY, objectPos.getZ() + 2.5d),
                    new Vec3d(objectPos.getX() + 0.5d, targetY, objectPos.getZ() - 0.5d),
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            Vec3d liveEye = new Vec3d(objectPos.getX() + 0.5d, targetY, objectPos.getZ() + 2.5d);
            Vec3d liveTarget = new Vec3d(objectPos.getX() + 0.5d, targetY, objectPos.getZ() + 0.5d);
            aimPlayerRaycastFromEye(mc, liveEye, liveTarget, 6.0d);
            mc.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult liveCrosshairHit = mc.crosshairTarget;

            String fields = " supportId=terrainslabs:grass_slab"
                    + " supportState=" + supportState
                    + " supportPos=" + SUPPORT_POS.toShortString()
                    + " subjectId=minecraft:stone"
                    + " subjectPos=" + objectPos.toShortString()
                    + " shouldSkipOffset=" + skipOffset
                    + " shouldSkipSlabSupport=" + skipSupport
                    + " isSupportingSlab=" + genericSupport
                    + " supportDy=" + supportDy
                    + " subjectDy=" + objectDy
                    + " modelMinY=" + modelMinY
                    + " outlineMinY=" + outlineMinY
                    + " raycastShapeMinY=" + (raycastShape.isEmpty() ? "empty" : Double.toString(raycastMinY))
                    + " outlineTargetHit=" + describeHit(outlineHit)
                    + " worldRaycastHit=" + describeHit(worldHit)
                    + " liveCrosshairHit=" + describeHit(liveCrosshairHit);
            System.out.println("TERRAIN_SLABS_DIRECT_SUPPORT_TRACE" + fields);

            String redReason = null;
            if (!skipSupport.present()) {
                redReason = "missing_generic_support_skip_hook";
            } else if (!skipOffset) {
                redReason = "terrain_slab_self_offset_not_skipped";
            } else if (!skipSupport.value()) {
                redReason = "terrain_slab_generic_support_not_skipped";
            } else if (genericSupport) {
                redReason = "terrain_slab_still_generic_support";
            } else if (Math.abs(supportDy) > 1.0e-6d) {
                redReason = "terrain_slab_self_dy_not_zero";
            } else if (Math.abs(objectDy - (-0.5d)) > 1.0e-6d) {
                redReason = "subject_direct_support_dy_mismatch";
            } else if (Math.abs(modelMinY - (-0.5d)) > 1.0e-6d) {
                redReason = "subject_model_dy_mismatch";
            } else if (Math.abs(outlineMinY - (-0.5d)) > 1.0e-6d) {
                redReason = "subject_outline_dy_mismatch";
            } else if (!raycastShape.isEmpty() && Math.abs(raycastMinY - (-0.5d)) > 1.0e-6d) {
                redReason = "subject_raycast_shape_dy_mismatch";
            } else if (outlineHit == null || !outlineHit.getBlockPos().equals(objectPos)) {
                redReason = "subject_outline_target_mismatch";
            } else if (!(liveCrosshairHit instanceof BlockHitResult liveBlockHit)
                    || liveCrosshairHit.getType() != HitResult.Type.BLOCK
                    || !liveBlockHit.getBlockPos().equals(objectPos)) {
                redReason = "subject_live_target_mismatch";
            }

            if (redReason != null) {
                System.out.println("TERRAIN_SLABS_DIRECT_SUPPORT_RED reason=" + redReason + fields);
                throw new AssertionError("Terrain Slabs direct support proof failed: " + redReason + fields);
            }

            System.out.println("TERRAIN_SLABS_DIRECT_SUPPORT_GREEN" + fields);
            assertTerrainSlabsCullingOptOutControl(mc, supportState);
            assertVanillaDirectSupportControl(mc);
        });
    }

    private static void assertTerrainSlabsCullingOptOutControl(MinecraftClient mc, BlockState supportState) {
        boolean skipOffset = CompatHooks.shouldSkipOffset(supportState);
        CompatSkipResult skipSupport = compatSkipSlabSupport(supportState);
        boolean genericSupport = SlabSupport.isSupportingSlab(supportState);
        boolean bottomSlab = SlabSupport.isBottomSlab(supportState);
        boolean solidTopFace = SlabSupport.canTreatAsSolidTopFace(mc.world, SUPPORT_POS);
        boolean directSurface = SlabSupport.isDirectObjectSupportSurface(mc.world, SUPPORT_POS, supportState);
        double directTopOffset = SlabSupport.getDirectObjectSupportTopOffset(supportState);
        double supportDy = SlabSupport.getYOffset(mc.world, SUPPORT_POS, supportState);
        boolean sideSolidFullSquare = supportState.isSideSolidFullSquare(mc.world, SUPPORT_POS, Direction.UP);

        String fields = " supportId=terrainslabs:grass_slab"
                + " supportState=" + supportState
                + " shouldSkipOffset=" + skipOffset
                + " shouldSkipSlabSupport=" + skipSupport
                + " isSupportingSlab=" + genericSupport
                + " isBottomSlab=" + bottomSlab
                + " canTreatAsSolidTopFace=" + solidTopFace
                + " directSurface=" + directSurface
                + " directTopOffset=" + directTopOffset
                + " supportDy=" + supportDy
                + " sideSolidFullSquareUp=" + sideSolidFullSquare;

        if (!skipOffset
                || !skipSupport.present()
                || !skipSupport.value()
                || genericSupport
                || bottomSlab
                || solidTopFace
                || !directSurface
                || Math.abs(directTopOffset - 0.5d) > 1.0e-6d
                || Math.abs(supportDy) > 1.0e-6d) {
            System.out.println("TERRAIN_SLABS_CULLING_OPT_OUT_RED" + fields);
            throw new AssertionError("Terrain Slabs generic support/culling opt-out failed:" + fields);
        }

        System.out.println("TERRAIN_SLABS_CULLING_OPT_OUT_GREEN" + fields);
    }

    private static void assertVanillaDirectSupportControl(MinecraftClient mc) {
        BlockState supportState = mc.world.getBlockState(VANILLA_SUPPORT_POS);
        BlockPos objectPos = VANILLA_SUPPORT_POS.up();
        BlockState objectState = mc.world.getBlockState(objectPos);
        double supportTopOffset = SlabSupport.getDirectObjectSupportTopOffset(supportState);
        boolean directSurface = SlabSupport.isDirectObjectSupportSurface(mc.world, VANILLA_SUPPORT_POS, supportState);
        boolean genericSupport = SlabSupport.isSupportingSlab(supportState);
        double objectDy = SlabSupport.getYOffset(mc.world, objectPos, objectState);
        double modelMinY = modelMinY(mc.world, objectPos, objectState);
        VoxelShape outline = objectState.getOutlineShape(mc.world, objectPos, ShapeContext.of(mc.player));
        double outlineMinY = outline.isEmpty() ? Double.NaN : outline.getBoundingBox().minY;

        String fields = " supportId=minecraft:stone_slab"
                + " supportState=" + supportState
                + " subjectId=minecraft:stone"
                + " supportTopOffset=" + supportTopOffset
                + " directSurface=" + directSurface
                + " isSupportingSlab=" + genericSupport
                + " subjectDy=" + objectDy
                + " modelMinY=" + modelMinY
                + " outlineMinY=" + outlineMinY;

        if (!supportState.isOf(Blocks.STONE_SLAB)
                || !objectState.isOf(Blocks.STONE)
                || Math.abs(supportTopOffset - 0.5d) > 1.0e-6d
                || !directSurface
                || !genericSupport
                || Math.abs(objectDy - (-0.5d)) > 1.0e-6d
                || Math.abs(modelMinY - (-0.5d)) > 1.0e-6d
                || Math.abs(outlineMinY - (-0.5d)) > 1.0e-6d) {
            System.out.println("TERRAIN_SLABS_VANILLA_DIRECT_SUPPORT_RED" + fields);
            throw new AssertionError("Vanilla direct slab support control failed:" + fields);
        }

        System.out.println("TERRAIN_SLABS_VANILLA_DIRECT_SUPPORT_GREEN" + fields);
    }

    private static double modelMinY(net.minecraft.world.BlockRenderView world, BlockPos pos, BlockState state) {
        BlockStateModel model = net.minecraft.client.MinecraftClient.getInstance()
                .getBlockRenderManager()
                .getModel(state);
        if (!(model instanceof FabricBlockStateModel fabricModel)) {
            throw new AssertionError("expected FabricBlockStateModel for " + state + ", found " + model.getClass().getName());
        }
        Renderer renderer = Renderer.get();
        if (renderer == null) {
            throw new AssertionError("Fabric renderer missing for Terrain Slabs direct support proof");
        }
        MutableMesh mesh = renderer.mutableMesh();
        fabricModel.emitQuads(mesh.emitter(), world, pos, state, Random.create(0x51abbEDL), direction -> false);
        if (mesh.size() <= 0) {
            throw new AssertionError("no model quads emitted for " + state + " at " + pos);
        }
        ModelBounds bounds = new ModelBounds();
        mesh.forEach(bounds::accept);
        return bounds.minY;
    }

    private static Block terrainBlock(String path) {
        Identifier id = Identifier.of(TerrainSlabsCompat.MOD_ID, path);
        for (Identifier candidate : Registries.BLOCK.getIds()) {
            if (candidate.equals(id)) {
                return Registries.BLOCK.get(id);
            }
        }
        throw new AssertionError("missing Terrain Slabs block " + id);
    }

    private static CompatSkipResult compatSkipSlabSupport(BlockState state) {
        try {
            Method method = CompatHooks.class.getMethod("shouldSkipSlabSupport", BlockState.class);
            Object result = method.invoke(null, state);
            return new CompatSkipResult(true, Boolean.TRUE.equals(result));
        } catch (NoSuchMethodException e) {
            return new CompatSkipResult(false, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to invoke CompatHooks.shouldSkipSlabSupport", e);
        }
    }

    private static String describeProperties(BlockState state) {
        StringBuilder builder = new StringBuilder();
        for (Property<?> property : state.getProperties()) {
            if (!builder.isEmpty()) {
                builder.append(",");
            }
            builder.append(property.getName()).append("=");
            appendPropertyValue(builder, state, property);
        }
        return builder.isEmpty() ? "none" : builder.toString();
    }

    private static <T extends Comparable<T>> void appendPropertyValue(
            StringBuilder builder,
            BlockState state,
            Property<T> property
    ) {
        builder.append(property.name(state.get(property)));
    }

    private static String candidateSurfaceKind(BlockState state) {
        if (!state.contains(SlabBlock.TYPE)) {
            return "NONE";
        }
        SlabType type = state.get(SlabBlock.TYPE);
        return switch (type) {
            case BOTTOM -> "BOTTOM_LIKE";
            case TOP -> "TOP_LIKE";
            case DOUBLE -> "DOUBLE_LIKE";
        };
    }

    private static String describeHit(HitResult hit) {
        if (hit == null) {
            return "null";
        }
        if (hit instanceof BlockHitResult blockHit) {
            return hit.getType() + ":" + blockHit.getBlockPos().toShortString() + ":" + blockHit.getSide();
        }
        return hit.getType().toString();
    }

    private static HitResult aimPlayerRaycastFromEye(
            MinecraftClient mc,
            Vec3d eye,
            Vec3d target,
            double reach
    ) {
        Vec3d delta = target.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horizontal)));
        double feetY = eye.y - mc.player.getStandingEyeHeight();
        mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        return mc.player.raycast(reach, 0.0f, false);
    }

    private record CompatSkipResult(boolean present, boolean value) {
        @Override
        public String toString() {
            return present ? Boolean.toString(value) : "MISSING";
        }
    }

    private static final class ModelBounds {
        double minY = Double.POSITIVE_INFINITY;

        void accept(QuadView quad) {
            minY = Math.min(minY, Math.min(Math.min(quad.y(0), quad.y(1)), Math.min(quad.y(2), quad.y(3))));
        }
    }
}
