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
import net.minecraft.registry.Registries;
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
    private static final SlabType CANONICAL_TOP_SLAB_TYPE = SlabType.BOTTOM;

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
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_DONE] status=STRUCTURE_INVALID");
                return;
            }
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_BASELINE]"
                    + " supportPresent.upperSide=PENDING supportPresent.lowerSide=PENDING"
                    + " supportPresent.topFace=PENDING supportMissing.side=PENDING"
                    + " supportMissing.topFace=PENDING hitbox=PENDING");

            verdicts.supportPresentLowerSide = runSideCase(
                    ctx, singleplayer, verdicts, "SUPPORT_PRESENT_SIDE_LOWER", ANGLE_A_FACE, -0.75d, SlabType.BOTTOM);
            verdicts.supportPresentUpperSide = runSideCase(
                    ctx, singleplayer, verdicts, "SUPPORT_PRESENT_SIDE_UPPER", ANGLE_A_FACE, 0.25d, SlabType.TOP);
            verdicts.supportPresentTopFace = runTopFaceCase(ctx, singleplayer, verdicts, "SUPPORT_PRESENT_TOP_FACE", false);
            verdicts.supportMissingSide = runSupportMissingSideCase(ctx, singleplayer, verdicts);
            verdicts.supportMissingTopFace = runTopFaceCase(ctx, singleplayer, verdicts, "SUPPORT_MISSING_TOP_FACE", true);

            verdicts.hitbox = verdicts.wrongOwner || verdicts.miss ? "RED" : "GREEN";
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_HITBOX_" + verdicts.hitbox + "]"
                    + " wrongOwner=" + verdicts.wrongOwner
                    + " miss=" + verdicts.miss
                    + " observedCases=" + verdicts.observedCases);

            String releaseBlockers = releaseBlockers(verdicts);
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_SUMMARY]"
                    + " structure=GREEN"
                    + " fixtureTruth=GREEN"
                    + " supportPresent.upperSide=" + verdicts.supportPresentUpperSide
                    + " supportPresent.lowerSide=" + verdicts.supportPresentLowerSide
                    + " supportPresent.topFace=" + verdicts.supportPresentTopFace
                    + " supportMissing.side=" + verdicts.supportMissingSide
                    + " supportMissing.topFace=" + verdicts.supportMissingTopFace
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
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_INVALID] reason=client_world_missing");
                return;
            }
            green[0] = emitStructureProof(mc.world);
        });
        if (!green[0]) {
            throw new RuntimeException("[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_INVALID] reason=fixture_truth_not_proven");
        }
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
                throw new RuntimeException("[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_INVALID] case="
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
                throw new RuntimeException("[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_INVALID] case="
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

    private static String runTopFaceCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Verdicts verdicts,
            String caseName,
            boolean removeSupport
    ) {
        seedCanonicalStructure(singleplayer);
        if (removeSupport) {
            removeSupportUnderUpperFullBlock(singleplayer);
        }
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
                throw new RuntimeException("[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_INVALID] case="
                        + caseName + " reason=client_not_ready");
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult beforeTarget = mc.crosshairTarget;
            ActionResult action = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_TRACE]"
                    + " case=" + caseName
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
                throw new RuntimeException("[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_INVALID] case="
                        + caseName + " reason=client_world_missing_after_click");
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
            boolean upperFullStillPresent = source.isOf(Blocks.STONE);
            boolean cleanRejectPreserve = !ghostOrSkip
                    && source.isOf(Blocks.STONE)
                    && Math.abs(sourceDy + 1.0d) <= EPSILON;
            verdict[0] = legalTop ? "GREEN" : "RED";
            verdicts.ghost |= !legalTop && ghostOrSkip;
            verdicts.observedCases++;
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_" + caseName + "_" + verdict[0] + "]"
                    + " expected=stone_slab[type=bottom] dy=0.0"
                    + " supportRemoved=" + removeSupport
                    + " missingUnderSlabVariantTested=" + removeSupport
                    + " topCandidate=" + describeBlock(mc.world, expectedTop)
                    + " skippedCandidate=" + describeBlock(mc.world, skippedTop)
                    + " ghostOrSkipSlabAppeared=" + ghostOrSkip
                    + " cleanRejectPreserve=" + cleanRejectPreserve
                    + " upperFullStillPresent=" + upperFullStillPresent
                    + " missingUnderSlabVariantSupportPos=" + TOP_SLAB_A.toShortString()
                    + " source=" + describeBlock(mc.world, UPPER_FULL));
        });
        return verdict[0];
    }

    private static String runSupportMissingSideCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Verdicts verdicts
    ) {
        seedCanonicalStructure(singleplayer);
        removeSupportUnderUpperFullBlock(singleplayer);
        waitForClient(ctx, singleplayer, 5);
        final BlockPos sideCandidate = UPPER_FULL.offset(ANGLE_A_FACE);
        final BlockHitResult upperHit = faceHit(UPPER_FULL, ANGLE_A_FACE, 0.25d);
        final String caseName = "SUPPORT_MISSING_SIDE";
        final String[] verdict = {"RED"};
        final boolean[] miss = {false};
        final boolean[] wrongOwner = {false};

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncAim(ctx, singleplayer, eyeFor(ANGLE_A_FACE), upperHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("[JULIA_BETA4_LIVE_GOBLIN_" + caseName
                        + "_RED] reason=client_not_ready_before_side_place");
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult beforeTarget = mc.crosshairTarget;
            miss[0] = beforeTarget == null || beforeTarget.getType() == HitResult.Type.MISS;
            wrongOwner[0] = owner(beforeTarget) != null && !UPPER_FULL.equals(owner(beforeTarget));
            BlockState supportBefore = mc.world.getBlockState(TOP_SLAB_A);
            BlockState upperBefore = mc.world.getBlockState(UPPER_FULL);
            BlockState candidateBefore = mc.world.getBlockState(sideCandidate);
            ActionResult action = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, upperHit);
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_TRACE]"
                    + " case=" + caseName
                    + " phase=click"
                    + " expectedOwner=" + UPPER_FULL.toShortString()
                    + " actualTarget=" + describeHit(beforeTarget)
                    + " face=" + upperHit.getSide().asString()
                    + " supportRemoved=true"
                    + " missingUnderSlabVariantTested=true"
                    + " missingUnderSlabVariantSupportPos=" + TOP_SLAB_A.toShortString()
                    + " sourceDy=" + dy(mc.world, UPPER_FULL, upperBefore)
                    + " candidateDy=" + dy(mc.world, sideCandidate, candidateBefore)
                    + " targetMiss=" + miss[0]
                    + " targetJumped=" + wrongOwner[0]
                    + " hitY=" + upperHit.getPos().y
                    + " hitBand=" + hitBand(upperHit)
                    + " result=" + action
                    + " removedSupport=" + describeBlock(mc.world, TOP_SLAB_A, supportBefore)
                    + " source=" + describeBlock(mc.world, UPPER_FULL, upperBefore)
                    + " candidateBefore=" + describeBlock(mc.world, sideCandidate, candidateBefore));
        });
        waitForClient(ctx, singleplayer, 2);
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                verdict[0] = "RED";
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_" + caseName
                        + "_RED] reason=client_world_missing_after_click");
                return;
            }
            BlockState upperAfter = mc.world.getBlockState(UPPER_FULL);
            BlockState supportAfter = mc.world.getBlockState(TOP_SLAB_A);
            BlockState sideAfter = mc.world.getBlockState(sideCandidate);
            double sideDy = dy(mc.world, sideCandidate, sideAfter);
            boolean legal = sideAfter.isOf(Blocks.STONE_SLAB)
                    && sideAfter.contains(SlabBlock.TYPE)
                    && sideAfter.get(SlabBlock.TYPE) == SlabType.TOP
                    && Math.abs(sideDy + 0.5d) <= EPSILON;
            boolean upperFullStillPresent = upperAfter.isOf(Blocks.STONE);
            boolean jump = upperFullStillPresent && Math.abs(dy(mc.world, UPPER_FULL, upperAfter) + 1.0d) > EPSILON;
            boolean ghost = !legal && sideAfter.isOf(Blocks.STONE_SLAB);
            verdict[0] = legal ? "GREEN" : "RED";
            verdicts.jump |= jump;
            verdicts.ghost |= ghost;
            verdicts.miss |= miss[0];
            verdicts.wrongOwner |= wrongOwner[0];
            verdicts.observedCases++;
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_" + caseName + "_" + verdict[0] + "]"
                    + " expected=stone_slab[type=top] dy=-0.5"
                    + " supportRemoved=true"
                    + " missingUnderSlabVariantTested=true"
                    + " missingUnderSlabVariantSupportPos=" + TOP_SLAB_A.toShortString()
                    + " jump=" + jump
                    + " upperFullStillPresent=" + upperFullStillPresent
                    + " targetMiss=" + miss[0]
                    + " wrongOwner=" + wrongOwner[0]
                    + " ghost=" + ghost
                    + " upperFullBlock=" + describeBlock(mc.world, UPPER_FULL, upperAfter)
                    + " removedSupport=" + describeBlock(mc.world, TOP_SLAB_A, supportAfter)
                    + " sideCandidate=" + describeBlock(mc.world, sideCandidate, sideAfter));
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

            world.setBlockState(TOP_SLAB_A, topSlab(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, TOP_SLAB_A, world.getBlockState(TOP_SLAB_A));
            world.setBlockState(TOP_SLAB_B, topSlab(), Block.NOTIFY_LISTENERS);
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

    private static void removeSupportUnderUpperFullBlock(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, TOP_SLAB_A);
            world.breakBlock(TOP_SLAB_A, false);
            world.updateNeighborsAlways(TOP_SLAB_A, Blocks.AIR, null);
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_SUPPORT_MISSING_SETUP]"
                    + " missingUnderSlabVariantTested=true"
                    + " missingUnderSlabVariantSupportPos=" + TOP_SLAB_A.toShortString()
                    + " removedSupport=" + describeBlock(world, TOP_SLAB_A)
                    + " upperFullBlock=" + describeBlock(world, UPPER_FULL)
                    + " bridgeFullBlockA=" + describeBlock(world, BRIDGE_FULL_A)
                    + " bridgeFullBlockB=" + describeBlock(world, BRIDGE_FULL_B));
        });
    }

    private static boolean emitStructureProof(net.minecraft.world.BlockView world) {
        boolean bottomA = isBottomSlab(world, BOTTOM_SLAB_A);
        boolean bottomB = isBottomSlab(world, BOTTOM_SLAB_B);
        boolean bridgeA = isStoneDy(world, BRIDGE_FULL_A, -0.5d);
        boolean bridgeB = isStoneDy(world, BRIDGE_FULL_B, -0.5d);
        boolean topA = isExpectedSlab(world, TOP_SLAB_A, CANONICAL_TOP_SLAB_TYPE) && isDy(world, TOP_SLAB_A, -0.5d);
        boolean topB = isExpectedSlab(world, TOP_SLAB_B, CANONICAL_TOP_SLAB_TYPE) && isDy(world, TOP_SLAB_B, -0.5d);
        boolean upper = isStoneDy(world, UPPER_FULL, -1.0d)
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(world, UPPER_FULL);
        boolean missingVariantNamed = TOP_SLAB_A.equals(UPPER_FULL.down());
        String marker = bottomA && bottomB && bridgeA && bridgeB && topA && topB && upper
                && missingVariantNamed
                ? "[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_GREEN]"
                : "[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_INVALID]";
        System.out.println(marker
                + " fixtureTruth=" + (marker.endsWith("GREEN]") ? "GREEN" : "RED")
                + " bottomSlabA=" + describeBlock(world, BOTTOM_SLAB_A)
                + " bottomSlabB=" + describeBlock(world, BOTTOM_SLAB_B)
                + " bridgeFullBlockA=" + describeBlock(world, BRIDGE_FULL_A)
                + " bridgeFullBlockB=" + describeBlock(world, BRIDGE_FULL_B)
                + " topSlabA=" + describeBlock(world, TOP_SLAB_A)
                + " topSlabB=" + describeBlock(world, TOP_SLAB_B)
                + " upperFullBlock=" + describeBlock(world, UPPER_FULL)
                + " upperFullBlockCompound=" + SlabAnchorAttachment.isCompoundFullBlockAnchor(world, UPPER_FULL)
                + " upperFullBlockAnchored=" + SlabAnchorAttachment.isAnchored(world, UPPER_FULL)
                + " upperFullBlockLowered=" + isDy(world, UPPER_FULL, -1.0d)
                + " lowerHalfSideCandidate=" + UPPER_FULL.offset(ANGLE_A_FACE).toShortString()
                + " upperHalfSideCandidate=" + UPPER_FULL.offset(ANGLE_A_FACE).toShortString()
                + " topFaceCandidate=" + UPPER_FULL.up().toShortString()
                + " missingUnderSlabVariantSupportPos=" + TOP_SLAB_A.toShortString()
                + " missingUnderSlabVariantTested=true"
                + " invalidReasons=" + invalidReasons(bottomA, bottomB, bridgeA, bridgeB, topA, topB, upper, missingVariantNamed));
        return bottomA && bottomB && bridgeA && bridgeB && topA && topB && upper && missingVariantNamed;
    }

    private static void clearArea(World world) {
        for (int x = BOTTOM_SLAB_A.getX() - 3; x <= BOTTOM_SLAB_A.getX() + 4; x++) {
            for (int y = BOTTOM_SLAB_A.getY() - 2; y <= BOTTOM_SLAB_A.getY() + 6; y++) {
                for (int z = BOTTOM_SLAB_A.getZ() - 3; z <= BOTTOM_SLAB_A.getZ() + 3; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    SlabAnchorAttachment.removeAnchor(world, pos);
                    SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, pos);
                    SlabAnchorAttachment.removeCompoundFullBlockAnchor(world, pos);
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

    private static BlockState topSlab() {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, CANONICAL_TOP_SLAB_TYPE);
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
                + " blockId=" + Registries.BLOCK.getId(state.getBlock())
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
        return isExpectedSlab(world, pos, SlabType.BOTTOM);
    }

    private static boolean isExpectedSlab(net.minecraft.world.BlockView world, BlockPos pos, SlabType expectedType) {
        BlockState state = world.getBlockState(pos);
        return state.isOf(Blocks.STONE_SLAB)
                && state.contains(SlabBlock.TYPE)
                && state.get(SlabBlock.TYPE) == expectedType;
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
        if (!"GREEN".equals(verdicts.supportPresentLowerSide)) {
            blockers = "lowerSide";
        }
        if (!"GREEN".equals(verdicts.supportPresentTopFace)) {
            blockers = blockers.isEmpty() ? "topFace" : blockers + ",topFace";
        }
        if (!"GREEN".equals(verdicts.supportMissingSide)) {
            blockers = blockers.isEmpty() ? "supportMissingSide" : blockers + ",supportMissingSide";
        }
        if (!"GREEN".equals(verdicts.supportMissingTopFace)) {
            blockers = blockers.isEmpty() ? "supportMissingTopFace" : blockers + ",supportMissingTopFace";
        }
        return blockers.isEmpty() ? "none" : blockers;
    }

    private static String invalidReasons(
            boolean bottomA,
            boolean bottomB,
            boolean bridgeA,
            boolean bridgeB,
            boolean topA,
            boolean topB,
            boolean upper,
            boolean missingVariantNamed
    ) {
        String reasons = "";
        reasons = appendReason(reasons, bottomA, "bottomSlabA");
        reasons = appendReason(reasons, bottomB, "bottomSlabB");
        reasons = appendReason(reasons, bridgeA, "bridgeFullBlockA");
        reasons = appendReason(reasons, bridgeB, "bridgeFullBlockB");
        reasons = appendReason(reasons, topA, "topSlabA");
        reasons = appendReason(reasons, topB, "topSlabB");
        reasons = appendReason(reasons, upper, "upperFullBlock");
        reasons = appendReason(reasons, missingVariantNamed, "missingUnderSlabVariant");
        return reasons.isEmpty() ? "none" : reasons;
    }

    private static String appendReason(String reasons, boolean ok, String reason) {
        if (ok) {
            return reasons;
        }
        return reasons.isEmpty() ? reason : reasons + "," + reason;
    }

    private static final class Verdicts {
        String supportPresentLowerSide = "PENDING";
        String supportPresentUpperSide = "PENDING";
        String supportPresentTopFace = "PENDING";
        String supportMissingSide = "PENDING";
        String supportMissingTopFace = "PENDING";
        String hitbox = "PENDING";
        boolean ghost;
        boolean jump;
        boolean wrongOwner;
        boolean miss;
        int observedCases;
    }
}
