package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes the see-through "window" on a lowered opaque cube (crafting table / pumpkin / …
 * on a slab in a terrace) on the <em>vanilla chunk render path</em>.
 *
 * <p>In 1.21.11 chunk blocks render through {@code BlockRenderManager.renderBlock} →
 * {@code BlockModelRenderer.render}, whose face culling is {@link BlockModelRenderer}
 * {@code #shouldDrawFace} → {@code Block.shouldDrawSide}, computed at the un-shifted
 * voxel. When the cube is lowered onto a slab the step exposes part of a side face that
 * vanilla still culls. Redraw such slab height-step faces. This mirrors the FRAPI-path
 * fix in {@code OffsetBlockStateModel} so both rendering backends are covered.
 */
@Mixin(BlockModelRenderer.class)
public class BlockModelRendererCullMixin {

    @Inject(method = "shouldDrawFace", at = @At("RETURN"), cancellable = true)
    private static void slabbed$drawSlabHeightStepFace(
            BlockRenderView world, BlockState state, boolean cull, Direction direction, BlockPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return; // vanilla already draws this face
        }
        if (SlabSupport.isSlabHeightStepFace(world, pos, state, direction)) {
            cir.setReturnValue(true);
        }
    }
}
