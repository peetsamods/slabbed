package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.debug.SlabbedRetargetTestHooks;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.function.Predicate;

public final class SlabbedLabBsFbAdjacentPlacementProofClientGameTest implements FabricClientGameTest {

    private static final BlockPos SUPPORT_POS = new BlockPos(0, 200, 0);
    private static final BlockPos FULL_POS = SUPPORT_POS.up();
    private static final BlockPos ADJACENT_FULL_POS = FULL_POS.east();
    private static final BlockPos TOP_SLAB_POS = ADJACENT_FULL_POS.up();
    private static final BlockPos UPPER_SLAB_POS = TOP_SLAB_POS.up();
    private static final BlockPos NEXT_TOP_SLAB_POS = UPPER_SLAB_POS.up();
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
     * 4) Place STONE_SLAB on top face of that adjacent lowered anchored STONE.
     * 5) Merge that slab into a lowered DOUBLE, place STONE_SLAB on its top face.
     * 6) Merge the upper slab into a lowered DOUBLE, place STONE_SLAB on its top face
     *    to prove recursive lowered carrier propagation up the chain.
     *
     * Desired legal state:
     * - adjacent STONE should be dy=-0.5, anchored=true, lowered=true.
     * - slab on adjacent STONE should inherit the lowered lane.
     * - slab on lowered DOUBLE should inherit the lowered lane.
     * - slab on lowered DOUBLE that itself sits on a lowered DOUBLE should inherit the
     *   lowered lane (recursive lowered carrier grammar).
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
            int maxY = SUPPORT_POS.getY() + 5;
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
                    Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(
                    world,
                    ADJACENT_FULL_POS,
                    world.getBlockState(ADJACENT_FULL_POS),
                    FULL_POS,
                    world.getBlockState(FULL_POS));
            world.setBlockState(
                    TOP_SLAB_POS,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);

            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer player missing during BSFB proof setup");
            }
            server.getPlayerManager().getPlayerList().get(0).changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        });

        waitForPlacementSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();
        runJuliaLiveTraceSideSlabRetargetRed(ctx, singleplayer);

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

        // Step 3: verify seeded adjacent lowered FB. The slab placement below still uses real item interaction.
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after seeded adjacent FB setup");
            }
            BlockState adjacentFb = mc.world.getBlockState(ADJACENT_FULL_POS);
            if (!adjacentFb.isOf(Blocks.STONE)) {
                throw new RuntimeException("BSFB proof expected adjacent STONE at " + ADJACENT_FULL_POS.toShortString()
                        + ", found " + adjacentFb);
            }
            BlockState adjacentBelow = mc.world.getBlockState(ADJACENT_FULL_POS.down());
            if (adjacentBelow.isOf(Blocks.STONE_SLAB)) {
                throw new RuntimeException("BSFB proof expected no bottom slab below adjacent STONE at "
                        + ADJACENT_FULL_POS.down().toShortString() + ", found " + adjacentBelow);
            }
            double adjacentDy = SlabSupport.getYOffset(mc.world, ADJACENT_FULL_POS, adjacentFb);
            boolean adjacentAnchored = SlabAnchorAttachment.isAnchored(mc.world, ADJACENT_FULL_POS);
            System.out.println("[SBSB-TRACE][HEAD] item=minecraft:stone face=east hitPos="
                    + ADJACENT_FULL_POS.toShortString()
                    + " placePos=" + ADJACENT_FULL_POS.toShortString()
                    + " stone dy=" + adjacentDy
                    + " anchored=" + adjacentAnchored + " lowered=" + (adjacentDy < -EPSILON)
                    + " below=" + adjacentBelow);
            if (Math.abs(adjacentDy + 0.5d) > EPSILON || !adjacentAnchored) {
                throw new RuntimeException("RED: adjacent STONE should be dy=-0.500 and anchored, but found dy=" + adjacentDy
                        + " anchored=" + adjacentAnchored + " at " + ADJACENT_FULL_POS.toShortString());
            }
        });

        runDoubleSlabFullHeightCarrierTriadProof(ctx, singleplayer);

        // Step 4: place STONE_SLAB on top face of lowered adjacent FB.
        BlockHitResult slabOnAdjacentTopHit = resolveLoweredUpMergeHit(ADJACENT_FULL_POS);
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(ADJACENT_FULL_POS.getX() + 0.5d, eyeY, ADJACENT_FULL_POS.getZ() + 1.7d),
                slabOnAdjacentTopHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for BSFB proof top slab placement");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            ActionResult slabPlacement = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, slabOnAdjacentTopHit);
            if (!slabPlacement.isAccepted()) {
                mc.world.getPlayerByUuid(mc.player.getUuid())
                        .sendMessage(Text.literal("BSFB proof slab-on-adjacent-FB placement rejected unexpectedly"), true);
                throw new RuntimeException("BSFB proof expected slab-on-adjacent-FB placement to be accepted, but received "
                        + slabPlacement);
            }
            BlockState topSlab = mc.world.getBlockState(TOP_SLAB_POS);
            if (!topSlab.isOf(Blocks.STONE_SLAB)) {
                throw new RuntimeException("BSFB proof expected STONE_SLAB at " + TOP_SLAB_POS.toShortString()
                        + ", found " + topSlab);
            }

            double slabDy = SlabSupport.getYOffset(mc.world, TOP_SLAB_POS, topSlab);
            boolean slabAnchored = SlabAnchorAttachment.isAnchored(mc.world, TOP_SLAB_POS);
            String slabType = topSlab.contains(SlabBlock.TYPE) ? topSlab.get(SlabBlock.TYPE).asString() : "missing";
            System.out.println("[SBSB-TRACE][TOP_SLAB_ON_ADJACENT_FB] item=minecraft:stone_slab face=up target="
                    + ADJACENT_FULL_POS.toShortString()
                    + " placePos=" + TOP_SLAB_POS.toShortString()
                    + " state=" + topSlab
                    + " type=" + slabType
                    + " dy=" + slabDy
                    + " anchored=" + slabAnchored);
            if (Math.abs(slabDy + 0.5d) > EPSILON) {
                throw new RuntimeException("RED: slab placed on top of anchored lowered FB should inherit lowered lane, but found dy="
                        + slabDy
                        + " anchored=" + slabAnchored
                        + " state=" + topSlab
                        + " at " + TOP_SLAB_POS.toShortString());
            }
        });

        // Step 5: establish the first slab as a lowered DOUBLE, then prove slab-on-lowered-DOUBLE placement.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    TOP_SLAB_POS,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    UPPER_SLAB_POS,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        waitForPlacementSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after lowered DOUBLE setup");
            }
            BlockState lowerDouble = mc.world.getBlockState(TOP_SLAB_POS);
            if (!lowerDouble.isOf(Blocks.STONE_SLAB) || !lowerDouble.contains(SlabBlock.TYPE)
                    || lowerDouble.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("BSFB proof expected lowered DOUBLE slab at " + TOP_SLAB_POS.toShortString()
                        + ", found " + lowerDouble);
            }
            double lowerDoubleDy = SlabSupport.getYOffset(mc.world, TOP_SLAB_POS, lowerDouble);
            System.out.println("[SBSB-TRACE][LOWERED_DOUBLE_ON_ADJACENT_FB] pos="
                    + TOP_SLAB_POS.toShortString()
                    + " state=" + lowerDouble
                    + " dy=" + lowerDoubleDy
                    + " lowered=" + (lowerDoubleDy < -EPSILON));
            if (Math.abs(lowerDoubleDy + 0.5d) > EPSILON) {
                throw new RuntimeException("BSFB proof expected lowered DOUBLE dy=-0.500 at "
                        + TOP_SLAB_POS.toShortString() + ", found dy=" + lowerDoubleDy
                        + " state=" + lowerDouble);
            }
        });

        BlockHitResult slabOnLoweredDoubleTopHit = resolveLoweredUpMergeHit(TOP_SLAB_POS);
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(TOP_SLAB_POS.getX() + 0.5d, eyeY + 1.0d, TOP_SLAB_POS.getZ() + 1.7d),
                slabOnLoweredDoubleTopHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for BSFB proof upper slab placement");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            ActionResult upperSlabPlacement = mc.interactionManager.interactBlock(
                    mc.player,
                    Hand.MAIN_HAND,
                    slabOnLoweredDoubleTopHit);
            if (!upperSlabPlacement.isAccepted()) {
                mc.world.getPlayerByUuid(mc.player.getUuid())
                        .sendMessage(Text.literal("BSFB proof slab-on-lowered-DOUBLE placement rejected unexpectedly"), true);
                throw new RuntimeException("BSFB proof expected slab-on-lowered-DOUBLE placement to be accepted, but received "
                        + upperSlabPlacement);
            }
            BlockState upperSlab = mc.world.getBlockState(UPPER_SLAB_POS);
            if (!upperSlab.isOf(Blocks.STONE_SLAB)) {
                throw new RuntimeException("BSFB proof expected upper STONE_SLAB at " + UPPER_SLAB_POS.toShortString()
                        + ", found " + upperSlab);
            }

            double upperSlabDy = SlabSupport.getYOffset(mc.world, UPPER_SLAB_POS, upperSlab);
            boolean upperSlabLowered = upperSlabDy < -EPSILON;
            String upperSlabType = upperSlab.contains(SlabBlock.TYPE) ? upperSlab.get(SlabBlock.TYPE).asString() : "missing";
            System.out.println("[SBSB-TRACE][TOP_SLAB_ON_LOWERED_DOUBLE] item=minecraft:stone_slab face=up target="
                    + TOP_SLAB_POS.toShortString()
                    + " placePos=" + UPPER_SLAB_POS.toShortString()
                    + " state=" + upperSlab
                    + " type=" + upperSlabType
                    + " dy=" + upperSlabDy
                    + " lowered=" + upperSlabLowered);
            if (Math.abs(upperSlabDy + 0.5d) > EPSILON) {
                throw new RuntimeException("RED: slab placed on top of lowered DOUBLE should inherit lowered lane, but found dy="
                        + upperSlabDy
                        + " lowered=" + upperSlabLowered
                        + " state=" + upperSlab
                        + " at " + UPPER_SLAB_POS.toShortString());
            }
        });

        // Step 6: complete the upper slab into a lowered DOUBLE so the chain is
        // now lowered DOUBLE on lowered DOUBLE on lowered FB. Then place a real
        // STONE_SLAB on its top face. DOUBLE slabs are full-height carriers only
        // inside Slabbed's lowered carrier/support law, so this must stay lowered.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    UPPER_SLAB_POS,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    NEXT_TOP_SLAB_POS,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        waitForPlacementSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after upper lowered DOUBLE setup");
            }
            BlockState upperDouble = mc.world.getBlockState(UPPER_SLAB_POS);
            if (!upperDouble.isOf(Blocks.STONE_SLAB) || !upperDouble.contains(SlabBlock.TYPE)
                    || upperDouble.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("BSFB proof expected upper lowered DOUBLE slab at "
                        + UPPER_SLAB_POS.toShortString() + ", found " + upperDouble);
            }
            double upperDoubleDy = SlabSupport.getYOffset(mc.world, UPPER_SLAB_POS, upperDouble);
            System.out.println("[SBSB-TRACE][UPPER_LOWERED_DOUBLE] pos="
                    + UPPER_SLAB_POS.toShortString()
                    + " state=" + upperDouble
                    + " dy=" + upperDoubleDy
                    + " lowered=" + (upperDoubleDy < -EPSILON));
            if (Math.abs(upperDoubleDy + 0.5d) > EPSILON) {
                throw new RuntimeException("BSFB proof expected upper lowered DOUBLE dy=-0.500 at "
                        + UPPER_SLAB_POS.toShortString() + ", found dy=" + upperDoubleDy
                        + " state=" + upperDouble);
            }
        });

        BlockHitResult slabOnRecursiveDoubleHit = resolveLoweredUpMergeHit(UPPER_SLAB_POS);
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(UPPER_SLAB_POS.getX() + 0.5d, eyeY + 2.0d, UPPER_SLAB_POS.getZ() + 1.7d),
                slabOnRecursiveDoubleHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for BSFB proof recursive lowered slab placement");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            ActionResult recursivePlacement = mc.interactionManager.interactBlock(
                    mc.player,
                    Hand.MAIN_HAND,
                    slabOnRecursiveDoubleHit);
            if (!recursivePlacement.isAccepted()) {
                mc.world.getPlayerByUuid(mc.player.getUuid())
                        .sendMessage(Text.literal("BSFB proof recursive lowered slab placement rejected unexpectedly"), true);
                throw new RuntimeException("BSFB proof expected recursive lowered slab placement to be accepted, but received "
                        + recursivePlacement);
            }
            BlockState nextTopSlab = mc.world.getBlockState(NEXT_TOP_SLAB_POS);
            if (!nextTopSlab.isOf(Blocks.STONE_SLAB)) {
                throw new RuntimeException("BSFB proof expected STONE_SLAB at " + NEXT_TOP_SLAB_POS.toShortString()
                        + ", found " + nextTopSlab);
            }

            double nextDy = SlabSupport.getYOffset(mc.world, NEXT_TOP_SLAB_POS, nextTopSlab);
            boolean nextLowered = nextDy < -EPSILON;
            String nextType = nextTopSlab.contains(SlabBlock.TYPE) ? nextTopSlab.get(SlabBlock.TYPE).asString() : "missing";
            System.out.println("[SBSB-TRACE][TOP_SLAB_ON_RECURSIVE_LOWERED_DOUBLE] item=minecraft:stone_slab face=up target="
                    + UPPER_SLAB_POS.toShortString()
                    + " placePos=" + NEXT_TOP_SLAB_POS.toShortString()
                    + " state=" + nextTopSlab
                    + " type=" + nextType
                    + " dy=" + nextDy
                    + " lowered=" + nextLowered);
            if (Math.abs(nextDy + 0.5d) > EPSILON) {
                throw new RuntimeException("RED: recursive lowered slab carrier should continue lowered lane, but found dy="
                        + nextDy
                        + " lowered=" + nextLowered
                        + " state=" + nextTopSlab
                        + " at " + NEXT_TOP_SLAB_POS.toShortString());
            }
        });

        BlockPos sideCarrierPos = UPPER_SLAB_POS;
        BlockHitResult sideSlabHit = resolveLoweredSideFaceHit(sideCarrierPos, Direction.EAST, SlabType.DOUBLE);
        BlockPos expectedSideSlabPos = sideCarrierPos.east();
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(sideCarrierPos.getX() + 0.5d, eyeY + 2.0d, sideCarrierPos.getZ() + 1.7d),
                sideSlabHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for BSFB proof recursive side slab placement");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            System.out.println("[SBSB-TRACE][SIDE_SLAB_RECURSIVE_HIT] item=minecraft:stone_slab face=east target="
                    + sideCarrierPos.toShortString()
                    + " expectedPlacePos=" + expectedSideSlabPos.toShortString()
                    + " hitPos=" + sideSlabHit.getPos()
                    + " targetState=" + mc.world.getBlockState(sideCarrierPos));
            ActionResult sidePlacement = mc.interactionManager.interactBlock(
                    mc.player,
                    Hand.MAIN_HAND,
                    sideSlabHit);
            if (!sidePlacement.isAccepted()) {
                mc.world.getPlayerByUuid(mc.player.getUuid())
                        .sendMessage(Text.literal("BSFB proof recursive side slab placement rejected unexpectedly"), true);
                throw new RuntimeException("BSFB proof expected recursive side slab placement to be accepted, but received "
                        + sidePlacement);
            }
            BlockPos[] sideCandidates = new BlockPos[] {
                    expectedSideSlabPos,
                    sideCarrierPos.west(),
                    sideCarrierPos.north(),
                    sideCarrierPos.south(),
                    expectedSideSlabPos.up(),
                    expectedSideSlabPos.down(),
                    sideCarrierPos.up(),
                    sideCarrierPos.down()
            };
            BlockPos sideSlabPos = null;
            BlockState sideSlab = Blocks.AIR.getDefaultState();
            StringBuilder nearby = new StringBuilder();
            for (BlockPos candidate : sideCandidates) {
                BlockState candidateState = mc.world.getBlockState(candidate);
                if (nearby.length() > 0) {
                    nearby.append(" | ");
                }
                nearby.append(candidate.toShortString()).append("=").append(candidateState);
                if (sideSlabPos == null && candidateState.isOf(Blocks.STONE_SLAB)) {
                    sideSlabPos = candidate;
                    sideSlab = candidateState;
                }
            }
            System.out.println("[SBSB-TRACE][SIDE_SLAB_RECURSIVE_NEARBY] " + nearby);
            if (sideSlabPos == null) {
                throw new RuntimeException("BSFB proof recursive side slab placement did not produce a slab near target="
                        + sideCarrierPos.toShortString()
                        + " face=east expected=" + expectedSideSlabPos.toShortString()
                        + " nearby=" + nearby);
            }

            double sideDy = SlabSupport.getYOffset(mc.world, sideSlabPos, sideSlab);
            boolean sideLowered = sideDy < -EPSILON;
            String sideType = sideSlab.contains(SlabBlock.TYPE) ? sideSlab.get(SlabBlock.TYPE).asString() : "missing";
            System.out.println("[SBSB-TRACE][SIDE_SLAB_BESIDE_RECURSIVE_LOWERED_DOUBLE] item=minecraft:stone_slab face=east target="
                    + sideCarrierPos.toShortString()
                    + " placePos=" + sideSlabPos.toShortString()
                    + " state=" + sideSlab
                    + " type=" + sideType
                    + " dy=" + sideDy
                    + " lowered=" + sideLowered);
            if (Math.abs(sideDy + 0.5d) > EPSILON || !sideLowered) {
                throw new RuntimeException("RED: side slab beside recursive lowered carrier should inherit lowered lane, but found dy="
                        + sideDy
                        + " lowered=" + sideLowered
                        + " state=" + sideSlab
                        + " at " + sideSlabPos.toShortString());
            }
        });

        // Step 8: persistent lowered carrier authority after its original side support is removed.
        // This uses direct support-link removal as a focused stand-in for the live player break.
        BlockPos persistentSupportLinkPos = sideCarrierPos;
        BlockPos persistentDependentPos = expectedSideSlabPos;
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    persistentSupportLinkPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    persistentDependentPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    persistentDependentPos,
                    world.getBlockState(persistentDependentPos));
        });
        waitForPlacementSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing for persistent side carrier setup");
            }
            BlockState supportState = mc.world.getBlockState(persistentSupportLinkPos);
            BlockState dependentState = mc.world.getBlockState(persistentDependentPos);
            double supportDy = SlabSupport.getYOffset(mc.world, persistentSupportLinkPos, supportState);
            double dependentDy = SlabSupport.getYOffset(mc.world, persistentDependentPos, dependentState);
            boolean supportLowered = supportDy < -EPSILON;
            boolean dependentLowered = dependentDy < -EPSILON;
            boolean dependentPersistentCarrier =
                    SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, persistentDependentPos, dependentState);
            System.out.println("[SBSB-TRACE][PERSISTENT_SIDE_CARRIER_BEFORE_REMOVAL] supportPos="
                    + persistentSupportLinkPos.toShortString()
                    + " supportState=" + supportState
                    + " supportDy=" + supportDy
                    + " supportLowered=" + supportLowered
                    + " dependentPos=" + persistentDependentPos.toShortString()
                    + " dependentState=" + dependentState
                    + " dependentDy=" + dependentDy
                    + " dependentLowered=" + dependentLowered
                    + " dependentPersistentCarrier=" + dependentPersistentCarrier);
            if (Math.abs(supportDy + 0.5d) > EPSILON || !supportLowered) {
                throw new RuntimeException("persistent side carrier setup expected support link "
                        + persistentSupportLinkPos.toShortString()
                        + " to start lowered, but got state="
                        + supportState
                        + " dy=" + supportDy
                        + " lowered=" + supportLowered);
            }
            if (Math.abs(dependentDy + 0.5d) > EPSILON || !dependentLowered) {
                throw new RuntimeException("persistent side carrier setup expected dependent "
                        + persistentDependentPos.toShortString()
                        + " to start lowered, but got state="
                        + dependentState
                        + " dy=" + dependentDy
                        + " lowered=" + dependentLowered);
            }
        });
        singleplayer.getServer().runOnServer(server -> server.getOverworld().setBlockState(
                persistentSupportLinkPos,
                Blocks.AIR.getDefaultState(),
                net.minecraft.block.Block.NOTIFY_LISTENERS));
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing for persistent side carrier assertion");
            }
            BlockState dependentState = mc.world.getBlockState(persistentDependentPos);
            double dependentDy = SlabSupport.getYOffset(mc.world, persistentDependentPos, dependentState);
            boolean dependentLowered = dependentDy < -EPSILON;
            boolean dependentPersistentCarrier =
                    SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, persistentDependentPos, dependentState);
            System.out.println("[SBSB-TRACE][PERSISTENT_SIDE_CARRIER_AFTER_REMOVAL] supportPos="
                    + persistentSupportLinkPos.toShortString()
                    + " supportState=" + mc.world.getBlockState(persistentSupportLinkPos)
                    + " dependentPos=" + persistentDependentPos.toShortString()
                    + " dependentState=" + dependentState
                    + " dependentDy=" + dependentDy
                    + " dependentLowered=" + dependentLowered
                    + " dependentPersistentCarrier=" + dependentPersistentCarrier);
            if (Math.abs(dependentDy + 0.5d) > EPSILON || !dependentLowered) {
                throw new RuntimeException("RED: dependent lowered slab at "
                        + persistentDependentPos.toShortString()
                        + " should remain lowered after direct support-link removal removes support link "
                        + persistentSupportLinkPos.toShortString()
                        + " because it was promoted to an independent lowered carrier, but got state="
                        + dependentState
                        + " dy=" + dependentDy
                        + " lowered=" + dependentLowered);
            }
        });
        singleplayer.getServer().runOnServer(server -> server.getOverworld().setBlockState(
                persistentSupportLinkPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                net.minecraft.block.Block.NOTIFY_LISTENERS));
        waitForPlacementSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();

        BlockPos retargetCarrierPos = UPPER_SLAB_POS;
        Direction retargetFace = Direction.WEST;
        BlockHitResult intendedRetargetHit = resolveLoweredSideFaceHit(retargetCarrierPos, retargetFace, SlabType.DOUBLE);
        BlockPos expectedRetargetPlacePos = retargetCarrierPos.offset(retargetFace);
        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException("client not ready to set slab-held stack for BSFB proof slab-held retarget audit");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
        });
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(retargetCarrierPos.getX() - 1.7d, eyeY + 2.0d, retargetCarrierPos.getZ() + 0.5d),
                intendedRetargetHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for BSFB proof slab-held retarget audit");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult finalTarget = mc.crosshairTarget;
            if (!(finalTarget instanceof BlockHitResult finalHit)) {
                throw new RuntimeException("RED: slab-held click on lowered carrier should place at intended side position, but actual placement was no block target from target initial="
                        + describeHit(intendedRetargetHit)
                        + " final=" + describeHit(finalTarget)
                        + " expectedPlacePos=" + expectedRetargetPlacePos.toShortString());
            }

            BlockPos finalPlacePos = finalHit.getBlockPos().offset(finalHit.getSide());
            BlockPos[] auditCandidates = new BlockPos[] {
                    expectedRetargetPlacePos,
                    finalPlacePos,
                    expectedRetargetPlacePos.up(),
                    expectedRetargetPlacePos.down(),
                    retargetCarrierPos.up(),
                    retargetCarrierPos.down(),
                    retargetCarrierPos.east(),
                    retargetCarrierPos.north(),
                    retargetCarrierPos.south()
            };
            BlockState[] before = new BlockState[auditCandidates.length];
            for (int i = 0; i < auditCandidates.length; i++) {
                before[i] = mc.world.getBlockState(auditCandidates[i]);
            }

            System.out.println("[SBSB-TRACE][SLAB_HELD_RETARGET_AUDIT_BEFORE] intended="
                    + describeHit(intendedRetargetHit)
                    + " final=" + describeHit(finalTarget)
                    + " expectedPlacePos=" + expectedRetargetPlacePos.toShortString()
                    + " finalPlacePos=" + finalPlacePos.toShortString()
                    + " targetState=" + mc.world.getBlockState(retargetCarrierPos));
            ActionResult retargetPlacement = mc.interactionManager.interactBlock(
                    mc.player,
                    Hand.MAIN_HAND,
                    finalHit);
            if (!retargetPlacement.isAccepted()) {
                throw new RuntimeException("RED: slab-held click on lowered carrier should place at intended side position, but actual placement was rejected from target initial="
                        + describeHit(intendedRetargetHit)
                        + " final=" + describeHit(finalTarget)
                        + " expectedPlacePos=" + expectedRetargetPlacePos.toShortString());
            }

            StringBuilder nearby = new StringBuilder();
            BlockPos actualPos = null;
            BlockState actualState = Blocks.AIR.getDefaultState();
            for (int i = 0; i < auditCandidates.length; i++) {
                BlockPos candidate = auditCandidates[i];
                BlockState after = mc.world.getBlockState(candidate);
                if (nearby.length() > 0) {
                    nearby.append(" | ");
                }
                nearby.append(candidate.toShortString()).append("=").append(after);
                if (actualPos == null && !after.equals(before[i])) {
                    actualPos = candidate;
                    actualState = after;
                }
            }
            System.out.println("[SBSB-TRACE][SLAB_HELD_RETARGET_AUDIT_AFTER] " + nearby);
            if (actualPos == null) {
                actualPos = expectedRetargetPlacePos;
                actualState = mc.world.getBlockState(expectedRetargetPlacePos);
            }
            double actualDy = actualState.isAir()
                    ? Double.NaN
                    : SlabSupport.getYOffset(mc.world, actualPos, actualState);
            boolean actualMatchesExpected = actualPos.equals(expectedRetargetPlacePos)
                    && actualState.isOf(Blocks.STONE_SLAB);
            System.out.println("[SBSB-TRACE][SLAB_HELD_RETARGET_AUDIT_RESULT] intended="
                    + describeHit(intendedRetargetHit)
                    + " final=" + describeHit(finalTarget)
                    + " expectedPlacePos=" + expectedRetargetPlacePos.toShortString()
                    + " actualPlacePos=" + actualPos.toShortString()
                    + " actualState=" + actualState
                    + " actualDy=" + actualDy
                    + " matchesExpected=" + actualMatchesExpected);
            if (!actualMatchesExpected) {
                throw new RuntimeException("RED: slab-held click on lowered carrier should place at intended side position, but actual placement was "
                        + actualPos.toShortString()
                        + "/" + actualState
                        + "/dy=" + actualDy
                        + " from target initial=" + describeHit(intendedRetargetHit)
                        + " final=" + describeHit(finalTarget)
                        + " expectedPlacePos=" + expectedRetargetPlacePos.toShortString());
            }
        });

        // Step 9: live wrong-face placement RED.
        // Aim from east-and-slightly-above the tower at the TOP face of the
        // lower lowered DOUBLE (TOP_SLAB_POS y=202). Vanilla DDA finds the
        // lower slab top face at (1.83, 202.5, 0.5) face=UP — a legal
        // slab-held placement target. The retarget mixin's DDA marches with
        // samplePos.up(), discovers UPPER_SLAB_POS east face at (2, ~202.6,
        // 0.5) — strictly closer to the eye — and replaces the crosshair via
        // the `<= bestDist2 + eps` gate.
        // Mirrors the live log:
        //   initial=BLOCK pos=X,Y,Z face=up   target=lowered slab
        //   final  =BLOCK pos=X,Y+1,Z face=east target=lowered DOUBLE
        //   decision=scan-side-slab-fired
        // The mixin steals a valid slab-held placement intent because there is
        // no guard preventing replacement when vanilla's initial hit is itself
        // a valid lowered slab face.
        Vec3d hijackEye = new Vec3d(
                TOP_SLAB_POS.getX() + 2.0d,
                TOP_SLAB_POS.getY() + 1.2d,
                TOP_SLAB_POS.getZ() + 0.5d);
        Vec3d hijackTarget = new Vec3d(
                TOP_SLAB_POS.getX() + 0.0d,
                TOP_SLAB_POS.getY() + 0.0d,
                TOP_SLAB_POS.getZ() + 0.5d);
        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException("client not ready to set slab-held stack for hijack audit");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
        });
        syncPlayerAim(ctx, singleplayer, hijackEye, hijackTarget);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null) {
                throw new RuntimeException("client not ready for slab-held hijack audit");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            net.minecraft.entity.Entity cam = mc.getCameraEntity();
            if (cam == null) {
                throw new RuntimeException("client camera missing for slab-held hijack audit");
            }
            HitResult vanillaTarget = cam.raycast(5.0d, 0.0f, false);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult finalTarget = mc.crosshairTarget;

            BlockPos vanillaPos = vanillaTarget instanceof BlockHitResult vbh ? vbh.getBlockPos() : null;
            Direction vanillaFace = vanillaTarget instanceof BlockHitResult vbh ? vbh.getSide() : null;
            BlockPos finalPos = finalTarget instanceof BlockHitResult fbh ? fbh.getBlockPos() : null;
            Direction finalFace = finalTarget instanceof BlockHitResult fbh ? fbh.getSide() : null;
            BlockPos expectedPlacePos = vanillaPos != null && vanillaFace != null
                    ? vanillaPos.offset(vanillaFace) : null;
            BlockPos finalPlacePos = finalPos != null && finalFace != null
                    ? finalPos.offset(finalFace) : null;

            System.out.println("[SBSB-TRACE][SLAB_HELD_WRONG_FACE_HIJACK_AUDIT] eye=" + hijackEye
                    + " target=" + hijackTarget
                    + " vanilla=" + describeHit(vanillaTarget)
                    + " final=" + describeHit(finalTarget)
                    + " expectedPlacePos=" + (expectedPlacePos == null ? "null" : expectedPlacePos.toShortString())
                    + " finalPlacePos=" + (finalPlacePos == null ? "null" : finalPlacePos.toShortString()));

            if (!(vanillaTarget instanceof BlockHitResult)) {
                throw new RuntimeException("hijack audit setup expected vanilla raycast to hit a block, got "
                        + describeHit(vanillaTarget));
            }
            if (!(finalTarget instanceof BlockHitResult)) {
                throw new RuntimeException("RED: slab-held retarget should preserve intended lowered face, but initial="
                        + describeHit(vanillaTarget)
                        + " final=" + describeHit(finalTarget)
                        + " expectedPlacePos=" + (expectedPlacePos == null ? "null" : expectedPlacePos.toShortString())
                        + " actual=null");
            }
            if (!finalPos.equals(vanillaPos) || finalFace != vanillaFace) {
                throw new RuntimeException("RED: slab-held retarget should preserve intended lowered face, but initial="
                        + describeHit(vanillaTarget)
                        + " final=" + describeHit(finalTarget)
                        + " expectedPlacePos=" + (expectedPlacePos == null ? "null" : expectedPlacePos.toShortString())
                        + " actual=pos=" + finalPos.toShortString()
                        + "/face=" + finalFace.asString()
                        + "/placePos=" + (finalPlacePos == null ? "null" : finalPlacePos.toShortString()));
            }
        });

        // Step 10: live recursive carrier click RED.
        // Mirrors the latest live inspect signature:
        //   initial=BLOCK anchored lowered full block face=east
        //   final=BLOCK adjacent lowered DOUBLE face=south
        //   decision=scan-side-slab-fired
        // This actually clicks the final target, so a wrong retarget proves the
        // misplaced slab position rather than only proving a target mismatch.
        BlockPos liveAnchorPos = ADJACENT_FULL_POS;
        BlockPos liveCarrierPos = liveAnchorPos.east();
        BlockPos liveWrongPlacePos = liveCarrierPos.south();
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    liveCarrierPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    liveWrongPlacePos,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    liveCarrierPos.up(),
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    liveCarrierPos.down(),
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, liveAnchorPos, world.getBlockState(liveAnchorPos));
        });
        waitForPlacementSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();

        Vec3d liveEye = new Vec3d(
                liveAnchorPos.getX() + 3.2873d,
                liveAnchorPos.getY() - 0.38d,
                liveAnchorPos.getZ() + 3.7099d);
        Vec3d liveIntendedHit = new Vec3d(
                liveAnchorPos.getX() + 1.0d,
                liveAnchorPos.getY() - 0.0170d,
                liveAnchorPos.getZ() + 0.1977d);
        syncPlayerAim(ctx, singleplayer, liveEye, liveIntendedHit);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live recursive carrier click audit");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            net.minecraft.entity.Entity cam = mc.getCameraEntity();
            if (cam == null) {
                throw new RuntimeException("client camera missing for live recursive carrier click audit");
            }
            HitResult vanillaTarget = cam.raycast(5.0d, 0.0f, false);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult finalTarget = mc.crosshairTarget;
            if (!(vanillaTarget instanceof BlockHitResult vanillaHit)
                    || !vanillaHit.getBlockPos().equals(liveAnchorPos)
                    || vanillaHit.getSide() != Direction.EAST) {
                throw new RuntimeException("live recursive carrier setup expected vanilla target="
                        + liveAnchorPos.toShortString()
                        + "/face=east, got "
                        + describeHit(vanillaTarget));
            }

            if (!(finalTarget instanceof BlockHitResult finalHit)) {
                throw new RuntimeException("RED: live recursive carrier placement should preserve intended face=east/placePos="
                        + liveCarrierPos.toShortString()
                        + ", but target=" + describeHit(finalTarget)
                        + " placePos=null state=null dy=NaN expected=target="
                        + liveAnchorPos.toShortString()
                        + "/face=east/placePos=" + liveCarrierPos.toShortString());
            }

            ActionResult livePlacement = mc.interactionManager.interactBlock(
                    mc.player,
                    Hand.MAIN_HAND,
                    finalHit);
            BlockPos actualPlacePos = finalHit.getBlockPos().offset(finalHit.getSide());
            BlockState actualState = mc.world.getBlockState(actualPlacePos);
            double actualDy = actualState.isAir()
                    ? Double.NaN
                    : SlabSupport.getYOffset(mc.world, actualPlacePos, actualState);
            System.out.println("[SBSB-TRACE][LIVE_RECURSIVE_CARRIER_CLICK] vanilla="
                    + describeHit(vanillaTarget)
                    + " final=" + describeHit(finalTarget)
                    + " expectedPlacePos=" + liveCarrierPos.toShortString()
                    + " actualPlacePos=" + actualPlacePos.toShortString()
                    + " actualState=" + actualState
                    + " actualDy=" + actualDy
                    + " result=" + livePlacement);
            if (!finalHit.getBlockPos().equals(liveAnchorPos)
                    || finalHit.getSide() != Direction.EAST
                    || !actualPlacePos.equals(liveCarrierPos)) {
                throw new RuntimeException("RED: live recursive carrier placement should preserve intended face=east/placePos="
                        + liveCarrierPos.toShortString()
                        + ", but target=" + describeHit(finalTarget)
                        + " placePos=" + actualPlacePos.toShortString()
                        + " state=" + actualState
                        + " dy=" + actualDy
                        + " expected=target=" + liveAnchorPos.toShortString()
                        + "/face=east/placePos=" + liveCarrierPos.toShortString()
                        + " vanilla=" + describeHit(vanillaTarget)
                        + " result=" + livePlacement);
            }
        });
    }

    private static void runJuliaLiveTraceSideSlabRetargetRed(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        String proofName = "RED_JULIA_LIVE_TRACE_SLAB_HELD_SIDE_SLAB_RETARGET_STEAL";
        BlockPos initialPos = new BlockPos(19, -59, 0);
        Direction initialFace = Direction.EAST;
        Vec3d initialHitVec = new Vec3d(19.353d, -58.445d, 0.408d);
        BlockPos dangerousCandidate = new BlockPos(17, -58, -1);
        BlockPos candidateSupport = dangerousCandidate.north();
        Vec3d eye = new Vec3d(14.566d, -58.380d, -1.035d);
        Vec3d end = new Vec3d(20.311d, -58.458d, 0.696d);
        BlockHitResult initialHit = new BlockHitResult(initialHitVec, initialFace, initialPos, false);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = 14; x <= 21; x++) {
                for (int y = -60; y <= -57; y++) {
                    for (int z = -3; z <= 1; z++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }
            world.setBlockState(initialPos, Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(dangerousCandidate,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(candidateSupport.down(),
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(candidateSupport, Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, candidateSupport, world.getBlockState(candidateSupport));
            forceAddPersistentLoweredSlabCarrierForTest(
                    world, dangerousCandidate, proofName);
        });
        waitForPlacementSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null) {
                throw new RuntimeException(proofName + " client not ready");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            forceAddPersistentLoweredSlabCarrierForClientTest(
                    mc.world, dangerousCandidate, proofName);
            var cam = mc.getCameraEntity();
            if (cam == null) {
                throw new RuntimeException(proofName + " client camera missing");
            }

            if (!initialHit.getBlockPos().equals(initialPos) || initialHit.getSide() != initialFace) {
                throw new RuntimeException(proofName + " setup initial mismatch initial=" + describeHit(initialHit));
            }
            BlockState candidateState = mc.world.getBlockState(dangerousCandidate);
            double candidateDy = SlabSupport.getYOffset(mc.world, dangerousCandidate, candidateState);
            BlockHitResult outlineHit = candidateState.getOutlineShape(mc.world, dangerousCandidate,
                    ShapeContext.of(cam)).raycast(eye, end, dangerousCandidate);
            BlockHitResult nativeHit = candidateState.getRaycastShape(mc.world, dangerousCandidate)
                    .raycast(eye, end, dangerousCandidate);
            BlockHitResult retarget = SlabbedRetargetTestHooks.findLoweredSideSlabRetarget(
                    mc.world, cam, eye, end, initialHit, true);
            boolean sideSlabRetargetFired = retarget != null;
            HitResult finalTarget = sideSlabRetargetFired ? retarget : initialHit;

            System.out.println("[SBSB-TRACE][" + proofName + "]"
                    + " initial=" + describeHit(initialHit)
                    + " final=" + describeHit(finalTarget)
                    + " sideSlabCandidate=" + dangerousCandidate.toShortString()
                    + " candidateState=" + candidateState
                    + " candidateDy=" + candidateDy
                    + " outline=" + describeHit(outlineHit)
                    + " nativeRaycast=" + describeHit(nativeHit)
                    + " sideSlabRetargetFired=" + sideSlabRetargetFired);

            if (!candidateState.isOf(Blocks.STONE_SLAB)
                    || !candidateState.contains(SlabBlock.TYPE)
                    || candidateState.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(candidateDy + 0.5d) > EPSILON
                    || outlineHit == null
                    || nativeHit != null) {
                throw new RuntimeException(proofName + " setup expected lowered BOTTOM candidate"
                        + " with outline hit and native raycast miss, candidate=" + candidateState
                        + "/dy=" + candidateDy
                        + " outline=" + describeHit(outlineHit)
                        + " nativeRaycast=" + describeHit(nativeHit));
            }

            if (retarget != null
                    && retarget.getBlockPos().equals(dangerousCandidate)
                    && retarget.getSide() == Direction.WEST) {
                throw new RuntimeException("RED: " + proofName
                        + " side-slab retarget stole ownership from valid live initial target"
                        + " initial=" + describeHit(initialHit)
                        + " final=" + describeHit(retarget)
                        + " candidateDy=" + candidateDy
                        + " outline=" + describeHit(outlineHit)
                        + " nativeRaycast=miss"
                        + " sideSlabRetargetFired=true");
            }

            if (retarget != null) {
                throw new RuntimeException("RED: " + proofName
                        + " unexpected slab-held retarget from Julia trace"
                        + " initial=" + describeHit(initialHit)
                        + " final=" + describeHit(retarget)
                        + " sideSlabRetargetFired=true");
            }
        });
    }

    private static void runDoubleSlabFullHeightCarrierTriadProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        singleplayer.getServer().runOnServer(server -> {
            World world = server.getOverworld();

            BlockPos caseA = SUPPORT_POS.add(8, 0, 0);
            BlockPos caseB = SUPPORT_POS.add(14, 0, 0);
            BlockPos caseC = SUPPORT_POS.add(21, 0, 0);
            clearTriadArea(world, caseA, 4);
            clearTriadArea(world, caseB, 5);
            clearTriadArea(world, caseC, 6);

            BlockPos aSupport = caseA;
            BlockPos aCarrier = aSupport.up();
            BlockPos aTarget = aCarrier.east();
            seedAnchoredFullCarrier(world, aSupport, aCarrier);
            world.setBlockState(aTarget, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(
                    world,
                    aTarget,
                    world.getBlockState(aTarget),
                    aCarrier,
                    world.getBlockState(aCarrier));
            assertLoweredFullTarget(world, "FULL_FULL_REFERENCE", aCarrier, aTarget);

            BlockPos bSupport = caseB;
            BlockPos bFullCarrier = bSupport.up();
            BlockPos bDoubleCarrier = bFullCarrier.east();
            BlockPos bTarget = bDoubleCarrier.east();
            seedAnchoredFullCarrier(world, bSupport, bFullCarrier);
            seedLoweredDoubleCarrier(world, bDoubleCarrier);
            world.setBlockState(bTarget, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(
                    world,
                    bTarget,
                    world.getBlockState(bTarget),
                    bDoubleCarrier,
                    world.getBlockState(bDoubleCarrier));
            assertLoweredDoubleCarrier(world, "FULL_DOUBLE_BRIDGE", bDoubleCarrier);
            assertLoweredFullTarget(world, "FULL_DOUBLE_BRIDGE", bDoubleCarrier, bTarget);

            BlockPos cSupport = caseC;
            BlockPos cFullSeed = cSupport.up();
            BlockPos cFirstDouble = cFullSeed.east();
            BlockPos cSecondDouble = cFirstDouble.east();
            BlockPos cTarget = cSecondDouble.east();
            seedAnchoredFullCarrier(world, cSupport, cFullSeed);
            seedLoweredDoubleCarrier(world, cFirstDouble);
            seedLoweredDoubleCarrier(world, cSecondDouble);
            world.setBlockState(cTarget, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(
                    world,
                    cTarget,
                    world.getBlockState(cTarget),
                    cSecondDouble,
                    world.getBlockState(cSecondDouble));
            assertLoweredDoubleCarrier(world, "DOUBLE_DOUBLE_BRIDGE:first", cFirstDouble);
            assertLoweredDoubleCarrier(world, "DOUBLE_DOUBLE_BRIDGE:second", cSecondDouble);
            assertLoweredFullTarget(world, "DOUBLE_DOUBLE_BRIDGE", cSecondDouble, cTarget);
        });
        waitForPlacementSync(ctx);
    }

    private static void clearTriadArea(World world, BlockPos supportOrigin, int width) {
        for (int x = -1; x <= width; x++) {
            for (int y = 0; y <= 3; y++) {
                BlockPos pos = supportOrigin.add(x, y, 0);
                SlabAnchorAttachment.removeAnchor(world, pos);
                SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, pos);
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            }
        }
    }

    private static void seedAnchoredFullCarrier(World world, BlockPos supportPos, BlockPos carrierPos) {
        world.setBlockState(
                supportPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        world.setBlockState(carrierPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(world, carrierPos, world.getBlockState(carrierPos));
    }

    private static void seedLoweredDoubleCarrier(World world, BlockPos carrierPos) {
        world.setBlockState(
                carrierPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, carrierPos, world.getBlockState(carrierPos));
    }

    private static void forceAddPersistentLoweredSlabCarrierForTest(
            World world,
            BlockPos carrierPos,
            String proofName
    ) {
        BlockState state = world.getBlockState(carrierPos);
        if (!state.getBlock().equals(Blocks.AIR)) {
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, carrierPos, state);
        }
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, carrierPos, state)) {
            throw new RuntimeException("RED: " + proofName
                    + " helper failed to seed persistent lowered slab carrier on server for "
                    + carrierPos.toShortString());
        }
    }

    private static void forceAddPersistentLoweredSlabCarrierForClientTest(
            World world,
            BlockPos carrierPos,
            String proofName
    ) {
        forceAddPersistentLoweredSlabCarrierForTest(world, carrierPos, proofName);
        Predicate<BlockPos> previous = SlabAnchorAttachment.clientLoweredSlabCarrierLookup;
        SlabAnchorAttachment.clientLoweredSlabCarrierLookup = pos -> pos.equals(carrierPos)
                || previous != null && previous.test(pos);
        BlockState state = world.getBlockState(carrierPos);
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, carrierPos, state)) {
            throw new RuntimeException("RED: " + proofName
                    + " helper failed to seed persistent lowered slab carrier on client for "
                    + carrierPos.toShortString());
        }
    }

    private static void assertLoweredDoubleCarrier(World world, String label, BlockPos carrierPos) {
        BlockState carrierState = world.getBlockState(carrierPos);
        double carrierDy = SlabSupport.getYOffset(world, carrierPos, carrierState);
        boolean fullHeightCarrier = SlabSupport.isFullHeightLoweredCarrier(world, carrierPos, carrierState);
        boolean persistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, carrierPos, carrierState);
        System.out.println("[SBSB-TRACE][DOUBLE_FULL_HEIGHT_CARRIER_TRIAD] case=" + label
                + " carrierPos=" + carrierPos.toShortString()
                + " carrierState=" + carrierState
                + " carrierDy=" + carrierDy
                + " carrierFullHeight=" + fullHeightCarrier
                + " carrierPersistent=" + persistentCarrier);
        if (!carrierState.isOf(Blocks.STONE_SLAB)
                || !carrierState.contains(SlabBlock.TYPE)
                || carrierState.get(SlabBlock.TYPE) != SlabType.DOUBLE
                || Math.abs(carrierDy + 0.5d) > EPSILON
                || !fullHeightCarrier) {
            throw new RuntimeException("RED: " + label
                    + " expected lowered DOUBLE carrier dy=-0.500/fullHeight=true at "
                    + carrierPos.toShortString()
                    + ", state=" + carrierState
                    + " dy=" + carrierDy
                    + " fullHeightCarrier=" + fullHeightCarrier
                    + " persistentCarrier=" + persistentCarrier);
        }
    }

    private static void assertLoweredFullTarget(World world, String label, BlockPos carrierPos, BlockPos targetPos) {
        BlockState carrierState = world.getBlockState(carrierPos);
        double carrierDy = SlabSupport.getYOffset(world, carrierPos, carrierState);
        boolean carrierFullHeight = SlabSupport.isFullHeightLoweredCarrier(world, carrierPos, carrierState);
        BlockState targetState = world.getBlockState(targetPos);
        double targetDy = SlabSupport.getYOffset(world, targetPos, targetState);
        boolean targetAnchored = SlabAnchorAttachment.isAnchored(world, targetPos);
        System.out.println("[SBSB-TRACE][DOUBLE_FULL_HEIGHT_CARRIER_TRIAD] case=" + label
                + " carrierPos=" + carrierPos.toShortString()
                + " carrierState=" + carrierState
                + " carrierDy=" + carrierDy
                + " carrierFullHeight=" + carrierFullHeight
                + " targetPos=" + targetPos.toShortString()
                + " targetState=" + targetState
                + " targetDy=" + targetDy
                + " targetAnchored=" + targetAnchored);
        if (!carrierFullHeight
                || Math.abs(carrierDy + 0.5d) > EPSILON
                || !targetState.isOf(Blocks.STONE)
                || Math.abs(targetDy + 0.5d) > EPSILON
                || !targetAnchored) {
            throw new RuntimeException("RED: " + label
                    + " expected target STONE to inherit lowered full-height carrier ownership at "
                    + targetPos.toShortString()
                    + ", carrier=" + carrierState
                    + "/dy=" + carrierDy
                    + "/fullHeight=" + carrierFullHeight
                    + " target=" + targetState
                    + "/dy=" + targetDy
                    + "/anchored=" + targetAnchored);
        }
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
        ctx.runOnClient(mc -> {
            if (mc.player == null) {
                throw new RuntimeException("client not ready to sync BSFB proof camera aim");
            }
            mc.player.setPosition(eye.x, feetY, eye.z);
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
    }

    private static void waitForPlacementSync(ClientGameTestContext ctx) {
        for (int i = 0; i < 3; i++) {
            ctx.waitTick();
        }
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

    private static String describeHit(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return hit == null ? "null" : hit.getType().toString();
        }
        return "pos=" + blockHit.getBlockPos().toShortString()
                + " face=" + blockHit.getSide().asString()
                + " hit=" + blockHit.getPos();
    }
}
