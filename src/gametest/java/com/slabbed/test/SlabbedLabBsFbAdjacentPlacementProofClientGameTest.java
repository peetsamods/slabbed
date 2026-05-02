package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class SlabbedLabBsFbAdjacentPlacementProofClientGameTest implements FabricClientGameTest {

    private static final BlockPos SUPPORT_POS = new BlockPos(0, 200, 0);
    private static final BlockPos FULL_POS = SUPPORT_POS.up();
    private static final BlockPos ADJACENT_FULL_POS = FULL_POS.east();
    private static final double EPSILON = 1.0e-6d;
    private static final double TOP_FACE_HIT_Y = 1.0d;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runBsfbToAdjacentFbLiveProof(ctx, singleplayer);
        }
    }

    /**
     * LIVE proof for BSFB -> adjacent FB placement.
     *
     * 1) Place BOTTOM slab at support.
     * 2) Place STONE on top of that slab (expect dy=-0.5 and anchored).
     * 3) Place STONE against the side face of that lowered anchored STONE.
     *
     * Desired legal state:
     * - adjacent STONE should be dy=-0.5, anchored=true, lowered=true.
     * - if placement is rejected, test should fail to force explicit legal-state routing.
     */
    private static void runBsfbToAdjacentFbLiveProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final double eyeY = SUPPORT_POS.getY() + 2.8d;
        final double placeTargetX = SUPPORT_POS.getX() + 0.5d;
        final double placeTargetZ = SUPPORT_POS.getZ() + 0.5d;

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            int minY = SUPPORT_POS.getY() - 1;
            int maxY = SUPPORT_POS.getY() + 2;
            for (int x = SUPPORT_POS.getX() - 2; x <= SUPPORT_POS.getX() + 2; x++) {
                for (int z = SUPPORT_POS.getZ() - 2; z <= SUPPORT_POS.getZ() + 2; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        world.setBlockState(
                                new BlockPos(x, y, z),
                                Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }
            world.setBlockState(
                    SUPPORT_POS,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    FULL_POS,
                    Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, FULL_POS, world.getBlockState(FULL_POS));
            world.setBlockState(
                    ADJACENT_FULL_POS,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);

            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer player missing during BSFB proof setup");
            }
            server.getPlayerManager().getPlayerList().get(0).changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        // Step 1: verify seeded lowered support slab.
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after slab placement");
            }
            BlockState support = mc.world.getBlockState(SUPPORT_POS);
            if (!support.isOf(Blocks.STONE_SLAB) || !support.contains(SlabBlock.TYPE)
                    || support.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("BSFB proof expected support slab at " + SUPPORT_POS.toShortString()
                        + ", found " + support);
            }
        });

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after source FB placement");
            }
            BlockState sourceFb = mc.world.getBlockState(FULL_POS);
            if (!sourceFb.isOf(Blocks.STONE)) {
                throw new RuntimeException("BSFB proof expected source STONE at " + FULL_POS.toShortString()
                        + ", found " + sourceFb);
            }

            double sourceDy = SlabSupport.getYOffset(mc.world, FULL_POS, sourceFb);
            boolean sourceAnchored = SlabAnchorAttachment.isAnchored(mc.world, FULL_POS);
            if (!sourceAnchored) {
                throw new RuntimeException("BSFB proof source FB was not anchored at " + FULL_POS.toShortString()
                        + " dy=" + sourceDy);
            }
            if (Math.abs(sourceDy + 0.5d) > EPSILON) {
                throw new RuntimeException("BSFB proof expected source FB dy=-0.500, found " + sourceDy);
            }
        });

        // Step 3: place STONE against side face of lowered source FB.
        BlockHitResult adjacentSideHit = resolveLoweredSideFaceHit(FULL_POS, Direction.EAST, SlabType.BOTTOM);
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(FULL_POS.getX() + 1.7d, eyeY, FULL_POS.getZ() + 0.5d),
                adjacentSideHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for BSFB proof adjacent FB placement");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8));
            ActionResult adjacentPlacement = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, adjacentSideHit);
            if (!adjacentPlacement.isAccepted()) {
                mc.world.getPlayerByUuid(mc.player.getUuid())
                        .sendMessage(Text.literal("BSFB proof adjacent FB placement rejected unexpectedly"), true);
                throw new RuntimeException("BSFB proof expected adjacent FB placement to be accepted, but received "
                        + adjacentPlacement);
            }
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after adjacent FB placement");
            }
            BlockState adjacentFb = mc.world.getBlockState(ADJACENT_FULL_POS);
            if (!adjacentFb.isOf(Blocks.STONE)) {
                throw new RuntimeException("BSFB proof expected adjacent STONE at " + ADJACENT_FULL_POS.toShortString()
                        + ", found " + adjacentFb);
            }
            double adjacentDy = SlabSupport.getYOffset(mc.world, ADJACENT_FULL_POS, adjacentFb);
            boolean adjacentAnchored = SlabAnchorAttachment.isAnchored(mc.world, ADJACENT_FULL_POS);
            System.out.println("[SBSB-TRACE][HEAD] item=minecraft:stone face=east hitPos="
                    + ADJACENT_FULL_POS.toShortString()
                    + " placePos=" + ADJACENT_FULL_POS.toShortString()
                    + " stone dy=" + adjacentDy
                    + " anchored=" + adjacentAnchored + " lowered=" + (adjacentDy < -EPSILON));
            if (Math.abs(adjacentDy + 0.5d) > EPSILON || !adjacentAnchored) {
                throw new RuntimeException("RED: adjacent STONE should be dy=-0.500 and anchored, but found dy=" + adjacentDy
                        + " anchored=" + adjacentAnchored + " at " + ADJACENT_FULL_POS.toShortString());
            }
        });
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
            player.setPosition(eye.x, feetY, eye.z);
            player.setYaw(yaw);
            player.setPitch(pitch);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
    }

    private static BlockHitResult resolveLoweredSideFaceHit(BlockPos targetPos, Direction face, SlabType targetType) {
        double yOffset = targetType == SlabType.BOTTOM ? -0.25d : 0.25d;
        return resolveLoweredFaceHit(targetPos, face, yOffset);
    }

    private static BlockHitResult resolveLoweredUpMergeHit(BlockPos targetPos) {
        return new BlockHitResult(
                new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + TOP_FACE_HIT_Y, targetPos.getZ() + 0.5d),
                Direction.UP,
                targetPos,
                false,
                false);
    }

    private static BlockHitResult resolveLoweredFaceHit(BlockPos targetPos, Direction face, double yOffset) {
        return switch (face) {
            case NORTH -> new BlockHitResult(
                    new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + yOffset, targetPos.getZ()),
                    Direction.NORTH,
                    targetPos,
                    false,
                    false);
            case SOUTH -> new BlockHitResult(
                    new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + yOffset, targetPos.getZ() + 1.0d),
                    Direction.SOUTH,
                    targetPos,
                    false,
                    false);
            case EAST -> new BlockHitResult(
                    new Vec3d(targetPos.getX() + 1.0d, targetPos.getY() + yOffset, targetPos.getZ() + 0.5d),
                    Direction.EAST,
                    targetPos,
                    false,
                    false);
            case WEST -> new BlockHitResult(
                    new Vec3d(targetPos.getX(), targetPos.getY() + yOffset, targetPos.getZ() + 0.5d),
                    Direction.WEST,
                    targetPos,
                    false,
                    false);
            default -> throw new IllegalArgumentException("unsupported face for repro: " + face);
        };
    }
}
