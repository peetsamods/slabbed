package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Offsets item frame rendering down by 0.5 when the block the frame is attached to
 * sits above a bottom slab (or slab chain).
 */
@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameRenderOffsetMixin {
    @Unique
    private static final RenderStateDataKey<Vec3> SLABBED_RENDER_OFFSET =
            RenderStateDataKey.create(() -> "slabbed:item_frame_render_offset");
    @Unique
    private static final Vec3 SLABBED_NO_OFFSET = new Vec3(0.0, 0.0, 0.0);

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
            at = @At("TAIL"))
    private void slabbed$adjustItemFrameOffset(ItemFrame entity,
                                               ItemFrameRenderState state,
                                               float tickDelta,
                                               CallbackInfo ci) {
        state.setData(SLABBED_RENDER_OFFSET, SLABBED_NO_OFFSET);
        Level world = entity.level();
        if (world == null) {
            return;
        }

        BlockPos attachedPos = entity.getPos();
        if (attachedPos == null) {
            return;
        }

        BlockState attachedState = world.getBlockState(attachedPos);

        if (SlabSupport.shouldOffset(world, attachedPos, attachedState)) {
            state.setData(SLABBED_RENDER_OFFSET, new Vec3(0.0, -0.5, 0.0));
        }
    }

    @Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"),
            cancellable = true)
    private void slabbed$applyItemFrameOffset(ItemFrameRenderState state,
                                              CallbackInfoReturnable<Vec3> cir) {
        Vec3 offset = state.getDataOrDefault(SLABBED_RENDER_OFFSET, SLABBED_NO_OFFSET);
        if (offset.x() != 0.0 || offset.y() != 0.0 || offset.z() != 0.0) {
            cir.setReturnValue(cir.getReturnValue().add(offset));
        }
    }
}
