package com.slabbed.test.mixin;

import com.slabbed.test.ParticleOffsetLookupProbe;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SlabSupport.class, remap = false)
public abstract class ParticleOffsetLookupProbeMixin {
    @Inject(method = "getYOffset(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)D", at = @At("HEAD"))
    private static void count(BlockView world, BlockPos pos, BlockState state, CallbackInfoReturnable<Double> cir) {
        ParticleOffsetLookupProbe.record();
    }
}
