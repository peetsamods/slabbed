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
                eyeFor(expectedFace == Direction.UP ? ANGLE_A_FACE : expectedFace), aimMarker,
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
