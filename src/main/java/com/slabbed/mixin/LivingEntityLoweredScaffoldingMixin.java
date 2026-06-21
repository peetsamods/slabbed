package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(LivingEntity.class)
public abstract class LivingEntityLoweredScaffoldingMixin {
    @Shadow
    private Optional<BlockPos> lastClimbablePos;

    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void slabbed$loweredScaffoldingVisualVolumeIsClimbable(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isSpectator() || self.isFallFlying()) {
            return;
        }
        BlockPos loweredScaffolding = slabbed$loweredScaffoldingVisualPos();
        if (loweredScaffolding != null) {
            lastClimbablePos = Optional.of(loweredScaffolding);
            cir.setReturnValue(true);
        }
    }

    @Redirect(method = "handleOnClimbable",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean slabbed$loweredVisualScaffoldingAllowsDescent(BlockState state, Object block) {
        if (block instanceof Block target && state.is(target)) {
            return true;
        }
        return block == Blocks.SCAFFOLDING && slabbed$loweredScaffoldingVisualPos() != null;
    }

    @Unique
    private BlockPos slabbed$loweredScaffoldingVisualPos() {
        LivingEntity self = (LivingEntity) (Object) this;
        Level level = self.level();
        if (level == null) {
            return null;
        }
        AABB entityBox = self.getBoundingBox();
        BlockPos base = self.blockPosition();
        for (int i = 0; i <= 1; i++) {
            BlockPos candidate = base.above(i);
            BlockState state = level.getBlockState(candidate);
            if (!(state.getBlock() instanceof ScaffoldingBlock)) {
                continue;
            }
            double dy = SlabSupport.getYOffset(level, candidate, state);
            if (dy >= -1.0e-6d) {
                continue;
            }
            AABB visualVolume = new AABB(candidate).move(0.0d, dy, 0.0d);
            if (entityBox.intersects(visualVolume)) {
                return candidate;
            }
        }
        return null;
    }
}
