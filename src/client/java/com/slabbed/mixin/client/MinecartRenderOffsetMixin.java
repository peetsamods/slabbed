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

/** Retains the legacy minecart render offset; a cart on a modern rail is positioned physically. */
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

        if (!(blockState.getBlock() instanceof AbstractRailBlock)) {
            return;
        }
        // A cart on a rail with modern provenance is positioned physically (MinecartPhysicalOffsetMixin).
        if (SlabAnchorAttachment.usesFrozenPlacementHeight(world, pos)) {
            return;
        }

        if (SlabSupport.shouldOffset(world, pos, blockState)) {
            matrices.translate(0.0, -0.5, 0.0);
        }
    }
}
