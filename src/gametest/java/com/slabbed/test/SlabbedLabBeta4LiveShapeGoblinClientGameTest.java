package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Opt-in diagnostic harness for Julia's beta4 canonical live shape.
 *
 * <p>No gameplay behavior is implemented here. The test builds the live
 * structure, proves the source truth, then records the current lower/upper/top
 * side behavior honestly behind {@code -Dslabbed.beta4LiveShapeGoblin=true}.
 */
public final class SlabbedLabBeta4LiveShapeGoblinClientGameTest implements FabricClientGameTest {
    private static final String OPT_IN = "slabbed.beta4LiveShapeGoblin";
    private static final double EPSILON = 1.0e-6d;

    private static final BlockPos BOTTOM_SLAB_A = new BlockPos(40, 200, 8);
    private static final BlockPos BOTTOM_SLAB_B = BOTTOM_SLAB_A.east();
    private static final BlockPos BRIDGE_FULL_A = BOTTOM_SLAB_A.up();
    private static final BlockPos BRIDGE_FULL_B = BOTTOM_SLAB_B.up();
    private static final BlockPos TOP_SLAB_A = BRIDGE_FULL_A.up();
    private static final BlockPos TOP_SLAB_B = BRIDGE_FULL_B.up();
    private static final BlockPos UPPER_FULL = TOP_SLAB_A.up();

