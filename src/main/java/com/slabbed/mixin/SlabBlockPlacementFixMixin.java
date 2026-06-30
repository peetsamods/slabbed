package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WYSIWYG slab-type-on-placement against lowered support.
 *
 * <p>Vanilla {@link SlabBlock#getStateForPlacement} chooses TOP vs BOTTOM from the
 * hit Y relative to the placement cell. When the player is aiming at a block that
 * Slabbed renders at a non-zero visual {@code dy}, that logical-cell rule disagrees
 * with what the player sees: aiming at the visible UPPER half of a lowered block's
 * side face yields a logical-cell hit below 0.5 (because the visible geometry is
 * shifted down by {@code dy}), so vanilla places a BOTTOM slab where the player
 * expected a TOP slab. This re-derives the slab type from the VISIBLE half.
 */
@Mixin(SlabBlock.class)
public abstract class SlabBlockPlacementFixMixin {

    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void slabbed$slabTypeFollowsVisibleHalf(BlockPlaceContext ctx, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state == null || !state.hasProperty(SlabBlock.TYPE)) {
            return;
        }
        // Clicking an existing slab to merge it into a DOUBLE is not a half choice.
        if (state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
            return;
        }

        Direction face = ctx.getClickedFace();
        BlockPos placePos = ctx.getClickedPos();

        // UP/DOWN faces are left to vanilla: top-face placement onto a lowered support must
        // stay a BOTTOM slab (the established popping-law fixture / Visual Triad law), and
        // forcing TOP there breaks it. Only the horizontal (cantilever) case disagrees with
        // what the player sees, because the visible geometry is shifted by dy.

        // Horizontal face: the placed slab cantilevers off the side of the aimed block
        // and inherits that block's visual dy. Choose TOP/BOTTOM from the VISIBLE half
        // the player aimed at, i.e. the hit Y un-shifted by the target's dy.
        if (face.getAxis().isHorizontal()) {
            BlockPos targetPos = placePos.relative(face.getOpposite());
            BlockState target = ctx.getLevel().getBlockState(targetPos);
            double targetDy = SlabSupport.getYOffset(ctx.getLevel(), targetPos, target);
            if (targetDy == 0.0) {
                return; // not lowered -> vanilla half choice is already WYSIWYG-correct
            }
            // targetPos and placePos share the same Y for a horizontal face, so the cell
            // origin is placePos.getY(). Un-shift the visible hit by targetDy.
            double visibleLocalY = (ctx.getClickLocation().y - placePos.getY()) - targetDy;
            SlabType desired = visibleLocalY > 0.5 ? SlabType.TOP : SlabType.BOTTOM;
            if (state.getValue(SlabBlock.TYPE) != desired) {
                cir.setReturnValue(state.setValue(SlabBlock.TYPE, desired));
            }
        }
    }
}
