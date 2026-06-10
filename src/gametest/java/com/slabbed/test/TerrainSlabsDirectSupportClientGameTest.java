package com.slabbed.test;

import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.TorchParticleTrace;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldBuilder;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WallShape;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.EnumProperty;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Default-off MC 1.21.11 proof for Terrain Slabs direct custom support.
 */
public final class TerrainSlabsDirectSupportClientGameTest implements FabricClientGameTest {
    private static final String COMPAT_DUMP_PROPERTY = "slabbed.terrainSlabsCompatDump";
    private static final String DIRECT_SUPPORT_RED_ONLY_PROPERTY = "slabbed.terrainSlabsDirectSupportRedOnly";
    private static final String GENERATED_DOUBLE_DIRECT_SUPPORT_RED_ONLY_PROPERTY =
            "slabbed.terrainSlabsGeneratedDoubleDirectSupportRedOnly";
    private static final String EXACT_SEED_TRACE_PROPERTY = "slabbed.terrainSlabsExactSeedTrace";
    private static final String LIVE_PLACEMENT_PROPERTY = "slabbed.terrainSlabsLivePlacementProof";
    private static final String PARTICLE_PROOF_PROPERTY = "slabbed.terrainSlabsParticleProof";
    private static final String LOWERED_CUBE_CULL_PROPERTY = "slabbed.terrainSlabsLoweredCubeCullProof";
    private static final String EXACT_SEED = "681745208735773989";
    private static final BlockPos SUPPORT_POS = new BlockPos(24, 200, 0);
    private static final BlockPos VANILLA_SUPPORT_POS = SUPPORT_POS.add(4, 0, 0);
    private static final BlockPos OBJECT_SUPPORT_POS = SUPPORT_POS.add(8, 0, 0);
    private static final BlockPos CRAFTING_SUPPORT_POS = SUPPORT_POS.add(8, 0, 4);
    private static final BlockPos PUMPKIN_SUPPORT_POS = SUPPORT_POS.add(8, 0, 8);
    private static final BlockPos BOOKSHELF_SUPPORT_POS = SUPPORT_POS.add(8, 0, 12);
    private static final BlockPos STACK_TORCH_SUPPORT_POS = SUPPORT_POS.add(4, 0, 8);
    private static final BlockPos STACK_FENCE_SUPPORT_POS = SUPPORT_POS.add(4, 0, 12);
    private static final BlockPos LIVE_SUPPORT_POS = SUPPORT_POS.add(0, 0, 8);
    private static final double EPSILON = 1.0e-6d;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        boolean dump = Boolean.getBoolean(COMPAT_DUMP_PROPERTY);
        boolean proof = Boolean.getBoolean(DIRECT_SUPPORT_RED_ONLY_PROPERTY);
        boolean generatedDoubleProof = Boolean.getBoolean(GENERATED_DOUBLE_DIRECT_SUPPORT_RED_ONLY_PROPERTY);
        boolean exactSeedTrace = Boolean.getBoolean(EXACT_SEED_TRACE_PROPERTY);
        boolean livePlacement = Boolean.getBoolean(LIVE_PLACEMENT_PROPERTY);
        boolean particleProof = Boolean.getBoolean(PARTICLE_PROOF_PROPERTY);
        boolean loweredCubeCullProof = Boolean.getBoolean(LOWERED_CUBE_CULL_PROPERTY);
        boolean windowDiag = Boolean.getBoolean("slabbed.terrainSlabsWindowDiag");
        if (!dump && !proof && !generatedDoubleProof
                && !exactSeedTrace && !livePlacement && !particleProof && !loweredCubeCullProof && !windowDiag) {
            return;
        }

        if (!TerrainSlabsCompat.isLoaded()) {
            System.out.println("TERRAIN_SLABS_COMPAT_DUMP_BEGIN terrainslabs_not_loaded");
            System.out.println("TERRAIN_SLABS_COMPAT_DUMP_END total=0");
            if (proof || generatedDoubleProof) {
                throw new AssertionError("Countered Terrain Slabs mod is not loaded");
            }
            return;
        }

        TestWorldBuilder worldBuilder = ctx.worldBuilder().setUseConsistentSettings(true);
        if (exactSeedTrace) {
            worldBuilder.setUseConsistentSettings(false)
                    .adjustSettings(creator -> creator.setSeed(EXACT_SEED));
        }

