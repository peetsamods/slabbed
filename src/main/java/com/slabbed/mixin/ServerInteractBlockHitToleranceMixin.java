package com.slabbed.mixin;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.placement.LandingHitValidationPolicy;
import com.slabbed.util.RuntimeDiagnostics;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerInteractBlockHitToleranceMixin {
    private static final double EPSILON = 1.0e-6d;
    private static final String REPEAT_SEAM_TRACE_OPT_IN = "slabbed.beta4RepeatMergeTrace";

    @Shadow @Final public ServerPlayerEntity player;

    @Inject(
            method = "onPlayerInteractBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;updateLastActionTime()V"),
            cancellable = true
    )
    private void slabbed$finalizeLoweredSameCellSlabMerge(
            PlayerInteractBlockC2SPacket packet,
            CallbackInfo ci
    ) {
        if (player == null || packet == null) {
            return;
        }
        BlockHitResult hit = packet.getBlockHitResult();
        if (hit == null) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        Vec3d validationCenter = slabbed$loweredSameCellSlabMergeValidationCenter(pos, packet);
        if (validationCenter == null) {
            return;
        }

        World world = player.getEntityWorld();
        BlockState state = world.getBlockState(pos);
        BlockState mergedState = state.with(SlabBlock.TYPE, SlabType.DOUBLE);
        if (Boolean.getBoolean(REPEAT_SEAM_TRACE_OPT_IN)) {
            Slabbed.LOGGER.info("[MAINTAINER_BETA4_REPEAT_SEAM_PLACEMENT_CONTEXT]"
                    + " phase=server-direct-finalization"
                    + " side=SERVER"
                    + " incomingPos=" + pos.toShortString()
                    + " incomingFace=" + hit.getSide().asString()
                    + " incomingHit=" + hit.getPos()
                    + " incomingState=" + state
                    + " incomingDy=" + SlabSupport.getYOffset(world, pos, state)
                    + " heldItem=" + Registries.ITEM.getId(player.getStackInHand(packet.getHand()).getItem())
                    + " decision=LOWERED_SAME_CELL_SLAB_MERGE");
        }
        double beforeDy = SlabSupport.getYOffset(world, pos, state);
        boolean changed = world.setBlockState(pos, mergedState, Block.NOTIFY_ALL);
        if (Boolean.getBoolean(REPEAT_SEAM_TRACE_OPT_IN)) {
            Slabbed.LOGGER.info("[MAINTAINER_BETA4_REPEAT_SEAM_PLACEMENT_EXIT]"
                    + " phase=server-direct-finalization"
                    + " side=SERVER"
                    + " target=" + pos.toShortString()
                    + " beforeState=" + state
                    + " beforeDy=" + beforeDy
                    + " afterState=" + world.getBlockState(pos)
                    + " afterDy=" + SlabSupport.getYOffset(world, pos, world.getBlockState(pos))
                    + " setBlockStateDurable=" + (changed ? "YES" : "NO"));
        }
        if (!changed) {
            return;
        }
        if (!player.isInCreativeMode()) {
            player.getStackInHand(packet.getHand()).decrementUnlessCreative(1, player);
        }
        player.swingHand(packet.getHand(), true);
        ci.cancel();
    }

    @Redirect(
            method = "onPlayerInteractBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Vec3d;ofCenter(Lnet/minecraft/util/math/Vec3i;)Lnet/minecraft/util/math/Vec3d;")
    )
    private Vec3d slabbed$compoundFullBlockVisualCenter(Vec3i blockPos, PlayerInteractBlockC2SPacket packet) {
        Vec3d center = Vec3d.ofCenter(blockPos);
        Vec3d loweredSameCellSlabMergeCenter = null;
        Vec3d beta35ShiftedCenter = null;
        if (blockPos instanceof BlockPos pos && player != null && packet != null) {
            loweredSameCellSlabMergeCenter = slabbed$loweredSameCellSlabMergeValidationCenter(pos, packet);
            beta35ShiftedCenter = slabbed$beta35ShiftedValidationCenter(pos, packet, center);
            RuntimeDiagnostics.logFenceWallServerTolerance(
                    player.getEntityWorld(),
                    player,
                    packet.getBlockHitResult(),
                    player.getStackInHand(packet.getHand()),
                    center,
                    beta35ShiftedCenter);
            RuntimeDiagnostics.logSlabHeightServerTolerance(
                    player.getEntityWorld(),
                    player,
                    packet.getBlockHitResult(),
                    player.getStackInHand(packet.getHand()),
                    center,
                    beta35ShiftedCenter);
            slabbed$logRepeatMergeTolerance(pos, packet, center, loweredSameCellSlabMergeCenter);
        }
        if (loweredSameCellSlabMergeCenter != null) {
            RuntimeDiagnostics.logManualServerTolerance(
                    player.getEntityWorld(),
                    packet.getBlockHitResult(),
                    player.getStackInHand(packet.getHand()),
                    center,
                    loweredSameCellSlabMergeCenter,
                    "LOWERED_SAME_CELL_SLAB_MERGE");
            return loweredSameCellSlabMergeCenter;
        }
        if (beta35ShiftedCenter != null) {
            return beta35ShiftedCenter;
        }
        if (!(blockPos instanceof BlockPos pos) || player == null || packet == null) {
            return center;
        }
        // The server's use-packet distance check must recognise a deep visible body at ANY depth, not
        // only at exactly -1.0 — otherwise every aim at an owner past about -2.0 is thrown away
        // server-side even though the honest raycast targeted it, and the placement simply never
        // happens. Shift the validation center by the OWNER'S OWN stored/live depth (stored first),
        // parametric in that depth, still bounded by vanilla's own per-axis tolerance. This SUBSUMES
        // the retired exactly--1.0 lane.
        //
        // LANE OVERLAP with the beta35 branch above is benign rather than a two-answer disease: that
        // branch also matches a contact object in the cell ABOVE and the HELD item, so a deep full
        // block with e.g. a fence on top is claimed there first — but both branches shift by the same
        // owner's stored-first depth (getBeta35ShiftedServerValidationYOffset reads the store through
        // getYOffset exactly as slabbed$ownerVisibleDy does), so either ordering yields the same
        // center. This lane is the any-depth backstop for the targets beta35 does not match.
        double shiftedDy = slabbed$legalCompoundFullBlockVisualHitDy(pos, packet);
        if (Double.isNaN(shiftedDy)) {
            RuntimeDiagnostics.logManualServerTolerance(
                    player.getEntityWorld(),
                    packet.getBlockHitResult(),
                    player.getStackInHand(packet.getHand()),
                    center,
                    center,
                    "leave_packet_unchanged");
            return center;
        }
        Vec3d shiftedCenter = center.add(0.0d, shiftedDy, 0.0d);
        RuntimeDiagnostics.logManualServerTolerance(
                player.getEntityWorld(),
                packet.getBlockHitResult(),
                player.getStackInHand(packet.getHand()),
                center,
                shiftedCenter,
                "landing_hit_validation_policy_dy=" + shiftedDy);
        return shiftedCenter;
    }

    private Vec3d slabbed$beta35ShiftedValidationCenter(
            BlockPos pos,
            PlayerInteractBlockC2SPacket packet,
            Vec3d center
    ) {
        World world = player.getEntityWorld();
        BlockHitResult hit = packet.getBlockHitResult();
        if (world == null || hit == null || center == null || !pos.equals(hit.getBlockPos())) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        double targetDy = SlabSupport.getBeta35ShiftedServerValidationYOffset(world, pos, state);
        if (!Double.isFinite(targetDy) || targetDy >= -EPSILON) {
            return null;
        }
        if (!slabbed$isBeta35ShiftedHitTarget(world, pos, packet)) {
            return null;
        }
        Vec3d shiftedCenter = center.add(0.0d, targetDy, 0.0d);
        Vec3d delta = hit.getPos().subtract(shiftedCenter);
        return slabbed$isWithinVanillaComponentTolerance(delta) ? shiftedCenter : null;
    }

    private boolean slabbed$isBeta35ShiftedHitTarget(
            World world,
            BlockPos pos,
            PlayerInteractBlockC2SPacket packet
    ) {
        BlockState targetState = world.getBlockState(pos);
        BlockState objectState = world.getBlockState(pos.up());
        ItemStack heldStack = player.getStackInHand(packet.getHand());
        if (SlabSupport.isBeta35FenceWallVariantContactObject(targetState) || targetState.isOf(net.minecraft.block.Blocks.ANVIL)) {
            return true;
        }
        if (SlabSupport.isBeta35LoweredTrapdoorOrFloorButtonServerHitTarget(world, pos, targetState)) {
            return true;
        }
        if (SlabSupport.isBeta35LoweredRegularDoorServerHitTarget(world, pos, targetState)) {
            return true;
        }
        if (SlabSupport.isLoweredPointedDripstoneServerHitTarget(world, pos, targetState)) {
            return true;
        }
        if (SlabSupport.isBeta35FenceWallVariantContactObject(objectState) || objectState.isOf(net.minecraft.block.Blocks.ANVIL)) {
            return true;
        }
        if (heldStack != null && heldStack.getItem() instanceof BlockItem blockItem) {
            BlockState heldState = blockItem.getBlock().getDefaultState();
            return SlabSupport.isBeta35FenceWallVariantContactObject(heldState) || heldState.isOf(net.minecraft.block.Blocks.ANVIL);
        }
        return false;
    }

    private static boolean slabbed$isWithinVanillaComponentTolerance(Vec3d delta) {
        double tolerance = 1.0000001d;
        return Math.abs(delta.x) < tolerance
                && Math.abs(delta.y) < tolerance
                && Math.abs(delta.z) < tolerance;
    }

    private void slabbed$logRepeatMergeTolerance(
            BlockPos pos,
            PlayerInteractBlockC2SPacket packet,
            Vec3d center,
            Vec3d loweredSameCellSlabMergeCenter
    ) {
        if (!Boolean.getBoolean(REPEAT_SEAM_TRACE_OPT_IN)) {
            return;
        }
        World world = player.getEntityWorld();
        BlockHitResult hit = packet.getBlockHitResult();
        BlockState state = world == null ? null : world.getBlockState(pos);
        ItemStack heldStack = player.getStackInHand(packet.getHand());
        boolean legalLoweredSameCellMerge = loweredSameCellSlabMergeCenter != null;
        Slabbed.LOGGER.info(
                "[MAINTAINER_BETA4_REPEAT_SEAM_SERVER_TOLERANCE] packetBlockPos={} state={} dy={} face={} hitVec={} heldItem={} legalLoweredSameCellMerge={} action={} centerBefore={} centerAfter={} reason={}",
                pos.toShortString(),
                state,
                state == null ? "null" : SlabSupport.getYOffset(world, pos, state),
                hit == null ? "null" : hit.getSide(),
                hit == null ? "null" : hit.getPos(),
                heldStack == null ? "null" : Registries.ITEM.getId(heldStack.getItem()),
                legalLoweredSameCellMerge,
                legalLoweredSameCellMerge ? "rewrite_to_lowered_same_cell_slab_merge_hit" : "leave_packet_unchanged",
                center,
                legalLoweredSameCellMerge ? loweredSameCellSlabMergeCenter : center,
                legalLoweredSameCellMerge ? "LOWERED_SAME_CELL_SLAB_MERGE" : "not_lowered_same_cell_merge");
    }

    private Vec3d slabbed$loweredSameCellSlabMergeValidationCenter(
            BlockPos pos,
            PlayerInteractBlockC2SPacket packet
    ) {
        World world = player.getEntityWorld();
        BlockHitResult hit = packet.getBlockHitResult();
        BlockState state = world == null ? null : world.getBlockState(pos);
        ItemStack heldStack = player.getStackInHand(packet.getHand());
        if (world == null
                || pos == null
                || state == null
                || hit == null
                || !pos.equals(hit.getBlockPos())
                || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || state.get(SlabBlock.TYPE) == SlabType.DOUBLE
                || !state.getFluidState().isEmpty()
                || Math.abs(SlabSupport.getYOffset(world, pos, state) + 0.5d) > EPSILON
                || !SlabSupport.isLoweredSideLaneSlabCarrier(world, pos, state)
                || heldStack == null
                || !(heldStack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof SlabBlock)
                || !state.isOf(blockItem.getBlock())) {
            return null;
        }
        SlabType targetType = state.get(SlabBlock.TYPE);
        if ((targetType == SlabType.BOTTOM && hit.getSide() != Direction.UP)
                || (targetType == SlabType.TOP && hit.getSide() != Direction.DOWN)) {
            return null;
        }
        Vec3d hitPos = hit.getPos();
        if (hitPos == null
                || hitPos.x < pos.getX() - EPSILON
                || hitPos.x > pos.getX() + 1.0d + EPSILON
                || hitPos.z < pos.getZ() - EPSILON
                || hitPos.z > pos.getZ() + 1.0d + EPSILON) {
            return null;
        }
        double visualMinY = targetType == SlabType.BOTTOM ? pos.getY() - 0.5d : pos.getY();
        double visualMaxY = targetType == SlabType.BOTTOM ? pos.getY() : pos.getY() + 0.5d;
        if (hitPos.y < visualMinY - EPSILON || hitPos.y > visualMaxY + EPSILON) {
            return null;
        }
        return hitPos;
    }

    /**
     * Depth-parametric successor of the retired exactly--1.0 boolean lane: returns the OWNER's own
     * lowering depth (stored first, else live) to shift the validation center by, or
     * {@link Double#NaN} when the hit is not one the policy admits. The admission decision itself
     * lives in {@link LandingHitValidationPolicy}, shared with the landing resolver so the server
     * accepts exactly the aims the resolver knows how to land.
     */
    private double slabbed$legalCompoundFullBlockVisualHitDy(
            BlockPos pos,
            PlayerInteractBlockC2SPacket packet
    ) {
        World world = player.getEntityWorld();
        BlockHitResult hit = packet.getBlockHitResult();
        if (world == null || hit == null || !pos.equals(hit.getBlockPos())) {
            return Double.NaN;
        }
        BlockState state = world.getBlockState(pos);
        double ownerDy = slabbed$ownerVisibleDy(world, pos, state);
        if (!(ownerDy < -EPSILON)) {
            return Double.NaN;
        }
        ItemStack heldStack = player.getStackInHand(packet.getHand());
        boolean ordinaryTargetUse = heldStack != null
                && (heldStack.isEmpty() || !(heldStack.getItem() instanceof BlockItem));
        BlockState heldState = heldStack != null && heldStack.getItem() instanceof BlockItem blockItem
                ? blockItem.getBlock().getDefaultState()
                : null;
        return LandingHitValidationPolicy.shiftedCenterDy(
                pos, state, ownerDy, hit.getSide(), hit.getPos(), heldState, ordinaryTargetUse);
    }

    /** Owner's visible lowering depth: the frozen store first (any depth), else the live lane. */
    private static double slabbed$ownerVisibleDy(World world, BlockPos pos, BlockState state) {
        double stored = SlabAnchorAttachment.storedPlacementDy(world, pos);
        if (!Double.isNaN(stored)) {
            return stored;
        }
        return SlabSupport.getYOffset(world, pos, state);
    }

}
