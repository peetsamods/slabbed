package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.placement.PlacementCollisionShapes;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockCollisionSpliterator;
import net.minecraft.world.CollisionView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Iterator;
import java.util.function.BiFunction;

/** Adds stored owners after the unchanged native search, including across empty vertical sections. */
@Mixin(BlockCollisionSpliterator.class)
public abstract class StoredPlacementCollisionIteratorMixin<T> {
    @Shadow @Final private CollisionView world;
    @Shadow @Final private ShapeContext context;
    @Shadow @Final private Box box;
    @Shadow @Final private boolean forEntity;
    @Shadow @Final private BiFunction<BlockPos.Mutable, VoxelShape, T> resultFunction;
    @Unique private Iterator<PlacementCollisionShapes.Contact> slabbed$additional;

    @WrapOperation(method = "computeNext", remap = false, at = @At(value = "INVOKE",
            target = "endOfData()Ljava/lang/Object;", remap = false))
    private Object slabbed$finishAfterStoredOwners(BlockCollisionSpliterator<T> iterator, Operation<Object> original) {
        if (slabbed$additional == null) {
            slabbed$additional = PlacementCollisionShapes.above(world, context, box, forEntity).iterator();
        }
        if (slabbed$additional.hasNext()) {
            var contact = slabbed$additional.next();
            return resultFunction.apply(contact.owner().mutableCopy(), contact.shape());
        }
        return original.call(iterator);
    }
}
