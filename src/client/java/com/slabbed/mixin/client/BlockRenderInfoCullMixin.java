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
 * <p>The chunk mesher (Fabric Indigo) culls opaque-block faces via
 * {@code BlockRenderInfo.shouldDrawSide/shouldCullSide}, which calls vanilla
 * {@code Block.shouldDrawSide} at the un-shifted voxel — independent of the model's
 * cull predicate. So when an opaque cube is lowered onto a slab, the step exposes part
 * of a side face (or a neighbour's facing side) that Indigo still culls. This is the
 * actual gate (neither {@code BlockModelRenderer.shouldDrawFace} nor the FRAPI cullTest
 * is honoured here). Redraw such slab height-step faces. BlockRenderInfo carries the
 * block position, so the rule is position-aware. It only ever ADDS faces — never removes.
 *
 * <p>Targeted by name because the class is fabric-api internal.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo")
public class BlockRenderInfoCullMixin {

    @Shadow
    public BlockRenderView blockView;
    @Shadow
    public BlockPos blockPos;
    @Shadow
    public BlockState blockState;

    @Inject(method = "shouldDrawSide", at = @At("RETURN"), cancellable = true)
    private void slabbed$drawStepFace(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()
                && SlabSupport.isSlabHeightStepFace(blockView, blockPos, blockState, direction)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "shouldCullSide", at = @At("RETURN"), cancellable = true)
    private void slabbed$dontCullStepFace(Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()
                && SlabSupport.isSlabHeightStepFace(blockView, blockPos, blockState, direction)) {
            cir.setReturnValue(false);
        }
    }
}
