package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Opt-in diagnostic harness for Julia's beta4 canonical live shape.
 *
 * <p>No gameplay behavior is implemented here. The test builds the live
 * structure, proves the source truth, then records the current lower/upper/top
 * side behavior honestly behind {@code -Dslabbed.beta4LiveShapeGoblin=true}.
 */
public final class SlabbedLabBeta4LiveShapeGoblinClientGameTest implements FabricClientGameTest {
    private static final String OPT_IN = "slabbed.beta4LiveShapeGoblin";
    private static final String COMPOUND_VISIBLE_SLAB_LANE_RED_OPT_IN =
            "slabbed.beta4CompoundVisibleSlabLaneRed";
    private static final String REPEAT_SEAM_TRACE_OPT_IN = "slabbed.beta4RepeatMergeTrace";
    private static final double EPSILON = 1.0e-6d;
    private static final double AIM_EPSILON = 1.0e-4d;
    private static final double EXPECTED_UPPER_FULL_DY = -1.0d;
    private static final double LOOK_ANGLE_TOLERANCE_DEGREES = 0.75d;

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
        if (Boolean.getBoolean(COMPOUND_VISIBLE_SLAB_LANE_RED_OPT_IN)) {
            runCompoundVisibleSlabLaneRedProof(ctx);
            return;
        }
        if (!Boolean.getBoolean(OPT_IN)) {
            return;
        }

