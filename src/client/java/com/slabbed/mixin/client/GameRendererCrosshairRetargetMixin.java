package com.slabbed.mixin.client;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.client.runtime.LoweredSideSlabRetargeter;
import com.slabbed.util.LiveCursorIntentRecorder;
import com.slabbed.util.PlacementIntentState;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;

/**
 * <p>Client-side raycast retarget for lowered owners placed on bottom slabs.
 *
 * <p>When the lowered block's visible lower half extends into {@code pos.below()}'s
 * voxel, vanilla per-voxel DDA raycast hits the slab below before it can
 * consider the offset shape at {@code pos}. After vanilla
 * {@code Minecraft.pick(float)} has resolved the crosshair,
 * we re-test the ray against the owning block above (if it qualifies per
 * the lowered-owner helpers in {@link SlabSupport}) and, if the ray hits
 * its offset shape at an equal or closer distance, we replace
 * {@link Minecraft#hitResult} with that result.
 *
 * <p>The shape tested is the block's <em>outline</em> shape, using the
 * camera entity's {@link CollisionContext}. This mirrors vanilla crosshair
 * targeting which uses {@code RaycastContext.ShapeType.OUTLINE}; using
 * the raycast shape instead would silently miss blocks (chests, barrels,
 * signs, etc.) whose {@code getRaycastShape} falls back to empty.
 *
 * <p>This retarget is the single ownership rule; the outline renderer
 * automatically follows because it reads {@code hitResult}.
 */
@Mixin(Minecraft.class)
public abstract class GameRendererCrosshairRetargetMixin {

    @Unique
    private Minecraft slabbed$self() {
        return (Minecraft) (Object) this;
    }

    private static final long BETA4_FINAL_TARGET_TRACE_MIN_INTERVAL_NANOS = 1_000_000_000L;
    private static final double BETA35_VISIBLE_OWNER_SUPPORT_OVERRUN = 0.75d;
    private static String slabbed$beta4FinalTargetTraceLastSignature;
    private static long slabbed$beta4FinalTargetTraceLastLogNanos;
    private static boolean slabbed$beta4LiveRetargetRecorderStartLogged;
    private static boolean slabbed$beta4ReloadJumpRecorderStartLogged;
    private static ClientLevel slabbed$beta4ReloadJumpRecorderWorld;
    private static int slabbed$beta4ReloadJumpRecorderTicksRemaining;
    private static long slabbed$beta4ReloadJumpRecorderLastWorldTick = Long.MIN_VALUE;
    private static boolean slabbed$beta4OutlineRecorderStartLogged;
    private static ClientLevel slabbed$beta4OutlineRecorderWorld;
    private static int slabbed$beta4OutlineRecorderTicksRemaining;
    private static long slabbed$beta4OutlineRecorderLastWorldTick = Long.MIN_VALUE;

    private static final String SEAM_OWNER_ANCHORED_FULL_BLOCK = "ANCHORED_FULL_BLOCK";
    private static final String SEAM_OWNER_VISIBLE_UPPER_LOWERED_SLAB = "VISIBLE_UPPER_LOWERED_SLAB";
    private static final String SEAM_OWNER_ADJACENT_VISIBLE_TARGET = "ADJACENT_VISIBLE_TARGET";
    private static final String SEAM_OWNER_KEEP_INITIAL = "KEEP_INITIAL";
    private static final String SEAM_OWNER_NO_RESCUE = "NO_RESCUE";

    @Inject(method = "pick", at = @At("TAIL"))
    private void slabbed$retargetLoweredBlockEntity(float tickProgress, CallbackInfo ci) {
        slabbed$logBeta4LiveRetargetRecorderStart();
        slabbed$recordBeta4ReloadJumpRecorder(tickProgress);
        slabbed$recordBeta4OutlineRecorder(tickProgress);
        HitResult ht = this.slabbed$self().hitResult;
        PlacementIntentState.onPickStart(
                slabbed$placementIntentProducerDetails(ht, null, "pick-start"));
        slabbed$auditProducerEval(ht, null, "pick-start");
        if (ht == null) {
            PlacementIntentState.auditBoundary("PICK_BOUNDARY", "NO_BLOCK_HIT",
                    slabbed$placementIntentProducerDetails(null, null, "no-block-hit"));
            slabbed$auditProducerSkip(null, null, "SKIP_NO_BLOCK_HIT", "no-block-hit");
            return;
        }
        HitResult initialTarget = ht;
        boolean slabHeld = slabbed$isSlabPlacementIntent();
        BlockHitResult objectOwner = slabbed$retargetLoweredObjectShapeOwner(tickProgress, ht);
        if (objectOwner != null) {
            BlockHitResult anchoredOwner = slabbed$retargetAnchoredLoweredFullBlock(tickProgress, ht);
            BlockHitResult chosenOwner = slabbed$isCloserOrTied(tickProgress, anchoredOwner, objectOwner)
                    ? anchoredOwner
                    : objectOwner;
            String decision = chosenOwner == anchoredOwner
                    ? "anchored-fb-before-object-owner-preserve"
                    : "object-shape-owner-preserve";
            slabbed$setHitResultWithPlacementIntent(tickProgress, initialTarget, chosenOwner, decision);
            slabbed$traceTargeting(tickProgress, initialTarget, decision, false);
            return;
        }

        // Slab-held intent guard: when vanilla's initial crosshair already
        // sits on an intended placement target (anchored/lowered full-block
        // UP hit, lowered slab face, or lowered full-block placement-intent
        // face), preserve that face and do not let DDA scans steal ownership.
        if (slabHeld && ht instanceof BlockHitResult initialHit) {
            ClientLevel world = this.slabbed$self().level;
            BlockPos pos = initialHit.getBlockPos();
            if (world != null
                    && initialHit.getDirection() == Direction.UP
                    && slabbed$isAnchoredLoweredFullBlock(world, pos, world.getBlockState(pos))) {
                BlockHitResult anchoredOwner = slabbed$retargetAnchoredLoweredFullBlock(tickProgress, ht);
                if (slabbed$isDistinctOwner(initialHit, anchoredOwner)) {
                    slabbed$setHitResultWithPlacementIntent(tickProgress, initialTarget, anchoredOwner,
                            "scan-anchored-fb-fired-slab-held");
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
                    slabbed$setHitResultWithPlacementIntent(tickProgress, initialTarget, sideOwner,
                            slabbed$beta35VisibleOwnerDecision(originalSideOwner, sideOwner, "scan-side-slab-fired"));
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
                slabbed$auditProducerSkip(initialTarget, initialHit, "SKIP_OWNER_UNCHANGED",
                        "scan-skip-slab-held-anchored-lowered-full-block-up");
                return;
            }
            if (slabbed$isInitialHitOnLoweredSlabFace(initialHit)) {
                String seamOwner = slabbed$classifyLiveFirstSeamOwner(initialHit);
                if (SEAM_OWNER_VISIBLE_UPPER_LOWERED_SLAB.equals(seamOwner)
                        || SEAM_OWNER_ADJACENT_VISIBLE_TARGET.equals(seamOwner)) {
                    slabbed$setHitResultWithPlacementIntent(tickProgress, initialTarget, initialHit,
                            slabbed$seamOwnerDecision(seamOwner));
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
                    slabbed$setHitResultWithPlacementIntent(tickProgress, initialTarget, aboveAngleOwner,
                            "aboveAngleLowerFrontPreserve");
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
                        slabbed$setHitResultWithPlacementIntent(tickProgress, initialTarget, anchoredOwner,
                                "scan-anchored-fb-fired-slab-held");
                        slabbed$traceTargeting(tickProgress, initialTarget, "scan-anchored-fb-fired-slab-held", false);
                        return;
                    }
                    slabbed$setHitResultWithPlacementIntent(tickProgress, initialTarget, sideOwner,
                            slabbed$beta35VisibleOwnerDecision(originalSideOwner, sideOwner, "scan-side-slab-fired"));
                    slabbed$traceTargeting(
                            tickProgress,
                            initialTarget,
                            slabbed$beta35VisibleOwnerDecision(originalSideOwner, sideOwner, "scan-side-slab-fired"),
                            !slabbed$beta35VisibleOwnerPreserved(originalSideOwner, sideOwner));
                    return;
                }
                if (slabbed$isDistinctOwner(initialHit, anchoredOwner)) {
                    slabbed$setHitResultWithPlacementIntent(tickProgress, initialTarget, anchoredOwner,
                            "scan-anchored-fb-fired-slab-held");
                    slabbed$traceTargeting(tickProgress, initialTarget, "scan-anchored-fb-fired-slab-held", false);
                    return;
                }
                BlockHitResult visibleOwner = slabbed$preserveBeta35VisibleOwnerBeforeSupport(tickProgress, initialHit);
                if (slabbed$beta35VisibleOwnerPreserved(initialHit, visibleOwner)) {
                    slabbed$setHitResultWithPlacementIntent(tickProgress, initialTarget, visibleOwner,
                            slabbed$beta35VisibleOwnerDecision(initialHit, visibleOwner, "visible-object-owner-preserve"));
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
                slabbed$auditProducerSkip(initialTarget, initialHit, "SKIP_OWNER_UNCHANGED",
                        "scan-skip-initial-already-lowered-slab-face");
                return;
            }
            if (slabbed$isInitialHitOnLoweredFullBlockPlacementIntent(initialHit)) {
                slabbed$traceTargeting(
                        tickProgress,
                        initialTarget,
                        "scan-skip-initial-already-lowered-full-block-placement-intent",
                        false);
                slabbed$auditProducerSkip(initialTarget, initialHit, "SKIP_OWNER_UNCHANGED",
                        "scan-skip-initial-already-lowered-full-block-placement-intent");
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
            slabbed$setHitResultWithPlacementIntent(tickProgress, initialTarget, chosen, decision);
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
                    slabbed$setHitResultWithPlacementIntent(tickProgress, initialTarget, sideOwner,
                            slabbed$beta35VisibleOwnerDecision(originalSideOwner, sideOwner, "scan-side-slab-fired"));
                    slabbed$traceTargeting(
                            tickProgress,
                            initialTarget,
                            slabbed$beta35VisibleOwnerDecision(originalSideOwner, sideOwner, "scan-side-slab-fired"),
                            !slabbed$beta35VisibleOwnerPreserved(originalSideOwner, sideOwner));
                    return;
                }
                BlockHitResult blockBackedMissOwner = slabbed$preserveBlockBackedTopSlabMiss(tickProgress, initialTarget);
                if (blockBackedMissOwner != null) {
                    slabbed$setHitResultWithPlacementIntent(tickProgress, initialTarget, blockBackedMissOwner,
                            "miss-block-backed-top-slab-preserve");
                    slabbed$traceTargeting(tickProgress, initialTarget,
                            "miss-block-backed-top-slab-preserve", false);
                    return;
                }
            }
            slabbed$traceTargeting(tickProgress, initialTarget, "scan-no-rescue-candidate", false);
            slabbed$auditProducerSkip(initialTarget, null, "SKIP_FINAL_TARGET_UNKNOWN",
                    "miss-no-rescue-candidate");
            return;
        }
        if (ht.getType() != HitResult.Type.BLOCK) {
            slabbed$traceTargeting(tickProgress, initialTarget, "scan-no-rescue-candidate", false);
            slabbed$auditProducerSkip(initialTarget, null, "SKIP_NO_BLOCK_HIT",
                    "scan-no-rescue-candidate-non-block");
            return;
        }
        if (!(ht instanceof BlockHitResult slabHit)) {
            slabbed$traceTargeting(tickProgress, initialTarget, "scan-no-rescue-candidate", false);
            slabbed$auditProducerSkip(initialTarget, null, "SKIP_NO_BLOCK_HIT",
                    "scan-no-rescue-candidate-not-block-hit-result");
            return;
        }

        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
        if (world == null || cam == null) {
            slabbed$auditProducerSkip(initialTarget, null, "SKIP_CONTEXT_NOT_CLIENT_RELEVANT",
                    "world-or-camera-null");
            return;
        }

        BlockPos abovePos = slabHit.getBlockPos().above();
        BlockState aboveState = world.getBlockState(abovePos);
        boolean loweredOwner =
                SlabSupport.isLoweredBlockEntityVisual(world, abovePos, aboveState)
                        || SlabSupport.isLoweredTorchVisual(world, abovePos, aboveState)
                        || SlabSupport.isLoweredBedVisual(world, abovePos, aboveState);
        if (!loweredOwner) {
            // Ordinary solid full blocks have an unambiguous owner signature:
            // a lowered full-cube outline directly above the slab hit. Keep
            // crafting tables out of this pass because they remain a no-go.
            net.minecraft.world.level.block.Block block = aboveState.getBlock();
            loweredOwner = aboveState.isSolidRender()
                    && !(block instanceof net.minecraft.world.level.block.EntityBlock)
                    && !(block instanceof net.minecraft.world.level.block.CraftingTableBlock)
                    && SlabSupport.getYOffset(world, abovePos, aboveState) == -0.5;
        }
        if (!loweredOwner) {
            BlockHitResult visibleOwner = slabbed$preserveBeta35VisibleOwnerBeforeSupport(tickProgress, slabHit);
            if (slabbed$beta35VisibleOwnerPreserved(slabHit, visibleOwner)) {
                slabbed$setHitResultWithPlacementIntent(tickProgress, initialTarget, visibleOwner,
                        slabbed$beta35VisibleOwnerDecision(slabHit, visibleOwner, "visible-object-owner-preserve"));
                slabbed$traceTargeting(tickProgress, initialTarget,
                        slabbed$beta35VisibleOwnerDecision(slabHit, visibleOwner, "visible-object-owner-preserve"),
                        false);
                return;
            }
            slabbed$recordFinalTargetUnknownExpectedPlaceKnownState(tickProgress, initialTarget, slabHit,
                    "scan-no-rescue-candidate;legacy-above-target-not-lowered-owner");
            slabbed$traceTargeting(tickProgress, initialTarget,
                    "scan-no-rescue-candidate;legacy-above-target-not-lowered-owner", false);
            slabbed$auditProducerSkip(initialTarget, null, "SKIP_FINAL_TARGET_UNKNOWN",
                    "scan-no-rescue-candidate;legacy-above-target-not-lowered-owner");
            return;
        }

        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 slabHitPos = slabHit.getLocation();
        Vec3 dir = slabHitPos.subtract(eye);
        double slabDist = dir.length();
        if (slabDist <= 0.0) {
            slabbed$traceTargeting(tickProgress, initialTarget,
                    "scan-no-rescue-candidate;legacy-slab-distance-zero", false);
            slabbed$auditProducerSkip(initialTarget, null, "SKIP_EXPECTED_PLACE_UNKNOWN",
                    "scan-no-rescue-candidate;legacy-slab-distance-zero");
            return;
        }
        // Extend slightly past the original hit so shape.raycast can intersect
        // the chest's offset front face which may be marginally further along
        // the ray than the slab-top intersection.
        Vec3 end = eye.add(dir.normalize().scale(slabDist + 0.5));

        // Mirror vanilla crosshair ownership: crosshair targeting uses
        // RaycastContext.ShapeType.OUTLINE, which resolves to getOutlineShape
        // with the camera entity's CollisionContext. Blocks whose native
        // getRaycastShape is empty (most EntityBlock blocks) would
        // otherwise never retarget.
        VoxelShape shape = aboveState.getShape(world, abovePos, CollisionContext.of(cam));
        BlockHitResult chestHit = shape.clip(eye, end, abovePos);
        if (chestHit == null) {
            slabbed$traceTargeting(tickProgress, initialTarget,
                    "scan-no-rescue-candidate;legacy-shape-miss", false);
            slabbed$auditProducerSkip(initialTarget, null, "SKIP_FINAL_TARGET_UNKNOWN",
                    "scan-no-rescue-candidate;legacy-shape-miss");
            return;
        }
        double chestDist2 = chestHit.getLocation().distanceToSqr(eye);
        double slabDist2 = slabHitPos.distanceToSqr(eye);
        // Only retarget when the chest's offset shape is actually closer or
        // coincident with the slab hit — this is the overflow signature.
        if (chestDist2 > slabDist2 + 1.0e-6) {
            slabbed$traceTargeting(tickProgress, initialTarget,
                    "scan-no-rescue-candidate;legacy-candidate-farther-than-vanilla-hit", false);
            slabbed$auditProducerSkip(initialTarget, chestHit, "SKIP_NOT_PLACEMENT_RELEVANT",
                    "scan-no-rescue-candidate;legacy-candidate-farther-than-vanilla-hit");
            return;
        }

        slabbed$setHitResultWithPlacementIntent(tickProgress, initialTarget, chestHit,
                "scan-no-rescue-candidate;legacy-retarget-fired");
        slabbed$traceTargeting(tickProgress, initialTarget,
                "scan-no-rescue-candidate;legacy-retarget-fired", false);
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
        Entity cam = this.slabbed$self().getCameraEntity();
        if (cam == null) {
            return slabHeld ? slab : anchored;
        }
        Vec3 eye = cam.getEyePosition(tickProgress);
        double anchoredDist2 = anchored.getLocation().distanceToSqr(eye);
        double slabDist2 = slab.getLocation().distanceToSqr(eye);
        final double eps = 1.0e-6;
        if (slabHeld) {
            return (slabDist2 <= anchoredDist2 + eps) ? slab : anchored;
        }
        return (anchoredDist2 <= slabDist2 + eps) ? anchored : slab;
    }

