package com.slabbed.mixin;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.placement.LandingHitValidationPolicy;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerInteractBlockHitToleranceMixin {
    private static final double EPSILON = 1.0e-6d;
    private static final String REPEAT_SEAM_TRACE_OPT_IN = "slabbed.beta4RepeatMergeTrace";

    @Shadow @Final public ServerPlayer player;

    @Inject(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V"),
            cancellable = true
    )
    private void slabbed$finalizeLoweredSameCellSlabMerge(
            ServerboundUseItemOnPacket packet,
            CallbackInfo ci
    ) {
        if (player == null || packet == null) {
            return;
        }
        BlockHitResult hit = packet.getHitResult();
        if (hit == null) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        Vec3 validationCenter = slabbed$loweredSameCellSlabMergeValidationCenter(pos, packet);
        if (validationCenter == null) {
            return;
        }

        ServerLevel world = player.level();
        BlockState state = world.getBlockState(pos);
        BlockState mergedState = state.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        // Opt-in dev trace (default off) — gated by the same property the sibling
        // BlockItemPlacementIntentMixin uses; the gate was previously missing here so these
        // [MAINTAINER_BETA4_*] probes printed on every server merge. Keep them flag-gated, not shipped-on.
        boolean traceEnabled = Boolean.getBoolean(REPEAT_SEAM_TRACE_OPT_IN);
        if (traceEnabled) {
            System.out.println("[MAINTAINER_BETA4_REPEAT_SEAM_PLACEMENT_CONTEXT]"
                    + " phase=server-direct-finalization"
                    + " side=SERVER"
                    + " incomingPos=" + pos.toShortString()
                    + " incomingFace=" + hit.getDirection().getSerializedName()
                    + " incomingHit=" + hit.getLocation()
                    + " incomingState=" + state
                    + " incomingDy=" + SlabSupport.getYOffset(world, pos, state)
                    + " heldItem=" + BuiltInRegistries.ITEM.getKey(player.getItemInHand(packet.getHand()).getItem())
                    + " decision=LOWERED_SAME_CELL_SLAB_MERGE");
        }
        double beforeDy = traceEnabled ? SlabSupport.getYOffset(world, pos, state) : 0.0;
        // GOES C2 (design §1.3.4 / §3): re-store the owner's landing dy for the merged DOUBLE at the
        // server-direct merge finalization (the other DOUBLE-merge site besides the vanilla place-RETURN
        // path). The bottom->DOUBLE state swap is a same-block-KIND change, so the removal hook does not
        // fire and the stored value already survives — this makes the re-store EXPLICIT (single-rule),
        // robust to any future change in the removal-hook semantics.
        double ownerStoredDy = SlabAnchorAttachment.storedPlacementDy(world, pos);
        boolean changed = world.setBlock(pos, mergedState, Block.UPDATE_ALL);
        if (changed && !Double.isNaN(ownerStoredDy)) {
            SlabAnchorAttachment.writePlacementDy(world, pos, ownerStoredDy);
        }
        if (traceEnabled) {
            System.out.println("[MAINTAINER_BETA4_REPEAT_SEAM_PLACEMENT_EXIT]"
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
        if (!player.isCreative()) {
            player.getItemInHand(packet.getHand()).consume(1, player);
        }
        player.swing(packet.getHand(), true);
        ci.cancel();
    }

    @Redirect(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;atCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 slabbed$compoundFullBlockVisualCenter(Vec3i blockPos, ServerboundUseItemOnPacket packet) {
        Vec3 center = Vec3.atCenterOf(blockPos);
        Vec3 loweredSameCellSlabMergeCenter = null;
        Vec3 beta35ShiftedCenter = null;
        if (blockPos instanceof BlockPos pos && player != null && packet != null) {
            loweredSameCellSlabMergeCenter = slabbed$loweredSameCellSlabMergeValidationCenter(pos, packet);
            beta35ShiftedCenter = slabbed$beta35ShiftedValidationCenter(pos, packet, center);
            // 26.1.2 port: recorder diagnostic side effect deferred until core compile is restored.
            slabbed$logRepeatMergeTolerance(pos, packet, center, loweredSameCellSlabMergeCenter);
        }
        if (loweredSameCellSlabMergeCenter != null) {
            return loweredSameCellSlabMergeCenter;
        }
        if (beta35ShiftedCenter != null) {
            return beta35ShiftedCenter;
        }
        if (!(blockPos instanceof BlockPos pos) || player == null || packet == null) {
            return center;
        }
        // GOES C2 (design §1.4 row 4 / R6, review Mismatch B): the server use-packet distance check must
        // recognise a deep visible full-block body at ANY depth, not only exactly -1.0 — else the deepest
        // aims (owners past ~-2.0) are silently swallowed server-side even when the honest raycast targets
        // them. Shift the validation center by the OWNER'S OWN stored/live dy (stored-first authority),
        // parametric to any depth, gated by the same vanilla per-axis tolerance. This SUBSUMES the old
        // -1.0-pinned compound lane. LANE OVERLAP (fix-round MINOR-3, corrected — the prior "no two lanes
        // claim the same target" claim was FALSE): the beta35 lane above is NOT strictly disjoint from
        // this one — its trigger (isBeta35ShiftedHitTarget) also matches a NEIGHBOR-ABOVE contact object
        // and the HELD item, so a deep FULL BLOCK with e.g. a fence on top, or aimed with a fence in
        // hand, is claimed by beta35 FIRST (it pre-empts by ordering). That is benign, not a two-answer
        // disease: both lanes shift the center by the same owner's stored-first depth authority
        // (getBeta35ShiftedServerValidationYOffset reads the store first via getYOffset, exactly as
        // slabbed$ownerVisibleDy below does), so whichever lane fires produces the same shifted center.
        // This lane is the ANY-DEPTH backstop for ordinary full blocks the beta35 trigger does not match.
        double compoundDy = slabbed$legalCompoundFullBlockVisualHitDy(pos, packet);
        if (Double.isNaN(compoundDy)) {
            return center;
        }
        return center.add(0.0d, compoundDy, 0.0d);
    }

    private Vec3 slabbed$beta35ShiftedValidationCenter(
            BlockPos pos,
            ServerboundUseItemOnPacket packet,
            Vec3 center
    ) {
        ServerLevel world = player.level();
        BlockHitResult hit = packet.getHitResult();
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
        Vec3 shiftedCenter = center.add(0.0d, targetDy, 0.0d);
        Vec3 delta = hit.getLocation().subtract(shiftedCenter);
        return slabbed$isWithinVanillaComponentTolerance(delta) ? shiftedCenter : null;
    }

    private boolean slabbed$isBeta35ShiftedHitTarget(
            ServerLevel world,
            BlockPos pos,
            ServerboundUseItemOnPacket packet
    ) {
        BlockState targetState = world.getBlockState(pos);
        BlockState objectState = world.getBlockState(pos.above());
        ItemStack heldStack = player.getItemInHand(packet.getHand());
        if (SlabSupport.isBeta35FenceWallVariantContactObject(targetState) || targetState.is(Blocks.ANVIL)) {
            return true;
        }
        if (SlabSupport.isBeta35LoweredTrapdoorOrFloorButtonServerHitTarget(world, pos, targetState)) {
            return true;
        }
        if (SlabSupport.isBeta35LoweredRegularDoorServerHitTarget(world, pos, targetState)) {
            return true;
        }
        if (SlabSupport.isBeta35FenceWallVariantContactObject(objectState) || objectState.is(Blocks.ANVIL)) {
            return true;
        }
        if (heldStack != null && heldStack.getItem() instanceof BlockItem blockItem) {
            BlockState heldState = blockItem.getBlock().defaultBlockState();
            return SlabSupport.isBeta35FenceWallVariantContactObject(heldState) || heldState.is(Blocks.ANVIL);
        }
        return false;
    }

    private static boolean slabbed$isWithinVanillaComponentTolerance(Vec3 delta) {
        double tolerance = 1.0000001d;
        return Math.abs(delta.x) < tolerance
                && Math.abs(delta.y) < tolerance
                && Math.abs(delta.z) < tolerance;
    }

    private void slabbed$logRepeatMergeTolerance(
            BlockPos pos,
            ServerboundUseItemOnPacket packet,
            Vec3 center,
            Vec3 loweredSameCellSlabMergeCenter
    ) {
        if (!Boolean.getBoolean(REPEAT_SEAM_TRACE_OPT_IN)) {
            return;
        }
        ServerLevel world = player.level();
        BlockHitResult hit = packet.getHitResult();
        BlockState state = world == null ? null : world.getBlockState(pos);
        ItemStack heldStack = player.getItemInHand(packet.getHand());
        boolean legalLoweredSameCellMerge = loweredSameCellSlabMergeCenter != null;
        Slabbed.LOGGER.info(
                "[MAINTAINER_BETA4_REPEAT_SEAM_SERVER_TOLERANCE] packetBlockPos={} state={} dy={} face={} hitVec={} heldItem={} legalLoweredSameCellMerge={} action={} centerBefore={} centerAfter={} reason={}",
                pos.toShortString(),
                state,
                state == null ? "null" : SlabSupport.getYOffset(world, pos, state),
                hit == null ? "null" : hit.getDirection(),
                hit == null ? "null" : hit.getLocation(),
                heldStack == null ? "null" : BuiltInRegistries.ITEM.getKey(heldStack.getItem()),
                legalLoweredSameCellMerge,
                legalLoweredSameCellMerge ? "rewrite_to_lowered_same_cell_slab_merge_hit" : "leave_packet_unchanged",
                center,
                legalLoweredSameCellMerge ? loweredSameCellSlabMergeCenter : center,
                legalLoweredSameCellMerge ? "LOWERED_SAME_CELL_SLAB_MERGE" : "not_lowered_same_cell_merge");
    }

    private Vec3 slabbed$loweredSameCellSlabMergeValidationCenter(
            BlockPos pos,
            ServerboundUseItemOnPacket packet
    ) {
        ServerLevel world = player.level();
        BlockHitResult hit = packet.getHitResult();
        BlockState state = world == null ? null : world.getBlockState(pos);
        ItemStack heldStack = player.getItemInHand(packet.getHand());
        if (world == null
                || pos == null
                || state == null
                || hit == null
                || !pos.equals(hit.getBlockPos())
                || !(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)
                || state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE
                || !state.getFluidState().isEmpty()
                || Math.abs(SlabSupport.getYOffset(world, pos, state) + 0.5d) > EPSILON
                || !SlabSupport.isLoweredSideLaneSlabCarrier(world, pos, state)
                || heldStack == null
                || !(heldStack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof SlabBlock)
                || !state.is(blockItem.getBlock())) {
            return null;
        }
        SlabType targetType = state.getValue(SlabBlock.TYPE);
        if ((targetType == SlabType.BOTTOM && hit.getDirection() != Direction.UP)
                || (targetType == SlabType.TOP && hit.getDirection() != Direction.DOWN)) {
            return null;
        }
        Vec3 hitPos = hit.getLocation();
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
     * GOES C2 depth-parametric successor of the old {@code isLegalCompoundFullBlockVisualHit} boolean:
     * returns the OWNER's own lowering dy (stored-first, else live) to shift the validation center by, or
     * {@link Double#NaN} when the hit is not a legal deep-full-block visual hit. Generalises the old
     * exact-{@code -1.0} gate to any depth (R6); the center is shifted by the true depth, and the visual
     * bounds are parametric in that depth.
     */
    private double slabbed$legalCompoundFullBlockVisualHitDy(BlockPos pos, ServerboundUseItemOnPacket packet) {
        ServerLevel world = player.level();
        BlockHitResult hit = packet.getHitResult();
        if (world == null || hit == null || !pos.equals(hit.getBlockPos())) {
            return Double.NaN;
        }
        BlockState state = world.getBlockState(pos);
        double ownerDy = slabbed$ownerVisibleDy(world, pos, state);
        // Any-depth: lowered by the compound path (marker OR a store-frozen deep owner), not just -1.0.
        if (!(ownerDy < -EPSILON)
                && !SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)) {
            return Double.NaN;
        }
        if (!(ownerDy < -EPSILON)) {
            return Double.NaN;
        }
        ItemStack heldStack = player.getItemInHand(packet.getHand());
        BlockState heldState = heldStack != null && heldStack.getItem() instanceof BlockItem blockItem
                ? blockItem.getBlock().defaultBlockState()
                : null;
        return LandingHitValidationPolicy.shiftedCenterDy(
                pos, state, ownerDy, hit.getDirection(), hit.getLocation(), heldState);
    }

    /** Owner's visible lowering dy: the frozen store first (any depth), else the live lane. */
    private static double slabbed$ownerVisibleDy(ServerLevel world, BlockPos pos, BlockState state) {
        double stored = SlabAnchorAttachment.storedPlacementDy(world, pos);
        if (!Double.isNaN(stored)) {
            return stored;
        }
        return SlabSupport.getYOffset(world, pos, state);
    }

}
