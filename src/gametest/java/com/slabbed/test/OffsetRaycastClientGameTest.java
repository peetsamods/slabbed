package com.slabbed.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end client GameTest for the targeting overhaul.
 *
 * <p>Drives the REAL pick path — {@code mc.gameRenderer.updateCrosshairTarget(...)} ->
 * {@code ClientPlayerEntity.method_76762/76763} -> the {@code ClientPickOffsetRaycastMixin}
 * redirect -> {@link com.slabbed.util.SlabbedOffsetRaycast} — and asserts the crosshair
 * targets the lowered block the player is actually looking at, at an aim where stock
 * vanilla {@code world.raycast} cannot see it (the near-horizontal mid-height ray that
 * never enters the block's logical voxel cell). This proves the whole client pipeline,
 * not just the raycast util in isolation.
 *
 * <p>Runs unconditionally (it uses only vanilla blocks, needs no extra mods) so a plain
 * {@code runClientGameTest} provides continuous end-to-end coverage.
 */
public final class OffsetRaycastClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getClientWorld().waitForChunksRender();
            runLoweredFullBlockMidHeightPickProof(ctx, singleplayer);
        }
    }

    private static void runLoweredFullBlockMidHeightPickProof(
            ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        // Lowered full block: a stone block directly on a bottom slab (dy = -0.5),
        // so its outline spans world-Y [full.y-0.5, full.y+0.5].
        final BlockPos slab = new BlockPos(0, 200, 0);
        final BlockPos full = slab.up();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(slab,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(full, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        AtomicReference<String> verdict = new AtomicReference<>("not_run");

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                verdict.set("BLOCKED: client world/player unavailable");
                return;
            }

            // Horizontal aim at world-Y = full.y - 0.25: inside the lowered outline but
            // in the cell LAYER of the slab below, which the vanilla DDA never leaves.
            double y = full.getY() - 0.25;
            Vec3d eye = new Vec3d(full.getX() + 0.5, y, full.getZ() - 3.0);
            Vec3d dir = new Vec3d(0.0, 0.0, 1.0); // due +Z (south)
            Vec3d end = eye.add(dir.multiply(6.0));

            mc.player.refreshPositionAndAngles(
                    eye.x,
                    eye.y - mc.player.getStandingEyeHeight(),
                    eye.z,
                    0.0f,   // yaw 0 = +Z
                    0.0f);  // pitch 0 = level
            mc.player.setVelocity(Vec3d.ZERO);

            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    eye, end,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult crosshair = mc.crosshairTarget;

            boolean vanillaSawFull = vanilla.getType() == HitResult.Type.BLOCK
                    && vanilla.getBlockPos().equals(full);
            boolean crosshairOnFull = crosshair instanceof BlockHitResult bh
                    && crosshair.getType() == HitResult.Type.BLOCK
                    && bh.getBlockPos().equals(full);

            if (vanillaSawFull) {
                verdict.set("BLOCKED: control invalid — vanilla already saw the lowered block "
                        + "(bug geometry wrong); vanilla=" + formatHit(vanilla));
            } else if (!crosshairOnFull) {
                verdict.set("RED: crosshair did not target the lowered block via the redirect; "
                        + "vanilla=" + formatHit(vanilla) + " crosshair=" + formatHit(crosshair));
            } else {
                verdict.set("GREEN: crosshair targeted the lowered block where vanilla missed; "
                        + "vanilla=" + formatHit(vanilla) + " crosshair=" + formatHit(crosshair));
            }
        });

        String proof = "[offset-raycast-client] full=" + full.toShortString()
                + " verdict=" + verdict.get();
        System.out.println(proof);
        if (verdict.get().startsWith("RED") || verdict.get().startsWith("BLOCKED")) {
            throw new RuntimeException(proof);
        }
    }

    private static String formatHit(HitResult hit) {
        if (hit == null) {
            return "null";
        }
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            return "BLOCK " + bh.getBlockPos().toShortString() + " side=" + bh.getSide();
        }
        return hit.getType().toString();
    }
}
