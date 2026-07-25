package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Redstone wire: allow slab top faces to count as valid ground for placement/survival.
 */
@Mixin(RedStoneWireBlock.class)
public abstract class RedstoneWireBlockMixin {

    @Inject(method = "canSurvive",
            at = @At("HEAD"), cancellable = true)
    private void slabbed$canPlaceAt(BlockState state, LevelReader world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SlabSupport.isRedstoneSupportTopSurface(world, pos.below())) {
            cir.setReturnValue(true);
        }
    }

    @ModifyArgs(
            method = "spawnParticlesAlongLine",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle"
                            + "(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
            )
    )
    private static void slabbed$translatePoweredDustParticleY(
            Args args,
            Level world,
            RandomSource random,
            BlockPos pos,
            int color,
            Direction firstDirection,
            Direction secondDirection,
            float min,
            float max) {
        double frozenDy = SlabAnchorAttachment.storedPlacementDy(world, pos);
        if (!Double.isFinite(frozenDy)) {
            return;
        }
        double vanillaY = args.get(2);
        args.set(2, vanillaY + frozenDy);
    }
}
