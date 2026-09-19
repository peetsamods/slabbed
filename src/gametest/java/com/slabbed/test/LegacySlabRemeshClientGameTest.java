package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

/** Checks render-cache invalidation for legacy slabs without a stored placement height. */
public final class LegacySlabRemeshClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        BlockPos source = new BlockPos(12, 200, 0);
        try (TestSingleplayerContext game = ctx.worldBuilder()
                .setUseConsistentSettings(true).create()) {
            game.getClientWorld().waitForChunksRender();
            game.getServer().runOnServer(server -> {
                var world = server.getOverworld();
                var slab = Blocks.SPRUCE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
                world.setBlockState(source.down(), slab, Block.NOTIFY_LISTENERS);
                world.setBlockState(source, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                for (int x = 1; x <= 16; x++) {
                    world.setBlockState(source.east(x), slab, Block.NOTIFY_LISTENERS);
                }
            });
            for (int i = 0; i < 30; i++) ctx.waitTick();
            game.getClientWorld().waitForChunksRender();
            ctx.runOnClient(mc -> {
                for (int x = 1; x <= 16; x++) {
                    BlockPos pos = source.east(x);
                    double dy = SlabSupport.getVisualYOffset(mc.world, pos, mc.world.getBlockState(pos));
                    require(dy == -0.5, "legacy fixture must start lowered: distance=" + x + " dy=" + dy);
                }
            });
            game.getServer().runOnServer(server -> server.getOverworld()
                    .setBlockState(source, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS));
            for (int i = 0; i < 30; i++) ctx.waitTick();
            game.getClientWorld().waitForChunksRender();
            capture(ctx, game, source, "legacy_slab_source_removed");
            assertHeights(ctx, source, 0.0);
            game.getServer().runOnServer(server -> server.getOverworld()
                    .setBlockState(source, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS));
            for (int i = 0; i < 30; i++) ctx.waitTick();
            game.getClientWorld().waitForChunksRender();
            capture(ctx, game, source, "legacy_slab_source_restored");
            assertHeights(ctx, source, -0.5);
        }
    }

    private static void capture(ClientGameTestContext ctx, TestSingleplayerContext game,
                                BlockPos source, String name) {
        ctx.runOnClient(mc -> {
            mc.options.hudHidden = true;
            mc.player.refreshPositionAndAngles(source.getX() + 8.5, source.getY() + 2.0,
                    source.getZ() - 14.0, 0.0f, 13.0f);
            mc.player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
        });
        ctx.waitTick();
        game.getClientWorld().waitForChunksRender();
        ctx.takeScreenshot(name);
    }

    private static void assertHeights(ClientGameTestContext ctx, BlockPos source, double expected) {
        ctx.runOnClient(mc -> {
            var builder = new ChunkRendererRegionBuilder();
            for (int x : new int[]{1, 2, 3, 6, 10, 16}) {
                BlockPos pos = source.east(x);
                var state = mc.world.getBlockState(pos);
                double fresh = SlabSupport.getYOffset(mc.world, pos, state);
                var region = builder.build(mc.world, ChunkSectionPos.from(pos).asLong());
                require(region != null, "render region must exist");
                double visual = SlabSupport.getVisualYOffset(region, pos, state);
                System.out.println("[legacy-slab-remesh] distance=" + x
                        + " fresh=" + fresh + " visual=" + visual);
                require(fresh == expected, "legacy source state must produce expected height=" + expected);
                require(visual == fresh, "stale visual height at distance=" + x
                        + " fresh=" + fresh + " visual=" + visual);
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
