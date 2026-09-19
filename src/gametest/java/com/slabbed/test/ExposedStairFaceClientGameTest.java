package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Checks exposed stair backs beside a neighbor at a different placed height. */
public final class ExposedStairFaceClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext ctx) {
        BlockPos p = new BlockPos(0, 200, 0);
        try (TestSingleplayerContext game = ctx.worldBuilder().setUseConsistentSettings(true).create()) {
            game.getClientWorld().waitForChunksRender();
            game.getServer().runOnServer(server -> {
                var w = server.getOverworld();
                w.setBlockState(p, Blocks.COBBLED_DEEPSLATE_STAIRS.getDefaultState()
                    .with(StairsBlock.FACING, Direction.EAST), Block.NOTIFY_LISTENERS);
                w.setBlockState(p.east(), Blocks.OAK_PLANKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                SlabPlacementDyAttachment.record(w, p, -0.5);
                SlabPlacementDyAttachment.record(w, p.east(), 0.0);
            });
            for (int i=0;i<40;i++) ctx.waitTick();
            ctx.runOnClient(mc -> {
                mc.options.hudHidden=true;
                mc.player.setNoGravity(true);
                mc.player.refreshPositionAndAngles(3.8, 198.5, -3.0, 45.0f, 0.0f);
                mc.player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
                double dy=SlabSupport.getVisualYOffset(mc.world,p,mc.world.getBlockState(p));
                if (dy != -0.5) throw new AssertionError("stair height sync failed: "+dy);
            });
            ctx.waitTick();
            game.getClientWorld().waitForChunksRender();
            ctx.takeScreenshot("lowered_stair_beside_planks");
            ctx.runOnClient(mc -> {
                boolean face=SlabSupport.isSlabHeightStepFace(mc.world,p,mc.world.getBlockState(p),Direction.EAST);
                System.out.println("[exposed-stair-face] correction="+face);
                if (!face) throw new AssertionError("exposed lowered stair back must remain visible beside flush planks");
            });
        }
    }
}
