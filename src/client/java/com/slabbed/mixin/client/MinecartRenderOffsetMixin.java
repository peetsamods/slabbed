package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Offsets minecart rendering down by 0.5 when the minecart sits on a rail
 * visually anchored to a bottom slab.
 */
@Mixin(AbstractMinecartRenderer.class)
public abstract class MinecartRenderOffsetMixin {
    @Unique
    private static final RenderStateDataKey<Vec3> SLABBED_RENDER_OFFSET =
            RenderStateDataKey.create(() -> "slabbed:minecart_render_offset");
    @Unique
    private static final Vec3 SLABBED_NO_OFFSET = new Vec3(0.0, 0.0, 0.0);

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;F)V",
            at = @At("TAIL"))
    private void slabbed$adjustMinecartOffset(AbstractMinecart entity,
                                              MinecartRenderState state,
                                              float tickDelta,
                                              CallbackInfo ci) {
        state.setData(SLABBED_RENDER_OFFSET, SLABBED_NO_OFFSET);
        Level world = entity.level();
        if (world == null) {
            return;
        }

        BlockPos pos = entity.blockPosition();
        BlockState blockState = world.getBlockState(pos);

        if (!(blockState.getBlock() instanceof BaseRailBlock)) {
            return;
        }

        if (SlabSupport.shouldOffset(world, pos, blockState)) {
            state.setData(SLABBED_RENDER_OFFSET, new Vec3(0.0, -0.5, 0.0));
        }
    }

    @Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"),
            cancellable = true)
    private void slabbed$applyMinecartOffset(MinecartRenderState state,
                                             CallbackInfoReturnable<Vec3> cir) {
        Vec3 offset = state.getDataOrDefault(SLABBED_RENDER_OFFSET, SLABBED_NO_OFFSET);
        if (offset.x() != 0.0 || offset.y() != 0.0 || offset.z() != 0.0) {
            cir.setReturnValue(cir.getReturnValue().add(offset));
        }
    }
}
