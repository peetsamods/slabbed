package com.slabbed.mixin.client;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.debug.SlabbedInspect;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockEntityProvider;
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

    @Inject(method = "updateCrosshairTarget", at = @At("TAIL"))
    private void slabbed$retargetLoweredBlockEntity(float tickProgress, CallbackInfo ci) {
        HitResult ht = client.crosshairTarget;
        if (ht == null) {
            return;
        }
        HitResult initialTarget = ht;
        boolean slabHeld = slabbed$isSlabPlacementIntent();

        if (slabHeld && ht instanceof BlockHitResult blockHit && blockHit.getSide() == Direction.UP) {
            ClientWorld world = client.world;
            BlockPos pos = blockHit.getBlockPos();
            if (world != null && slabbed$isAnchoredLoweredFullBlock(world, pos, world.getBlockState(pos))) {
                slabbed$traceTargeting(tickProgress, initialTarget,
                        "scan-skip-slab-held-anchored-lowered-full-block-up", false);
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
        BlockHitResult loweredSlabHit = slabbed$retargetLoweredSideSlab(tickProgress, ht);
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
            return (slabDist2 <= anchoredDist2 + eps) ? slab : anchored;
        }
        return (anchoredDist2 <= slabDist2 + eps) ? anchored : slab;
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

    private BlockHitResult slabbed$retargetLoweredSideSlab(float tickProgress, HitResult currentHit) {
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

            BlockHitResult hit = slabbed$raycastLoweredSideSlab(world, cam, eye, end, samplePos);
            if (hit != null) {
                double dist2 = hit.getPos().squaredDistanceTo(eye);
                if (dist2 <= bestDist2 + 1.0e-6) {
                    bestHit = hit;
                    bestDist2 = dist2;
                }
            }

            hit = slabbed$raycastLoweredSideSlab(world, cam, eye, end, samplePos.up());
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

    private static BlockHitResult slabbed$raycastLoweredSideSlab(
            ClientWorld world, Entity cam, Vec3d eye, Vec3d end, BlockPos pos
    ) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || (state.get(SlabBlock.TYPE) != SlabType.BOTTOM && state.get(SlabBlock.TYPE) != SlabType.DOUBLE)
                || SlabSupport.getYOffset(world, pos, state) != -0.5) {
            return null;
        }
        VoxelShape shape = state.getOutlineShape(world, pos, ShapeContext.of(cam));
        BlockHitResult hit = shape.raycast(eye, end, pos);
        if (hit == null) {
            return null;
        }
        return hit.getPos().squaredDistanceTo(eye) <= end.squaredDistanceTo(eye) + 1.0e-6 ? hit : null;
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
        VoxelShape shape = state.getOutlineShape(world, pos, ShapeContext.of(cam));
        BlockHitResult hit = shape.raycast(eye, end, pos);
        if (hit == null) {
            return null;
        }
        return hit.getPos().squaredDistanceTo(eye) <= end.squaredDistanceTo(eye) + 1.0e-6 ? hit : null;
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
                && SlabSupport.getYOffset(world, pos, state) == -0.5;
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
        SlabbedInspect.logClientTarget(
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
}
