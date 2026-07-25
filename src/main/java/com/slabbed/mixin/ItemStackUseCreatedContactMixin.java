package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.placement.LandingResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Publishes the frozen contact height for fire created directly by a successful item use.
 *
 * <p>Fire is not placed through {@code BlockItem}, so it cannot use the ordinary placement-intent
 * publication path. Each wrapped invocation keeps its own immutable frame, making nested or
 * re-entrant item uses independent without shared mutable capture state.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackUseCreatedContactMixin {

    private record FireUseFrame(
            LandingResolver.PlacementAim aim,
            BlockPos targetPos,
            BlockState targetBefore
    ) {
        private FireUseFrame {
            targetPos = targetPos.immutable();
        }
    }

    @WrapMethod(method = "useOn")
    private InteractionResult slabbed$publishUseCreatedFireContact(
            UseOnContext context,
            Operation<InteractionResult> original
    ) {
        FireUseFrame frame = slabbed$captureFireUse(context);
        InteractionResult result = original.call(context);
        slabbed$publishFireContact(context, frame, result);
        return result;
    }

    private static FireUseFrame slabbed$captureFireUse(UseOnContext context) {
        if (context == null) {
            return null;
        }
        Level level = context.getLevel();
        if (level == null || level.isClientSide() || context.getClickedFace() != Direction.UP) {
            return null;
        }
        LandingResolver.PlacementAim aim = LandingResolver.captureAim(context);
        BlockPos targetPos = aim.ownerPos().relative(aim.clickedFace()).immutable();
        return new FireUseFrame(aim, targetPos, level.getBlockState(targetPos));
    }

    private static void slabbed$publishFireContact(
            UseOnContext context,
            FireUseFrame frame,
            InteractionResult result
    ) {
        if (context == null || frame == null || result == null || !result.consumesAction()) {
            return;
        }
        Level level = context.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockState createdState = level.getBlockState(frame.targetPos());
        if (frame.targetBefore().equals(createdState)
                || frame.targetBefore().getBlock() instanceof BaseFireBlock
                || !(createdState.getBlock() instanceof BaseFireBlock)
                || SlabAnchorAttachment.rawPlacementDyFact(level, frame.targetPos()).present()) {
            return;
        }

        LandingResolver.PlacementResolution resolution = LandingResolver.resolve(
                frame.aim(),
                frame.targetPos(),
                createdState,
                LandingResolver.classify(createdState));
        if (resolution == null
                || !resolution.targetCell().equals(frame.targetPos())
                || !Double.isFinite(resolution.landingDy())) {
            return;
        }

        SlabAnchorAttachment.writePlacementDy(level, frame.targetPos(), resolution.landingDy());
    }
}
