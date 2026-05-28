package com.slabbed.test;

import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.TorchParticleTrace;
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
import net.minecraft.block.DoorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

import java.lang.reflect.Method;

/**
 * Default-off MC 1.21.11 proof for Terrain Slabs direct custom support.
 */
public final class TerrainSlabsDirectSupportClientGameTest implements FabricClientGameTest {
    private static final String COMPAT_DUMP_PROPERTY = "slabbed.terrainSlabsCompatDump";
    private static final String DIRECT_SUPPORT_RED_ONLY_PROPERTY = "slabbed.terrainSlabsDirectSupportRedOnly";
    private static final String LIVE_PLACEMENT_PROPERTY = "slabbed.terrainSlabsLivePlacementProof";
    private static final String PARTICLE_PROOF_PROPERTY = "slabbed.terrainSlabsParticleProof";
    private static final BlockPos SUPPORT_POS = new BlockPos(24, 200, 0);
    private static final BlockPos VANILLA_SUPPORT_POS = SUPPORT_POS.add(4, 0, 0);
    private static final BlockPos OBJECT_SUPPORT_POS = SUPPORT_POS.add(8, 0, 0);
    private static final BlockPos LIVE_SUPPORT_POS = SUPPORT_POS.add(0, 0, 8);
    private static final double EPSILON = 1.0e-6d;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        boolean dump = Boolean.getBoolean(COMPAT_DUMP_PROPERTY);
        boolean proof = Boolean.getBoolean(DIRECT_SUPPORT_RED_ONLY_PROPERTY);
        boolean livePlacement = Boolean.getBoolean(LIVE_PLACEMENT_PROPERTY);
        boolean particleProof = Boolean.getBoolean(PARTICLE_PROOF_PROPERTY);
        if (!dump && !proof && !livePlacement && !particleProof) {
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
            if (livePlacement) {
                runLivePlacementProof(ctx, singleplayer);
            }
            if (particleProof) {
                runParticleProof(ctx, singleplayer);
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
            world.setBlockState(OBJECT_SUPPORT_POS.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(OBJECT_SUPPORT_POS, finalTerrainSlab, Block.NOTIFY_LISTENERS);
            world.setBlockState(OBJECT_SUPPORT_POS.up(), Blocks.TORCH.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(OBJECT_SUPPORT_POS.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
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
            assertTerrainSlabsLoweredObjectSupport(mc);
            assertVanillaDirectSupportControl(mc);
        });
    }

    private static void runLivePlacementProof(ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        runLivePlacementSurfaceProof(ctx, singleplayer, "grass_slab", LIVE_SUPPORT_POS);
        runGeneratedGrassLivePlacementProof(ctx, singleplayer, LIVE_SUPPORT_POS.add(0, 0, 4));
        runLivePlacementSurfaceProof(ctx, singleplayer, "sand_slab", LIVE_SUPPORT_POS.add(8, 0, 0));
        assertVanillaLivePlacementControl(ctx, singleplayer, LIVE_SUPPORT_POS.add(16, 0, 0));
    }

    private static void runParticleProof(ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        runLivePlacedParticleSurfaceProof(ctx, singleplayer, "grass_slab", LIVE_SUPPORT_POS);
        runGeneratedGrassParticleProof(ctx, singleplayer, LIVE_SUPPORT_POS.add(0, 0, 4));
        runLivePlacedParticleSurfaceProof(ctx, singleplayer, "sand_slab", LIVE_SUPPORT_POS.add(8, 0, 0));
    }

    private static void runLivePlacedParticleSurfaceProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String terrainPath,
            BlockPos supportPos
    ) {
        String supportId = "terrainslabs:" + terrainPath;
        placeSupportByItem(ctx, singleplayer, terrainBlock(terrainPath), supportPos, supportId);
        boolean torchPlacementAccepted = placeSubjectByItem(
                ctx,
                singleplayer,
                new ItemStack(Items.TORCH, 8),
                supportPos,
                "minecraft:torch");
        BlockPos torchPos = supportPos.up();
        ctx.runOnClient(mc -> {
            assertLivePlacedSubject(
                    mc,
                    "TERRAIN_SLABS_PARTICLE_STATE_CONTROL",
                    supportId,
                    supportPos,
                    "minecraft:torch",
                    torchPos,
                    torchPlacementAccepted,
                    true);
            assertTerrainSlabsTorchParticles(mc, supportId, supportPos, torchPos);
        });

        BlockPos redstoneTorchSupportPos = supportPos.add(0, 0, 2);
        placeSupportByItem(ctx, singleplayer, terrainBlock(terrainPath), redstoneTorchSupportPos, supportId);
        boolean redstoneTorchPlacementAccepted = placeSubjectByItem(
                ctx,
                singleplayer,
                new ItemStack(Items.REDSTONE_TORCH, 8),
                redstoneTorchSupportPos,
                "minecraft:redstone_torch");
        BlockPos redstoneTorchPos = redstoneTorchSupportPos.up();
        ctx.runOnClient(mc -> {
            assertLivePlacedSubject(
                    mc,
                    "TERRAIN_SLABS_REDSTONE_PARTICLE_STATE_CONTROL",
                    supportId,
                    redstoneTorchSupportPos,
                    "minecraft:redstone_torch",
                    redstoneTorchPos,
                    redstoneTorchPlacementAccepted,
                    true);
            assertTerrainSlabsRedstoneTorchParticles(mc, supportId, redstoneTorchSupportPos, redstoneTorchPos);
        });
    }

    private static void runGeneratedGrassParticleProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos supportPos
    ) {
        String supportId = "terrainslabs:grass_slab";
        setGeneratedGrassSupport(ctx, singleplayer, supportPos);
        boolean torchPlacementAccepted = placeSubjectByItem(
                ctx,
                singleplayer,
                new ItemStack(Items.TORCH, 8),
                supportPos,
                "minecraft:torch");
        BlockPos torchPos = supportPos.up();
        ctx.runOnClient(mc -> {
            assertLivePlacedSubject(
                    mc,
                    "TERRAIN_SLABS_PARTICLE_STATE_CONTROL_GENERATED_GRASS",
                    supportId,
                    supportPos,
                    "minecraft:torch",
                    torchPos,
                    torchPlacementAccepted,
                    true);
            assertTerrainSlabsTorchParticles(mc, supportId, supportPos, torchPos);
        });

        BlockPos redstoneTorchSupportPos = supportPos.add(0, 0, 2);
        setGeneratedGrassSupport(ctx, singleplayer, redstoneTorchSupportPos);
        boolean redstoneTorchPlacementAccepted = placeSubjectByItem(
                ctx,
                singleplayer,
                new ItemStack(Items.REDSTONE_TORCH, 8),
                redstoneTorchSupportPos,
                "minecraft:redstone_torch");
        BlockPos redstoneTorchPos = redstoneTorchSupportPos.up();
        ctx.runOnClient(mc -> {
            assertLivePlacedSubject(
                    mc,
                    "TERRAIN_SLABS_REDSTONE_PARTICLE_STATE_CONTROL_GENERATED_GRASS",
                    supportId,
                    redstoneTorchSupportPos,
                    "minecraft:redstone_torch",
                    redstoneTorchPos,
                    redstoneTorchPlacementAccepted,
                    true);
            assertTerrainSlabsRedstoneTorchParticles(mc, supportId, redstoneTorchSupportPos, redstoneTorchPos);
        });
    }

