package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.test.support.TestConnections;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.core.Direction.Axis;
import com.slabbed.util.SlabSupport;

/** Connected integrated-server movement, distinct from mock-player collision tests. */
public final class CarpetMovementClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().setUseConsistentSettings(true).create()) {
            TestConnections.waitForChunksDownload(world);
            for (int lane = 0; lane < 4; lane++) checkLane(context, world, lane);
            Slabbed.LOGGER.info("CLIENT_GAMETEST | CarpetMovementClientGameTest | PASS cases=4");
        }
    }

    private static double broadphaseTop(net.minecraft.world.entity.player.Player player) {
        double top = Double.NEGATIVE_INFINITY;
        for (var shape : player.level().getBlockCollisions(player, player.getBoundingBox().move(0, -.1, 0))) {
            if (!shape.isEmpty()) top = Math.max(top, shape.max(Axis.Y));
        }
        return top;
    }

    private static void checkLane(ClientGameTestContext context, TestSingleplayerContext world, int lane) {
        String name = new String[] {"moss_slab", "wool_slab", "bare_slab", "moss_full"}[lane];
        double top = lane == 3 ? 1.0625d : lane == 2 ? .5d : .5625d;
        BlockPos origin = world.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = server.overworld();
            player.setGameMode(GameType.SURVIVAL);
            BlockPos p = player.blockPosition().above(6).immutable();
            for (BlockPos clear : BlockPos.betweenClosed(p.offset(-2, -2, -2), p.offset(2, 5, 14))) {
                level.setBlock(clear, Blocks.AIR.defaultBlockState(), 3);
            }
            var carpet = lane == 1 ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                    net.minecraft.resources.Identifier.withDefaultNamespace("white_carpet")) : Blocks.MOSS_CARPET;
            for (int z = 0; z < 13; z++) {
                BlockPos support = p.south(z);
                level.setBlock(support, lane == 3 ? Blocks.STONE.defaultBlockState()
                        : Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState(), 3);
                if (lane != 2) {
                    ItemStack stack = new ItemStack(carpet);
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(new Vec3(support.getX() + .5,
                                    support.getY() + (lane == 3 ? 1 : .5), support.getZ() + .5),
                                    Direction.UP, support, false)));
                    if (!result.consumesAction() || !level.getBlockState(support.above()).is(carpet)) {
                        throw new AssertionError(name + ": real carpet placement failed");
                    }
                }
            }
            for (int z = 0; z < 13; z++) {
                BlockPos support = p.south(z);
                var supportState = level.getBlockState(support);
                if (!supportState.is(lane == 3 ? Blocks.STONE : Blocks.DEEPSLATE_TILE_SLAB)) {
                    throw new AssertionError(name + ": support changed during setup at index " + z);
                }
                if (lane != 2) {
                    var above = level.getBlockState(support.above());
                    double stored = SlabAnchorAttachment.storedPlacementDy(level, support.above());
                    if (!above.is(carpet) || Math.abs(stored - (lane == 3 ? 0 : -.5)) > 1e-6) {
                        throw new AssertionError(name + ": carpet state/height changed during setup at index " + z
                                + " state=" + above + " dy=" + stored);
                    }
                }
                var own = supportState.getCollisionShape(level, support, CollisionContext.empty());
                var union = SlabSupport.withHangingLoweredCollisionFromAbove(own, level, support);
                double collisionTop = union.max(Axis.Y);
                if (Math.abs(collisionTop - (lane == 3 ? 1.0d : top)) > 1e-6) {
                    throw new AssertionError(name + ": fixture collision top=" + collisionTop
                            + " expected=" + top + " at index " + z);
                }
            }
            Slabbed.LOGGER.info("CARPET_FIXTURE | {} | PASS cells=13 top={}", name, top);
            player.teleportTo(p.getX() + .5, p.getY() + top, p.getZ() + .5);
            Slabbed.LOGGER.info("CARPET_BROADPHASE | {} | serverStartRelativeTop={}",
                    name, broadphaseTop(player) - p.getY());
            return p;
        });
        context.waitFor(client -> client.player != null && client.level != null
                && Math.abs(client.player.getX() - origin.getX() - .5) < .05
                && Math.abs(client.player.getY() - origin.getY() - top) < .05
                && client.level.getBlockState(origin.south(12)).is(lane == 3 ? Blocks.STONE : Blocks.DEEPSLATE_TILE_SLAB)
                && (lane == 2 || Math.abs(SlabAnchorAttachment.storedPlacementDy(client.level,
                        origin.south(12).above()) - (lane == 3 ? 0 : -.5)) < 1e-6), 400);
        context.runOnClient(client -> {
            for (int z = 0; z < 13; z++) {
                BlockPos support = origin.south(z);
                var state = client.level.getBlockState(support);
                if (lane != 2) {
                    var expectedCarpet = lane == 1 ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                            net.minecraft.resources.Identifier.withDefaultNamespace("white_carpet")) : Blocks.MOSS_CARPET;
                    if (!client.level.getBlockState(support.above()).is(expectedCarpet)
                            || Math.abs(SlabAnchorAttachment.storedPlacementDy(client.level, support.above())
                            - (lane == 3 ? 0 : -.5)) > 1e-6) {
                        throw new AssertionError(name + ": client fixture missing carpet/height at index " + z);
                    }
                }
                if (lane != 2) {
                    var carpetState = client.level.getBlockState(support.above());
                    var outline = carpetState.getShape(client.level, support.above(), CollisionContext.empty());
                    var collision = carpetState.getCollisionShape(client.level, support.above(), CollisionContext.empty());
                    if (Math.abs(outline.max(Axis.Y) + 1 - top) > 1e-6
                            || Math.abs(collision.max(Axis.Y) + 1 - top) > 1e-6) {
                        throw new AssertionError(name + ": carpet outline and collision must keep the same top at index " + z);
                    }
                }
                var own = state.getCollisionShape(client.level, support, CollisionContext.empty());
                var union = SlabSupport.withHangingLoweredCollisionFromAbove(own, client.level, support);
                if (Math.abs(union.max(Axis.Y) - (lane == 3 ? 1.0d : top)) > 1e-6) {
                    throw new AssertionError(name + ": client fixture collision height differs at index " + z);
                }
            }
            Slabbed.LOGGER.info("CARPET_CLIENT_FIXTURE | {} | PASS cells=13", name);
            client.player.setYRot(0);
            client.player.setXRot(0);
            client.player.getAbilities().flying = false;
            client.player.setSprinting(false);
            client.options.keyUp.setDown(true);
        });
        Vec3[] sample = new Vec3[1];
        double previous = origin.getZ() + .5;
        double maxVerticalError = 0, maxPairDistance = 0;
        int backwards = 0;
        int wrongHeightSamples = 0;
        try {
            for (int tick = 0; tick < 30; tick++) {
                context.waitTicks(1);
                context.runOnClient(client -> sample[0] = client.player.position());
                Vec3 authority = world.getServer().computeOnServer(server ->
                        server.getPlayerList().getPlayers().getFirst().position());
                Vec3 predicted = sample[0];
                if (predicted.z < previous - .02) backwards++;
                previous = predicted.z;
                if (Math.abs(predicted.y - origin.getY() - top) > .01
                        || Math.abs(authority.y - origin.getY() - top) > .01) wrongHeightSamples++;
                maxVerticalError = Math.max(maxVerticalError, Math.max(Math.abs(predicted.y - origin.getY() - top),
                        Math.abs(authority.y - origin.getY() - top)));
                maxPairDistance = Math.max(maxPairDistance, predicted.distanceTo(authority));
            }
        } finally {
            context.runOnClient(client -> client.options.keyUp.setDown(false));
        }
        context.waitTicks(10);
        context.runOnClient(client -> sample[0] = client.player.position());
        Vec3 authority = world.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().position());
        double serverCollisionTop = world.getServer().computeOnServer(server ->
                broadphaseTop(server.getPlayerList().getPlayers().getFirst())) - origin.getY();
        context.runOnClient(client -> Slabbed.LOGGER.info(
                "CARPET_BROADPHASE | {} | clientFinalRelativeTop={} serverFinalRelativeTop={}",
                name, broadphaseTop(client.player) - origin.getY(), serverCollisionTop));
        double progress = sample[0].z - origin.getZ() - .5;
        double settledError = sample[0].distanceTo(authority);
        double finalClientHeightError = sample[0].y - origin.getY() - top;
        double finalServerHeightError = authority.y - origin.getY() - top;
        Slabbed.LOGGER.info("CARPET_HEIGHT | {} | wrongHeightSamples={} finalClientHeightError={} finalServerHeightError={}",
                name, wrongHeightSamples, finalClientHeightError, finalServerHeightError);
        Slabbed.LOGGER.info("CARPET_MOVEMENT | {} | progress={} backwards={} maxVerticalError={} maxPairDistance={} settledError={}",
                name, progress, backwards, maxVerticalError, maxPairDistance, settledError);
        if (progress < 3 || progress > 11 || backwards != 0 || maxVerticalError > .01 || settledError > .15) {
            throw new AssertionError(name + ": connected movement stalled, jittered or disagreed with server");
        }
    }
}
