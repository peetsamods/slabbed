package com.slabbed.mixin.client;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.client.runtime.LoweredSideSlabRetargeter;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.RuntimeDiagnostics;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Client-side raycast retarget for lowered owners placed on bottom slabs.
 *
 * <p>When the lowered block's visible lower half extends into {@code pos.down()}'s
 * voxel, vanilla per-voxel DDA raycast hits the slab below before it can
 * consider the offset shape at {@code pos}. After vanilla
 * {@link GameRenderer#updateCrosshairTarget} has resolved the crosshair,
 * we re-test the ray against the owning block above (if it qualifies per
 * the lowered-owner helpers in {@link SlabSupport}) and, if the ray hits
 * its offset shape at an equal or closer distance, we replace
 * {@link MinecraftClient#crosshairTarget} with that result.
 *
 * <p>The shape tested is the block's <em>outline</em> shape, using the
 * camera entity's {@link ShapeContext}. This mirrors vanilla crosshair
 * targeting which uses {@code RaycastContext.ShapeType.OUTLINE}; using
 * the raycast shape instead would silently miss blocks (chests, barrels,
 * signs, etc.) whose {@code getRaycastShape} falls back to empty.
 *
 * <p>This retarget is the single ownership rule; the outline renderer
 * automatically follows because it reads {@code crosshairTarget}.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererCrosshairRetargetMixin {

    @Shadow @Final private MinecraftClient client;
    private static final long BETA4_FINAL_TARGET_TRACE_MIN_INTERVAL_NANOS = 1_000_000_000L;
    private static final String MC1211_LIVE_OUTLINE_TARGET_TRACE_PROPERTY =
            "slabbed.mc1211.liveOutlineTargetTrace";
    private static final String MC1211_TRAPDOOR_SEAM_MP4_RED_PROPERTY =
            "slabbed.mc1211.trapdoorLoweredSeamMp4Red";
    private static final int MC1211_LIVE_OUTLINE_TARGET_TRACE_SUMMARY_INTERVAL = 20;
    private static final double BETA35_VISIBLE_OWNER_SUPPORT_OVERRUN = 0.75d;
    private static String slabbed$beta4FinalTargetTraceLastSignature;
    private static long slabbed$beta4FinalTargetTraceLastLogNanos;
    private static final Set<String> slabbed$mc1211LiveOutlineTargetTraceSignatures = ConcurrentHashMap.newKeySet();
    private static boolean slabbed$mc1211LiveOutlineTargetTraceCanaryLogged;
    private static int slabbed$mc1211LiveOutlineTargetTraceRows;
    private static int slabbed$mc1211LiveOutlineTargetTraceSkipped;
    private static int slabbed$mc1211LiveOutlineTargetTraceTargetMatchesAnchored;
    private static int slabbed$mc1211LiveOutlineTargetTraceTargetAboveAnchored;
    private static int slabbed$mc1211LiveOutlineTargetTraceDifferentOwner;
    private static int slabbed$mc1211LiveOutlineTargetTraceAirOrMiss;
    private static int slabbed$mc1211LiveOutlineTargetTraceNoRecentAnchor;
    private static final Set<String> slabbed$mc1211TrapdoorSeamMp4ManualRedSignatures =
            ConcurrentHashMap.newKeySet();
    private static boolean slabbed$mc1211TrapdoorSeamMp4ManualStartLogged;
    private static int slabbed$mc1211TrapdoorSeamMp4ManualRedRows;
    private static final Set<String> slabbed$manualSideRescueVerdictSignatures =
            ConcurrentHashMap.newKeySet();
    private static boolean slabbed$manualSideRescueVerdictStartLogged;
    private static int slabbed$manualSideRescueVerdictRows;
    private static int slabbed$manualSideRescueVerdictGreenRows;
    private static int slabbed$manualSideRescueVerdictRedRows;
    private static boolean slabbed$beta4LiveRetargetRecorderStartLogged;
    private static boolean slabbed$beta4ReloadJumpRecorderStartLogged;
    private static ClientWorld slabbed$beta4ReloadJumpRecorderWorld;
    private static int slabbed$beta4ReloadJumpRecorderTicksRemaining;
    private static long slabbed$beta4ReloadJumpRecorderLastWorldTick = Long.MIN_VALUE;
    private static boolean slabbed$beta4OutlineRecorderStartLogged;
    private static ClientWorld slabbed$beta4OutlineRecorderWorld;
    private static int slabbed$beta4OutlineRecorderTicksRemaining;
    private static long slabbed$beta4OutlineRecorderLastWorldTick = Long.MIN_VALUE;

    private static final String SEAM_OWNER_ANCHORED_FULL_BLOCK = "ANCHORED_FULL_BLOCK";
    private static final String SEAM_OWNER_VISIBLE_UPPER_LOWERED_SLAB = "VISIBLE_UPPER_LOWERED_SLAB";
    private static final String SEAM_OWNER_ADJACENT_VISIBLE_TARGET = "ADJACENT_VISIBLE_TARGET";
    private static final String SEAM_OWNER_KEEP_INITIAL = "KEEP_INITIAL";
    private static final String SEAM_OWNER_NO_RESCUE = "NO_RESCUE";

    @Inject(method = "updateCrosshairTarget", at = @At("TAIL"))
    private void slabbed$retargetLoweredBlockEntity(float tickProgress, CallbackInfo ci) {
        HitResult slabbed$mc1211LiveOutlineTargetTraceInitialTarget = client.crosshairTarget;
        try {
        slabbed$logBeta4LiveRetargetRecorderStart();
        slabbed$recordBeta4ReloadJumpRecorder(tickProgress);
        slabbed$recordBeta4OutlineRecorder(tickProgress);
        HitResult ht = client.crosshairTarget;
        if (ht == null) {
            return;
        }
        HitResult initialTarget = ht;
        boolean slabHeld = slabbed$isSlabPlacementIntent();
        BlockHitResult objectOwner = slabbed$retargetLoweredObjectShapeOwner(tickProgress, ht);
        if (objectOwner != null) {
            client.crosshairTarget = objectOwner;
            slabbed$traceTargeting(tickProgress, initialTarget, "object-shape-owner-preserve", false);
            return;
        }
        // Slab-held intent guard: when vanilla's initial crosshair already
        // sits on an intended placement target (anchored/lowered full-block
        // UP hit, lowered slab face, or lowered full-block placement-intent
        // face), preserve that face and do not let DDA scans steal ownership.
        if (slabHeld && ht instanceof BlockHitResult initialHit) {
            ClientWorld world = client.world;
            BlockPos pos = initialHit.getBlockPos();
            if (world != null
                    && initialHit.getSide() == Direction.UP
                    && slabbed$isAnchoredLoweredFullBlock(world, pos, world.getBlockState(pos))) {
                BlockHitResult anchoredOwner = slabbed$retargetAnchoredLoweredFullBlock(tickProgress, ht);
                if (slabbed$isDistinctOwner(initialHit, anchoredOwner)) {
                    client.crosshairTarget = anchoredOwner;
                    slabbed$traceTargeting(
                            tickProgress,
                            initialTarget,
                            "scan-anchored-fb-fired-slab-held",
                            false);
                    return;
                }
                BlockHitResult sideOwner = slabbed$traceSlabHeldUpGuardSideOwnerClassification(
                        tickProgress,
                        initialTarget,
                        initialHit);
                BlockHitResult originalSideOwner = sideOwner;
                sideOwner = slabbed$preserveBeta35VisibleOwnerBeforeSupport(tickProgress, sideOwner);
                if (sideOwner != null) {
                    client.crosshairTarget = sideOwner;
                    slabbed$traceTargeting(
                            tickProgress,
                            initialTarget,
                            slabbed$beta35VisibleOwnerDecision(originalSideOwner, sideOwner, "scan-side-slab-fired"),
                            !slabbed$beta35VisibleOwnerPreserved(originalSideOwner, sideOwner));
                    return;
                }
                slabbed$traceTargeting(
                        tickProgress,
                        initialTarget,
                        "scan-skip-slab-held-anchored-lowered-full-block-up",
                        false);
                return;
            }
            if (slabbed$isInitialHitOnLoweredSlabFace(initialHit)) {
                String seamOwner = slabbed$classifyLiveFirstSeamOwner(initialHit);
                if (SEAM_OWNER_VISIBLE_UPPER_LOWERED_SLAB.equals(seamOwner)
                        || SEAM_OWNER_ADJACENT_VISIBLE_TARGET.equals(seamOwner)) {
                    client.crosshairTarget = initialHit;
                    slabbed$traceTargeting(
                            tickProgress,
                            initialTarget,
                            slabbed$seamOwnerDecision(seamOwner),
                            SEAM_OWNER_ADJACENT_VISIBLE_TARGET.equals(seamOwner));
                    return;
                }

                BlockHitResult aboveAngleOwner = slabbed$retargetAboveAngleLowerFrontSlabToAnchoredOwner(
                        tickProgress,
                        initialHit);
                if (aboveAngleOwner != null) {
                    client.crosshairTarget = aboveAngleOwner;
                    slabbed$traceTargeting(tickProgress, initialTarget, "aboveAngleLowerFrontPreserve", false);
                    return;
                }
                BlockHitResult anchoredOwner = slabbed$retargetAnchoredLoweredFullBlock(tickProgress, initialTarget);
                BlockHitResult sideOwner = slabbed$traceSlabHeldMissSideRescueClassification(
                        tickProgress,
                        initialTarget,
                        "lowered-slab-face-preserve");
                BlockHitResult originalSideOwner = sideOwner;
                sideOwner = slabbed$preserveBeta35VisibleOwnerBeforeSupport(tickProgress, sideOwner);
                if (sideOwner != null) {
                    BlockHitResult chosenOwner = slabbed$chooseRescue(tickProgress, anchoredOwner, sideOwner, false);
                    if (chosenOwner == anchoredOwner) {
                        client.crosshairTarget = anchoredOwner;
                        slabbed$traceTargeting(tickProgress, initialTarget, "scan-anchored-fb-fired-slab-held", false);
                        return;
                    }
                    client.crosshairTarget = sideOwner;
                    slabbed$traceTargeting(
                            tickProgress,
                            initialTarget,
                            slabbed$beta35VisibleOwnerDecision(originalSideOwner, sideOwner, "scan-side-slab-fired"),
                            !slabbed$beta35VisibleOwnerPreserved(originalSideOwner, sideOwner));
                    return;
                }
                if (slabbed$isDistinctOwner(initialHit, anchoredOwner)) {
                    client.crosshairTarget = anchoredOwner;
                    slabbed$traceTargeting(tickProgress, initialTarget, "scan-anchored-fb-fired-slab-held", false);
                    return;
                }
                BlockHitResult visibleOwner = slabbed$preserveBeta35VisibleOwnerBeforeSupport(tickProgress, initialHit);
                if (slabbed$beta35VisibleOwnerPreserved(initialHit, visibleOwner)) {
                    client.crosshairTarget = visibleOwner;
                    slabbed$traceTargeting(
                            tickProgress,
                            initialTarget,
                            slabbed$beta35VisibleOwnerDecision(initialHit, visibleOwner, "visible-object-owner-preserve"),
                            false);
                    return;
                }
                slabbed$traceTargeting(
                        tickProgress,
                        initialTarget,
                        "scan-skip-initial-already-lowered-slab-face",
                        false);
                return;
            }
            if (slabbed$isInitialHitOnLoweredFullBlockPlacementIntent(initialHit)) {
                slabbed$traceTargeting(
                        tickProgress,
                        initialTarget,
                        "scan-skip-initial-already-lowered-full-block-placement-intent",
                        false);
                return;
            }
        }


        // Narrow slab-held guard: always evaluate both anchored-FB and
        // lowered-side-slab rescues, then choose by closer-or-tied distance.
        // When the held item is a slab, ties resolve to the side-slab so a
        // genuine BS-FB-0.5S placement aim still owns. When no side-slab
        // candidate is on the ray, the anchored-FB rescue still fires even
        // with a slab in hand — preventing live-trace failures where the
        // initial target sits on a farther/underneath block.
        BlockHitResult anchoredHit = slabbed$retargetAnchoredLoweredFullBlock(tickProgress, ht);
        BlockHitResult loweredSlabHit = slabbed$retargetLoweredSideSlab(tickProgress, ht, slabHeld);
        BlockHitResult originalLoweredSlabHit = loweredSlabHit;
        loweredSlabHit = slabbed$preserveBeta35VisibleOwnerBeforeSupport(tickProgress, loweredSlabHit);
        if (slabHeld && slabbed$isAboveAngleAnchoredOwnerSideSlabSteal(tickProgress, initialTarget, loweredSlabHit)) {
            loweredSlabHit = null;
        }
        BlockHitResult chosen = slabbed$chooseRescue(tickProgress, anchoredHit, loweredSlabHit, slabHeld);
        BlockHitResult loweredChainHit = slabHeld ? null : slabbed$retargetLoweredChainTopSupport(tickProgress, ht);
        if (loweredChainHit != null && slabbed$isCloserOrTied(tickProgress, loweredChainHit, chosen)) {
            chosen = loweredChainHit;
        }
        if (chosen != null) {
            client.crosshairTarget = chosen;
            boolean sideSlabFired = chosen == loweredSlabHit;
            String decision;
            if (chosen == loweredChainHit) {
                decision = "scan-lowered-chain-fired";
            } else if (slabbed$beta35VisibleOwnerPreserved(originalLoweredSlabHit, loweredSlabHit)
                    && chosen == loweredSlabHit) {
                decision = slabbed$beta35VisibleOwnerDecision(
                        originalLoweredSlabHit,
                        loweredSlabHit,
                        "visible-object-owner-preserve");
                sideSlabFired = false;
            } else if (sideSlabFired) {
                decision = slabHeld
                        ? (anchoredHit != null ? "scan-side-slab-fired-slab-held-tiebreak" : "scan-side-slab-fired")
                        : "scan-side-slab-fired";
            } else {
                decision = slabHeld ? "scan-anchored-fb-fired-slab-held" : "scan-anchored-fb-fired";
            }
            slabbed$traceTargeting(tickProgress, initialTarget, decision, sideSlabFired);
            return;
        }

        if (ht.getType() == HitResult.Type.MISS) {
            if (slabHeld) {
                BlockHitResult sideOwner = slabbed$traceSlabHeldMissSideRescueClassification(
                        tickProgress,
                        initialTarget,
                        "miss-no-rescue-candidate");
                BlockHitResult originalSideOwner = sideOwner;
                sideOwner = slabbed$preserveBeta35VisibleOwnerBeforeSupport(tickProgress, sideOwner);
                if (sideOwner != null) {
                    client.crosshairTarget = sideOwner;
                    slabbed$traceTargeting(
                            tickProgress,
                            initialTarget,
                            slabbed$beta35VisibleOwnerDecision(originalSideOwner, sideOwner, "scan-side-slab-fired"),
                            !slabbed$beta35VisibleOwnerPreserved(originalSideOwner, sideOwner));
                    return;
                }
            }
            slabbed$traceTargeting(tickProgress, initialTarget, "scan-no-rescue-candidate", false);
            return;
        }
        if (ht.getType() != HitResult.Type.BLOCK) {
            slabbed$traceTargeting(tickProgress, initialTarget, "scan-no-rescue-candidate", false);
            return;
        }
        if (!(ht instanceof BlockHitResult slabHit)) {
            slabbed$traceTargeting(tickProgress, initialTarget, "scan-no-rescue-candidate", false);
            return;
        }

        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            return;
        }

        BlockPos abovePos = slabHit.getBlockPos().up();
        BlockState aboveState = world.getBlockState(abovePos);
        boolean loweredOwner =
                SlabSupport.isLoweredBlockEntityVisual(world, abovePos, aboveState)
                        || SlabSupport.isLoweredTorchVisual(world, abovePos, aboveState)
                        || SlabSupport.isLoweredBedVisual(world, abovePos, aboveState);
        if (!loweredOwner) {
            // Ordinary solid full blocks have an unambiguous owner signature:
            // a lowered full-cube outline directly above the slab hit. Keep
            // crafting tables out of this pass because they remain a no-go.
            net.minecraft.block.Block block = aboveState.getBlock();
            loweredOwner = aboveState.isSolidBlock(world, abovePos)
                    && !(block instanceof net.minecraft.block.BlockEntityProvider)
                    && !(block instanceof net.minecraft.block.CraftingTableBlock)
                    && SlabSupport.getYOffset(world, abovePos, aboveState) == -0.5;
        }
        if (!loweredOwner) {
            BlockHitResult visibleOwner = slabbed$preserveBeta35VisibleOwnerBeforeSupport(tickProgress, slabHit);
            if (slabbed$beta35VisibleOwnerPreserved(slabHit, visibleOwner)) {
                client.crosshairTarget = visibleOwner;
                slabbed$traceTargeting(tickProgress, initialTarget,
                        slabbed$beta35VisibleOwnerDecision(slabHit, visibleOwner, "visible-object-owner-preserve"),
                        false);
                return;
            }
            slabbed$traceTargeting(tickProgress, initialTarget,
                    "scan-no-rescue-candidate;legacy-above-target-not-lowered-owner", false);
            return;
        }

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d slabHitPos = slabHit.getPos();
        Vec3d dir = slabHitPos.subtract(eye);
        double slabDist = dir.length();
        if (slabDist <= 0.0) {
            slabbed$traceTargeting(tickProgress, initialTarget,
                    "scan-no-rescue-candidate;legacy-slab-distance-zero", false);
            return;
        }
        // Extend slightly past the original hit so shape.raycast can intersect
        // the chest's offset front face which may be marginally further along
        // the ray than the slab-top intersection.
        Vec3d end = eye.add(dir.normalize().multiply(slabDist + 0.5));

        // Mirror vanilla crosshair ownership: crosshair targeting uses
        // RaycastContext.ShapeType.OUTLINE, which resolves to getOutlineShape
        // with the camera entity's ShapeContext. Blocks whose native
        // getRaycastShape is empty (most BlockEntityProvider blocks) would
        // otherwise never retarget.
        VoxelShape shape = aboveState.getOutlineShape(world, abovePos, ShapeContext.of(cam));
        BlockHitResult chestHit = shape.raycast(eye, end, abovePos);
        if (chestHit == null) {
            slabbed$traceTargeting(tickProgress, initialTarget,
                    "scan-no-rescue-candidate;legacy-shape-miss", false);
            return;
        }
        double chestDist2 = chestHit.getPos().squaredDistanceTo(eye);
        double slabDist2 = slabHitPos.squaredDistanceTo(eye);
        // Only retarget when the chest's offset shape is actually closer or
        // coincident with the slab hit — this is the overflow signature.
        if (chestDist2 > slabDist2 + 1.0e-6) {
            slabbed$traceTargeting(tickProgress, initialTarget,
                    "scan-no-rescue-candidate;legacy-candidate-farther-than-vanilla-hit", false);
            return;
        }

        client.crosshairTarget = chestHit;
        slabbed$traceTargeting(tickProgress, initialTarget,
                "scan-no-rescue-candidate;legacy-retarget-fired", false);
        } finally {
            slabbed$traceMc1211LiveOutlineTarget(
                    tickProgress,
                    slabbed$mc1211LiveOutlineTargetTraceInitialTarget);
        }
    }

    private void slabbed$traceMc1211LiveOutlineTarget(float tickProgress, HitResult initialTarget) {
        if (!Boolean.getBoolean(MC1211_LIVE_OUTLINE_TARGET_TRACE_PROPERTY)) {
            return;
        }

        if (!slabbed$mc1211LiveOutlineTargetTraceCanaryLogged) {
            slabbed$mc1211LiveOutlineTargetTraceCanaryLogged = true;
            Slabbed.LOGGER.info(
                    "[MC1211_LIVE_OUTLINE_TARGET_TRACE_CANARY] enabled=true property={}",
                    MC1211_LIVE_OUTLINE_TARGET_TRACE_PROPERTY);
        }

        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            slabbed$mc1211LiveOutlineTargetTraceSkipped++;
            return;
        }

        HitResult target = client.crosshairTarget;
        BlockHitResult blockTarget = target instanceof BlockHitResult blockHit
                && target.getType() == HitResult.Type.BLOCK
                ? blockHit
                : null;
        BlockPos targetPos = blockTarget == null ? null : blockTarget.getBlockPos();
        BlockState targetState = targetPos == null ? null : world.getBlockState(targetPos);
        BlockPos playerPos = client.player == null ? null : client.player.getBlockPos();
        BlockPos anchoredPos = slabbed$findMc1211LiveAnchoredStone(world, targetPos, playerPos);
        BlockState anchoredState = anchoredPos == null ? null : world.getBlockState(anchoredPos);
        boolean targetIsTraceBlock = targetState != null
                && (targetState.getBlock() == Blocks.STONE || targetState.getBlock() instanceof SlabBlock);
        boolean targetAirOrMiss = target == null
                || target.getType() == HitResult.Type.MISS
                || targetState == null
                || targetState.isAir();

        if (!targetIsTraceBlock && !targetAirOrMiss && anchoredPos == null) {
            slabbed$mc1211LiveOutlineTargetTraceSkipped++;
            return;
        }

        boolean targetEqualsAnchored = targetPos != null && targetPos.equals(anchoredPos);
        boolean targetAboveAnchored = targetPos != null && anchoredPos != null && targetPos.equals(anchoredPos.up());
        double anchoredDy = anchoredPos == null || anchoredState == null
                ? Double.NaN
                : SlabSupport.getYOffset(world, anchoredPos, anchoredState);
        String outlineMatchesVisibleModel = slabbed$mc1211OutlineMatchesVisibleModel(
                world,
                cam,
                targetPos,
                targetState,
                anchoredPos,
                anchoredDy);
        String reason = slabbed$mc1211LiveOutlineTargetReason(
                targetAirOrMiss,
                anchoredPos,
                targetEqualsAnchored,
                targetAboveAnchored);
        slabbed$countMc1211LiveOutlineTargetReason(reason);

        String signature = (target == null ? "null" : target.getType().toString())
                + '|'
                + (targetPos == null ? "none" : targetPos.toShortString())
                + '|'
                + (blockTarget == null ? "none" : blockTarget.getSide().toString())
                + '|'
                + (anchoredPos == null ? "none" : anchoredPos.toShortString())
                + '|'
                + reason;
        if (!slabbed$mc1211LiveOutlineTargetTraceSignatures.add(signature)) {
            slabbed$mc1211LiveOutlineTargetTraceSkipped++;
            return;
        }

        slabbed$mc1211LiveOutlineTargetTraceRows++;
        ItemStack held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandStack();
        Vec3d cameraPos = cam.getCameraPosVec(tickProgress);
        StringBuilder line = new StringBuilder(2048);
        line.append("[MC1211_LIVE_OUTLINE_TARGET_TRACE_ROW]");
        line.append(" tick=").append(world.getTime());
        line.append(" row=").append(slabbed$mc1211LiveOutlineTargetTraceRows);
        line.append(" heldItem=").append(held.isEmpty() ? "empty" : held.getItem().getTranslationKey());
        line.append(" cameraPos=").append(slabbed$formatVec(cameraPos));
        line.append(" playerPos=").append(client.player == null
                ? "none"
                : slabbed$formatVec(new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ())));
        line.append(" initialTarget=").append(slabbed$formatHit(initialTarget));
        line.append(" crosshairTargetType=").append(target == null ? "null" : target.getType());
        line.append(" crosshairTargetPos=").append(targetPos == null ? "none" : targetPos.toShortString());
        line.append(" crosshairTargetFace=").append(blockTarget == null ? "none" : blockTarget.getSide());
        line.append(" crosshairTargetHit=").append(blockTarget == null ? "none" : slabbed$formatVec(blockTarget.getPos()));
        line.append(" targetState=").append(targetState == null ? "none" : targetState);
        line.append(" targetDy=").append(targetState == null
                ? "NaN"
                : slabbed$formatDouble(SlabSupport.getYOffset(world, targetPos, targetState)));
        line.append(" targetClientDy=").append(targetState == null
                ? "NaN"
                : slabbed$formatDouble(ClientDy.dyFor(world, targetPos, targetState)));
        line.append(" targetOutlineBounds=").append(targetState == null
                ? "none"
                : slabbed$shapeBounds(world, cam, targetPos, targetState, true));
        line.append(" selectedShapeBounds=").append(targetState == null
                ? "none"
                : slabbed$shapeBounds(world, cam, targetPos, targetState, true));
        line.append(" modelTraceCandidatePos=").append(anchoredState != null && anchoredState.getBlock() == Blocks.STONE
                ? anchoredPos.toShortString()
                : "none");
        line.append(" knownAnchoredFullBlockPos=").append(anchoredPos == null ? "none" : anchoredPos.toShortString());
        line.append(" knownAnchoredFullBlockState=").append(anchoredState == null ? "none" : anchoredState);
        line.append(" knownAnchoredFullBlockDy=").append(Double.isNaN(anchoredDy)
                ? "NaN"
                : slabbed$formatDouble(anchoredDy));
        line.append(" targetEqualsAnchoredFullBlock=").append(targetEqualsAnchored);
        line.append(" targetOneAboveAnchoredFullBlock=").append(targetAboveAnchored);
        line.append(" selectedOutlineBoundsMatchVisibleModelBounds=").append(outlineMatchesVisibleModel);
        line.append(" reason=").append(reason);
        Slabbed.LOGGER.info(line.toString());

        if (slabbed$mc1211LiveOutlineTargetTraceRows % MC1211_LIVE_OUTLINE_TARGET_TRACE_SUMMARY_INTERVAL == 0) {
            slabbed$logMc1211LiveOutlineTargetTraceSummary();
        }
    }

    private static BlockPos slabbed$findMc1211LiveAnchoredStone(
            ClientWorld world,
            BlockPos targetPos,
            BlockPos playerPos
    ) {
        BlockPos first = slabbed$findMc1211LiveAnchoredStoneNear(world, targetPos, targetPos);
        if (first != null) {
            return first;
        }
        return slabbed$findMc1211LiveAnchoredStoneNear(world, playerPos, targetPos);
    }

    private static BlockPos slabbed$findMc1211LiveAnchoredStoneNear(
            ClientWorld world,
            BlockPos center,
            BlockPos distanceOrigin
    ) {
        if (world == null || center == null) {
            return null;
        }
        BlockPos best = null;
        double bestDist2 = Double.POSITIVE_INFINITY;
        int radius = 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (state.getBlock() != Blocks.STONE) {
                        continue;
                    }
                    if (!SlabAnchorAttachment.isAnchored(world, pos)) {
                        continue;
                    }
                    if (SlabSupport.getYOffset(world, pos, state) != -0.5d) {
                        continue;
                    }
                    double dist2 = distanceOrigin == null ? 0.0d : pos.getSquaredDistance(distanceOrigin);
                    if (best == null || dist2 < bestDist2) {
                        best = pos.toImmutable();
                        bestDist2 = dist2;
                    }
                }
            }
        }
        return best;
    }

    private static String slabbed$mc1211OutlineMatchesVisibleModel(
            ClientWorld world,
            Entity cam,
            BlockPos targetPos,
            BlockState targetState,
            BlockPos anchoredPos,
            double anchoredDy
    ) {
        if (targetPos == null || targetState == null || anchoredPos == null || !targetPos.equals(anchoredPos)) {
            return "unknown";
        }
        try {
            VoxelShape outline = targetState.getOutlineShape(world, targetPos, ShapeContext.of(cam));
            if (outline == null || outline.isEmpty()) {
                return "false";
            }
            Box bounds = outline.getBoundingBox();
            return Boolean.toString(
                    Math.abs(bounds.minY - anchoredDy) < 1.0e-6
                            && Math.abs(bounds.maxY - (anchoredDy + 1.0d)) < 1.0e-6);
        } catch (Throwable t) {
            return "error:" + t.getClass().getSimpleName();
        }
    }

    private static String slabbed$mc1211LiveOutlineTargetReason(
            boolean targetAirOrMiss,
            BlockPos anchoredPos,
            boolean targetEqualsAnchored,
            boolean targetAboveAnchored
    ) {
        if (targetAirOrMiss) {
            return "TARGET_AIR_OR_MISS";
        }
        if (anchoredPos == null) {
            return "TRACE_NO_RECENT_ANCHORED_BLOCK";
        }
        if (targetEqualsAnchored) {
            return "TARGET_MATCHES_ANCHORED_MODEL";
        }
        if (targetAboveAnchored) {
            return "TARGET_ABOVE_VISIBLE_MODEL";
        }
        return "TARGET_DIFFERENT_OWNER";
    }

    private static void slabbed$countMc1211LiveOutlineTargetReason(String reason) {
        switch (reason) {
            case "TARGET_MATCHES_ANCHORED_MODEL" -> slabbed$mc1211LiveOutlineTargetTraceTargetMatchesAnchored++;
            case "TARGET_ABOVE_VISIBLE_MODEL" -> slabbed$mc1211LiveOutlineTargetTraceTargetAboveAnchored++;
            case "TARGET_DIFFERENT_OWNER" -> slabbed$mc1211LiveOutlineTargetTraceDifferentOwner++;
            case "TARGET_AIR_OR_MISS" -> slabbed$mc1211LiveOutlineTargetTraceAirOrMiss++;
            case "TRACE_NO_RECENT_ANCHORED_BLOCK" -> slabbed$mc1211LiveOutlineTargetTraceNoRecentAnchor++;
            default -> { }
        }
    }

    private static void slabbed$logMc1211LiveOutlineTargetTraceSummary() {
        Slabbed.LOGGER.info(
                "[MC1211_LIVE_OUTLINE_TARGET_TRACE_SUMMARY] rows={} skipped={} targetMatchesAnchored={} targetAboveAnchored={} differentOwner={} airOrMiss={} noRecentAnchoredBlock={}",
                slabbed$mc1211LiveOutlineTargetTraceRows,
                slabbed$mc1211LiveOutlineTargetTraceSkipped,
                slabbed$mc1211LiveOutlineTargetTraceTargetMatchesAnchored,
                slabbed$mc1211LiveOutlineTargetTraceTargetAboveAnchored,
                slabbed$mc1211LiveOutlineTargetTraceDifferentOwner,
                slabbed$mc1211LiveOutlineTargetTraceAirOrMiss,
                slabbed$mc1211LiveOutlineTargetTraceNoRecentAnchor);
    }

    /**
     * Choose between an anchored-FB rescue candidate and a lowered-side-slab
     * rescue candidate. Both candidates have already been filtered to be
     * closer-than-the-vanilla-current-target by their respective scans.
     *
     * <p>Tie-break (within {@code 1e-6}):
     * <ul>
     *   <li><b>slab held</b>: prefer side-slab — preserves BS-FB-0.5S
     *       placement intent when the player is aiming at the side slab body.</li>
     *   <li><b>not slab held</b>: prefer anchored-FB — preserves the canonical
     *       lowered full-block selection for normal interaction.</li>
     * </ul>
     */
    private BlockHitResult slabbed$chooseRescue(
            float tickProgress, BlockHitResult anchored, BlockHitResult slab, boolean slabHeld
    ) {
        if (anchored == null && slab == null) {
            return null;
        }
        if (anchored == null) {
            return slab;
        }
        if (slab == null) {
            return anchored;
        }
        Entity cam = client.getCameraEntity();
        if (cam == null) {
            return slabHeld ? slab : anchored;
        }
        Vec3d eye = cam.getCameraPosVec(tickProgress);
        double anchoredDist2 = anchored.getPos().squaredDistanceTo(eye);
        double slabDist2 = slab.getPos().squaredDistanceTo(eye);
        final double eps = 1.0e-6;
        if (slabHeld) {
            if (slabbed$slabHeldStealsAnchoredUpOwner(anchored, slab)) {
                return anchored;
            }
            return (slabDist2 <= anchoredDist2 + eps) ? slab : anchored;
        }
        return (anchoredDist2 <= slabDist2 + eps) ? anchored : slab;
    }

    private boolean slabbed$slabHeldStealsAnchoredUpOwner(
            BlockHitResult anchored,
            BlockHitResult slab
    ) {
        if (anchored == null || slab == null || anchored.getSide() != Direction.UP) {
            return false;
        }
        ClientWorld world = client.world;
        if (world == null) {
            return false;
        }
        BlockPos anchoredPos = anchored.getBlockPos();
        BlockState anchoredState = world.getBlockState(anchoredPos);
        if (!slabbed$isAnchoredLoweredFullBlock(world, anchoredPos, anchoredState)) {
            return false;
        }
        if (slab.getSide().getAxis() == Direction.Axis.Y) {
            return false;
        }
        BlockPos slabPos = slab.getBlockPos();
        BlockState slabState = world.getBlockState(slabPos);
        return slabbed$isVisibleUpperLoweredSlabOwner(world, slabPos, slabState);
    }

    private static boolean slabbed$isDistinctOwner(BlockHitResult initialHit, BlockHitResult candidate) {
        return candidate != null && !candidate.getBlockPos().equals(initialHit.getBlockPos());
    }

    private BlockHitResult slabbed$retargetLoweredObjectShapeOwner(float tickProgress, HitResult currentHit) {
        if (currentHit == null || currentHit.getType() == HitResult.Type.ENTITY) {
            return null;
        }

        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d end;
        double currentDist2 = Double.POSITIVE_INFINITY;
        if (currentHit instanceof BlockHitResult blockHit && currentHit.getType() == HitResult.Type.BLOCK) {
            Vec3d currentPos = blockHit.getPos();
            Vec3d dir = currentPos.subtract(eye);
            double currentDist = dir.length();
            if (currentDist <= 0.0d) {
                return null;
            }
            end = eye.add(dir.normalize().multiply(currentDist + 0.5d));
            currentDist2 = currentPos.squaredDistanceTo(eye);

            BlockPos initialPos = blockHit.getBlockPos();
            BlockHitResult directHit = slabbed$raycastLoweredObjectShapeOwner(
                    world, cam, eye, end, initialPos, currentDist2);
            if (directHit != null) {
                return directHit;
            }

            directHit = slabbed$raycastLoweredObjectShapeOwner(
                    world, cam, eye, end, initialPos.up(), currentDist2);
            if (directHit != null) {
                return directHit;
            }

            BlockState initialState = world.getBlockState(initialPos);
            if (slabbed$isBeta35VisibleOwnerSupportSurface(initialState)) {
                directHit = slabbed$raycastBeta35VisibleOwnerBehindSupport(
                        world,
                        cam,
                        eye,
                        end,
                        initialPos.up(),
                        currentPos);
                if (directHit != null) {
                    return directHit;
                }
            }
        } else {
            end = eye.add(cam.getRotationVec(tickProgress).multiply(6.0d));
        }

        int steps = Math.max(16, (int) Math.ceil(6.0d / 0.05d));
        BlockHitResult bestHit = null;
        double bestDist2 = currentDist2;
        for (int i = 1; i <= steps; i++) {
            double t = 6.0d * i / steps;
            if (t * t > bestDist2 + 1.0e-6d) {
                break;
            }
            Vec3d sample = eye.add(cam.getRotationVec(tickProgress).multiply(t));
            BlockPos samplePos = BlockPos.ofFloored(sample);

            BlockHitResult hit = slabbed$raycastLoweredObjectShapeOwner(
                    world, cam, eye, end, samplePos, bestDist2);
            if (hit != null) {
                bestHit = hit;
                bestDist2 = hit.getPos().squaredDistanceTo(eye);
            }

            hit = slabbed$raycastLoweredObjectShapeOwner(
                    world, cam, eye, end, samplePos.up(), bestDist2);
            if (hit != null) {
                bestHit = hit;
                bestDist2 = hit.getPos().squaredDistanceTo(eye);
            }
        }

        if (bestHit != null) {
            return bestHit;
        }
        return slabbed$findBeta35HitboxOwnerObject(world, cam, eye, end);
    }

    private static BlockHitResult slabbed$raycastLoweredObjectShapeOwner(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, BlockPos pos, double currentDist2
    ) {
        BlockState state = world.getBlockState(pos);
        if (!slabbed$isLoweredObjectShapeOwner(world, pos, state)) {
            return null;
        }

        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.of(cam));
        if (outline == null || outline.isEmpty()) {
            return null;
        }
        BlockHitResult hit = outline.raycast(eye, end, pos);
        if (hit == null) {
            return null;
        }
        return hit.getPos().squaredDistanceTo(eye) <= currentDist2 + 1.0e-6d ? hit : null;
    }

    private static BlockHitResult slabbed$findBeta35HitboxOwnerObject(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end
    ) {
        int steps = Math.max(16, (int) Math.ceil(6.0d / 0.05d));
        BlockHitResult bestHit = null;
        double bestDist2 = Double.POSITIVE_INFINITY;
        Vec3d ray = end.subtract(eye);
        for (int i = 1; i <= steps; i++) {
            Vec3d sample = eye.add(ray.multiply((double) i / steps));
            BlockPos samplePos = BlockPos.ofFloored(sample);

            BlockHitResult hit = slabbed$raycastBeta35HitboxOwnerObject(world, cam, eye, end, samplePos, bestDist2);
            if (hit != null) {
                bestHit = hit;
                bestDist2 = hit.getPos().squaredDistanceTo(eye);
            }

            hit = slabbed$raycastBeta35HitboxOwnerObject(world, cam, eye, end, samplePos.up(), bestDist2);
            if (hit != null) {
                bestHit = hit;
                bestDist2 = hit.getPos().squaredDistanceTo(eye);
            }

            hit = slabbed$raycastBeta35HitboxOwnerObject(world, cam, eye, end, samplePos.up(2), bestDist2);
            if (hit != null) {
                bestHit = hit;
                bestDist2 = hit.getPos().squaredDistanceTo(eye);
            }

            // Slab-held MISS manual evidence shows some lowered underside owners
            // sit just below the sampled voxel band; include one step down.
            hit = slabbed$raycastBeta35HitboxOwnerObject(world, cam, eye, end, samplePos.down(), bestDist2);
            if (hit != null) {
                bestHit = hit;
                bestDist2 = hit.getPos().squaredDistanceTo(eye);
            }
        }
        return bestHit;
    }

    private static BlockHitResult slabbed$raycastBeta35HitboxOwnerObject(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, BlockPos pos, double bestDist2
    ) {
        BlockState state = world.getBlockState(pos);
        if (!slabbed$isBeta35HitboxOwnerObject(world, pos, state)) {
            return null;
        }

        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.of(cam));
        if (outline == null || outline.isEmpty()) {
            return null;
        }
        BlockHitResult hit = outline.raycast(eye, end, pos);
        if (hit == null) {
            return null;
        }
        return hit.getPos().squaredDistanceTo(eye) <= bestDist2 + 1.0e-6d ? hit : null;
    }

    private static BlockHitResult slabbed$raycastBeta35VisibleOwnerBehindSupport(
            ClientWorld world,
            Entity cam,
            Vec3d eye,
            Vec3d end,
            BlockPos pos,
            Vec3d supportHit
    ) {
        BlockState state = world.getBlockState(pos);
        if (!slabbed$isVisibleOwnerBeforeSupportObject(world, pos, state)) {
            return null;
        }

        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.of(cam));
        if (outline == null || outline.isEmpty()) {
            return null;
        }
        BlockHitResult hit = outline.raycast(eye, end, pos);
        if (hit == null) {
            return null;
        }
        double supportDist = supportHit.distanceTo(eye);
        double ownerDist = hit.getPos().distanceTo(eye);
        return ownerDist <= supportDist + BETA35_VISIBLE_OWNER_SUPPORT_OVERRUN ? hit : null;
    }

    private BlockHitResult slabbed$preserveBeta35VisibleOwnerBeforeSupport(float tickProgress, BlockHitResult candidate) {
        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (candidate == null || world == null || cam == null || candidate.getType() != HitResult.Type.BLOCK) {
            return candidate;
        }
        BlockPos supportPos = candidate.getBlockPos();
        BlockState supportState = world.getBlockState(supportPos);
        if (!slabbed$isBeta35VisibleOwnerSupportSurface(supportState)) {
            return candidate;
        }

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d supportHit = candidate.getPos();
        Vec3d toSupport = supportHit.subtract(eye);
        double supportDist = toSupport.length();
        if (supportDist <= 0.0d) {
            return candidate;
        }
        Vec3d end = eye.add(toSupport.normalize().multiply(supportDist + BETA35_VISIBLE_OWNER_SUPPORT_OVERRUN));
        BlockHitResult visibleOwner = slabbed$raycastBeta35VisibleOwnerBehindSupport(
                world,
                cam,
                eye,
                end,
                supportPos.up(),
                supportHit);
        return visibleOwner == null ? candidate : visibleOwner;
    }

    private boolean slabbed$beta35VisibleOwnerPreserved(BlockHitResult original, BlockHitResult chosen) {
        if (original == null || chosen == null || original.getBlockPos().equals(chosen.getBlockPos())) {
            return false;
        }
        ClientWorld world = client.world;
        return world != null
                && slabbed$isVisibleOwnerBeforeSupportObject(
                        world,
                        chosen.getBlockPos(),
                        world.getBlockState(chosen.getBlockPos()));
    }

    private String slabbed$beta35VisibleOwnerDecision(
            BlockHitResult original, BlockHitResult chosen, String fallback
    ) {
        if (!slabbed$beta35VisibleOwnerPreserved(original, chosen)) {
            return fallback;
        }
        ClientWorld world = client.world;
        if (world == null) {
            return "visible-object-owner-preserve";
        }
        BlockState state = world.getBlockState(chosen.getBlockPos());
        if (SlabSupport.isBeta35BottomTrapdoorVisibleOwnerObject(state)) {
            return "visible-trapdoor-owner-preserve";
        }
        if (SlabSupport.isBeta35VerticalChainVisibleOwnerObject(state)) {
            return "visible-chain-owner-preserve";
        }
        if (SlabSupport.isBeta35RegularDoorVisibleOwnerObject(world, chosen.getBlockPos(), state)) {
            return "visible-door-owner-preserve";
        }
        if (SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, chosen.getBlockPos(), state)) {
            return "visible-owner-top-slab-preserve";
        }
        return "visible-object-owner-preserve";
    }

    private static boolean slabbed$isBeta35VisibleOwnerSupportSurface(BlockState state) {
        return state != null && (state.getBlock() instanceof SlabBlock || SlabSupport.isSupportingSlab(state));
    }

    private static boolean slabbed$isLoweredObjectShapeOwner(ClientWorld world, BlockPos pos, BlockState state) {
        return SlabSupport.isLoweredTorchVisual(world, pos, state)
                || SlabSupport.isLoweredBlockEntityVisual(world, pos, state)
                || SlabSupport.isLoweredBedVisual(world, pos, state)
                || slabbed$isCompoundFullBlockShapeOwner(world, pos, state)
                || slabbed$isBeta35HitboxOwnerObject(world, pos, state);
    }

    private static boolean slabbed$isCompoundFullBlockShapeOwner(ClientWorld world, BlockPos pos, BlockState state) {
        return world != null
                && pos != null
                && state != null
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, pos, state)
                && Math.abs(SlabSupport.getYOffset(world, pos, state) + 1.0d) <= 1.0e-6d;
    }

    private static boolean slabbed$isBeta35HitboxOwnerObject(ClientWorld world, BlockPos pos, BlockState state) {
        return world != null
                && pos != null
                && state != null
                && SlabSupport.getYOffset(world, pos, state) < 0.0d
                && (SlabSupport.isBeta35FenceWallVariantContactObject(state)
                        || state.isOf(Blocks.ANVIL)
                        || slabbed$isVisibleOwnerBeforeSupportObject(world, pos, state)
                        || slabbed$isBeta35SlabHeightApertureOwnerObject(world, pos, state));
    }

    private static boolean slabbed$isVisibleOwnerBeforeSupportObject(
            ClientWorld world, BlockPos pos, BlockState state
    ) {
        return SlabSupport.isBeta35SlabHeightVisibleOwnerObject(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state);
    }

    private static boolean slabbed$isBeta35SlabHeightApertureOwnerObject(
            ClientWorld world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null || state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof SlabBlock || SlabSupport.isSupportingSlab(state)) {
            return false;
        }
        double objectDy = SlabSupport.getYOffset(world, pos, state);
        if (objectDy >= -1.0e-6d) {
            return false;
        }
        BlockPos supportPos = pos.down();
        BlockState supportState = world.getBlockState(supportPos);
        if (!(supportState.getBlock() instanceof SlabBlock)) {
            return false;
        }
        double supportDy = SlabSupport.getYOffset(world, supportPos, supportState);
        return Math.abs(supportDy + 1.0d) <= 1.0e-6d;
    }

    private String slabbed$classifyLiveFirstSeamOwner(BlockHitResult hit) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return SEAM_OWNER_NO_RESCUE;
        }

        ClientWorld world = client.world;
        if (world == null) {
            return SEAM_OWNER_KEEP_INITIAL;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (hit.getSide() == Direction.UP && slabbed$isAnchoredLoweredFullBlock(world, pos, state)) {
            return SEAM_OWNER_ANCHORED_FULL_BLOCK;
        }

        if (hit.getSide().getAxis() != Direction.Axis.Y
                && slabbed$isLoweredBottomSlabVisibleOwner(world, pos, state)) {
            if (slabbed$isVisibleUpperLoweredSlabOwner(world, pos, state)) {
                return SEAM_OWNER_VISIBLE_UPPER_LOWERED_SLAB;
            }
            if (slabbed$hasAdjacentAnchoredLoweredFullBlock(world, pos)) {
                return SEAM_OWNER_ADJACENT_VISIBLE_TARGET;
            }
        }

        return SEAM_OWNER_KEEP_INITIAL;
    }

    private static boolean slabbed$isLoweredBottomSlabVisibleOwner(ClientWorld world, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof SlabBlock
                && state.contains(SlabBlock.TYPE)
                && state.get(SlabBlock.TYPE) == SlabType.BOTTOM
                && state.getFluidState().isEmpty()
                && SlabSupport.getYOffset(world, pos, state) == -0.5;
    }

    private static boolean slabbed$isVisibleUpperLoweredSlabOwner(ClientWorld world, BlockPos pos, BlockState state) {
        return slabbed$isLoweredBottomSlabVisibleOwner(world, pos, state)
                && SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
                && slabbed$isAnchoredLoweredFullBlock(world, pos.down(), world.getBlockState(pos.down()));
    }

    private static String slabbed$seamOwnerDecision(String seamOwner) {
        return switch (seamOwner) {
            case SEAM_OWNER_VISIBLE_UPPER_LOWERED_SLAB -> "seam-visible-upper-lowered-slab-owner";
            case SEAM_OWNER_ADJACENT_VISIBLE_TARGET -> "seam-adjacent-visible-target-owner";
            case SEAM_OWNER_ANCHORED_FULL_BLOCK -> "seam-anchored-full-block-owner";
            case SEAM_OWNER_NO_RESCUE -> "seam-no-rescue";
            default -> "seam-keep-initial";
        };
    }

    private BlockHitResult slabbed$traceSlabHeldUpGuardSideOwnerClassification(
            float tickProgress, HitResult initialTarget, BlockHitResult initialHit
    ) {
        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            Slabbed.LOGGER.info("[SLAB_HELD_UP_GUARD_SIDE_OWNER_CLASSIFY] classification=unknown reason=no-world-or-camera");
            return null;
        }

        BlockPos initialPos = initialHit.getBlockPos();
        BlockState initialState = world.getBlockState(initialPos);
        double initialDy = SlabSupport.getYOffset(world, initialPos, initialState);
        boolean initialAnchored = SlabAnchorAttachment.isAnchored(world, initialPos);
        boolean initialLowered = initialDy == -0.5;
        boolean initialFullBlock = initialState.isSolidBlock(world, initialPos);
        Vec3d local = initialHit.getPos().subtract(initialPos.getX(), initialPos.getY(), initialPos.getZ());
        boolean topHit = initialHit.getSide() == Direction.UP;
        boolean edgeLike = local.x <= 0.15 || local.x >= 0.85 || local.z <= 0.15 || local.z >= 0.85;
        boolean topInterior = topHit && !edgeLike;

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        BlockHitResult sideOwner = slabbed$retargetLoweredSideSlab(tickProgress, initialHit, true);
        String candidateReason = sideOwner == null ? "none" : "accepted";
        double initialDist2 = initialHit.getPos().squaredDistanceTo(eye);
        double candidateDist2 = sideOwner == null ? Double.NaN : sideOwner.getPos().squaredDistanceTo(eye);
        boolean visibleUpperSideCandidate = false;
        if (sideOwner != null) {
            BlockPos sidePos = sideOwner.getBlockPos();
            BlockState sideState = world.getBlockState(sidePos);
            double sideDy = SlabSupport.getYOffset(world, sidePos, sideState);
            visibleUpperSideCandidate = slabbed$isVisibleUpperLoweredSlabOwner(world, sidePos, sideState)
                    && sideOwner.getSide().getAxis() != Direction.Axis.Y
                    && sideState.contains(SlabBlock.TYPE)
                    && sideState.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && sideDy == -0.5
                    && candidateDist2 < initialDist2;
        }
        String classification;
        if (sideOwner == null) {
            classification = "noCandidate";
        } else if (topHit && initialAnchored && initialLowered && initialFullBlock) {
            classification = "anchoredUpPreserve";
        } else if (topInterior) {
            classification = "trueTopPreserve";
        } else {
            classification = "sideOwnerWouldWin";
        }

        BlockHitResult finalTarget = "sideOwnerWouldWin".equals(classification)
                || "visibleUpperSideFaceOwner".equals(classification)
                ? sideOwner
                : initialHit;
        slabbed$recordBeta4LiveRetarget(
                tickProgress,
                initialTarget,
                finalTarget,
                "UP_GUARD",
                classification,
                sideOwner,
                candidateReason,
                edgeLike,
                topInterior,
                initialDist2,
                candidateDist2);

        ItemStack held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandStack();
        StringBuilder line = new StringBuilder(768);
        line.append("[SLAB_HELD_UP_GUARD_SIDE_OWNER_CLASSIFY]");
        line.append(" heldItem=").append(held.isEmpty() ? "empty" : held.getItem().getTranslationKey());
        line.append(" heldIsSlab=").append(slabbed$isSlabPlacementIntent());
        line.append(" initial=").append(slabbed$formatHit(initialHit));
        line.append(" initialState=").append(initialState);
        line.append(" initialDy=").append(String.format("%.3f", initialDy));
        line.append(" initialAnchored=").append(initialAnchored);
        line.append(" initialLowered=").append(initialLowered);
        line.append(" initialFullBlock=").append(initialFullBlock);
        line.append(" localHit=").append(slabbed$formatVec(local));
        line.append(" topHit=").append(topHit);
        line.append(" edgeLike=").append(edgeLike);
        line.append(" topInterior=").append(topInterior);
        line.append(" sideScanCandidateExists=").append(sideOwner != null);
        line.append(" sideScanCandidateReason=").append(candidateReason);
        line.append(" sideScanCandidate=").append(slabbed$formatHit(sideOwner));
        if (sideOwner != null) {
            line.append(" sideScanCandidateFacts=")
                    .append(slabbed$formatSideOwnerFacts(world, sideOwner.getBlockPos()));
        }
        line.append(" initialDist2=").append(String.format("%.6f", initialDist2));
        line.append(" candidateDist2=").append(sideOwner == null ? "NaN" : String.format("%.6f", candidateDist2));
        line.append(" candidateMinusInitialDist2=")
                .append(sideOwner == null ? "NaN" : String.format("%.6f", candidateDist2 - initialDist2));
        line.append(" wouldProduceScanSideSlabFired=").append(sideOwner != null);
        line.append(" classification=").append(classification);
        line.append(" initialTarget=").append(slabbed$formatHit(initialTarget));
        Slabbed.LOGGER.info(line.toString());
        return "sideOwnerWouldWin".equals(classification) || "visibleUpperSideFaceOwner".equals(classification)
                ? sideOwner
                : null;
    }

    private static String slabbed$formatSideOwnerFacts(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        boolean lowered = dy == -0.5;
        String slabType = state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE).asString() : "none";
        return "pos=" + pos.toShortString()
                + " state=" + state
                + " dy=" + String.format("%.3f", dy)
                + " slabType=" + slabType
                + " anchored=" + anchored
                + " lowered=" + lowered;
    }

    private BlockHitResult slabbed$traceSlabHeldMissSideRescueClassification(
            float tickProgress, HitResult initialTarget, String exitReason
    ) {
        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            Slabbed.LOGGER.info("[SLAB_HELD_MISS_SIDE_RESCUE_CLASSIFY] classification=unknown reason=no-world-or-camera");
            return null;
        }

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d dir = cam.getRotationVec(tickProgress);
        double reach = 6.0;
        Vec3d end = eye.add(dir.multiply(reach));
        boolean initialMiss = initialTarget == null || initialTarget.getType() == HitResult.Type.MISS;
        boolean loweredSlabFacePreserve = "lowered-slab-face-preserve".equals(exitReason);

        BlockHitResult slabHeldCandidate = slabbed$retargetLoweredSideSlab(tickProgress, initialTarget, true);
        BlockHitResult visibleUpperSideFaceMissCandidate =
                slabbed$retargetVisibleUpperLoweredSlabSideFaceMiss(tickProgress, initialTarget);
        if (slabbed$isCloserOrTied(tickProgress, visibleUpperSideFaceMissCandidate, slabHeldCandidate)) {
            slabHeldCandidate = visibleUpperSideFaceMissCandidate;
        }
        boolean visibleUpperSideFaceMissOwner = visibleUpperSideFaceMissCandidate != null
                && slabHeldCandidate != null
                && slabHeldCandidate.getBlockPos().equals(visibleUpperSideFaceMissCandidate.getBlockPos())
                && slabHeldCandidate.getSide().getAxis() != Direction.Axis.Y;
        BlockHitResult suppressedAboveAngleCandidate = null;
        if (!visibleUpperSideFaceMissOwner
                && slabbed$isAboveAngleAnchoredOwnerSideSlabSteal(tickProgress, initialTarget, slabHeldCandidate)) {
            suppressedAboveAngleCandidate = slabHeldCandidate;
            slabHeldCandidate = null;
        }
        BlockHitResult nonSlabComparisonCandidate = slabbed$retargetLoweredSideSlab(tickProgress, initialTarget, false);
        BlockHitResult reportCandidate = slabHeldCandidate != null
                ? slabHeldCandidate
                : (suppressedAboveAngleCandidate != null ? suppressedAboveAngleCandidate : nonSlabComparisonCandidate);

        boolean sameBlockSuppressed = slabHeldCandidate == null
                && nonSlabComparisonCandidate != null
                && initialTarget instanceof BlockHitResult initialBlock
                && initialBlock.getBlockPos().equals(nonSlabComparisonCandidate.getBlockPos());

        String candidateReason;
        String classification;
        if (slabHeldCandidate != null) {
            candidateReason = visibleUpperSideFaceMissOwner
                    ? "visible-upper-side-face-offset-hit"
                    : "accepted";
            classification = visibleUpperSideFaceMissOwner
                    ? "visibleUpperSideFaceOwner"
                    : "sideOwnerWouldWin";
        } else if (suppressedAboveAngleCandidate != null) {
            candidateReason = "above-angle-anchored-owner-suppress";
            classification = "suppressedByAboveAngleAnchoredOwner";
        } else if (nonSlabComparisonCandidate != null) {
            candidateReason = sameBlockSuppressed ? "same-block-slab-held-suppress" : "unknown";
            classification = "suppressedBySlabHeld";
        } else {
            candidateReason = "no-candidate";
            classification = initialMiss ? "trueMiss" : "noCandidate";
        }

        double candidateDist2 = reportCandidate == null ? Double.NaN : reportCandidate.getPos().squaredDistanceTo(eye);
        double initialDist2 = initialTarget == null || initialTarget.getType() != HitResult.Type.BLOCK
                ? Double.NaN
                : initialTarget.getPos().squaredDistanceTo(eye);
        ItemStack held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandStack();

        slabbed$recordBeta4LiveRetarget(
                tickProgress,
                initialTarget,
                slabHeldCandidate != null ? slabHeldCandidate : initialTarget,
                "MISS_SIDE",
                classification,
                reportCandidate,
                candidateReason,
                null,
                null,
                initialDist2,
                candidateDist2);

        StringBuilder line = new StringBuilder(768);
        line.append("[SLAB_HELD_MISS_SIDE_RESCUE_CLASSIFY]");
        line.append(" heldItem=").append(held.isEmpty() ? "empty" : held.getItem().getTranslationKey());
        line.append(" heldIsSlab=").append(slabbed$isSlabPlacementIntent());
        line.append(" initialType=").append(initialTarget == null ? "null" : initialTarget.getType());
        line.append(" initial=").append(slabbed$formatHit(initialTarget));
        line.append(" eye=").append(slabbed$formatVec(eye));
        line.append(" end=").append(slabbed$formatVec(end));
        line.append(" reach=").append(String.format("%.3f", reach));
        line.append(" initialMiss=").append(initialMiss);
        line.append(" exitReason=").append(exitReason);
        line.append(" slabHeldLoweredSlabFacePreserve=").append(loweredSlabFacePreserve);
        line.append(" sideScanRun=true");
        line.append(" sideScanCandidateExists=").append(slabHeldCandidate != null);
        line.append(" sideScanCandidateReason=").append(candidateReason);
        line.append(" sideScanCandidate=").append(slabbed$formatHit(reportCandidate));
        if (reportCandidate != null) {
            line.append(" sideScanCandidateFacts=")
                    .append(slabbed$formatSideOwnerFacts(world, reportCandidate.getBlockPos()));
        }
        line.append(" candidateDist2=").append(reportCandidate == null ? "NaN" : String.format("%.6f", candidateDist2));
        line.append(" wouldProduceScanSideSlabFired=").append(slabHeldCandidate != null);
        line.append(" nonSlabComparisonCandidateExists=").append(nonSlabComparisonCandidate != null);
        line.append(" nonSlabComparisonCandidate=").append(slabbed$formatHit(nonSlabComparisonCandidate));
        line.append(" nonSlabWouldProduceScanSideSlabFired=").append(nonSlabComparisonCandidate != null);
        line.append(" classification=").append(classification);
        Slabbed.LOGGER.info(line.toString());
        slabbed$emitManualSideRescueVerdict(
                classification,
                held,
                initialTarget,
                exitReason,
                slabHeldCandidate,
                reportCandidate,
                nonSlabComparisonCandidate,
                candidateReason,
                eye,
                end);
        slabbed$emitMc1211TrapdoorSeamMp4ManualRed(
                classification,
                held,
                initialTarget,
                exitReason,
                slabHeldCandidate,
                reportCandidate,
                nonSlabComparisonCandidate,
                candidateReason,
                eye,
                end);
        return slabHeldCandidate != null ? slabHeldCandidate : null;
    }

    private void slabbed$emitMc1211TrapdoorSeamMp4ManualRed(
            String classification,
            ItemStack held,
            HitResult initialTarget,
            String exitReason,
            BlockHitResult slabHeldCandidate,
            BlockHitResult reportCandidate,
            BlockHitResult nonSlabComparisonCandidate,
            String candidateReason,
            Vec3d eye,
            Vec3d end
    ) {
        if (!Boolean.getBoolean(MC1211_TRAPDOOR_SEAM_MP4_RED_PROPERTY)
                || (!"trueMiss".equals(classification) && !"noCandidate".equals(classification))) {
            return;
        }
        if (!slabbed$mc1211TrapdoorSeamMp4ManualStartLogged) {
            slabbed$mc1211TrapdoorSeamMp4ManualStartLogged = true;
            Slabbed.LOGGER.info("[MC1211_TRAPDOOR_SEAM_MP4_MANUAL_RED_START]"
                    + " route=runClient"
                    + " property=" + MC1211_TRAPDOOR_SEAM_MP4_RED_PROPERTY
                    + " sourceTruth=manual-live-recorder"
                    + " gameplayPatch=false");
        }
        String signature = classification
                + "|initial=" + slabbed$formatHit(initialTarget)
                + "|exitReason=" + exitReason
                + "|candidateReason=" + candidateReason;
        if (!slabbed$mc1211TrapdoorSeamMp4ManualRedSignatures.add(signature)) {
            return;
        }
        slabbed$mc1211TrapdoorSeamMp4ManualRedRows++;
        Slabbed.LOGGER.info("[MC1211_TRAPDOOR_SEAM_MP4_MANUAL_RED]"
                + " row=" + slabbed$mc1211TrapdoorSeamMp4ManualRedRows
                + " finalResult=RED"
                + " classification=MP4_MANUAL_LIVE_TARGETING_RED"
                + " liveClassification=" + classification
                + " failureLayer=raycast/rescue"
                + " heldItem=" + (held.isEmpty() ? "empty" : held.getItem().getTranslationKey())
                + " heldIsSlab=" + slabbed$isSlabPlacementIntent()
                + " initial=" + slabbed$formatHit(initialTarget)
                + " exitReason=" + exitReason
                + " sideScanCandidateReason=" + candidateReason
                + " sideScanCandidate=" + slabbed$formatHit(reportCandidate)
                + " slabHeldCandidate=" + slabbed$formatHit(slabHeldCandidate)
                + " nonSlabComparisonCandidate=" + slabbed$formatHit(nonSlabComparisonCandidate)
                + " eye=" + slabbed$formatVec(eye)
                + " end=" + slabbed$formatVec(end)
                + " gameplayPatch=false");
        Slabbed.LOGGER.info("[MC1211_TRAPDOOR_SEAM_MP4_MANUAL_SUMMARY]"
                + " rows=" + slabbed$mc1211TrapdoorSeamMp4ManualRedRows
                + " finalResult=RED"
                + " classification=MP4_MANUAL_LIVE_TARGETING_RED"
                + " failureLayer=raycast/rescue"
                + " patchAllowedNext=false"
                + " gameplayPatch=false");
    }

    private void slabbed$emitManualSideRescueVerdict(
            String classification,
            ItemStack held,
            HitResult initialTarget,
            String exitReason,
            BlockHitResult slabHeldCandidate,
            BlockHitResult reportCandidate,
            BlockHitResult nonSlabComparisonCandidate,
            String candidateReason,
            Vec3d eye,
            Vec3d end
    ) {
        if (!Boolean.getBoolean("slabbed.beta4ManualLiveTrace")) {
            return;
        }
        if (!slabbed$manualSideRescueVerdictStartLogged) {
            slabbed$manualSideRescueVerdictStartLogged = true;
            Slabbed.LOGGER.info(
                    "[SLAB_HELD_BLOCK_LOWERED_FACE_SIDE_RESCUE_START] route=runClient property=slabbed.beta4ManualLiveTrace sourceTruth=manual-live-recorder gameplayPatch=false");
        }

        String finalResult = switch (classification) {
            case "sideOwnerWouldWin", "visibleUpperSideFaceOwner" -> "GREEN";
            case "suppressedByAboveAngleAnchoredOwner", "suppressedBySlabHeld", "trueMiss", "noCandidate" -> "RED";
            default -> "TRACE_GAP";
        };
        String failureLayer = "GREEN".equals(finalResult) ? "NONE"
                : ("TRACE_GAP".equals(finalResult) ? "proof gap" : "raycast/rescue");
        String signature = classification
                + "|initial=" + slabbed$formatHit(initialTarget)
                + "|exitReason=" + exitReason
                + "|candidateReason=" + candidateReason
                + "|final=" + finalResult;
        if (!slabbed$manualSideRescueVerdictSignatures.add(signature)) {
            return;
        }

        slabbed$manualSideRescueVerdictRows++;
        if ("GREEN".equals(finalResult)) {
            slabbed$manualSideRescueVerdictGreenRows++;
        } else if ("RED".equals(finalResult)) {
            slabbed$manualSideRescueVerdictRedRows++;
        }

        Slabbed.LOGGER.info(
                "[SLAB_HELD_BLOCK_LOWERED_FACE_SIDE_RESCUE] row={} finalResult={} classification={} liveClassification={} failureLayer={} heldItem={} heldIsSlab={} initial={} exitReason={} sideScanCandidateReason={} sideScanCandidate={} slabHeldCandidate={} nonSlabComparisonCandidate={} eye={} end={} sourceTruth=manual-live-recorder gameplayPatch=false",
                slabbed$manualSideRescueVerdictRows,
                finalResult,
                "GREEN".equals(finalResult)
                        ? "SLAB_HELD_BLOCK_LOWERED_FACE_SIDE_RESCUE_GREEN"
                        : ("RED".equals(finalResult)
                        ? "SLAB_HELD_BLOCK_LOWERED_FACE_SIDE_RESCUE_RED"
                        : "SLAB_HELD_BLOCK_LOWERED_FACE_SIDE_RESCUE_TRACE_GAP"),
                classification,
                failureLayer,
                held.isEmpty() ? "empty" : held.getItem().getTranslationKey(),
                slabbed$isSlabPlacementIntent(),
                slabbed$formatHit(initialTarget),
                exitReason,
                candidateReason,
                slabbed$formatHit(reportCandidate),
                slabbed$formatHit(slabHeldCandidate),
                slabbed$formatHit(nonSlabComparisonCandidate),
                slabbed$formatVec(eye),
                slabbed$formatVec(end));
        Slabbed.LOGGER.info(
                "[SLAB_HELD_BLOCK_LOWERED_FACE_SIDE_RESCUE_SUMMARY] rows={} greenRows={} redRows={} finalResult={} liveClassification={} failureLayer={} gameplayPatch=false",
                slabbed$manualSideRescueVerdictRows,
                slabbed$manualSideRescueVerdictGreenRows,
                slabbed$manualSideRescueVerdictRedRows,
                finalResult,
                classification,
                failureLayer);
    }

    private boolean slabbed$isAboveAngleAnchoredOwnerSideSlabSteal(
            float tickProgress, HitResult initialTarget, BlockHitResult sideSlabCandidate
    ) {
        if (initialTarget instanceof BlockHitResult initialBlock
                && initialBlock.getType() == HitResult.Type.BLOCK
                && sideSlabCandidate != null
                && initialBlock.getBlockPos().equals(sideSlabCandidate.getBlockPos())) {
            return false;
        }
        return slabbed$isAboveAngleLowerFrontSlabOverAnchoredOwner(tickProgress, sideSlabCandidate, 0.15d);
    }

    private BlockHitResult slabbed$retargetAboveAngleLowerFrontSlabToAnchoredOwner(
            float tickProgress, BlockHitResult lowerFrontHit
    ) {
        if (!slabbed$isAboveAngleLowerFrontSlabOverAnchoredOwner(tickProgress, lowerFrontHit, 0.20d)) {
            return null;
        }
        BlockPos ownerPos = lowerFrontHit.getBlockPos().down();
        return new BlockHitResult(
                lowerFrontHit.getPos(),
                lowerFrontHit.getSide(),
                ownerPos,
                lowerFrontHit.isInsideBlock());
    }

    private boolean slabbed$isAboveAngleLowerFrontSlabOverAnchoredOwner(
            float tickProgress, BlockHitResult sideSlabCandidate, double maxAbsVerticalLook
    ) {
        if (sideSlabCandidate == null || sideSlabCandidate.getSide().getAxis() == Direction.Axis.Y) {
            return false;
        }

        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            return false;
        }

        BlockPos slabPos = sideSlabCandidate.getBlockPos();
        BlockState slabState = world.getBlockState(slabPos);
        if (!(slabState.getBlock() instanceof SlabBlock)
                || !slabState.contains(SlabBlock.TYPE)
                || slabState.get(SlabBlock.TYPE) != SlabType.BOTTOM
                || SlabSupport.getYOffset(world, slabPos, slabState) != -0.5
                || !SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, slabPos, slabState)) {
            return false;
        }

        BlockPos ownerPos = slabPos.down();
        if (!slabbed$isAnchoredLoweredFullBlock(world, ownerPos, world.getBlockState(ownerPos))) {
            return false;
        }

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d dir = cam.getRotationVec(tickProgress);
        if (Math.abs(dir.y) > maxAbsVerticalLook) {
            return false;
        }

        VoxelShape outline = slabState.getOutlineShape(world, slabPos, ShapeContext.of(cam));
        if (outline == null || outline.isEmpty()) {
            return false;
        }
        net.minecraft.util.math.Box bounds = outline.getBoundingBox();
        double minY = slabPos.getY() + bounds.minY;
        double maxY = slabPos.getY() + bounds.maxY;
        return eye.y >= minY - 0.05d && eye.y <= maxY + 0.05d;
    }

    private boolean slabbed$isCloserOrTied(float tickProgress, BlockHitResult candidate, BlockHitResult current) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        Entity cam = client.getCameraEntity();
        if (cam == null) {
            return false;
        }
        Vec3d eye = cam.getCameraPosVec(tickProgress);
        return candidate.getPos().squaredDistanceTo(eye) <= current.getPos().squaredDistanceTo(eye) + 1.0e-6;
    }

    private boolean slabbed$isSlabPlacementIntent() {
        if (client.player == null) {
            return false;
        }
        ItemStack stack = client.player.getMainHandStack();
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof SlabBlock;
    }

    private boolean slabbed$isInitialHitOnLoweredSlabFace(BlockHitResult hit) {
        ClientWorld world = client.world;
        if (world == null) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof SlabBlock
                && state.contains(SlabBlock.TYPE)
                && state.getFluidState().isEmpty()
                && SlabSupport.getYOffset(world, pos, state) == -0.5;
    }

    private boolean slabbed$isInitialHitOnLoweredFullBlockPlacementIntent(BlockHitResult hit) {
        ClientWorld world = client.world;
        if (world == null || hit.getType() != HitResult.Type.BLOCK || hit.getSide() == Direction.DOWN) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SlabBlock || !slabbed$isAnchoredOrLoweredFullBlock(world, pos, state)) {
            return false;
        }
        BlockPos placePos = pos.offset(hit.getSide());
        BlockState placeState = world.getBlockState(placePos);
        return placeState.isAir() || slabbed$isLoweredSlabLaneState(world, placePos, placeState);
    }

    private static boolean slabbed$isOrdinaryFullBlock(ClientWorld world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!state.isSolidBlock(world, pos)) {
            return false;
        }
        if (state.getBlock() instanceof SlabBlock) {
            return false;
        }
        net.minecraft.block.Block block = state.getBlock();
        return !(block instanceof BlockEntityProvider) && !(block instanceof CraftingTableBlock);
    }

    private static boolean slabbed$isAnchoredOrLoweredFullBlock(ClientWorld world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || state.getBlock() instanceof SlabBlock) {
            return false;
        }
        if (!state.isSolidBlock(world, pos)) {
            return false;
        }

        net.minecraft.block.Block block = state.getBlock();
        if (block instanceof BlockEntityProvider || block instanceof CraftingTableBlock) {
            return false;
        }

        return SlabAnchorAttachment.isAnchored(world, pos)
                || SlabSupport.getYOffset(world, pos, state) == -0.5;
    }

    private static boolean slabbed$isLoweredSlabLaneState(ClientWorld world, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof SlabBlock
                && state.contains(SlabBlock.TYPE)
                && state.getFluidState().isEmpty()
                && SlabSupport.getYOffset(world, pos, state) == -0.5;
    }

    private BlockHitResult slabbed$retargetLoweredSideSlab(float tickProgress, HitResult currentHit, boolean slabHeld) {
        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d dir = cam.getRotationVec(tickProgress);
        double reach = 6.0;
        Vec3d end = eye.add(dir.multiply(reach));
        return LoweredSideSlabRetargeter.findLoweredSideSlabRetarget(world, cam, eye, end, currentHit, slabHeld);
    }

    private BlockHitResult slabbed$retargetVisibleUpperLoweredSlabSideFaceMiss(
            float tickProgress, HitResult currentHit
    ) {
        if (currentHit == null || currentHit.getType() != HitResult.Type.MISS) {
            return null;
        }
        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d end = eye.add(cam.getRotationVec(tickProgress).multiply(6.0d));
        int minX = (int) Math.floor(Math.min(eye.x, end.x)) - 1;
        int minY = (int) Math.floor(Math.min(eye.y, end.y)) - 1;
        int minZ = (int) Math.floor(Math.min(eye.z, end.z)) - 1;
        int maxX = (int) Math.floor(Math.max(eye.x, end.x)) + 1;
        int maxY = (int) Math.floor(Math.max(eye.y, end.y)) + 1;
        int maxZ = (int) Math.floor(Math.max(eye.z, end.z)) + 1;

        BlockHitResult best = null;
        double bestDist2 = Double.POSITIVE_INFINITY;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (!slabbed$isVisibleUpperLoweredSlabOwner(world, pos, state)) {
                        continue;
                    }
                    VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.of(cam));
                    if (outline == null || outline.isEmpty()) {
                        continue;
                    }
                    BlockHitResult hit = outline.raycast(eye, end, pos);
                    if (hit == null || hit.getSide().getAxis() == Direction.Axis.Y) {
                        continue;
                    }
                    double dist2 = hit.getPos().squaredDistanceTo(eye);
                    if (dist2 <= 36.0d + 1.0e-6d && dist2 < bestDist2) {
                        bestDist2 = dist2;
                        best = new BlockHitResult(hit.getPos(), hit.getSide(), pos, hit.isInsideBlock());
                    }
                }
            }
        }
        return best;
    }

    private BlockHitResult slabbed$retargetLoweredChainTopSupport(float tickProgress, HitResult currentHit) {
        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d dir = cam.getRotationVec(tickProgress);
        double reach = 6.0;
        Vec3d end = eye.add(dir.multiply(reach));
        double currentDist2 = Double.POSITIVE_INFINITY;
        if (currentHit != null && currentHit.getType() == HitResult.Type.BLOCK) {
            currentDist2 = currentHit.getPos().squaredDistanceTo(eye);
        }
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05));

        BlockHitResult bestHit = null;
        double bestDist2 = currentDist2;
        for (int i = 1; i <= steps; i++) {
            double t = reach * i / steps;
            if (t * t > bestDist2 + 1.0e-6) {
                break;
            }
            Vec3d sample = eye.add(dir.multiply(t));
            BlockPos samplePos = BlockPos.ofFloored(sample);

            BlockHitResult hit = slabbed$raycastLoweredChainTopSupport(world, cam, eye, end, samplePos);
            if (hit != null) {
                double dist2 = hit.getPos().squaredDistanceTo(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }

            hit = slabbed$raycastLoweredChainTopSupport(world, cam, eye, end, samplePos.up());
            if (hit != null) {
                double dist2 = hit.getPos().squaredDistanceTo(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }
        }

        return bestHit;
    }

    private static BlockHitResult slabbed$raycastLoweredChainTopSupport(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, BlockPos pos
    ) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChainBlock)
                || !state.contains(ChainBlock.AXIS)
                || state.get(ChainBlock.AXIS) != Direction.Axis.Y
                || SlabSupport.getYOffset(world, pos, state) >= 0.0) {
            return null;
        }

        BlockPos supportPos = pos.down();
        BlockState supportState = world.getBlockState(supportPos);
        if (!(supportState.getBlock() instanceof SlabBlock)
                || !supportState.contains(SlabBlock.TYPE)
                || supportState.get(SlabBlock.TYPE) != SlabType.BOTTOM
                || SlabSupport.getYOffset(world, supportPos, supportState) != -0.5
                || !slabbed$hasAdjacentAnchoredLoweredFullBlock(world, supportPos)) {
            return null;
        }

        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.of(cam));
        BlockHitResult outlineHit = outline.raycast(eye, end, pos);
        if (outlineHit == null) {
            return null;
        }
        return outlineHit.getPos().squaredDistanceTo(eye) <= end.squaredDistanceTo(eye) + 1.0e-6
                ? outlineHit : null;
    }

    private static boolean slabbed$hasAdjacentAnchoredLoweredFullBlock(ClientWorld world, BlockPos supportPos) {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos candidatePos = supportPos.offset(direction);
            BlockState candidateState = world.getBlockState(candidatePos);
            if (slabbed$isAnchoredLoweredFullBlock(world, candidatePos, candidateState)) {
                return true;
            }
        }
        return false;
    }

    private BlockHitResult slabbed$retargetAnchoredLoweredFullBlock(float tickProgress, HitResult currentHit) {
        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d dir = cam.getRotationVec(tickProgress);
        double reach = 6.0;
        Vec3d end = eye.add(dir.multiply(reach));
        double currentDist2 = Double.POSITIVE_INFINITY;
        if (currentHit != null && currentHit.getType() == HitResult.Type.BLOCK) {
            currentDist2 = currentHit.getPos().squaredDistanceTo(eye);
        }
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05));

        BlockHitResult bestHit = null;
        double bestDist2 = currentDist2;
        for (int i = 1; i <= steps; i++) {
            double t = reach * i / steps;
            if (t * t > bestDist2 + 1.0e-6) {
                break;
            }
            Vec3d sample = eye.add(dir.multiply(t));
            BlockPos samplePos = BlockPos.ofFloored(sample);

            BlockPos candidatePos = samplePos;
            BlockState candidateState = world.getBlockState(candidatePos);
            BlockHitResult hit = slabbed$raycastAnchoredLoweredFullBlock(world, cam, eye, end, candidatePos, candidateState);
            if (hit != null) {
                double dist2 = hit.getPos().squaredDistanceTo(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }

            candidatePos = samplePos.up();
            candidateState = world.getBlockState(candidatePos);
            hit = slabbed$raycastAnchoredLoweredFullBlock(world, cam, eye, end, candidatePos, candidateState);
            if (hit != null) {
                double dist2 = hit.getPos().squaredDistanceTo(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }
        }

        return bestHit;
    }

    private static BlockHitResult slabbed$raycastAnchoredLoweredFullBlock(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, BlockPos pos, BlockState state
    ) {
        if (!slabbed$isAnchoredLoweredFullBlock(world, pos, state)) {
            return null;
        }
        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.of(cam));
        VoxelShape raycast = state.getRaycastShape(world, pos);
        BlockHitResult outlineHit = outline.raycast(eye, end, pos);
        BlockHitResult raycastHit = raycast.raycast(eye, end, pos);
        if (outlineHit == null || raycastHit == null) {
            return null;
        }
        return raycastHit.getPos().squaredDistanceTo(eye) <= end.squaredDistanceTo(eye) + 1.0e-6
                ? raycastHit : null;
    }

    private static boolean slabbed$isAnchoredLoweredFullBlock(ClientWorld world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!state.isSolidBlock(world, pos)) {
            return false;
        }

        net.minecraft.block.Block block = state.getBlock();
        if (block instanceof BlockEntityProvider || block instanceof CraftingTableBlock) {
            return false;
        }

        return SlabAnchorAttachment.isAnchored(world, pos)
                && SlabSupport.getYOffset(world, pos, state) < 0.0;
    }

    private void slabbed$traceTargeting(
            float tickProgress, HitResult initialTarget, String anchoredDecision, boolean sideSlabRetargetFired
    ) {
        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null || client.player == null) {
            return;
        }

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d dir = cam.getRotationVec(tickProgress);
        double reach = 6.0;
        Vec3d end = eye.add(dir.multiply(reach));
        ItemStack held = client.player.getMainHandStack();
        RuntimeDiagnostics.logInspectClientTarget(
                world,
                eye,
                end,
                client.player.getYaw(),
                client.player.getPitch(),
                held,
                initialTarget,
                client.crosshairTarget,
                anchoredDecision,
                sideSlabRetargetFired);
        RuntimeDiagnostics.recordFenceWallClientTarget(
                world,
                cam,
                client.player,
                eye,
                end,
                held,
                initialTarget,
                client.crosshairTarget,
                anchoredDecision);
        RuntimeDiagnostics.recordSlabHeightClientTarget(
                world,
                cam,
                client.player,
                eye,
                end,
                held,
                initialTarget,
                client.crosshairTarget,
                anchoredDecision);
        slabbed$traceBeta4FinalTarget(
                tickProgress,
                initialTarget,
                anchoredDecision,
                sideSlabRetargetFired);

        if (Boolean.getBoolean("slabbed.beta4LiveRetargetRecorderEveryTick")) {
            double initialDist2 = initialTarget == null || initialTarget.getType() != HitResult.Type.BLOCK
                    ? Double.NaN
                    : initialTarget.getPos().squaredDistanceTo(eye);
            slabbed$recordBeta4LiveRetarget(
                    tickProgress,
                    initialTarget,
                    client.crosshairTarget,
                    anchoredDecision,
                    anchoredDecision,
                    null,
                    "not-run",
                    null,
                    null,
                    initialDist2,
                    Double.NaN);
        }

        if (!Boolean.getBoolean("slabbed.target.trace")) {
            return;
        }

        double vanillaDist2 = Double.POSITIVE_INFINITY;
        if (initialTarget != null && initialTarget.getType() == HitResult.Type.BLOCK) {
            vanillaDist2 = initialTarget.getPos().squaredDistanceTo(eye);
        }

        String fbCandidate = slabbed$findAnchoredFbCandidate(world, cam, eye, end, vanillaDist2);
        String slabCandidate = slabbed$findLoweredSlabCandidate(world, cam, eye, end, vanillaDist2);
        if (fbCandidate == null && slabCandidate == null) {
            return;
        }

        StringBuilder line = new StringBuilder(512);
        line.append("[slabbed.target.trace] heldItem=").append(held.getItem().getTranslationKey());
        line.append(" heldIsSlab=").append(slabbed$isSlabPlacementIntent());
        line.append(" initial=").append(slabbed$formatHit(initialTarget));
        line.append(" eye=").append(slabbed$formatVec(eye));
        line.append(" end=").append(slabbed$formatVec(end));
        line.append(" reach=").append(String.format("%.3f", reach));
        line.append(" anchoredFbDecision=").append(anchoredDecision);
        line.append(" fbCandidate=").append(fbCandidate == null ? "none" : fbCandidate);
        line.append(" sideSlabCandidate=").append(slabCandidate == null ? "none" : slabCandidate);
        line.append(" sideSlabRetargetFired=").append(sideSlabRetargetFired);
        line.append(" final=").append(slabbed$formatHit(client.crosshairTarget));
        Slabbed.LOGGER.info(line.toString());
    }

    private static boolean slabbed$beta4LiveRetargetRecorderEnabled() {
        return Boolean.getBoolean("slabbed.beta4LiveRetargetRecorder");
    }

    private static void slabbed$logBeta4LiveRetargetRecorderStart() {
        if (!slabbed$beta4LiveRetargetRecorderEnabled()
                || slabbed$beta4LiveRetargetRecorderStartLogged) {
            return;
        }
        slabbed$beta4LiveRetargetRecorderStartLogged = true;
        Slabbed.LOGGER.info("[BETA4_LIVE_RETARGET_RECORDER_START] enabled=true head=e761e67");
    }

    private static boolean slabbed$beta4ReloadJumpRecorderEnabled() {
        return Boolean.getBoolean("slabbed.beta4ReloadJumpRecorder");
    }

    private void slabbed$recordBeta4ReloadJumpRecorder(float tickProgress) {
        if (!slabbed$beta4ReloadJumpRecorderEnabled()) {
            return;
        }

        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            return;
        }

        if (world != slabbed$beta4ReloadJumpRecorderWorld) {
            slabbed$beta4ReloadJumpRecorderWorld = world;
            slabbed$beta4ReloadJumpRecorderTicksRemaining =
                    slabbed$intProperty("slabbed.beta4ReloadJumpRecorderTicks", 400);
            slabbed$beta4ReloadJumpRecorderLastWorldTick = Long.MIN_VALUE;
            slabbed$beta4ReloadJumpRecorderStartLogged = false;
        }

        int radius = slabbed$intProperty("slabbed.beta4ReloadJumpRecorderRadius", 6);
        if (!slabbed$beta4ReloadJumpRecorderStartLogged) {
            slabbed$beta4ReloadJumpRecorderStartLogged = true;
            Slabbed.LOGGER.info(
                    "[BETA4_RELOAD_JUMP_RECORDER_START] enabled=true head=e82abfb ticks={} radius={} world={}",
                    slabbed$beta4ReloadJumpRecorderTicksRemaining,
                    radius,
                    world.getRegistryKey().getValue());
        }

        if (slabbed$beta4ReloadJumpRecorderTicksRemaining <= 0) {
            return;
        }

        long worldTick = world.getTime();
        if (worldTick == slabbed$beta4ReloadJumpRecorderLastWorldTick) {
            return;
        }
        slabbed$beta4ReloadJumpRecorderLastWorldTick = worldTick;
        slabbed$beta4ReloadJumpRecorderTicksRemaining--;

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d look = cam.getRotationVec(tickProgress);
        HitResult target = client.crosshairTarget;
        BlockHitResult blockTarget = target instanceof BlockHitResult blockHit ? blockHit : null;
        BlockPos center = blockTarget == null
                ? (client.player == null ? BlockPos.ofFloored(eye) : client.player.getBlockPos())
                : blockTarget.getBlockPos();

        StringBuilder line = new StringBuilder(4096);
        line.append("[BETA4_RELOAD_JUMP_RECORDER]");
        line.append(" tick=").append(worldTick);
        line.append(" ticksRemaining=").append(slabbed$beta4ReloadJumpRecorderTicksRemaining);
        line.append(" worldPresent=true");
        line.append(" world=").append(world.getRegistryKey().getValue());
        line.append(" playerPos=").append(client.player == null
                ? "none"
                : slabbed$formatVec(new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ())));
        line.append(" eye=").append(slabbed$formatVec(eye));
        line.append(" look=").append(slabbed$formatVec(look));
        if (client.player != null) {
            line.append(" yaw=").append(String.format("%.3f", client.player.getYaw()));
            line.append(" pitch=").append(String.format("%.3f", client.player.getPitch()));
        }
        line.append(" crosshairType=").append(target == null ? "null" : target.getType());
        line.append(" outlinePos=").append(blockTarget == null ? "none" : blockTarget.getBlockPos().toShortString());
        line.append(" outlineFace=").append(blockTarget == null ? "none" : blockTarget.getSide());
        line.append(" centerMode=").append(blockTarget == null ? "playerBlock" : "crosshair");
        line.append(" centerPos=").append(center.toShortString());
        line.append(" radius=").append(radius);
        slabbed$appendSourceTruth(line, world, center, "center");
        slabbed$appendConfiguredReloadJumpWatch(line, world);
        Slabbed.LOGGER.info(line.toString());
    }

    private void slabbed$recordBeta4OutlineRecorder(float tickProgress) {
        if (!Boolean.getBoolean("slabbed.beta4OutlineRecorder")) {
            return;
        }

        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            return;
        }

        if (world != slabbed$beta4OutlineRecorderWorld) {
            slabbed$beta4OutlineRecorderWorld = world;
            slabbed$beta4OutlineRecorderTicksRemaining =
                    slabbed$intProperty("slabbed.beta4OutlineRecorderTicks", 700);
            slabbed$beta4OutlineRecorderLastWorldTick = Long.MIN_VALUE;
            slabbed$beta4OutlineRecorderStartLogged = false;
        }

        if (!slabbed$beta4OutlineRecorderStartLogged) {
            slabbed$beta4OutlineRecorderStartLogged = true;
            Slabbed.LOGGER.info(
                    "[BETA4_OUTLINE_RECORDER_START] enabled=true ticks={} world={} watch={}",
                    slabbed$beta4OutlineRecorderTicksRemaining,
                    world.getRegistryKey().getValue(),
                    System.getProperty("slabbed.beta4OutlineRecorderWatch", ""));
        }

        if (slabbed$beta4OutlineRecorderTicksRemaining <= 0) {
            return;
        }

        long worldTick = world.getTime();
        if (worldTick == slabbed$beta4OutlineRecorderLastWorldTick) {
            return;
        }
        slabbed$beta4OutlineRecorderLastWorldTick = worldTick;
        slabbed$beta4OutlineRecorderTicksRemaining--;

        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d look = cam.getRotationVec(tickProgress);
        Vec3d end = eye.add(look.multiply(6.0d));
        HitResult target = client.crosshairTarget;
        BlockHitResult blockTarget = target instanceof BlockHitResult blockHit ? blockHit : null;
        BlockPos targetPos = blockTarget == null ? null : blockTarget.getBlockPos();
        BlockState targetState = targetPos == null ? null : world.getBlockState(targetPos);

        StringBuilder line = new StringBuilder(4096);
        line.append("[BETA4_OUTLINE_RECORDER]");
        line.append(" tick=").append(worldTick);
        line.append(" ticksRemaining=").append(slabbed$beta4OutlineRecorderTicksRemaining);
        line.append(" world=").append(world.getRegistryKey().getValue());
        line.append(" eye=").append(slabbed$formatVec(eye));
        line.append(" look=").append(slabbed$formatVec(look));
        line.append(" end=").append(slabbed$formatVec(end));
        line.append(" heldItem=").append(client.player == null ? "none" : client.player.getMainHandStack().getItem());
        line.append(" crosshairTargetType=").append(target == null ? "null" : target.getType());
        line.append(" targetIsMiss=").append(target == null || target.getType() == HitResult.Type.MISS);
        line.append(" targetPos=").append(targetPos == null ? "none" : targetPos.toShortString());
        line.append(" targetFace=").append(blockTarget == null ? "none" : blockTarget.getSide());
        line.append(" targetHit=").append(blockTarget == null ? "none" : slabbed$formatVec(blockTarget.getPos()));
        line.append(" targetState=").append(targetState == null ? "none" : targetState);
        line.append(" targetIsAir=").append(targetState == null || targetState.isAir());
        if (targetPos == null || targetState == null) {
            line.append(" targetDy=NaN targetClientDy=NaN targetOutlineShape=none targetOutlineHit=none");
            line.append(" targetRaycastShape=none targetRaycastHit=none targetOwnerClass=none");
        } else {
            line.append(" targetDy=").append(slabbed$formatDouble(SlabSupport.getYOffset(world, targetPos, targetState)));
            line.append(" targetClientDy=").append(slabbed$formatDouble(ClientDy.dyFor(world, targetPos, targetState)));
            line.append(" targetOutlineShape=").append(slabbed$shapeBounds(world, cam, targetPos, targetState, true));
            line.append(" targetOutlineHit=").append(slabbed$shapeHit(world, cam, eye, end, targetPos, targetState, true));
            line.append(" targetRaycastShape=").append(slabbed$shapeBounds(world, cam, targetPos, targetState, false));
            line.append(" targetRaycastHit=").append(slabbed$shapeHit(world, cam, eye, end, targetPos, targetState, false));
            line.append(" targetOwnerClass=").append(slabbed$ownerClass(world, targetPos, targetState));
            slabbed$appendSourceTruth(line, world, targetPos, "target");
        }
        slabbed$appendConfiguredOutlineWatch(line, world, cam, eye, end);
        Slabbed.LOGGER.info(line.toString());
    }

    private static int slabbed$intProperty(String name, int fallback) {
        String raw = System.getProperty(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void slabbed$appendConfiguredOutlineWatch(
            StringBuilder line,
            ClientWorld world,
            Entity cam,
            Vec3d eye,
            Vec3d end
    ) {
        String raw = System.getProperty("slabbed.beta4OutlineRecorderWatch", "");
        if (raw.isBlank()) {
            line.append(" outlineWatch=none");
            return;
        }

        String[] entries = raw.split(";");
        int index = 0;
        for (String entry : entries) {
            BlockPos pos = slabbed$parseBlockPos(entry);
            if (pos == null) {
                line.append(" outlineWatch").append(index).append("=invalid(").append(entry.trim()).append(')');
            } else {
                BlockState state = world.getBlockState(pos);
                line.append(" outlineWatch").append(index).append("={pos=").append(pos.toShortString());
                line.append(" state=").append(state);
                line.append(" dy=").append(slabbed$formatDouble(SlabSupport.getYOffset(world, pos, state)));
                line.append(" clientDy=").append(slabbed$formatDouble(ClientDy.dyFor(world, pos, state)));
                line.append(" sourceMode=").append(slabbed$sourceMode(world, pos, state));
                line.append(" outlineShape=").append(slabbed$shapeBounds(world, cam, pos, state, true));
                line.append(" outlineHit=").append(slabbed$shapeHit(world, cam, eye, end, pos, state, true));
                line.append(" raycastShape=").append(slabbed$shapeBounds(world, cam, pos, state, false));
                line.append(" raycastHit=").append(slabbed$shapeHit(world, cam, eye, end, pos, state, false));
                line.append(" ownerClass=").append(slabbed$ownerClass(world, pos, state));
                line.append('}');
            }
            index++;
        }
    }

    private static void slabbed$appendConfiguredReloadJumpWatch(StringBuilder line, ClientWorld world) {
        String raw = System.getProperty("slabbed.beta4ReloadJumpRecorderWatch", "");
        if (raw.isBlank()) {
            line.append(" configuredWatch=none");
            return;
        }

        String[] entries = raw.split(";");
        int index = 0;
        for (String entry : entries) {
            BlockPos pos = slabbed$parseBlockPos(entry);
            if (pos == null) {
                line.append(" watch").append(index).append("=invalid(").append(entry.trim()).append(')');
            } else {
                slabbed$appendSourceTruth(line, world, pos, "watch" + index);
            }
            index++;
        }
    }

    private static BlockPos slabbed$parseBlockPos(String raw) {
        String[] parts = raw.trim().split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void slabbed$recordBeta4LiveRetarget(
            float tickProgress,
            HitResult initialTarget,
            HitResult finalTarget,
            String path,
            String classification,
            BlockHitResult sideScanCandidate,
            String sideScanCandidateReason,
            Boolean edgeLike,
            Boolean topInterior,
            double initialDist2,
            double candidateDist2
    ) {
        if (!slabbed$beta4LiveRetargetRecorderEnabled()) {
            return;
        }

        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            return;
        }

        ItemStack held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandStack();
        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d look = cam.getRotationVec(tickProgress);
        double reach = 6.0d;
        Vec3d end = eye.add(look.multiply(reach));
        double candidateMinusInitialDist2 = Double.isNaN(initialDist2) || Double.isNaN(candidateDist2)
                ? Double.NaN
                : candidateDist2 - initialDist2;
        BlockHitResult initialBlock = initialTarget instanceof BlockHitResult initial ? initial : null;
        BlockHitResult finalBlock = finalTarget instanceof BlockHitResult finalHit ? finalHit : null;
        double finalDist2 = finalTarget == null || finalTarget.getType() != HitResult.Type.BLOCK
                ? Double.NaN
                : finalTarget.getPos().squaredDistanceTo(eye);

        StringBuilder line = new StringBuilder(1536);
        line.append("[BETA4_LIVE_RETARGET_RECORDER]");
        line.append(" heldItem=").append(held.isEmpty() ? "empty" : held.getItem().getTranslationKey());
        line.append(" crosshairType=").append(finalTarget == null ? "null" : finalTarget.getType());
        slabbed$appendHitFields(line, world, initialBlock, "initial");
        slabbed$appendHitFields(line, world, finalBlock, "final");
        line.append(" path=").append(path);
        line.append(" classification=").append(classification);
        line.append(" sideScanCandidateExists=").append(sideScanCandidate != null);
        line.append(" sideScanCandidateReason=").append(sideScanCandidateReason);
        slabbed$appendHitFields(line, world, sideScanCandidate, "candidate");
        line.append(" initialDist2=").append(slabbed$formatDouble(initialDist2));
        line.append(" finalDist2=").append(slabbed$formatDouble(finalDist2));
        line.append(" candidateDist2=").append(slabbed$formatDouble(candidateDist2));
        line.append(" candidateMinusInitialDist2=").append(slabbed$formatDouble(candidateMinusInitialDist2));
        line.append(" edgeLike=").append(edgeLike == null ? "unknown" : edgeLike);
        line.append(" topInterior=").append(topInterior == null ? "unknown" : topInterior);
        line.append(" eye=").append(slabbed$formatVec(eye));
        line.append(" look=").append(slabbed$formatVec(look));
        if (client.player != null) {
            line.append(" yaw=").append(String.format("%.3f", client.player.getYaw()));
            line.append(" pitch=").append(String.format("%.3f", client.player.getPitch()));
        }
        line.append(" start=").append(slabbed$formatVec(eye));
        line.append(" end=").append(slabbed$formatVec(end));
        line.append(" reach=6.000");
        line.append(" cameraFacing=").append(cam.getHorizontalFacing());
        line.append(" outlinePos=").append(finalBlock == null ? "none" : finalBlock.getBlockPos().toShortString());
        line.append(" outlineFace=").append(finalBlock == null ? "none" : finalBlock.getSide());
        Slabbed.LOGGER.info(line.toString());
        slabbed$recordBeta4SourceTruth(
                world,
                cam,
                eye,
                look,
                end,
                held,
                initialTarget,
                finalTarget,
                path,
                classification,
                sideScanCandidate,
                sideScanCandidateReason,
                initialDist2,
                candidateDist2,
                candidateMinusInitialDist2);
    }

    private void slabbed$recordBeta4SourceTruth(
            ClientWorld world,
            Entity cam,
            Vec3d eye,
            Vec3d look,
            Vec3d end,
            ItemStack held,
            HitResult initialTarget,
            HitResult finalTarget,
            String path,
            String classification,
            BlockHitResult sideScanCandidate,
            String sideScanCandidateReason,
            double initialDist2,
            double candidateDist2,
            double candidateMinusInitialDist2
    ) {
        BlockHitResult initialBlock = initialTarget instanceof BlockHitResult initial ? initial : null;
        BlockHitResult finalBlock = finalTarget instanceof BlockHitResult finalHit ? finalHit : null;
        double vanillaDist2 = initialTarget == null || initialTarget.getType() != HitResult.Type.BLOCK
                ? Double.POSITIVE_INFINITY
                : initialTarget.getPos().squaredDistanceTo(eye);
        String fbCandidate = slabbed$findAnchoredFbCandidate(world, cam, eye, end, vanillaDist2);
        String sideSlabCandidate = slabbed$findLoweredSlabCandidate(world, cam, eye, end, vanillaDist2);

        StringBuilder line = new StringBuilder(4096);
        line.append("[BETA4_LIVE_RETARGET_SOURCE_TRUTH]");
        line.append(" heldItem=").append(held.isEmpty() ? "empty" : held.getItem().getTranslationKey());
        line.append(" heldIsSlab=").append(slabbed$isSlabPlacementIntent());
        line.append(" path=").append(path);
        line.append(" classification=").append(classification);
        line.append(" sideSlabRetargetFired=").append(finalBlock != null
                && sideScanCandidate != null
                && finalBlock.getBlockPos().equals(sideScanCandidate.getBlockPos()));
        line.append(" anchoredFbDecision=").append(path);
        line.append(" sideScanCandidateReason=").append(sideScanCandidateReason);
        line.append(" fbCandidateSummary=").append(fbCandidate == null ? "none" : fbCandidate);
        line.append(" sideSlabCandidateSummary=").append(sideSlabCandidate == null ? "none" : sideSlabCandidate);
        line.append(" crosshairTargetBefore=").append(slabbed$formatHit(initialTarget));
        line.append(" crosshairTargetAfter=").append(slabbed$formatHit(finalTarget));
        line.append(" eye=").append(slabbed$formatVec(eye));
        line.append(" look=").append(slabbed$formatVec(look));
        if (client.player != null) {
            line.append(" yaw=").append(String.format("%.3f", client.player.getYaw()));
            line.append(" pitch=").append(String.format("%.3f", client.player.getPitch()));
        }
        line.append(" start=").append(slabbed$formatVec(eye));
        line.append(" end=").append(slabbed$formatVec(end));
        line.append(" reach=6.000");
        slabbed$appendReplayChecks(line, world, cam, eye, end, initialBlock, "initial");
        slabbed$appendReplayChecks(line, world, cam, eye, end, finalBlock, "final");
        slabbed$appendReplayChecks(line, world, cam, eye, end, sideScanCandidate, "candidate");
        line.append(" initialDist2=").append(slabbed$formatDouble(initialDist2));
        line.append(" candidateDist2=").append(slabbed$formatDouble(candidateDist2));
        line.append(" candidateMinusInitialDist2=").append(slabbed$formatDouble(candidateMinusInitialDist2));
        slabbed$appendSourceTruth(line, world, initialBlock == null ? null : initialBlock.getBlockPos(), "initial");
        slabbed$appendSourceTruth(line, world, finalBlock == null ? null : finalBlock.getBlockPos(), "final");
        slabbed$appendSourceTruth(line, world, sideScanCandidate == null ? null : sideScanCandidate.getBlockPos(), "candidate");
        Slabbed.LOGGER.info(line.toString());
    }

    private static void slabbed$appendReplayChecks(
            StringBuilder line,
            ClientWorld world,
            Entity cam,
            Vec3d eye,
            Vec3d end,
            BlockHitResult hit,
            String prefix
    ) {
        line.append(' ').append(prefix).append("HitVec=").append(hit == null ? "none" : slabbed$formatVec(hit.getPos()));
        if (hit == null) {
            line.append(' ').append(prefix).append("OutlineHit=none");
            line.append(' ').append(prefix).append("RaycastHit=none");
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        line.append(' ').append(prefix).append("OutlineHit=")
                .append(slabbed$shapeHit(world, cam, eye, end, pos, state, true));
        line.append(' ').append(prefix).append("RaycastHit=")
                .append(slabbed$shapeHit(world, cam, eye, end, pos, state, false));
    }

    private static String slabbed$shapeHit(
            ClientWorld world,
            Entity cam,
            Vec3d eye,
            Vec3d end,
            BlockPos pos,
            BlockState state,
            boolean outline
    ) {
        try {
            VoxelShape shape = outline
                    ? state.getOutlineShape(world, pos, ShapeContext.of(cam))
                    : state.getRaycastShape(world, pos);
            if (shape == null || shape.isEmpty()) {
                return "miss(empty)";
            }
            BlockHitResult hit = shape.raycast(eye, end, pos);
            return hit == null ? "miss" : slabbed$formatHit(hit);
        } catch (Throwable t) {
            return "error:" + t.getClass().getSimpleName();
        }
    }

    private static String slabbed$shapeBounds(
            ClientWorld world,
            Entity cam,
            BlockPos pos,
            BlockState state,
            boolean outline
    ) {
        try {
            VoxelShape shape = outline
                    ? state.getOutlineShape(world, pos, ShapeContext.of(cam))
                    : state.getRaycastShape(world, pos);
            if (shape == null || shape.isEmpty()) {
                return "empty";
            }
            Box box = shape.getBoundingBox();
            return "min=("
                    + slabbed$formatDouble(box.minX) + ','
                    + slabbed$formatDouble(box.minY) + ','
                    + slabbed$formatDouble(box.minZ) + "),max=("
                    + slabbed$formatDouble(box.maxX) + ','
                    + slabbed$formatDouble(box.maxY) + ','
                    + slabbed$formatDouble(box.maxZ) + ')';
        } catch (Throwable t) {
            return "error:" + t.getClass().getSimpleName();
        }
    }

    private static String slabbed$sourceMode(ClientWorld world, BlockPos pos, BlockState state) {
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean persistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state);
        return persistentCarrier
                ? "persistentLoweredSlabCarrier"
                : (Math.abs(dy + 0.5d) <= 1.0e-6 ? "dynamicLoweredOrAnchored" : "normal");
    }

    private static void slabbed$appendSourceTruth(
            StringBuilder line,
            ClientWorld world,
            BlockPos pos,
            String prefix
    ) {
        line.append(' ').append(prefix).append("SourceTruth=");
        if (pos == null) {
            line.append("none");
            return;
        }
        slabbed$appendBlockTruth(line, world, pos, "self");
        slabbed$appendBlockTruth(line, world, pos.down(), "below");
        slabbed$appendBlockTruth(line, world, pos.up(), "above");
        slabbed$appendBlockTruth(line, world, pos.north(), "northNeighbor");
        slabbed$appendBlockTruth(line, world, pos.south(), "southNeighbor");
        slabbed$appendBlockTruth(line, world, pos.east(), "eastNeighbor");
        slabbed$appendBlockTruth(line, world, pos.west(), "westNeighbor");
    }

    private static void slabbed$appendBlockTruth(
            StringBuilder line,
            ClientWorld world,
            BlockPos pos,
            String label
    ) {
        BlockState state = world.getBlockState(pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        boolean persistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state);
        boolean bottomCarrier = SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state);
        boolean lowered = Math.abs(dy + 0.5d) <= 1.0e-6;
        boolean slab = state.getBlock() instanceof SlabBlock;
        String slabType = state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE).asString() : "none";
        String sourceMode = persistentCarrier
                ? "persistentLoweredSlabCarrier"
                : (lowered ? "dynamicLoweredOrAnchored" : "normal");
        line.append(' ').append(label).append("={pos=").append(pos.toShortString())
                .append(" state=").append(state)
                .append(" dy=").append(slabbed$formatDouble(dy))
                .append(" anchored=").append(anchored)
                .append(" persistentFullBlockAnchor=").append(anchored && !slab)
                .append(" persistentLoweredSlabCarrier=").append(persistentCarrier)
                .append(" persistentLoweredBottomSlabCarrier=").append(bottomCarrier)
                .append(" lowered=").append(lowered)
                .append(" slabType=").append(slabType)
                .append(" solid=").append(state.isSolidBlock(world, pos))
                .append(" sourceMode=").append(sourceMode)
                .append('}');
    }

    private static void slabbed$appendHitFields(
            StringBuilder line, ClientWorld world, BlockHitResult hit, String prefix
    ) {
        if (hit == null) {
            line.append(' ').append(prefix).append("Type=none");
            line.append(' ').append(prefix).append("Pos=none");
            line.append(' ').append(prefix).append("Face=none");
            line.append(' ').append(prefix).append("Hit=none");
            line.append(' ').append(prefix).append("State=none");
            line.append(' ').append(prefix).append("Dy=NaN");
            if ("initial".equals(prefix)) {
                line.append(" initialAnchored=false");
                line.append(" initialLowered=false");
            }
            line.append(' ').append(prefix).append("OwnerClass=none");
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        boolean lowered = dy == -0.5;
        line.append(' ').append(prefix).append("Type=").append(hit.getType());
        line.append(' ').append(prefix).append("Pos=").append(pos.toShortString());
        line.append(' ').append(prefix).append("Face=").append(hit.getSide());
        line.append(' ').append(prefix).append("Hit=").append(slabbed$formatVec(hit.getPos()));
        line.append(' ').append(prefix).append("State=").append(state);
        line.append(' ').append(prefix).append("Dy=").append(slabbed$formatDouble(dy));
        if ("initial".equals(prefix)) {
            line.append(" initialAnchored=").append(anchored);
            line.append(" initialLowered=").append(lowered);
        }
        line.append(' ').append(prefix).append("OwnerClass=")
                .append(slabbed$ownerClass(world, pos, state));
    }

    private static String slabbed$ownerClass(ClientWorld world, BlockPos pos, BlockState state) {
        if (slabbed$isVisibleUpperLoweredSlabOwner(world, pos, state)) {
            return SEAM_OWNER_VISIBLE_UPPER_LOWERED_SLAB;
        }
        if (slabbed$isAnchoredLoweredFullBlock(world, pos, state)) {
            return SEAM_OWNER_ANCHORED_FULL_BLOCK;
        }
        if (slabbed$isLoweredBottomSlabVisibleOwner(world, pos, state)
                && slabbed$hasAdjacentAnchoredLoweredFullBlock(world, pos)) {
            return SEAM_OWNER_ADJACENT_VISIBLE_TARGET;
        }
        if (state.isAir()) {
            return "AIR";
        }
        return SEAM_OWNER_KEEP_INITIAL;
    }

    private void slabbed$traceBeta4FinalTarget(
            float tickProgress, HitResult initialTarget, String classification, boolean sideSlabRetargetFired
    ) {
        if (!Boolean.getBoolean("slabbed.beta4.finalTargetTrace")) {
            return;
        }

        ClientWorld world = client.world;
        Entity cam = client.getCameraEntity();
        if (world == null || cam == null) {
            return;
        }

        HitResult finalTarget = client.crosshairTarget;
        if (finalTarget == null) {
            return;
        }

        long now = System.nanoTime();
        String signature = slabbed$beta4FinalTargetSignature(world, finalTarget, classification, sideSlabRetargetFired);
        if (signature.equals(slabbed$beta4FinalTargetTraceLastSignature)
                && now - slabbed$beta4FinalTargetTraceLastLogNanos < BETA4_FINAL_TARGET_TRACE_MIN_INTERVAL_NANOS) {
            return;
        }

        slabbed$beta4FinalTargetTraceLastSignature = signature;
        slabbed$beta4FinalTargetTraceLastLogNanos = now;

        ItemStack held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandStack();
        Vec3d eye = cam.getCameraPosVec(tickProgress);
        Vec3d look = cam.getRotationVec(tickProgress);
        StringBuilder line = new StringBuilder(768);
        line.append("[BETA4_FINAL_TARGET]");
        line.append(" heldItem=").append(held.isEmpty() ? "empty" : held.getItem().getTranslationKey());
        line.append(" cameraPos=").append(slabbed$formatVec(eye));
        if (client.player != null) {
            line.append(" playerPos=").append(slabbed$formatVec(new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ())));
            line.append(" yaw=").append(String.format("%.3f", client.player.getYaw()));
            line.append(" pitch=").append(String.format("%.3f", client.player.getPitch()));
        }
        line.append(" lookVec=").append(slabbed$formatVec(look));
        line.append(" capturedInitialTarget=").append(slabbed$formatHit(initialTarget));
        line.append(" finalCrosshairTarget=").append(slabbed$formatHit(finalTarget));

        if (finalTarget instanceof BlockHitResult finalHit) {
            BlockPos pos = finalHit.getBlockPos();
            BlockState state = world.getBlockState(pos);
            double targetDy = SlabSupport.getYOffset(world, pos, state);
            boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
            boolean persistentLoweredSlabCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state);
            line.append(" targetState=").append(state);
            line.append(" targetDy=").append(String.format("%.3f", targetDy));
            line.append(" anchored=").append(anchored);
            line.append(" persistentLoweredSlabCarrier=").append(persistentLoweredSlabCarrier);
            line.append(" outlineOwner=").append(pos.toShortString());
            line.append(" outlineOwnerShape=").append(slabbed$formatOutlineOwnerShape(world, cam, pos, state));
        } else {
            line.append(" targetState=none");
            line.append(" targetDy=NaN");
            line.append(" anchored=false");
            line.append(" persistentLoweredSlabCarrier=false");
            line.append(" outlineOwner=none");
            line.append(" outlineOwnerShape=none");
        }

        line.append(" classification=").append(classification);
        line.append(" sideSlabRetargetFired=").append(sideSlabRetargetFired);
        Slabbed.LOGGER.info(line.toString());
    }

    private String slabbed$beta4FinalTargetSignature(
            ClientWorld world, HitResult finalTarget, String classification, boolean sideSlabRetargetFired
    ) {
        StringBuilder signature = new StringBuilder(256);
        signature.append(finalTarget.getType());
        signature.append('|').append(classification);
        signature.append('|').append(sideSlabRetargetFired);
        if (finalTarget instanceof BlockHitResult finalHit) {
            BlockPos pos = finalHit.getBlockPos();
            BlockState state = world.getBlockState(pos);
            signature.append('|').append(pos.toShortString());
            signature.append('|').append(finalHit.getSide());
            signature.append('|').append(state.getBlock());
            signature.append('|').append(String.format("%.3f", SlabSupport.getYOffset(world, pos, state)));
            signature.append('|').append(SlabAnchorAttachment.isAnchored(world, pos));
            signature.append('|').append(SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state));
        }
        return signature.toString();
    }

    private static String slabbed$formatOutlineOwnerShape(ClientWorld world, Entity cam, BlockPos pos, BlockState state) {
        try {
            VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.of(cam));
            if (outline == null || outline.isEmpty()) {
                return "empty";
            }
            return "bounds=" + outline.getBoundingBox();
        } catch (Throwable t) {
            return "error:" + t.getClass().getSimpleName();
        }
    }

    private static String slabbed$findAnchoredFbCandidate(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, double vanillaDist2
    ) {
        Vec3d dir = end.subtract(eye).normalize();
        double reach = end.distanceTo(eye);
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05));
        for (int i = 1; i <= steps; i++) {
            Vec3d sample = eye.add(dir.multiply(reach * i / steps));
            String candidate = slabbed$anchoredFbCandidateAt(world, cam, eye, end, BlockPos.ofFloored(sample), vanillaDist2);
            if (candidate != null) {
                return candidate;
            }
            candidate = slabbed$anchoredFbCandidateAt(world, cam, eye, end, BlockPos.ofFloored(sample).up(), vanillaDist2);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String slabbed$findLoweredSlabCandidate(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, double vanillaDist2
    ) {
        Vec3d dir = end.subtract(eye).normalize();
        double reach = end.distanceTo(eye);
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05));
        for (int i = 1; i <= steps; i++) {
            Vec3d sample = eye.add(dir.multiply(reach * i / steps));
            String candidate = slabbed$loweredSlabCandidateAt(world, cam, eye, end, BlockPos.ofFloored(sample), vanillaDist2);
            if (candidate != null) {
                return candidate;
            }
            candidate = slabbed$loweredSlabCandidateAt(world, cam, eye, end, BlockPos.ofFloored(sample).up(), vanillaDist2);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String slabbed$anchoredFbCandidateAt(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, BlockPos pos, double vanillaDist2
    ) {
        BlockState state = world.getBlockState(pos);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean solid = state.isSolidBlock(world, pos);
        if (!solid || !anchored || dy != -0.5) {
            return null;
        }

        net.minecraft.block.Block block = state.getBlock();
        if (block instanceof BlockEntityProvider || block instanceof CraftingTableBlock) {
            return null;
        }

        BlockHitResult outlineHit = state.getOutlineShape(world, pos, ShapeContext.of(cam)).raycast(eye, end, pos);
        if (outlineHit == null) {
            return "anchoredFB{pos=" + pos.toShortString()
                    + " state=" + state
                    + " dy=" + String.format("%.3f", dy)
                    + " anchored=" + anchored
                    + " solid=" + solid
                    + " outline=miss raycast=miss reason=outline-shape-miss}";
        }

        double outlineDist2 = outlineHit.getPos().squaredDistanceTo(eye);
        String reason = outlineDist2 > vanillaDist2 + 1.0e-6
                ? "candidate-farther-than-vanilla-hit"
                : "eligible";
        return "anchoredFB{pos=" + pos.toShortString()
                + " state=" + state
                + " dy=" + String.format("%.3f", dy)
                + " anchored=" + anchored
                + " solid=" + solid
                + " outline=" + slabbed$formatHit(outlineHit)
                + " raycast=" + slabbed$formatHit(state.getRaycastShape(world, pos).raycast(eye, end, pos))
                + " reason=" + reason
                + "}";
    }

    private static String slabbed$loweredSlabCandidateAt(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, BlockPos pos, double vanillaDist2
    ) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof SlabBlock)) {
            return null;
        }

        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean bottom = state.contains(SlabBlock.TYPE) && state.get(SlabBlock.TYPE) == SlabType.BOTTOM;
        if (!bottom || dy != -0.5) {
            return null;
        }

        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        boolean solid = state.isSolidBlock(world, pos);
        BlockHitResult outlineHit = state.getOutlineShape(world, pos, ShapeContext.of(cam)).raycast(eye, end, pos);
        if (outlineHit == null) {
            return "loweredSideSlab{pos=" + pos.toShortString()
                    + " state=" + state
                    + " dy=" + String.format("%.3f", dy)
                    + " anchored=" + anchored
                    + " solid=" + solid
                    + " outline=miss raycast=miss reason=outline-shape-miss}";
        }

        double outlineDist2 = outlineHit.getPos().squaredDistanceTo(eye);
        String reason = outlineDist2 > vanillaDist2 + 1.0e-6
                ? "candidate-farther-than-vanilla-hit"
                : "eligible";
        return "loweredSideSlab{pos=" + pos.toShortString()
                + " state=" + state
                + " dy=" + String.format("%.3f", dy)
                + " anchored=" + anchored
                + " solid=" + solid
                + " outline=" + slabbed$formatHit(outlineHit)
                + " raycast=" + slabbed$formatHit(state.getRaycastShape(world, pos).raycast(eye, end, pos))
                + " reason=" + reason
                + "}";
    }

    private static String slabbed$formatHit(HitResult hit) {
        if (hit == null) {
            return "miss";
        }
        if (hit instanceof BlockHitResult blockHit) {
            return slabbed$formatHit(blockHit);
        }
        return "type=" + hit.getType() + " hit=" + slabbed$formatVec(hit.getPos());
    }

    private static String slabbed$formatHit(BlockHitResult hit) {
        if (hit == null) {
            return "miss";
        }
        return "hit=true pos=" + hit.getBlockPos().toShortString()
                + " side=" + hit.getSide()
                + " hitVec=" + slabbed$formatVec(hit.getPos());
    }

    private static String slabbed$formatVec(Vec3d vec) {
        if (vec == null) {
            return "null";
        }
        return String.format("%.3f,%.3f,%.3f", vec.x, vec.y, vec.z);
    }

    private static String slabbed$formatDouble(double value) {
        return Double.isNaN(value) ? "NaN" : String.format("%.6f", value);
    }
}