    private static final Direction ANGLE_A_FACE = Direction.WEST;
    private static final Direction ANGLE_B_FACE = Direction.EAST;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!Boolean.getBoolean(OPT_IN)) {
            return;
        }

        System.out.println("[JULIA_BETA4_LIVE_GOBLIN_START]"
                + " flag=-Dslabbed.beta4LiveShapeGoblin=true"
                + " structure=two_bottom_slabs_full_block_bridge_two_top_slabs_one_upper_full_block");

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            Verdicts verdicts = new Verdicts();
            if (!proveFreshStructure(ctx, singleplayer)) {
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_DONE] status=STRUCTURE_FAIL");
                return;
            }
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_BASELINE]"
                    + " lowerA=PENDING lowerB=PENDING upperA=PENDING upperB=PENDING"
                    + " topFace=PENDING supportBreak=PENDING hitbox=PENDING");

            verdicts.lowerA = runSideCase(ctx, singleplayer, verdicts, "SIDE_LOWER_A", ANGLE_A_FACE, -0.75d, SlabType.BOTTOM);
            verdicts.lowerB = runSideCase(ctx, singleplayer, verdicts, "SIDE_LOWER_B", ANGLE_B_FACE, -0.75d, SlabType.BOTTOM);
            verdicts.upperA = runSideCase(ctx, singleplayer, verdicts, "SIDE_UPPER_A", ANGLE_A_FACE, 0.25d, SlabType.TOP);
            verdicts.upperB = runSideCase(ctx, singleplayer, verdicts, "SIDE_UPPER_B", ANGLE_B_FACE, 0.25d, SlabType.TOP);
            verdicts.topFace = runTopFaceCase(ctx, singleplayer, verdicts);
            verdicts.supportBreak = runSupportBreakCase(ctx, singleplayer, verdicts);

            verdicts.hitbox = verdicts.wrongOwner || verdicts.miss ? "RED" : "GREEN";
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_HITBOX_" + verdicts.hitbox + "]"
                    + " wrongOwner=" + verdicts.wrongOwner
                    + " miss=" + verdicts.miss
                    + " observedCases=" + verdicts.observedCases);

            String releaseBlockers = releaseBlockers(verdicts);
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_SUMMARY]"
                    + " structure=GREEN"
                    + " lowerA=" + verdicts.lowerA
                    + " lowerB=" + verdicts.lowerB
                    + " upperA=" + verdicts.upperA
                    + " upperB=" + verdicts.upperB
                    + " topFace=" + verdicts.topFace
                    + " supportBreak=" + verdicts.supportBreak
                    + " hitbox=" + verdicts.hitbox
                    + " ghost=" + verdicts.ghost
                    + " jump=" + verdicts.jump
                    + " wrongOwner=" + verdicts.wrongOwner
                    + " releaseBlockers=" + releaseBlockers);
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_DONE] status=OK");
        }
    }

    private static boolean proveFreshStructure(ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        seedCanonicalStructure(singleplayer);
        waitForClient(ctx, singleplayer, 5);
        final boolean[] green = {false};
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_FAIL] reason=client_world_missing");
                return;
            }
            green[0] = emitStructureProof(mc.world);
        });
        return green[0];
    }

    private static String runSideCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Verdicts verdicts,
            String caseName,
            Direction face,
            double hitYOffset,
            SlabType expectedType
    ) {
        seedCanonicalStructure(singleplayer);
        waitForClient(ctx, singleplayer, 5);
        final BlockPos candidate = UPPER_FULL.offset(face);
        final BlockHitResult hit = faceHit(UPPER_FULL, face, hitYOffset);
        final String[] verdict = {"RED"};
        final boolean[] miss = {false};
        final boolean[] wrongOwner = {false};

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncAim(ctx, singleplayer, eyeFor(face), hit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_FAIL] case="
                        + caseName + " reason=client_not_ready");
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult beforeTarget = mc.crosshairTarget;
            miss[0] = beforeTarget == null || beforeTarget.getType() == HitResult.Type.MISS;
            wrongOwner[0] = owner(beforeTarget) != null && !UPPER_FULL.equals(owner(beforeTarget));
            BlockState sourceBefore = mc.world.getBlockState(UPPER_FULL);
            BlockState candidateBefore = mc.world.getBlockState(candidate);
            ActionResult action = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_TRACE]"
                    + " case=" + caseName
                    + " phase=click"
                    + " expectedOwner=" + UPPER_FULL.toShortString()
                    + " actualTarget=" + describeHit(beforeTarget)
                    + " face=" + hit.getSide().asString()
                    + " sourceDy=" + dy(mc.world, UPPER_FULL, sourceBefore)
                    + " candidateDy=" + dy(mc.world, candidate, candidateBefore)
                    + " targetMiss=" + miss[0]
                    + " targetJumped=" + wrongOwner[0]
                    + " hitY=" + hit.getPos().y
                    + " hitBand=" + hitBand(hit)
                    + " result=" + action
                    + " candidateBefore=" + describeBlock(mc.world, candidate));
        });
        waitForClient(ctx, singleplayer, 2);
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_FAIL] case="
                        + caseName + " reason=client_world_missing_after_click");
            }
            BlockState after = mc.world.getBlockState(candidate);
            double candidateDy = dy(mc.world, candidate, after);
            boolean legal = after.isOf(Blocks.STONE_SLAB)
                    && after.contains(SlabBlock.TYPE)
                    && after.get(SlabBlock.TYPE) == expectedType
                    && Math.abs(candidateDy + 0.5d) <= EPSILON;
            boolean ghost = !legal && after.isOf(Blocks.STONE_SLAB);
            verdict[0] = legal ? "GREEN" : "RED";
            verdicts.miss |= miss[0];
            verdicts.wrongOwner |= wrongOwner[0];
            verdicts.ghost |= ghost;
            verdicts.observedCases++;
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_" + caseName + "_" + verdict[0] + "]"
                    + " expected=stone_slab[type=" + expectedType.asString() + "] dy=-0.5"
                    + " candidate=" + describeBlock(mc.world, candidate)
                    + " source=" + describeBlock(mc.world, UPPER_FULL)
                    + " targetMiss=" + miss[0]
                    + " wrongOwner=" + wrongOwner[0]
                    + " ghost=" + ghost);
        });
        return verdict[0];
    }

    private static String runTopFaceCase(ClientGameTestContext ctx, TestSingleplayerContext singleplayer, Verdicts verdicts) {
        seedCanonicalStructure(singleplayer);
        waitForClient(ctx, singleplayer, 5);
        final BlockPos expectedTop = UPPER_FULL.up();
        final BlockPos skippedTop = UPPER_FULL.up(2);
        final BlockHitResult hit = upHit(UPPER_FULL);
        final String[] verdict = {"RED"};

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncAim(ctx, singleplayer, new Vec3d(UPPER_FULL.getX() + 0.5d, UPPER_FULL.getY() + 2.4d,
                UPPER_FULL.getZ() + 2.6d), hit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_FAIL] case=TOP_FACE reason=client_not_ready");
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult beforeTarget = mc.crosshairTarget;
            ActionResult action = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_TRACE]"
                    + " case=TOP_FACE"
                    + " phase=click"
                    + " expectedOwner=" + UPPER_FULL.toShortString()
                    + " actualTarget=" + describeHit(beforeTarget)
                    + " face=" + hit.getSide().asString()
                    + " sourceDy=" + dy(mc.world, UPPER_FULL, mc.world.getBlockState(UPPER_FULL))
                    + " candidateDy=" + dy(mc.world, expectedTop, mc.world.getBlockState(expectedTop))
                    + " targetMiss=" + (beforeTarget == null || beforeTarget.getType() == HitResult.Type.MISS)
                    + " targetJumped=" + (owner(beforeTarget) != null && !UPPER_FULL.equals(owner(beforeTarget)))
                    + " hitY=" + hit.getPos().y
                    + " hitBand=" + hitBand(hit)
                    + " result=" + action);
        });
        waitForClient(ctx, singleplayer, 2);
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_FAIL] case=TOP_FACE reason=client_world_missing_after_click");
            }
            BlockState actual = mc.world.getBlockState(expectedTop);
            BlockState skipped = mc.world.getBlockState(skippedTop);
            BlockState source = mc.world.getBlockState(UPPER_FULL);
            double actualDy = dy(mc.world, expectedTop, actual);
            double sourceDy = dy(mc.world, UPPER_FULL, source);
            boolean legalTop = actual.isOf(Blocks.STONE_SLAB)
                    && actual.contains(SlabBlock.TYPE)
                    && actual.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(actualDy) <= EPSILON;
            boolean ghostOrSkip = actual.isOf(Blocks.STONE_SLAB) || skipped.isOf(Blocks.STONE_SLAB);
            boolean cleanRejectPreserve = !ghostOrSkip
                    && source.isOf(Blocks.STONE)
                    && Math.abs(sourceDy + 1.0d) <= EPSILON;
            verdict[0] = (legalTop || cleanRejectPreserve) ? "GREEN" : "RED";
            verdicts.ghost |= !legalTop && ghostOrSkip;
            verdicts.observedCases++;
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_TOP_FACE_GHOST_" + verdict[0] + "]"
                    + " expected=clean_reject_preserve_or_stone_slab[type=bottom] dy=0.0"
                    + " topCandidate=" + describeBlock(mc.world, expectedTop)
                    + " skippedCandidate=" + describeBlock(mc.world, skippedTop)
                    + " ghostOrSkipSlabAppeared=" + ghostOrSkip
                    + " cleanRejectPreserve=" + cleanRejectPreserve
                    + " source=" + describeBlock(mc.world, UPPER_FULL));
        });
        return verdict[0];
    }

    private static String runSupportBreakCase(ClientGameTestContext ctx, TestSingleplayerContext singleplayer, Verdicts verdicts) {
        seedCanonicalStructure(singleplayer);
        waitForClient(ctx, singleplayer, 5);
        final BlockPos sideCandidate = UPPER_FULL.offset(ANGLE_A_FACE);
        final BlockHitResult upperHit = faceHit(UPPER_FULL, ANGLE_A_FACE, 0.25d);
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncAim(ctx, singleplayer, eyeFor(ANGLE_A_FACE), upperHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("[JULIA_BETA4_LIVE_GOBLIN_SUPPORT_BREAK_RED] reason=client_not_ready_before_side_place");
            }
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, upperHit);
        });
        waitForClient(ctx, singleplayer, 3);

        final String[] verdict = {"GREEN"};
        singleplayer.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            BlockState upperBefore = world.getBlockState(UPPER_FULL);
            BlockState bridgeABefore = world.getBlockState(BRIDGE_FULL_A);
            BlockState bridgeBBefore = world.getBlockState(BRIDGE_FULL_B);
            BlockState topABefore = world.getBlockState(TOP_SLAB_A);
            BlockState topBBefore = world.getBlockState(TOP_SLAB_B);
            BlockState sideBefore = world.getBlockState(sideCandidate);
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_SUPPORT_BREAK_GREEN]"
                    + " phase=before_break"
                    + " breakPos=" + TOP_SLAB_A.toShortString()
                    + " upperFullBlock=" + describeBlock(world, UPPER_FULL, upperBefore)
                    + " bridgeFullBlockA=" + describeBlock(world, BRIDGE_FULL_A, bridgeABefore)
                    + " bridgeFullBlockB=" + describeBlock(world, BRIDGE_FULL_B, bridgeBBefore)
                    + " topSlabA=" + describeBlock(world, TOP_SLAB_A, topABefore)
                    + " topSlabB=" + describeBlock(world, TOP_SLAB_B, topBBefore)
                    + " sideSlab=" + describeBlock(world, sideCandidate, sideBefore));
            SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, TOP_SLAB_A);
            world.breakBlock(TOP_SLAB_A, false);
            world.updateNeighborsAlways(TOP_SLAB_A, Blocks.AIR, null);
        });
        waitForClient(ctx, singleplayer, 5);
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                verdict[0] = "RED";
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_SUPPORT_BREAK_RED] reason=client_world_missing_after_break");
                return;
            }
            BlockState upperAfter = mc.world.getBlockState(UPPER_FULL);
            BlockState bridgeAAfter = mc.world.getBlockState(BRIDGE_FULL_A);
            BlockState bridgeBAfter = mc.world.getBlockState(BRIDGE_FULL_B);
            BlockState topAAfter = mc.world.getBlockState(TOP_SLAB_A);
            BlockState topBAfter = mc.world.getBlockState(TOP_SLAB_B);
            BlockState sideAfter = mc.world.getBlockState(sideCandidate);
            boolean jump = (upperAfter.isOf(Blocks.STONE) && Math.abs(dy(mc.world, UPPER_FULL, upperAfter) + 1.0d) > EPSILON)
                    || (bridgeAAfter.isOf(Blocks.STONE) && Math.abs(dy(mc.world, BRIDGE_FULL_A, bridgeAAfter) + 0.5d) > EPSILON)
                    || (bridgeBAfter.isOf(Blocks.STONE) && Math.abs(dy(mc.world, BRIDGE_FULL_B, bridgeBAfter) + 0.5d) > EPSILON)
                    || (sideAfter.isOf(Blocks.STONE_SLAB) && Math.abs(dy(mc.world, sideCandidate, sideAfter) + 0.5d) > EPSILON);
            verdict[0] = jump ? "RED" : "GREEN";
            verdicts.jump |= jump;
            verdicts.observedCases++;
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_SUPPORT_BREAK_" + verdict[0] + "]"
                    + " phase=after_break"
                    + " breakPos=" + TOP_SLAB_A.toShortString()
                    + " jump=" + jump
                    + " upperFullBlock=" + describeBlock(mc.world, UPPER_FULL, upperAfter)
                    + " bridgeFullBlockA=" + describeBlock(mc.world, BRIDGE_FULL_A, bridgeAAfter)
                    + " bridgeFullBlockB=" + describeBlock(mc.world, BRIDGE_FULL_B, bridgeBAfter)
                    + " topSlabA=" + describeBlock(mc.world, TOP_SLAB_A, topAAfter)
                    + " topSlabB=" + describeBlock(mc.world, TOP_SLAB_B, topBAfter)
                    + " sideSlab=" + describeBlock(mc.world, sideCandidate, sideAfter));
        });
        return verdict[0];
    }

    private static void seedCanonicalStructure(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            clearArea(world);
            world.setBlockState(BOTTOM_SLAB_A, bottomSlab(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, BOTTOM_SLAB_A, world.getBlockState(BOTTOM_SLAB_A));
            world.setBlockState(BOTTOM_SLAB_B, bottomSlab(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, BOTTOM_SLAB_B, world.getBlockState(BOTTOM_SLAB_B));

            world.setBlockState(BRIDGE_FULL_A, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, BRIDGE_FULL_A, world.getBlockState(BRIDGE_FULL_A));
            world.setBlockState(BRIDGE_FULL_B, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, BRIDGE_FULL_B, world.getBlockState(BRIDGE_FULL_B));

            world.setBlockState(TOP_SLAB_A, bottomSlab(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, TOP_SLAB_A, world.getBlockState(TOP_SLAB_A));
            world.setBlockState(TOP_SLAB_B, bottomSlab(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, TOP_SLAB_B, world.getBlockState(TOP_SLAB_B));

            world.setBlockState(UPPER_FULL, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, UPPER_FULL, world.getBlockState(UPPER_FULL));
            SlabAnchorAttachment.addCompoundFullBlockAnchor(world, UPPER_FULL, world.getBlockState(UPPER_FULL));

            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
    }

    private static boolean emitStructureProof(net.minecraft.world.BlockView world) {
        boolean bottomA = isBottomSlab(world, BOTTOM_SLAB_A);
        boolean bottomB = isBottomSlab(world, BOTTOM_SLAB_B);
        boolean bridgeA = isStoneDy(world, BRIDGE_FULL_A, -0.5d);
        boolean bridgeB = isStoneDy(world, BRIDGE_FULL_B, -0.5d);
        boolean topA = isBottomSlab(world, TOP_SLAB_A) && isDy(world, TOP_SLAB_A, -0.5d);
        boolean topB = isBottomSlab(world, TOP_SLAB_B) && isDy(world, TOP_SLAB_B, -0.5d);
        boolean upper = isStoneDy(world, UPPER_FULL, -1.0d)
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(world, UPPER_FULL);
        String marker = bottomA && bottomB && bridgeA && bridgeB && topA && topB && upper
                ? "[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_GREEN]"
                : "[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_FAIL]";
        System.out.println(marker
                + " bottomSlabA=" + describeBlock(world, BOTTOM_SLAB_A)
                + " bottomSlabB=" + describeBlock(world, BOTTOM_SLAB_B)
                + " bridgeFullBlockA=" + describeBlock(world, BRIDGE_FULL_A)
                + " bridgeFullBlockB=" + describeBlock(world, BRIDGE_FULL_B)
                + " topSlabA=" + describeBlock(world, TOP_SLAB_A)
                + " topSlabB=" + describeBlock(world, TOP_SLAB_B)
                + " upperFullBlock=" + describeBlock(world, UPPER_FULL)
                + " upperFullBlockCompound=" + SlabAnchorAttachment.isCompoundFullBlockAnchor(world, UPPER_FULL)
                + " upperFullBlockAnchored=" + SlabAnchorAttachment.isAnchored(world, UPPER_FULL)
                + " lowerHalfSideCandidate=" + UPPER_FULL.offset(ANGLE_A_FACE).toShortString()
                + " upperHalfSideCandidate=" + UPPER_FULL.offset(ANGLE_B_FACE).toShortString()
                + " topFaceCandidate=" + UPPER_FULL.up().toShortString()
                + " supportBreakPositions=" + TOP_SLAB_A.toShortString() + "," + BOTTOM_SLAB_A.toShortString());
        return bottomA && bottomB && bridgeA && bridgeB && topA && topB && upper;
    }

    private static void clearArea(World world) {
        for (int x = BOTTOM_SLAB_A.getX() - 3; x <= BOTTOM_SLAB_A.getX() + 4; x++) {
            for (int y = BOTTOM_SLAB_A.getY() - 2; y <= BOTTOM_SLAB_A.getY() + 6; y++) {
                for (int z = BOTTOM_SLAB_A.getZ() - 3; z <= BOTTOM_SLAB_A.getZ() + 3; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    SlabAnchorAttachment.removeAnchor(world, pos);
                    SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, pos);
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        for (int x = BOTTOM_SLAB_A.getX() - 3; x <= BOTTOM_SLAB_A.getX() + 4; x++) {
            for (int z = BOTTOM_SLAB_A.getZ() - 3; z <= BOTTOM_SLAB_A.getZ() + 3; z++) {
                world.setBlockState(new BlockPos(x, BOTTOM_SLAB_A.getY() - 2, z),
                        Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
    }

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static void waitForClient(ClientGameTestContext ctx, TestSingleplayerContext singleplayer, int ticks) {
        for (int i = 0; i < ticks; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();
    }

    private static void syncHeldMainHand(ClientGameTestContext ctx, TestSingleplayerContext singleplayer, ItemStack stack) {
        ItemStack held = stack == null ? ItemStack.EMPTY : stack.copy();
        singleplayer.getServer().runOnServer(server -> {
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0).setStackInHand(Hand.MAIN_HAND, held.copy());
            }
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.setStackInHand(Hand.MAIN_HAND, held.copy());
            }
        });
        ctx.waitTick();
    }

    private static void syncAim(ClientGameTestContext ctx, TestSingleplayerContext singleplayer, Vec3d eye, Vec3d target) {
        Vec3d delta = target.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        singleplayer.getServer().runOnServer(server -> {
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                var player = server.getPlayerManager().getPlayerList().get(0);
                player.setPosition(eye.x, feetY, eye.z);
                player.setYaw(yaw);
                player.setPitch(pitch);
            }
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
                mc.player.setYaw(yaw);
                mc.player.setPitch(pitch);
            }
        });
        ctx.waitTick();
    }

    private static Vec3d eyeFor(Direction face) {
        double x = UPPER_FULL.getX() + 0.5d - face.getOffsetX() * 2.6d;
        double y = UPPER_FULL.getY() + 1.2d;
        double z = UPPER_FULL.getZ() + 0.5d - face.getOffsetZ() * 2.6d;
        return new Vec3d(x, y, z);
    }

    private static BlockHitResult faceHit(BlockPos targetPos, Direction face, double yOffset) {
        return switch (face) {
            case NORTH -> new BlockHitResult(new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + yOffset,
                    targetPos.getZ()), Direction.NORTH, targetPos, false, false);
            case SOUTH -> new BlockHitResult(new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + yOffset,
                    targetPos.getZ() + 1.0d), Direction.SOUTH, targetPos, false, false);
            case EAST -> new BlockHitResult(new Vec3d(targetPos.getX() + 1.0d, targetPos.getY() + yOffset,
                    targetPos.getZ() + 0.5d), Direction.EAST, targetPos, false, false);
            case WEST -> new BlockHitResult(new Vec3d(targetPos.getX(), targetPos.getY() + yOffset,
                    targetPos.getZ() + 0.5d), Direction.WEST, targetPos, false, false);
            default -> throw new IllegalArgumentException("unsupported side face " + face);
        };
    }

    private static BlockHitResult upHit(BlockPos targetPos) {
        return new BlockHitResult(new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + 1.0d,
                targetPos.getZ() + 0.5d), Direction.UP, targetPos, false, false);
    }

    private static String hitBand(BlockHitResult hit) {
        if (hit.getSide() == Direction.UP) {
            return "top_face";
        }
        if (hit.getPos().y < UPPER_FULL.getY() - 0.5d) {
            return "lower_compound_visible_half";
        }
        if (hit.getPos().y < UPPER_FULL.getY()) {
            return "upper_compound_visible_half";
        }
        return hit.getPos().y < UPPER_FULL.getY() + 0.5d ? "lower_source_half" : "upper_source_half";
    }

    private static BlockPos owner(HitResult hit) {
        return hit instanceof BlockHitResult blockHit ? blockHit.getBlockPos() : null;
    }

    private static String describeHit(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return "MISS";
        }
        return "pos=" + blockHit.getBlockPos().toShortString()
                + " face=" + blockHit.getSide().asString()
                + " hit=" + blockHit.getPos();
    }

    private static String describeBlock(net.minecraft.world.BlockView world, BlockPos pos) {
        return describeBlock(world, pos, world.getBlockState(pos));
    }

    private static String describeBlock(net.minecraft.world.BlockView world, BlockPos pos, BlockState state) {
        return "pos=" + pos.toShortString()
                + " state=" + state
                + " dy=" + dy(world, pos, state)
                + " slabType=" + (state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE).asString() : "none")
                + " anchored=" + SlabAnchorAttachment.isAnchored(world, pos)
                + " compoundFullBlockAnchor=" + SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)
                + " persistentLoweredSlabCarrier=" + SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state);
    }

    private static double dy(net.minecraft.world.BlockView world, BlockPos pos, BlockState state) {
        return SlabSupport.getYOffset(world, pos, state);
    }

    private static boolean isBottomSlab(net.minecraft.world.BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isOf(Blocks.STONE_SLAB)
                && state.contains(SlabBlock.TYPE)
                && state.get(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    private static boolean isStoneDy(net.minecraft.world.BlockView world, BlockPos pos, double expectedDy) {
        BlockState state = world.getBlockState(pos);
        return state.isOf(Blocks.STONE) && isDy(world, pos, expectedDy);
    }

    private static boolean isDy(net.minecraft.world.BlockView world, BlockPos pos, double expectedDy) {
        return Math.abs(SlabSupport.getYOffset(world, pos, world.getBlockState(pos)) - expectedDy) <= EPSILON;
    }

    private static String releaseBlockers(Verdicts verdicts) {
        String blockers = "";
        if (!"GREEN".equals(verdicts.lowerA) || !"GREEN".equals(verdicts.lowerB)) {
            blockers = "lowerSide";
        }
        if (!"GREEN".equals(verdicts.topFace)) {
            blockers = blockers.isEmpty() ? "topFace" : blockers + ",topFace";
        }
        return blockers.isEmpty() ? "none" : blockers;
    }

    private static final class Verdicts {
        String lowerA = "PENDING";
        String lowerB = "PENDING";
        String upperA = "PENDING";
        String upperB = "PENDING";
        String topFace = "PENDING";
        String supportBreak = "PENDING";
        String hitbox = "PENDING";
        boolean ghost;
        boolean jump;
        boolean wrongOwner;
        boolean miss;
        int observedCases;
    }
}
