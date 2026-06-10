package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.client.runtime.ModelDyTranslateTraceBridge;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.PaleMossCarpetBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class SlabbedLabModelDySingleOwnerAuditClientGameTest implements FabricClientGameTest {
    private static final String PROOF = "MODEL_DY_SINGLE_OWNER_AUDIT";
    private static final double EPSILON = 1.0e-6d;
    private static final int MAX_RENDER_ATTEMPTS = 4;
    private static final int TICKS_PER_ATTEMPT = 6;

    private static final BlockPos ANCHORED_SUPPORT = new BlockPos(4, 200, 4);
    private static final BlockPos ANCHORED_FULL = ANCHORED_SUPPORT.up();

    private static final BlockPos BOTTOM_SUPPORT = new BlockPos(10, 200, 4);
    private static final BlockPos BOTTOM_FULL = BOTTOM_SUPPORT.up();
    private static final BlockPos BOTTOM_CARRIER = BOTTOM_FULL.up();

    private static final BlockPos DOUBLE_SUPPORT = new BlockPos(16, 200, 4);
    private static final BlockPos DOUBLE_FULL = DOUBLE_SUPPORT.up();
    private static final BlockPos DOUBLE_CARRIER = DOUBLE_FULL.east();

    private static final BlockPos CARPET_SLAB = new BlockPos(22, 200, 4);
    private static final BlockPos CARPET_POS = CARPET_SLAB.up();

    private enum CaseClassification {
        SINGLE_OWNER,
        ZERO_OWNER,
        DOUBLE_OWNER,
        PROOF_GAP
    }

    private record CaseResult(
            String name,
            BlockPos pos,
            String state,
            double expectedDy,
            String eye,
            boolean rerenderRequested,
            int ticksWaited,
            boolean renderSubmitted,
            boolean offsetApplied,
            boolean mixinObserved,
            double offsetDy,
            boolean mixinApplied,
            double mixinDy,
            double totalAppliedModelDy,
            String proofGapReason,
            CaseClassification classification
    ) {
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runAudit(ctx, singleplayer);
        }
    }

    private static void runAudit(ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        seedAuditWorld(singleplayer);
        waitForSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();

        List<CaseResult> results = new ArrayList<>();
        results.add(runCase(ctx, singleplayer, "anchored_lowered_ordinary_full_block", ANCHORED_FULL));
        results.add(runCase(ctx, singleplayer, "persistent_lowered_bottom_slab_carrier", BOTTOM_CARRIER));
        results.add(runCase(ctx, singleplayer, "lowered_double_slab_carrier", DOUBLE_CARRIER));
        results.add(runCase(ctx, singleplayer, "carpet_on_bottom_slab", CARPET_POS));

        boolean carpetInvariantIntact = isCarpetInvariantIntact(singleplayer);
        String terminal = classifyTerminal(results, carpetInvariantIntact);
        System.out.println("[MODEL_DY_SINGLE_OWNER_AUDIT] proof=" + PROOF
                + " terminalClassification=" + terminal
                + " carpetInvariantIntact=" + carpetInvariantIntact);

        if (!"GREEN_SINGLE_OWNER".equals(terminal)) {
            throw new RuntimeException(PROOF + " terminalClassification=" + terminal);
        }
    }

    private static void seedAuditWorld(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            clearArea(world, ANCHORED_SUPPORT, 22);
            seedAnchoredLoweredFull(world, ANCHORED_SUPPORT, ANCHORED_FULL);
            seedLoweredBottomCarrier(world, BOTTOM_SUPPORT, BOTTOM_FULL, BOTTOM_CARRIER);
            seedLoweredDoubleCarrier(world, DOUBLE_SUPPORT, DOUBLE_FULL, DOUBLE_CARRIER);
            seedCarpetOnBottomSlab(world, CARPET_SLAB, CARPET_POS);
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
    }

    private static CaseResult runCase(ClientGameTestContext ctx, TestSingleplayerContext singleplayer, String caseName, BlockPos pos) {
        final BlockState[] stateBox = new BlockState[1];
        final double[] expectedDyBox = new double[1];
        final String[] eyeTextBox = new String[]{"none"};
        final int[] ticksWaitedBox = new int[]{0};
        final boolean[] rerenderRequestedBox = new boolean[]{false};
        final boolean[] renderSubmittedBox = new boolean[]{false};
        final String[] proofGapReasonBox = new String[]{"not_submitted"};

        for (int attempt = 0; attempt < MAX_RENDER_ATTEMPTS && !renderSubmittedBox[0]; attempt++) {
            final int attemptIndex = attempt;
            ctx.runOnClient(mc -> {
                if (mc.world == null || mc.worldRenderer == null || mc.player == null || mc.gameRenderer == null) {
                    proofGapReasonBox[0] = "client_world_or_player_or_renderer_missing";
                    return;
                }

                BlockState state = mc.world.getBlockState(pos);
                stateBox[0] = state;
                expectedDyBox[0] = ClientDy.dyFor(mc.world, pos, state);

                Vec3d eye = buildEyeForAttempt(pos, attemptIndex);
                Vec3d target = buildTargetForState(pos, state);
                eyeTextBox[0] = fmtVec(eye);
                positionPlayer(mc, eye, target);

                OffsetBlockStateModel.resetModelDyOwnerSample(pos);
                ModelDyTranslateTraceBridge.reset(pos);

                mc.worldRenderer.scheduleBlockRenders(
                        pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
                rerenderRequestedBox[0] = true;
                proofGapReasonBox[0] = "attempt_" + (attemptIndex + 1) + "_scheduled";
            });

            for (int i = 0; i < TICKS_PER_ATTEMPT; i++) {
                ctx.runOnClient(mc -> {
                    if (mc.world == null || mc.worldRenderer == null || mc.player == null || mc.gameRenderer == null) {
                        return;
                    }
                    BlockState state = mc.world.getBlockState(pos);
                    Vec3d eye = buildEyeForAttempt(pos, attemptIndex);
                    Vec3d target = buildTargetForState(pos, state);
                    positionPlayer(mc, eye, target);
                    mc.gameRenderer.updateCrosshairTarget(0.0f);
                    HitResult hit = mc.crosshairTarget;
                    if (hit instanceof BlockHitResult bhr
                            && hit.getType() == HitResult.Type.BLOCK
                            && bhr.getBlockPos().equals(pos)) {
                        proofGapReasonBox[0] = "crosshair_confirmed_attempt_" + (attemptIndex + 1);
                    }
                    mc.worldRenderer.scheduleBlockRenders(
                            pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
                });
                ctx.waitTick();
                ticksWaitedBox[0]++;
            }

            singleplayer.getClientWorld().waitForChunksRender();
            final OffsetBlockStateModel.ModelDyOwnerSample[] attemptOffsetTrace = new OffsetBlockStateModel.ModelDyOwnerSample[1];
            final ModelDyTranslateTraceBridge.Trace[] attemptMixinTrace = new ModelDyTranslateTraceBridge.Trace[1];
            ctx.runOnClient(mc -> {
                attemptOffsetTrace[0] = OffsetBlockStateModel.snapshotModelDyOwnerSample();
                attemptMixinTrace[0] = ModelDyTranslateTraceBridge.snapshot();
            });

            boolean offsetSeen = attemptOffsetTrace[0] != null && attemptOffsetTrace[0].seen();
            boolean mixinSeen = attemptMixinTrace[0] != null && attemptMixinTrace[0].seen();
            renderSubmittedBox[0] = offsetSeen || mixinSeen;
            if (!renderSubmittedBox[0]) {
                proofGapReasonBox[0] = "no_trace_seen_attempt_" + (attemptIndex + 1);
            }
        }

        final OffsetBlockStateModel.ModelDyOwnerSample[] offsetTraceBox = new OffsetBlockStateModel.ModelDyOwnerSample[1];
        final ModelDyTranslateTraceBridge.Trace[] mixinTraceBox = new ModelDyTranslateTraceBridge.Trace[1];
        ctx.runOnClient(mc -> {
            offsetTraceBox[0] = OffsetBlockStateModel.snapshotModelDyOwnerSample();
            mixinTraceBox[0] = ModelDyTranslateTraceBridge.snapshot();
        });

        OffsetBlockStateModel.ModelDyOwnerSample offsetTrace = offsetTraceBox[0];
        ModelDyTranslateTraceBridge.Trace mixinTrace = mixinTraceBox[0];
        BlockState state = stateBox[0];
        double expectedDy = expectedDyBox[0];
        boolean offsetApplied = offsetTrace != null && offsetTrace.appliedCalls() > 0;
        boolean mixinObserved = mixinTrace != null && mixinTrace.observedCalls() > 0;
        boolean mixinApplied = mixinTrace != null && mixinTrace.appliedCalls() > 0;
        double offsetDy = offsetTrace == null ? Double.NaN : offsetTrace.totalAppliedDy();
        double mixinDy = mixinTrace == null ? Double.NaN : mixinTrace.totalAppliedDy();
        double totalAppliedModelDy = (offsetTrace == null || mixinTrace == null)
                ? Double.NaN
                : offsetTrace.totalAppliedDy() + mixinTrace.totalAppliedDy();
        CaseClassification classification = classifyCase(
                expectedDy,
                renderSubmittedBox[0],
                offsetTrace,
                mixinTrace,
                offsetApplied,
                mixinApplied);

        System.out.println("[MODEL_DY_SINGLE_OWNER_AUDIT] proof=" + PROOF
                + " case=" + caseName
                + " block=" + (state == null ? "null" : state.getBlock())
                + " state=" + state
                + " pos=" + pos.toShortString()
                + " expectedDy=" + expectedDy
                + " eye=" + eyeTextBox[0]
                + " rerenderRequested=" + rerenderRequestedBox[0]
                + " ticksWaited=" + ticksWaitedBox[0]
                + " renderSubmitted=" + renderSubmittedBox[0]
                + " offsetApplied=" + offsetApplied
                + " offsetDy=" + offsetDy
                + " offsetEmitCalls=" + (offsetTrace == null ? -1 : offsetTrace.emitCalls())
                + " mixinObserved=" + mixinObserved
                + " mixinApplied=" + mixinApplied
                + " mixinDy=" + mixinDy
                + " mixinObservedCalls=" + (mixinTrace == null ? -1 : mixinTrace.observedCalls())
                + " mixinAppliedCalls=" + (mixinTrace == null ? -1 : mixinTrace.appliedCalls())
                + " totalAppliedModelDy=" + totalAppliedModelDy
                + " proofGapReason=" + proofGapReasonBox[0]
                + " classification=" + classification);

        return new CaseResult(
                caseName,
                pos,
                state == null ? "null" : state.toString(),
                expectedDy,
                eyeTextBox[0],
                rerenderRequestedBox[0],
                ticksWaitedBox[0],
                renderSubmittedBox[0],
                offsetApplied,
                mixinObserved,
                offsetDy,
                mixinApplied,
                mixinDy,
                totalAppliedModelDy,
                proofGapReasonBox[0],
                classification
        );
    }

    private static CaseClassification classifyCase(
            double expectedDy,
            boolean renderSubmitted,
            OffsetBlockStateModel.ModelDyOwnerSample offsetTrace,
            ModelDyTranslateTraceBridge.Trace mixinTrace,
            boolean offsetApplied,
            boolean mixinApplied
    ) {
        boolean loweredExpected = Math.abs(expectedDy) > EPSILON;
        if (!loweredExpected) {
            return CaseClassification.PROOF_GAP;
        }

        if (!renderSubmitted) {
            return CaseClassification.PROOF_GAP;
        }

        boolean offsetSeen = offsetTrace != null && offsetTrace.seen();
        boolean mixinSeen = mixinTrace != null && mixinTrace.seen();
        if (!offsetSeen && !mixinSeen) {
            return CaseClassification.PROOF_GAP;
        }

        int owners = (offsetApplied ? 1 : 0) + (mixinApplied ? 1 : 0);
        if (owners == 0) {
            return CaseClassification.ZERO_OWNER;
        }
        if (owners == 1) {
            return CaseClassification.SINGLE_OWNER;
        }
        return CaseClassification.DOUBLE_OWNER;
    }

    private static String classifyTerminal(List<CaseResult> results, boolean carpetInvariantIntact) {
        boolean anyProofGap = false;
        boolean anyZero = false;
        boolean anyDouble = false;

        for (CaseResult result : results) {
            if (result.classification == CaseClassification.PROOF_GAP) {
                anyProofGap = true;
            } else if (result.classification == CaseClassification.ZERO_OWNER) {
                anyZero = true;
            } else if (result.classification == CaseClassification.DOUBLE_OWNER) {
                anyDouble = true;
            }
        }

        if (!carpetInvariantIntact || anyProofGap) {
            return "PROOF_GAP";
        }
        if (anyDouble) {
            return "RED_DOUBLE_OWNER";
        }
        if (anyZero) {
            return "RED_ZERO_OWNER";
        }
        return "GREEN_SINGLE_OWNER";
    }

    private static boolean isCarpetInvariantIntact(TestSingleplayerContext singleplayer) {
        final boolean[] invariantBox = new boolean[]{false};
        singleplayer.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            BlockState carpetState = world.getBlockState(CARPET_POS);
            Block block = carpetState.getBlock();
            boolean isCarpet = block instanceof CarpetBlock || block instanceof PaleMossCarpetBlock;
            boolean onBottomSlab = SlabSupport.hasBottomSlabBelow(world, CARPET_POS);
            invariantBox[0] = isCarpet && onBottomSlab;
        });
        return invariantBox[0];
    }

    private static void seedAnchoredLoweredFull(World world, BlockPos supportPos, BlockPos fullPos) {
        world.setBlockState(supportPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(world, fullPos, world.getBlockState(fullPos));
    }

    private static void seedLoweredBottomCarrier(World world, BlockPos supportPos, BlockPos fullPos, BlockPos carrierPos) {
        seedAnchoredLoweredFull(world, supportPos, fullPos);
        world.setBlockState(carrierPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, carrierPos, world.getBlockState(carrierPos));
        SlabAnchorAttachment.removeAnchor(world, fullPos);
        world.setBlockState(fullPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    private static void seedLoweredDoubleCarrier(World world, BlockPos supportPos, BlockPos fullPos, BlockPos carrierPos) {
        seedAnchoredLoweredFull(world, supportPos, fullPos);
        world.setBlockState(carrierPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, carrierPos, world.getBlockState(carrierPos));
        SlabAnchorAttachment.removeAnchor(world, fullPos);
        world.setBlockState(fullPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    private static void seedCarpetOnBottomSlab(World world, BlockPos slabPos, BlockPos carpetPos) {
        world.setBlockState(slabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        world.setBlockState(carpetPos, Blocks.WHITE_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    private static void clearArea(World world, BlockPos origin, int width) {
        for (int x = -2; x <= width; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    SlabAnchorAttachment.removeAnchor(world, pos);
                    SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, pos);
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    private static void waitForSync(ClientGameTestContext ctx) {
        for (int i = 0; i < 6; i++) {
            ctx.waitTick();
        }
    }

    private static Vec3d buildEyeForAttempt(BlockPos pos, int attempt) {
        return switch (attempt % 4) {
            case 0 -> new Vec3d(pos.getX() + 0.5d, pos.getY() + 1.8d, pos.getZ() + 3.4d);
            case 1 -> new Vec3d(pos.getX() + 3.4d, pos.getY() + 1.8d, pos.getZ() + 0.5d);
            case 2 -> new Vec3d(pos.getX() + 0.5d, pos.getY() + 1.8d, pos.getZ() - 2.4d);
            default -> new Vec3d(pos.getX() - 2.4d, pos.getY() + 1.8d, pos.getZ() + 0.5d);
        };
    }

    private static Vec3d buildTargetForState(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CarpetBlock || state.getBlock() instanceof PaleMossCarpetBlock) {
            return new Vec3d(pos.getX() + 0.5d, pos.getY() + 0.05d, pos.getZ() + 0.5d);
        }
        return new Vec3d(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d);
    }

    private static void positionPlayer(net.minecraft.client.MinecraftClient mc, Vec3d eye, Vec3d target) {
        Vec3d delta = target.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - mc.player.getStandingEyeHeight();
        mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        mc.player.raycast(6.0d, 0.0f, false);
    }

    private static String fmtVec(Vec3d vec) {
        return String.format("%.4f,%.4f,%.4f", vec.x, vec.y, vec.z);
    }
}
