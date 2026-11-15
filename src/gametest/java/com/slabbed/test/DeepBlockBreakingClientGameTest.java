package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.test.support.TestConnections;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Breaking must validate the visible block, without granting reach to a distant block. */
public final class DeepBlockBreakingClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().setUseConsistentSettings(true).create()) {
            TestConnections.waitForChunksDownload(world);
            ctx.waitFor(c -> c.level != null && c.player != null && c.gameMode != null, 400);
            BlockPos pos = world.getServer().computeOnServer(s ->
                    s.getPlayerList().getPlayers().getFirst().blockPosition().above(12).immutable());
            runCase(ctx, world, pos, 0, 3, true);
            runCase(ctx, world, pos, 0, 7, false);
            runCase(ctx, world, pos, -3, 11, false);
            runCase(ctx, world, pos, -3, -4, false);
            runCase(ctx, world, pos, -3, 7, true);
            Slabbed.LOGGER.info("CLIENT_GAMETEST | DeepBlockBreakingClientGameTest | PASS");
        }
    }
    private static void runCase(ClientGameTestContext ctx, TestSingleplayerContext world,
            BlockPos pos, double dy, double eyeBelow, boolean shouldBreak) {
        Vec3 feet = world.getServer().computeOnServer(s -> {
            var level = s.overworld();
            var player = s.getPlayerList().getPlayers().getFirst();
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2,-12,-2),pos.offset(2,2,2)))
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            SlabAnchorAttachment.writePlacementDy(level,pos,dy);
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            Vec3 at = new Vec3(pos.getX()+.5,pos.getY()-eyeBelow-player.getEyeHeight(),pos.getZ()+.5);
            player.teleportTo(at.x,at.y,at.z);
            return at;
        });
        ctx.waitFor(c -> c.player.position().distanceToSqr(feet)<.01
                && c.level.getBlockState(pos).is(Blocks.STONE)
                && SlabAnchorAttachment.storedPlacementDy(c.level,pos)==dy,200);
        ctx.runOnClient(c -> c.gameMode.startDestroyBlock(pos, Direction.DOWN));
        ctx.waitTicks(20);
        boolean broken = world.getServer().computeOnServer(s -> s.overworld().getBlockState(pos).isAir());
        if (broken != shouldBreak) throw new AssertionError("breaking dy="+dy+" eyeBelow="+eyeBelow
                +" expected="+shouldBreak+" actual="+broken);
        Slabbed.LOGGER.info("DeepBlockBreaking case dy={} eyeBelow={} broken={}",dy,eyeBelow,broken);
    }
}
