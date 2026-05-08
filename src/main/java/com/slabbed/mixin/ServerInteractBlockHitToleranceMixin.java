package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
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
            return false;
        }
        Direction face = hit.getSide();
        if (face == Direction.UP || face == Direction.DOWN) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (!slabbed$isOrdinaryFullBlock(world, pos, state)) {
            return false;
        }
        if (!SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)) {
            return false;
        }
        if (Double.compare(SlabSupport.getYOffset(world, pos, state), COMPOUND_DY) != 0) {
            return false;
        }
        if (!slabbed$isHeldOrdinaryFullBlock(world, pos, player.getStackInHand(packet.getHand()))) {
            return false;
        }
        Vec3d hitPos = hit.getPos();
        return hitPos.x >= pos.getX() - EPSILON
                && hitPos.x <= pos.getX() + 1.0d + EPSILON
                && hitPos.y >= pos.getY() + COMPOUND_DY - EPSILON
                && hitPos.y <= pos.getY() + EPSILON
                && hitPos.z >= pos.getZ() - EPSILON
                && hitPos.z <= pos.getZ() + 1.0d + EPSILON;
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
