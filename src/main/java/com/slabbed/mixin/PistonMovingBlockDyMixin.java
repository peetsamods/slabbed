package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Map;

/** Preserves a moving cell's placement height when vanilla installs its final block state. */
@Mixin(PistonBlockEntity.class)
public abstract class PistonMovingBlockDyMixin {

    @Unique
    private static final ThreadLocal<ArrayDeque<SlabAnchorAttachment.PlacementDyFact>>
            SLABBED$TICK_FACTS = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<ArrayDeque<SlabAnchorAttachment.PlacementDyFact>>
            SLABBED$FINISH_FACTS = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "tick", at = @At("HEAD"))
    private static void slabbed$captureTickFact(
            World world,
            BlockPos pos,
            BlockState state,
            PistonBlockEntity blockEntity,
            CallbackInfo ci
    ) {
        SLABBED$TICK_FACTS.get().push(slabbed$fact(world, pos));
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private static void slabbed$restoreTickFact(
            World world,
            BlockPos pos,
            BlockState state,
            PistonBlockEntity blockEntity,
            CallbackInfo ci
    ) {
        slabbed$restore(world, pos, SLABBED$TICK_FACTS);
    }

    @Inject(method = "finish", at = @At("HEAD"))
    private void slabbed$captureFinishFact(CallbackInfo ci) {
        PistonBlockEntity self = (PistonBlockEntity) (Object) this;
        SLABBED$FINISH_FACTS.get().push(slabbed$fact(self.getWorld(), self.getPos()));
    }

    @Inject(method = "finish", at = @At("RETURN"))
    private void slabbed$restoreFinishFact(CallbackInfo ci) {
        PistonBlockEntity self = (PistonBlockEntity) (Object) this;
        slabbed$restore(self.getWorld(), self.getPos(), SLABBED$FINISH_FACTS);
    }

    @Unique
    private static SlabAnchorAttachment.PlacementDyFact slabbed$fact(World world, BlockPos pos) {
        return world != null && !world.isClient() && SlabAnchorAttachment.FROZEN_DY_ENABLED
                ? SlabAnchorAttachment.rawPlacementDyFact(world, pos)
                : SlabAnchorAttachment.PlacementDyFact.absent();
    }

    @Unique
    private static void slabbed$restore(
            World world,
            BlockPos pos,
            ThreadLocal<ArrayDeque<SlabAnchorAttachment.PlacementDyFact>> facts
    ) {
        ArrayDeque<SlabAnchorAttachment.PlacementDyFact> stack = facts.get();
        SlabAnchorAttachment.PlacementDyFact fact = stack.isEmpty()
                ? SlabAnchorAttachment.PlacementDyFact.absent()
                : stack.pop();
        if (stack.isEmpty()) {
            facts.remove();
        }
        if (world != null && fact.present() && !world.getBlockState(pos).isAir()) {
            SlabAnchorAttachment.writePlacementDyBatch(
                    world, Map.of(pos.toImmutable(), fact.rawBits()));
        }
    }
}
