package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Offsets item frame rendering when the block the frame is attached to is Slabbed-lowered.
 */
@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameRenderOffsetMixin {

    @Inject(method = "getRenderOffset(Lnet/minecraft/world/entity/decoration/ItemFrame;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$adjustItemFrameOffset(ItemFrame entity,
                                               float tickDelta,
                                               CallbackInfoReturnable<Vec3> cir) {
        Level world = entity.level();
        if (world == null) {
            return;
        }

        BlockPos attachedPos = entity.getPos();
        if (attachedPos == null) {
            return;
        }

        BlockState attachedState = world.getBlockState(attachedPos);
        double dy = SlabSupport.getYOffset(world, attachedPos, attachedState);
        if (dy != 0.0) {
            Vec3 current = cir.getReturnValue();
            cir.setReturnValue((current == null ? Vec3.ZERO : current).add(0.0, dy, 0.0));
        }
    }
}
