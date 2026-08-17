package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.client.ClientPlacementDyPrediction;
import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import com.slabbed.util.SlabSupport;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Client/server proof that authored Terrain placement is correct before attachment sync. */
public final class AuthoredTerrainPlacementClientGameTest implements FabricClientGameTest {
    private static final String ENABLED_PROPERTY = "slabbed.authoredTerrainPlacementProof";
    private static final double EPSILON = 1.0e-6d;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!Boolean.getBoolean(ENABLED_PROPERTY)) {
            return;
        }
        if (!FabricLoader.getInstance().isModLoaded(TerrainSlabsCompat.MOD_ID)) {
            throw new AssertionError("Terrain Slabs must be loaded for authored placement proof");
        }
        Block terrainSlab = TerrainSlabsTestShim.TEST_TS_SLAB;
        if (terrainSlab == Blocks.AIR || !(terrainSlab instanceof SlabBlock)) {
            throw new AssertionError("the Terrain-compatible client test slab must be registered");
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            BlockPos ground = new BlockPos(0, 199, 0);
            BlockPos vanillaBase = ground.up();
            BlockPos terrainMiddle = vanillaBase.up();
            BlockPos vanillaTop = terrainMiddle.up();
            AtomicReference<String> firstResult = new AtomicReference<>("not_run");
            AtomicReference<Double> firstImmediateDy = new AtomicReference<>(Double.NaN);
            AtomicReference<Double> firstImmediatePrediction = new AtomicReference<>(Double.NaN);
            AtomicReference<Double> firstAuthoritativeDy = new AtomicReference<>(Double.NaN);
            AtomicReference<Double> firstPredictionAfterSync = new AtomicReference<>(Double.NaN);
            AtomicReference<String> secondResult = new AtomicReference<>("not_run");
            AtomicReference<Double> secondImmediateDy = new AtomicReference<>(Double.NaN);
            AtomicReference<Double> secondImmediatePrediction = new AtomicReference<>(Double.NaN);
            AtomicReference<Double> secondAuthoritativeDy = new AtomicReference<>(Double.NaN);
            AtomicReference<Double> secondPredictionAfterSync = new AtomicReference<>(Double.NaN);

            singleplayer.getServer().runOnServer(server -> {
                var world = server.getOverworld();
                world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(vanillaBase,
                        Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        Block.NOTIFY_LISTENERS);
                world.setBlockState(terrainMiddle, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(vanillaTop, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                    var player = server.getPlayerManager().getPlayerList().get(0);
                    positionPlayer(player, vanillaBase);
                    player.setStackInHand(Hand.MAIN_HAND, new ItemStack(terrainSlab.asItem(), 4));
                }
            });
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            ctx.runOnClient(mc -> {
                if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                    firstResult.set("client_unavailable");
                    return;
                }
                positionPlayer(mc.player, vanillaBase);
                mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(terrainSlab.asItem(), 4));
                BlockHitResult hit = new BlockHitResult(
                        new Vec3d(vanillaBase.getX() + 0.5d, vanillaBase.getY() + 0.5d,
                                vanillaBase.getZ() + 0.5d),
                        Direction.UP,
                        vanillaBase,
                        false);
                ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                firstResult.set(result.toString());
                BlockState placed = mc.world.getBlockState(terrainMiddle);
                firstImmediateDy.set(SlabSupport.getVisualYOffset(mc.world, terrainMiddle, placed));
                firstImmediatePrediction.set(ClientPlacementDyPrediction.lookup(terrainMiddle));
            });

            waitTicks(ctx, 4);
            singleplayer.getClientWorld().waitForChunksRender();
            ctx.runOnClient(mc -> {
                if (mc.world != null) {
                    firstAuthoritativeDy.set(SlabPlacementDyAttachment.lookup(
                            mc.world.getChunk(terrainMiddle.getX() >> 4, terrainMiddle.getZ() >> 4),
                            terrainMiddle));
                    firstPredictionAfterSync.set(ClientPlacementDyPrediction.lookup(terrainMiddle));
                }
            });

            singleplayer.getServer().runOnServer(server -> {
                if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                    var player = server.getPlayerManager().getPlayerList().get(0);
                    positionPlayer(player, terrainMiddle);
                    player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Blocks.OAK_SLAB.asItem(), 4));
                }
            });
            ctx.waitTick();
            ctx.runOnClient(mc -> {
                if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                    secondResult.set("client_unavailable");
                    return;
                }
                positionPlayer(mc.player, terrainMiddle);
                mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Blocks.OAK_SLAB.asItem(), 4));
                BlockHitResult hit = new BlockHitResult(
                        new Vec3d(terrainMiddle.getX() + 0.5d, terrainMiddle.getY(),
                                terrainMiddle.getZ() + 0.5d),
                        Direction.UP,
                        terrainMiddle,
                        false);
                ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                secondResult.set(result.toString());
                BlockState placed = mc.world.getBlockState(vanillaTop);
                secondImmediateDy.set(SlabSupport.getVisualYOffset(mc.world, vanillaTop, placed));
                secondImmediatePrediction.set(ClientPlacementDyPrediction.lookup(vanillaTop));
            });

            waitTicks(ctx, 4);
            singleplayer.getClientWorld().waitForChunksRender();
            ctx.runOnClient(mc -> {
                if (mc.world != null) {
                    secondAuthoritativeDy.set(SlabPlacementDyAttachment.lookup(
                            mc.world.getChunk(vanillaTop.getX() >> 4, vanillaTop.getZ() >> 4),
                            vanillaTop));
                    secondPredictionAfterSync.set(ClientPlacementDyPrediction.lookup(vanillaTop));
                }
            });

            requireAccepted("Terrain placement", firstResult.get());
            requireDy("Terrain immediate visual", firstImmediateDy.get(), -0.5d);
            requireDy("Terrain immediate prediction", firstImmediatePrediction.get(), -0.5d);
            requireDy("Terrain synchronized fact", firstAuthoritativeDy.get(), -0.5d);
            requireAbsent("Terrain prediction after sync", firstPredictionAfterSync.get());
            requireAccepted("vanilla-on-Terrain placement", secondResult.get());
            requireDy("vanilla-on-Terrain immediate visual", secondImmediateDy.get(), -1.0d);
            requireDy("vanilla-on-Terrain immediate prediction", secondImmediatePrediction.get(), -1.0d);
            requireDy("vanilla-on-Terrain synchronized fact", secondAuthoritativeDy.get(), -1.0d);
            requireAbsent("vanilla-on-Terrain prediction after sync", secondPredictionAfterSync.get());
            System.out.println("[AUTHORED_TERRAIN_CLIENT] GREEN immediate_and_synced_heights_match");
        }
    }

    private static void waitTicks(ClientGameTestContext ctx, int count) {
        for (int i = 0; i < count; i++) {
            ctx.waitTick();
        }
    }

    private static void positionPlayer(net.minecraft.entity.player.PlayerEntity player, BlockPos target) {
        player.refreshPositionAndAngles(
                target.getX() + 0.5d,
                target.getY() + 0.5d - player.getStandingEyeHeight(),
                target.getZ() - 2.5d,
                0.0f,
                0.0f);
        player.setVelocity(Vec3d.ZERO);
    }

    private static void requireAccepted(String label, String result) {
        if (result == null || !result.startsWith("Success[")) {
            throw new AssertionError(label + " must succeed; got " + result);
        }
    }

    private static void requireDy(String label, double actual, double expected) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > EPSILON) {
            throw new AssertionError(label + " must be " + expected + "; got " + actual);
        }
    }

    private static void requireAbsent(String label, double actual) {
        if (!Double.isNaN(actual)) {
            throw new AssertionError(label + " must be retired; got " + actual);
        }
    }
}
