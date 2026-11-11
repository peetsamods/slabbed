package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.anchor.OffgridSurvivorTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** The surviving member keeps its authored seat when the mixed container unwraps (LAW.md). */
@Pseudo
@Mixin(targets = "dev.tazer.offgrid.slab.MixedSlabBlock", remap = false)
public abstract class OffgridMixedSlabBreakMixin {
    @WrapOperation(method = "breakHalf", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private static boolean slabbed$keepSurvivingSeat(Level level, BlockPos pos, BlockState survivor,
            int flags, Operation<Boolean> original, @Local(argsOnly = true) BlockState container) {
        try (var transition = OffgridSurvivorTransition.open(level, pos, container, survivor)) {
            return original.call(level, pos, survivor, flags);
        }
    }
}