    private static boolean slabbed$isDistinctOwner(BlockHitResult initialHit, BlockHitResult candidate) {
        return candidate != null && !candidate.getBlockPos().equals(initialHit.getBlockPos());
    }

    private BlockHitResult slabbed$retargetLoweredObjectShapeOwner(float tickProgress, HitResult currentHit) {
        if (currentHit == null || currentHit.getType() == HitResult.Type.ENTITY) {
            return null;
        }

        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 end;
        double currentDist2 = Double.POSITIVE_INFINITY;
        if (currentHit instanceof BlockHitResult blockHit && currentHit.getType() == HitResult.Type.BLOCK) {
            Vec3 currentPos = blockHit.getLocation();
            Vec3 dir = currentPos.subtract(eye);
            double currentDist = dir.length();
            if (currentDist <= 0.0d) {
                return null;
            }
            end = eye.add(dir.normalize().scale(currentDist + 0.5d));
            currentDist2 = currentPos.distanceToSqr(eye);

            BlockPos initialPos = blockHit.getBlockPos();
            BlockHitResult directHit = slabbed$raycastLoweredObjectShapeOwner(
                    world, cam, eye, end, initialPos, currentDist2);
            if (directHit != null) {
                return directHit;
            }

            directHit = slabbed$raycastLoweredObjectShapeOwner(
                    world, cam, eye, end, initialPos.above(), currentDist2);
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
                        initialPos.above(),
                        currentPos);
                if (directHit != null) {
                    return directHit;
                }
            }
        } else {
            end = eye.add(cam.getViewVector(tickProgress).scale(6.0d));
        }

        int steps = Math.max(16, (int) Math.ceil(6.0d / 0.05d));
        BlockHitResult bestHit = null;
        double bestDist2 = currentDist2;
        for (int i = 1; i <= steps; i++) {
            double t = 6.0d * i / steps;
            if (t * t > bestDist2 + 1.0e-6d) {
                break;
            }
            Vec3 sample = eye.add(cam.getViewVector(tickProgress).scale(t));
            BlockPos samplePos = BlockPos.containing(sample);

            BlockHitResult hit = slabbed$raycastLoweredObjectShapeOwner(
                    world, cam, eye, end, samplePos, bestDist2);
            if (hit != null) {
                bestHit = hit;
                bestDist2 = hit.getLocation().distanceToSqr(eye);
            }

            hit = slabbed$raycastLoweredObjectShapeOwner(
                    world, cam, eye, end, samplePos.above(), bestDist2);
            if (hit != null) {
                bestHit = hit;
                bestDist2 = hit.getLocation().distanceToSqr(eye);
            }
        }

        if (bestHit != null) {
            return bestHit;
        }
        return slabbed$findBeta35HitboxOwnerObject(world, cam, eye, end);
    }

    private static BlockHitResult slabbed$raycastLoweredObjectShapeOwner(
            ClientLevel world, Entity cam, Vec3 eye, Vec3 end, BlockPos pos, double currentDist2
    ) {
        BlockState state = world.getBlockState(pos);
        if (!slabbed$isLoweredObjectShapeOwner(world, pos, state)) {
            return null;
        }

        VoxelShape outline = state.getShape(world, pos, CollisionContext.of(cam));
        if (outline == null || outline.isEmpty()) {
            return null;
        }
        BlockHitResult hit = outline.clip(eye, end, pos);
        if (hit == null) {
            return null;
        }
        return hit.getLocation().distanceToSqr(eye) <= currentDist2 + 1.0e-6d ? hit : null;
    }

    private static BlockHitResult slabbed$findBeta35HitboxOwnerObject(
            ClientLevel world, Entity cam, Vec3 eye, Vec3 end
    ) {
        int steps = Math.max(16, (int) Math.ceil(6.0d / 0.05d));
        BlockHitResult bestHit = null;
        double bestDist2 = Double.POSITIVE_INFINITY;
        Vec3 ray = end.subtract(eye);
        for (int i = 1; i <= steps; i++) {
            Vec3 sample = eye.add(ray.scale((double) i / steps));
            BlockPos samplePos = BlockPos.containing(sample);

            BlockHitResult hit = slabbed$raycastBeta35HitboxOwnerObject(world, cam, eye, end, samplePos, bestDist2);
            if (hit != null) {
                bestHit = hit;
                bestDist2 = hit.getLocation().distanceToSqr(eye);
            }

            hit = slabbed$raycastBeta35HitboxOwnerObject(world, cam, eye, end, samplePos.above(), bestDist2);
            if (hit != null) {
                bestHit = hit;
                bestDist2 = hit.getLocation().distanceToSqr(eye);
            }

            hit = slabbed$raycastBeta35HitboxOwnerObject(world, cam, eye, end, samplePos.above(2), bestDist2);
            if (hit != null) {
                bestHit = hit;
                bestDist2 = hit.getLocation().distanceToSqr(eye);
            }
        }
        return bestHit;
    }

    private static BlockHitResult slabbed$raycastBeta35HitboxOwnerObject(
            ClientLevel world, Entity cam, Vec3 eye, Vec3 end, BlockPos pos, double bestDist2
    ) {
        BlockState state = world.getBlockState(pos);
        if (!slabbed$isBeta35HitboxOwnerObject(world, pos, state)) {
            return null;
        }

        VoxelShape outline = state.getShape(world, pos, CollisionContext.of(cam));
        if (outline == null || outline.isEmpty()) {
            return null;
        }
        BlockHitResult hit = outline.clip(eye, end, pos);
        if (hit == null) {
            return null;
        }
        return hit.getLocation().distanceToSqr(eye) <= bestDist2 + 1.0e-6d ? hit : null;
    }

    private static BlockHitResult slabbed$raycastBeta35VisibleOwnerBehindSupport(
            ClientLevel world,
            Entity cam,
            Vec3 eye,
            Vec3 end,
            BlockPos pos,
            Vec3 supportHit
    ) {
        BlockState state = world.getBlockState(pos);
        if (!SlabSupport.isBeta35SlabHeightVisibleOwnerObject(world, pos, state)) {
            return null;
        }

        VoxelShape outline = state.getShape(world, pos, CollisionContext.of(cam));
        if (outline == null || outline.isEmpty()) {
            return null;
        }
        BlockHitResult hit = outline.clip(eye, end, pos);
        if (hit == null) {
            return null;
        }
        double supportDist = supportHit.distanceTo(eye);
        double ownerDist = hit.getLocation().distanceTo(eye);
        return ownerDist <= supportDist + BETA35_VISIBLE_OWNER_SUPPORT_OVERRUN ? hit : null;
    }

    private BlockHitResult slabbed$preserveBeta35VisibleOwnerBeforeSupport(float tickProgress, BlockHitResult candidate) {
        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
        if (candidate == null || world == null || cam == null || candidate.getType() != HitResult.Type.BLOCK) {
            return candidate;
        }
        BlockPos supportPos = candidate.getBlockPos();
        BlockState supportState = world.getBlockState(supportPos);
        if (!slabbed$isBeta35VisibleOwnerSupportSurface(supportState)) {
            return candidate;
        }

        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 supportHit = candidate.getLocation();
        Vec3 toSupport = supportHit.subtract(eye);
        double supportDist = toSupport.length();
        if (supportDist <= 0.0d) {
            return candidate;
        }
        Vec3 end = eye.add(toSupport.normalize().scale(supportDist + BETA35_VISIBLE_OWNER_SUPPORT_OVERRUN));
        BlockHitResult visibleOwner = slabbed$raycastBeta35VisibleOwnerBehindSupport(
                world,
                cam,
                eye,
                end,
                supportPos.above(),
                supportHit);
        return visibleOwner == null ? candidate : visibleOwner;
    }

    private boolean slabbed$beta35VisibleOwnerPreserved(BlockHitResult original, BlockHitResult chosen) {
        if (original == null || chosen == null || original.getBlockPos().equals(chosen.getBlockPos())) {
            return false;
        }
        ClientLevel world = this.slabbed$self().level;
        return world != null
                && SlabSupport.isBeta35SlabHeightVisibleOwnerObject(
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
        ClientLevel world = this.slabbed$self().level;
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
        return "visible-object-owner-preserve";
    }

    private static boolean slabbed$isBeta35VisibleOwnerSupportSurface(BlockState state) {
        return state != null && (state.getBlock() instanceof SlabBlock || SlabSupport.isSupportingSlab(state));
    }

    private static boolean slabbed$isLoweredObjectShapeOwner(ClientLevel world, BlockPos pos, BlockState state) {
        return SlabSupport.isLoweredTorchVisual(world, pos, state)
                || SlabSupport.isLoweredBlockEntityVisual(world, pos, state)
                || SlabSupport.isLoweredBedVisual(world, pos, state)
                || slabbed$isBeta35HitboxOwnerObject(world, pos, state);
    }

    private static boolean slabbed$isBeta35HitboxOwnerObject(ClientLevel world, BlockPos pos, BlockState state) {
        return world != null
                && pos != null
                && state != null
                && SlabSupport.getYOffset(world, pos, state) < 0.0d
                && (SlabSupport.isBeta35FenceWallVariantContactObject(state)
                        || state.is(Blocks.ANVIL)
                        || SlabSupport.isBeta35SlabHeightVisibleOwnerObject(world, pos, state)
                        || slabbed$isBeta35SlabHeightApertureOwnerObject(world, pos, state));
    }

    private static boolean slabbed$isBeta35SlabHeightApertureOwnerObject(
            ClientLevel world, BlockPos pos, BlockState state
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
        BlockPos supportPos = pos.below();
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

        ClientLevel world = this.slabbed$self().level;
        if (world == null) {
            return SEAM_OWNER_KEEP_INITIAL;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (hit.getDirection() == Direction.UP && slabbed$isAnchoredLoweredFullBlock(world, pos, state)) {
            return SEAM_OWNER_ANCHORED_FULL_BLOCK;
        }

        if (hit.getDirection().getAxis() != Direction.Axis.Y
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

    private static boolean slabbed$isLoweredBottomSlabVisibleOwner(ClientLevel world, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                && state.getFluidState().isEmpty()
                && SlabSupport.getYOffset(world, pos, state) == -0.5;
    }

    private static boolean slabbed$isVisibleUpperLoweredSlabOwner(ClientLevel world, BlockPos pos, BlockState state) {
        return slabbed$isLoweredBottomSlabVisibleOwner(world, pos, state)
                && SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
                && slabbed$isAnchoredLoweredFullBlock(world, pos.below(), world.getBlockState(pos.below()));
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
        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        BlockPos initialPos = initialHit.getBlockPos();
        BlockState initialState = world.getBlockState(initialPos);
        double initialDy = SlabSupport.getYOffset(world, initialPos, initialState);
        boolean initialAnchored = SlabAnchorAttachment.isAnchored(world, initialPos);
        boolean initialLowered = initialDy == -0.5;
        boolean initialFullBlock = initialState.isSolidRender();
        Vec3 local = initialHit.getLocation().subtract(initialPos.getX(), initialPos.getY(), initialPos.getZ());
        boolean topHit = initialHit.getDirection() == Direction.UP;
        boolean edgeLike = local.x <= 0.15 || local.x >= 0.85 || local.z <= 0.15 || local.z >= 0.85;
        boolean topInterior = topHit && !edgeLike;

        Vec3 eye = cam.getEyePosition(tickProgress);
        BlockHitResult sideOwner = slabbed$retargetLoweredSideSlab(tickProgress, initialHit, true);
        String candidateReason = sideOwner == null ? "none" : "accepted";
        double initialDist2 = initialHit.getLocation().distanceToSqr(eye);
        double candidateDist2 = sideOwner == null ? Double.NaN : sideOwner.getLocation().distanceToSqr(eye);
        boolean visibleUpperSideCandidate = false;
        if (sideOwner != null) {
            BlockPos sidePos = sideOwner.getBlockPos();
            BlockState sideState = world.getBlockState(sidePos);
            double sideDy = SlabSupport.getYOffset(world, sidePos, sideState);
            visibleUpperSideCandidate = slabbed$isVisibleUpperLoweredSlabOwner(world, sidePos, sideState)
                    && sideOwner.getDirection().getAxis() != Direction.Axis.Y
                    && sideState.hasProperty(SlabBlock.TYPE)
                    && sideState.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                    && sideDy == -0.5
                    && candidateDist2 < initialDist2;
        }
        String classification;
        if (sideOwner == null) {
            classification = "noCandidate";
        } else if (topHit
                && initialAnchored
                && initialLowered
                && initialFullBlock
                && visibleUpperSideCandidate) {
            classification = "visibleUpperSideFaceOwner";
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

        return "sideOwnerWouldWin".equals(classification) || "visibleUpperSideFaceOwner".equals(classification)
                ? sideOwner
                : null;
    }

    private static String slabbed$formatSideOwnerFacts(ClientLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        boolean lowered = dy == -0.5;
        String slabType = state.hasProperty(SlabBlock.TYPE) ? state.getValue(SlabBlock.TYPE).getSerializedName() : "none";
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
        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 dir = cam.getViewVector(tickProgress);
        double reach = 6.0;
        Vec3 end = eye.add(dir.scale(reach));
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
                && slabHeldCandidate.getDirection().getAxis() != Direction.Axis.Y;
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

        double candidateDist2 = reportCandidate == null ? Double.NaN : reportCandidate.getLocation().distanceToSqr(eye);
        double initialDist2 = initialTarget == null || initialTarget.getType() != HitResult.Type.BLOCK
                ? Double.NaN
                : initialTarget.getLocation().distanceToSqr(eye);

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

        return slabHeldCandidate != null ? slabHeldCandidate : null;
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
        BlockPos ownerPos = lowerFrontHit.getBlockPos().below();
        return new BlockHitResult(
                lowerFrontHit.getLocation(),
                lowerFrontHit.getDirection(),
                ownerPos,
                lowerFrontHit.isInside(),
                false);
    }

    private boolean slabbed$isAboveAngleLowerFrontSlabOverAnchoredOwner(
            float tickProgress, BlockHitResult sideSlabCandidate, double maxAbsVerticalLook
    ) {
        if (sideSlabCandidate == null || sideSlabCandidate.getDirection().getAxis() == Direction.Axis.Y) {
            return false;
        }

        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
        if (world == null || cam == null) {
            return false;
        }

        BlockPos slabPos = sideSlabCandidate.getBlockPos();
        BlockState slabState = world.getBlockState(slabPos);
        if (!(slabState.getBlock() instanceof SlabBlock)
                || !slabState.hasProperty(SlabBlock.TYPE)
                || slabState.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                || SlabSupport.getYOffset(world, slabPos, slabState) != -0.5
                || !SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, slabPos, slabState)) {
            return false;
        }

        BlockPos ownerPos = slabPos.below();
        if (!slabbed$isAnchoredLoweredFullBlock(world, ownerPos, world.getBlockState(ownerPos))) {
            return false;
        }

        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 dir = cam.getViewVector(tickProgress);
        if (Math.abs(dir.y) > maxAbsVerticalLook) {
            return false;
        }

        VoxelShape outline = slabState.getShape(world, slabPos, CollisionContext.of(cam));
        if (outline == null || outline.isEmpty()) {
            return false;
        }
        AABB bounds = outline.bounds();
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
        Entity cam = this.slabbed$self().getCameraEntity();
        if (cam == null) {
            return false;
        }
        Vec3 eye = cam.getEyePosition(tickProgress);
        return candidate.getLocation().distanceToSqr(eye) <= current.getLocation().distanceToSqr(eye) + 1.0e-6;
    }

    private boolean slabbed$isSlabPlacementIntent() {
        if (this.slabbed$self().player == null) {
            return false;
        }
        ItemStack stack = this.slabbed$self().player.getMainHandItem();
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof SlabBlock;
    }

    private boolean slabbed$isPlacementIntentRelevant() {
        if (this.slabbed$self().player == null) {
            return false;
        }
        ItemStack stack = this.slabbed$self().player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        return blockItem.getBlock() instanceof SlabBlock || blockItem.getBlock() == Blocks.STONE;
    }

    private void slabbed$setHitResultWithPlacementIntent(
            float tickProgress,
            HitResult initialTarget,
            BlockHitResult finalTarget,
            String reason
    ) {
        this.slabbed$self().hitResult = finalTarget;
        slabbed$recordPlacementIntentState(tickProgress, initialTarget, finalTarget, reason);
    }

    private void slabbed$recordPlacementIntentState(
            float tickProgress,
            HitResult initialTarget,
            BlockHitResult finalTarget,
            String reason
    ) {
        slabbed$auditProducerEval(initialTarget, finalTarget, reason);
        ClientLevel world = this.slabbed$self().level;
        if (world == null || finalTarget == null) {
            slabbed$auditProducerSkip(initialTarget, finalTarget, "SKIP_FINAL_TARGET_UNKNOWN", reason);
            return;
        }
        if (!slabbed$isPlacementIntentRelevant()) {
            slabbed$auditProducerSkip(initialTarget, finalTarget, "SKIP_HELD_ITEM_UNRELATED", reason);
            return;
        }
        if (!(initialTarget instanceof BlockHitResult originalVisible)
                || initialTarget.getType() != HitResult.Type.BLOCK
                || finalTarget.getType() != HitResult.Type.BLOCK) {
            slabbed$auditProducerSkip(initialTarget, finalTarget, "SKIP_NO_BLOCK_HIT", reason);
            return;
        }
        if (originalVisible.getBlockPos().equals(finalTarget.getBlockPos())) {
            slabbed$auditProducerSkip(initialTarget, finalTarget, "SKIP_OWNER_UNCHANGED", reason);
            return;
        }
        if (originalVisible.getDirection().getAxis().isVertical()
                || !originalVisible.getDirection().equals(finalTarget.getDirection())) {
            slabbed$auditProducerSkip(initialTarget, finalTarget, "SKIP_NOT_PLACEMENT_RELEVANT", reason);
            return;
        }

        ItemStack held = this.slabbed$self().player.getMainHandItem();
        BlockPos originalVisibleOwnerPos = originalVisible.getBlockPos();
        Direction face = originalVisible.getDirection();
        BlockPos expectedPlacePos = originalVisibleOwnerPos.relative(face);
        if (expectedPlacePos.equals(finalTarget.getBlockPos())) {
            slabbed$auditProducerSkip(initialTarget, finalTarget, "SKIP_OWNER_UNCHANGED", reason);
            return;
        }

        BlockState originalVisibleState = world.getBlockState(originalVisibleOwnerPos);
        BlockState finalTargetState = world.getBlockState(finalTarget.getBlockPos());
        PlacementIntentState.set(
                world.dimension().toString(),
                BuiltInRegistries.ITEM.getKey(held.getItem()).toString(),
                originalVisibleOwnerPos,
                face,
                originalVisible.getLocation(),
                originalVisibleState.toString(),
                expectedPlacePos,
                face,
                finalTarget.getBlockPos(),
                finalTarget.getDirection(),
                finalTarget.getLocation(),
                finalTargetState.toString(),
                reason);
    }

    private void slabbed$recordFinalTargetUnknownExpectedPlaceKnownState(
            float tickProgress,
            HitResult initialTarget,
            BlockHitResult originalVisible,
            String reason
    ) {
        slabbed$auditProducerEval(initialTarget, null, reason);
        ClientLevel world = this.slabbed$self().level;
        if (world == null || originalVisible == null || originalVisible.getType() != HitResult.Type.BLOCK) {
            slabbed$auditProducerSkip(initialTarget, null, "SKIP_NO_BLOCK_HIT", reason);
            return;
        }
        if (!slabbed$isPlacementIntentRelevant()) {
            slabbed$auditProducerSkip(initialTarget, null, "SKIP_HELD_ITEM_UNRELATED", reason);
            return;
        }
        Direction face = originalVisible.getDirection();
        if (face.getAxis().isVertical()) {
            slabbed$auditProducerSkip(initialTarget, null, "SKIP_NOT_PLACEMENT_RELEVANT", reason);
            return;
        }

        ItemStack held = this.slabbed$self().player.getMainHandItem();
        BlockPos originalVisibleOwnerPos = originalVisible.getBlockPos();
        BlockPos expectedPlacePos = originalVisibleOwnerPos.relative(face);
        BlockState originalVisibleState = world.getBlockState(originalVisibleOwnerPos);
        PlacementIntentState.set(
                world.dimension().toString(),
                BuiltInRegistries.ITEM.getKey(held.getItem()).toString(),
                originalVisibleOwnerPos,
                face,
                originalVisible.getLocation(),
                originalVisibleState.toString(),
                expectedPlacePos,
                face,
                null,
                null,
                null,
                null,
                PlacementIntentState.Mode.FINAL_TARGET_UNKNOWN_EXPECTED_PLACE_KNOWN,
                reason);
    }

    private void slabbed$auditProducerEval(HitResult initialTarget, BlockHitResult finalTarget, String reason) {
        if (!PlacementIntentState.isAuditEnabled()) {
            return;
        }
        PlacementIntentState.auditProducerEval("EVAL", reason,
                slabbed$placementIntentProducerDetails(initialTarget, finalTarget, reason));
    }

    private void slabbed$auditProducerSkip(
            HitResult initialTarget,
            BlockHitResult finalTarget,
            String decision,
            String reason
    ) {
        if (!PlacementIntentState.isAuditEnabled()) {
            return;
        }
        PlacementIntentState.auditProducerSkip(decision, reason,
                slabbed$placementIntentProducerDetails(initialTarget, finalTarget, reason));
    }

    private String slabbed$placementIntentProducerDetails(
            HitResult initialTarget,
            BlockHitResult finalTarget,
            String reason
    ) {
        ItemStack held = this.slabbed$self().player == null ? ItemStack.EMPTY : this.slabbed$self().player.getMainHandItem();
        String heldItem = held.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        ClientLevel world = this.slabbed$self().level;
        BlockHitResult original = initialTarget instanceof BlockHitResult blockHit ? blockHit : null;
        BlockPos originalPos = original == null ? null : original.getBlockPos();
        Direction originalFace = original == null ? null : original.getDirection();
        BlockPos finalPos = finalTarget == null ? null : finalTarget.getBlockPos();
        Direction finalFace = finalTarget == null ? null : finalTarget.getDirection();
        BlockPos expectedPlacePos = originalPos == null || originalFace == null ? null : originalPos.relative(originalFace);
        boolean ownerChanged = originalPos != null && finalPos != null && !originalPos.equals(finalPos);
        return "path=GameRendererCrosshairRetargetMixin#pick"
                + " heldItem=" + heldItem
                + " producerReason=" + (reason == null ? "null" : reason.replace(' ', '_'))
                + " originalHitType=" + (initialTarget == null ? "null" : initialTarget.getType())
                + " initialTarget=" + slabbed$formatHit(initialTarget).replace(' ', '_')
                + " originalHitPos=" + slabbed$producerPos(originalPos)
                + " originalHitFace=" + slabbed$producerSafe(originalFace)
                + " originalHitState=" + slabbed$producerState(world, originalPos)
                + " originalHitDy=" + slabbed$producerDy(world, originalPos)
                + " finalTarget=" + slabbed$formatHit(finalTarget).replace(' ', '_')
                + " finalTargetPos=" + slabbed$producerPos(finalPos)
                + " finalTargetFace=" + slabbed$producerSafe(finalFace)
                + " finalTargetState=" + slabbed$producerState(world, finalPos)
                + " finalTargetDy=" + slabbed$producerDy(world, finalPos)
                + " ownerChanged=" + ownerChanged
                + " expectedPlaceComputed=" + (expectedPlacePos != null)
                + " expectedPlacePos=" + slabbed$producerPos(expectedPlacePos)
                + " expectedPlaceFace=" + slabbed$producerSafe(originalFace)
                + " expectedPlaceState=" + slabbed$producerState(world, expectedPlacePos)
                + " expectedPlaceDy=" + slabbed$producerDy(world, expectedPlacePos)
                + " finalOwnerOccupied=" + slabbed$producerOccupied(world, finalPos)
                + " finalOwnerOccupiedNonReplaceable=unknown"
                + " expectedPlaceEmpty=" + slabbed$producerExpectedPlaceOpen(world, expectedPlacePos)
                + " expectedPlaceEmptyOrReplaceable=unknown"
                + " placementIntentRelevant=unknown";
    }

    private static String slabbed$producerPos(BlockPos pos) {
        return pos == null ? "null" : pos.toShortString().replace(' ', '_');
    }

    private static String slabbed$producerSafe(Object value) {
        return value == null ? "null" : value.toString().replace(' ', '_');
    }

    private static String slabbed$producerState(ClientLevel world, BlockPos pos) {
        if (world == null || pos == null) {
            return "unknown";
        }
        return slabbed$producerSafe(world.getBlockState(pos));
    }

    private static String slabbed$producerDy(ClientLevel world, BlockPos pos) {
        if (world == null || pos == null) {
            return "unknown";
        }
        return slabbed$formatDouble(SlabSupport.getYOffset(world, pos, world.getBlockState(pos)));
    }

    private static String slabbed$producerOccupied(ClientLevel world, BlockPos pos) {
        if (world == null || pos == null) {
            return "unknown";
        }
        return Boolean.toString(!world.getBlockState(pos).isAir());
    }

    private static String slabbed$producerExpectedPlaceOpen(ClientLevel world, BlockPos pos) {
        if (world == null || pos == null) {
            return "unknown";
        }
        return Boolean.toString(world.getBlockState(pos).isAir());
    }

    private boolean slabbed$isInitialHitOnLoweredSlabFace(BlockHitResult hit) {
        ClientLevel world = this.slabbed$self().level;
        if (world == null) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getFluidState().isEmpty()
                && SlabSupport.getYOffset(world, pos, state) == -0.5;
    }

    private boolean slabbed$isInitialHitOnLoweredFullBlockPlacementIntent(BlockHitResult hit) {
        ClientLevel world = this.slabbed$self().level;
        if (world == null || hit.getType() != HitResult.Type.BLOCK || hit.getDirection() == Direction.DOWN) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SlabBlock || !slabbed$isAnchoredOrLoweredFullBlock(world, pos, state)) {
            return false;
        }
        BlockPos placePos = pos.relative(hit.getDirection());
        BlockState placeState = world.getBlockState(placePos);
        return placeState.isAir() || slabbed$isLoweredSlabLaneState(world, placePos, placeState);
    }

    private static boolean slabbed$isOrdinaryFullBlock(ClientLevel world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!state.isSolidRender()) {
            return false;
        }
        if (state.getBlock() instanceof SlabBlock) {
            return false;
        }
        net.minecraft.world.level.block.Block block = state.getBlock();
        return !(block instanceof EntityBlock) && !(block instanceof CraftingTableBlock);
    }

    private static boolean slabbed$isAnchoredOrLoweredFullBlock(ClientLevel world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || state.getBlock() instanceof SlabBlock) {
            return false;
        }
        if (!state.isSolidRender()) {
            return false;
        }

        net.minecraft.world.level.block.Block block = state.getBlock();
        if (block instanceof EntityBlock || block instanceof CraftingTableBlock) {
            return false;
        }

        return SlabAnchorAttachment.isAnchored(world, pos)
                || SlabSupport.getYOffset(world, pos, state) == -0.5;
    }

    private static boolean slabbed$isLoweredSlabLaneState(ClientLevel world, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getFluidState().isEmpty()
                && SlabSupport.getYOffset(world, pos, state) == -0.5;
    }

    private BlockHitResult slabbed$retargetLoweredSideSlab(float tickProgress, HitResult currentHit, boolean slabHeld) {
        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 dir = cam.getViewVector(tickProgress);
        double reach = 6.0;
        Vec3 end = eye.add(dir.scale(reach));
        return LoweredSideSlabRetargeter.findLoweredSideSlabRetarget(world, cam, eye, end, currentHit, slabHeld);
    }

    private BlockHitResult slabbed$retargetVisibleUpperLoweredSlabSideFaceMiss(
            float tickProgress, HitResult currentHit
    ) {
        if (currentHit == null || currentHit.getType() != HitResult.Type.MISS) {
            return null;
        }
        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 end = eye.add(cam.getViewVector(tickProgress).scale(6.0d));
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
                    VoxelShape outline = state.getShape(world, pos, CollisionContext.of(cam));
                    if (outline == null || outline.isEmpty()) {
                        continue;
                    }
                    BlockHitResult hit = outline.clip(eye, end, pos);
                    if (hit == null || hit.getDirection().getAxis() == Direction.Axis.Y) {
                        continue;
                    }
                    double dist2 = hit.getLocation().distanceToSqr(eye);
                    if (dist2 <= 36.0d + 1.0e-6d && dist2 < bestDist2) {
                        bestDist2 = dist2;
                        best = new BlockHitResult(hit.getLocation(), hit.getDirection(), pos, hit.isInside(), false);
                    }
                }
            }
        }
        return best;
    }

    private BlockHitResult slabbed$retargetLoweredChainTopSupport(float tickProgress, HitResult currentHit) {
        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 dir = cam.getViewVector(tickProgress);
        double reach = 6.0;
        Vec3 end = eye.add(dir.scale(reach));
        double currentDist2 = Double.POSITIVE_INFINITY;
        if (currentHit != null && currentHit.getType() == HitResult.Type.BLOCK) {
            currentDist2 = currentHit.getLocation().distanceToSqr(eye);
        }
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05));

        BlockHitResult bestHit = null;
        double bestDist2 = currentDist2;
        for (int i = 1; i <= steps; i++) {
            double t = reach * i / steps;
            if (t * t > bestDist2 + 1.0e-6) {
                break;
            }
            Vec3 sample = eye.add(dir.scale(t));
            BlockPos samplePos = BlockPos.containing(sample);

            BlockHitResult hit = slabbed$raycastLoweredChainTopSupport(world, cam, eye, end, samplePos);
            if (hit != null) {
                double dist2 = hit.getLocation().distanceToSqr(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }

            hit = slabbed$raycastLoweredChainTopSupport(world, cam, eye, end, samplePos.above());
            if (hit != null) {
                double dist2 = hit.getLocation().distanceToSqr(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }
        }

        return bestHit;
    }

    private static BlockHitResult slabbed$raycastLoweredChainTopSupport(
            ClientLevel world, Entity cam, Vec3 eye, Vec3 end, BlockPos pos
    ) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChainBlock)
                || !state.hasProperty(ChainBlock.AXIS)
                || state.getValue(ChainBlock.AXIS) != Direction.Axis.Y
                || SlabSupport.getYOffset(world, pos, state) >= 0.0) {
            return null;
        }

        BlockPos supportPos = pos.below();
        BlockState supportState = world.getBlockState(supportPos);
        if (!(supportState.getBlock() instanceof SlabBlock)
                || !supportState.hasProperty(SlabBlock.TYPE)
                || supportState.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                || SlabSupport.getYOffset(world, supportPos, supportState) != -0.5
                || !slabbed$hasAdjacentAnchoredLoweredFullBlock(world, supportPos)) {
            return null;
        }

        VoxelShape outline = state.getShape(world, pos, CollisionContext.of(cam));
        BlockHitResult outlineHit = outline.clip(eye, end, pos);
        if (outlineHit == null) {
            return null;
        }
        return outlineHit.getLocation().distanceToSqr(eye) <= end.distanceToSqr(eye) + 1.0e-6
                ? outlineHit : null;
    }

    private static boolean slabbed$hasAdjacentAnchoredLoweredFullBlock(ClientLevel world, BlockPos supportPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidatePos = supportPos.relative(direction);
            BlockState candidateState = world.getBlockState(candidatePos);
            if (slabbed$isAnchoredLoweredFullBlock(world, candidatePos, candidateState)) {
                return true;
            }
        }
        return false;
    }

    private BlockHitResult slabbed$preserveBlockBackedTopSlabMiss(float tickProgress, HitResult currentHit) {
        if (!(currentHit instanceof BlockHitResult missHit)
                || currentHit.getType() != HitResult.Type.MISS
                || missHit.getDirection().getAxis() == Direction.Axis.Y) {
            return null;
        }

        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        BlockPos pos = missHit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)
                || state.getValue(SlabBlock.TYPE) != SlabType.TOP
                || !state.getFluidState().isEmpty()
                || SlabSupport.getYOffset(world, pos, state) != 0.0d
                || SlabAnchorAttachment.isAnchored(world, pos)
                || SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)
                || !world.getBlockState(pos.below()).isAir()
                || !slabbed$hasAdjacentAnchoredLoweredFullBlock(world, pos)) {
            return null;
        }

        Vec3 localHit = missHit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        if (localHit.y < -1.0e-6d || localHit.y > 0.5d + 1.0e-6d) {
            return null;
        }

        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 end = eye.add(cam.getViewVector(tickProgress).scale(6.0d));
        BlockHitResult outlineHit = state
                .getShape(world, pos, CollisionContext.of(cam))
                .clip(eye, end, pos);
        if (outlineHit == null
                || outlineHit.getType() != HitResult.Type.BLOCK
                || !outlineHit.getBlockPos().equals(pos)
                || outlineHit.getDirection() != missHit.getDirection()) {
            return null;
        }
        return outlineHit;
    }

    private BlockHitResult slabbed$retargetAnchoredLoweredFullBlock(float tickProgress, HitResult currentHit) {
        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
        if (world == null || cam == null) {
            return null;
        }

        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 dir = cam.getViewVector(tickProgress);
        double reach = 6.0;
        Vec3 end = eye.add(dir.scale(reach));
        double currentDist2 = Double.POSITIVE_INFINITY;
        if (currentHit != null && currentHit.getType() == HitResult.Type.BLOCK) {
            currentDist2 = currentHit.getLocation().distanceToSqr(eye);
        }
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05));

        BlockHitResult bestHit = null;
        double bestDist2 = currentDist2;
        for (int i = 1; i <= steps; i++) {
            double t = reach * i / steps;
            if (t * t > bestDist2 + 1.0e-6) {
                break;
            }
            Vec3 sample = eye.add(dir.scale(t));
            BlockPos samplePos = BlockPos.containing(sample);

            BlockPos candidatePos = samplePos;
            BlockState candidateState = world.getBlockState(candidatePos);
            BlockHitResult hit = slabbed$raycastAnchoredLoweredFullBlock(world, cam, eye, end, candidatePos, candidateState);
            if (hit != null) {
                double dist2 = hit.getLocation().distanceToSqr(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }

            candidatePos = samplePos.above();
            candidateState = world.getBlockState(candidatePos);
            hit = slabbed$raycastAnchoredLoweredFullBlock(world, cam, eye, end, candidatePos, candidateState);
            if (hit != null) {
                double dist2 = hit.getLocation().distanceToSqr(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }
        }

        return bestHit;
    }

    private static BlockHitResult slabbed$raycastAnchoredLoweredFullBlock(
            ClientLevel world, Entity cam, Vec3 eye, Vec3 end, BlockPos pos, BlockState state
    ) {
        if (!slabbed$isAnchoredLoweredFullBlock(world, pos, state)) {
            return null;
        }
        VoxelShape outline = state.getShape(world, pos, CollisionContext.of(cam));
        VoxelShape raycast = state.getInteractionShape(world, pos);
        BlockHitResult outlineHit = outline.clip(eye, end, pos);
        BlockHitResult raycastHit = raycast.clip(eye, end, pos);
        if (outlineHit == null || raycastHit == null) {
            return null;
        }
        return raycastHit.getLocation().distanceToSqr(eye) <= end.distanceToSqr(eye) + 1.0e-6
                ? raycastHit : null;
    }

    private static boolean slabbed$isAnchoredLoweredFullBlock(ClientLevel world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!state.isSolidRender()) {
            return false;
        }

        net.minecraft.world.level.block.Block block = state.getBlock();
        if (block instanceof EntityBlock || block instanceof CraftingTableBlock) {
            return false;
        }

        return SlabAnchorAttachment.isAnchored(world, pos)
                && SlabSupport.getYOffset(world, pos, state) < 0.0;
    }

    private void slabbed$traceTargeting(
            float tickProgress, HitResult initialTarget, String anchoredDecision, boolean sideSlabRetargetFired
    ) {
        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
        if (world == null || cam == null || this.slabbed$self().player == null) {
            return;
        }

        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 dir = cam.getViewVector(tickProgress);
        double reach = 6.0;
        Vec3 end = eye.add(dir.scale(reach));
        ItemStack held = this.slabbed$self().player.getMainHandItem();
        slabbed$recordLiveCursorIntent(
                tickProgress,
                world,
                cam,
                eye,
                dir,
                end,
                held,
                initialTarget,
                this.slabbed$self().hitResult,
                anchoredDecision,
                sideSlabRetargetFired);
        slabbed$traceBeta4FinalTarget(
                tickProgress,
                initialTarget,
                anchoredDecision,
                sideSlabRetargetFired);

        if (Boolean.getBoolean("slabbed.beta4LiveRetargetRecorderEveryTick")) {
            double initialDist2 = initialTarget == null || initialTarget.getType() != HitResult.Type.BLOCK
                    ? Double.NaN
                    : initialTarget.getLocation().distanceToSqr(eye);
            slabbed$recordBeta4LiveRetarget(
                    tickProgress,
                    initialTarget,
                    this.slabbed$self().hitResult,
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
            vanillaDist2 = initialTarget.getLocation().distanceToSqr(eye);
        }

        String fbCandidate = slabbed$findAnchoredFbCandidate(world, cam, eye, end, vanillaDist2);
        String slabCandidate = slabbed$findLoweredSlabCandidate(world, cam, eye, end, vanillaDist2);
        if (fbCandidate == null && slabCandidate == null) {
            return;
        }

        StringBuilder line = new StringBuilder(512);
        line.append("[slabbed.target.trace] heldItem=").append(held.getItem().getDescriptionId());
        line.append(" heldIsSlab=").append(slabbed$isSlabPlacementIntent());
        line.append(" initial=").append(slabbed$formatHit(initialTarget));
        line.append(" eye=").append(slabbed$formatVec(eye));
        line.append(" end=").append(slabbed$formatVec(end));
        line.append(" reach=").append(String.format("%.3f", reach));
        line.append(" anchoredFbDecision=").append(anchoredDecision);
        line.append(" fbCandidate=").append(fbCandidate == null ? "none" : fbCandidate);
        line.append(" sideSlabCandidate=").append(slabCandidate == null ? "none" : slabCandidate);
        line.append(" sideSlabRetargetFired=").append(sideSlabRetargetFired);
        line.append(" final=").append(slabbed$formatHit(this.slabbed$self().hitResult));
        slabbed$appendSlabHeldAnchoredFbFocusedTrace(
                line,
                world,
                cam,
                eye,
                end,
                initialTarget,
                this.slabbed$self().hitResult,
                anchoredDecision,
                vanillaDist2);
        Slabbed.LOGGER.info(line.toString());
    }

    private void slabbed$recordLiveCursorIntent(
            float tickProgress,
            ClientLevel world,
            Entity cam,
            Vec3 eye,
            Vec3 look,
            Vec3 end,
            ItemStack held,
            HitResult initialTarget,
            HitResult finalTarget,
            String classification,
            boolean sideSlabRetargetFired
    ) {
        if (!LiveCursorIntentRecorder.enabled()) {
            return;
        }
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("tick", Long.toString(world.getGameTime()));
        row.put("time", Long.toString(System.currentTimeMillis()));
        row.put("playerPos", this.slabbed$self().player == null
                ? "none"
                : slabbed$formatVec(this.slabbed$self().player.position()));
        row.put("eyePos", slabbed$formatVec(eye));
        row.put("yaw", this.slabbed$self().player == null
                ? "NaN"
                : String.format("%.3f", this.slabbed$self().player.getYRot()));
        row.put("pitch", this.slabbed$self().player == null
                ? "NaN"
                : String.format("%.3f", this.slabbed$self().player.getXRot()));
        row.put("lookVec", slabbed$formatVec(look));
        row.put("rayEnd", slabbed$formatVec(end));
        row.put("heldItem", held.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
        row.put("selectedSlot", slabbed$selectedSlot());
        row.put("sideSlabRetargetFired", Boolean.toString(sideSlabRetargetFired));
        row.put("finalClassification", classification);
        slabbed$putLiveCursorHit(row, world, cam, eye, end, initialTarget, "vanilla");
        slabbed$putLiveCursorHit(row, world, cam, eye, end, finalTarget, "final");
        if (finalTarget instanceof BlockHitResult finalBlock) {
            BlockPos pos = finalBlock.getBlockPos();
            BlockState state = world.getBlockState(pos);
            row.put("outlineOwnerPos", pos.toShortString());
            row.put("outlineOwnerState", state.toString());
            row.put("outlineDy", slabbed$formatDouble(SlabSupport.getYOffset(world, pos, state)));
            row.put("outlineReplayHit", slabbed$shapeHit(world, cam, eye, end, pos, state, true));
            row.put("raycastOwnerPos", pos.toShortString());
            row.put("raycastDy", slabbed$formatDouble(SlabSupport.getYOffset(world, pos, state)));
            row.put("raycastReplayHit", slabbed$shapeHit(world, cam, eye, end, pos, state, false));
            row.put("collisionBounds", slabbed$collisionBounds(world, cam, pos, state));
            row.put("interactionBounds", slabbed$shapeBounds(world, cam, pos, state, false));
            row.put("raycastBounds", slabbed$shapeBounds(world, cam, pos, state, false));
            row.put("outlineBounds", slabbed$shapeBounds(world, cam, pos, state, true));
            row.put("expectedTargetOwner", pos.toShortString());
            row.put("expectedDy", slabbed$formatDouble(SlabSupport.getYOffset(world, pos, state)));
            row.put("expectedAction", slabbed$isSlabPlacementIntent() ? "place_block" : "use_block");
        } else {
            row.put("outlineOwnerPos", "none");
            row.put("outlineOwnerState", "none");
            row.put("outlineDy", "NaN");
            row.put("outlineReplayHit", "none");
            row.put("raycastOwnerPos", "none");
            row.put("raycastDy", "NaN");
            row.put("raycastReplayHit", "none");
            row.put("collisionBounds", "none");
            row.put("interactionBounds", "none");
            row.put("raycastBounds", "none");
            row.put("outlineBounds", "none");
            row.put("expectedTargetOwner", "MISS");
            row.put("expectedDy", "NaN");
            row.put("expectedAction", "miss");
        }
        LiveCursorIntentRecorder.recordCursor(row);
    }

    private void slabbed$putLiveCursorHit(
            LinkedHashMap<String, String> row,
            ClientLevel world,
            Entity cam,
            Vec3 eye,
            Vec3 end,
            HitResult hit,
            String prefix
    ) {
        row.put(prefix + "HitType", hit == null ? "null" : hit.getType().toString());
        if (!(hit instanceof BlockHitResult blockHit)) {
            row.put(prefix + "HitPos", "none");
            row.put(prefix + "HitFace", "none");
            row.put(prefix + "HitVec", hit == null ? "none" : slabbed$formatVec(hit.getLocation()));
            row.put(prefix + "HitState", "none");
            row.put(prefix + "Dy", "NaN");
            row.put(prefix + "HitClassification", "none");
            row.put(prefix + "OwnerClass", "none");
            row.put(prefix + "OwnerLaneKind", "none");
            row.put(prefix + "PersistentLoweredSlabCarrier", "false");
            row.put(prefix + "CompoundAnchor", "false");
            row.put(prefix + "OutlineReplayHit", "none");
            row.put(prefix + "RaycastReplayHit", "none");
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        String dy = slabbed$formatDouble(SlabSupport.getYOffset(world, pos, state));
        row.put(prefix + "HitPos", pos.toShortString());
        row.put(prefix + "HitFace", blockHit.getDirection().toString());
        row.put(prefix + "HitVec", slabbed$formatVec(blockHit.getLocation()));
        row.put(prefix + "HitState", state.toString());
        row.put(prefix + "Dy", dy);
        row.put(prefix + "HitClassification", slabbed$sourceMode(world, pos, state));
        row.put(prefix + "OwnerClass", slabbed$ownerClass(world, pos, state));
        row.put(prefix + "OwnerLaneKind", slabbed$laneKind(world, pos, state));
        row.put(prefix + "PersistentLoweredSlabCarrier",
                Boolean.toString(SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)));
        row.put(prefix + "CompoundAnchor", Boolean.toString(SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)));
        row.put(prefix + "OutlineReplayHit", slabbed$shapeHit(world, cam, eye, end, pos, state, true));
        row.put(prefix + "RaycastReplayHit", slabbed$shapeHit(world, cam, eye, end, pos, state, false));
    }

    private String slabbed$selectedSlot() {
        return "unknown";
    }

    private static String slabbed$laneKind(ClientLevel world, BlockPos pos, BlockState state) {
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
            return "persistent_lowered_slab_carrier";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)) {
            return "compound_visible_side_lower_slab";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)) {
            return "compound_visible_side_upper_slab";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)) {
            return "compound_visible_side_double_slab";
        }
        if (SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return "compound_visible_owner_top_slab";
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return "anchored_full_block";
        }
        return state.getBlock() instanceof SlabBlock ? "unnamed_or_vanilla_slab" : "none";
    }

    private static String slabbed$collisionBounds(ClientLevel world, Entity cam, BlockPos pos, BlockState state) {
        try {
            VoxelShape shape = state.getCollisionShape(world, pos, CollisionContext.of(cam));
            if (shape == null || shape.isEmpty()) {
                return "empty";
            }
            AABB box = shape.bounds();
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

    private static void slabbed$appendSlabHeldAnchoredFbFocusedTrace(
            StringBuilder line,
            ClientLevel world,
            Entity cam,
            Vec3 eye,
            Vec3 end,
            HitResult initialTarget,
            HitResult finalTarget,
            String anchoredDecision,
            double initialDist2
    ) {
        if (!"scan-anchored-fb-fired-slab-held".equals(anchoredDecision)) {
            return;
        }

        BlockHitResult initialBlock = initialTarget instanceof BlockHitResult initial ? initial : null;
        BlockHitResult anchoredCandidate = slabbed$findAnchoredFbCandidateHit(world, cam, eye, end, initialDist2);
        double anchoredDist2 = anchoredCandidate == null
                ? Double.NaN
                : anchoredCandidate.getLocation().distanceToSqr(eye);
        double anchoredMinusInitialDist2 = Double.isFinite(initialDist2) && !Double.isNaN(anchoredDist2)
                ? anchoredDist2 - initialDist2
                : Double.NaN;

        line.append(" focusedTrace=true");
        slabbed$appendFocusedHitTrace(line, world, cam, eye, end, initialBlock, "initial", initialDist2);
        slabbed$appendFocusedHitTrace(line, world, cam, eye, end, anchoredCandidate, "anchored", anchoredDist2);
        line.append(" anchoredMinusInitialDist2=").append(slabbed$formatDouble(anchoredMinusInitialDist2));
        line.append(" reasonSelected=")
                .append(slabbed$focusedAnchoredSelectionReason(initialBlock, anchoredCandidate, finalTarget, initialDist2, anchoredDist2));
        line.append(" reasonRejected=")
                .append(slabbed$focusedAnchoredRejectionReason(anchoredCandidate, initialDist2, anchoredDist2));
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

        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
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
                    world.dimension().toString());
        }

        if (slabbed$beta4ReloadJumpRecorderTicksRemaining <= 0) {
            return;
        }

        long worldTick = world.getGameTime();
        if (worldTick == slabbed$beta4ReloadJumpRecorderLastWorldTick) {
            return;
        }
        slabbed$beta4ReloadJumpRecorderLastWorldTick = worldTick;
        slabbed$beta4ReloadJumpRecorderTicksRemaining--;

        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 look = cam.getViewVector(tickProgress);
        HitResult target = this.slabbed$self().hitResult;
        BlockHitResult blockTarget = target instanceof BlockHitResult blockHit ? blockHit : null;
        BlockPos center = blockTarget == null
                ? (this.slabbed$self().player == null ? BlockPos.containing(eye) : this.slabbed$self().player.blockPosition())
                : blockTarget.getBlockPos();

        StringBuilder line = new StringBuilder(4096);
        line.append("[BETA4_RELOAD_JUMP_RECORDER]");
        line.append(" tick=").append(worldTick);
        line.append(" ticksRemaining=").append(slabbed$beta4ReloadJumpRecorderTicksRemaining);
        line.append(" worldPresent=true");
        line.append(" world=").append(world.dimension().toString());
        line.append(" playerPos=").append(this.slabbed$self().player == null
                ? "none"
                : slabbed$formatVec(this.slabbed$self().player.position()));
        line.append(" eye=").append(slabbed$formatVec(eye));
        line.append(" look=").append(slabbed$formatVec(look));
        if (this.slabbed$self().player != null) {
            line.append(" yaw=").append(String.format("%.3f", this.slabbed$self().player.getYRot()));
            line.append(" pitch=").append(String.format("%.3f", this.slabbed$self().player.getXRot()));
        }
        line.append(" crosshairType=").append(target == null ? "null" : target.getType());
        line.append(" outlinePos=").append(blockTarget == null ? "none" : blockTarget.getBlockPos().toShortString());
        line.append(" outlineFace=").append(blockTarget == null ? "none" : blockTarget.getDirection());
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

        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
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
                    world.dimension().toString(),
                    System.getProperty("slabbed.beta4OutlineRecorderWatch", ""));
        }

        if (slabbed$beta4OutlineRecorderTicksRemaining <= 0) {
            return;
        }

        long worldTick = world.getGameTime();
        if (worldTick == slabbed$beta4OutlineRecorderLastWorldTick) {
            return;
        }
        slabbed$beta4OutlineRecorderLastWorldTick = worldTick;
        slabbed$beta4OutlineRecorderTicksRemaining--;

        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 look = cam.getViewVector(tickProgress);
        Vec3 end = eye.add(look.scale(6.0d));
        HitResult target = this.slabbed$self().hitResult;
        BlockHitResult blockTarget = target instanceof BlockHitResult blockHit ? blockHit : null;
        BlockPos targetPos = blockTarget == null ? null : blockTarget.getBlockPos();
        BlockState targetState = targetPos == null ? null : world.getBlockState(targetPos);

        StringBuilder line = new StringBuilder(4096);
        line.append("[BETA4_OUTLINE_RECORDER]");
        line.append(" tick=").append(worldTick);
        line.append(" ticksRemaining=").append(slabbed$beta4OutlineRecorderTicksRemaining);
        line.append(" world=").append(world.dimension().toString());
        line.append(" eye=").append(slabbed$formatVec(eye));
        line.append(" look=").append(slabbed$formatVec(look));
        line.append(" end=").append(slabbed$formatVec(end));
        line.append(" heldItem=").append(this.slabbed$self().player == null ? "none" : this.slabbed$self().player.getMainHandItem().getItem());
        line.append(" crosshairTargetType=").append(target == null ? "null" : target.getType());
        line.append(" targetIsMiss=").append(target == null || target.getType() == HitResult.Type.MISS);
        line.append(" targetPos=").append(targetPos == null ? "none" : targetPos.toShortString());
        line.append(" targetFace=").append(blockTarget == null ? "none" : blockTarget.getDirection());
        line.append(" targetHit=").append(blockTarget == null ? "none" : slabbed$formatVec(blockTarget.getLocation()));
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
            ClientLevel world,
            Entity cam,
            Vec3 eye,
            Vec3 end
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

    private static void slabbed$appendConfiguredReloadJumpWatch(StringBuilder line, ClientLevel world) {
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

        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
        if (world == null || cam == null) {
            return;
        }

        ItemStack held = this.slabbed$self().player == null ? ItemStack.EMPTY : this.slabbed$self().player.getMainHandItem();
        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 look = cam.getViewVector(tickProgress);
        double reach = 6.0d;
        Vec3 end = eye.add(look.scale(reach));
        double candidateMinusInitialDist2 = Double.isNaN(initialDist2) || Double.isNaN(candidateDist2)
                ? Double.NaN
                : candidateDist2 - initialDist2;
        BlockHitResult initialBlock = initialTarget instanceof BlockHitResult initial ? initial : null;
        BlockHitResult finalBlock = finalTarget instanceof BlockHitResult finalHit ? finalHit : null;
        double finalDist2 = finalTarget == null || finalTarget.getType() != HitResult.Type.BLOCK
                ? Double.NaN
                : finalTarget.getLocation().distanceToSqr(eye);

        StringBuilder line = new StringBuilder(1536);
        line.append("[BETA4_LIVE_RETARGET_RECORDER]");
        line.append(" heldItem=").append(held.isEmpty() ? "empty" : held.getItem().getDescriptionId());
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
        if (this.slabbed$self().player != null) {
            line.append(" yaw=").append(String.format("%.3f", this.slabbed$self().player.getYRot()));
            line.append(" pitch=").append(String.format("%.3f", this.slabbed$self().player.getXRot()));
        }
        line.append(" start=").append(slabbed$formatVec(eye));
        line.append(" end=").append(slabbed$formatVec(end));
        line.append(" reach=6.000");
        line.append(" cameraFacing=").append(cam.getDirection());
        line.append(" outlinePos=").append(finalBlock == null ? "none" : finalBlock.getBlockPos().toShortString());
        line.append(" outlineFace=").append(finalBlock == null ? "none" : finalBlock.getDirection());
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
            ClientLevel world,
            Entity cam,
            Vec3 eye,
            Vec3 look,
            Vec3 end,
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
                : initialTarget.getLocation().distanceToSqr(eye);
        String fbCandidate = slabbed$findAnchoredFbCandidate(world, cam, eye, end, vanillaDist2);
        String sideSlabCandidate = slabbed$findLoweredSlabCandidate(world, cam, eye, end, vanillaDist2);

        StringBuilder line = new StringBuilder(4096);
        line.append("[BETA4_LIVE_RETARGET_SOURCE_TRUTH]");
        line.append(" heldItem=").append(held.isEmpty() ? "empty" : held.getItem().getDescriptionId());
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
        if (this.slabbed$self().player != null) {
            line.append(" yaw=").append(String.format("%.3f", this.slabbed$self().player.getYRot()));
            line.append(" pitch=").append(String.format("%.3f", this.slabbed$self().player.getXRot()));
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
            ClientLevel world,
            Entity cam,
            Vec3 eye,
            Vec3 end,
            BlockHitResult hit,
            String prefix
    ) {
        line.append(' ').append(prefix).append("HitVec=").append(hit == null ? "none" : slabbed$formatVec(hit.getLocation()));
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
            ClientLevel world,
            Entity cam,
            Vec3 eye,
            Vec3 end,
            BlockPos pos,
            BlockState state,
            boolean outline
    ) {
        try {
            VoxelShape shape = outline
                    ? state.getShape(world, pos, CollisionContext.of(cam))
                    : state.getInteractionShape(world, pos);
            if (shape == null || shape.isEmpty()) {
                return "miss(empty)";
            }
            BlockHitResult hit = shape.clip(eye, end, pos);
            return hit == null ? "miss" : slabbed$formatHit(hit);
        } catch (Throwable t) {
            return "error:" + t.getClass().getSimpleName();
        }
    }

    private static String slabbed$shapeBounds(
            ClientLevel world,
            Entity cam,
            BlockPos pos,
            BlockState state,
            boolean outline
    ) {
        try {
            VoxelShape shape = outline
                    ? state.getShape(world, pos, CollisionContext.of(cam))
                    : state.getInteractionShape(world, pos);
            if (shape == null || shape.isEmpty()) {
                return "empty";
            }
            AABB box = shape.bounds();
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

    private static BlockHitResult slabbed$findAnchoredFbCandidateHit(
            ClientLevel world, Entity cam, Vec3 eye, Vec3 end, double initialDist2
    ) {
        Vec3 delta = end.subtract(eye);
        double reach = delta.length();
        if (reach <= 0.0d) {
            return null;
        }
        Vec3 dir = delta.normalize();
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05));
        for (int i = 1; i <= steps; i++) {
            Vec3 sample = eye.add(dir.scale(reach * i / steps));
            BlockHitResult candidate = slabbed$anchoredFbCandidateHitAt(
                    world, cam, eye, end, BlockPos.containing(sample), initialDist2);
            if (candidate != null) {
                return candidate;
            }
            candidate = slabbed$anchoredFbCandidateHitAt(
                    world, cam, eye, end, BlockPos.containing(sample).above(), initialDist2);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static BlockHitResult slabbed$anchoredFbCandidateHitAt(
            ClientLevel world, Entity cam, Vec3 eye, Vec3 end, BlockPos pos, double initialDist2
    ) {
        BlockState state = world.getBlockState(pos);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (!state.isSolidRender() || !anchored || dy != -0.5) {
            return null;
        }

        net.minecraft.world.level.block.Block block = state.getBlock();
        if (block instanceof EntityBlock || block instanceof CraftingTableBlock) {
            return null;
        }

        BlockHitResult outlineHit = state.getShape(world, pos, CollisionContext.of(cam)).clip(eye, end, pos);
        if (outlineHit == null) {
            return null;
        }

        double outlineDist2 = outlineHit.getLocation().distanceToSqr(eye);
        return outlineDist2 > initialDist2 + 1.0e-6 ? null : outlineHit;
    }

    private static void slabbed$appendFocusedHitTrace(
            StringBuilder line,
            ClientLevel world,
            Entity cam,
            Vec3 eye,
            Vec3 end,
            BlockHitResult hit,
            String prefix,
            double dist2
    ) {
        line.append(' ').append(prefix).append("FocusedPos=")
                .append(hit == null ? "none" : hit.getBlockPos().toShortString());
        line.append(' ').append(prefix).append("FocusedFace=")
                .append(hit == null ? "none" : hit.getDirection());
        line.append(' ').append(prefix).append("FocusedHitVec=")
                .append(hit == null ? "none" : slabbed$formatVec(hit.getLocation()));
        line.append(' ').append(prefix).append("FocusedDist2=")
                .append(slabbed$formatDouble(dist2));
        if (hit == null) {
            line.append(' ').append(prefix).append("FocusedState=none");
            line.append(' ').append(prefix).append("FocusedDy=NaN");
            line.append(' ').append(prefix).append("FocusedOutlineBounds=minY=NaN,maxY=NaN");
            line.append(' ').append(prefix).append("FocusedOutlineContainsRay=false");
            line.append(' ').append(prefix).append("FocusedOutlineContainsHit=false");
            line.append(' ').append(prefix).append("FocusedInteractionContainsRay=false");
            line.append(' ').append(prefix).append("FocusedInteractionContainsHit=false");
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        line.append(' ').append(prefix).append("FocusedState=").append(state);
        line.append(' ').append(prefix).append("FocusedDy=").append(slabbed$formatDouble(dy));
        slabbed$appendFocusedShapeTrace(line, world, cam, eye, end, hit, state, prefix, true);
        slabbed$appendFocusedShapeTrace(line, world, cam, eye, end, hit, state, prefix, false);
    }

    private static void slabbed$appendFocusedShapeTrace(
            StringBuilder line,
            ClientLevel world,
            Entity cam,
            Vec3 eye,
            Vec3 end,
            BlockHitResult hit,
            BlockState state,
            String prefix,
            boolean outline
    ) {
        String shapePrefix = outline ? "Outline" : "Interaction";
        try {
            VoxelShape shape = outline
                    ? state.getShape(world, hit.getBlockPos(), CollisionContext.of(cam))
                    : state.getInteractionShape(world, hit.getBlockPos());
            if (shape == null || shape.isEmpty()) {
                line.append(' ').append(prefix).append("Focused").append(shapePrefix)
                        .append("Bounds=minY=NaN,maxY=NaN");
                line.append(' ').append(prefix).append("Focused").append(shapePrefix)
                        .append("ContainsRay=false");
                line.append(' ').append(prefix).append("Focused").append(shapePrefix)
                        .append("ContainsHit=false");
                return;
            }

            AABB box = shape.bounds();
            BlockHitResult shapeHit = shape.clip(eye, end, hit.getBlockPos());
            Vec3 localHit = hit.getLocation().subtract(
                    hit.getBlockPos().getX(),
                    hit.getBlockPos().getY(),
                    hit.getBlockPos().getZ());
            boolean hitInBounds = localHit.x >= box.minX - 1.0e-6
                    && localHit.x <= box.maxX + 1.0e-6
                    && localHit.y >= box.minY - 1.0e-6
                    && localHit.y <= box.maxY + 1.0e-6
                    && localHit.z >= box.minZ - 1.0e-6
                    && localHit.z <= box.maxZ + 1.0e-6;
            line.append(' ').append(prefix).append("Focused").append(shapePrefix)
                    .append("Bounds=minY=").append(slabbed$formatDouble(box.minY))
                    .append(",maxY=").append(slabbed$formatDouble(box.maxY));
            line.append(' ').append(prefix).append("Focused").append(shapePrefix)
                    .append("ContainsRay=").append(shapeHit != null);
            line.append(' ').append(prefix).append("Focused").append(shapePrefix)
                    .append("ContainsHit=").append(hitInBounds);
        } catch (Throwable t) {
            line.append(' ').append(prefix).append("Focused").append(shapePrefix)
                    .append("Bounds=error:").append(t.getClass().getSimpleName());
            line.append(' ').append(prefix).append("Focused").append(shapePrefix)
                    .append("ContainsRay=error");
            line.append(' ').append(prefix).append("Focused").append(shapePrefix)
                    .append("ContainsHit=error");
        }
    }

    private static String slabbed$focusedAnchoredSelectionReason(
            BlockHitResult initial,
            BlockHitResult anchored,
            HitResult finalTarget,
            double initialDist2,
            double anchoredDist2
    ) {
        if (anchored == null) {
            return "none";
        }
        if (!(finalTarget instanceof BlockHitResult finalBlock)
                || !finalBlock.getBlockPos().equals(anchored.getBlockPos())) {
            return "none";
        }
        if (initial != null && initial.getBlockPos().equals(anchored.getBlockPos())) {
            return "selected_same_owner";
        }
        if (Double.isFinite(initialDist2) && anchoredDist2 <= initialDist2 + 1.0e-6) {
            return "selected_closer_or_tied_visible";
        }
        return "selected_without_finite_initial";
    }

    private static String slabbed$focusedAnchoredRejectionReason(
            BlockHitResult anchored,
            double initialDist2,
            double anchoredDist2
    ) {
        if (anchored == null) {
            return "rejected_no_visible_anchored_candidate";
        }
        if (Double.isFinite(initialDist2) && anchoredDist2 > initialDist2 + 1.0e-6) {
            return "rejected_not_closer";
        }
        return "none";
    }

    private static String slabbed$sourceMode(ClientLevel world, BlockPos pos, BlockState state) {
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean persistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state);
        return persistentCarrier
                ? "persistentLoweredSlabCarrier"
                : (Math.abs(dy + 0.5d) <= 1.0e-6 ? "dynamicLoweredOrAnchored" : "normal");
    }

    private static void slabbed$appendSourceTruth(
            StringBuilder line,
            ClientLevel world,
            BlockPos pos,
            String prefix
    ) {
        line.append(' ').append(prefix).append("SourceTruth=");
        if (pos == null) {
            line.append("none");
            return;
        }
        slabbed$appendBlockTruth(line, world, pos, "self");
        slabbed$appendBlockTruth(line, world, pos.below(), "below");
        slabbed$appendBlockTruth(line, world, pos.above(), "above");
        slabbed$appendBlockTruth(line, world, pos.north(), "northNeighbor");
        slabbed$appendBlockTruth(line, world, pos.south(), "southNeighbor");
        slabbed$appendBlockTruth(line, world, pos.east(), "eastNeighbor");
        slabbed$appendBlockTruth(line, world, pos.west(), "westNeighbor");
    }

    private static void slabbed$appendBlockTruth(
            StringBuilder line,
            ClientLevel world,
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
        String slabType = state.hasProperty(SlabBlock.TYPE) ? state.getValue(SlabBlock.TYPE).getSerializedName() : "none";
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
                .append(" solid=").append(state.isSolidRender())
                .append(" sourceMode=").append(sourceMode)
                .append('}');
    }

    private static void slabbed$appendHitFields(
            StringBuilder line, ClientLevel world, BlockHitResult hit, String prefix
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
        line.append(' ').append(prefix).append("Face=").append(hit.getDirection());
        line.append(' ').append(prefix).append("Hit=").append(slabbed$formatVec(hit.getLocation()));
        line.append(' ').append(prefix).append("State=").append(state);
        line.append(' ').append(prefix).append("Dy=").append(slabbed$formatDouble(dy));
        if ("initial".equals(prefix)) {
            line.append(" initialAnchored=").append(anchored);
            line.append(" initialLowered=").append(lowered);
        }
        line.append(' ').append(prefix).append("OwnerClass=")
                .append(slabbed$ownerClass(world, pos, state));
    }

    private static String slabbed$ownerClass(ClientLevel world, BlockPos pos, BlockState state) {
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

        ClientLevel world = this.slabbed$self().level;
        Entity cam = this.slabbed$self().getCameraEntity();
        if (world == null || cam == null) {
            return;
        }

        HitResult finalTarget = this.slabbed$self().hitResult;
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

        ItemStack held = this.slabbed$self().player == null ? ItemStack.EMPTY : this.slabbed$self().player.getMainHandItem();
        Vec3 eye = cam.getEyePosition(tickProgress);
        Vec3 look = cam.getViewVector(tickProgress);
        StringBuilder line = new StringBuilder(768);
        line.append("[BETA4_FINAL_TARGET]");
        line.append(" heldItem=").append(held.isEmpty() ? "empty" : held.getItem().getDescriptionId());
        line.append(" cameraPos=").append(slabbed$formatVec(eye));
        if (this.slabbed$self().player != null) {
            line.append(" playerPos=").append(slabbed$formatVec(this.slabbed$self().player.position()));
            line.append(" yaw=").append(String.format("%.3f", this.slabbed$self().player.getYRot()));
            line.append(" pitch=").append(String.format("%.3f", this.slabbed$self().player.getXRot()));
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
            ClientLevel world, HitResult finalTarget, String classification, boolean sideSlabRetargetFired
    ) {
        StringBuilder signature = new StringBuilder(256);
        signature.append(finalTarget.getType());
        signature.append('|').append(classification);
        signature.append('|').append(sideSlabRetargetFired);
        if (finalTarget instanceof BlockHitResult finalHit) {
            BlockPos pos = finalHit.getBlockPos();
            BlockState state = world.getBlockState(pos);
            signature.append('|').append(pos.toShortString());
            signature.append('|').append(finalHit.getDirection());
            signature.append('|').append(state.getBlock());
            signature.append('|').append(String.format("%.3f", SlabSupport.getYOffset(world, pos, state)));
            signature.append('|').append(SlabAnchorAttachment.isAnchored(world, pos));
            signature.append('|').append(SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state));
        }
        return signature.toString();
    }

    private static String slabbed$formatOutlineOwnerShape(ClientLevel world, Entity cam, BlockPos pos, BlockState state) {
        try {
            VoxelShape outline = state.getShape(world, pos, CollisionContext.of(cam));
            if (outline == null || outline.isEmpty()) {
                return "empty";
            }
            return "bounds=" + outline.bounds();
        } catch (Throwable t) {
            return "error:" + t.getClass().getSimpleName();
        }
    }

    private static String slabbed$findAnchoredFbCandidate(
            ClientLevel world, Entity cam, Vec3 eye, Vec3 end, double vanillaDist2
    ) {
        Vec3 dir = end.subtract(eye).normalize();
        double reach = end.distanceTo(eye);
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05));
        for (int i = 1; i <= steps; i++) {
            Vec3 sample = eye.add(dir.scale(reach * i / steps));
            String candidate = slabbed$anchoredFbCandidateAt(world, cam, eye, end, BlockPos.containing(sample), vanillaDist2);
            if (candidate != null) {
                return candidate;
            }
            candidate = slabbed$anchoredFbCandidateAt(world, cam, eye, end, BlockPos.containing(sample).above(), vanillaDist2);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String slabbed$findLoweredSlabCandidate(
            ClientLevel world, Entity cam, Vec3 eye, Vec3 end, double vanillaDist2
    ) {
        Vec3 dir = end.subtract(eye).normalize();
        double reach = end.distanceTo(eye);
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05));
        for (int i = 1; i <= steps; i++) {
            Vec3 sample = eye.add(dir.scale(reach * i / steps));
            String candidate = slabbed$loweredSlabCandidateAt(world, cam, eye, end, BlockPos.containing(sample), vanillaDist2);
            if (candidate != null) {
                return candidate;
            }
            candidate = slabbed$loweredSlabCandidateAt(world, cam, eye, end, BlockPos.containing(sample).above(), vanillaDist2);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String slabbed$anchoredFbCandidateAt(
            ClientLevel world, Entity cam, Vec3 eye, Vec3 end, BlockPos pos, double vanillaDist2
    ) {
        BlockState state = world.getBlockState(pos);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean solid = state.isSolidRender();
        if (!solid || !anchored || dy != -0.5) {
            return null;
        }

        net.minecraft.world.level.block.Block block = state.getBlock();
        if (block instanceof EntityBlock || block instanceof CraftingTableBlock) {
            return null;
        }

        BlockHitResult outlineHit = state.getShape(world, pos, CollisionContext.of(cam)).clip(eye, end, pos);
        if (outlineHit == null) {
            return "anchoredFB{pos=" + pos.toShortString()
                    + " state=" + state
                    + " dy=" + String.format("%.3f", dy)
                    + " anchored=" + anchored
                    + " solid=" + solid
                    + " outline=miss raycast=miss reason=outline-shape-miss}";
        }

        double outlineDist2 = outlineHit.getLocation().distanceToSqr(eye);
        String reason = outlineDist2 > vanillaDist2 + 1.0e-6
                ? "candidate-farther-than-vanilla-hit"
                : "eligible";
        return "anchoredFB{pos=" + pos.toShortString()
                + " state=" + state
                + " dy=" + String.format("%.3f", dy)
                + " anchored=" + anchored
                + " solid=" + solid
                + " outline=" + slabbed$formatHit(outlineHit)
                + " raycast=" + slabbed$formatHit(state.getInteractionShape(world, pos).clip(eye, end, pos))
                + " reason=" + reason
                + "}";
    }

    private static String slabbed$loweredSlabCandidateAt(
            ClientLevel world, Entity cam, Vec3 eye, Vec3 end, BlockPos pos, double vanillaDist2
    ) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof SlabBlock)) {
            return null;
        }

        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean bottom = state.hasProperty(SlabBlock.TYPE) && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
        if (!bottom || dy != -0.5) {
            return null;
        }

        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        boolean solid = state.isSolidRender();
        BlockHitResult outlineHit = state.getShape(world, pos, CollisionContext.of(cam)).clip(eye, end, pos);
        if (outlineHit == null) {
            return "loweredSideSlab{pos=" + pos.toShortString()
                    + " state=" + state
                    + " dy=" + String.format("%.3f", dy)
                    + " anchored=" + anchored
                    + " solid=" + solid
                    + " outline=miss raycast=miss reason=outline-shape-miss}";
        }

        double outlineDist2 = outlineHit.getLocation().distanceToSqr(eye);
        String reason = outlineDist2 > vanillaDist2 + 1.0e-6
                ? "candidate-farther-than-vanilla-hit"
                : "eligible";
        return "loweredSideSlab{pos=" + pos.toShortString()
                + " state=" + state
                + " dy=" + String.format("%.3f", dy)
                + " anchored=" + anchored
                + " solid=" + solid
                + " outline=" + slabbed$formatHit(outlineHit)
                + " raycast=" + slabbed$formatHit(state.getInteractionShape(world, pos).clip(eye, end, pos))
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
        return "type=" + hit.getType() + " hit=" + slabbed$formatVec(hit.getLocation());
    }

    private static String slabbed$formatHit(BlockHitResult hit) {
        if (hit == null) {
            return "miss";
        }
        return "hit=true pos=" + hit.getBlockPos().toShortString()
                + " side=" + hit.getDirection()
                + " hitVec=" + slabbed$formatVec(hit.getLocation());
    }

    private static String slabbed$formatVec(Vec3 vec) {
        if (vec == null) {
            return "null";
        }
        return String.format("%.3f,%.3f,%.3f", vec.x, vec.y, vec.z);
    }

    private static String slabbed$formatDouble(double value) {
        return Double.isNaN(value) ? "NaN" : String.format("%.6f", value);
    }
}
