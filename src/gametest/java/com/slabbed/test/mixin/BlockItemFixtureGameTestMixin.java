package com.slabbed.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.test.PlacementCaptureBoundaryGameTest;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** GameTest-only fixture overrides nested inside the production C3 observation wrappers. */
@Mixin(value = BlockItem.class, priority = 900)
public abstract class BlockItemFixtureGameTestMixin {
    @WrapOperation(
            method = "place",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/BlockItem;updatePlacementContext(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/item/context/BlockPlaceContext;")
    )
    private BlockPlaceContext slabbed$fixturePlacementContext(
            BlockItem instance,
            BlockPlaceContext context,
            Operation<BlockPlaceContext> original) {
        BlockPlaceContext replacement =
                PlacementCaptureBoundaryGameTest.fixturePlacementContext(instance, context);
        return replacement == null ? original.call(instance, context) : replacement;
    }

    @WrapOperation(
            method = "place",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/BlockItem;getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;")
    )
    private BlockState slabbed$fixturePlacementState(
            BlockItem instance,
            BlockPlaceContext context,
            Operation<BlockState> original) {
        BlockState replacement =
                PlacementCaptureBoundaryGameTest.fixturePlacementState(instance, context);
        return replacement == null ? original.call(instance, context) : replacement;
    }

    @WrapOperation(
            method = "place",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z")
    )
    private boolean slabbed$fixturePlaceBlock(
            BlockItem instance,
            BlockPlaceContext context,
            BlockState state,
            Operation<Boolean> original) {
        Boolean replacement =
                PlacementCaptureBoundaryGameTest.fixturePlaceBlock(instance, context, state);
        return replacement == null ? original.call(instance, context, state) : replacement;
    }
}
