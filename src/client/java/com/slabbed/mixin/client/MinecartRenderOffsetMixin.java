package com.slabbed.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slabbed.client.ClientDy;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offsets minecart rendering when the minecart sits on a Slabbed-lowered rail.
 */
@Mixin(MinecartRenderer.class)
public abstract class MinecartRenderOffsetMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/vehicle/AbstractMinecart;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void slabbed$adjustMinecartOffset(AbstractMinecart entity,
                                              float yaw,
                                              float tickDelta,
                                              PoseStack matrices,
                                              MultiBufferSource vertexConsumers,
                                              int light,
                                              CallbackInfo ci) {
        Level world = entity.level();
        if (world == null) {
            return;
        }

        BlockPos pos = entity.blockPosition();
        BlockState blockState = world.getBlockState(pos);

        if (!(blockState.getBlock() instanceof BaseRailBlock)) {
            return;
        }

        double dy = ClientDy.dyFor(world, pos, blockState);
        if (dy != 0.0) {
            matrices.translate(0.0, dy, 0.0);
        }
    }
}
