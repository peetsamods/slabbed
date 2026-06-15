package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * COLLISION-FOLLOW broadphase half (Julia's "solid where you see it"): MC's
 * {@link BlockCollisions} iterator is cell-bounded — it only asks each cell's OWN block for a
 * collision shape. A Slabbed-lowered block's collision lives partly in the cell BELOW it (its shape
 * hangs down by |dy| to sit at the visual position), so the broadphase never samples that hanging
 * part and the player clips into / walks through the lowered visual.
 *
 * <p>This redirects the single {@code getCollisionShape} site in {@code computeNext} so that, for
 * each cell queried, the hanging collision of a lowered block directly above is unioned in. The
 * per-state collision shape itself stays vanilla (so the cell-bounded broadphase still samples the
 * upper, un-hung part normally); this adds the missing lower part. Toggle with
 * {@code -Dslabbed.collisionFollow=false}.
 */
@Mixin(BlockCollisions.class)
public abstract class BlockCollisionsLoweredAboveMixin {

    @Redirect(method = "computeNext",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/shapes/CollisionContext;getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape slabbed$addHangingLoweredAbove(CollisionContext ctx, BlockState state,
                                                      CollisionGetter getter, BlockPos pos) {
        VoxelShape own = ctx.getCollisionShape(state, getter, pos);
        return SlabSupport.withHangingLoweredCollisionFromAbove(own, getter, pos);
    }
}