        try (TestSingleplayerContext singleplayer = worldBuilder.create()) {
            ctx.waitTick();
            if (exactSeedTrace) {
                runExactSeedTrace(ctx);
            }
            if (dump) {
                ctx.runOnClient(mc -> dumpTerrainSlabsCompatFacts(mc.world));
            }
            if (windowDiag) {
                runWindowScreenshotDiagnostic(ctx, singleplayer);
            }
            if (proof) {
                runDirectSupportProof(ctx, singleplayer);
                runConnectorConnectionProof(singleplayer);
                runLoweredCubeCullProof(ctx, singleplayer);
                runCeilingHangRegressionProof(singleplayer);
                runHangerFollowLoweredProof(singleplayer);
            }
            if (loweredCubeCullProof) {
                runLoweredCubeCullProof(ctx, singleplayer);
            }
            if (generatedDoubleProof) {
                runGeneratedDoubleDirectSupportProof(ctx, singleplayer);
            }
            if (livePlacement) {
                runLivePlacementProof(ctx, singleplayer);
            }
            if (particleProof) {
                runParticleProof(ctx, singleplayer);
            }
        }
    }

    private static void runExactSeedTrace(ClientGameTestContext ctx) {
        ctx.waitTicks(40);
        ctx.runOnClient(mc -> {
            if (mc.getServer() == null) {
                throw new AssertionError("exact seed trace server missing");
            }
            long seed = mc.getServer().getOverworld().getSeed();
            String levelName = mc.getServer().getSaveProperties().getLevelName();
            System.out.println("TERRAIN_SLABS_EXACT_SEED_TRACE"
                    + " levelName=" + levelName
                    + " seed=" + seed
                    + " expectedSeed=" + EXACT_SEED
                    + " matches=" + Long.toString(seed).equals(EXACT_SEED));
            if (!Long.toString(seed).equals(EXACT_SEED)) {
                throw new AssertionError("exact seed trace expected seed " + EXACT_SEED + " but got " + seed);
            }
        });
        ctx.waitTicks(80);
    }

    private static void dumpTerrainSlabsCompatFacts(net.minecraft.world.BlockView world) {
        String terrainNamespace = loadedTerrainSlabsNamespace();
        var mod = FabricLoader.getInstance().getModContainer(terrainNamespace);
        if (mod.isPresent()) {
            var meta = mod.get().getMetadata();
            System.out.println("TERRAIN_SLABS_COMPAT_DUMP_BEGIN"
                    + " modId=" + meta.getId()
                    + " version=" + meta.getVersion().getFriendlyString()
                    + " name=" + meta.getName()
                    + " worldReady=" + (world != null));
        } else {
            System.out.println("TERRAIN_SLABS_COMPAT_DUMP_BEGIN"
                    + " modId=" + terrainNamespace
                    + " version=<unknown> name=<unknown>"
                    + " worldReady=" + (world != null));
        }

        int total = 0;
        for (Identifier id : Registries.BLOCK.getIds()) {
            if (!terrainNamespace.equals(id.getNamespace())) {
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

    private static String loadedTerrainSlabsNamespace() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded(TerrainSlabsCompat.MOD_ID)) {
            return TerrainSlabsCompat.MOD_ID;
        }
        if (loader.isModLoaded(TerrainSlabsCompat.LEGACY_MOD_ID)) {
            return TerrainSlabsCompat.LEGACY_MOD_ID;
        }
        return TerrainSlabsCompat.MOD_ID;
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
            world.setBlockState(CRAFTING_SUPPORT_POS.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(CRAFTING_SUPPORT_POS, finalTerrainSlab, Block.NOTIFY_LISTENERS);
            world.setBlockState(CRAFTING_SUPPORT_POS.up(), Blocks.CRAFTING_TABLE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(CRAFTING_SUPPORT_POS.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(PUMPKIN_SUPPORT_POS.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(PUMPKIN_SUPPORT_POS, finalTerrainSlab, Block.NOTIFY_LISTENERS);
            world.setBlockState(PUMPKIN_SUPPORT_POS.up(), Blocks.PUMPKIN.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(PUMPKIN_SUPPORT_POS.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(BOOKSHELF_SUPPORT_POS.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(BOOKSHELF_SUPPORT_POS, finalTerrainSlab, Block.NOTIFY_LISTENERS);
            world.setBlockState(BOOKSHELF_SUPPORT_POS.up(), Blocks.BOOKSHELF.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(BOOKSHELF_SUPPORT_POS.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            // stacking: torch on fence on terrain slab
            world.setBlockState(STACK_TORCH_SUPPORT_POS.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(STACK_TORCH_SUPPORT_POS, finalTerrainSlab, Block.NOTIFY_LISTENERS);
            world.setBlockState(STACK_TORCH_SUPPORT_POS.up(), Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(STACK_TORCH_SUPPORT_POS.up(2), Blocks.TORCH.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(STACK_TORCH_SUPPORT_POS.up(3), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            // stacking: fence on fence on terrain slab
            world.setBlockState(STACK_FENCE_SUPPORT_POS.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(STACK_FENCE_SUPPORT_POS, finalTerrainSlab, Block.NOTIFY_LISTENERS);
            world.setBlockState(STACK_FENCE_SUPPORT_POS.up(), Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(STACK_FENCE_SUPPORT_POS.up(2), Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(STACK_FENCE_SUPPORT_POS.up(3), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
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

            // Terrain Slabs compat contract: an opaque full cube (stone) placed on a
            // custom Terrain Slabs bottom surface must NOT be lowered. The slab stays a
            // valid direct-support SURFACE (so non-opaque objects still lower onto it),
            // but the opaque cube opts out of the -0.5 dy so its culling shape and model
            // stay in the same voxel — no see-through / missing-face terrain.
            boolean skipOffset = CompatHooks.shouldSkipOffset(supportState);
            CompatSkipResult skipSupport = compatSkipSlabSupport(supportState);
            boolean genericSupport = SlabSupport.isSupportingSlab(supportState);
            boolean directSurface = SlabSupport.isDirectObjectSupportSurface(mc.world, SUPPORT_POS, supportState);
            boolean directSubject = SlabSupport.isDirectCustomSlabSupportedObject(mc.world, objectPos, objectState);
            boolean opaqueFullCube = objectState.isOpaqueFullCube();
            double supportDy = SlabSupport.getYOffset(mc.world, SUPPORT_POS, supportState);
            double objectDy = SlabSupport.getYOffset(mc.world, objectPos, objectState);
            double modelMinY = modelMinY(mc.world, objectPos, objectState);
            VoxelShape outline = objectState.getOutlineShape(mc.world, objectPos, ShapeContext.of(mc.player));
            VoxelShape raycastShape = objectState.getRaycastShape(mc.world, objectPos);
            double outlineMinY = outline.isEmpty() ? Double.NaN : outline.getBoundingBox().minY;
            double raycastMinY = raycastShape.isEmpty() ? Double.NaN : raycastShape.getBoundingBox().minY;

            String fields = " supportId=terrainslabs:grass_slab"
                    + " supportState=" + supportState
                    + " supportPos=" + SUPPORT_POS.toShortString()
                    + " subjectId=minecraft:stone"
                    + " subjectPos=" + objectPos.toShortString()
                    + " policy=opaque_fullcube_opt_out"
                    + " opaqueFullCube=" + opaqueFullCube
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
            } else if (!directSurface) {
                redReason = "terrain_slab_not_direct_surface";
            } else if (!opaqueFullCube) {
                redReason = "subject_not_opaque_full_cube";
            } else if (directSubject) {
                redReason = "opaque_subject_unexpectedly_direct_supported";
            } else if (Math.abs(objectDy) > 1.0e-6d) {
                redReason = "opaque_subject_dy_not_zero";
            } else if (Math.abs(modelMinY) > 1.0e-6d) {
                redReason = "opaque_subject_model_dy_not_zero";
            } else if (!outline.isEmpty() && Math.abs(outlineMinY) > 1.0e-6d) {
                redReason = "opaque_subject_outline_dy_not_zero";
            } else if (!raycastShape.isEmpty() && Math.abs(raycastMinY) > 1.0e-6d) {
                redReason = "opaque_subject_raycast_shape_dy_not_zero";
            }

            if (redReason != null) {
                System.out.println("TERRAIN_SLABS_DIRECT_SUPPORT_RED reason=" + redReason + fields);
                throw new AssertionError("Terrain Slabs direct support proof failed: " + redReason + fields);
            }

            System.out.println("TERRAIN_SLABS_DIRECT_SUPPORT_GREEN" + fields);
            assertTerrainSlabsCullingOptOutControl(mc, supportState);
            assertTerrainSlabsLoweredObjectSupport(mc, OBJECT_SUPPORT_POS, Blocks.TORCH, "minecraft:torch");
            assertTerrainSlabsLoweredObjectSupport(mc, CRAFTING_SUPPORT_POS, Blocks.CRAFTING_TABLE,
                    "minecraft:crafting_table");
            assertTerrainSlabsLoweredObjectSupport(mc, PUMPKIN_SUPPORT_POS, Blocks.PUMPKIN, "minecraft:pumpkin");
            assertTerrainSlabsLoweredObjectSupport(mc, BOOKSHELF_SUPPORT_POS, Blocks.BOOKSHELF, "minecraft:bookshelf");
            // stacking: objects on top of lowered objects inherit the slab lowering
            assertStackedObjectLowered(mc, STACK_TORCH_SUPPORT_POS.up(), Blocks.OAK_FENCE, "minecraft:oak_fence");
            assertStackedObjectLowered(mc, STACK_TORCH_SUPPORT_POS.up(2), Blocks.TORCH, "minecraft:torch");
            assertStackedObjectLowered(mc, STACK_FENCE_SUPPORT_POS.up(2), Blocks.OAK_FENCE, "minecraft:oak_fence");
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
        ctx.runOnClient(mc -> assertLivePlacedOpaqueOptOut(
                mc,
                "TERRAIN_SLABS_LIVE_PLACEMENT_GENERATED_GRASS_FULL",
                "terrainslabs:grass_slab",
                supportPos,
                "minecraft:stone",
                supportPos.up(),
                stonePlacementAccepted));

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

    private static void runGeneratedDoubleDirectSupportProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        BlockPos fenceSupportPos = SUPPORT_POS.add(16, 0, 0);
        BlockPos wallSupportPos = SUPPORT_POS.add(18, 0, 0);
        BlockPos gateSupportPos = SUPPORT_POS.add(20, 0, 0);
        BlockPos gateInWallSupportPos = SUPPORT_POS.add(22, 0, 0);
        BlockPos gateBaselinePos = gateSupportPos.add(0, 1, 4);
        BlockPos gateInWallBaselinePos = gateInWallSupportPos.add(0, 1, 4);
        setGeneratedDoubleGrassSupport(ctx, singleplayer, fenceSupportPos);
        setGeneratedDoubleGrassSupport(ctx, singleplayer, wallSupportPos);
        setGeneratedDoubleGrassSupport(ctx, singleplayer, gateSupportPos);
        setGeneratedDoubleGrassSupport(ctx, singleplayer, gateInWallSupportPos);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(fenceSupportPos.up(), Blocks.SPRUCE_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(fenceSupportPos.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(wallSupportPos.up(), Blocks.ANDESITE_WALL.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(wallSupportPos.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(gateSupportPos.up(), Blocks.SPRUCE_FENCE_GATE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(gateSupportPos.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    gateInWallSupportPos.up(),
                    Blocks.SPRUCE_FENCE_GATE.getDefaultState().with(FenceGateBlock.IN_WALL, true),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(gateInWallSupportPos.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(gateBaselinePos.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(gateBaselinePos, Blocks.SPRUCE_FENCE_GATE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(gateBaselinePos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(gateInWallBaselinePos.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    gateInWallBaselinePos,
                    Blocks.SPRUCE_FENCE_GATE.getDefaultState().with(FenceGateBlock.IN_WALL, true),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(gateInWallBaselinePos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            StringBuilder failures = new StringBuilder();
            assertGeneratedDoubleSubjectCase(
                    mc,
                    "TERRAIN_SLABS_GENERATED_DOUBLE_SPRUCE_FENCE_DIRECT_SUPPORT",
                    fenceSupportPos,
                    "minecraft:spruce_fence",
                    failures);
            assertGeneratedDoubleSubjectCase(
                    mc,
                    "TERRAIN_SLABS_GENERATED_DOUBLE_ANDESITE_WALL_DIRECT_SUPPORT",
                    wallSupportPos,
                    "minecraft:andesite_wall",
                    failures);
            assertGeneratedDoubleSubjectCase(
                    mc,
                    "TERRAIN_SLABS_GENERATED_DOUBLE_SPRUCE_FENCE_GATE_DIRECT_SUPPORT",
                    gateSupportPos,
                    "minecraft:spruce_fence_gate",
                    gateBaselinePos,
                    failures);
            assertGeneratedDoubleSubjectCase(
                    mc,
                    "TERRAIN_SLABS_GENERATED_DOUBLE_SPRUCE_FENCE_GATE_IN_WALL_DIRECT_SUPPORT",
                    gateInWallSupportPos,
                    "minecraft:spruce_fence_gate",
                    gateInWallBaselinePos,
                    failures);

            if (!failures.isEmpty()) {
                throw new AssertionError("Generated double Terrain Slabs direct support proof failed: " + failures);
            }
            System.out.println("TERRAIN_SLABS_GENERATED_DOUBLE_DIRECT_SUPPORT_GREEN"
                    + " fenceSupportPos=" + fenceSupportPos.toShortString()
                    + " wallSupportPos=" + wallSupportPos.toShortString()
                    + " gateSupportPos=" + gateSupportPos.toShortString()
                    + " gateInWallSupportPos=" + gateInWallSupportPos.toShortString());
        });
    }

    private static void assertGeneratedDoubleSubjectCase(
            MinecraftClient mc,
            String markerPrefix,
            BlockPos supportPos,
            String subjectId,
            StringBuilder failures
    ) {
        assertGeneratedDoubleSubjectCase(mc, markerPrefix, supportPos, subjectId, null, failures);
    }

    private static void assertGeneratedDoubleSubjectCase(
            MinecraftClient mc,
            String markerPrefix,
            BlockPos supportPos,
            String subjectId,
            BlockPos modelBaselinePos,
            StringBuilder failures
    ) {
        try {
            assertLivePlacedSubject(
                    mc,
                    markerPrefix,
                    "terrainslabs:grass_slab",
                    supportPos,
                    subjectId,
                    supportPos.up(),
                    true,
                    true,
                    modelBaselinePos);
        } catch (AssertionError error) {
            if (!failures.isEmpty()) {
                failures.append(" | ");
            }
            failures.append(markerPrefix).append(": ").append(error.getMessage());
        }
    }

    /**
     * Proves the connector-down-slabs rule for fences, glass panes, and walls: two
     * horizontally-adjacent connectors of the same family at a uniform visual height
     * still connect, but one lowered onto a slab next to one at grid height stays a
     * single post (no connection). Deterministic: recomputes the EAST connection
     * through {@code getStateForNeighborUpdate} (which carries the Slabbed mixin) and
     * reads the resulting property.
     */
    /**
     * Builds a small terrace (lowered crafting tables beside grid-height crafting tables /
     * terrain) and screenshots it, so the actual rendered result can be inspected for the
     * see-through "window" on the live vanilla chunk render path.
     */
    private static void runWindowScreenshotDiagnostic(ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        BlockState slab = terrainBlock("grass_slab").getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState finalSlab = slab;
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = -4; x <= 6; x++) {
                for (int y = 86; y <= 108; y++) {
                    for (int z = -4; z <= 6; z++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
            // Solid grass/stone platform.
            for (int x = -3; x <= 3; x++) {
                for (int z = -4; z <= 4; z++) {
                    for (int y = 95; y <= 99; y++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                    world.setBlockState(new BlockPos(x, 100, z), Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
            // Lowered crafting table; one block NORTH a grid-height grass cube stands a half-block
            // taller. The lowering empties the crafting table's upper voxel-half, exposing the
            // grid cube's south face there -- which vanilla culls -> the see-through "window".
            world.setBlockState(new BlockPos(0, 100, 0), finalSlab, Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(0, 101, 0), Blocks.CRAFTING_TABLE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(0, 101, -1), Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(-1, 101, -1), Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(1, 101, -1), Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
        for (int i = 0; i < 6; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            mc.player.getAbilities().flying = true;
            mc.player.setNoGravity(true);
            mc.player.refreshPositionAndAngles(0.5, 102.0, 3.0, 180.0f, 38.0f);
            mc.player.setVelocity(0.0, 0.0, 0.0);
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            mc.player.refreshPositionAndAngles(0.5, 102.0, 3.0, 180.0f, 38.0f);
            mc.player.setVelocity(0.0, 0.0, 0.0);
        });
        ctx.waitTick();
        ctx.takeScreenshot("terrain_slabs_window_diag");
        System.out.println("TERRAIN_SLABS_WINDOW_DIAG_SHOT taken");
    }

    private static void runConnectorConnectionProof(TestSingleplayerContext singleplayer) {
        Block terrainSlabBlock = terrainBlock("grass_slab");
        BlockState terrainSlab = terrainSlabBlock.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState finalTerrainSlab = terrainSlab;
        singleplayer.getServer().runOnServer(server -> {
            ServerWorld world = server.getOverworld();
            List<String> failures = new ArrayList<>();
            checkConnectorScenarios(world, failures, "minecraft:oak_fence", Blocks.OAK_FENCE, finalTerrainSlab, -8);
            checkConnectorScenarios(world, failures, "minecraft:glass_pane", Blocks.GLASS_PANE, finalTerrainSlab, -16);
            checkConnectorScenarios(world, failures, "minecraft:cobblestone_wall", Blocks.COBBLESTONE_WALL,
                    finalTerrainSlab, -24);
            if (!failures.isEmpty()) {
                String joined = String.join("; ", failures);
                System.out.println("TERRAIN_SLABS_CONNECTOR_CONNECTION_RED " + joined);
                throw new AssertionError("Terrain Slabs connector connection rule failed: " + joined);
            }
            System.out.println("TERRAIN_SLABS_CONNECTOR_CONNECTION_GREEN "
                    + "fence/pane/wall: flat=connect step=single_post ground=connect");
        });
    }

    private static void checkConnectorScenarios(
            ServerWorld world, List<String> failures, String id, Block connector,
            BlockState terrainSlab, int baseZ) {
        // Flat run on custom slabs: both lowered to the same height -> connect.
        checkConnectorPair(world, failures, id + "/flat_custom_slabs", connector,
                new BlockPos(24, 200, baseZ), terrainSlab,
                new BlockPos(25, 200, baseZ), terrainSlab, true);
        // Stepped: lowered onto a custom slab beside one on a full block -> single posts.
        checkConnectorPair(world, failures, id + "/step_custom_slab_to_fullblock", connector,
                new BlockPos(24, 200, baseZ - 2), terrainSlab,
                new BlockPos(25, 200, baseZ - 2), Blocks.GRASS_BLOCK.getDefaultState(), false);
        // Flat run on the ground (vanilla baseline): both at grid height -> connect.
        checkConnectorPair(world, failures, id + "/flat_ground", connector,
                new BlockPos(24, 200, baseZ - 4), Blocks.GRASS_BLOCK.getDefaultState(),
                new BlockPos(25, 200, baseZ - 4), Blocks.GRASS_BLOCK.getDefaultState(), true);
    }

    private static void checkConnectorPair(
            ServerWorld world, List<String> failures, String label, Block connector,
            BlockPos supportA, BlockState supportAState,
            BlockPos supportB, BlockState supportBState, boolean expectConnected) {
        world.setBlockState(supportA.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(supportB.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(supportA, supportAState, Block.NOTIFY_ALL);
        world.setBlockState(supportB, supportBState, Block.NOTIFY_ALL);
        BlockPos connA = supportA.up();
        BlockPos connB = supportB.up();
        world.setBlockState(connA.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(connB.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(connA, connector.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(connB, connector.getDefaultState(), Block.NOTIFY_ALL);

        Direction dir = Direction.EAST; // connB sits east of connA
        BlockState aState = world.getBlockState(connA);
        BlockState bState = world.getBlockState(connB);
        BlockState recomputed = aState.getStateForNeighborUpdate(
                world, world, connA, dir, connB, bState, world.getRandom());
        boolean connected = connectedEast(recomputed);

        double dyA = SlabSupport.getYOffset(world, connA, aState);
        double dyB = SlabSupport.getYOffset(world, connB, bState);
        String fields = " label=" + label
                + " connA=" + connA.toShortString() + " connB=" + connB.toShortString()
                + " supportA=" + Registries.BLOCK.getId(supportAState.getBlock())
                + " supportB=" + Registries.BLOCK.getId(supportBState.getBlock())
                + " dyA=" + dyA + " dyB=" + dyB
                + " expectedConnected=" + expectConnected + " actualConnected=" + connected;
        System.out.println("TERRAIN_SLABS_CONNECTOR_CONNECTION_TRACE" + fields);
        if (connected != expectConnected) {
            failures.add(label + "(expected=" + expectConnected + " actual=" + connected + ")");
        }
    }

    private static boolean connectedEast(BlockState state) {
        BooleanProperty boolProp = ConnectingBlock.FACING_PROPERTIES.get(Direction.EAST);
        if (state.contains(boolProp)) {
            return state.get(boolProp);
        }
        EnumProperty<WallShape> wallProp = WallBlock.WALL_SHAPE_PROPERTIES_BY_DIRECTION.get(Direction.EAST);
        if (state.contains(wallProp)) {
            return state.get(wallProp) != WallShape.NONE;
        }
        return false;
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
        ctx.runOnClient(mc -> assertLivePlacedOpaqueOptOut(
                mc,
                "TERRAIN_SLABS_LIVE_PLACEMENT_FULL",
                "terrainslabs:" + terrainPath,
                supportPos,
                "minecraft:stone",
                stonePos,
                stonePlacementAccepted));

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

    private static void setGeneratedDoubleGrassSupport(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos supportPos
    ) {
        Block grassSlab = terrainBlock("grass_slab");
        BlockState state = grassSlab.getDefaultState();
        state = state.with(SlabBlock.TYPE, SlabType.DOUBLE);
        state = withBooleanPropertyIfPresent(state, "generated", true);
        state = withBooleanPropertyIfPresent(state, "snowy", false);
        state = withBooleanPropertyIfPresent(state, "waterlogged", false);
        BlockState generatedDoubleGrass = state;
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(supportPos.down(), Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(supportPos, generatedDoubleGrass, Block.NOTIFY_LISTENERS);
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
        assertLivePlacedSubject(
                mc,
                markerPrefix,
                supportId,
                supportPos,
                subjectId,
                subjectPos,
                subjectPlacementAccepted,
                expectCustomSupport,
                null);
    }

    private static void assertLivePlacedSubject(
            MinecraftClient mc,
            String markerPrefix,
            String supportId,
            BlockPos supportPos,
            String subjectId,
            BlockPos subjectPos,
            boolean subjectPlacementAccepted,
            boolean expectCustomSupport,
            BlockPos modelBaselinePos
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
        BlockState baselineState = modelBaselinePos == null ? null : mc.world.getBlockState(modelBaselinePos);
        boolean baselineSubjectMatches = modelBaselinePos == null
                || Registries.BLOCK.getId(baselineState.getBlock()).toString().equals(subjectId);
        double baselineModelMinY = modelBaselinePos != null && baselineSubjectMatches
                ? modelMinY(mc.world, modelBaselinePos, baselineState)
                : Double.NaN;
        double modelDyDelta = modelBaselinePos != null && subjectIsExpected && baselineSubjectMatches
                ? modelMinY - baselineModelMinY
                : Double.NaN;
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
                + " modelBaselinePos=" + (modelBaselinePos == null ? "none" : modelBaselinePos.toShortString())
                + " modelBaselineState=" + (baselineState == null ? "none" : baselineState.toString())
                + " modelBaselineMinY=" + baselineModelMinY
                + " modelDyDelta=" + modelDyDelta
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
        } else if (modelBaselinePos != null && !baselineSubjectMatches) {
            redReason = "subject_model_baseline_mismatch";
        } else if (modelBaselinePos != null && Math.abs(modelDyDelta - subjectDy) > EPSILON) {
            redReason = "subject_model_dy_delta_mismatch";
        } else if (modelBaselinePos == null && Math.abs(modelMinY - (-0.5d)) > EPSILON) {
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

    /**
     * Live-placement proof for the Terrain Slabs opaque-full-cube opt-out: an opaque
     * full cube (e.g. minecraft:stone) placed on a custom Terrain Slabs bottom surface
     * must stay at grid height (dy=0) and must NOT be a direct custom-supported subject,
     * while the slab remains a valid direct-support surface for non-opaque objects.
     * This is what keeps lowered opaque cubes from producing see-through / missing-face
     * terrain (the beta 4.1 culling blocker).
     */
    private static void assertLivePlacedOpaqueOptOut(
            MinecraftClient mc,
            String markerPrefix,
            String supportId,
            BlockPos supportPos,
            String subjectId,
            BlockPos subjectPos,
            boolean subjectPlacementAccepted
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
        boolean opaqueFullCube = subjectIsExpected && subjectState.isOpaqueFullCube();
        double supportDy = SlabSupport.getYOffset(mc.world, supportPos, supportState);
        double subjectDy = SlabSupport.getYOffset(mc.world, subjectPos, subjectState);
        double modelMinY = subjectIsExpected ? modelMinY(mc.world, subjectPos, subjectState) : Double.NaN;
        VoxelShape outline = subjectState.getOutlineShape(mc.world, subjectPos, ShapeContext.of(mc.player));
        VoxelShape raycastShape = subjectState.getRaycastShape(mc.world, subjectPos);
        double outlineMinY = outline.isEmpty() ? Double.NaN : outline.getBoundingBox().minY;
        double raycastMinY = raycastShape.isEmpty() ? Double.NaN : raycastShape.getBoundingBox().minY;

        String fields = " supportId=" + supportId
                + " supportState=" + supportState
                + " supportProperties=" + describeProperties(supportState)
                + " supportPos=" + supportPos.toShortString()
                + " subjectId=" + subjectId
                + " subjectState=" + subjectState
                + " subjectPos=" + subjectPos.toShortString()
                + " policy=opaque_fullcube_opt_out"
                + " subjectPlacementAccepted=" + subjectPlacementAccepted
                + " supportMatches=" + supportIsExpected
                + " subjectMatches=" + subjectIsExpected
                + " opaqueFullCube=" + opaqueFullCube
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
                + " raycastShapeMinY=" + (raycastShape.isEmpty() ? "empty" : Double.toString(raycastMinY));
        System.out.println(markerPrefix + "_TRACE" + fields);

        String redReason = null;
        if (!supportIsExpected) {
            redReason = "support_placement_state_mismatch";
        } else if (!subjectPlacementAccepted) {
            redReason = "subject_placement_rejected";
        } else if (!subjectIsExpected) {
            redReason = "subject_placement_state_mismatch";
        } else if (!opaqueFullCube) {
            redReason = "subject_not_opaque_full_cube";
        } else if (!skipOffset) {
            redReason = "terrain_slab_self_offset_not_skipped";
        } else if (!skipSupport.present() || !skipSupport.value()) {
            redReason = "terrain_slab_generic_support_not_skipped";
        } else if (genericSupport) {
            redReason = "terrain_slab_still_generic_support";
        } else if (surfaceKind != CompatSlabSurfaceKind.BOTTOM_LIKE) {
            redReason = "support_not_bottom_like_custom_surface";
        } else if (!directSurface) {
            redReason = "support_not_direct_surface";
        } else if (Math.abs(supportDy) > 1.0e-6d) {
            redReason = "support_dy_not_zero";
        } else if (directSubject) {
            redReason = "opaque_subject_unexpectedly_direct_supported";
        } else if (Math.abs(subjectDy) > 1.0e-6d) {
            redReason = "opaque_subject_dy_not_zero";
        } else if (Math.abs(modelMinY) > 1.0e-6d) {
            redReason = "opaque_subject_model_dy_not_zero";
        } else if (!outline.isEmpty() && Math.abs(outlineMinY) > 1.0e-6d) {
            redReason = "opaque_subject_outline_dy_not_zero";
        } else if (!raycastShape.isEmpty() && Math.abs(raycastMinY) > 1.0e-6d) {
            redReason = "opaque_subject_raycast_shape_dy_not_zero";
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

    private static void assertStackedObjectLowered(
            MinecraftClient mc, BlockPos objectPos, Block expectedBlock, String subjectId) {
        BlockState objectState = mc.world.getBlockState(objectPos);
        if (!objectState.isOf(expectedBlock)) {
            throw new AssertionError("expected " + subjectId + " at " + objectPos + ", found " + objectState);
        }
        boolean directSubject = SlabSupport.isDirectCustomSlabSupportedObject(mc.world, objectPos, objectState);
        double objectDy = SlabSupport.getYOffset(mc.world, objectPos, objectState);
        double modelMinY = modelMinY(mc.world, objectPos, objectState);
        String fields = " subjectId=" + subjectId
                + " subjectPos=" + objectPos.toShortString()
                + " supportBelow=" + Registries.BLOCK.getId(mc.world.getBlockState(objectPos.down()).getBlock())
                + " directSubject=" + directSubject
                + " subjectDy=" + objectDy
                + " modelMinY=" + modelMinY;
        System.out.println("TERRAIN_SLABS_STACKED_OBJECT_TRACE" + fields);

        String redReason = null;
        if (!directSubject) {
            redReason = "stacked_object_not_direct_supported";
        } else if (Math.abs(objectDy - (-0.5d)) > 1.0e-6d) {
            redReason = "stacked_object_dy_mismatch";
        } else if (Math.abs(modelMinY - (-0.5d)) > 1.0e-6d) {
            redReason = "stacked_object_model_dy_mismatch";
        }

        if (redReason != null) {
            System.out.println("TERRAIN_SLABS_STACKED_OBJECT_RED reason=" + redReason + fields);
            throw new AssertionError("Terrain Slabs stacked object proof failed: " + redReason + fields);
        }
        System.out.println("TERRAIN_SLABS_STACKED_OBJECT_GREEN" + fields);
    }

    private static void assertTerrainSlabsLoweredObjectSupport(
            MinecraftClient mc, BlockPos supportPos, Block expectedBlock, String subjectId) {
        BlockPos objectPos = supportPos.up();
        BlockState supportState = mc.world.getBlockState(supportPos);
        BlockState objectState = mc.world.getBlockState(objectPos);
        if (!objectState.isOf(expectedBlock)) {
            throw new AssertionError("expected " + subjectId + " at " + objectPos + ", found " + objectState);
        }

        boolean skipOffset = CompatHooks.shouldSkipOffset(supportState);
        CompatSkipResult skipSupport = compatSkipSlabSupport(supportState);
        boolean genericSupport = SlabSupport.isSupportingSlab(supportState);
        boolean directSurface = SlabSupport.isDirectObjectSupportSurface(mc.world, supportPos, supportState);
        boolean directSubject = SlabSupport.isDirectCustomSlabSupportedObject(mc.world, objectPos, objectState);
        double supportDy = SlabSupport.getYOffset(mc.world, supportPos, supportState);
        double objectDy = SlabSupport.getYOffset(mc.world, objectPos, objectState);
        double modelMinY = modelMinY(mc.world, objectPos, objectState);
        VoxelShape outline = objectState.getOutlineShape(mc.world, objectPos, ShapeContext.of(mc.player));
        VoxelShape raycastShape = objectState.getRaycastShape(mc.world, objectPos);
        double outlineMinY = outline.isEmpty() ? Double.NaN : outline.getBoundingBox().minY;
        double raycastMinY = raycastShape.isEmpty() ? Double.NaN : raycastShape.getBoundingBox().minY;

        String fields = " supportId=terrainslabs:grass_slab"
                + " supportState=" + supportState
                + " supportPos=" + supportPos.toShortString()
                + " subjectId=" + subjectId
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
        return modelProbe(world, pos, state, direction -> false).minY();
    }

    /**
     * Proves the lowered-cube cull fix at the predicate that Indigo's {@code BlockRenderInfo}
     * actually consults ({@link SlabSupport#isSlabHeightStepFace}). In 1.21.11 chunk meshing
     * the opaque-face cull gate is {@code BlockRenderInfo.shouldDrawSide/shouldCullSide};
     * neither the FRAPI model cullTest nor {@code BlockModelRenderer.shouldDrawFace} is the
     * gate for chunks, so the proof exercises the predicate the mixin injects there.
     * <ul>
     *   <li>STEP — a lowered opaque cube (pumpkin on a slab) beside a grid-height cube:
     *       its stepped side face must be redrawn (vanilla would cull it).</li>
     *   <li>FLAT — two cubes lowered the same amount: the flush face stays culled (no
     *       overdraw).</li>
     *   <li>MIRROR — a grid-height opaque cube (stone) beside a lowered object (crafting
     *       table): the cube's facing side must be redrawn. This is the exact geometry of
     *       the see-through "window" — the crafting table is not a full cube, so the window
     *       is always closed on the neighbouring opaque cube.</li>
     *   <li>MODEL_PATH — Sodium/FRAPI-style model culling must follow the same
     *       STEP/FLAT/MIRROR rule; the Indigo {@code BlockRenderInfo} hook is not used
     *       by every renderer in the live Modrinth profile.</li>
     * </ul>
     */
    private static void runLoweredCubeCullProof(ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        Block terrainSlabBlock = terrainBlock("grass_slab");
        BlockState finalSlab = terrainSlabBlock.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockPos stepSlab = SUPPORT_POS.add(12, 0, 0);
        BlockPos flatSlab = SUPPORT_POS.add(12, 0, 4);
        BlockPos mirrorSlab = SUPPORT_POS.add(12, 0, 8);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            // STEP: pumpkin (opaque cube) lowered onto a slab; east neighbour at grid height; air below-east.
            world.setBlockState(stepSlab, finalSlab, Block.NOTIFY_LISTENERS);
            world.setBlockState(stepSlab.up(), Blocks.PUMPKIN.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(stepSlab.east(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(stepSlab.up().east(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            // FLAT: pumpkin lowered; east neighbour pumpkin lowered the same amount.
            world.setBlockState(flatSlab, finalSlab, Block.NOTIFY_LISTENERS);
            world.setBlockState(flatSlab.up(), Blocks.PUMPKIN.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(flatSlab.east(), finalSlab, Block.NOTIFY_LISTENERS);
            world.setBlockState(flatSlab.east().up(), Blocks.PUMPKIN.getDefaultState(), Block.NOTIFY_LISTENERS);
            // MIRROR: grid-height stone beside a lowered crafting table (the window geometry).
            world.setBlockState(mirrorSlab, finalSlab, Block.NOTIFY_LISTENERS);
            world.setBlockState(mirrorSlab.up(), Blocks.CRAFTING_TABLE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(mirrorSlab.east(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(mirrorSlab.up().east(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            BlockPos stepCube = stepSlab.up();
            BlockPos flatCube = flatSlab.up();
            BlockPos mirrorCube = mirrorSlab.up().east();
            BlockState stepState = mc.world.getBlockState(stepCube);
            BlockState flatState = mc.world.getBlockState(flatCube);
            BlockState mirrorState = mc.world.getBlockState(mirrorCube);
            double stepDy = SlabSupport.getYOffset(mc.world, stepCube, stepState);
            double mirrorDy = SlabSupport.getYOffset(mc.world, mirrorCube, mirrorState);
            double mirrorNeighborDy = SlabSupport.getYOffset(mc.world, mirrorCube.west(),
                    mc.world.getBlockState(mirrorCube.west()));
            // The actual chunk cull gate: Indigo's BlockRenderInfo.shouldDrawSide consults this predicate.
            boolean stepDraw = SlabSupport.isSlabHeightStepFace(mc.world, stepCube, stepState, Direction.EAST);
            boolean flatDraw = SlabSupport.isSlabHeightStepFace(mc.world, flatCube, flatState, Direction.EAST);
            boolean mirrorDraw = SlabSupport.isSlabHeightStepFace(mc.world, mirrorCube, mirrorState, Direction.WEST);
            FaceProbe stepEastFaces = probeFacesToward(
                    mc.world, stepCube, stepState, dir -> dir == Direction.EAST, Direction.EAST);
            FaceProbe flatEastFaces = probeFacesToward(
                    mc.world, flatCube, flatState, dir -> dir == Direction.EAST, Direction.EAST);
            FaceProbe mirrorWestFaces = probeFacesToward(
                    mc.world, mirrorCube, mirrorState, dir -> dir == Direction.WEST, Direction.WEST);
            String fields = " stepCube=" + stepCube.toShortString() + " stepDy=" + stepDy + " stepDraw=" + stepDraw
                    + " stepEastFaces=" + stepEastFaces.targetFaces()
                    + " stepEastCulledFaces=" + stepEastFaces.culledFaces()
                    + " stepEastUnculledFaces=" + stepEastFaces.unculledFaces()
                    + " stepEastNominalFaces=" + stepEastFaces.nominalFaces()
                    + " flatDraw=" + flatDraw + " flatEastFaces=" + flatEastFaces.targetFaces()
                    + " flatEastCulledFaces=" + flatEastFaces.culledFaces()
                    + " mirrorCube=" + mirrorCube.toShortString() + " mirrorDy=" + mirrorDy
                    + " mirrorNeighborDy=" + mirrorNeighborDy + " mirrorDraw=" + mirrorDraw
                    + " mirrorWestFaces=" + mirrorWestFaces.targetFaces()
                    + " mirrorWestCulledFaces=" + mirrorWestFaces.culledFaces()
                    + " mirrorWestUnculledFaces=" + mirrorWestFaces.unculledFaces()
                    + " mirrorWestNominalFaces=" + mirrorWestFaces.nominalFaces();
            System.out.println("TERRAIN_SLABS_LOWERED_CUBE_CULL_TRACE" + fields);

            String redReason = null;
            if (!stepState.isOf(Blocks.PUMPKIN) || !flatState.isOf(Blocks.PUMPKIN)) {
                redReason = "pumpkin_state_mismatch";
            } else if (Math.abs(stepDy - (-0.5d)) > 1.0e-6d) {
                redReason = "stepped_cube_not_lowered";
            } else if (!stepDraw) {
                redReason = "stepped_exposed_face_culled";
            } else if (flatDraw) {
                redReason = "flat_flush_face_overdrawn";
            } else if (!mirrorState.isOf(Blocks.STONE) || Math.abs(mirrorDy) > 1.0e-6d) {
                redReason = "mirror_grid_cube_mismatch";
            } else if (Math.abs(mirrorNeighborDy - (-0.5d)) > 1.0e-6d) {
                redReason = "mirror_neighbor_not_lowered";
            } else if (!mirrorDraw) {
                redReason = "mirror_grid_face_culled";
            } else if (stepEastFaces.targetFaces() <= 0) {
                redReason = "stepped_model_face_culled";
            } else if (flatEastFaces.targetFaces() != 0) {
                redReason = "flat_model_face_overdrawn";
            } else if (mirrorWestFaces.targetFaces() <= 0) {
                redReason = "mirror_model_face_culled";
            } else if (stepEastFaces.culledFaces() > 0) {
                redReason = "stepped_model_face_kept_cullface";
            } else if (stepEastFaces.nominalFaces() != stepEastFaces.targetFaces()) {
                redReason = "stepped_model_face_nominal_not_preserved";
            } else if (mirrorWestFaces.culledFaces() > 0) {
                redReason = "mirror_model_face_kept_cullface";
            } else if (mirrorWestFaces.nominalFaces() != mirrorWestFaces.targetFaces()) {
                redReason = "mirror_model_face_nominal_not_preserved";
            }
            if (redReason != null) {
                System.out.println("TERRAIN_SLABS_LOWERED_CUBE_CULL_RED reason=" + redReason + fields);
                throw new AssertionError("Terrain Slabs lowered cube cull proof failed: " + redReason + fields);
            }
            System.out.println("TERRAIN_SLABS_LOWERED_CUBE_CULL_GREEN" + fields);
        });
    }

    private record FaceProbe(
            int targetFaces,
            int culledFaces,
            int unculledFaces,
            int nominalFaces) {
    }

    private static FaceProbe probeFacesToward(
            net.minecraft.world.BlockRenderView world, BlockPos pos, BlockState state,
            java.util.function.Predicate<Direction> cullTest, Direction face) {
        BlockStateModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
        if (!(model instanceof FabricBlockStateModel fabricModel)) {
            throw new AssertionError("expected FabricBlockStateModel for " + state);
        }
        Renderer renderer = Renderer.get();
        if (renderer == null) {
            throw new AssertionError("Fabric renderer missing for lowered cube cull proof");
        }
        MutableMesh mesh = renderer.mutableMesh();
        fabricModel.emitQuads(mesh.emitter(), world, pos, state, Random.create(0x51abbEDL), cullTest);
        int[] targetFaces = {0};
        int[] culledFaces = {0};
        int[] unculledFaces = {0};
        int[] nominalFaces = {0};
        mesh.forEach(quad -> {
            Direction cullFace = quad.cullFace();
            Direction nominalFace = quad.nominalFace();
            Direction quadFace = cullFace != null ? cullFace : nominalFace;
            if (quadFace == face) {
                targetFaces[0]++;
                if (cullFace == face) {
                    culledFaces[0]++;
                } else {
                    unculledFaces[0]++;
                }
                if (nominalFace == face) {
                    nominalFaces[0]++;
                }
            }
        });
        return new FaceProbe(
                targetFaces[0],
                culledFaces[0],
                unculledFaces[0],
                nominalFaces[0]);
    }

    /**
     * Regression: hanging things under a (vanilla) top slab still float up +0.5 to the
     * underside. The Terrain Slabs stacking/cull work must not disturb ceiling support.
     */
    private static void runCeilingHangRegressionProof(TestSingleplayerContext singleplayer) {
        BlockPos topSlabPos = SUPPORT_POS.add(12, 0, 12);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(topSlabPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    Block.NOTIFY_LISTENERS);
            BlockPos hangPos = topSlabPos.down();
            world.setBlockState(hangPos, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(hangPos.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            BlockState hangState = world.getBlockState(hangPos);
            boolean ceilingAttached = SlabSupport.isCeilingAttached(hangState);
            double hangDy = SlabSupport.getYOffset(world, hangPos, hangState);
            String fields = " hangPos=" + hangPos.toShortString()
                    + " ceilingAttached=" + ceilingAttached + " hangDy=" + hangDy;
            System.out.println("TERRAIN_SLABS_CEILING_HANG_TRACE" + fields);
            String redReason = null;
            if (!hangState.isOf(Blocks.LANTERN)) {
                redReason = "lantern_state_mismatch";
            } else if (!ceilingAttached) {
                redReason = "lantern_not_ceiling_attached";
            } else if (Math.abs(hangDy - 0.5d) > 1.0e-6d) {
                redReason = "lantern_not_raised_to_top_slab";
            }
            if (redReason != null) {
                System.out.println("TERRAIN_SLABS_CEILING_HANG_RED reason=" + redReason + fields);
                throw new AssertionError("Terrain Slabs ceiling hang regression failed: " + redReason + fields);
            }
            System.out.println("TERRAIN_SLABS_CEILING_HANG_GREEN" + fields);
        });
    }

    /**
     * Regression: a decorative hanger (lantern / spore blossom / hanging roots) placed under
     * a block that Slabbed lowered (anchored onto a slab) must inherit that block's −0.5 so it
     * hangs cleanly from the lowered underside instead of clipping into it. Chains are excluded
     * (they keep clipping to connect). Top-slab adherence (+0.5) and plain blocks (0) unchanged.
     */
    private static void runHangerFollowLoweredProof(TestSingleplayerContext singleplayer) {
        BlockState lantern = Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true);
        BlockState spore = Blocks.SPORE_BLOSSOM.getDefaultState();
        BlockState roots = Blocks.HANGING_ROOTS.getDefaultState();
        BlockState chain = Registries.BLOCK.get(Identifier.of("minecraft", "chain")).getDefaultState();
        BlockState topSlab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
        BlockState bottomSlab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockPos base = SUPPORT_POS.add(16, 0, 0);
        singleplayer.getServer().runOnServer(server -> {
            ServerWorld world = server.getOverworld();
            List<String> failures = new ArrayList<>();
            BlockState[] hangers = {lantern, chain, spore, roots};
            String[] names = {"lantern", "chain", "spore", "roots"};
            double[] expected = {-0.5, 0.0, -0.5, -0.5};
            for (int i = 0; i < hangers.length; i++) {
                BlockPos sup = base.add(0, 1, i * 3);
                BlockPos hp = base.add(0, 0, i * 3);
                // Anchor the support lowered (place on a bottom slab, anchor, remove slab), then hang under it.
                world.setBlockState(hp, bottomSlab, Block.NOTIFY_LISTENERS);
                world.setBlockState(sup, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                com.slabbed.anchor.SlabAnchorAttachment.addAnchor(world, sup, Blocks.STONE.getDefaultState());
                world.setBlockState(hp, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(hp, hangers[i], Block.NOTIFY_LISTENERS);
                double supDy = SlabSupport.getYOffset(world, sup, world.getBlockState(sup));
                double hDy = SlabSupport.getYOffset(world, hp, world.getBlockState(hp));
                if (Math.abs(supDy - (-0.5d)) > 1.0e-6d) {
                    failures.add(names[i] + "_support_not_lowered(supDy=" + supDy + ")");
                }
                if (Math.abs(hDy - expected[i]) > 1.0e-6d) {
                    failures.add(names[i] + "_hangerDy=" + hDy + "_expected=" + expected[i]);
                }
            }
            // control: lantern under a plain (non-lowered) full block -> 0
            BlockPos cSup = base.add(0, 1, 12);
            BlockPos cHp = base.add(0, 0, 12);
            world.setBlockState(cSup, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(cHp, lantern, Block.NOTIFY_LISTENERS);
            double ctrlDy = SlabSupport.getYOffset(world, cHp, world.getBlockState(cHp));
            if (Math.abs(ctrlDy) > 1.0e-6d) {
                failures.add("control_lantern_full_block_dy=" + ctrlDy);
            }
            // top-slab adherence -> +0.5
            BlockPos tSup = base.add(0, 1, 15);
            BlockPos tHp = base.add(0, 0, 15);
            world.setBlockState(tSup, topSlab, Block.NOTIFY_LISTENERS);
            world.setBlockState(tHp, lantern, Block.NOTIFY_LISTENERS);
            double topDy = SlabSupport.getYOffset(world, tHp, world.getBlockState(tHp));
            if (Math.abs(topDy - 0.5d) > 1.0e-6d) {
                failures.add("lantern_top_slab_dy=" + topDy);
            }
            if (!failures.isEmpty()) {
                String joined = String.join("; ", failures);
                System.out.println("TERRAIN_SLABS_HANGER_FOLLOW_RED " + joined);
                throw new AssertionError("Terrain Slabs hanger follow-lowered proof failed: " + joined);
            }
            System.out.println("TERRAIN_SLABS_HANGER_FOLLOW_GREEN lantern/spore/roots follow -0.5, "
                    + "chain stays 0, control(full)=0, topSlab=+0.5");
        });
    }

    private static ModelProbe modelProbe(
            net.minecraft.world.BlockRenderView world,
            BlockPos pos,
            BlockState state,
            java.util.function.Predicate<Direction> cullTest
    ) {
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
        fabricModel.emitQuads(mesh.emitter(), world, pos, state, Random.create(0x51abbEDL), cullTest);
        if (mesh.size() <= 0) {
            throw new AssertionError("no model quads emitted for " + state + " at " + pos);
        }
        ModelBounds bounds = new ModelBounds();
        mesh.forEach(bounds::accept);
        return new ModelProbe(bounds.minY, mesh.size());
    }

    private static Block terrainBlock(String path) {
        Identifier id = Identifier.of(loadedTerrainSlabsNamespace(), path);
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

    private record ModelProbe(double minY, int quads) {
    }

    private static final class ModelBounds {
        double minY = Double.POSITIVE_INFINITY;

        void accept(QuadView quad) {
            minY = Math.min(minY, Math.min(Math.min(quad.y(0), quad.y(1)), Math.min(quad.y(2), quad.y(3))));
        }
    }
}
