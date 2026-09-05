package com.slabbed.mixin.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.MinecartEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps minecart rendering at the frozen height of its direct or one-cell-below rail owner. */
@Mixin(MinecartEntityRenderer.class)
public abstract class MinecartRenderOffsetMixin {

    @Inject(method = "render(Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void slabbed$adjustMinecartOffset(AbstractMinecartEntity entity,
                                              float yaw,
                                              float tickDelta,
                                              MatrixStack matrices,
                                              VertexConsumerProvider vertexConsumers,
                                              int light,
                                              CallbackInfo ci) {
        World world = entity.getEntityWorld();
        if (world == null) {
            return;
        }

        BlockPos pos = entity.getBlockPos();
        BlockState blockState = world.getBlockState(pos);

        if (SlabAnchorAttachment.FROZEN_DY_ENABLED) {
            if (!AbstractRailBlock.isRail(blockState)) {
                BlockPos below = pos.down();
                BlockState belowState = world.getBlockState(below);
                if (!AbstractRailBlock.isRail(belowState)) {
                    return;
                }
                pos = below;
                blockState = belowState;
            }
            double dy = SlabSupport.getYOffset(world, pos, blockState);
            if (Double.isFinite(dy) && dy != 0.0d) {
                matrices.translate(0.0d, dy, 0.0d);
            }
            return;
        }

        if (!(blockState.getBlock() instanceof AbstractRailBlock)) {
            return;
        }

        if (SlabSupport.shouldOffset(world, pos, blockState)) {
            matrices.translate(0.0, -0.5, 0.0);
        }
    }
}
