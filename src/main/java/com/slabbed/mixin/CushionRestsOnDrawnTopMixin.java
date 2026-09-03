package com.slabbed.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A cushion may rest on a lowered block's drawn top (WYSIWYG; maintainer ruling, 2026-09-03).
 *
 * <p>Vanilla's anchor probe already finds a lowered block: it reads outline shapes, which this mod
 * shifts to the drawn body. What refused the cushion is {@code isCoveredBySuffocatingBlocks}: it asks
 * every CELL the cushion's box overlaps whether its block suffocates, and a full cube answers from
 * its shape cache regardless of where it is drawn. A cushion resting on a lowered block's drawn top
 * sits inside that block's cell while the body is below it. A block suffocates the cushion only where
 * its drawn body (the outline shape, the same geometry the anchor probe reads) actually overlaps the
 * cushion; for an unlowered block that is exactly vanilla's answer. Placement and the periodic
 * survival check share this method, so a placed cushion also keeps surviving.
 *
 * <p>The plain collision overload deliberately stays unshifted on this line; do not judge this on it.
 */
@Mixin(Cushion.class)
public abstract class CushionRestsOnDrawnTopMixin {

    @Redirect(
            method = "isCoveredBySuffocatingBlocks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;isSuffocating("
                            + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"
            )
    )
    private static boolean slabbed$suffocatesOnlyWhereDrawn(BlockState state, BlockGetter getter, BlockPos pos,
                                                          Level level, AABB cushion) {
        if (!state.isSuffocating(getter, pos)) {
            return false;
        }
        VoxelShape drawn = state.getShape(getter, pos);
        if (drawn.isEmpty()) {
            return true;
        }
        return drawn.bounds().move(pos).intersects(cushion);
    }
}
