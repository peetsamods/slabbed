package com.slabbed.mixin.client;

import com.slabbed.client.ClientDy;
import net.minecraft.block.BlockState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps top overlays from connecting across a rendered height step. */
@Pseudo
@Mixin(targets = "me.pepperbell.continuity.client.processor.overlay.StandardOverlayQuadProcessor", remap = false)
public abstract class ContinuityOverlayHeightMixin {
    @Inject(method = "appliesOverlay", at = @At("RETURN"), cancellable = true, remap = false)
    private void slabbed$requireLevelTop(BlockPos otherPos, BlockState otherAppearanceState,
            BlockState otherState, BlockRenderView view, BlockPos pos, BlockState appearanceState,
            BlockState state, Direction face, Sprite sprite, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && face == Direction.UP) {
            // Compare the same rendered offsets used by both block models, including stored heights.
            double top = pos.getY() + ClientDy.dyFor(view, pos, state);
            double otherTop = otherPos.getY() + ClientDy.dyFor(view, otherPos, otherState);
            if (Math.abs(top - otherTop) > 1.0e-6) {
                cir.setReturnValue(false);
            }
        }
    }
}
