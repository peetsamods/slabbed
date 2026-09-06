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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Carries immutable placement heights through vanilla piston movement. */
@Mixin(PistonBlock.class)
public abstract class PistonPlacementDyTransferMixin {

    @Unique
    private static final ThreadLocal<ArrayDeque<Map<BlockPos, Long>>> SLABBED$MOVE_FACTS =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<ArrayDeque<Set<BlockPos>>> SLABBED$MOVE_MODERN =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<ArrayDeque<SlabAnchorAttachment.PlacementDyFact>>
            SLABBED$BASE_FACTS = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<ArrayDeque<Boolean>> SLABBED$BASE_MODERN =
            ThreadLocal.withInitial(ArrayDeque::new);

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
        SLABBED$MOVE_MODERN.get().push(new LinkedHashSet<>());
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
        if (!accepted || world.isClient()) {
            return accepted;
        }
        Map<BlockPos, Long> destinations = SLABBED$MOVE_FACTS.get().peek();
        Set<BlockPos> modernDestinations = SLABBED$MOVE_MODERN.get().peek();
        if (destinations == null || modernDestinations == null) {
            return accepted;
        }
        Direction motion = extending ? pistonDirection : pistonDirection.getOpposite();
        for (BlockPos source : handler.getMovedBlocks()) {
            slabbed$captureDestination(world, source, source.offset(motion), destinations, modernDestinations);
        }
        if (extending) {
            slabbed$captureDestination(
                    world,
                    pistonPos,
                    pistonPos.offset(pistonDirection),
                    destinations,
                    modernDestinations);
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
        ArrayDeque<Set<BlockPos>> modernStack = SLABBED$MOVE_MODERN.get();
        Set<BlockPos> modernDestinations = modernStack.isEmpty() ? Set.of() : modernStack.pop();
        if (modernStack.isEmpty()) {
            SLABBED$MOVE_MODERN.remove();
        }
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            if (!destinations.isEmpty()) {
                SlabAnchorAttachment.writePlacementDyBatch(world, destinations);
            }
            if (!modernDestinations.isEmpty()) {
                SlabAnchorAttachment.restoreTransferredModernPlacements(world, modernDestinations);
            }
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
        boolean carriesPlacement = !world.isClient()
                && SlabAnchorAttachment.usesFrozenPlacementHeight(world, pos);
        SlabAnchorAttachment.PlacementDyFact fact = carriesPlacement
                ? SlabAnchorAttachment.rawPlacementDyFact(world, pos)
                : SlabAnchorAttachment.PlacementDyFact.absent();
        SLABBED$BASE_FACTS.get().push(fact);
        SLABBED$BASE_MODERN.get().push(
                carriesPlacement && SlabAnchorAttachment.isModernPlacement(world, pos));
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
        ArrayDeque<Boolean> modernStack = SLABBED$BASE_MODERN.get();
        boolean modern = !modernStack.isEmpty() && modernStack.pop();
        if (modernStack.isEmpty()) {
            SLABBED$BASE_MODERN.remove();
        }
        if (Boolean.TRUE.equals(cir.getReturnValue()) && !world.getBlockState(pos).isAir()) {
            if (fact.present()) {
                SlabAnchorAttachment.writePlacementDyBatch(
                        world, Map.of(pos.toImmutable(), fact.rawBits()));
            }
            if (modern) {
                SlabAnchorAttachment.restoreTransferredModernPlacements(world, Set.of(pos.toImmutable()));
            }
        }
    }

    @Unique
    private static void slabbed$captureDestination(
            World world,
            BlockPos source,
            BlockPos destination,
            Map<BlockPos, Long> facts,
            Set<BlockPos> modernDestinations
    ) {
        if (!SlabAnchorAttachment.usesFrozenPlacementHeight(world, source)) {
            return;
        }
        BlockPos immutableDestination = destination.toImmutable();
        SlabAnchorAttachment.PlacementDyFact fact =
                SlabAnchorAttachment.rawPlacementDyFact(world, source);
        if (fact.present()) {
            facts.put(immutableDestination, fact.rawBits());
        }
        if (SlabAnchorAttachment.isModernPlacement(world, source)) {
            modernDestinations.add(immutableDestination);
        }
    }
}
