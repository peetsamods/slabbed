package com.slabbed.mixin;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.Beta4ManualLiveTrace;
import com.slabbed.util.Beta35FenceWallLiveInspectRecorder;
import com.slabbed.util.Beta35SlabHeightHitAcceptanceRecorder;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerInteractBlockHitToleranceMixin {
    private static final double COMPOUND_DY = -1.0d;
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

        ServerWorld world = player.getEntityWorld();
        BlockState state = world.getBlockState(pos);
        BlockState mergedState = state.with(SlabBlock.TYPE, SlabType.DOUBLE);
        System.out.println("[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_CONTEXT]"
                + " phase=server-direct-finalization"
                + " side=SERVER"
                + " incomingPos=" + pos.toShortString()
                + " incomingFace=" + hit.getSide().asString()
                + " incomingHit=" + hit.getPos()
                + " incomingState=" + state
                + " incomingDy=" + SlabSupport.getYOffset(world, pos, state)
                + " heldItem=" + Registries.ITEM.getId(player.getStackInHand(packet.getHand()).getItem())
                + " decision=LOWERED_SAME_CELL_SLAB_MERGE");
        double beforeDy = SlabSupport.getYOffset(world, pos, state);
        boolean changed = world.setBlockState(pos, mergedState, Block.NOTIFY_ALL);
        System.out.println("[JULIA_BETA4_REPEAT_SEAM_PLACEMENT_EXIT]"
                + " phase=server-direct-finalization"
                + " side=SERVER"
                + " target=" + pos.toShortString()
                + " beforeState=" + state
                + " beforeDy=" + beforeDy
                + " afterState=" + world.getBlockState(pos)
                + " afterDy=" + SlabSupport.getYOffset(world, pos, world.getBlockState(pos))
                + " setBlockStateDurable=" + (changed ? "YES" : "NO"));
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
            Beta35FenceWallLiveInspectRecorder.logServerTolerance(
                    player.getEntityWorld(),
                    player,
                    packet.getBlockHitResult(),
                    player.getStackInHand(packet.getHand()),
                    center,
                    beta35ShiftedCenter);
            Beta35SlabHeightHitAcceptanceRecorder.logServerTolerance(
                    player.getEntityWorld(),
                    player,
                    packet.getBlockHitResult(),
                    player.getStackInHand(packet.getHand()),
                    center,
                    beta35ShiftedCenter);
            slabbed$logRepeatMergeTolerance(pos, packet, center, loweredSameCellSlabMergeCenter);
        }
        if (loweredSameCellSlabMergeCenter != null) {
            Beta4ManualLiveTrace.logServerTolerance(
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
        if (!(blockPos instanceof BlockPos pos)
                || player == null
                || packet == null
                || !slabbed$isLegalCompoundFullBlockVisualHit(pos, packet)) {
            if (blockPos instanceof BlockPos rawPos && player != null && packet != null) {
                Beta4ManualLiveTrace.logServerTolerance(
                        player.getEntityWorld(),
                        packet.getBlockHitResult(),
                        player.getStackInHand(packet.getHand()),
                        center,
                        center,
                        "leave_packet_unchanged");
            }
            return center;
        }
        Beta4ManualLiveTrace.logServerTolerance(
                player.getEntityWorld(),
                packet.getBlockHitResult(),
                player.getStackInHand(packet.getHand()),
                center,
                center.add(0.0d, COMPOUND_DY, 0.0d),
                "compound_full_block_visual_hit");
        return center.add(0.0d, COMPOUND_DY, 0.0d);
    }

    private Vec3d slabbed$beta35ShiftedValidationCenter(
            BlockPos pos,
            PlayerInteractBlockC2SPacket packet,
            Vec3d center
    ) {
        ServerWorld world = player.getEntityWorld();
        BlockHitResult hit = packet.getBlockHitResult();
        if (world == null || hit == null || center == null || !pos.equals(hit.getBlockPos())) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        double targetDy = SlabSupport.getYOffset(world, pos, state);
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
            ServerWorld world,
            BlockPos pos,
            PlayerInteractBlockC2SPacket packet
    ) {
        BlockState targetState = world.getBlockState(pos);
        BlockState objectState = world.getBlockState(pos.up());
        ItemStack heldStack = player.getStackInHand(packet.getHand());
        if (SlabSupport.isBeta35FenceWallVariantContactObject(targetState) || targetState.isOf(net.minecraft.block.Blocks.ANVIL)) {
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
        ServerWorld world = player.getEntityWorld();
        BlockHitResult hit = packet.getBlockHitResult();
        BlockState state = world == null ? null : world.getBlockState(pos);
        ItemStack heldStack = player.getStackInHand(packet.getHand());
        boolean legalLoweredSameCellMerge = loweredSameCellSlabMergeCenter != null;
        Slabbed.LOGGER.info(
                "[JULIA_BETA4_REPEAT_SEAM_SERVER_TOLERANCE] packetBlockPos={} state={} dy={} face={} hitVec={} heldItem={} legalLoweredSameCellMerge={} action={} centerBefore={} centerAfter={} reason={}",
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
        ServerWorld world = player.getEntityWorld();
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

    private boolean slabbed$isLegalCompoundFullBlockVisualHit(BlockPos pos, PlayerInteractBlockC2SPacket packet) {
        ServerWorld world = player.getEntityWorld();
        BlockHitResult hit = packet.getBlockHitResult();
        if (world == null || hit == null || !pos.equals(hit.getBlockPos())) {
            slabbed$logHitValidityBridge(world, pos, hit, null, false, false, false,
                    false, "wrong_block_pos_or_missing_hit");
            return false;
        }
        Direction face = hit.getSide();
        BlockState state = world.getBlockState(pos);
        if (!slabbed$isOrdinaryFullBlock(world, pos, state)) {
            slabbed$logHitValidityBridge(world, pos, hit, player.getStackInHand(packet.getHand()), false, false,
                    slabbed$isInsideCompoundVisualBounds(pos, hit.getPos()), false, "target_not_ordinary_full_block");
            return false;
        }
        if (!SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)) {
            slabbed$logHitValidityBridge(world, pos, hit, player.getStackInHand(packet.getHand()), true, false,
                    slabbed$isInsideCompoundVisualBounds(pos, hit.getPos()), false, "not_compound_anchor");
            return false;
        }
        if (Double.compare(SlabSupport.getYOffset(world, pos, state), COMPOUND_DY) != 0) {
            slabbed$logHitValidityBridge(world, pos, hit, player.getStackInHand(packet.getHand()), true, true,
                    slabbed$isInsideCompoundVisualBounds(pos, hit.getPos()), false, "dy_not_minus_one");
            return false;
        }
        ItemStack heldStack = player.getStackInHand(packet.getHand());
        boolean heldOrdinaryFullBlock = slabbed$isHeldOrdinaryFullBlock(world, pos, heldStack);
        boolean heldLegalCompoundSlabRemap = slabbed$isHeldLegalCompoundSlabRemap(world, pos, state, hit, heldStack);
        if ((face == Direction.UP || face == Direction.DOWN) && !heldLegalCompoundSlabRemap) {
            slabbed$logHitValidityBridge(world, pos, hit, heldStack, true, true,
                    slabbed$isInsideCompoundVisualBounds(pos, hit.getPos()), false, "face_vertical");
            return false;
        }
        if (!heldOrdinaryFullBlock && !heldLegalCompoundSlabRemap) {
            slabbed$logHitValidityBridge(world, pos, hit, heldStack, true, true,
                    slabbed$isInsideCompoundVisualBounds(pos, hit.getPos()), false,
                    slabbed$heldRejectionReason(heldStack));
            return false;
        }
        Vec3d hitPos = hit.getPos();
        boolean insideVisualBounds = slabbed$isInsideCompoundVisualBounds(pos, hitPos);
        slabbed$logHitValidityBridge(world, pos, hit, heldStack, true, true,
                insideVisualBounds, insideVisualBounds,
                insideVisualBounds ? "accepted" : "hit_outside_visual_bounds");
        return insideVisualBounds;
    }

    private static boolean slabbed$isInsideCompoundVisualBounds(BlockPos pos, Vec3d hitPos) {
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
            ServerWorld world,
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
                heldStack == null ? "null" : Registries.ITEM.getId(heldStack.getItem()),
                heldIsSlab,
                pos == null ? "null" : pos.toShortString(),
                hit == null ? "null" : hit.getBlockPos().toShortString(),
                state,
                state == null ? "null" : SlabSupport.getYOffset(world, pos, state),
                compoundFullBlockAnchor,
                hit == null ? "null" : hit.getSide(),
                hit == null ? "null" : hit.getPos(),
                hitInsideVisualBounds,
                ordinaryFullBlockTarget,
                ordinaryFullBlockHeld,
                bridgeAccepted,
                bridgeAccepted ? "none" : rejectionReason);
    }

    private static boolean slabbed$isHeldOrdinaryFullBlock(ServerWorld world, BlockPos pos, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || blockItem.getBlock() instanceof SlabBlock) {
            return false;
        }
        return slabbed$isOrdinaryFullBlock(world, pos, blockItem.getBlock().getDefaultState());
    }

    private static boolean slabbed$isHeldLegalCompoundSlabRemap(
            ServerWorld world,
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
        return SlabSupport.findLegalCompoundSlabRemap(world, pos, state, hit.getSide(), hit.getPos()).legal();
    }

    private static boolean slabbed$isOrdinaryFullBlock(ServerWorld world, BlockPos pos, BlockState state) {
        return state != null
                && !state.isAir()
                && !(state.getBlock() instanceof SlabBlock)
                && state.isFullCube(world, pos);
    }
}