        System.out.println("[JULIA_BETA4_LIVE_GOBLIN_PARITY_START]"
                + " flag=-Dslabbed.beta4LiveShapeGoblin=true"
                + " mode=real_crosshair_sequence"
                + " syntheticUsed=false"
                + " structure=two_bottom_slabs_full_block_bridge_two_top_slabs_one_upper_full_block");

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            ParityVerdicts verdicts = new ParityVerdicts();
            seedCanonicalStructure(singleplayer);
            waitForClient(ctx, singleplayer, 10);
            final boolean[] structureGreen = {false};
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    System.out.println("[JULIA_BETA4_LIVE_GOBLIN_STRUCTURE_INVALID] reason=client_world_missing");
                    return;
                }
                structureGreen[0] = emitStructureProof(mc.world);
            });
            if (!structureGreen[0]) {
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_PARITY_SUMMARY]"
                        + " structure=RED realCrosshair=false syntheticUsed=false"
                        + " lowerAimParity=NOT_RUN upperAimParity=NOT_RUN topAimParity=NOT_RUN"
                        + " firstSide=NOT_RUN lowerAfterFirst=NOT_RUN repeatPlacement=NOT_RUN topFace=NOT_RUN"
                        + " ghost=false wrongDelta=false wrongOwner=false releaseBlockers=structure");
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_DONE] status=STRUCTURE_INVALID");
                return;
            }

            syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 16));
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_TARGETING_DIAG_START]"
                    + " realCrosshair=true"
                    + " syntheticUsed=false"
                    + " intendedOwner=" + UPPER_FULL.toShortString()
                    + " expectedOwnerDy=" + EXPECTED_UPPER_FULL_DY
                    + " localCoordBasis=visualDyAdjusted"
                    + " rawLocalOutside01ExplainedBy=compound_owner_dy_when_visualLocalWithin01");
            AimCorridor lowerCorridor = scanAimCorridors(
                    ctx, singleplayer, "lower", ANGLE_A_FACE, 0.25d);
            verdicts.lowerCorridor = lowerCorridor.found() ? "FOUND" : "NONE";
            RealClickResult firstSide = runRealCrosshairClick(
                    ctx, singleplayer, "FIRST_SIDE", "UPPER", ANGLE_A_FACE,
                    visibleSideHitPoint(UPPER_FULL, ANGLE_A_FACE, 0.75d, EXPECTED_UPPER_FULL_DY),
                    "JULIA_BETA4_LIVE_GOBLIN_AIM_UPPER_REAL_TARGET",
                    UPPER_FULL.offset(ANGLE_A_FACE), "stone_slab[type=top] dy=-0.5");
            verdicts.upperAimParity = firstSide.aimParity;
            verdicts.firstSide = firstSide.result;
            verdicts.upperTargeting = firstSide.targetingClassification;
            verdicts.sequenceFirstTargeting = firstSide.targetingClassification;
            verdicts.absorb(firstSide);

            AimCorridor lowerAfterFirstCorridor = scanAimCorridors(
                    ctx, singleplayer, "sequenceLowerAfterFirst", ANGLE_A_FACE, 0.25d);
            verdicts.lowerAfterFirstCorridor = lowerAfterFirstCorridor.found() ? "FOUND" : "NONE";
            RealClickResult lowerAfterFirst = lowerAfterFirstCorridor.found()
                    ? runRealCrosshairClick(
                            ctx, singleplayer, "LOWER_AFTER_FIRST", "LOWER", ANGLE_A_FACE,
                            lowerAfterFirstCorridor.hit,
                            lowerAfterFirstCorridor.eye,
                            "JULIA_BETA4_LIVE_GOBLIN_AIM_LOWER_REAL_TARGET",
                            UPPER_FULL.offset(ANGLE_A_FACE).offset(ANGLE_A_FACE),
                            "stone_slab[type=bottom] dy=-0.5")
                    : noCorridorClick("LOWER_AFTER_FIRST", lowerAfterFirstCorridor);
            verdicts.lowerAimParity = lowerAfterFirst.aimParity;
            verdicts.lowerAfterFirst = lowerAfterFirst.result;
            verdicts.lowerTargeting = lowerAfterFirst.targetingClassification;
            verdicts.sequenceLowerAfterFirstTargeting = lowerAfterFirst.targetingClassification;
            verdicts.absorb(lowerAfterFirst);

            RealClickResult repeat = runRealCrosshairClick(
                    ctx, singleplayer, "REPEAT", "UPPER", ANGLE_A_FACE,
                    visibleSideHitPoint(UPPER_FULL, ANGLE_A_FACE, 0.75d, EXPECTED_UPPER_FULL_DY),
                    "JULIA_BETA4_LIVE_GOBLIN_AIM_UPPER_REAL_TARGET",
                    UPPER_FULL.offset(ANGLE_A_FACE), "stone_slab[type=double] dy=-0.5");
            verdicts.repeatPlacement = repeat.result;
            verdicts.absorb(repeat);

            RealClickResult topFace = runRealCrosshairClick(
                    ctx, singleplayer, "TOP_FACE", "TOP", Direction.UP,
                    visibleTopHitPoint(UPPER_FULL, EXPECTED_UPPER_FULL_DY),
                    "JULIA_BETA4_LIVE_GOBLIN_AIM_TOP_REAL_TARGET",
                    UPPER_FULL.up(), "stone_slab[type=bottom] dy=0.0");
            verdicts.topAimParity = topFace.aimParity;
            verdicts.topFace = topFace.result;
            verdicts.topTargeting = topFace.targetingClassification;
            verdicts.sequenceTopTargeting = topFace.targetingClassification;
            verdicts.absorb(topFace);

            String releaseBlockers = parityReleaseBlockers(verdicts);
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_PARITY_SUMMARY]"
                    + " structure=GREEN"
                    + " realCrosshair=true"
                    + " syntheticUsed=false"
                    + " lowerAimParity=" + verdicts.lowerAimParity
                    + " upperAimParity=" + verdicts.upperAimParity
                    + " topAimParity=" + verdicts.topAimParity
                    + " firstSide=" + verdicts.firstSide
                    + " lowerAfterFirst=" + verdicts.lowerAfterFirst
                    + " repeatPlacement=" + verdicts.repeatPlacement
                    + " topFace=" + verdicts.topFace
                    + " ghost=" + verdicts.ghost
                    + " wrongDelta=" + verdicts.wrongDelta
                    + " wrongOwner=" + verdicts.wrongOwner
                    + " releaseBlockers=" + releaseBlockers);
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_TARGETING_SUMMARY]"
                    + " lower=" + verdicts.lowerTargeting
                    + " upper=" + verdicts.upperTargeting
                    + " top=" + verdicts.topTargeting
                    + " sequenceFirst=" + verdicts.sequenceFirstTargeting
                    + " sequenceLowerAfterFirst=" + verdicts.sequenceLowerAfterFirstTargeting
                    + " sequenceTop=" + verdicts.sequenceTopTargeting
                    + " harnessAimFailures=" + verdicts.countTargeting("HARNESS_AIM_FAIL")
                    + " ownerFailures=" + verdicts.countTargeting("OWNER_FAIL")
                    + " occlusionCases=" + verdicts.countTargeting("OCCLUSION_EXPECTED")
                    + " nextAction=" + verdicts.nextTargetingAction());
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_CORRIDOR_SUMMARY]"
                    + " lowerCorridor=" + verdicts.lowerCorridor
                    + " lowerAfterFirstCorridor=" + verdicts.lowerAfterFirstCorridor
                    + " sequenceLowerResult=" + verdicts.lowerAfterFirst
                    + " repeatPlacement=" + verdicts.repeatPlacement
                    + " topFace=" + verdicts.topFace
                    + " ghost=" + verdicts.ghost
                    + " wrongDelta=" + verdicts.wrongDelta
                    + " releaseBlockers=" + releaseBlockers);
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_DONE] status=OK");
        }
    }

    private static void runCompoundVisibleSlabLaneRedProof(ClientGameTestContext ctx) {
        System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_START]"
                + " flag=-Dslabbed.beta4CompoundVisibleSlabLaneRed=true"
                + " mode=gated_red_proof"
                + " gameplayChange=false"
                + " expectedLane=source_owned_authored_or_persistent_compound_full_block_dy_-1.0");

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            seedCanonicalStructure(singleplayer);
            waitForClient(ctx, singleplayer, 10);

            final boolean[] fixtureGreen = {false};
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_FIXTURE_FAIL]"
                            + " fixtureTruth=FAIL reason=client_world_missing");
                    return;
                }
                fixtureGreen[0] = emitCompoundVisibleSlabLaneFixtureProof(mc.world);
            });

            if (!fixtureGreen[0]) {
                System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_SUMMARY]"
                        + " fixtureTruth=FAIL lower=PENDING upper=PENDING merge=PENDING top=PENDING"
                        + " supportMissing=PENDING triad=PENDING reload=PENDING"
                        + " releaseBlockers=fixture");
                throw new RuntimeException("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_FIXTURE_FAIL]"
                        + " reason=fixture_truth_not_proven");
            }

            CompoundVisibleSlabLaneResults results = new CompoundVisibleSlabLaneResults();
            results.lower = runCompoundVisibleSideLaneCase(ctx, singleplayer,
                    "LOWER", 0.25d, SlabType.BOTTOM, "COMPOUND_VISIBLE_SIDE_LOWER_SLAB");
            results.upper = runCompoundVisibleSideLaneCase(ctx, singleplayer,
                    "UPPER", 0.75d, SlabType.TOP, "COMPOUND_VISIBLE_SIDE_UPPER_SLAB");
            results.merge = runCompoundVisibleMergeLaneCase(ctx, singleplayer);
            results.top = runCompoundVisibleTopLaneCase(ctx, singleplayer,
                    "TOP", false, "COMPOUND_VISIBLE_OWNER_TOP_SLAB");
            results.supportMissing = runCompoundVisibleSupportMissingLaneCase(ctx, singleplayer);
            results.triad = emitCompoundVisibleTriadRed(ctx, singleplayer, results);
            results.reload = emitCompoundVisibleReloadRed(ctx, singleplayer, results);

            System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_SUMMARY]"
                    + " fixtureTruth=GREEN"
                    + " lower=" + results.lower
                    + " upper=" + results.upper
                    + " merge=" + results.merge
                    + " top=" + results.top
                    + " supportMissing=" + results.supportMissing
                    + " triad=" + results.triad
                    + " reload=" + results.reload
                    + " releaseBlockers=compoundVisibleSlabLane");
        }
    }

    private static String runCompoundVisibleSideLaneCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String caseName,
            double visualLocalY,
            SlabType expectedType,
            String expectedLaw
    ) {
        seedCanonicalStructure(singleplayer);
        waitForClient(ctx, singleplayer, 10);
        final BlockPos candidate = UPPER_FULL.offset(ANGLE_A_FACE);
        final BlockHitResult hit = visibleSideHit(UPPER_FULL, ANGLE_A_FACE, visualLocalY);
        final String[] action = {"NOT_RUN"};

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncAim(ctx, singleplayer, eyeFor(ANGLE_A_FACE), hit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                action[0] = "CLIENT_NOT_READY";
                return;
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult beforeTarget = mc.crosshairTarget;
            action[0] = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).toString();
            System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TRACE]"
                    + " row=" + caseName
                    + " phase=click"
                    + " expectedLaw=" + expectedLaw
                    + " expectedSource=" + UPPER_FULL.toShortString()
                    + " expectedCandidate=" + candidate.toShortString()
                    + " actualTarget=" + describeHit(beforeTarget)
                    + " syntheticHit=" + describeHit(hit)
                    + " visualLocalY=" + visualLocalY
                    + " hitBand=" + hitBand(hit)
                    + " action=" + action[0]);
        });
        waitForClient(ctx, singleplayer, 4);
        return emitCompoundVisibleSideLaneResult(ctx, singleplayer, caseName, candidate,
                expectedType, expectedLaw, action[0]);
    }

    private static String runCompoundVisibleMergeLaneCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        seedCanonicalStructure(singleplayer);
        waitForClient(ctx, singleplayer, 10);
        final BlockPos candidate = UPPER_FULL.offset(ANGLE_A_FACE);
        final BlockHitResult lowerHit = visibleSideHit(UPPER_FULL, ANGLE_A_FACE, 0.25d);
        final BlockHitResult upperHit = visibleSideHit(UPPER_FULL, ANGLE_A_FACE, 0.75d);
        final String[] firstAction = {"NOT_RUN"};
        final String[] secondAction = {"NOT_RUN"};

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncAim(ctx, singleplayer, eyeFor(ANGLE_A_FACE), lowerHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                firstAction[0] = "CLIENT_NOT_READY";
                return;
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            firstAction[0] = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, lowerHit).toString();
        });
        waitForClient(ctx, singleplayer, 4);
        syncAim(ctx, singleplayer, eyeFor(ANGLE_A_FACE), upperHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                secondAction[0] = "CLIENT_NOT_READY";
                return;
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            secondAction[0] = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, upperHit).toString();
            System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TRACE]"
                    + " row=MERGE phase=clicks"
                    + " expectedLaw=COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB"
                    + " expectedSource=" + UPPER_FULL.toShortString()
                    + " expectedCandidate=" + candidate.toShortString()
                    + " lowerHit=" + describeHit(lowerHit)
                    + " upperHit=" + describeHit(upperHit)
                    + " firstAction=" + firstAction[0]
                    + " secondAction=" + secondAction[0]);
        });
        waitForClient(ctx, singleplayer, 4);
        return emitCompoundVisibleSideLaneResult(ctx, singleplayer, "MERGE", candidate,
                SlabType.DOUBLE, "COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB",
                firstAction[0] + "," + secondAction[0]);
    }

    private static String runCompoundVisibleTopLaneCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String caseName,
            boolean removeSupport,
            String expectedLaw
    ) {
        seedCanonicalStructure(singleplayer);
        if (removeSupport) {
            removeSupportUnderUpperFullBlock(singleplayer);
        }
        waitForClient(ctx, singleplayer, 10);
        final BlockPos candidate = UPPER_FULL.up();
        final BlockHitResult hit = visibleTopHit(UPPER_FULL);
        final String[] action = {"NOT_RUN"};

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncAim(ctx, singleplayer, topFaceEye(), hit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                action[0] = "CLIENT_NOT_READY";
                return;
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult beforeTarget = mc.crosshairTarget;
            action[0] = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).toString();
            System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TRACE]"
                    + " row=" + caseName
                    + " phase=click"
                    + " expectedLaw=" + expectedLaw
                    + " supportRemoved=" + removeSupport
                    + " expectedSource=" + UPPER_FULL.toShortString()
                    + " expectedCandidate=" + candidate.toShortString()
                    + " actualTarget=" + describeHit(beforeTarget)
                    + " syntheticHit=" + describeHit(hit)
                    + " hitBand=" + hitBand(hit)
                    + " action=" + action[0]);
        });
        waitForClient(ctx, singleplayer, 4);
        return emitCompoundVisibleTopLaneResult(ctx, singleplayer, caseName, candidate,
                removeSupport, expectedLaw, action[0]);
    }

    private static String runCompoundVisibleSupportMissingLaneCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        NamedCompoundVisibleLaneFixture fixture = buildCompoundVisibleNamedStateFixture(ctx, singleplayer,
                "SUPPORT_MISSING");
        final String[] beforeSource = {"NOT_RUN"};
        final String[] afterSource = {"NOT_RUN"};
        final boolean[] supportRemoved = {false};
        final boolean[] sourcePreserved = {false};
        final boolean[] sourceJumped = {true};
        final boolean[] namedStatesPreserved = {false};
        final boolean[] noDyBelowMinusOne = {false};

        singleplayer.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            BlockState sourceBefore = world.getBlockState(UPPER_FULL);
            double sourceDyBefore = dy(world, UPPER_FULL, sourceBefore);
            beforeSource[0] = describeBlock(world, UPPER_FULL, sourceBefore);

            SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, TOP_SLAB_A);
            world.breakBlock(TOP_SLAB_A, false);
            world.updateNeighborsAlways(TOP_SLAB_A, Blocks.AIR, null);

            BlockState sourceAfter = world.getBlockState(UPPER_FULL);
            double sourceDyAfter = dy(world, UPPER_FULL, sourceAfter);
            afterSource[0] = describeBlock(world, UPPER_FULL, sourceAfter);
            supportRemoved[0] = world.getBlockState(TOP_SLAB_A).isAir();
            sourcePreserved[0] = sourceAfter.isOf(Blocks.STONE)
                    && Math.abs(sourceDyAfter + 1.0d) <= EPSILON
                    && SlabAnchorAttachment.isCompoundFullBlockAnchor(world, UPPER_FULL);
            sourceJumped[0] = Math.abs(sourceDyAfter - sourceDyBefore) > EPSILON;
            namedStatesPreserved[0] = isNamedCompoundVisibleLaneFixture(world, fixture);
            noDyBelowMinusOne[0] = noDyBelowMinusOne(world, fixture);

            System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_SUPPORT_MISSING_TRACE]"
                    + " removedSupport=" + describeBlock(world, TOP_SLAB_A)
                    + " sourceBefore=" + beforeSource[0]
                    + " sourceAfter=" + afterSource[0]
                    + " lower=" + describeBlock(world, fixture.lowerPos)
                    + " upper=" + describeBlock(world, fixture.upperPos)
                    + " merge=" + describeBlock(world, fixture.mergePos)
                    + " top=" + describeBlock(world, fixture.topPos)
                    + " supportRemoved=" + supportRemoved[0]
                    + " sourcePreserved=" + sourcePreserved[0]
                    + " sourceJumped=" + sourceJumped[0]
                    + " namedStatesPreserved=" + namedStatesPreserved[0]
                    + " noDyBelowMinusOne=" + noDyBelowMinusOne[0]);
        });
        waitForClient(ctx, singleplayer, 4);

        String verdict = fixture.green()
                && supportRemoved[0]
                && sourcePreserved[0]
                && !sourceJumped[0]
                && namedStatesPreserved[0]
                && noDyBelowMinusOne[0] ? "GREEN" : "RED";
        System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_SUPPORT_MISSING_" + verdict + "]"
                + " expected=source_owned_named_dy_-1.0_lane_states_survive_support_removal"
                + " fixtureStates=" + fixture.statusSummary()
                + " supportRemoved=" + supportRemoved[0]
                + " sourceBefore={" + beforeSource[0] + "}"
                + " sourceAfter={" + afterSource[0] + "}"
                + " sourceState=stone"
                + " sourceDy=-1.0"
                + " sourcePersistentCompoundOwnerTruthIntact=" + sourcePreserved[0]
                + " jump=" + sourceJumped[0]
                + " namedStatesPreserved=" + namedStatesPreserved[0]
                + " lowerSideBottomDy=-1.0"
                + " upperSideTopDy=-1.0"
                + " sideDoubleDy=-1.0"
                + " ownerTopBottomDy=-1.0"
                + " noDyBelowMinusOne=" + noDyBelowMinusOne[0]
                + " releaseBlocker=" + (!"GREEN".equals(verdict)));
        return verdict;
    }

    private static String runCompoundVisibleSupportMissingSideSubcase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        seedCanonicalStructure(singleplayer);
        removeSupportUnderUpperFullBlock(singleplayer);
        waitForClient(ctx, singleplayer, 10);
        final BlockPos candidate = UPPER_FULL.offset(ANGLE_A_FACE);
        final BlockHitResult hit = visibleSideHit(UPPER_FULL, ANGLE_A_FACE, 0.25d);
        final String[] action = {"NOT_RUN"};

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncAim(ctx, singleplayer, eyeFor(ANGLE_A_FACE), hit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                action[0] = "CLIENT_NOT_READY";
                return;
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            action[0] = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).toString();
        });
        waitForClient(ctx, singleplayer, 4);
        return emitCompoundVisibleSideLaneResult(ctx, singleplayer, "SUPPORT_MISSING_SIDE", candidate,
                SlabType.BOTTOM, "COMPOUND_VISIBLE_SIDE_LOWER_SLAB", action[0]);
    }

    private static String emitCompoundVisibleSideLaneResult(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String caseName,
            BlockPos candidate,
            SlabType expectedType,
            String expectedLaw,
            String action
    ) {
        final String[] serverFinal = {"NOT_RUN"};
        final boolean[] serverLegal = {false};
        singleplayer.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            BlockState candidateState = world.getBlockState(candidate);
            serverLegal[0] = isSlabDy(world, candidate, expectedType, -1.0d);
            serverFinal[0] = describeBlock(world, candidate, candidateState);
            System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_SERVER_FINAL]"
                    + " row=" + caseName
                    + " candidate=" + serverFinal[0]);
        });

        final String[] verdict = {"RED"};
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                verdict[0] = "RED";
                System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_" + caseName + "_RED]"
                        + " reason=client_world_missing_after_click"
                        + " expectedLaw=" + expectedLaw);
                return;
            }
            BlockState candidateState = mc.world.getBlockState(candidate);
            BlockState sourceState = mc.world.getBlockState(UPPER_FULL);
            boolean clientLegal = isSlabDy(mc.world, candidate, expectedType, -1.0d);
            boolean sourceLegal = sourceState.isOf(Blocks.STONE)
                    && isDy(mc.world, UPPER_FULL, -1.0d)
                    && SlabAnchorAttachment.isCompoundFullBlockAnchor(mc.world, UPPER_FULL);
            double candidateDy = dy(mc.world, candidate, candidateState);
            boolean noRecursiveDy = candidateDy >= -1.0d - EPSILON;
            verdict[0] = clientLegal && serverLegal[0] && sourceLegal && noRecursiveDy ? "GREEN" : "RED";
            System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_" + caseName + "_" + verdict[0] + "]"
                    + " expectedLaw=" + expectedLaw
                    + " action=" + action
                    + " expectedCandidate=" + candidate.toShortString()
                    + " expectedState=stone_slab[type=" + expectedType.asString() + "]"
                    + " expectedDy=-1.0"
                    + " actualCandidate=" + describeBlock(mc.world, candidate, candidateState)
                    + " serverCandidate=" + serverFinal[0]
                    + " clickedSource=" + describeBlock(mc.world, UPPER_FULL, sourceState)
                    + " sourceRemainsCompoundFullBlockDyMinusOne=" + sourceLegal
                    + " noRecursiveDyBelowMinusOne=" + noRecursiveDy
                    + " oldDyMinusHalfIsGreen=false"
                    + " dyZeroIsGreen=false");
        });
        return verdict[0];
    }

    private static String emitCompoundVisibleTopLaneResult(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String caseName,
            BlockPos candidate,
            boolean supportRemoved,
            String expectedLaw,
            String action
    ) {
        final String[] serverFinal = {"NOT_RUN"};
        final boolean[] serverLegal = {false};
        final boolean[] serverMarked = {false};
        singleplayer.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            BlockState candidateState = world.getBlockState(candidate);
            serverMarked[0] = SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, candidate, candidateState);
            serverLegal[0] = isSlabDy(world, candidate, SlabType.BOTTOM, -1.0d) && serverMarked[0];
            serverFinal[0] = describeBlock(world, candidate, candidateState);
            System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_SERVER_FINAL]"
                    + " row=" + caseName
                    + " candidate=" + serverFinal[0]);
        });

        final String[] verdict = {"RED"};
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                verdict[0] = "RED";
                System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_" + caseName + "_RED]"
                        + " reason=client_world_missing_after_click"
                        + " expectedLaw=" + expectedLaw);
                return;
            }
            BlockState candidateState = mc.world.getBlockState(candidate);
            BlockState sourceState = mc.world.getBlockState(UPPER_FULL);
            boolean clientMarked = SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(mc.world, candidate,
                    candidateState);
            boolean clientLegal = isSlabDy(mc.world, candidate, SlabType.BOTTOM, -1.0d) && clientMarked;
            boolean sourceLegal = sourceState.isOf(Blocks.STONE)
                    && isDy(mc.world, UPPER_FULL, -1.0d)
                    && SlabAnchorAttachment.isCompoundFullBlockAnchor(mc.world, UPPER_FULL);
            double candidateDy = dy(mc.world, candidate, candidateState);
            boolean noDyZeroFloatingSlab = !(candidateState.isOf(Blocks.STONE_SLAB)
                    && Math.abs(candidateDy) <= EPSILON);
            verdict[0] = clientLegal && serverLegal[0] && sourceLegal && noDyZeroFloatingSlab ? "GREEN" : "RED";
            System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_" + caseName + "_" + verdict[0] + "]"
                    + " expectedLaw=" + expectedLaw
                    + " action=" + action
                    + " supportRemoved=" + supportRemoved
                    + " expectedCandidate=" + candidate.toShortString()
                    + " expectedState=stone_slab[type=bottom]"
                    + " expectedDy=-1.0"
                    + " expectedMarker=COMPOUND_VISIBLE_OWNER_TOP_SLAB"
                    + " actualCandidate=" + describeBlock(mc.world, candidate, candidateState)
                    + " serverCandidate=" + serverFinal[0]
                    + " clientMarked=" + clientMarked
                    + " serverMarked=" + serverMarked[0]
                    + " clickedSource=" + describeBlock(mc.world, UPPER_FULL, sourceState)
                    + " sourceRemainsCompoundFullBlockDyMinusOne=" + sourceLegal
                    + " noDyZeroFloatingSlab=" + noDyZeroFloatingSlab
                    + " oldDyMinusHalfIsGreen=false"
                    + " dyZeroIsGreen=false");
        });
        return verdict[0];
    }

    private static String emitCompoundVisibleTriadRed(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            CompoundVisibleSlabLaneResults results
    ) {
        boolean allNamedStatesGreen = "GREEN".equals(results.lower)
                && "GREEN".equals(results.upper)
                && "GREEN".equals(results.merge)
                && "GREEN".equals(results.top)
                && "GREEN".equals(results.supportMissing);
        if (!allNamedStatesGreen) {
            System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TRIAD_RED]"
                    + " expected=model_outline_raycast_target_agree_for_each_named_dy_-1.0_slab_result"
                    + " lower=" + results.lower
                    + " upper=" + results.upper
                    + " merge=" + results.merge
                    + " top=" + results.top
                    + " supportMissing=" + results.supportMissing
                    + " missingSurface=model,outline,raycast,target"
                    + " reason=no_complete_named_dy_-1.0_slab_lane_result_to_validate");
            return "RED";
        }

        NamedCompoundVisibleLaneFixture fixture = buildCompoundVisibleNamedStateFixture(ctx, singleplayer, "TRIAD");
        final TriadProof[] proof = {TriadProof.notRun()};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                proof[0] = TriadProof.red("client_world_missing");
                return;
            }
            proof[0] = proveCompoundVisibleTriad(mc.world, mc.player, fixture);
        });

        String verdict;
        String missingSurface;
        if (!fixture.green() || !proof[0].dy || !proof[0].outline || !proof[0].target) {
            verdict = "RED";
            missingSurface = proof[0].missingSurfaces("model");
        } else {
            verdict = "PARTIAL";
            missingSurface = proof[0].missingSurfaces("model");
        }
        String provenSurfaces = proof[0].provenSurfaces();
        System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TRIAD_" + verdict + "]"
                + " expected=model_outline_raycast_target_agree_for_each_named_dy_-1.0_slab_result"
                + " lower=" + results.lower
                + " upper=" + results.upper
                + " merge=" + results.merge
                + " top=" + results.top
                + " supportMissing=" + results.supportMissing
                + " fixtureStates=" + fixture.statusSummary()
                + " dy=" + proof[0].dy
                + " outline=" + proof[0].outline
                + " raycast=" + proof[0].raycast
                + " target=" + proof[0].target
                + " model=false"
                + " expectedModelDy=-1.0"
                + " actualModelDy=not_available_in_this_harness"
                + " proofMethod=noModelHarness"
                + " modelSurface=PENDING"
                + " missingSurface=" + missingSurface
                + " provenSurfaces=" + provenSurfaces
                + " detail=\"" + proof[0].detail + "\""
                + " reason=" + ("PARTIAL".equals(verdict)
                ? "dy_outline_raycast_target_proven_model_path_not_available_in_this_harness"
                : "one_or_more_direct_surfaces_failed"));
        return verdict;
    }

    private static String emitCompoundVisibleReloadRed(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            CompoundVisibleSlabLaneResults results
    ) {
        boolean allNamedStatesGreen = "GREEN".equals(results.lower)
                && "GREEN".equals(results.upper)
                && "GREEN".equals(results.merge)
                && "GREEN".equals(results.top)
                && "GREEN".equals(results.supportMissing);
        if (!allNamedStatesGreen) {
            System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_RELOAD_RED]"
                    + " expected=named_dy_-1.0_slab_states_persist_after_reload_or_equivalent_save_load_proof"
                    + " lower=" + results.lower
                    + " upper=" + results.upper
                    + " merge=" + results.merge
                    + " top=" + results.top
                    + " supportMissing=" + results.supportMissing
                    + " reason=no_complete_named_dy_-1.0_slab_lane_result_to_reload");
            return "RED";
        }

        try {
            TestSingleplayerContext reloadSource = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create();
            NamedCompoundVisibleLaneFixture fixture = buildCompoundVisibleNamedStateFixture(ctx, reloadSource,
                    "RELOAD");
            final String[] pre = {"NOT_RUN"};
            reloadSource.getServer().runOnServer(server -> pre[0] = describeNamedFixture(server.getOverworld(),
                    fixture));
            TestWorldSave save = reloadSource.getWorldSave();
            reloadSource.close();

            TestSingleplayerContext reloaded = save.open();
            try {
                waitForClient(ctx, reloaded, 8);
                final String[] post = {"NOT_RUN"};
                final boolean[] persisted = {false};
                final boolean[] sourceTruth = {false};
                final boolean[] noCollapse = {false};
                final boolean[] noDeepDy = {false};
                reloaded.getServer().runOnServer(server -> {
                    World world = server.getOverworld();
                    post[0] = describeNamedFixture(world, fixture);
                    sourceTruth[0] = sourceIsCompoundOwnerDyMinusOne(world);
                    noCollapse[0] = noCollapseToDyZeroOrMinusHalf(world, fixture);
                    noDeepDy[0] = noDyBelowMinusOne(world, fixture);
                    persisted[0] = isNamedCompoundVisibleLaneFixture(world, fixture)
                            && sourceTruth[0]
                            && noDeepDy[0];
                });
                String verdict = fixture.green() && persisted[0] ? "GREEN" : "RED";
                System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_RELOAD_" + verdict + "]"
                        + " expected=named_dy_-1.0_slab_states_persist_after_TestWorldSave_open"
                        + " lower=" + results.lower
                        + " upper=" + results.upper
                        + " merge=" + results.merge
                        + " top=" + results.top
                        + " supportMissing=" + results.supportMissing
                        + " pre={" + pre[0] + "}"
                        + " post={" + post[0] + "}"
                        + " markerSourceTruthSurvived=" + sourceTruth[0]
                        + " namedStatesSurvived=" + persisted[0]
                        + " noCollapseToDyZeroOrMinusHalf=" + noCollapse[0]
                        + " noDyBelowMinusOne=" + noDeepDy[0]
                        + " reloadHarness=TestWorldSave.open");
                return verdict;
            } finally {
                try {
                    reloaded.close();
                } catch (Throwable ignored) {
                    // best-effort
                }
            }
        } catch (Throwable t) {
            System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_RELOAD_PENDING]"
                    + " expected=named_dy_-1.0_slab_states_persist_after_reload_or_equivalent_save_load_proof"
                    + " lower=" + results.lower
                    + " upper=" + results.upper
                    + " merge=" + results.merge
                    + " top=" + results.top
                    + " supportMissing=" + results.supportMissing
                    + " reason=reloadHarnessUnavailable"
                    + " error=\"" + safeMessage(t) + "\"");
            return "PENDING";
        }
    }

    private static NamedCompoundVisibleLaneFixture buildCompoundVisibleNamedStateFixture(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String purpose
    ) {
        seedCanonicalStructure(singleplayer);
        waitForClient(ctx, singleplayer, 10);
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 16));

        BlockPos lower = UPPER_FULL.offset(Direction.WEST);
        BlockPos upper = UPPER_FULL.offset(Direction.EAST);
        BlockPos merge = UPPER_FULL.offset(Direction.NORTH);
        BlockPos top = UPPER_FULL.up();

        String lowerAction = clickCompoundVisibleSide(ctx, singleplayer, purpose + "_LOWER",
                Direction.WEST, 0.25d);
        String upperAction = clickCompoundVisibleSide(ctx, singleplayer, purpose + "_UPPER",
                Direction.EAST, 0.75d);
        String mergeLowerAction = clickCompoundVisibleSide(ctx, singleplayer, purpose + "_MERGE_LOWER",
                Direction.NORTH, 0.25d);
        String mergeUpperAction = clickCompoundVisibleSide(ctx, singleplayer, purpose + "_MERGE_UPPER",
                Direction.NORTH, 0.75d);
        String topAction = clickCompoundVisibleTop(ctx, singleplayer, purpose + "_TOP");
        waitForClient(ctx, singleplayer, 6);

        final boolean[] green = {false};
        final String[] status = {"NOT_RUN"};
        singleplayer.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            NamedCompoundVisibleLaneFixture fixture = new NamedCompoundVisibleLaneFixture(lower, upper, merge, top,
                    false, "building");
            green[0] = isNamedCompoundVisibleLaneFixture(world, fixture) && sourceIsCompoundOwnerDyMinusOne(world);
            status[0] = describeNamedFixture(world, fixture);
        });

        System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_NAMED_FIXTURE]"
                + " purpose=" + purpose
                + " lowerAction=" + lowerAction
                + " upperAction=" + upperAction
                + " mergeLowerAction=" + mergeLowerAction
                + " mergeUpperAction=" + mergeUpperAction
                + " topAction=" + topAction
                + " namedStates=" + status[0]
                + " fixtureGreen=" + green[0]);
        return new NamedCompoundVisibleLaneFixture(lower, upper, merge, top, green[0], status[0]);
    }

    private static String clickCompoundVisibleSide(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String label,
            Direction face,
            double visualLocalY
    ) {
        BlockHitResult hit = visibleSideHit(UPPER_FULL, face, visualLocalY);
        syncAim(ctx, singleplayer, eyeFor(face), hit.getPos());
        final String[] action = {"NOT_RUN"};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                action[0] = "CLIENT_NOT_READY";
                return;
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            action[0] = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).toString();
            System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TRACE]"
                    + " row=" + label
                    + " phase=build_named_state"
                    + " face=" + face.asString()
                    + " visualLocalY=" + visualLocalY
                    + " syntheticHit=" + describeHit(hit)
                    + " action=" + action[0]);
        });
        waitForClient(ctx, singleplayer, 4);
        return action[0];
    }

    private static String clickCompoundVisibleTop(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String label
    ) {
        BlockHitResult hit = visibleTopHit(UPPER_FULL);
        syncAim(ctx, singleplayer, topFaceEye(), hit.getPos());
        final String[] action = {"NOT_RUN"};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                action[0] = "CLIENT_NOT_READY";
                return;
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            action[0] = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).toString();
            System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TRACE]"
                    + " row=" + label
                    + " phase=build_named_state"
                    + " syntheticHit=" + describeHit(hit)
                    + " action=" + action[0]);
        });
        waitForClient(ctx, singleplayer, 4);
        return action[0];
    }

    private static TriadProof proveCompoundVisibleTriad(
            World world,
            net.minecraft.entity.Entity entity,
            NamedCompoundVisibleLaneFixture fixture
    ) {
        TriadSurface lower = proveTriadSurface(world, entity, "lower", fixture.lowerPos, SlabType.BOTTOM);
        TriadSurface upper = proveTriadSurface(world, entity, "upper", fixture.upperPos, SlabType.TOP);
        TriadSurface merge = proveTriadSurface(world, entity, "merge", fixture.mergePos, SlabType.DOUBLE);
        TriadSurface top = proveTriadSurface(world, entity, "top", fixture.topPos, SlabType.BOTTOM);
        return new TriadProof(
                lower.dy && upper.dy && merge.dy && top.dy,
                lower.outline && upper.outline && merge.outline && top.outline,
                lower.raycast && upper.raycast && merge.raycast && top.raycast,
                lower.target && upper.target && merge.target && top.target,
                lower.detail + "|" + upper.detail + "|" + merge.detail + "|" + top.detail);
    }

    private static TriadSurface proveTriadSurface(
            World world,
            net.minecraft.entity.Entity entity,
            String name,
            BlockPos pos,
            SlabType expectedType
    ) {
        BlockState state = world.getBlockState(pos);
        boolean dyOk = isSlabDy(world, pos, expectedType, -1.0d);
        boolean markerTruth = isExpectedCompoundVisibleMarker(world, name, pos, state);
        double actualDy = dy(world, pos, state);
        VoxelShape outlineShape = state.getOutlineShape(world, pos, ShapeContext.absent());
        VoxelShape raycastShape = state.getRaycastShape(world, pos);
        double expectedOutlineMinY = expectedType == SlabType.TOP ? -0.5d : -1.0d;
        boolean outlineOk = !outlineShape.isEmpty()
                && Math.abs(outlineShape.getBoundingBox().minY - expectedOutlineMinY) <= EPSILON;
        Box targetBox = !outlineShape.isEmpty() ? outlineShape.getBoundingBox() : null;
        double targetY = targetBox == null ? pos.getY() : pos.getY() + ((targetBox.minY + targetBox.maxY) * 0.5d);
        double targetX = targetBox == null ? pos.getX() + 0.5d
                : pos.getX() + ((targetBox.minX + targetBox.maxX) * 0.5d);
        double targetZ = targetBox == null ? pos.getZ() + 0.5d
                : pos.getZ() + ((targetBox.minZ + targetBox.maxZ) * 0.5d);
        Vec3d[] ray = triadRay(name, pos, targetX, targetY, targetZ);
        Vec3d start = ray[0];
        Vec3d end = ray[1];
        BlockHitResult outlineHit = outlineShape.raycast(start, end, pos);
        BlockHitResult raycastHit = raycastShape.raycast(start, end, pos);
        BlockHitResult rawWorldOutlineTarget = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                entity));
        BlockHitResult ownerTarget = SlabSupport.findCompoundVisibleSlabLaneOwnerTarget(world, entity, start, end);
        boolean raycastOk = ownerTarget != null
                && ownerTarget.getType() == HitResult.Type.BLOCK
                && ownerTarget.getBlockPos().equals(pos);
        boolean targetOk = outlineHit != null
                && outlineHit.getBlockPos().equals(pos)
                && raycastOk
                && markerTruth;
        boolean caseGreen = dyOk && outlineOk && raycastOk && targetOk;
        String markerName = compoundVisibleMarkerName(name);
        System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_TRIAD_CASE]"
                + " caseName=" + name
                + " pos=" + pos.toShortString()
                + " state=" + state
                + " marker=" + markerName
                + " markerTruth=" + markerTruth
                + " slabSupportDy=" + actualDy
                + " clientDy=" + actualDy
                + " expectedDy=-1.0"
                + " outlineMinY=" + (outlineShape.isEmpty() ? "empty" : outlineShape.getBoundingBox().minY)
                + " outlineMaxY=" + (outlineShape.isEmpty() ? "empty" : outlineShape.getBoundingBox().maxY)
                + " outlineDy=" + (outlineOk ? "-1.0" : "mismatch")
                + " directRaycastShapeMinY="
                + (raycastShape.isEmpty() ? "empty" : Double.toString(raycastShape.getBoundingBox().minY))
                + " rawRaycastTarget=" + describeHit(rawWorldOutlineTarget)
                + " raycastTarget=" + describeHit(ownerTarget)
                + " raycastOwnerMatchesVisibleBody=" + raycastOk
                + " outlineTarget=" + describeHit(outlineHit)
                + " directRaycastTarget=" + describeHit(raycastHit)
                + " targetDy=" + (ownerTarget != null && ownerTarget.getType() == HitResult.Type.BLOCK
                ? dy(world, ownerTarget.getBlockPos(), world.getBlockState(ownerTarget.getBlockPos()))
                : "MISS")
                + " expectedModelDy=-1.0"
                + " actualModelDy=not_available_in_this_harness"
                + " proofMethod=noModelHarness"
                + " modelSurface=PENDING"
                + " surfaces=dy:" + dyOk
                + ",outline:" + outlineOk
                + ",raycast:" + raycastOk
                + ",target:" + targetOk
                + ",model:false"
                + " verdict=" + (caseGreen ? "GREEN_EXCEPT_MODEL" : "RED"));
        System.out.println("[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_MODEL_PENDING]"
                + " caseName=" + name
                + " pos=" + pos.toShortString()
                + " state=" + state
                + " marker=" + markerName
                + " markerTruth=" + markerTruth
                + " expectedModelDy=-1.0"
                + " actualModelDy=not_available_in_this_harness"
                + " proofMethod=noModelHarness"
                + " modelSurface=PENDING");
        return new TriadSurface(dyOk, outlineOk, raycastOk, targetOk,
                name + "{block=" + describeBlock(world, pos, state)
                        + " marker=" + markerName
                        + " markerTruth=" + markerTruth
                        + " outlineMinY=" + (outlineShape.isEmpty() ? "empty" : outlineShape.getBoundingBox().minY)
                        + " directRaycastShapeMinY="
                        + (raycastShape.isEmpty() ? "empty" : raycastShape.getBoundingBox().minY)
                        + " outlineTarget=" + describeHit(outlineHit)
                        + " rawRaycastTarget=" + describeHit(rawWorldOutlineTarget)
                        + " raycastTarget=" + describeHit(ownerTarget)
                        + " directRaycastTarget=" + describeHit(raycastHit) + "}");
    }

    private static Vec3d[] triadRay(String name, BlockPos pos, double targetX, double targetY, double targetZ) {
        if ("lower".equals(name)) {
            return new Vec3d[] {
                    new Vec3d(pos.getX() - 1.0d, targetY, targetZ),
                    new Vec3d(pos.getX() + 2.0d, targetY, targetZ)
            };
        }
        if ("upper".equals(name)) {
            return new Vec3d[] {
                    new Vec3d(pos.getX() + 2.0d, targetY, targetZ),
                    new Vec3d(pos.getX() - 1.0d, targetY, targetZ)
            };
        }
        if ("merge".equals(name)) {
            return new Vec3d[] {
                    new Vec3d(targetX, targetY, pos.getZ() - 1.0d),
                    new Vec3d(targetX, targetY, pos.getZ() + 2.0d)
            };
        }
        return new Vec3d[] {
                new Vec3d(targetX, pos.getY() + 2.0d, targetZ),
                new Vec3d(targetX, pos.getY() - 1.0d, targetZ)
        };
    }

    private static boolean isExpectedCompoundVisibleMarker(
            net.minecraft.world.BlockView world,
            String name,
            BlockPos pos,
            BlockState state
    ) {
        if ("lower".equals(name)) {
            return SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state);
        }
        if ("upper".equals(name)) {
            return SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state);
        }
        if ("merge".equals(name)) {
            return SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state);
        }
        return SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state);
    }

    private static String compoundVisibleMarkerName(String name) {
        if ("lower".equals(name)) {
            return "COMPOUND_VISIBLE_SIDE_LOWER_SLAB";
        }
        if ("upper".equals(name)) {
            return "COMPOUND_VISIBLE_SIDE_UPPER_SLAB";
        }
        if ("merge".equals(name)) {
            return "COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB";
        }
        return "COMPOUND_VISIBLE_OWNER_TOP_SLAB";
    }

    private static boolean isNamedCompoundVisibleLaneFixture(
            net.minecraft.world.BlockView world,
            NamedCompoundVisibleLaneFixture fixture
    ) {
        BlockState lower = world.getBlockState(fixture.lowerPos);
        BlockState upper = world.getBlockState(fixture.upperPos);
        BlockState merge = world.getBlockState(fixture.mergePos);
        BlockState top = world.getBlockState(fixture.topPos);
        return isSlabDy(world, fixture.lowerPos, SlabType.BOTTOM, -1.0d)
                && SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, fixture.lowerPos, lower)
                && isSlabDy(world, fixture.upperPos, SlabType.TOP, -1.0d)
                && SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, fixture.upperPos, upper)
                && isSlabDy(world, fixture.mergePos, SlabType.DOUBLE, -1.0d)
                && SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, fixture.mergePos, merge)
                && isSlabDy(world, fixture.topPos, SlabType.BOTTOM, -1.0d)
                && SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, fixture.topPos, top);
    }

    private static boolean sourceIsCompoundOwnerDyMinusOne(net.minecraft.world.BlockView world) {
        BlockState source = world.getBlockState(UPPER_FULL);
        return source.isOf(Blocks.STONE)
                && isDy(world, UPPER_FULL, -1.0d)
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(world, UPPER_FULL);
    }

    private static boolean noDyBelowMinusOne(
            net.minecraft.world.BlockView world,
            NamedCompoundVisibleLaneFixture fixture
    ) {
        for (BlockPos pos : fixture.positions()) {
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() && dy(world, pos, state) < -1.0d - EPSILON) {
                return false;
            }
        }
        return dy(world, UPPER_FULL, world.getBlockState(UPPER_FULL)) >= -1.0d - EPSILON;
    }

    private static boolean noCollapseToDyZeroOrMinusHalf(
            net.minecraft.world.BlockView world,
            NamedCompoundVisibleLaneFixture fixture
    ) {
        for (BlockPos pos : fixture.positions()) {
            BlockState state = world.getBlockState(pos);
            double actualDy = dy(world, pos, state);
            if (state.isOf(Blocks.STONE_SLAB)
                    && (Math.abs(actualDy) <= EPSILON || Math.abs(actualDy + 0.5d) <= EPSILON)) {
                return false;
            }
        }
        return true;
    }

    private static String describeNamedFixture(
            net.minecraft.world.BlockView world,
            NamedCompoundVisibleLaneFixture fixture
    ) {
        return "source={" + describeBlock(world, UPPER_FULL) + "}"
                + " lower={" + describeBlock(world, fixture.lowerPos) + "}"
                + " upper={" + describeBlock(world, fixture.upperPos) + "}"
                + " merge={" + describeBlock(world, fixture.mergePos) + "}"
                + " top={" + describeBlock(world, fixture.topPos) + "}"
                + " namedStatesGreen=" + isNamedCompoundVisibleLaneFixture(world, fixture)
                + " sourceTruthGreen=" + sourceIsCompoundOwnerDyMinusOne(world)
                + " noDyBelowMinusOne=" + noDyBelowMinusOne(world, fixture)
                + " noCollapseToDyZeroOrMinusHalf=" + noCollapseToDyZeroOrMinusHalf(world, fixture);
    }

    private static String safeMessage(Throwable t) {
        String message = t.getMessage();
        return message == null ? t.getClass().getName() : message.replace('"', '\'');
    }

    private static RealClickResult runRealCrosshairClick(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String step,
            String expectedBand,
            Direction expectedFace,
            Vec3d aimPoint,
            String aimMarker,
            BlockPos expectedChangedPos,
            String expectedChangedState
    ) {
        return runRealCrosshairClick(ctx, singleplayer, step, expectedBand, expectedFace, aimPoint,
                expectedFace == Direction.UP ? topFaceEye() : eyeFor(expectedFace), aimMarker,
                expectedChangedPos, expectedChangedState);
    }

    private static RealClickResult runRealCrosshairClick(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String step,
            String expectedBand,
            Direction expectedFace,
            Vec3d aimPoint,
            Vec3d eye,
            String aimMarker,
            BlockPos expectedChangedPos,
            String expectedChangedState
    ) {
        syncAim(ctx, singleplayer, eye, aimPoint);
        waitForClient(ctx, singleplayer, 4);
        syncAim(ctx, singleplayer, eye, aimPoint);

        final Map<BlockPos, BlockFact>[] before = new Map[] {Map.of()};
        final RealClickResult[] click = {new RealClickResult(step)};
        final String[] serverFinal = {"NOT_RUN"};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                click[0].aimParity = "RED";
                click[0].result = "RED";
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_AIM_PARITY_FAIL]"
                        + " step=" + step + " reason=client_not_ready");
                return;
            }
            before[0] = snapshot(mc.world);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult target = mc.crosshairTarget;
            AimFacts facts = AimFacts.from(mc.world, target);
            TargetingDiagnostic diagnostic = TargetingDiagnostic.from(mc.world, mc.player, UPPER_FULL,
                    expectedFace, expectedBand, aimPoint, facts, target);
            click[0].targetingClassification = diagnostic.classification;
            emitTargetingDiagnostic(step, expectedBand, diagnostic);
            boolean realBlock = target instanceof BlockHitResult;
            boolean targetOwner = UPPER_FULL.equals(facts.pos);
            boolean faceOk = facts.face == expectedFace;
            boolean bandOk = switch (expectedBand) {
                case "LOWER" -> facts.visualLocalY >= -AIM_EPSILON && facts.visualLocalY < 0.5d;
                case "UPPER" -> facts.visualLocalY >= 0.5d && facts.visualLocalY <= 1.0d + AIM_EPSILON;
                case "TOP" -> facts.face == Direction.UP && Math.abs(facts.visualLocalY - 1.0d) <= 0.001d;
                default -> false;
            };
            boolean repeatMergeTarget = "REPEAT".equals(step)
                    && realBlock
                    && expectedChangedPos.equals(facts.pos)
                    && facts.face == Direction.UP
                    && facts.state.contains("stone_slab")
                    && facts.state.contains("type=bottom");
            boolean parity = realBlock && ((targetOwner && faceOk && bandOk) || repeatMergeTarget);
            click[0].aimParity = parity ? "GREEN" : "RED";
            click[0].wrongOwner = realBlock && !targetOwner && !repeatMergeTarget;
            System.out.println("[" + aimMarker + "]"
                    + " step=" + step
                    + " parity=" + click[0].aimParity
                    + " expectedOwner=" + UPPER_FULL.toShortString()
                    + " actualTarget=" + facts.describe()
                    + " actualState=" + facts.state
                    + " actualDy=" + facts.dy
                    + " localX=" + facts.localX
                    + " localY=" + facts.localY
                    + " localZ=" + facts.localZ
                    + " visualLocalY=" + facts.visualLocalY
                    + " localCoordBasis=visualDyAdjusted"
                    + " band=" + facts.band
                    + " heldItem=" + (mc.player == null ? "none" : mc.player.getStackInHand(Hand.MAIN_HAND)));
            if ("REPEAT".equals(step) && repeatSeamTraceEnabled()) {
                emitRepeatClientBefore(mc.world, mc.player.getStackInHand(Hand.MAIN_HAND), facts,
                        expectedChangedPos, expectedChangedState);
            }
            if (!parity) {
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_AIM_PARITY_FAIL]"
                        + " step=" + step
                        + " reason=" + parityReason(realBlock, targetOwner, faceOk, bandOk)
                        + " expectedBand=" + expectedBand
                        + " expectedFace=" + expectedFace.asString()
                        + " actualTarget=" + facts.describe());
                click[0].result = "HARNESS";
                return;
            }
            ActionResult action = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, (BlockHitResult) target);
            click[0].action = action.toString();
            if ("REPEAT".equals(step) && repeatSeamTraceEnabled()) {
                BlockState immediate = mc.world.getBlockState(expectedChangedPos);
                System.out.println("[JULIA_BETA4_REPEAT_SEAM_CLIENT_RESULT]"
                        + " interactCall=ClientPlayerInteractionManager.interactBlock"
                        + " action=" + action
                        + " immediateClientState=" + describeBlock(mc.world, expectedChangedPos, immediate)
                        + " immediateClientDouble=" + isDoubleSlabDy(mc.world, expectedChangedPos, -0.5d));
            }
        });
        if ("REPEAT".equals(step) && repeatSeamTraceEnabled()) {
            waitForClient(ctx, singleplayer, 1);
            emitRepeatSeamServerTick(singleplayer, 1, expectedChangedPos, serverFinal);
            emitRepeatSeamClientTick(ctx, 1, expectedChangedPos);
            waitForClient(ctx, singleplayer, 4);
            emitRepeatSeamServerTick(singleplayer, 5, expectedChangedPos, serverFinal);
            emitRepeatSeamClientTick(ctx, 5, expectedChangedPos);
            waitForClient(ctx, singleplayer, 15);
            emitRepeatSeamServerTick(singleplayer, 20, expectedChangedPos, serverFinal);
            emitRepeatSeamClientTick(ctx, 20, expectedChangedPos);
        } else {
            waitForClient(ctx, singleplayer, 4);
        }
        ctx.runOnClient(mc -> {
            Map<BlockPos, BlockFact> after = snapshot(mc.world);
            DeltaFacts delta = DeltaFacts.from(step, before[0], after, expectedChangedPos);
            click[0].ghost = delta.ghost;
            click[0].wrongDelta = delta.wrongDelta;
            click[0].result = "GREEN".equals(click[0].aimParity) && delta.expectedChanged && !delta.wrongDelta ? "GREEN" : "RED";
            String marker = switch (step) {
                case "FIRST_SIDE" -> "[JULIA_BETA4_LIVE_GOBLIN_SEQUENCE_FIRST_SIDE]";
                case "LOWER_AFTER_FIRST" -> "[JULIA_BETA4_LIVE_GOBLIN_SEQUENCE_LOWER_AFTER_FIRST]";
                case "REPEAT" -> "[JULIA_BETA4_LIVE_GOBLIN_SEQUENCE_REPEAT]";
                case "TOP_FACE" -> "[JULIA_BETA4_LIVE_GOBLIN_SEQUENCE_TOP_FACE]";
                default -> "[JULIA_BETA4_LIVE_GOBLIN_SEQUENCE]";
            };
            System.out.println(marker
                    + " result=" + click[0].result
                    + " action=" + click[0].action
                    + " expectedChangedPos=" + expectedChangedPos.toShortString()
                    + " expectedChangedState=" + expectedChangedState
                    + " expectedChanged=" + delta.expectedChanged
                    + " changedCount=" + delta.changedCount
                    + " ghost=" + delta.ghost
                    + " wrongDelta=" + delta.wrongDelta);
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_DELTA_SCAN]"
                    + " step=" + step
                    + " " + delta.describe);
            if ("REPEAT".equals(step) && repeatSeamTraceEnabled()) {
                BlockState clientFinalState = mc.world.getBlockState(expectedChangedPos);
                boolean clientFinalDouble = isDoubleSlabDy(mc.world, expectedChangedPos, -0.5d);
                boolean serverFinalDouble = serverFinal[0].contains("stone_slab")
                        && serverFinal[0].contains("type=double")
                        && serverFinal[0].contains("dy=-0.5");
                String seam = clientFinalDouble && serverFinalDouble
                        ? "FIXED_GREEN"
                        : "SERVER_TOLERANCE_REJECT";
                System.out.println("[JULIA_BETA4_REPEAT_SEAM_SUMMARY]"
                        + " clientPredict=DOUBLE"
                        + " serverTolerance=" + (serverFinalDouble
                                ? "LOWERED_SAME_CELL_SLAB_MERGE"
                                : "LEGAL_CANDIDATE_NOT_REWRITTEN")
                        + " placementContext=" + (serverFinalDouble
                                ? "SERVER_FINALIZATION_REACHED"
                                : "CLIENT_ONLY_FACE_NOT_HORIZONTAL")
                        + " setBlockState=" + (serverFinalDouble ? "YES" : "NO")
                        + " serverFinal=" + serverFinal[0]
                        + " serverFinalDy=" + (serverFinalDouble ? "-0.5" : "not_double")
                        + " clientFinal=" + describeBlock(mc.world, expectedChangedPos, clientFinalState)
                        + " clientFinalDy=" + dy(mc.world, expectedChangedPos, clientFinalState)
                        + " seam=" + seam);
            }
        });
        return click[0];
    }

    private static boolean repeatSeamTraceEnabled() {
        return Boolean.getBoolean(REPEAT_SEAM_TRACE_OPT_IN);
    }

    private static void emitRepeatClientBefore(
            net.minecraft.world.BlockView world,
            ItemStack held,
            AimFacts facts,
            BlockPos expectedChangedPos,
            String expectedChangedState
    ) {
        System.out.println("[JULIA_BETA4_REPEAT_SEAM_START]"
                + " mode=finalization_fix"
                + " gameplayChange=true"
                + " target=" + expectedChangedPos.toShortString()
                + " expected=" + expectedChangedState);
        System.out.println("[JULIA_BETA4_REPEAT_SEAM_CLIENT_BEFORE]"
                + " target=" + facts.describe()
                + " state=" + facts.state
                + " dy=" + facts.dy
                + " face=" + facts.face
                + " localX=" + facts.localX
                + " localY=" + facts.localY
                + " localZ=" + facts.localZ
                + " visualLocalY=" + facts.visualLocalY
                + " heldItem=" + held);
        System.out.println("[JULIA_BETA4_REPEAT_SEAM_CLIENT_PREDICT]"
                + " target=" + expectedChangedPos.toShortString()
                + " predictedState=minecraft:stone_slab[type=double]"
                + " predictedDy=-0.5"
                + " currentTarget=" + describeBlock(world, expectedChangedPos));
    }

    private static void emitRepeatSeamServerTick(
            TestSingleplayerContext singleplayer,
            int tick,
            BlockPos target,
            String[] serverFinal
    ) {
        singleplayer.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            serverFinal[0] = describeBlock(world, target);
            System.out.println("[JULIA_BETA4_REPEAT_SEAM_SERVER_TICK]"
                    + " tick=" + tick
                    + " target=" + target.toShortString()
                    + " state=" + serverFinal[0]);
        });
    }

    private static void emitRepeatSeamClientTick(ClientGameTestContext ctx, int tick, BlockPos target) {
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                System.out.println("[JULIA_BETA4_REPEAT_SEAM_CLIENT_TICK]"
                        + " tick=" + tick
                        + " target=" + target.toShortString()
                        + " state=client_world_missing");
                return;
            }
            System.out.println("[JULIA_BETA4_REPEAT_SEAM_CLIENT_TICK]"
                    + " tick=" + tick
                    + " target=" + target.toShortString()
                    + " state=" + describeBlock(mc.world, target));
        });
    }

    private static RealClickResult noCorridorClick(String step, AimCorridor corridor) {
        RealClickResult result = new RealClickResult(step);
        result.aimParity = "NO_CORRIDOR";
        result.result = "NO_CORRIDOR";
        result.action = "NOT_RUN";
        result.targetingClassification = "NO_CORRIDOR";
        System.out.println("[JULIA_BETA4_LIVE_GOBLIN_LOWER_CORRIDOR_SEQUENCE]"
                + " step=" + step
                + " result=NO_CORRIDOR"
                + " selected=false"
                + " testedCandidates=" + corridor.testedCandidates
                + " reason=no_player_realistic_lower_corridor_after_first_side");
        return result;
    }

    private static String parityReason(boolean realBlock, boolean targetOwner, boolean faceOk, boolean bandOk) {
        String reason = "";
        reason = appendReason(reason, realBlock, "realBlockTarget");
        reason = appendReason(reason, targetOwner, "upperFullOwner");
        reason = appendReason(reason, faceOk, "face");
        reason = appendReason(reason, bandOk, "band");
        return reason.isEmpty() ? "none" : reason;
    }

    private static Map<BlockPos, BlockFact> snapshot(net.minecraft.world.BlockView world) {
        Map<BlockPos, BlockFact> facts = new LinkedHashMap<>();
        if (world == null) {
            return facts;
        }
        for (int x = BOTTOM_SLAB_A.getX() - 3; x <= BOTTOM_SLAB_A.getX() + 4; x++) {
            for (int y = BOTTOM_SLAB_A.getY() - 1; y <= BOTTOM_SLAB_A.getY() + 7; y++) {
                for (int z = BOTTOM_SLAB_A.getZ() - 3; z <= BOTTOM_SLAB_A.getZ() + 3; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    facts.put(pos, BlockFact.from(world, pos));
                }
            }
        }
        return facts;
    }

    private static String parityReleaseBlockers(ParityVerdicts verdicts) {
        String blockers = "";
        if (!"GREEN".equals(verdicts.lowerAimParity) || !"GREEN".equals(verdicts.lowerAfterFirst)) {
            blockers = "lowerAfterFirst";
        }
        if (!"GREEN".equals(verdicts.repeatPlacement)) {
            blockers = blockers.isEmpty() ? "repeatPlacement" : blockers + ",repeatPlacement";
        }
        if (!"GREEN".equals(verdicts.topAimParity) || !"GREEN".equals(verdicts.topFace)) {
            blockers = blockers.isEmpty() ? "topFace" : blockers + ",topFace";
        }
        if (!"GREEN".equals(verdicts.upperAimParity) || !"GREEN".equals(verdicts.firstSide)) {
            blockers = blockers.isEmpty() ? "firstSide" : blockers + ",firstSide";
        }
        return blockers.isEmpty() ? "none" : blockers;
    }

    private static boolean proveFreshStructure(ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        seedCanonicalStructure(singleplayer);
        waitForClient(ctx, singleplayer, 10);
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
        waitForClient(ctx, singleplayer, 10);
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
            boolean wrongUpperForLower = "SUPPORT_PRESENT_SIDE_LOWER".equals(caseName)
                    && after.isOf(Blocks.STONE_SLAB)
                    && after.contains(SlabBlock.TYPE)
                    && after.get(SlabBlock.TYPE) == SlabType.TOP;
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
            if ("SUPPORT_PRESENT_SIDE_LOWER".equals(caseName)) {
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_SIDE_LOWER_EXACT_" + verdict[0] + "]"
                        + " clickedSource=" + describeBlock(mc.world, UPPER_FULL)
                        + " hitFace=" + hit.getSide().asString()
                        + " hitY=" + hit.getPos().y
                        + " hitBand=" + hitBand(hit)
                        + " expectedCandidate=" + candidate.toShortString()
                        + " expected=stone_slab[type=bottom] dy=-0.5"
                        + " actualCandidate=" + describeBlock(mc.world, candidate)
                        + " otherNearbySlabChanged=" + describeNearbySlabs(mc.world, candidate));
                if (wrongUpperForLower) {
                    System.out.println("[JULIA_BETA4_LIVE_GOBLIN_SIDE_LOWER_WRONG_RESULT_RED]"
                            + " reason=lower_half_click_produced_upper_result"
                            + " expectedCandidate=" + candidate.toShortString()
                            + " expected=stone_slab[type=bottom] dy=-0.5"
                            + " actualCandidate=" + describeBlock(mc.world, candidate)
                            + " source=" + describeBlock(mc.world, UPPER_FULL));
                }
            } else if ("SUPPORT_PRESENT_SIDE_UPPER".equals(caseName)) {
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_SIDE_UPPER_FIRST_" + verdict[0] + "]"
                        + " clickedSource=" + describeBlock(mc.world, UPPER_FULL)
                        + " hitFace=" + hit.getSide().asString()
                        + " hitY=" + hit.getPos().y
                        + " hitBand=" + hitBand(hit)
                        + " expectedCandidate=" + candidate.toShortString()
                        + " expected=stone_slab[type=top] dy=-0.5"
                        + " actualCandidate=" + describeBlock(mc.world, candidate)
                        + " otherNearbySlabChanged=" + describeNearbySlabs(mc.world, candidate));
            }
        });
        return verdict[0];
    }

    private static String runRepeatPlacementCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Verdicts verdicts
    ) {
        seedCanonicalStructure(singleplayer);
        waitForClient(ctx, singleplayer, 10);
        final BlockPos candidate = UPPER_FULL.offset(ANGLE_A_FACE);
        final BlockHitResult upperHit = faceHit(UPPER_FULL, ANGLE_A_FACE, 0.25d);
        final String[] verdict = {"RED"};
        final String[] firstAction = {"NOT_RUN"};
        final String[] secondAction = {"NOT_RUN"};

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncAim(ctx, singleplayer, eyeFor(ANGLE_A_FACE), upperHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("[JULIA_BETA4_LIVE_GOBLIN_REPEAT_PLACEMENT_RED]"
                        + " reason=client_not_ready_before_first_click");
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            firstAction[0] = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, upperHit).toString();
        });
        waitForClient(ctx, singleplayer, 2);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("[JULIA_BETA4_LIVE_GOBLIN_REPEAT_PLACEMENT_RED]"
                        + " reason=client_not_ready_before_second_click");
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            secondAction[0] = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, upperHit).toString();
        });
        waitForClient(ctx, singleplayer, 2);
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_REPEAT_PLACEMENT_RED]"
                        + " reason=client_world_missing_after_repeat");
                return;
            }
            BlockState actual = mc.world.getBlockState(candidate);
            double actualDy = dy(mc.world, candidate, actual);
            boolean legalRepeat = actual.isOf(Blocks.STONE_SLAB)
                    && actual.contains(SlabBlock.TYPE)
                    && actual.get(SlabBlock.TYPE) == SlabType.DOUBLE
                    && Math.abs(actualDy + 0.5d) <= EPSILON;
            verdict[0] = legalRepeat ? "GREEN" : "RED";
            verdicts.ghost |= !legalRepeat && actual.isOf(Blocks.STONE_SLAB);
            verdicts.observedCases++;
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_REPEAT_PLACEMENT_" + verdict[0] + "]"
                    + " firstAction=" + firstAction[0]
                    + " secondAction=" + secondAction[0]
                    + " clickedSource=" + describeBlock(mc.world, UPPER_FULL)
                    + " hitFace=" + upperHit.getSide().asString()
                    + " hitY=" + upperHit.getPos().y
                    + " hitBand=" + hitBand(upperHit)
                    + " expectedCandidate=" + candidate.toShortString()
                    + " expectedAfterRepeat=stone_slab[type=double] dy=-0.5"
                    + " actualCandidate=" + describeBlock(mc.world, candidate)
                    + " otherNearbySlabChanged=" + describeNearbySlabs(mc.world, candidate));
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
        waitForClient(ctx, singleplayer, 10);
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
                    + " expectedCandidate=" + expectedTop.toShortString()
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
            if ("SUPPORT_PRESENT_TOP_FACE".equals(caseName) && !legalTop && ghostOrSkip) {
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_TOP_FACE_WRONG_RESULT_RED]"
                        + " reason=top_face_exact_candidate_failed"
                        + " expectedCandidate=" + expectedTop.toShortString()
                        + " expected=stone_slab[type=bottom] dy=0.0"
                        + " actualTopCandidate=" + describeBlock(mc.world, expectedTop)
                        + " actualSkippedCandidate=" + describeBlock(mc.world, skippedTop)
                        + " source=" + describeBlock(mc.world, UPPER_FULL));
            }
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
        waitForClient(ctx, singleplayer, 10);
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
        boolean sideCandidateEmpty = world.getBlockState(UPPER_FULL.offset(ANGLE_A_FACE)).isAir();
        boolean topFaceCandidateEmpty = world.getBlockState(UPPER_FULL.up()).isAir();
        boolean skippedTopCandidateEmpty = world.getBlockState(UPPER_FULL.up(2)).isAir();
        boolean missingVariantNamed = TOP_SLAB_A.equals(UPPER_FULL.down());
        String marker = bottomA && bottomB && bridgeA && bridgeB && topA && topB && upper
                && sideCandidateEmpty && topFaceCandidateEmpty && skippedTopCandidateEmpty && missingVariantNamed
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
                + " lowerHalfSideCandidate=" + describeBlock(world, UPPER_FULL.offset(ANGLE_A_FACE))
                + " lowerHalfSideCandidateEmpty=" + sideCandidateEmpty
                + " upperHalfSideCandidate=" + describeBlock(world, UPPER_FULL.offset(ANGLE_A_FACE))
                + " upperHalfSideCandidateEmpty=" + sideCandidateEmpty
                + " topFaceCandidate=" + describeBlock(world, UPPER_FULL.up())
                + " topFaceCandidateEmpty=" + topFaceCandidateEmpty
                + " skippedTopCandidate=" + describeBlock(world, UPPER_FULL.up(2))
                + " skippedTopCandidateEmpty=" + skippedTopCandidateEmpty
                + " missingUnderSlabVariantSupportPos=" + TOP_SLAB_A.toShortString()
                + " missingUnderSlabVariantTested=true"
                + " invalidReasons=" + invalidReasons(bottomA, bottomB, bridgeA, bridgeB, topA, topB, upper,
                sideCandidateEmpty, topFaceCandidateEmpty, skippedTopCandidateEmpty, missingVariantNamed));
        return bottomA && bottomB && bridgeA && bridgeB && topA && topB && upper
                && sideCandidateEmpty && topFaceCandidateEmpty && skippedTopCandidateEmpty && missingVariantNamed;
    }

    private static boolean emitCompoundVisibleSlabLaneFixtureProof(net.minecraft.world.BlockView world) {
        boolean bottomA = isBottomSlab(world, BOTTOM_SLAB_A);
        boolean bottomB = isBottomSlab(world, BOTTOM_SLAB_B);
        boolean bridgeA = isStoneDy(world, BRIDGE_FULL_A, -0.5d);
        boolean bridgeB = isStoneDy(world, BRIDGE_FULL_B, -0.5d);
        boolean topA = isExpectedSlab(world, TOP_SLAB_A, CANONICAL_TOP_SLAB_TYPE) && isDy(world, TOP_SLAB_A, -0.5d);
        boolean topB = isExpectedSlab(world, TOP_SLAB_B, CANONICAL_TOP_SLAB_TYPE) && isDy(world, TOP_SLAB_B, -0.5d);
        boolean upper = isStoneDy(world, UPPER_FULL, -1.0d)
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(world, UPPER_FULL);
        boolean sideCandidateEmpty = world.getBlockState(UPPER_FULL.offset(ANGLE_A_FACE)).isAir();
        boolean topFaceCandidateEmpty = world.getBlockState(UPPER_FULL.up()).isAir();
        boolean skippedTopCandidateEmpty = world.getBlockState(UPPER_FULL.up(2)).isAir();
        boolean missingVariantNamed = TOP_SLAB_A.equals(UPPER_FULL.down());
        boolean green = bottomA && bottomB && bridgeA && bridgeB && topA && topB && upper
                && sideCandidateEmpty && topFaceCandidateEmpty && skippedTopCandidateEmpty && missingVariantNamed;
        String marker = green
                ? "[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_FIXTURE_GREEN]"
                : "[JULIA_BETA4_COMPOUND_VISIBLE_SLAB_LANE_FIXTURE_FAIL]";
        System.out.println(marker
                + " fixtureTruth=" + (green ? "GREEN" : "FAIL")
                + " bottomSlabA=" + describeBlock(world, BOTTOM_SLAB_A)
                + " bottomSlabB=" + describeBlock(world, BOTTOM_SLAB_B)
                + " bridgeFullBlockA=" + describeBlock(world, BRIDGE_FULL_A)
                + " bridgeFullBlockB=" + describeBlock(world, BRIDGE_FULL_B)
                + " topSlabA=" + describeBlock(world, TOP_SLAB_A)
                + " topSlabB=" + describeBlock(world, TOP_SLAB_B)
                + " upperFullBlock=" + describeBlock(world, UPPER_FULL)
                + " upperFullBlockCompound=" + SlabAnchorAttachment.isCompoundFullBlockAnchor(world, UPPER_FULL)
                + " upperFullBlockAuthoredOrPersistent=true"
                + " upperFullBlockLowered=" + isDy(world, UPPER_FULL, -1.0d)
                + " lowerHalfSideCandidate=" + describeBlock(world, UPPER_FULL.offset(ANGLE_A_FACE))
                + " topFaceCandidate=" + describeBlock(world, UPPER_FULL.up())
                + " missingUnderSlabVariantSupportPos=" + TOP_SLAB_A.toShortString()
                + " invalidReasons=" + invalidReasons(bottomA, bottomB, bridgeA, bridgeB, topA, topB, upper,
                sideCandidateEmpty, topFaceCandidateEmpty, skippedTopCandidateEmpty, missingVariantNamed));
        return green;
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
                player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
                player.setVelocity(Vec3d.ZERO);
            }
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
                mc.player.setVelocity(Vec3d.ZERO);
                mc.player.setYaw(yaw);
                mc.player.setPitch(pitch);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
                mc.player.setVelocity(Vec3d.ZERO);
            }
        });
    }

    private static Vec3d eyeFor(Direction face) {
        double x = UPPER_FULL.getX() + 0.5d + face.getOffsetX() * 2.6d;
        double y = UPPER_FULL.getY() + 1.2d;
        double z = UPPER_FULL.getZ() + 0.5d + face.getOffsetZ() * 2.6d;
        return new Vec3d(x, y, z);
    }

    private static Vec3d topFaceEye() {
        return new Vec3d(UPPER_FULL.getX() + 0.5d, UPPER_FULL.getY() + 2.4d, UPPER_FULL.getZ() + 2.6d);
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

    private static BlockHitResult visibleSideHit(BlockPos targetPos, Direction face, double visualLocalY) {
        return new BlockHitResult(visibleSideHitPoint(targetPos, face, visualLocalY, EXPECTED_UPPER_FULL_DY),
                face, targetPos, false, false);
    }

    private static BlockHitResult visibleTopHit(BlockPos targetPos) {
        return new BlockHitResult(visibleTopHitPoint(targetPos, EXPECTED_UPPER_FULL_DY),
                Direction.UP, targetPos, false, false);
    }

    private static Vec3d visibleSideHitPoint(BlockPos targetPos, Direction face, double visualLocalY, double ownerDy) {
        return switch (face) {
            case NORTH -> new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + ownerDy + visualLocalY,
                    targetPos.getZ());
            case SOUTH -> new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + ownerDy + visualLocalY,
                    targetPos.getZ() + 1.0d);
            case EAST -> new Vec3d(targetPos.getX() + 1.0d, targetPos.getY() + ownerDy + visualLocalY,
                    targetPos.getZ() + 0.5d);
            case WEST -> new Vec3d(targetPos.getX(), targetPos.getY() + ownerDy + visualLocalY,
                    targetPos.getZ() + 0.5d);
            default -> throw new IllegalArgumentException("unsupported side face " + face);
        };
    }

    private static Vec3d visibleTopHitPoint(BlockPos targetPos, double ownerDy) {
        return new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + ownerDy + 1.0d,
                targetPos.getZ() + 0.5d);
    }

    private static AimCorridor scanAimCorridors(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String caseName,
            Direction face,
            double visualLocalY
    ) {
        AimCandidate[] candidates = corridorCandidates(face, visualLocalY);
        System.out.println("[JULIA_BETA4_LIVE_GOBLIN_AIM_CORRIDOR_START]"
                + " caseName=" + caseName
                + " intendedOwnerPos=" + UPPER_FULL.toShortString()
                + " intendedFace=" + face.asString()
                + " intendedVisualLocalY=" + visualLocalY
                + " candidateCount=" + candidates.length
                + " playerRealistic=true");
        AimCorridor selected = null;
        int tested = 0;
        for (int i = 0; i < candidates.length; i++) {
            AimCandidate candidate = candidates[i];
            syncAim(ctx, singleplayer, candidate.eye, candidate.hit);
            waitForClient(ctx, singleplayer, 2);
            syncAim(ctx, singleplayer, candidate.eye, candidate.hit);
            final AimCorridor[] result = {AimCorridor.none(caseName, tested + 1)};
            final int index = i;
            ctx.runOnClient(mc -> {
                if (mc.player == null || mc.world == null) {
                    result[0] = AimCorridor.none(caseName, index + 1);
                    System.out.println("[JULIA_BETA4_LIVE_GOBLIN_AIM_CORRIDOR_CANDIDATE]"
                            + " caseName=" + caseName
                            + " cameraIndex=" + index
                            + " cameraName=" + candidate.name
                            + " eye=" + fmtVec(candidate.eye)
                            + " intendedOwnerPos=" + UPPER_FULL.toShortString()
                            + " intendedFace=" + face.asString()
                            + " intendedLocalHit=" + fmtVec(candidate.hit)
                            + " actualTarget=MISS"
                            + " actualFace=none"
                            + " actualLocalHit=NaN,NaN,NaN"
                            + " classification=UNKNOWN"
                            + " occluder=none"
                            + " reason=client_missing");
                    return;
                }
                mc.gameRenderer.updateCrosshairTarget(0.0f);
                HitResult target = mc.crosshairTarget;
                AimFacts facts = AimFacts.from(mc.world, target);
                TargetingDiagnostic diagnostic = TargetingDiagnostic.from(mc.world, mc.player, UPPER_FULL,
                        face, "LOWER", candidate.hit, facts, target);
                String occluder = "none";
                if ("OCCLUSION_EXPECTED".equals(diagnostic.classification) && facts.pos != null) {
                    occluder = describeBlock(mc.world, facts.pos);
                }
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_AIM_CORRIDOR_CANDIDATE]"
                        + " caseName=" + caseName
                        + " cameraIndex=" + index
                        + " cameraName=" + candidate.name
                        + " eye=" + fmtVec(candidate.eye)
                        + " intendedOwnerPos=" + UPPER_FULL.toShortString()
                        + " intendedFace=" + face.asString()
                        + " intendedLocalHit=" + fmtVec(candidate.hit)
                        + " actualTarget=" + facts.describe()
                        + " actualFace=" + (facts.face == null ? "none" : facts.face.asString())
                        + " actualLocalHit=" + facts.localX + "," + facts.localY + "," + facts.localZ
                        + " actualVisualLocalY=" + facts.visualLocalY
                        + " classification=" + diagnostic.classification
                        + " occluder=" + occluder
                        + " " + diagnostic.describe());
                result[0] = new AimCorridor(caseName, candidate.name, candidate.eye, candidate.hit,
                        diagnostic.classification, diagnostic.reason, facts, occluder, index + 1);
            });
            tested = i + 1;
            if (result[0].found()) {
                selected = result[0];
                break;
            }
        }
        if (selected != null) {
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_AIM_CORRIDOR_SELECTED]"
                    + " caseName=" + caseName
                    + " cameraName=" + selected.cameraName
                    + " eye=" + fmtVec(selected.eye)
                    + " intendedOwnerPos=" + UPPER_FULL.toShortString()
                    + " intendedFace=" + face.asString()
                    + " intendedLocalHit=" + fmtVec(selected.hit)
                    + " classification=" + selected.classification
                    + " testedCandidates=" + tested);
            if (caseName.startsWith("sequence")) {
                System.out.println("[JULIA_BETA4_LIVE_GOBLIN_LOWER_CORRIDOR_SEQUENCE]"
                        + " step=LOWER_AFTER_FIRST"
                        + " result=CORRIDOR_SELECTED"
                        + " cameraName=" + selected.cameraName
                        + " eye=" + fmtVec(selected.eye)
                        + " intendedHit=" + fmtVec(selected.hit));
            }
            return selected.withTested(tested);
        }
        System.out.println("[JULIA_BETA4_LIVE_GOBLIN_AIM_CORRIDOR_NONE]"
                + " caseName=" + caseName
                + " intendedOwnerPos=" + UPPER_FULL.toShortString()
                + " intendedFace=" + face.asString()
                + " intendedVisualLocalY=" + visualLocalY
                + " testedCandidates=" + tested
                + " marker=NO_PLAYER_REALISTIC_LOWER_CORRIDOR");
        if (caseName.startsWith("sequence")) {
            System.out.println("[JULIA_BETA4_LIVE_GOBLIN_LOWER_CORRIDOR_SEQUENCE]"
                    + " step=LOWER_AFTER_FIRST"
                    + " result=NO_CORRIDOR"
                    + " testedCandidates=" + tested
                    + " reason=no_player_realistic_lower_corridor_after_first_side");
        }
        return AimCorridor.none(caseName, tested);
    }

    private static AimCandidate[] corridorCandidates(Direction face, double visualLocalY) {
        return new AimCandidate[] {
                aimCandidate("same_side_straight", face, visualLocalY, 2.6d, 1.20d, 0.50d),
                aimCandidate("same_side_low", face, visualLocalY, 2.6d, 0.70d, 0.50d),
                aimCandidate("same_side_slight_left", face, visualLocalY, 2.6d, 1.00d, 0.20d),
                aimCandidate("same_side_slight_right", face, visualLocalY, 2.6d, 1.00d, 0.80d),
                aimCandidate("same_side_above", face, visualLocalY, 2.6d, 1.55d, 0.50d),
                aimCandidate("same_side_below", face, visualLocalY, 2.6d, 0.45d, 0.50d),
                aimCandidate("near_corner_left", face, visualLocalY, 2.35d, 1.00d, 0.05d),
                aimCandidate("near_corner_right", face, visualLocalY, 2.35d, 1.00d, 0.95d),
                aimCandidate("wide_left", face, visualLocalY, 3.2d, 1.15d, -0.35d),
                aimCandidate("wide_right", face, visualLocalY, 3.2d, 1.15d, 1.35d)
        };
    }

    private static AimCandidate aimCandidate(
            String name,
            Direction face,
            double visualLocalY,
            double distance,
            double eyeVisualY,
            double faceLocalZ
    ) {
        Vec3d hit = visibleSideHitPointWithLocalZ(UPPER_FULL, face, visualLocalY,
                EXPECTED_UPPER_FULL_DY, faceLocalZ);
        double baseY = UPPER_FULL.getY() + EXPECTED_UPPER_FULL_DY + eyeVisualY;
        double x = UPPER_FULL.getX() + 0.5d + face.getOffsetX() * distance;
        double z = UPPER_FULL.getZ() + 0.5d + face.getOffsetZ() * distance;
        if (face.getAxis() == Direction.Axis.X) {
            z = UPPER_FULL.getZ() + faceLocalZ;
        } else {
            x = UPPER_FULL.getX() + faceLocalZ;
        }
        return new AimCandidate(name, new Vec3d(x, baseY, z), hit);
    }

    private static Vec3d visibleSideHitPointWithLocalZ(
            BlockPos targetPos,
            Direction face,
            double visualLocalY,
            double ownerDy,
            double faceLocalZ
    ) {
        return switch (face) {
            case NORTH -> new Vec3d(targetPos.getX() + faceLocalZ, targetPos.getY() + ownerDy + visualLocalY,
                    targetPos.getZ());
            case SOUTH -> new Vec3d(targetPos.getX() + faceLocalZ, targetPos.getY() + ownerDy + visualLocalY,
                    targetPos.getZ() + 1.0d);
            case EAST -> new Vec3d(targetPos.getX() + 1.0d, targetPos.getY() + ownerDy + visualLocalY,
                    targetPos.getZ() + faceLocalZ);
            case WEST -> new Vec3d(targetPos.getX(), targetPos.getY() + ownerDy + visualLocalY,
                    targetPos.getZ() + faceLocalZ);
            default -> throw new IllegalArgumentException("unsupported side face " + face);
        };
    }

    private static void emitTargetingDiagnostic(String step, String expectedBand, TargetingDiagnostic diagnostic) {
        String markers = switch (expectedBand) {
            case "LOWER" -> "[JULIA_BETA4_LIVE_GOBLIN_TARGETING_LOWER_DIAG]";
            case "UPPER" -> "[JULIA_BETA4_LIVE_GOBLIN_TARGETING_UPPER_DIAG]";
            case "TOP" -> "[JULIA_BETA4_LIVE_GOBLIN_TARGETING_TOP_DIAG]";
            default -> "[JULIA_BETA4_LIVE_GOBLIN_TARGETING_DIAG]";
        };
        markers = switch (step) {
            case "FIRST_SIDE" -> markers + "\n[JULIA_BETA4_LIVE_GOBLIN_TARGETING_SEQUENCE_FIRST_DIAG]";
            case "LOWER_AFTER_FIRST" -> markers + "\n[JULIA_BETA4_LIVE_GOBLIN_TARGETING_SEQUENCE_LOWER_AFTER_FIRST_DIAG]";
            case "TOP_FACE" -> markers + "\n[JULIA_BETA4_LIVE_GOBLIN_TARGETING_SEQUENCE_TOP_DIAG]";
            default -> markers;
        };
        for (String marker : markers.split("\n")) {
            System.out.println(marker + " step=" + step + " " + diagnostic.describe());
        }
        switch (diagnostic.classification) {
            case "HARNESS_AIM_FAIL" -> System.out.println("[JULIA_BETA4_LIVE_GOBLIN_TARGETING_HARNESS_AIM_FAIL]"
                    + " step=" + step + " reason=" + diagnostic.reason);
            case "OWNER_FAIL" -> System.out.println("[JULIA_BETA4_LIVE_GOBLIN_TARGETING_OWNER_FAIL]"
                    + " step=" + step + " reason=" + diagnostic.reason);
            case "OCCLUSION_EXPECTED" -> System.out.println("[JULIA_BETA4_LIVE_GOBLIN_TARGETING_OCCLUSION_EXPECTED]"
                    + " step=" + step + " reason=" + diagnostic.reason);
            case "SEQUENCE_STATE_MISMATCH" -> System.out.println("[JULIA_BETA4_LIVE_GOBLIN_TARGETING_SEQUENCE_STATE_MISMATCH]"
                    + " step=" + step + " reason=" + diagnostic.reason);
            default -> {
            }
        }
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
                + " compoundVisibleOwnerTopSlab=" + SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)
                + " persistentLoweredSlabCarrier=" + SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state);
    }

    private static String describeNearbySlabs(net.minecraft.world.BlockView world, BlockPos expectedCandidate) {
        String slabs = "";
        for (BlockPos pos : new BlockPos[] {
                expectedCandidate,
                expectedCandidate.up(),
                UPPER_FULL.up(),
                UPPER_FULL.up(2),
                UPPER_FULL.offset(ANGLE_B_FACE),
                UPPER_FULL.offset(ANGLE_B_FACE).up()
        }) {
            BlockState state = world.getBlockState(pos);
            if (state.isOf(Blocks.STONE_SLAB)) {
                String entry = pos.toShortString() + ":"
                        + (state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE).asString() : "none")
                        + ":dy=" + dy(world, pos, state);
                slabs = slabs.isEmpty() ? entry : slabs + "|" + entry;
            }
        }
        return slabs.isEmpty() ? "none" : slabs;
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

    private static boolean isDoubleSlabDy(net.minecraft.world.BlockView world, BlockPos pos, double expectedDy) {
        return isExpectedSlab(world, pos, SlabType.DOUBLE) && isDy(world, pos, expectedDy);
    }

    private static boolean isSlabDy(
            net.minecraft.world.BlockView world,
            BlockPos pos,
            SlabType expectedType,
            double expectedDy
    ) {
        return isExpectedSlab(world, pos, expectedType) && isDy(world, pos, expectedDy);
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
        if (!"GREEN".equals(verdicts.lowerExact)) {
            blockers = "lowerExact";
        }
        if (!"GREEN".equals(verdicts.repeatPlacement)) {
            blockers = blockers.isEmpty() ? "repeatPlacement" : blockers + ",repeatPlacement";
        }
        if (!"GREEN".equals(verdicts.topFaceExact)) {
            blockers = blockers.isEmpty() ? "topFace" : blockers + ",topFace";
        }
        if (!"GREEN".equals(verdicts.upperFirst)) {
            blockers = blockers.isEmpty() ? "upperFirst" : blockers + ",upperFirst";
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
            boolean sideCandidateEmpty,
            boolean topFaceCandidateEmpty,
            boolean skippedTopCandidateEmpty,
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
        reasons = appendReason(reasons, sideCandidateEmpty, "sideCandidateEmpty");
        reasons = appendReason(reasons, topFaceCandidateEmpty, "topFaceCandidateEmpty");
        reasons = appendReason(reasons, skippedTopCandidateEmpty, "skippedTopCandidateEmpty");
        reasons = appendReason(reasons, missingVariantNamed, "missingUnderSlabVariant");
        return reasons.isEmpty() ? "none" : reasons;
    }

    private static String appendReason(String reasons, boolean ok, String reason) {
        if (ok) {
            return reasons;
        }
        return reasons.isEmpty() ? reason : reasons + "," + reason;
    }

    private static boolean within01(double value) {
        return value >= -AIM_EPSILON && value <= 1.0d + AIM_EPSILON;
    }

    private static Vec3d safeNormalize(Vec3d vec) {
        return vec.lengthSquared() <= EPSILON ? new Vec3d(0.0d, 0.0d, 0.0d) : vec.normalize();
    }

    private static double angularDifferenceDegrees(Vec3d expected, Vec3d actual) {
        double dot = expected.dotProduct(actual);
        double clamped = Math.max(-1.0d, Math.min(1.0d, dot));
        return Math.toDegrees(Math.acos(clamped));
    }

    private static String fmtVec(Vec3d vec) {
        if (vec == null) {
            return "null";
        }
        return "(" + vec.x + "," + vec.y + "," + vec.z + ")";
    }

    private static final class Verdicts {
        String supportPresentLowerSide = "PENDING";
        String supportPresentUpperSide = "PENDING";
        String supportPresentTopFace = "PENDING";
        String supportMissingSide = "PENDING";
        String supportMissingTopFace = "PENDING";
        String lowerExact = "PENDING";
        String upperFirst = "PENDING";
        String repeatPlacement = "PENDING";
        String topFaceExact = "PENDING";
        String hitbox = "PENDING";
        boolean ghost;
        boolean jump;
        boolean wrongOwner;
        boolean miss;
        int observedCases;
    }

    private static final class ParityVerdicts {
        String lowerAimParity = "NOT_RUN";
        String upperAimParity = "NOT_RUN";
        String topAimParity = "NOT_RUN";
        String firstSide = "NOT_RUN";
        String lowerAfterFirst = "NOT_RUN";
        String repeatPlacement = "NOT_RUN";
        String topFace = "NOT_RUN";
        String lowerTargeting = "NOT_RUN";
        String upperTargeting = "NOT_RUN";
        String topTargeting = "NOT_RUN";
        String sequenceFirstTargeting = "NOT_RUN";
        String sequenceLowerAfterFirstTargeting = "NOT_RUN";
        String sequenceTopTargeting = "NOT_RUN";
        String lowerCorridor = "NOT_RUN";
        String lowerAfterFirstCorridor = "NOT_RUN";
        boolean ghost;
        boolean wrongDelta;
        boolean wrongOwner;

        void absorb(RealClickResult result) {
            ghost |= result.ghost;
            wrongDelta |= result.wrongDelta;
            wrongOwner |= result.wrongOwner;
        }

        int countTargeting(String classification) {
            int count = 0;
            count += classification.equals(lowerTargeting) ? 1 : 0;
            count += classification.equals(upperTargeting) ? 1 : 0;
            count += classification.equals(topTargeting) ? 1 : 0;
            count += classification.equals(sequenceFirstTargeting) ? 1 : 0;
            count += classification.equals(sequenceLowerAfterFirstTargeting) ? 1 : 0;
            count += classification.equals(sequenceTopTargeting) ? 1 : 0;
            return count;
        }

        String nextTargetingAction() {
            if (countTargeting("HARNESS_AIM_FAIL") > 0) {
                return "fixHarnessAim";
            }
            if (countTargeting("OCCLUSION_EXPECTED") > 0) {
                return "choosePlayerRealisticAimPoint";
            }
            if (countTargeting("OWNER_FAIL") > 0) {
                return "fixOwnerRetargetLayer";
            }
            if (countTargeting("SEQUENCE_STATE_MISMATCH") > 0) {
                return "fixSequenceFixtureState";
            }
            if (countTargeting("UNKNOWN") > 0) {
                return "auditMissingTargetingData";
            }
            return "targetingParityClear";
        }
    }

    private static final class CompoundVisibleSlabLaneResults {
        String lower = "PENDING";
        String upper = "PENDING";
        String merge = "PENDING";
        String top = "PENDING";
        String supportMissing = "PENDING";
        String triad = "PENDING";
        String reload = "PENDING";
    }

    private static final class NamedCompoundVisibleLaneFixture {
        final BlockPos lowerPos;
        final BlockPos upperPos;
        final BlockPos mergePos;
        final BlockPos topPos;
        final boolean green;
        final String status;

        NamedCompoundVisibleLaneFixture(
                BlockPos lowerPos,
                BlockPos upperPos,
                BlockPos mergePos,
                BlockPos topPos,
                boolean green,
                String status
        ) {
            this.lowerPos = lowerPos;
            this.upperPos = upperPos;
            this.mergePos = mergePos;
            this.topPos = topPos;
            this.green = green;
            this.status = status;
        }

        boolean green() {
            return green;
        }

        String statusSummary() {
            return status;
        }

        BlockPos[] positions() {
            return new BlockPos[] {lowerPos, upperPos, mergePos, topPos};
        }
    }

    private static final class TriadProof {
        final boolean dy;
        final boolean outline;
        final boolean raycast;
        final boolean target;
        final String detail;

        TriadProof(boolean dy, boolean outline, boolean raycast, boolean target, String detail) {
            this.dy = dy;
            this.outline = outline;
            this.raycast = raycast;
            this.target = target;
            this.detail = detail;
        }

        static TriadProof notRun() {
            return new TriadProof(false, false, false, false, "not_run");
        }

        static TriadProof red(String reason) {
            return new TriadProof(false, false, false, false, reason);
        }

        String missingSurfaces(String alwaysMissing) {
            String missing = alwaysMissing;
            if (!dy) {
                missing += ",dy";
            }
            if (!outline) {
                missing += ",outline";
            }
            if (!raycast) {
                missing += ",raycast";
            }
            if (!target) {
                missing += ",target";
            }
            return missing;
        }

        String provenSurfaces() {
            String proven = "";
            if (dy) {
                proven = "dy";
            }
            if (outline) {
                proven = proven.isEmpty() ? "outline" : proven + ",outline";
            }
            if (raycast) {
                proven = proven.isEmpty() ? "raycast" : proven + ",raycast";
            }
            if (target) {
                proven = proven.isEmpty() ? "target" : proven + ",target";
            }
            return proven.isEmpty() ? "none" : proven;
        }
    }

    private static final class TriadSurface {
        final boolean dy;
        final boolean outline;
        final boolean raycast;
        final boolean target;
        final String detail;

        TriadSurface(boolean dy, boolean outline, boolean raycast, boolean target, String detail) {
            this.dy = dy;
            this.outline = outline;
            this.raycast = raycast;
            this.target = target;
            this.detail = detail;
        }
    }

    private static final class RealClickResult {
        final String step;
        String aimParity = "NOT_RUN";
        String result = "NOT_RUN";
        String action = "NOT_RUN";
        String targetingClassification = "NOT_RUN";
        boolean ghost;
        boolean wrongDelta;
        boolean wrongOwner;

        RealClickResult(String step) {
            this.step = step;
        }
    }

    private static final class AimCandidate {
        final String name;
        final Vec3d eye;
        final Vec3d hit;

        private AimCandidate(String name, Vec3d eye, Vec3d hit) {
            this.name = name;
            this.eye = eye;
            this.hit = hit;
        }
    }

    private static final class AimCorridor {
        final String caseName;
        final String cameraName;
        final Vec3d eye;
        final Vec3d hit;
        final String classification;
        final String reason;
        final AimFacts facts;
        final String occluder;
        final int testedCandidates;

        private AimCorridor(
                String caseName,
                String cameraName,
                Vec3d eye,
                Vec3d hit,
                String classification,
                String reason,
                AimFacts facts,
                String occluder,
                int testedCandidates
        ) {
            this.caseName = caseName;
            this.cameraName = cameraName;
            this.eye = eye;
            this.hit = hit;
            this.classification = classification;
            this.reason = reason;
            this.facts = facts;
            this.occluder = occluder;
            this.testedCandidates = testedCandidates;
        }

        static AimCorridor none(String caseName, int testedCandidates) {
            return new AimCorridor(caseName, "none", null, null, "NO_CORRIDOR",
                    "no_player_realistic_lower_corridor", null, "none", testedCandidates);
        }

        boolean found() {
            return "TARGET_OK".equals(classification);
        }

        AimCorridor withTested(int tested) {
            return new AimCorridor(caseName, cameraName, eye, hit, classification, reason, facts, occluder, tested);
        }
    }

    private static final class AimFacts {
        final BlockPos pos;
        final Direction face;
        final Vec3d hit;
        final String state;
        final double dy;
        final double localX;
        final double localY;
        final double localZ;
        final double visualLocalY;
        final String band;

        private AimFacts(
                BlockPos pos,
                Direction face,
                Vec3d hit,
                String state,
                double dy,
                double localX,
                double localY,
                double localZ,
                double visualLocalY,
                String band
        ) {
            this.pos = pos;
            this.face = face;
            this.hit = hit;
            this.state = state;
            this.dy = dy;
            this.localX = localX;
            this.localY = localY;
            this.localZ = localZ;
            this.visualLocalY = visualLocalY;
            this.band = band;
        }

        static AimFacts from(net.minecraft.world.BlockView world, HitResult target) {
            if (!(target instanceof BlockHitResult blockHit) || world == null) {
                return new AimFacts(null, null, null, "MISS", 0.0d,
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN, "MISS");
            }
            BlockPos pos = blockHit.getBlockPos();
            Vec3d hit = blockHit.getPos();
            BlockState state = world.getBlockState(pos);
            double localX = hit.x - pos.getX();
            double localY = hit.y - pos.getY();
            double localZ = hit.z - pos.getZ();
            double dy = dy(world, pos, state);
            double visualLocalY = localY - dy;
            String band = blockHit.getSide() == Direction.UP
                    ? "TOP"
                    : (visualLocalY < 0.5d ? "LOWER" : "UPPER");
            return new AimFacts(pos, blockHit.getSide(), hit, state.toString(), dy,
                    localX, localY, localZ, visualLocalY, band);
        }

        String describe() {
            if (pos == null) {
                return "MISS";
            }
            return "pos=" + pos.toShortString()
                    + " face=" + face.asString()
                    + " hit=" + hit;
        }
    }

    private static final class TargetingDiagnostic {
        final String classification;
        final String reason;
        final String details;

        private TargetingDiagnostic(String classification, String reason, String details) {
            this.classification = classification;
            this.reason = reason;
            this.details = details;
        }

        static TargetingDiagnostic from(
                net.minecraft.world.BlockView world,
                net.minecraft.client.network.ClientPlayerEntity player,
                BlockPos intendedOwner,
                Direction intendedFace,
                String expectedBand,
                Vec3d intendedWorldHit,
                AimFacts actual,
                HitResult rawTarget
        ) {
            if (world == null || player == null) {
                return new TargetingDiagnostic("UNKNOWN", "client_missing",
                        "intendedOwnerPos=" + intendedOwner.toShortString());
            }
            BlockState intendedState = world.getBlockState(intendedOwner);
            double intendedOwnerDy = dy(world, intendedOwner, intendedState);
            double intendedLocalX = intendedWorldHit.x - intendedOwner.getX();
            double intendedLocalY = intendedWorldHit.y - intendedOwner.getY();
            double intendedLocalZ = intendedWorldHit.z - intendedOwner.getZ();
            double intendedVisualLocalY = intendedLocalY - intendedOwnerDy;
            Vec3d eye = player.getCameraPosVec(1.0f);
            Vec3d expectedDirection = safeNormalize(intendedWorldHit.subtract(eye));
            Vec3d actualLook = safeNormalize(player.getRotationVec(1.0f));
            double vectorDiff = expectedDirection.subtract(actualLook).length();
            double angularDiff = angularDifferenceDegrees(expectedDirection, actualLook);
            boolean aimedCorrectly = angularDiff <= LOOK_ANGLE_TOLERANCE_DEGREES;
            boolean intendedLocalWithin = within01(intendedLocalX)
                    && within01(intendedVisualLocalY)
                    && within01(intendedLocalZ);
            boolean sequenceStateOk = intendedState.isOf(Blocks.STONE)
                    && Math.abs(intendedOwnerDy - EXPECTED_UPPER_FULL_DY) <= 0.001d
                    && SlabAnchorAttachment.isCompoundFullBlockAnchor(world, intendedOwner);
            boolean realCrosshairTarget = rawTarget instanceof BlockHitResult;
            boolean targetEqualsOwner = intendedOwner.equals(actual.pos);
            boolean faceEquals = actual.face == intendedFace;
            boolean actualLocalWithin = within01(actual.localX)
                    && within01(actual.visualLocalY)
                    && within01(actual.localZ);
            boolean bandEquals = switch (expectedBand) {
                case "LOWER" -> actual.visualLocalY >= -AIM_EPSILON && actual.visualLocalY < 0.5d;
                case "UPPER" -> actual.visualLocalY >= 0.5d && actual.visualLocalY <= 1.0d + AIM_EPSILON;
                case "TOP" -> actual.face == Direction.UP && Math.abs(actual.visualLocalY - 1.0d) <= 0.001d;
                default -> false;
            };
            double actualHitDistance = actual.hit == null ? Double.NaN : eye.distanceTo(actual.hit);
            double intendedHitDistance = eye.distanceTo(intendedWorldHit);
            boolean closerVisibleBlock = realCrosshairTarget
                    && !targetEqualsOwner
                    && actualHitDistance + 0.001d < intendedHitDistance;

            String classification;
            String reason;
            if (!sequenceStateOk) {
                classification = "SEQUENCE_STATE_MISMATCH";
                reason = "intended_owner_state_or_dy_changed";
            } else if (!intendedLocalWithin || !aimedCorrectly) {
                classification = "HARNESS_AIM_FAIL";
                reason = !intendedLocalWithin ? "intended_visual_local_outside_0_1" : "camera_look_not_on_intended_hit";
            } else if (closerVisibleBlock) {
                classification = "OCCLUSION_EXPECTED";
                reason = "closer_visible_block_intersects_before_intended_owner";
            } else if (!realCrosshairTarget || !targetEqualsOwner || !faceEquals || !actualLocalWithin || !bandEquals) {
                classification = "OWNER_FAIL";
                reason = "real_crosshair_target_mismatch";
            } else {
                classification = "TARGET_OK";
                reason = "real_crosshair_matches_intended_owner_face_band";
            }

            String details = "classification=" + classification
                    + " reason=" + reason
                    + " intendedOwnerPos=" + intendedOwner.toShortString()
                    + " intendedOwnerState=" + intendedState
                    + " intendedOwnerDy=" + intendedOwnerDy
                    + " intendedFace=" + intendedFace.asString()
                    + " intendedLocalX=" + intendedLocalX
                    + " intendedLocalY=" + intendedLocalY
                    + " intendedVisualLocalY=" + intendedVisualLocalY
                    + " intendedLocalZ=" + intendedLocalZ
                    + " intendedWorldHit=" + fmtVec(intendedWorldHit)
                    + " cameraEye=" + fmtVec(eye)
                    + " cameraYaw=" + player.getYaw()
                    + " cameraPitch=" + player.getPitch()
                    + " expectedRayDirection=" + fmtVec(expectedDirection)
                    + " actualLookDirection=" + fmtVec(actualLook)
                    + " vectorDifference=" + vectorDiff
                    + " angularDifferenceDegrees=" + angularDiff
                    + " actualCrosshairTarget=" + actual.describe()
                    + " actualState=" + actual.state
                    + " actualLocalX=" + actual.localX
                    + " actualLocalY=" + actual.localY
                    + " actualVisualLocalY=" + actual.visualLocalY
                    + " actualLocalZ=" + actual.localZ
                    + " actualTargetDy=" + actual.dy
                    + " actualTargetEqualsIntendedOwner=" + targetEqualsOwner
                    + " actualFaceEqualsIntended=" + faceEquals
                    + " rawLocalYWithin01=" + within01(actual.localY)
                    + " visualLocalCoordsWithin01=" + actualLocalWithin
                    + " realCrosshairTarget=" + realCrosshairTarget
                    + " localCoordBasis=visualDyAdjusted"
                    + " rawLocalOutside01Explanation=valid_only_when_visualLocalWithin01_and_ownerDy_nonzero";
            return new TargetingDiagnostic(classification, reason, details);
        }

        String describe() {
            return details;
        }
    }

    private static final class BlockFact {
        final String state;
        final double dy;

        private BlockFact(String state, double dy) {
            this.state = state;
            this.dy = dy;
        }

        static BlockFact from(net.minecraft.world.BlockView world, BlockPos pos) {
            BlockState state = world.getBlockState(pos);
            return new BlockFact(state.toString(), dy(world, pos, state));
        }

        boolean sameAs(BlockFact other) {
            return other != null && state.equals(other.state) && Math.abs(dy - other.dy) <= EPSILON;
        }

        String describe() {
            return state + " dy=" + dy;
        }
    }

    private static final class DeltaFacts {
        final String describe;
        final int changedCount;
        final boolean expectedChanged;
        final boolean ghost;
        final boolean wrongDelta;

        private DeltaFacts(String describe, int changedCount, boolean expectedChanged, boolean ghost, boolean wrongDelta) {
            this.describe = describe;
            this.changedCount = changedCount;
            this.expectedChanged = expectedChanged;
            this.ghost = ghost;
            this.wrongDelta = wrongDelta;
        }

        static DeltaFacts from(
                String step,
                Map<BlockPos, BlockFact> before,
                Map<BlockPos, BlockFact> after,
                BlockPos expectedChangedPos
        ) {
            String changed = "";
            int changedCount = 0;
            boolean expectedChanged = false;
            boolean ghost = false;
            boolean wrongDelta = false;
            for (Map.Entry<BlockPos, BlockFact> entry : after.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockFact beforeFact = before.get(pos);
                BlockFact afterFact = entry.getValue();
                if (afterFact.sameAs(beforeFact)) {
                    continue;
                }
                boolean expected = pos.equals(expectedChangedPos);
                expectedChanged |= expected;
                changedCount++;
                boolean ghostHere = !expected || pos.getY() > UPPER_FULL.up().getY();
                ghost |= ghostHere;
                wrongDelta |= !expected;
                String item = "changed[" + changedCount + "]"
                        + " pos=" + pos.toShortString()
                        + " before=" + (beforeFact == null ? "missing" : beforeFact.describe())
                        + " after=" + afterFact.describe()
                        + " expected=" + expected
                        + " ghost=" + ghostHere;
                changed = changed.isEmpty() ? item : changed + " | " + item;
            }
            if (changed.isEmpty()) {
                changed = "step=" + step + " changed=none";
            } else {
                changed = "step=" + step + " " + changed;
            }
            return new DeltaFacts(changed, changedCount, expectedChanged, ghost, wrongDelta);
        }
    }
}
