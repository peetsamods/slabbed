package com.slabbed.test.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.test.RenderAttachmentReadProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SlabAnchorAttachment.class)
public abstract class RenderAttachmentReadGameTestMixin {
    @Inject(method = "rawPlacementDyFact", at = @At("HEAD"))
    private static void slabbed$forbidWorkerBackingReads(BlockGetter view, BlockPos pos,
            CallbackInfoReturnable<SlabAnchorAttachment.PlacementDyFact> cir) {
        RenderAttachmentReadProbe.check();
    }
}
