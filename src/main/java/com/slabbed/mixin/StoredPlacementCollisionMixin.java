package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;

/** Stored placement heights move collision exactly once; unstored geometry retains its legacy path. */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class StoredPlacementCollisionMixin {
    @WrapMethod(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;")
    private VoxelShape slabbed$storedCollision(BlockView world, BlockPos pos, ShapeContext context,
                                             Operation<VoxelShape> original) {
        if (SlabSupport.isRawShapeProbeActive()) return original.call(world, pos, context);
        double dy = SlabPlacementDyAttachment.storedDy(world, pos);
        if (!Double.isFinite(dy) || dy == 0.0) return original.call(world, pos, context);
        return SlabSupport.withRawShapeProbe(() -> original.call(world, pos, context)).offset(0.0, dy, 0.0);
    }

    @WrapMethod(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;")
    private VoxelShape slabbed$storedCachedCollision(BlockView world, BlockPos pos,
                                                   Operation<VoxelShape> original) {
        if (SlabSupport.isRawShapeProbeActive()) return original.call(world, pos);
        double dy = SlabPlacementDyAttachment.storedDy(world, pos);
        if (!Double.isFinite(dy) || dy == 0.0) return original.call(world, pos);
        return SlabSupport.withRawShapeProbe(() -> original.call(world, pos)).offset(0.0, dy, 0.0);
    }
}
