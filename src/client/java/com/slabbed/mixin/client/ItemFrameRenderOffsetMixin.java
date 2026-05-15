package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Offsets item frame rendering down by 0.5 when the block the frame is attached to
 * sits above a bottom slab (or slab chain).
 */
@Mixin(ItemFrameEntityRenderer.class)
public abstract class ItemFrameRenderOffsetMixin {

    @Inject(method = "getPositionOffset(Lnet/minecraft/entity/decoration/ItemFrameEntity;F)Lnet/minecraft/util/math/Vec3d;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$adjustItemFrameOffset(ItemFrameEntity entity,
                                               float tickDelta,
                                               CallbackInfoReturnable<Vec3d> cir) {
        World world = entity.getEntityWorld();
        if (world == null) {
            return;
        }

        BlockPos attachedPos = entity.getAttachedBlockPos();
        if (attachedPos == null) {
            return;
        }

        BlockState attachedState = world.getBlockState(attachedPos);

        if (SlabSupport.shouldOffset(world, attachedPos, attachedState)) {
            Vec3d current = cir.getReturnValue();
            cir.setReturnValue((current == null ? Vec3d.ZERO : current).add(0.0, -0.5, 0.0));
        }
    }
}
