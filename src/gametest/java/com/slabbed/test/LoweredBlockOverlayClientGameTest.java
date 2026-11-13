package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.LoweredBlockOverlay;
import com.slabbed.test.support.TestConnections;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Mutation: withholding the overlay adapters restores a false raw-cell overlay. */
public final class LoweredBlockOverlayClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().setUseConsistentSettings(true).create()) {
            TestConnections.waitForChunksDownload(world);
            BlockPos origin = world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                BlockPos p = server.getPlayerList().getPlayers().getFirst().blockPosition().above(8).immutable();
                for (BlockPos clear : BlockPos.betweenClosed(p.offset(-1, -20, -1), p.offset(20, 18, 1))) {
                    level.setBlock(clear, Blocks.AIR.defaultBlockState(), 3);
                }
                for (int index = 0; index < 6; index++) {
                    BlockPos owner = p.east(index * 3);
                    level.setBlock(owner, index == 3 ? Blocks.GLASS.defaultBlockState()
                            : Blocks.STONE.defaultBlockState(), 3);
                    SlabAnchorAttachment.writePlacementDy(level, owner,
                            index == 0 ? 0 : index == 4 ? -16 : index == 5 ? -17 : -2);
                }
                // A valid second owner must not be hidden by an empty raw cell below it.
                level.setBlock(p.east(6).above(2), Blocks.DIRT.defaultBlockState(), 3);
                SlabAnchorAttachment.writePlacementDy(level, p.east(6).above(2), -2);
                return p;
            });
            context.waitFor(client -> client.level != null && client.player != null
                    && client.level.getBlockState(origin.east(15)).is(Blocks.STONE)
                    && SlabAnchorAttachment.storedPlacementDy(client.level, origin.east(15)) == -17, 400);
            context.runOnClient(client -> {
                var player = client.player;
                var oldPos = player.position();
                boolean oldNoPhysics = player.noPhysics;
                try {
                    player.noPhysics = false;
                    check(client, origin, .5, Blocks.STONE.defaultBlockState(), "vanilla interior");
                    check(client, origin.east(3), .5, null, "empty raw cell");
                    check(client, origin.east(3), -1.5, Blocks.STONE.defaultBlockState(), "lowered interior");
                    check(client, origin.east(3), -.9, null, "above lowered top");
                    check(client, origin.east(6), .5, Blocks.DIRT.defaultBlockState(), "mixed owners");
                    check(client, origin.east(9), -1.5, null, "transparent material");
                    check(client, origin.east(12), -15.5, Blocks.STONE.defaultBlockState(), "owner bound included");
                    check(client, origin.east(15), -16.5, null, "owner bound excluded");
                    player.noPhysics = true;
                    check(client, origin, .5, null, "no physics");
                    Slabbed.LOGGER.info("CLIENT_GAMETEST | LoweredBlockOverlayClientGameTest | PASS cases=9");
                } finally {
                    player.setPos(oldPos.x, oldPos.y, oldPos.z);
                    player.noPhysics = oldNoPhysics;
                }
            });
        }
    }

    private static void check(net.minecraft.client.Minecraft client, BlockPos owner, double eyeOffset,
                              BlockState expected, String label) {
        var player = client.player;
        double eyeY = owner.getY() + eyeOffset;
        player.setPos(owner.getX() + .5, eyeY - player.getEyeHeight(), owner.getZ() + .5);
        AABB plane = AABB.ofSize(player.getEyePosition(), .02, .02, .02);
        BlockState actual = LoweredBlockOverlay.find(player, plane);
        BlockState production;
        try {
            if (com.slabbed.compat.MinecraftVersions.AT_LEAST_26_3) {
                var frustum = new net.minecraft.client.renderer.culling.Frustum(new org.joml.Matrix4f(),
                        new org.joml.Matrix4f().perspective(1.0F, 1.0F, .01F, 100F));
                var method = Class.forName("net.minecraft.client.renderer.extract.LevelExtractor")
                        .getDeclaredMethod("getViewBlockingState", net.minecraft.client.player.LocalPlayer.class,
                                net.minecraft.client.renderer.culling.Frustum.class);
                method.setAccessible(true);
                production = (BlockState) method.invoke(null, player, frustum);
            } else {
                var method = net.minecraft.client.renderer.ScreenEffectRenderer.class.getDeclaredMethod(
                        "getViewBlockingState", net.minecraft.world.entity.player.Player.class);
                method.setAccessible(true);
                production = (BlockState) method.invoke(null, player);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("production overlay invocation failed", e);
        }
        if (production != expected) throw new AssertionError(label + ": production expected " + expected + ", got " + production);
        if (actual != expected) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
