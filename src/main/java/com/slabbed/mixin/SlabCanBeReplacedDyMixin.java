package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WYSIWYG slab-combine-vs-extend against a lowered/cantilevered slab.
 *
 * <p>Vanilla {@link SlabBlock#canBeReplaced} decides whether a click merges the
 * existing slab into a DOUBLE (same cell) or extends into the adjacent cell, using
 * {@code clickLocation.y - clickedPos.getY() > 0.5} -- a fraction against the block's
 * raw INTEGER grid Y, with no notion of Slabbed's visual {@code dy} offset. For a
 * lowered TOP slab (dy&lt;0), the slab's entire visible band sits below the raw
 * 0.5 threshold, so every horizontal click satisfies vanilla's combine condition
 * regardless of which half the player visually aims at: a lowered/cantilevered slab
 * can never be extended sideways with a single click, only merged into a double at
 * the same cell (which reads as "the new piece appeared underneath/inside the
 * existing one" rather than "extending outward"). This mirrors vanilla's own
 * combine/extend logic exactly, substituting the dy-corrected click fraction so a
 * shifted slab decides combine-vs-extend the same way an unshifted one would for the
 * same VISUAL click position. No-ops (falls through to vanilla) whenever the tested
 * slab's own dy is zero, so ordinary slab-combine behavior is completely untouched.
 */
@Mixin(SlabBlock.class)
public abstract class SlabCanBeReplacedDyMixin {

    @Inject(method = "canBeReplaced", at = @At("HEAD"), cancellable = true)
    private void slabbed$dyCorrectedCanBeReplaced(
            BlockState state,
            BlockPlaceContext ctx,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockPos pos = ctx.getClickedPos();
        double dy = SlabSupport.getYOffset(ctx.getLevel(), pos, state);
        if (dy == 0.0d) {
            return;
        }

        ItemStack held = ctx.getItemInHand();
        SlabType type = state.getValue(SlabBlock.TYPE);
        if (type == SlabType.DOUBLE || !held.is(state.getBlock().asItem())) {
            cir.setReturnValue(false);
            return;
        }
        if (!ctx.replacingClickedOnBlock()) {
            cir.setReturnValue(true);
            return;
        }

        boolean upperHalf = (ctx.getClickLocation().y - pos.getY() - dy) > 0.5d;
        Direction face = ctx.getClickedFace();
        boolean combine = type == SlabType.BOTTOM
                ? (face == Direction.UP || (upperHalf && face.getAxis().isHorizontal()))
                : (face == Direction.DOWN || (!upperHalf && face.getAxis().isHorizontal()));
        cir.setReturnValue(combine);
    }
}