    private static void assertTerrainSlabsTorchParticles(
            MinecraftClient mc,
            String supportId,
            BlockPos supportPos,
            BlockPos torchPos
    ) {
        if (mc.world == null) {
            throw new AssertionError("client world missing for Terrain Slabs particle proof");
        }
        BlockState supportState = mc.world.getBlockState(supportPos);
        BlockState torchState = mc.world.getBlockState(torchPos);
        System.setProperty("slabbed.torchParticleTrace", "true");
        TorchParticleTrace.reset();
        try {
            mc.world.randomBlockDisplayTick(
                    torchPos.getX(),
                    torchPos.getY(),
                    torchPos.getZ(),
                    1,
                    Random.create(5485906805653361449L),
                    Blocks.AIR,
                    new BlockPos.Mutable());
        } finally {
            System.clearProperty("slabbed.torchParticleTrace");
        }

        TorchParticleTrace.HookEntry hookTrace = TorchParticleTrace.hookSnapshot();
        TorchParticleTrace.ParticleEntry clientTrace = TorchParticleTrace.clientParticleSnapshot();
        double torchDy = SlabSupport.getYOffset(mc.world, torchPos, torchState);
        double expectedParticleY = torchPos.getY() + 0.7d + torchDy;
        String fields = " supportId=" + supportId
                + " supportState=" + supportState
                + " supportProperties=" + describeProperties(supportState)
                + " supportPos=" + supportPos.toShortString()
                + " subjectId=minecraft:torch"
                + " torchState=" + torchState
                + " torchPos=" + torchPos.toShortString()
                + " torchDy=" + torchDy
                + " expectedParticleY=" + expectedParticleY
                + " hookSeen=" + hookTrace.seen()
                + " hookPos=" + hookTrace.pos()
                + " hookDy=" + hookTrace.dy()
                + " hookParticleY=" + hookTrace.particleY()
                + " clientParticleSeen=" + clientTrace.seen()
                + " clientParticleId=" + clientTrace.particleId()
                + " clientParticleX=" + clientTrace.x()
                + " clientParticleY=" + clientTrace.y()
                + " clientParticleZ=" + clientTrace.z();
        System.out.println("TERRAIN_SLABS_TORCH_PARTICLE_TRACE" + fields);

        String redReason = null;
        if (!hookTrace.seen()) {
            redReason = "torch_particle_hook_not_seen";
        } else if (!torchPos.toShortString().equals(hookTrace.pos())) {
            redReason = "torch_particle_hook_wrong_pos";
        } else if (Math.abs(torchDy - (-0.5d)) > EPSILON) {
            redReason = "torch_dy_not_lowered";
        } else if (Math.abs(hookTrace.dy() - torchDy) > EPSILON) {
            redReason = "particle_hook_dy_mismatch";
        } else if (Math.abs(hookTrace.particleY() - expectedParticleY) > EPSILON) {
            redReason = "particle_hook_y_mismatch";
        } else if (!clientTrace.seen()) {
            redReason = "client_particle_not_seen";
        } else if (Math.abs(clientTrace.y() - expectedParticleY) > EPSILON) {
            redReason = "client_particle_y_mismatch";
        }

        if (redReason != null) {
            System.out.println("TERRAIN_SLABS_TORCH_PARTICLE_RED reason=" + redReason + fields);
            throw new AssertionError("TERRAIN_SLABS_TORCH_PARTICLE failed: " + redReason + fields);
        }

        System.out.println("TERRAIN_SLABS_TORCH_PARTICLE_GREEN" + fields);
    }

