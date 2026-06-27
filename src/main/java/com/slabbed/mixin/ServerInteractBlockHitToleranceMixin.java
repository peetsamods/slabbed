package com.slabbed.mixin;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.RuntimeDiagnostics;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
    private static final double COMPOUND_DY = -1.0d;
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

        Level world = player.level();
        BlockState state = world.getBlockState(pos);
        BlockState mergedState = state.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        if (Boolean.getBoolean(REPEAT_SEAM_TRACE_OPT_IN)) {
            Slabbed.LOGGER.info("[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_CONTEXT]"
                    + " phase=server-direct-finalization"
                    + " side=SERVER"
                    + " incomingPos=" + slabbed$shortPos(pos)
                    + " incomingFace=" + hit.getDirection().getSerializedName()
                    + " incomingHit=" + hit.getLocation()
                    + " incomingState=" + state
                    + " incomingDy=" + SlabSupport.getYOffset(world, pos, state)
                    + " heldItem=" + BuiltInRegistries.ITEM.getKey(player.getItemInHand(packet.getHand()).getItem())
                    + " decision=LOWERED_SAME_CELL_SLAB_MERGE");
        }
        double beforeDy = SlabSupport.getYOffset(world, pos, state);
        boolean changed = world.setBlock(pos, mergedState, Block.UPDATE_ALL);
        if (Boolean.getBoolean(REPEAT_SEAM_TRACE_OPT_IN)) {
            Slabbed.LOGGER.info("[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_EXIT]"
                    + " phase=server-direct-finalization"
                    + " side=SERVER"
                    + " target=" + slabbed$shortPos(pos)
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
            RuntimeDiagnostics.logFenceWallServerTolerance(
                    player.level(),
                    player,
                    packet.getHitResult(),
                    player.getItemInHand(packet.getHand()),
                    center,
                    beta35ShiftedCenter);
            RuntimeDiagnostics.logSlabHeightServerTolerance(
                    player.level(),
                    player,
                    packet.getHitResult(),
                    player.getItemInHand(packet.getHand()),
                    center,
                    beta35ShiftedCenter);
            slabbed$logRepeatMergeTolerance(pos, packet, center, loweredSameCellSlabMergeCenter);
        }
        if (loweredSameCellSlabMergeCenter != null) {
            RuntimeDiagnostics.logManualServerTolerance(
                    player.level(),
                    packet.getHitResult(),
                    player.getItemInHand(packet.getHand()),
                    center,
                    loweredSameCellSlabMergeCenter,
                    "LOWERED_SAME_CELL_SLAB_MERGE");
            return loweredSameCellSlabMergeCenter;
        }
        if (beta35ShiftedCenter != null) {
            return beta35ShiftedCenter;
        }
        if (!(blockPos instanceof BlockPos pos)
                || player == null
                || packet == null
                || !slabbed$isLegalCompoundFullBlockVisualHit(pos, packet)) {
            if (blockPos instanceof BlockPos rawPos && player != null && packet != null) {
                RuntimeDiagnostics.logManualServerTolerance(
                        player.level(),
                        packet.getHitResult(),
                        player.getItemInHand(packet.getHand()),
                        center,
                        center,
                        "leave_packet_unchanged");
            }
            return center;
        }
        RuntimeDiagnostics.logManualServerTolerance(
                player.level(),
                packet.getHitResult(),
                player.getItemInHand(packet.getHand()),
                center,
                center.add(0.0d, COMPOUND_DY, 0.0d),
                "compound_full_block_visual_hit");
        return center.add(0.0d, COMPOUND_DY, 0.0d);
    }

    private Vec3 slabbed$beta35ShiftedValidationCenter(
            BlockPos pos,
            ServerboundUseItemOnPacket packet,
            Vec3 center
    ) {
        Level world = player.level();
        BlockHitResult hit = packet.getHitResult();
        if (world == null || hit == null || center == null || !pos.equals(hit.getBlockPos())) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        double targetDy = SlabSupport.getBeta35ShiftedServerValidationYOffset(world, pos, state);
        if (!Double.isFinite(targetDy) || Math.abs(targetDy) <= EPSILON) {
            return null;
        }
        if (targetDy > EPSILON && !SlabSupport.isBeta35PointedDripstoneServerHitTarget(world, pos, state)) {
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
            Level world,
            BlockPos pos,
            ServerboundUseItemOnPacket packet
    ) {
        BlockState targetState = world.getBlockState(pos);
        BlockState objectState = world.getBlockState(pos.above());
        ItemStack heldStack = player.getItemInHand(packet.getHand());
        if (SlabSupport.isBeta35FenceWallVariantContactObject(targetState) || targetState.is(net.minecraft.world.level.block.Blocks.ANVIL)) {
            return true;
        }
        if (SlabSupport.isBeta35PointedDripstoneServerHitTarget(world, pos, targetState)) {
            return true;
        }
        if (SlabSupport.isBeta35LoweredTrapdoorOrFloorButtonServerHitTarget(world, pos, targetState)) {
            return true;
        }
        if (SlabSupport.isBeta35LoweredRegularDoorServerHitTarget(world, pos, targetState)) {
            return true;
        }
        if (SlabSupport.isBeta35FenceWallVariantContactObject(objectState) || objectState.is(net.minecraft.world.level.block.Blocks.ANVIL)) {
            return true;
        }
        if (heldStack != null && heldStack.getItem() instanceof BlockItem blockItem) {
            BlockState heldState = blockItem.getBlock().defaultBlockState();
            return SlabSupport.isBeta35FenceWallVariantContactObject(heldState)
                    || SlabSupport.isBeta35PointedDripstoneServerHitTarget(world, pos, targetState)
                    || heldState.is(net.minecraft.world.level.block.Blocks.ANVIL);
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
        Level world = player.level();
        BlockHitResult hit = packet.getHitResult();
        BlockState state = world == null ? null : world.getBlockState(pos);
        ItemStack heldStack = player.getItemInHand(packet.getHand());
        boolean legalLoweredSameCellMerge = loweredSameCellSlabMergeCenter != null;
        Slabbed.LOGGER.info(
                "[JULIA_BETA4_REPEAT_SEAM_SERVER_TOLERANCE] packetBlockPos={} state={} dy={} face={} hitVec={} heldItem={} legalLoweredSameCellMerge={} action={} centerBefore={} centerAfter={} reason={}",
                slabbed$shortPos(pos),
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
        Level world = player.level();
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

    private boolean slabbed$isLegalCompoundFullBlockVisualHit(BlockPos pos, ServerboundUseItemOnPacket packet) {
        Level world = player.level();
        BlockHitResult hit = packet.getHitResult();
        if (world == null || hit == null || !pos.equals(hit.getBlockPos())) {
            slabbed$logHitValidityBridge(world, pos, hit, null, false, false, false,
                    false, "wrong_block_pos_or_missing_hit");
            return false;
        }
        Direction face = hit.getDirection();
        BlockState state = world.getBlockState(pos);
        if (!slabbed$isOrdinaryFullBlock(world, pos, state)) {
            slabbed$logHitValidityBridge(world, pos, hit, player.getItemInHand(packet.getHand()), false, false,
                    slabbed$isInsideCompoundVisualBounds(pos, hit.getLocation()), false, "target_not_ordinary_full_block");
            return false;
        }
        if (!SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)) {
            slabbed$logHitValidityBridge(world, pos, hit, player.getItemInHand(packet.getHand()), true, false,
                    slabbed$isInsideCompoundVisualBounds(pos, hit.getLocation()), false, "not_compound_anchor");
            return false;
        }
        if (Double.compare(SlabSupport.getYOffset(world, pos, state), COMPOUND_DY) != 0) {
            slabbed$logHitValidityBridge(world, pos, hit, player.getItemInHand(packet.getHand()), true, true,
                    slabbed$isInsideCompoundVisualBounds(pos, hit.getLocation()), false, "dy_not_minus_one");
            return false;
        }
        ItemStack heldStack = player.getItemInHand(packet.getHand());
        boolean heldOrdinaryFullBlock = slabbed$isHeldOrdinaryFullBlock(world, pos, heldStack);
        boolean heldLegalCompoundSlabRemap = slabbed$isHeldLegalCompoundSlabRemap(world, pos, state, hit, heldStack);
        if ((face == Direction.UP || face == Direction.DOWN) && !heldLegalCompoundSlabRemap) {
            slabbed$logHitValidityBridge(world, pos, hit, heldStack, true, true,
                    slabbed$isInsideCompoundVisualBounds(pos, hit.getLocation()), false, "face_vertical");
            return false;
        }
        if (!heldOrdinaryFullBlock && !heldLegalCompoundSlabRemap) {
            slabbed$logHitValidityBridge(world, pos, hit, heldStack, true, true,
                    slabbed$isInsideCompoundVisualBounds(pos, hit.getLocation()), false,
                    slabbed$heldRejectionReason(heldStack));
            return false;
        }
        Vec3 hitPos = hit.getLocation();
        boolean insideVisualBounds = slabbed$isInsideCompoundVisualBounds(pos, hitPos);
        slabbed$logHitValidityBridge(world, pos, hit, heldStack, true, true,
                insideVisualBounds, insideVisualBounds,
                insideVisualBounds ? "accepted" : "hit_outside_visual_bounds");
        return insideVisualBounds;
    }

    private static boolean slabbed$isInsideCompoundVisualBounds(BlockPos pos, Vec3 hitPos) {
        return hitPos != null
                && hitPos.x >= pos.getX() - EPSILON
                && hitPos.x <= pos.getX() + 1.0d + EPSILON
                && hitPos.y >= pos.getY() + COMPOUND_DY - EPSILON
                && hitPos.y <= pos.getY() + EPSILON
                && hitPos.z >= pos.getZ() - EPSILON
                && hitPos.z <= pos.getZ() + 1.0d + EPSILON;
    }

    private static String slabbed$heldRejectionReason(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof SlabBlock) {
            return "held_item_slab";
        }
        return "held_item_not_full_block";
    }

    private void slabbed$logHitValidityBridge(
            Level world,
            BlockPos pos,
            BlockHitResult hit,
            ItemStack heldStack,
            boolean ordinaryFullBlockTarget,
            boolean compoundFullBlockAnchor,
            boolean hitInsideVisualBounds,
            boolean bridgeAccepted,
            String rejectionReason
    ) {
        if (!RuntimeDiagnostics.compoundLivePathEnabled()) {
            return;
        }
        BlockState state = world == null || pos == null ? null : world.getBlockState(pos);
        boolean heldIsSlab = heldStack != null
                && heldStack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof SlabBlock;
        boolean ordinaryFullBlockHeld = world != null
                && pos != null
                && heldStack != null
                && slabbed$isHeldOrdinaryFullBlock(world, pos, heldStack);
        Slabbed.LOGGER.info(
                "[BETA4_COMPOUND_HIT_VALIDITY_BRIDGE] heldItem={} heldIsSlab={} blockPos={} packetBlockPos={} state={} dy={} compoundFullBlockAnchor={} face={} hitVec={} hitInsideVisualBounds={} ordinaryFullBlockTarget={} ordinaryFullBlockHeld={} bridgeAccepted={} rejectionReason={}",
                heldStack == null ? "null" : BuiltInRegistries.ITEM.getKey(heldStack.getItem()),
                heldIsSlab,
                pos == null ? "null" : slabbed$shortPos(pos),
                hit == null ? "null" : slabbed$shortPos(hit.getBlockPos()),
                state,
                state == null ? "null" : SlabSupport.getYOffset(world, pos, state),
                compoundFullBlockAnchor,
                hit == null ? "null" : hit.getDirection(),
                hit == null ? "null" : hit.getLocation(),
                hitInsideVisualBounds,
                ordinaryFullBlockTarget,
                ordinaryFullBlockHeld,
                bridgeAccepted,
                bridgeAccepted ? "none" : rejectionReason);
    }

    private static boolean slabbed$isHeldOrdinaryFullBlock(Level world, BlockPos pos, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || blockItem.getBlock() instanceof SlabBlock) {
            return false;
        }
        return slabbed$isOrdinaryFullBlock(world, pos, blockItem.getBlock().defaultBlockState());
    }

    private static boolean slabbed$isHeldLegalCompoundSlabRemap(
            Level world,
            BlockPos pos,
            BlockState state,
            BlockHitResult hit,
            ItemStack stack
    ) {
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof SlabBlock)
                || hit == null) {
            return false;
        }
        return SlabSupport.findLegalCompoundSlabRemap(world, pos, state, hit.getDirection(), hit.getLocation()).legal();
    }

    private static boolean slabbed$isOrdinaryFullBlock(Level world, BlockPos pos, BlockState state) {
        return state != null
                && !state.isAir()
                && !(state.getBlock() instanceof SlabBlock)
                && state.isSolidRender(world, pos);
    }

    private static String slabbed$shortPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
