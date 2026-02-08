package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.entity.AbstractMinecartEntityRenderer;
import net.minecraft.client.render.entity.state.MinecartEntityRenderState;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offsets minecart rendering down by 0.5 when the minecart sits on a rail
 * visually anchored to a bottom slab.
 */
@Mixin(AbstractMinecartEntityRenderer.class)
public abstract class MinecartRenderOffsetMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;Lnet/minecraft/client/render/entity/state/MinecartEntityRenderState;F)V",
            at = @At("TAIL"))
    private void slabbed$adjustMinecartOffset(AbstractMinecartEntity entity,
                                              MinecartEntityRenderState state,
                                              float tickDelta,
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

        if (SlabSupport.shouldOffset(world, pos, blockState)) {
            Vec3d current = state.positionOffset;
            if (current != null) {
                state.positionOffset = current.add(0.0, -0.5, 0.0);
            } else {
                state.positionOffset = new Vec3d(0.0, -0.5, 0.0);
            }
        }
    }
}