    private static void assertTerrainSlabsRedstoneTorchParticles(
            MinecraftClient mc,
            String supportId,
            BlockPos supportPos,
            BlockPos torchPos
    ) {
        if (mc.world == null) {
            throw new AssertionError("client world missing for Terrain Slabs redstone torch particle proof");
        }
        BlockState supportState = mc.world.getBlockState(supportPos);
        BlockState torchState = mc.world.getBlockState(torchPos);
        System.setProperty("slabbed.torchParticleTrace", "true");
        TorchParticleTrace.reset();
        try {
            mc.world.randomBlockDisplayTick(
                    torchPos.getX(),
                    torchPos.getY(),
                    torchPos.getZ(),
                    1,
                    Random.create(5485906805653361449L),
                    Blocks.AIR,
                    new BlockPos.Mutable());
        } finally {
            System.clearProperty("slabbed.torchParticleTrace");
        }

        TorchParticleTrace.HookEntry hookTrace = TorchParticleTrace.hookSnapshot();
        TorchParticleTrace.ParticleEntry clientTrace = TorchParticleTrace.clientParticleSnapshot();
        double torchDy = SlabSupport.getYOffset(mc.world, torchPos, torchState);
        String fields = " supportId=" + supportId
                + " supportState=" + supportState
                + " supportProperties=" + describeProperties(supportState)
                + " supportPos=" + supportPos.toShortString()
                + " subjectId=minecraft:redstone_torch"
                + " torchState=" + torchState
                + " torchPos=" + torchPos.toShortString()
                + " torchDy=" + torchDy
                + " hookSeen=" + hookTrace.seen()
                + " hookPos=" + hookTrace.pos()
                + " hookDy=" + hookTrace.dy()
                + " hookParticleY=" + hookTrace.particleY()
                + " clientParticleSeen=" + clientTrace.seen()
                + " clientParticleId=" + clientTrace.particleId()
                + " clientParticleX=" + clientTrace.x()
                + " clientParticleY=" + clientTrace.y()
                + " clientParticleZ=" + clientTrace.z();
        System.out.println("TERRAIN_SLABS_REDSTONE_TORCH_PARTICLE_TRACE" + fields);

        String redReason = null;
        if (!hookTrace.seen()) {
            redReason = "redstone_torch_particle_hook_not_seen";
        } else if (!torchPos.toShortString().equals(hookTrace.pos())) {
            redReason = "redstone_torch_particle_hook_wrong_pos";
        } else if (Math.abs(torchDy - (-0.5d)) > EPSILON) {
            redReason = "redstone_torch_dy_not_lowered";
        } else if (Math.abs(hookTrace.dy() - torchDy) > EPSILON) {
            redReason = "redstone_particle_hook_dy_mismatch";
        } else if (!clientTrace.seen()) {
            redReason = "redstone_client_particle_not_seen";
        } else if (!"minecraft:dust".equals(clientTrace.particleId())) {
            redReason = "redstone_client_particle_type_mismatch";
        } else if (Math.abs(clientTrace.y() - hookTrace.particleY()) > EPSILON) {
            redReason = "redstone_client_particle_y_mismatch";
        }

        if (redReason != null) {
            System.out.println("TERRAIN_SLABS_REDSTONE_TORCH_PARTICLE_RED reason=" + redReason + fields);
            throw new AssertionError("TERRAIN_SLABS_REDSTONE_TORCH_PARTICLE failed: " + redReason + fields);
        }

        System.out.println("TERRAIN_SLABS_REDSTONE_TORCH_PARTICLE_GREEN" + fields);
    }

    private static void runGeneratedGrassLivePlacementProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos supportPos
    ) {
        setGeneratedGrassSupport(ctx, singleplayer, supportPos);
        boolean stonePlacementAccepted = placeSubjectByItem(
                ctx,
                singleplayer,
                new ItemStack(Items.STONE, 8),
                supportPos,
                "minecraft:stone");
        ctx.runOnClient(mc -> assertLivePlacedSubject(
                mc,
                "TERRAIN_SLABS_LIVE_PLACEMENT_GENERATED_GRASS_FULL",
                "terrainslabs:grass_slab",
                supportPos,
                "minecraft:stone",
                supportPos.up(),
                stonePlacementAccepted,
                true));

        BlockPos torchSupportPos = supportPos.add(0, 0, 2);
        setGeneratedGrassSupport(ctx, singleplayer, torchSupportPos);
        boolean torchPlacementAccepted = placeSubjectByItem(
                ctx,
                singleplayer,
                new ItemStack(Items.TORCH, 8),
                torchSupportPos,
                "minecraft:torch");
        ctx.runOnClient(mc -> assertLivePlacedSubject(
                mc,
                "TERRAIN_SLABS_LIVE_PLACEMENT_GENERATED_GRASS_OBJECT",
                "terrainslabs:grass_slab",
                torchSupportPos,
                "minecraft:torch",
                torchSupportPos.up(),
                torchPlacementAccepted,
                true));

        BlockPos fenceSupportPos = supportPos.add(0, 0, 4);
        setGeneratedGrassSupport(ctx, singleplayer, fenceSupportPos);
        boolean fencePlacementAccepted = placeSubjectByItem(
                ctx,
                singleplayer,
                new ItemStack(Items.OAK_FENCE, 8),
                fenceSupportPos,
                "minecraft:oak_fence");
        ctx.runOnClient(mc -> assertLivePlacedSubject(
                mc,
                "TERRAIN_SLABS_LIVE_PLACEMENT_GENERATED_GRASS_FENCE",
                "terrainslabs:grass_slab",
                fenceSupportPos,
                "minecraft:oak_fence",
                fenceSupportPos.up(),
                fencePlacementAccepted,
                true));

        BlockPos doorSupportPos = supportPos.add(0, 0, 6);
        setGeneratedGrassSupport(ctx, singleplayer, doorSupportPos);
        boolean doorPlacementAccepted = placeSubjectByItem(
                ctx,
                singleplayer,
                new ItemStack(Items.OAK_DOOR, 8),
                doorSupportPos,
                "minecraft:oak_door");
        ctx.runOnClient(mc -> assertLivePlacedDoorSubject(
                mc,
                "TERRAIN_SLABS_LIVE_PLACEMENT_GENERATED_GRASS_DOOR",
                "terrainslabs:grass_slab",
                doorSupportPos,
                doorPlacementAccepted,
                true));
    }

    private static void runLivePlacementSurfaceProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String terrainPath,
            BlockPos supportPos
    ) {
        Block terrainSlabBlock = terrainBlock(terrainPath);
        placeSupportByItem(ctx, singleplayer, terrainSlabBlock, supportPos, "terrainslabs:" + terrainPath);
        boolean stonePlacementAccepted = placeSubjectByItem(
                ctx,
                singleplayer,
                new ItemStack(Items.STONE, 8),
                supportPos,
                "minecraft:stone");

        BlockPos stonePos = supportPos.up();
        ctx.runOnClient(mc -> assertLivePlacedSubject(
                mc,
                "TERRAIN_SLABS_LIVE_PLACEMENT_FULL",
                "terrainslabs:" + terrainPath,
                supportPos,
                "minecraft:stone",
                stonePos,
                stonePlacementAccepted,
                true));

        BlockPos torchSupportPos = supportPos.add(0, 0, 2);
        placeSupportByItem(ctx, singleplayer, terrainSlabBlock, torchSupportPos, "terrainslabs:" + terrainPath);
        boolean torchPlacementAccepted = placeSubjectByItem(
                ctx,
                singleplayer,
                new ItemStack(Items.TORCH, 8),
                torchSupportPos,
                "minecraft:torch");

        BlockPos torchPos = torchSupportPos.up();
        ctx.runOnClient(mc -> assertLivePlacedSubject(
                mc,
                "TERRAIN_SLABS_LIVE_PLACEMENT_OBJECT",
                "terrainslabs:" + terrainPath,
                torchSupportPos,
                "minecraft:torch",
                torchPos,
                torchPlacementAccepted,
                true));

        BlockPos fenceSupportPos = supportPos.add(0, 0, 4);
        placeSupportByItem(ctx, singleplayer, terrainSlabBlock, fenceSupportPos, "terrainslabs:" + terrainPath);
        boolean fencePlacementAccepted = placeSubjectByItem(
                ctx,
                singleplayer,
                new ItemStack(Items.OAK_FENCE, 8),
                fenceSupportPos,
                "minecraft:oak_fence");

        BlockPos fencePos = fenceSupportPos.up();
        ctx.runOnClient(mc -> assertLivePlacedSubject(
                mc,
                "TERRAIN_SLABS_LIVE_PLACEMENT_FENCE",
                "terrainslabs:" + terrainPath,
                fenceSupportPos,
                "minecraft:oak_fence",
                fencePos,
                fencePlacementAccepted,
                true));

        BlockPos doorSupportPos = supportPos.add(0, 0, 6);
        placeSupportByItem(ctx, singleplayer, terrainSlabBlock, doorSupportPos, "terrainslabs:" + terrainPath);
        boolean doorPlacementAccepted = placeSubjectByItem(
                ctx,
                singleplayer,
                new ItemStack(Items.OAK_DOOR, 8),
                doorSupportPos,
                "minecraft:oak_door");
        ctx.runOnClient(mc -> assertLivePlacedDoorSubject(
                mc,
                "TERRAIN_SLABS_LIVE_PLACEMENT_DOOR",
                "terrainslabs:" + terrainPath,
                doorSupportPos,
                doorPlacementAccepted,
                true));
    }

    private static void assertVanillaLivePlacementControl(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos supportPos
    ) {
        placeSupportByItem(ctx, singleplayer, Blocks.STONE_SLAB, supportPos, "minecraft:stone_slab");
        boolean stonePlacementAccepted = placeSubjectByItem(
                ctx,
                singleplayer,
                new ItemStack(Items.STONE, 8),
                supportPos,
                "minecraft:stone");
        ctx.runOnClient(mc -> assertLivePlacedSubject(
                mc,
                "TERRAIN_SLABS_LIVE_PLACEMENT_VANILLA_CONTROL",
                "minecraft:stone_slab",
                supportPos,
                "minecraft:stone",
                supportPos.up(),
                stonePlacementAccepted,
                false));
    }

    private static void placeSupportByItem(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Block supportBlock,
            BlockPos supportPos,
            String supportId
    ) {
        BlockPos basePos = supportPos.down();
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(basePos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(supportPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(supportPos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(supportPos.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            var player = server.getPlayerManager().getPlayerList().get(0);
            player.changeGameMode(GameMode.CREATIVE);
            player.setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(supportBlock.asItem(), 8));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        BlockHitResult supportHit = topFaceHit(basePos);
        syncPlayerAim(ctx, singleplayer, placementEye(supportPos), supportHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new AssertionError("client not ready for live support placement " + supportId);
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(supportBlock.asItem(), 8));
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult liveTarget = mc.crosshairTarget;
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, supportHit);
            System.out.println("TERRAIN_SLABS_LIVE_SUPPORT_PLACE"
                    + " supportId=" + supportId
                    + " supportPos=" + supportPos.toShortString()
                    + " hit=" + describeHit(supportHit)
                    + " liveTarget=" + describeHit(liveTarget)
                    + " action=" + result);
            if (!result.isAccepted()) {
                throw new AssertionError("live support placement rejected for " + supportId + ": " + result);
            }
        });
        waitForPlacementSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();
        waitForSupportState(ctx, singleplayer, supportPos, supportId);
    }

    private static void setGeneratedGrassSupport(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos supportPos
    ) {
        Block grassSlab = terrainBlock("grass_slab");
        BlockState state = grassSlab.getDefaultState();
        state = state.with(SlabBlock.TYPE, SlabType.BOTTOM);
        state = withBooleanPropertyIfPresent(state, "generated", true);
        state = withBooleanPropertyIfPresent(state, "snowy", false);
        state = withBooleanPropertyIfPresent(state, "waterlogged", false);
        BlockState generatedGrass = state;
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(supportPos, generatedGrass, Block.NOTIFY_LISTENERS);
            world.setBlockState(supportPos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(supportPos.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
        singleplayer.getClientWorld().waitForChunksRender();
        waitForSupportState(ctx, singleplayer, supportPos, "terrainslabs:grass_slab");
    }

    private static void waitForSupportState(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos supportPos,
            String supportId
    ) {
        String[] lastState = {"not_checked"};
        for (int i = 0; i < 12; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            boolean[] matched = {false};
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    lastState[0] = "client_world_missing";
                    return;
                }
                BlockState state = mc.world.getBlockState(supportPos);
                lastState[0] = state.toString();
                matched[0] = Registries.BLOCK.getId(state.getBlock()).toString().equals(supportId);
            });
            if (matched[0]) {
                return;
            }
        }
        throw new AssertionError("support placement did not sync for "
                + supportId
                + " at "
                + supportPos.toShortString()
                + ": lastState="
                + lastState[0]);
    }

    private static boolean placeSubjectByItem(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            ItemStack stack,
            BlockPos supportPos,
            String subjectId
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().get(0);
            player.changeGameMode(GameMode.CREATIVE);
            player.setStackInHand(Hand.MAIN_HAND, stack.copy());
        });
        ctx.waitTick();

        boolean[] accepted = {false};
        BlockHitResult subjectHit = bottomSlabTopFaceHit(supportPos);
        syncPlayerAim(ctx, singleplayer, placementEye(supportPos), subjectHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new AssertionError("client not ready for live subject placement " + subjectId);
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, stack.copy());
            BlockHitResult liveTarget = requireBottomSlabTopFaceTarget(mc, supportPos, "subject", subjectId);
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, liveTarget);
            accepted[0] = result.isAccepted();
            System.out.println("TERRAIN_SLABS_LIVE_SUBJECT_PLACE"
                    + " subjectId=" + subjectId
                    + " supportPos=" + supportPos.toShortString()
                    + " intendedHit=" + describeHit(subjectHit)
                    + " clickedLiveTarget=" + describeHit(liveTarget)
                    + " action=" + result);
        });
        waitForPlacementSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();
        return accepted[0];
    }

    private static Vec3d placementEye(BlockPos targetPos) {
        return new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + 2.2d, targetPos.getZ() + 2.5d);
    }

    private static void syncPlayerAim(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Vec3d eye,
            Vec3d target
    ) {
        Vec3d delta = target.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        singleplayer.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            var player = server.getPlayerManager().getPlayerList().get(0);
            player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            player.setVelocity(Vec3d.ZERO);
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new AssertionError("client not ready to sync Terrain Slabs live placement camera");
            }
            mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            mc.player.setVelocity(Vec3d.ZERO);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
    }

    private static void waitForPlacementSync(ClientGameTestContext ctx) {
        for (int i = 0; i < 3; i++) {
            ctx.waitTick();
        }
    }

    private static BlockHitResult topFaceHit(BlockPos pos) {
        return new BlockHitResult(
                new Vec3d(pos.getX() + 0.5d, pos.getY() + 1.0d, pos.getZ() + 0.5d),
                Direction.UP,
                pos,
                false,
                false);
    }

    private static BlockHitResult bottomSlabTopFaceHit(BlockPos pos) {
        return new BlockHitResult(
                new Vec3d(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d),
                Direction.UP,
                pos,
                false,
                false);
    }

    private static BlockHitResult requireBottomSlabTopFaceTarget(
            MinecraftClient mc,
            BlockPos expectedPos,
            String phase,
            String id
    ) {
        mc.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult target = mc.crosshairTarget;
        if (!(target instanceof BlockHitResult blockHit)
                || target.getType() != HitResult.Type.BLOCK
                || !blockHit.getBlockPos().equals(expectedPos)
                || blockHit.getSide() != Direction.UP
                || Math.abs(blockHit.getPos().y - (expectedPos.getY() + 0.5d)) > EPSILON) {
            BlockState supportState = mc.world.getBlockState(expectedPos);
            VoxelShape outline = supportState.getOutlineShape(mc.world, expectedPos, ShapeContext.of(mc.player));
            VoxelShape raycast = supportState.getRaycastShape(mc.world, expectedPos);
            Vec3d eye = mc.player.getCameraPosVec(0.0f);
            Vec3d expectedHit = bottomSlabTopFaceHit(expectedPos).getPos();
            Vec3d end = eye.add(expectedHit.subtract(eye).normalize().multiply(6.0d));
            HitResult worldRaycast = mc.world.raycast(new RaycastContext(
                    eye,
                    end,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            System.out.println("TERRAIN_SLABS_LIVE_TARGET_RED"
                    + " phase=" + phase
                    + " id=" + id
                    + " expectedPos=" + expectedPos.toShortString()
                    + " expectedY=" + (expectedPos.getY() + 0.5d)
                    + " supportState=" + supportState
                    + " supportProperties=" + describeProperties(supportState)
                    + " customSlabSurfaceKind=" + CompatHooks.customSlabSurfaceKind(supportState)
                    + " supportDy=" + SlabSupport.getYOffset(mc.world, expectedPos, supportState)
                    + " directSurface=" + SlabSupport.isDirectObjectSupportSurface(mc.world, expectedPos, supportState)
                    + " outlineBounds=" + describeShapeBounds(outline)
                    + " raycastBounds=" + describeShapeBounds(raycast)
                    + " eye=" + describeVec(eye)
                    + " intendedHit=" + describeVec(expectedHit)
                    + " worldRaycast=" + describeHit(worldRaycast)
                    + " liveTarget=" + describeHit(target));
            throw new AssertionError("live " + phase + " placement target mismatch for "
                    + id
                    + ": expected bottom slab top face "
                    + expectedPos.toShortString()
                    + " y=" + (expectedPos.getY() + 0.5d)
                    + ", got " + describeHit(target));
        }
        return blockHit;
    }

    private static void assertLivePlacedSubject(
            MinecraftClient mc,
            String markerPrefix,
            String supportId,
            BlockPos supportPos,
            String subjectId,
            BlockPos subjectPos,
            boolean subjectPlacementAccepted,
            boolean expectCustomSupport
    ) {
        if (mc.world == null || mc.player == null) {
            throw new AssertionError("client world/player missing for " + markerPrefix);
        }

        BlockState supportState = mc.world.getBlockState(supportPos);
        BlockState subjectState = mc.world.getBlockState(subjectPos);
        boolean supportIsExpected = Registries.BLOCK.getId(supportState.getBlock()).toString().equals(supportId);
        boolean subjectIsExpected = Registries.BLOCK.getId(subjectState.getBlock()).toString().equals(subjectId);
        boolean skipOffset = CompatHooks.shouldSkipOffset(supportState);
        CompatSkipResult skipSupport = compatSkipSlabSupport(supportState);
        CompatSlabSurfaceKind surfaceKind = CompatHooks.customSlabSurfaceKind(supportState);
        boolean genericSupport = SlabSupport.isSupportingSlab(supportState);
        boolean directSurface = SlabSupport.isDirectObjectSupportSurface(mc.world, supportPos, supportState);
        boolean directSubject = SlabSupport.isDirectCustomSlabSupportedObject(mc.world, subjectPos, subjectState);
        double supportDy = SlabSupport.getYOffset(mc.world, supportPos, supportState);
        double subjectDy = SlabSupport.getYOffset(mc.world, subjectPos, subjectState);
        double modelMinY = subjectIsExpected ? modelMinY(mc.world, subjectPos, subjectState) : Double.NaN;
        VoxelShape outline = subjectState.getOutlineShape(mc.world, subjectPos, ShapeContext.of(mc.player));
        VoxelShape raycastShape = subjectState.getRaycastShape(mc.world, subjectPos);
        double outlineMinY = outline.isEmpty() ? Double.NaN : outline.getBoundingBox().minY;
        double raycastMinY = raycastShape.isEmpty() ? Double.NaN : raycastShape.getBoundingBox().minY;
        double targetY = subjectPos.getY() - 0.25d;
        BlockHitResult outlineHit = outline.isEmpty() ? null : outline.raycast(
                new Vec3d(subjectPos.getX() + 0.5d, targetY, subjectPos.getZ() + 2.5d),
                new Vec3d(subjectPos.getX() + 0.5d, targetY, subjectPos.getZ() - 0.5d),
                subjectPos);
        HitResult worldHit = mc.world.raycast(new RaycastContext(
                new Vec3d(subjectPos.getX() + 0.5d, targetY, subjectPos.getZ() + 2.5d),
                new Vec3d(subjectPos.getX() + 0.5d, targetY, subjectPos.getZ() - 0.5d),
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player));
        Vec3d liveEye = new Vec3d(subjectPos.getX() + 0.5d, targetY, subjectPos.getZ() + 2.5d);
        Vec3d liveTarget = new Vec3d(subjectPos.getX() + 0.5d, targetY, subjectPos.getZ() + 0.5d);
        aimPlayerRaycastFromEye(mc, liveEye, liveTarget, 6.0d);
        mc.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        mc.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult liveCrosshairHit = mc.crosshairTarget;

        String fields = " supportId=" + supportId
                + " supportState=" + supportState
                + " supportProperties=" + describeProperties(supportState)
                + " supportPos=" + supportPos.toShortString()
                + " subjectId=" + subjectId
                + " subjectState=" + subjectState
                + " subjectPos=" + subjectPos.toShortString()
                + " subjectPlacementAccepted=" + subjectPlacementAccepted
                + " supportMatches=" + supportIsExpected
                + " subjectMatches=" + subjectIsExpected
                + " customSlabSurfaceKind=" + surfaceKind
                + " shouldSkipOffset=" + skipOffset
                + " shouldSkipSlabSupport=" + skipSupport
                + " isSupportingSlab=" + genericSupport
                + " directSurface=" + directSurface
                + " directSubject=" + directSubject
                + " supportDy=" + supportDy
                + " subjectDy=" + subjectDy
                + " modelMinY=" + modelMinY
                + " outlineMinY=" + outlineMinY
                + " raycastShapeMinY=" + (raycastShape.isEmpty() ? "empty" : Double.toString(raycastMinY))
                + " outlineTargetHit=" + describeHit(outlineHit)
                + " worldRaycastHit=" + describeHit(worldHit)
                + " liveCrosshairHit=" + describeHit(liveCrosshairHit);
        System.out.println(markerPrefix + "_TRACE" + fields);

        String redReason = null;
        if (!supportIsExpected) {
            redReason = "support_placement_state_mismatch";
        } else if (!subjectPlacementAccepted) {
            redReason = "subject_placement_rejected";
        } else if (!subjectIsExpected) {
            redReason = "subject_placement_state_mismatch";
        } else if (expectCustomSupport && !skipOffset) {
            redReason = "terrain_slab_self_offset_not_skipped";
        } else if (expectCustomSupport && (!skipSupport.present() || !skipSupport.value())) {
            redReason = "terrain_slab_generic_support_not_skipped";
        } else if (expectCustomSupport && genericSupport) {
            redReason = "terrain_slab_still_generic_support";
        } else if (expectCustomSupport && surfaceKind != CompatSlabSurfaceKind.BOTTOM_LIKE) {
            redReason = "support_not_bottom_like_custom_surface";
        } else if (Math.abs(supportDy) > EPSILON) {
            redReason = "support_dy_not_zero";
        } else if (!directSurface) {
            redReason = "support_not_direct_surface";
        } else if (expectCustomSupport && !directSubject) {
            redReason = "subject_not_direct_custom_supported";
        } else if (Math.abs(subjectDy - (-0.5d)) > EPSILON) {
            redReason = "subject_dy_mismatch";
        } else if (Math.abs(modelMinY - (-0.5d)) > EPSILON) {
            redReason = "subject_model_dy_mismatch";
        } else if (Math.abs(outlineMinY - (-0.5d)) > EPSILON) {
            redReason = "subject_outline_dy_mismatch";
        } else if (!raycastShape.isEmpty() && Math.abs(raycastMinY - (-0.5d)) > EPSILON) {
            redReason = "subject_raycast_shape_dy_mismatch";
        } else if (outlineHit == null || !outlineHit.getBlockPos().equals(subjectPos)) {
            redReason = "subject_outline_target_mismatch";
        } else if (!(liveCrosshairHit instanceof BlockHitResult liveBlockHit)
                || liveCrosshairHit.getType() != HitResult.Type.BLOCK
                || !liveBlockHit.getBlockPos().equals(subjectPos)) {
            redReason = "subject_live_target_mismatch";
        }

        if (redReason != null) {
            System.out.println(markerPrefix + "_RED reason=" + redReason + fields);
            throw new AssertionError(markerPrefix + " failed: " + redReason + fields);
        }

        System.out.println(markerPrefix + "_GREEN" + fields);
    }

    private static void assertLivePlacedDoorSubject(
            MinecraftClient mc,
            String markerPrefix,
            String supportId,
            BlockPos supportPos,
            boolean subjectPlacementAccepted,
            boolean expectCustomSupport
    ) {
        if (mc.world == null || mc.player == null) {
            throw new AssertionError("client world/player missing for " + markerPrefix);
        }

        BlockPos lowerPos = supportPos.up();
        BlockPos upperPos = supportPos.up(2);
        BlockState supportState = mc.world.getBlockState(supportPos);
        BlockState lowerState = mc.world.getBlockState(lowerPos);
        BlockState upperState = mc.world.getBlockState(upperPos);
        boolean supportIsExpected = Registries.BLOCK.getId(supportState.getBlock()).toString().equals(supportId);
        boolean lowerIsDoor = lowerState.getBlock() instanceof DoorBlock
                && lowerState.contains(DoorBlock.HALF)
                && lowerState.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
        boolean upperIsDoor = upperState.getBlock() instanceof DoorBlock
                && upperState.contains(DoorBlock.HALF)
                && upperState.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
        CompatSlabSurfaceKind surfaceKind = CompatHooks.customSlabSurfaceKind(supportState);
        boolean genericSupport = SlabSupport.isSupportingSlab(supportState);
        boolean directSurface = SlabSupport.isDirectObjectSupportSurface(mc.world, supportPos, supportState);
        boolean lowerDirectSubject = SlabSupport.isDirectCustomSlabSupportedObject(mc.world, lowerPos, lowerState);
        boolean upperDirectSubject = SlabSupport.isDirectCustomSlabSupportedObject(mc.world, upperPos, upperState);
        double supportDy = SlabSupport.getYOffset(mc.world, supportPos, supportState);
        double lowerDy = SlabSupport.getYOffset(mc.world, lowerPos, lowerState);
        double upperDy = SlabSupport.getYOffset(mc.world, upperPos, upperState);
        double lowerModelMinY = lowerIsDoor ? modelMinY(mc.world, lowerPos, lowerState) : Double.NaN;
        double upperModelMinY = upperIsDoor ? modelMinY(mc.world, upperPos, upperState) : Double.NaN;
        VoxelShape lowerOutline = lowerState.getOutlineShape(mc.world, lowerPos, ShapeContext.of(mc.player));
        VoxelShape upperOutline = upperState.getOutlineShape(mc.world, upperPos, ShapeContext.of(mc.player));
        double lowerOutlineMinY = lowerOutline.isEmpty() ? Double.NaN : lowerOutline.getBoundingBox().minY;
        double upperOutlineMinY = upperOutline.isEmpty() ? Double.NaN : upperOutline.getBoundingBox().minY;

        String fields = " supportId=" + supportId
                + " supportState=" + supportState
                + " supportProperties=" + describeProperties(supportState)
                + " supportPos=" + supportPos.toShortString()
                + " subjectId=minecraft:oak_door"
                + " lowerState=" + lowerState
                + " lowerPos=" + lowerPos.toShortString()
                + " upperState=" + upperState
                + " upperPos=" + upperPos.toShortString()
                + " subjectPlacementAccepted=" + subjectPlacementAccepted
                + " supportMatches=" + supportIsExpected
                + " lowerIsDoor=" + lowerIsDoor
                + " upperIsDoor=" + upperIsDoor
                + " customSlabSurfaceKind=" + surfaceKind
                + " shouldSkipOffset=" + CompatHooks.shouldSkipOffset(supportState)
                + " shouldSkipSlabSupport=" + compatSkipSlabSupport(supportState)
                + " isSupportingSlab=" + genericSupport
                + " directSurface=" + directSurface
                + " lowerDirectSubject=" + lowerDirectSubject
                + " upperDirectSubject=" + upperDirectSubject
                + " supportDy=" + supportDy
                + " lowerDy=" + lowerDy
                + " upperDy=" + upperDy
                + " lowerModelMinY=" + lowerModelMinY
                + " upperModelMinY=" + upperModelMinY
                + " lowerOutlineMinY=" + lowerOutlineMinY
                + " upperOutlineMinY=" + upperOutlineMinY;
        System.out.println(markerPrefix + "_TRACE" + fields);

        String redReason = null;
        if (!supportIsExpected) {
            redReason = "support_placement_state_mismatch";
        } else if (!subjectPlacementAccepted) {
            redReason = "door_placement_rejected";
        } else if (!lowerIsDoor) {
            redReason = "door_lower_state_mismatch";
        } else if (!upperIsDoor) {
            redReason = "door_upper_state_mismatch";
        } else if (expectCustomSupport && genericSupport) {
            redReason = "terrain_slab_still_generic_support";
        } else if (expectCustomSupport && surfaceKind != CompatSlabSurfaceKind.BOTTOM_LIKE) {
            redReason = "support_not_bottom_like_custom_surface";
        } else if (Math.abs(supportDy) > EPSILON) {
            redReason = "support_dy_not_zero";
        } else if (!directSurface) {
            redReason = "support_not_direct_surface";
        } else if (expectCustomSupport && !lowerDirectSubject) {
            redReason = "door_lower_not_direct_custom_supported";
        } else if (expectCustomSupport && !upperDirectSubject) {
            redReason = "door_upper_not_direct_custom_supported";
        } else if (Math.abs(lowerDy - (-0.5d)) > EPSILON) {
            redReason = "door_lower_dy_mismatch";
        } else if (Math.abs(upperDy - (-0.5d)) > EPSILON) {
            redReason = "door_upper_dy_mismatch";
        } else if (Math.abs(lowerModelMinY - (-0.5d)) > EPSILON) {
            redReason = "door_lower_model_dy_mismatch";
        } else if (Math.abs(upperModelMinY - (-0.5d)) > EPSILON) {
            redReason = "door_upper_model_dy_mismatch";
        } else if (Math.abs(lowerOutlineMinY - (-0.5d)) > EPSILON) {
            redReason = "door_lower_outline_dy_mismatch";
        } else if (Math.abs(upperOutlineMinY - (-0.5d)) > EPSILON) {
            redReason = "door_upper_outline_dy_mismatch";
        }

        if (redReason != null) {
            System.out.println(markerPrefix + "_RED reason=" + redReason + fields);
            throw new AssertionError(markerPrefix + " failed: " + redReason + fields);
        }

        System.out.println(markerPrefix + "_GREEN" + fields);
    }

    private static void assertTerrainSlabsLoweredObjectSupport(MinecraftClient mc) {
        BlockPos objectPos = OBJECT_SUPPORT_POS.up();
        BlockState supportState = mc.world.getBlockState(OBJECT_SUPPORT_POS);
        BlockState objectState = mc.world.getBlockState(objectPos);
        if (!objectState.isOf(Blocks.TORCH)) {
            throw new AssertionError("expected minecraft:torch at " + objectPos + ", found " + objectState);
        }

        boolean skipOffset = CompatHooks.shouldSkipOffset(supportState);
        CompatSkipResult skipSupport = compatSkipSlabSupport(supportState);
        boolean genericSupport = SlabSupport.isSupportingSlab(supportState);
        boolean directSurface = SlabSupport.isDirectObjectSupportSurface(mc.world, OBJECT_SUPPORT_POS, supportState);
        boolean directSubject = SlabSupport.isDirectCustomSlabSupportedObject(mc.world, objectPos, objectState);
        double supportDy = SlabSupport.getYOffset(mc.world, OBJECT_SUPPORT_POS, supportState);
        double objectDy = SlabSupport.getYOffset(mc.world, objectPos, objectState);
        double modelMinY = modelMinY(mc.world, objectPos, objectState);
        VoxelShape outline = objectState.getOutlineShape(mc.world, objectPos, ShapeContext.of(mc.player));
        VoxelShape raycastShape = objectState.getRaycastShape(mc.world, objectPos);
        double outlineMinY = outline.isEmpty() ? Double.NaN : outline.getBoundingBox().minY;
        double raycastMinY = raycastShape.isEmpty() ? Double.NaN : raycastShape.getBoundingBox().minY;

        String fields = " supportId=terrainslabs:grass_slab"
                + " supportState=" + supportState
                + " supportPos=" + OBJECT_SUPPORT_POS.toShortString()
                + " subjectId=minecraft:torch"
                + " subjectPos=" + objectPos.toShortString()
                + " shouldSkipOffset=" + skipOffset
                + " shouldSkipSlabSupport=" + skipSupport
                + " isSupportingSlab=" + genericSupport
                + " directSurface=" + directSurface
                + " directSubject=" + directSubject
                + " supportDy=" + supportDy
                + " subjectDy=" + objectDy
                + " modelMinY=" + modelMinY
                + " outlineMinY=" + outlineMinY
                + " raycastShapeMinY=" + (raycastShape.isEmpty() ? "empty" : Double.toString(raycastMinY));
        System.out.println("TERRAIN_SLABS_LOWERED_OBJECT_SUPPORT_TRACE" + fields);

        String redReason = null;
        if (!skipOffset) {
            redReason = "terrain_slab_self_offset_not_skipped";
        } else if (!skipSupport.present() || !skipSupport.value()) {
            redReason = "terrain_slab_generic_support_not_skipped";
        } else if (genericSupport) {
            redReason = "terrain_slab_still_generic_support";
        } else if (!directSurface) {
            redReason = "terrain_slab_not_direct_surface";
        } else if (!directSubject) {
            redReason = "object_not_direct_custom_supported";
        } else if (Math.abs(supportDy) > 1.0e-6d) {
            redReason = "terrain_slab_self_dy_not_zero";
        } else if (Math.abs(objectDy - (-0.5d)) > 1.0e-6d) {
            redReason = "object_direct_support_dy_mismatch";
        } else if (Math.abs(modelMinY - (-0.5d)) > 1.0e-6d) {
            redReason = "object_model_dy_mismatch";
        } else if (Math.abs(outlineMinY - (-0.5d)) > 1.0e-6d) {
            redReason = "object_outline_dy_mismatch";
        } else if (!raycastShape.isEmpty() && Math.abs(raycastMinY - (-0.5d)) > 1.0e-6d) {
            redReason = "object_raycast_shape_dy_mismatch";
        }

        if (redReason != null) {
            System.out.println("TERRAIN_SLABS_LOWERED_OBJECT_SUPPORT_RED reason=" + redReason + fields);
            throw new AssertionError("Terrain Slabs lowered object support proof failed: " + redReason + fields);
        }

        System.out.println("TERRAIN_SLABS_LOWERED_OBJECT_SUPPORT_GREEN" + fields);
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState withBooleanPropertyIfPresent(BlockState state, String propertyName, boolean value) {
        for (Property<?> property : state.getProperties()) {
            if (propertyName.equals(property.getName()) && state.get((Property) property) instanceof Boolean) {
                return state.with((Property<Boolean>) property, value);
            }
        }
        return state;
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

    private static String describeShapeBounds(VoxelShape shape) {
        return shape.isEmpty() ? "empty" : shape.getBoundingBox().toString();
    }

    private static String describeVec(Vec3d vec) {
        return String.format(java.util.Locale.ROOT, "%.5f,%.5f,%.5f", vec.x, vec.y, vec.z);
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
