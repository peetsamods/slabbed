package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RED proof for slab placement against a visible lowered side slab.
 *
 * <p>Fixture: bottom slab support, lowered full block, and a BOTTOM side slab
 * beside the full block. The side slab is visually lowered to 0.5S space
 * (dy=-0.5), a placement lane vanilla would not normally choose.
 *
 * <p>Action: hold a slab, aim at the existing lowered side slab's visible east
 * face, and click once. The expected Slabbed intent is a neighboring slab in
 * the same lowered visual lane. Routing to native slab space at the intended
 * cell, or to the cell above, leaves the visible lowered face/window empty.
 */
public final class SlabbedLabVanillaIllegalSlabToSlabPlacementClientGameTest implements FabricClientGameTest {
    private static final BlockPos FIXTURE_ORIGIN = new BlockPos(0, 200, 0);
    private static final double EPSILON = 1.0e-6;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (TerrainSlabsProofFocus.skipUnrelatedClientGameTest(getClass().getSimpleName())) {
            return;
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            Path screenshotDir = SlabbedLabClientGameTest.resolveClientGameTestScreenshotDir();
            Set<String> knownScreenshotFiles = SlabbedLabClientGameTest.listScreenshotFileNames(screenshotDir);
            singleplayer.getClientWorld().waitForChunksRender();
            runProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles);
        }
    }

    private static void runProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles
    ) {
        final String testId = "vanilla_illegal_slab_to_slab_placement";
        final BlockPos supportPos = FIXTURE_ORIGIN;
        final BlockPos fullPos = supportPos.up();
        final BlockPos targetSlabPos = fullPos.east();
        final BlockPos intendedPos = targetSlabPos.east();
        final BlockPos nativeAbovePos = intendedPos.up();
        final Vec3d eye = new Vec3d(targetSlabPos.getX() + 1.75d, targetSlabPos.getY() - 0.25d,
                targetSlabPos.getZ() + 0.5d);
        final Vec3d target = new Vec3d(targetSlabPos.getX() + 1.0d, targetSlabPos.getY() - 0.25d,
                targetSlabPos.getZ() + 0.5d);

        setupFixture(singleplayer, testId, supportPos, fullPos, targetSlabPos, intendedPos, nativeAbovePos);
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        Frame before = capture(ctx, "before", supportPos, fullPos, targetSlabPos, intendedPos, nativeAbovePos);
        AtomicReference<BlockState> targetStateBeforeRef = new AtomicReference<>(Blocks.AIR.getDefaultState());
        AtomicReference<Boolean> targetIsSolidRef = new AtomicReference<>(false);
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                return;
            }
            BlockState targetState = mc.world.getBlockState(targetSlabPos);
            targetStateBeforeRef.set(targetState);
            targetIsSolidRef.set(targetState.isSolidBlock(mc.world, targetSlabPos));
        });
        BlockState targetStateBefore = targetStateBeforeRef.get();
        Direction incomingFace = Direction.EAST;
        boolean targetIsSolid = targetIsSolidRef.get();
        boolean targetIsLoweredSlab = targetStateBefore.getBlock() instanceof SlabBlock
                && Math.abs(before.targetSlab().dy() + 0.5d) <= EPSILON;
        BlockPos vanillaPlacePos = targetSlabPos.offset(incomingFace);
        double vanillaLocalHitYAtPlacePos = target.y - vanillaPlacePos.getY();
        String predictedVanillaSlabType = vanillaLocalHitYAtPlacePos > 0.5d
                ? SlabType.TOP.asString()
                : SlabType.BOTTOM.asString();
        String placementIntentDecision = targetIsSolid
                ? "would_continue_to_lowered_full_block_checks"
                : targetIsLoweredSlab
                ? "remapped_lowered_slab_target"
                : "not_remapped_target_not_solid";
        String simulatedLoweredSlabRemapDecision = targetIsLoweredSlab
                ? "would_remap_lowered_slab_target"
                : "not_applicable";

        ctx.takeScreenshot(testId + "_before");
        HitSnapshot hit = aimAndCaptureHit(ctx, eye, target);
        PlacementContextDiagnostics placementDiagnostics = capturePlacementDiagnostics(
                ctx, targetSlabPos, incomingFace, target, intendedPos, nativeAbovePos);
        syncServerPlayer(singleplayer, eye, target);
        ClickSnapshot click = clickCurrentTarget(ctx, supportPos, fullPos, targetSlabPos, intendedPos, nativeAbovePos);
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();
        Frame after = capture(ctx, "after", supportPos, fullPos, targetSlabPos, intendedPos, nativeAbovePos);
        ctx.takeScreenshot(testId + "_after");

        Placement placement = Placement.from(before, click.immediate(), intendedPos, nativeAbovePos, targetSlabPos);
        boolean crosshairOnTargetSlab = hit.matches(targetSlabPos);
        boolean intendedLoweredLanePresent = after.intended().isSlab()
                && Math.abs(after.intended().dy() - before.targetSlab().dy()) <= EPSILON;
        boolean routedAboveOrNative = placement.pos().equals(nativeAbovePos)
                || (placement.pos().equals(intendedPos)
                && Math.abs(placement.dy() - before.targetSlab().dy()) > EPSILON);
        boolean ghostFaceIndicator = crosshairOnTargetSlab && !intendedLoweredLanePresent;

        String verdict;
        boolean pass;
        if (!crosshairOnTargetSlab) {
            verdict = "BLOCKED: crosshair did not resolve to lowered target slab";
            pass = false;
        } else if (intendedLoweredLanePresent) {
            verdict = "GREEN: slab-to-slab click placed in intended lowered adjacent lane";
            pass = true;
        } else {
            verdict = "RED: crosshair hit lowered slab face but placement did not occupy intended lowered adjacent lane";
            pass = false;
        }

        List<SlabbedLabClientGameTest.NoteField> fields = new ArrayList<>();
        fields.add(new SlabbedLabClientGameTest.NoteField("proofId", testId));
        fields.add(new SlabbedLabClientGameTest.NoteField("supportPos", supportPos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("fullPos", fullPos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("targetSlabPos", targetSlabPos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("intendedPlacementPos", intendedPos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("nativeAbovePlacementPos", nativeAbovePos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("heldItem", "minecraft:stone_slab"));
        fields.add(new SlabbedLabClientGameTest.NoteField("aimEye", fmtVec(eye)));
        fields.add(new SlabbedLabClientGameTest.NoteField("aimTarget", fmtVec(target)));
        fields.add(new SlabbedLabClientGameTest.NoteField("crosshairBeforeClick", hit.describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("incomingTargetPos", targetSlabPos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("incomingTargetFace", incomingFace.asString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("incomingTargetHitVec", fmtVec(target)));
        fields.add(new SlabbedLabClientGameTest.NoteField("incomingTargetIsSolid", Boolean.toString(targetIsSolid)));
        fields.add(new SlabbedLabClientGameTest.NoteField("incomingTargetIsLoweredSlab",
                Boolean.toString(targetIsLoweredSlab)));
        fields.add(new SlabbedLabClientGameTest.NoteField("placementIntentMixinDecision", placementIntentDecision));
        fields.add(new SlabbedLabClientGameTest.NoteField("simulatedLoweredSlabRemapDecision",
                simulatedLoweredSlabRemapDecision));
        fields.add(new SlabbedLabClientGameTest.NoteField("vanillaItemPlacementContextPlacePos",
                vanillaPlacePos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("vanillaLocalHitYAtPlacePos",
                String.format("%.4f", vanillaLocalHitYAtPlacePos)));
        fields.add(new SlabbedLabClientGameTest.NoteField("predictedVanillaSlabType", predictedVanillaSlabType));
        fields.add(new SlabbedLabClientGameTest.NoteField("nativeContext", placementDiagnostics.nativeContext().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("simulatedRemapContext",
                placementDiagnostics.simulatedRemapContext().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("simulatedRemapFailGate",
                placementDiagnostics.simulatedRemapContext().failGate()));
        fields.add(new SlabbedLabClientGameTest.NoteField("clickResult", click.result()));
        fields.add(new SlabbedLabClientGameTest.NoteField("targetSlabBefore", before.targetSlab().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("targetSlabImmediatelyAfterClick",
                click.immediate().targetSlab().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("targetSlabAfterSettle", after.targetSlab().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("fullBlockBefore", before.fullBlock().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("intendedBefore", before.intended().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("intendedImmediatelyAfterClick",
                click.immediate().intended().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("intendedAfterSettle", after.intended().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("nativeAboveBefore", before.nativeAbove().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("nativeAboveImmediatelyAfterClick",
                click.immediate().nativeAbove().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("nativeAboveAfterSettle", after.nativeAbove().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("actualPlacementPos", placement.posLabel()));
        fields.add(new SlabbedLabClientGameTest.NoteField("actualPlacedSlabType", placement.slabType()));
        fields.add(new SlabbedLabClientGameTest.NoteField("actualPlacedSlabDy", Double.toString(placement.dy())));
        fields.add(new SlabbedLabClientGameTest.NoteField("actualRoutedAboveOrNativeLegal", Boolean.toString(routedAboveOrNative)));
        fields.add(new SlabbedLabClientGameTest.NoteField("intendedLoweredLanePresent",
                Boolean.toString(intendedLoweredLanePresent)));
        fields.add(new SlabbedLabClientGameTest.NoteField("ghostFaceIndicator", Boolean.toString(ghostFaceIndicator)));
        fields.add(new SlabbedLabClientGameTest.NoteField("diagnosis",
                "proof isolates placement intent after crosshair ownership resolves to the visible lowered slab"));
        fields.add(new SlabbedLabClientGameTest.NoteField("verdict", verdict));

        SlabbedLabClientGameTest.writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "vanilla-illegal lowered slab-to-slab placement proof",
                "A slab placed against a visible lowered side slab should occupy the adjacent lowered visual lane, not native/above slab space.",
                testId + "_before",
                testId + "_after",
                fields,
                pass);

        if (!pass) {
            throw new RuntimeException("[" + testId + "] " + verdict);
        }
    }

    private static void setupFixture(
            TestSingleplayerContext singleplayer,
            String testId,
            BlockPos supportPos,
            BlockPos fullPos,
            BlockPos targetSlabPos,
            BlockPos intendedPos,
            BlockPos nativeAbovePos
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (BlockPos pos : List.of(
                    supportPos,
                    fullPos,
                    targetSlabPos,
                    intendedPos,
                    nativeAbovePos,
                    intendedPos.down(),
                    nativeAbovePos.up())) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
            world.setBlockState(supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, fullPos, world.getBlockState(fullPos));
            world.setBlockState(targetSlabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer server player list empty for " + testId);
            }
            server.getPlayerManager().getPlayerList().get(0)
                    .setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
        });
    }

    private static HitSnapshot aimAndCaptureHit(ClientGameTestContext ctx, Vec3d eye, Vec3d target) {
        AtomicReference<HitSnapshot> out = new AtomicReference<>(HitSnapshot.miss("not_recorded"));
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                out.set(HitSnapshot.miss("null_world_or_player"));
                return;
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            positionPlayer(mc, eye, target);
            Vec3d end = eye.add(target.subtract(eye).normalize().multiply(6.0d));
            HitResult vanilla = mc.world.raycast(new RaycastContext(
                    eye,
                    end,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            out.set(HitSnapshot.from(mc.crosshairTarget, vanilla));
        });
        return out.get();
    }

    private static ClickSnapshot clickCurrentTarget(
            ClientGameTestContext ctx,
            BlockPos supportPos,
            BlockPos fullPos,
            BlockPos targetSlabPos,
            BlockPos intendedPos,
            BlockPos nativeAbovePos
    ) {
        AtomicReference<ClickSnapshot> out = new AtomicReference<>(
                new ClickSnapshot("not_run", Frame.empty("immediate_after_click")));
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                out.set(new ClickSnapshot(
                        "BLOCKED: client interaction path unavailable",
                        Frame.empty("immediate_after_click")));
                return;
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            HitResult target = mc.crosshairTarget;
            if (!(target instanceof BlockHitResult hit) || target.getType() != HitResult.Type.BLOCK) {
                out.set(new ClickSnapshot(
                        "MISS_NO_CLICK target=" + describeHit(target),
                        captureNow(mc, "immediate_after_click", supportPos, fullPos, targetSlabPos,
                                intendedPos, nativeAbovePos)));
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            out.set(new ClickSnapshot(
                    result.toString(),
                    captureNow(mc, "immediate_after_click", supportPos, fullPos, targetSlabPos,
                            intendedPos, nativeAbovePos)));
        });
        return out.get();
    }

    private static PlacementContextDiagnostics capturePlacementDiagnostics(
            ClientGameTestContext ctx,
            BlockPos targetSlabPos,
            Direction face,
            Vec3d hit,
            BlockPos intendedPos,
            BlockPos nativeAbovePos
    ) {
        AtomicReference<PlacementContextDiagnostics> out = new AtomicReference<>(
                PlacementContextDiagnostics.empty("not_recorded"));
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                out.set(PlacementContextDiagnostics.empty("null_world_or_player"));
                return;
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));

            BlockHitResult nativeHit = new BlockHitResult(hit, face, targetSlabPos, false, false);
            ItemPlacementContext nativeContext = new ItemPlacementContext(
                    new ItemUsageContext(mc.player, Hand.MAIN_HAND, nativeHit));

            double loweredVisualUpperBoundary = targetSlabPos.getY() + 0.5d;
            boolean exactLoweredVisualBoundary = Math.abs(hit.y - loweredVisualUpperBoundary) <= EPSILON;
            boolean upperHalfIntent = hit.y >= targetSlabPos.getY() && !exactLoweredVisualBoundary;
            double remappedY = upperHalfIntent ? targetSlabPos.getY() + 0.501d : targetSlabPos.getY() + 0.499d;
            Vec3d remappedHitPos = new Vec3d(hit.x, remappedY, hit.z);
            BlockHitResult remappedHit = new BlockHitResult(remappedHitPos, face, targetSlabPos, false, false);
            ItemPlacementContext remappedContext = new ItemPlacementContext(
                    new ItemUsageContext(mc.player, Hand.MAIN_HAND, remappedHit));

            out.set(new PlacementContextDiagnostics(
                    ContextDiagnostics.from(mc, "native", nativeHit, nativeContext, intendedPos, nativeAbovePos),
                    ContextDiagnostics.from(mc, "simulated_remap", remappedHit, remappedContext,
                            intendedPos, nativeAbovePos)));
        });
        return out.get();
    }

    private static ContextDiagnostics contextDiagnosticsUnavailable(String reason) {
        return new ContextDiagnostics(
                reason, "none", "none", "none", "none", "none", "none",
                false, false, "none", "none", true, false, false,
                "none", "none", "none", "none", "none", false, false,
                "none", "none", false, false, false, "unavailable");
    }

    private static void positionPlayer(MinecraftClient mc, Vec3d eye, Vec3d target) {
        if (mc.player == null) {
            return;
        }
        Vec3d delta = target.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - mc.player.getStandingEyeHeight();
        mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        mc.player.raycast(6.0d, 0.0f, false);
    }

    private static void syncServerPlayer(TestSingleplayerContext singleplayer, Vec3d eye, Vec3d target) {
        singleplayer.getServer().runOnServer(server -> {
            List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
            if (players.isEmpty()) {
                return;
            }
            ServerPlayerEntity player = players.getFirst();
            Vec3d delta = target.subtract(eye);
            double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
            double feetY = eye.y - player.getStandingEyeHeight();
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        });
    }

    private static Frame capture(
            ClientGameTestContext ctx,
            String label,
            BlockPos supportPos,
            BlockPos fullPos,
            BlockPos targetSlabPos,
            BlockPos intendedPos,
            BlockPos nativeAbovePos
    ) {
        AtomicReference<Frame> out = new AtomicReference<>(Frame.empty(label));
        ctx.runOnClient(mc -> out.set(new Frame(
                label,
                sample(mc.world, supportPos),
                sample(mc.world, fullPos),
                sample(mc.world, targetSlabPos),
                sample(mc.world, intendedPos),
                sample(mc.world, nativeAbovePos))));
        return out.get();
    }

    private static Frame captureNow(
            MinecraftClient mc,
            String label,
            BlockPos supportPos,
            BlockPos fullPos,
            BlockPos targetSlabPos,
            BlockPos intendedPos,
            BlockPos nativeAbovePos
    ) {
        return new Frame(
                label,
                sample(mc.world, supportPos),
                sample(mc.world, fullPos),
                sample(mc.world, targetSlabPos),
                sample(mc.world, intendedPos),
                sample(mc.world, nativeAbovePos));
    }

    private static Sample sample(net.minecraft.world.BlockView world, BlockPos pos) {
        if (world == null) {
            return new Sample(pos, "null_world", 0.0d, false, 0.0d, 0.0d, false, "none", false);
        }
        BlockState state = world.getBlockState(pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.absent());
        boolean empty = outline.isEmpty();
        double minY = pos.getY() + (empty ? 0.0d : outline.getBoundingBox().minY);
        double maxY = pos.getY() + (empty ? 0.0d : outline.getBoundingBox().maxY);
        String slabType = state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE).toString() : "none";
        return new Sample(pos, state.toString(), dy, anchored, minY, maxY,
                state.isOf(Blocks.STONE_SLAB), slabType, !state.isAir());
    }

    private static String describeHit(HitResult hit) {
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
            return hit == null ? "null" : hit.getType().toString();
        }
        return "BLOCK blockPos=" + bh.getBlockPos().toShortString()
                + " face=" + bh.getSide().asString()
                + " hit=" + fmtVec(bh.getPos());
    }

    private static String fmtVec(Vec3d vec) {
        return String.format("%.4f,%.4f,%.4f", vec.x, vec.y, vec.z);
    }

    private record Frame(String label, Sample support, Sample fullBlock, Sample targetSlab,
                         Sample intended, Sample nativeAbove) {
        static Frame empty(String label) {
            Sample sample = new Sample(BlockPos.ORIGIN, "none", 0.0d, false,
                    0.0d, 0.0d, false, "none", false);
            return new Frame(label, sample, sample, sample, sample, sample);
        }
    }

    private record ClickSnapshot(String result, Frame immediate) {
    }

    private record PlacementContextDiagnostics(ContextDiagnostics nativeContext,
                                               ContextDiagnostics simulatedRemapContext) {
        static PlacementContextDiagnostics empty(String reason) {
            ContextDiagnostics unavailable = contextDiagnosticsUnavailable(reason);
            return new PlacementContextDiagnostics(unavailable, unavailable);
        }
    }

    private record ContextDiagnostics(
            String label,
            String hitTargetPos,
            String hitFace,
            String hitVec,
            String contextBlockPos,
            String contextSide,
            String contextHitPos,
            boolean contextCanPlace,
            boolean contextCanReplaceExisting,
            String stateAtContextPos,
            String rawSlabPlacementState,
            boolean rawSlabPlacementStateNull,
            boolean rawStateCanPlaceAt,
            boolean worldCanPlaceRawState,
            String intendedStateBefore,
            String nativeAboveStateBefore,
            String playerBox,
            String intendedNativeSlabBox,
            String intendedLoweredSlabBox,
            boolean playerIntersectsNativeSlabBox,
            boolean playerIntersectsLoweredSlabBox,
            String nativeCollisionShapeBox,
            String loweredCollisionShapeBox,
            boolean nativeCandidateEntityClear,
            boolean loweredCandidateEntityClear,
            boolean loweredCandidateEntityClearIgnoringOnlyPlayer,
            String failGate
    ) {
        static ContextDiagnostics from(
                MinecraftClient mc,
                String label,
                BlockHitResult hit,
                ItemPlacementContext context,
                BlockPos intendedPos,
                BlockPos nativeAbovePos
        ) {
            BlockState rawState = Blocks.STONE_SLAB.getPlacementState(context);
            boolean rawStateNull = rawState == null;
            boolean rawStateCanPlaceAt = rawState != null && rawState.canPlaceAt(mc.world, context.getBlockPos());
            boolean worldCanPlaceRawState = rawState != null
                    && mc.world.canPlace(rawState, context.getBlockPos(), ShapeContext.ofPlacement(mc.player));
            boolean contextCanPlace = context.canPlace();
            VoxelShape nativeShape = rawState == null
                    ? VoxelShapes.empty()
                    : rawState.getCollisionShape(mc.world, context.getBlockPos(), ShapeContext.ofPlacement(mc.player))
                    .offset(context.getBlockPos());
            VoxelShape loweredShape = rawState == null
                    ? VoxelShapes.empty()
                    : rawState.getCollisionShape(mc.world, context.getBlockPos(), ShapeContext.ofPlacement(mc.player))
                    .offset(0.0d, -0.5d, 0.0d)
                    .offset(context.getBlockPos());
            Box nativeBox = slabBox(context.getBlockPos(), rawState, 0.0d);
            Box loweredBox = slabBox(context.getBlockPos(), rawState, -0.5d);
            Box playerBox = mc.player == null ? null : mc.player.getBoundingBox();
            boolean playerIntersectsNative = playerBox != null && nativeBox != null && playerBox.intersects(nativeBox);
            boolean playerIntersectsLowered = playerBox != null && loweredBox != null && playerBox.intersects(loweredBox);
            boolean nativeEntityClear = mc.world.doesNotIntersectEntities(null, nativeShape);
            boolean loweredEntityClear = mc.world.doesNotIntersectEntities(null, loweredShape);
            boolean loweredEntityClearIgnoringPlayer = mc.player != null
                    && mc.world.doesNotIntersectEntities(mc.player, loweredShape);
            boolean loweredShapeIntersectsPlayer = mc.player != null
                    && VoxelShapes.matchesAnywhere(
                    loweredShape,
                    VoxelShapes.cuboid(mc.player.getBoundingBox()),
                    BooleanBiFunction.AND);
            String failGate;
            if (!contextCanPlace) {
                failGate = "context_canPlace_false";
            } else if (rawStateNull) {
                failGate = "raw_slab_placement_state_null";
            } else if (!rawStateCanPlaceAt) {
                failGate = "block_item_canPlace_false_state_canPlaceAt_false";
            } else if (!worldCanPlaceRawState) {
                failGate = "block_item_canPlace_false_world_canPlace_false";
            } else {
                failGate = "would_pass_BlockItem_pre_place_gates";
            }
            return new ContextDiagnostics(
                    label,
                    hit.getBlockPos().toShortString(),
                    hit.getSide().asString(),
                    fmtVec(hit.getPos()),
                    context.getBlockPos().toShortString(),
                    context.getSide().asString(),
                    fmtVec(context.getHitPos()),
                    contextCanPlace,
                    context.canReplaceExisting(),
                    mc.world.getBlockState(context.getBlockPos()).toString(),
                    rawStateNull ? "null" : rawState.toString(),
                    rawStateNull,
                    rawStateCanPlaceAt,
                    worldCanPlaceRawState,
                    mc.world.getBlockState(intendedPos).toString(),
                    mc.world.getBlockState(nativeAbovePos).toString(),
                    boxLabel(playerBox),
                    boxLabel(nativeBox),
                    boxLabel(loweredBox),
                    playerIntersectsNative,
                    playerIntersectsLowered,
                    shapeBoxLabel(nativeShape),
                    shapeBoxLabel(loweredShape),
                    nativeEntityClear,
                    loweredEntityClear,
                    loweredEntityClearIgnoringPlayer && loweredShapeIntersectsPlayer,
                    failGate);
        }

        private static Box slabBox(BlockPos pos, BlockState state, double dy) {
            if (state == null || !state.contains(SlabBlock.TYPE)) {
                return null;
            }
            SlabType type = state.get(SlabBlock.TYPE);
            double minY = switch (type) {
                case TOP -> 0.5d;
                case BOTTOM -> 0.0d;
                case DOUBLE -> 0.0d;
            };
            double maxY = switch (type) {
                case TOP -> 1.0d;
                case BOTTOM -> 0.5d;
                case DOUBLE -> 1.0d;
            };
            return new Box(
                    pos.getX(),
                    pos.getY() + minY + dy,
                    pos.getZ(),
                    pos.getX() + 1.0d,
                    pos.getY() + maxY + dy,
                    pos.getZ() + 1.0d);
        }

        private static String boxLabel(Box box) {
            if (box == null) {
                return "none";
            }
            return String.format("%.3f,%.3f,%.3f..%.3f,%.3f,%.3f",
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }

        private static String shapeBoxLabel(VoxelShape shape) {
            if (shape == null || shape.isEmpty()) {
                return "none";
            }
            return boxLabel(shape.getBoundingBox());
        }

        String describe() {
            return "label=" + label
                    + " hitTarget=" + hitTargetPos
                    + " hitFace=" + hitFace
                    + " hitVec=" + hitVec
                    + " contextBlockPos=" + contextBlockPos
                    + " contextSide=" + contextSide
                    + " contextHitPos=" + contextHitPos
                    + " canPlace=" + contextCanPlace
                    + " canReplaceExisting=" + contextCanReplaceExisting
                    + " stateAtContextPos=" + stateAtContextPos
                    + " rawSlabPlacementState=" + rawSlabPlacementState
                    + " rawStateNull=" + rawSlabPlacementStateNull
                    + " rawStateCanPlaceAt=" + rawStateCanPlaceAt
                    + " worldCanPlaceRawState=" + worldCanPlaceRawState
                    + " intendedStateBefore=" + intendedStateBefore
                    + " nativeAboveStateBefore=" + nativeAboveStateBefore
                    + " playerBox=" + playerBox
                    + " intendedNativeSlabBox=" + intendedNativeSlabBox
                    + " intendedLoweredSlabBox=" + intendedLoweredSlabBox
                    + " playerIntersectsNativeSlabBox=" + playerIntersectsNativeSlabBox
                    + " playerIntersectsLoweredSlabBox=" + playerIntersectsLoweredSlabBox
                    + " nativeCollisionShapeBox=" + nativeCollisionShapeBox
                    + " loweredCollisionShapeBox=" + loweredCollisionShapeBox
                    + " nativeCandidateEntityClear=" + nativeCandidateEntityClear
                    + " loweredCandidateEntityClear=" + loweredCandidateEntityClear
                    + " loweredCandidateEntityClearIgnoringOnlyPlayer="
                    + loweredCandidateEntityClearIgnoringOnlyPlayer
                    + " failGate=" + failGate;
        }
    }

    private record Sample(BlockPos pos, String state, double dy, boolean anchored,
                          double minY, double maxY, boolean isSlab,
                          String slabType, boolean nonAir) {
        String describe() {
            return "pos=" + pos.toShortString()
                    + " dy=" + dy
                    + " anchored=" + anchored
                    + " visualY=" + String.format("%.3f..%.3f", minY, maxY)
                    + " slabType=" + slabType
                    + " state=" + state;
        }
    }

    private record HitSnapshot(BlockPos owner, String actual, String vanilla) {
        static HitSnapshot from(HitResult actualHit, HitResult vanillaHit) {
            BlockPos owner = actualHit instanceof BlockHitResult bh && actualHit.getType() == HitResult.Type.BLOCK
                    ? bh.getBlockPos()
                    : null;
            return new HitSnapshot(owner, describeHit(actualHit), describeHit(vanillaHit));
        }

        static HitSnapshot miss(String reason) {
            return new HitSnapshot(null, reason, reason);
        }

        boolean matches(BlockPos pos) {
            return owner != null && owner.equals(pos);
        }

        String describe() {
            return "actual=" + actual + " vanilla=" + vanilla;
        }
    }

    private record Placement(BlockPos pos, String posLabel, String slabType, double dy) {
        static Placement from(Frame before, Frame after, BlockPos intendedPos,
                              BlockPos nativeAbovePos, BlockPos targetSlabPos) {
            if (!before.intended().isSlab() && after.intended().isSlab()) {
                return of(after.intended());
            }
            if (!before.nativeAbove().isSlab() && after.nativeAbove().isSlab()) {
                return of(after.nativeAbove());
            }
            if (after.targetSlab().isSlab()
                    && !after.targetSlab().slabType().equals(before.targetSlab().slabType())) {
                return of(after.targetSlab());
            }
            if (after.intended().isSlab()) {
                return of(after.intended());
            }
            if (after.nativeAbove().isSlab()) {
                return of(after.nativeAbove());
            }
            if (after.targetSlab().isSlab() && after.targetSlab().pos().equals(targetSlabPos)) {
                return new Placement(BlockPos.ORIGIN, "none", "none", 0.0d);
            }
            return new Placement(BlockPos.ORIGIN, "none", "none", 0.0d);
        }

        private static Placement of(Sample sample) {
            return new Placement(sample.pos(), sample.pos().toShortString(), sample.slabType(), sample.dy());
        }
    }
}
