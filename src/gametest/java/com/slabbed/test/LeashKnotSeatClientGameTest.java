package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.test.support.TestConnections;
import com.slabbed.util.HangingSeatDyHolder;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Server-authored knot seat reaches the client box, rope endpoint and actual renderer state. */
public final class LeashKnotSeatClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().setUseConsistentSettings(true).create()) {
            TestConnections.waitForChunksDownload(world);
            ctx.waitFor(client -> client.level != null && client.player != null, 400);
            int[] ids = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos pos = player.blockPosition().relative(player.getDirection(), 5).immutable();
                var level = server.overworld();
                level.setBlock(pos.below(), Blocks.STONE_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
                level.setBlock(pos, Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
                SlabAnchorAttachment.writePlacementDy(level, pos, -0.5d);
                var knot = LeashFenceKnotEntity.getOrCreateKnot(level, pos);
                if (((HangingSeatDyHolder) knot).slabbed$hangSeatDy() != -0.5d)
                    throw new AssertionError("server knot seat: " + ((HangingSeatDyHolder) knot).slabbed$hangSeatDy());
                var cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
                if (cow == null) throw new AssertionError("missing cow fixture");
                cow.setPos(pos.getX() + 2.5d, pos.getY(), pos.getZ() + 0.5d);
                cow.setNoAi(true);
                level.addFreshEntity(cow);
                cow.setLeashedTo(knot, true);
                return new int[]{knot.getId(), cow.getId()};
            });
            ctx.waitFor(client -> client.level != null && client.level.getEntity(ids[0]) != null
                    && client.level.getEntity(ids[1]) != null, 400);
            ctx.waitTicks(10);
            ctx.runOnClient(client -> {
                var knot = (LeashFenceKnotEntity) client.level.getEntity(ids[0]);
                var leashed = (net.minecraft.world.entity.Leashable) client.level.getEntity(ids[1]);
                if (((HangingSeatDyHolder) knot).slabbed$hangSeatDy() != -0.5d || leashed.getLeashHolder() != knot) {
                    throw new AssertionError("client seat=" + ((HangingSeatDyHolder) knot).slabbed$hangSeatDy()
                            + " holder=" + (leashed.getLeashHolder() == null ? "none" : leashed.getLeashHolder().getId()));
                }
            });
            ctx.waitTicks(10);
            ctx.runOnClient(client -> {
                var knot = (LeashFenceKnotEntity) client.level.getEntity(ids[0]);
                double seat = knot.getPos().getY() + 0.375d - 0.5d;
                var state = client.getEntityRenderDispatcher().getRenderer(knot).createRenderState(knot, 1.0f);
                if (Math.abs(knot.getBoundingBox().minY - seat) > 1.0e-6d
                        || Math.abs(knot.getRopeHoldPosition(1.0f).y - seat - 0.2d) > 1.0e-6d
                        || Math.abs(state.y - seat) > 1.0e-6d) {
                    throw new AssertionError("synced knot box, rope and renderer must share lowered seat");
                }
                Slabbed.LOGGER.info("CLIENT_GAMETEST | LeashKnotSeatClientGameTest | PASS");
            });
            // Keep welcome chat from covering the knot in the visual receipt.
            ctx.waitTicks(220);
            ctx.takeScreenshot("leash-knot-lowered-seat");
        }
    }
}
