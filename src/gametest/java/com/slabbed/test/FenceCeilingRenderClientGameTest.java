package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Checks the actual wrapped fence model, including after its ceiling is removed. */
public final class FenceCeilingRenderClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder().setUseConsistentSettings(true).create()) {
            singleplayer.getConnection().waitForChunksDownload();
            ctx.waitFor(client -> client.level != null && client.player != null, 400);
            BlockPos post = singleplayer.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos p = player.blockPosition().relative(player.getDirection(), 5).above().immutable();
                var world = server.overworld();
                world.setBlock(p, Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
                world.setBlock(p.above(), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                        Block.UPDATE_ALL);
                return p;
            });
            ctx.waitFor(client -> client.level != null && client.level.getBlockState(post).is(Blocks.OAK_FENCE)
                    && client.level.getBlockState(post.above()).is(Blocks.OAK_SLAB), 400);
            ctx.waitTicks(5);
            ctx.runOnClient(client -> checkModel(client, post, 1.5d));
            ctx.takeScreenshot("fence-ceiling-connected");
            singleplayer.getServer().runOnServer(server ->
                    server.overworld().setBlock(post.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
            ctx.waitFor(client -> client.level != null && client.level.getBlockState(post.above()).isAir(), 400);
            ctx.waitTicks(5);
            ctx.runOnClient(client -> {
                checkModel(client, post, 1.0d);
                Slabbed.LOGGER.info("CLIENT_GAMETEST | FenceCeilingRenderClientGameTest | PASS");
            });
        }
    }

    private static void checkModel(Minecraft client, BlockPos post, double expectedTop) {
        var state = client.level.getBlockState(post);
        var model = client.getModelManager().getBlockStateModelSet().get(state);
        if (!(model instanceof FabricBlockStateModel fabric) || Renderer.get() == null) {
            throw new AssertionError("the production fence model and renderer must be available");
        }
        double[] minMax = {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        QuadEmitter out = Renderer.get().quadEmitter(quad -> {
            for (int i = 0; i < 4; i++) {
                if (quad.x(i)>=0.375f && quad.x(i)<=0.625f && quad.z(i)>=0.375f && quad.z(i)<=0.625f) {
                    minMax[0]=Math.min(minMax[0],quad.y(i));
                    minMax[1]=Math.max(minMax[1],quad.y(i));
                }
                if (!Float.isFinite(quad.u(i)) || !Float.isFinite(quad.v(i))) {
                    throw new AssertionError("connection texture coordinates must stay finite");
                }
            }
        });
        fabric.emitQuads(out, client.level, post, state, RandomSource.create(42L), direction -> false);
        if (Math.abs(minMax[0])>1.0e-5d || Math.abs(minMax[1]-expectedTop)>1.0e-5d) {
            throw new AssertionError("post vertices must span 0 to "+expectedTop+", got "+minMax[0]+" to "+minMax[1]);
        }
        double stored = SlabAnchorAttachment.storedPlacementDy(client.level, post);
        if (Double.isFinite(stored) && stored!=0.0d) {
            throw new AssertionError("connecting geometry must not move the saved post");
        }
    }
}
