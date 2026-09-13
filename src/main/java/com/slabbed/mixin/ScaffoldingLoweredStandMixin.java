package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A lowered scaffolding column is stood on at its REAL (drawn) top.
 *
 * <p>Vanilla scaffolding has no body: its collision is a thin standing layer that exists only while
 * {@link CollisionContext#isAbove} says the entity's feet are at or above the cell's full-cube top.
 * That comparison is made against the UNLOWERED cube, so on a lowered column the feet sit below the
 * test plane, the standing layer is withheld, and the entity sinks into the column until the climb
 * rule catches it. This mixin offsets the compared shape by the cell's stored height before the
 * vanilla test runs; the returned shape is still moved by the general collision lane, so the layer
 * ends exactly at the drawn top. Flush columns pass a zero offset and keep vanilla behaviour.
 *
 * <p>Both {@code isAbove} call sites are wrapped: the standing-layer test and the descending test.
 */
@Mixin(ScaffoldingBlock.class)
public abstract class ScaffoldingLoweredStandMixin {

    @WrapOperation(
            method = "getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/shapes/CollisionContext;isAbove(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean slabbed$isAboveLoweredTop(CollisionContext context, VoxelShape shape, BlockPos pos, boolean defaultValue,
                                              Operation<Boolean> original,
                                              BlockState state, BlockGetter world, BlockPos cell, CollisionContext ignored) {
        double dy = SlabSupport.getYOffset(world, cell, state);
        VoxelShape compared = Double.isFinite(dy) && dy != 0.0d ? shape.move(0.0d, dy, 0.0d) : shape;
        return original.call(context, compared, pos, defaultValue);
    }
}
