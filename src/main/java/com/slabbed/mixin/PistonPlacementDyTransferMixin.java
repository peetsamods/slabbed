package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.block.BlockState;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Carries immutable placement heights through vanilla piston movement. */
@Mixin(PistonBlock.class)
public abstract class PistonPlacementDyTransferMixin {

    @Unique
    private static final ThreadLocal<ArrayDeque<Map<BlockPos, Long>>> SLABBED$MOVE_FACTS =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<ArrayDeque<SlabAnchorAttachment.PlacementDyFact>>
            SLABBED$BASE_FACTS = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "move", at = @At("HEAD"))
    private void slabbed$captureMovedFacts(
            World world,
            BlockPos pistonPos,
            Direction pistonDirection,
            boolean extending,
            CallbackInfoReturnable<Boolean> cir
    ) {
        // Balance every method return before vanilla reaches its handler calculation. The wrapper
        // below fills this exact map from vanilla's accepted, post-head-removal moved-block list.
        SLABBED$MOVE_FACTS.get().push(new LinkedHashMap<>());
    }

    @WrapOperation(
            method = "move",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/piston/PistonHandler;calculatePush()Z"
            )
    )
    private boolean slabbed$captureActualMovedFacts(
            PistonHandler handler,
            Operation<Boolean> original,
            @Local(argsOnly = true) World world,
            @Local(argsOnly = true) BlockPos pistonPos,
            @Local(argsOnly = true) Direction pistonDirection,
            @Local(argsOnly = true) boolean extending
    ) {
        boolean accepted = original.call(handler);
        if (!accepted || world.isClient() || !SlabAnchorAttachment.FROZEN_DY_ENABLED) {
            return accepted;
        }
        Map<BlockPos, Long> destinations = SLABBED$MOVE_FACTS.get().peek();
        if (destinations == null) {
            return accepted;
        }
        Direction motion = extending ? pistonDirection : pistonDirection.getOpposite();
        for (BlockPos source : handler.getMovedBlocks()) {
            SlabAnchorAttachment.PlacementDyFact fact =
                    SlabAnchorAttachment.rawPlacementDyFact(world, source);
            if (fact.present()) {
                destinations.put(source.offset(motion).toImmutable(), fact.rawBits());
            }
        }
        if (extending) {
            SlabAnchorAttachment.PlacementDyFact baseFact =
                    SlabAnchorAttachment.rawPlacementDyFact(world, pistonPos);
            if (baseFact.present()) {
                destinations.put(pistonPos.offset(pistonDirection).toImmutable(), baseFact.rawBits());
            }
        }
        return true;
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void slabbed$publishMovedFacts(
            World world,
            BlockPos pistonPos,
            Direction pistonDirection,
            boolean extending,
            CallbackInfoReturnable<Boolean> cir
    ) {
        ArrayDeque<Map<BlockPos, Long>> stack = SLABBED$MOVE_FACTS.get();
        Map<BlockPos, Long> destinations = stack.isEmpty() ? Map.of() : stack.pop();
        if (stack.isEmpty()) {
            SLABBED$MOVE_FACTS.remove();
        }
        if (Boolean.TRUE.equals(cir.getReturnValue()) && !destinations.isEmpty()) {
            SlabAnchorAttachment.writePlacementDyBatch(world, destinations);
        }
    }

    @Inject(method = "onSyncedBlockEvent", at = @At("HEAD"))
    private void slabbed$capturePistonBaseFact(
            BlockState state,
            World world,
            BlockPos pos,
            int type,
            int data,
            CallbackInfoReturnable<Boolean> cir
    ) {
        SlabAnchorAttachment.PlacementDyFact fact =
                !world.isClient() && SlabAnchorAttachment.FROZEN_DY_ENABLED
                        ? SlabAnchorAttachment.rawPlacementDyFact(world, pos)
                        : SlabAnchorAttachment.PlacementDyFact.absent();
        SLABBED$BASE_FACTS.get().push(fact);
    }

    @Inject(method = "onSyncedBlockEvent", at = @At("RETURN"))
    private void slabbed$restorePistonBaseFact(
            BlockState state,
            World world,
            BlockPos pos,
            int type,
            int data,
            CallbackInfoReturnable<Boolean> cir
    ) {
        ArrayDeque<SlabAnchorAttachment.PlacementDyFact> stack = SLABBED$BASE_FACTS.get();
        SlabAnchorAttachment.PlacementDyFact fact = stack.isEmpty()
                ? SlabAnchorAttachment.PlacementDyFact.absent()
                : stack.pop();
        if (stack.isEmpty()) {
            SLABBED$BASE_FACTS.remove();
        }
        if (Boolean.TRUE.equals(cir.getReturnValue())
                && fact.present()
                && !world.getBlockState(pos).isAir()) {
            SlabAnchorAttachment.writePlacementDyBatch(
                    world, Map.of(pos.toImmutable(), fact.rawBits()));
        }
    }
}
