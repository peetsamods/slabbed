package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes the see-through "window" / "doom-infinity" hole on a lowered opaque cube at a
 * slab height step.
 *
 * <p>The chunk mesher (Fabric Indigo) culls opaque-block faces; when an opaque cube is
 * lowered onto a slab, the step exposes part of a side face that Indigo still culls. So
 * the exposed step face must be force-redrawn. It only ever ADDS faces — never removes.
 *
 * <p><b>1.21.1 adaptation of the 1.21.11 overhaul.</b> The 1.21.11 Indigo (renderer 5.x)
 * exposes the cull gate as {@code shouldDrawSide}/{@code shouldCullSide}; the 1.21.1
 * fabric-api (renderer-indigo 1.7.0, bundled with fabric-api 0.115.6+1.21.1) exposes the
 * single package-private gate {@code boolean shouldDrawFace(Direction)} instead — verified
 * by bytecode inspection. We inject there. {@code BlockRenderInfo} carries the block
 * position/state/view as public fields, so the rule is position-aware.
 *
 * <p>Targeted by name because the class is fabric-api internal.
 *
 * <p><b>STATUS: NOT live-confirmed.</b> Compiles and the injection target is valid, but
 * the visual fix and the mesher-thread neighbour lookup in
 * {@link SlabSupport#isSlabHeightStepFace} need a {@code runClient} pass. Kill switch:
 * {@code -Dslabbed.disableStepCull=true}.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo")
public class BlockRenderInfoCullMixin {

    @Shadow
    public BlockRenderView blockView;
    @Shadow
    public BlockPos blockPos;
    @Shadow
    public BlockState blockState;

    @Inject(method = "shouldDrawFace", at = @At("RETURN"), cancellable = true)
    private void slabbed$drawStepFace(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()
                && SlabSupport.isSlabHeightStepFace(blockView, blockPos, blockState, direction)) {
            cir.setReturnValue(true);
        }
    }
}
