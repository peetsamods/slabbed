package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Adds the hanging part of lowered blocks above the current broadphase cell.
 *
 * <p>NeoForge 1.21.1's {@link BlockCollisions} samples the current cell's block
 * collision only. A Slabbed-lowered block above can be visually and legally in
 * this cell, so the broadphase must also see that hanging collision.
 */
@Mixin(BlockCollisions.class)
public abstract class BlockCollisionsLoweredAboveMixin {

    @Redirect(method = "computeNext",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape slabbed$addHangingLoweredAbove(
            BlockState state,
            BlockGetter getter,
            BlockPos pos,
            CollisionContext context
    ) {
        VoxelShape own = state.getCollisionShape(getter, pos, context);
        return SlabSupport.withHangingLoweredCollisionFromAbove(own, getter, pos);
    }
}
