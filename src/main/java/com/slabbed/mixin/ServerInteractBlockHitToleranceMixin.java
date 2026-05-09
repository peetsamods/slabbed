package com.slabbed.mixin;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.RuntimeDiagnostics;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
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
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerInteractBlockHitToleranceMixin {
    private static final double COMPOUND_DY = -1.0d;
    private static final double EPSILON = 1.0e-6d;

    @Shadow @Final public ServerPlayerEntity player;

    @Redirect(
            method = "onPlayerInteractBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Vec3d;ofCenter(Lnet/minecraft/util/math/Vec3i;)Lnet/minecraft/util/math/Vec3d;")
    )
    private Vec3d slabbed$compoundFullBlockVisualCenter(Vec3i blockPos, PlayerInteractBlockC2SPacket packet) {
        Vec3d center = Vec3d.ofCenter(blockPos);
        if (!(blockPos instanceof BlockPos pos)
                || player == null
                || packet == null
                || !slabbed$isLegalCompoundFullBlockVisualHit(pos, packet)) {
            return center;
        }
        return center.add(0.0d, COMPOUND_DY, 0.0d);
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
        if (face == Direction.UP || face == Direction.DOWN) {
            slabbed$logHitValidityBridge(world, pos, hit, player.getStackInHand(packet.getHand()), false, false,
                    slabbed$isInsideCompoundVisualBounds(pos, hit.getPos()), false, "face_vertical");
            return false;
        }
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
        if (!slabbed$isHeldOrdinaryFullBlock(world, pos, heldStack)) {
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

    private static boolean slabbed$isOrdinaryFullBlock(ServerWorld world, BlockPos pos, BlockState state) {
        return state != null
                && !state.isAir()
                && !(state.getBlock() instanceof SlabBlock)
                && state.isFullCube(world, pos);
    }
}
