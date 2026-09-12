package com.slabbed.mixin.client;

import com.slabbed.client.ClientDy;
import net.minecraft.block.entity.BlockEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps container visuals and their animated parts at the stored placement height. */
@Pseudo
@Mixin(targets = {
        "com.zurrtum.create.client.vanillin.visuals.ChestVisual",
        "com.zurrtum.create.client.vanillin.visuals.ShulkerBoxVisual"
}, remap = false)
public abstract class CreateFlyContainerOffsetMixin {
    @Inject(method = "createInitialPose", at = @At("RETURN"), remap = false)
    private void slabbed$offsetContainerPose(CallbackInfoReturnable<Matrix4f> cir) {
        BlockEntity entity = ((CreateFlyBlockEntityVisualAccessor) this).slabbed$getBlockEntity();
        double dy = ClientDy.dyFor(entity.getWorld(), entity.getPos(), entity.getCachedState());
        if (dy != 0.0) {
            // Pre-multiply so lowering remains vertical even when the model is rotated.
            cir.getReturnValue().translateLocal(0.0f, (float) dy, 0.0f);
        }
    }
}
