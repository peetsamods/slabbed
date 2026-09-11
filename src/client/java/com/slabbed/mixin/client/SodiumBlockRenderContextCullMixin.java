package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves exposed height-step faces in Sodium's independently cached culling path. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext", remap = false)
public abstract class SodiumBlockRenderContextCullMixin {
    @Shadow(remap = false)
    protected BlockRenderView level;

    @Shadow(remap = false)
    protected BlockPos pos;

    @Shadow(remap = false)
    protected BlockState state;

    // Sodium caches this result per face before both early model culling and final quad culling.
    // Reuse the Indigo predicate so only exposed height-step faces can become visible.
    @Inject(method = "shouldDrawSide", at = @At("RETURN"), cancellable = true, remap = false)
    private void slabbed$drawStepFace(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()
                && SlabSupport.isSlabHeightStepFace(level, pos, state, direction)) {
            cir.setReturnValue(true);
        }
    }
}
