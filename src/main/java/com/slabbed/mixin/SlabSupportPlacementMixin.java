package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Placement/survival SUPPORT ONLY on {@link BlockBehaviour.BlockStateBase}: makes slabs report
 * their visible support faces as sturdy so objects can be placed on / hung under them (standing
 * decorations on a slab top, ceiling mounts under a top/lowered slab). Every injector here is a
 * boolean {@code canSurvive}/{@code isFaceSturdy} override — it NEVER moves a shape.
 *
 * <p>This is deliberately split out of {@link SlabSupportStateMixin} (which stays DISABLED in the
 * Forge build): that class also carries the getShape/getInteractionShape/getCollisionShape
 * offset injectors, and enabling those would double-offset because this port already applies the
 * visual offset via the event/model path. Only these support-only injectors are safe to enable.
 * See docs/codex/13-mixin-layer-wiring-audit.md and the mixin-layer-disabled lesson.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class SlabSupportPlacementMixin {

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void slabbed$flowerPotFloorTopSurvival(
            LevelReader world,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockState self = (BlockState) (Object) this;
        if (!self.is(Blocks.FLOWER_POT)) {
            return;
        }
        BlockPos below = pos.below();
        BlockState belowState = world.getBlockState(below);
        cir.setReturnValue(SlabSupport.canTreatAsSolidTopFace(world, below)
                || belowState.isFaceSturdy(world, below, Direction.UP));
    }

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void slabbed$lanternLoweredSlabUndersideSurvival(
            LevelReader world,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockState self = (BlockState) (Object) this;
        if ((!self.is(Blocks.LANTERN) && !self.is(Blocks.SOUL_LANTERN))
                || !self.hasProperty(BlockStateProperties.HANGING)
                || !self.getValue(BlockStateProperties.HANGING)) {
            return;
        }
        BlockPos supportPos = pos.above();
        BlockState supportState = world.getBlockState(supportPos);
        if (supportState.getBlock() instanceof SlabBlock
                && supportState.hasProperty(SlabBlock.TYPE)
                && supportState.getFluidState().isEmpty()
                && (supportState.getValue(SlabBlock.TYPE) == SlabType.TOP
                || supportState.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                || supportState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/SupportType;)Z", at = @At("HEAD"), cancellable = true)
    private void slabbed$slabTopSolid(BlockGetter world, BlockPos pos, Direction direction, SupportType shapeType, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (direction == Direction.UP && SlabSupport.isBottomSlab(self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/SupportType;)Z", at = @At("HEAD"), cancellable = true)
    private void slabbed$ceilingSupport(BlockGetter world, BlockPos pos, Direction direction, SupportType shapeType, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (SlabSupport.isTopSlabUndersideSupport(self, direction)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void slabbed$ceilingSolidFullSquare(BlockGetter world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (SlabSupport.isTopSlabUndersideSupport(self, direction)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void slabbed$slabTopSolidFullSquare(BlockGetter world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (direction == Direction.UP && SlabSupport.isBottomSlab(self)) {
            cir.setReturnValue(true);
        }
    }
}
