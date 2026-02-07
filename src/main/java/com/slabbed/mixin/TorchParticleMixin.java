package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.TorchBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offsets torch flame and smoke particles down by 0.5 when the torch
 * sits on a bottom slab, matching the model offset applied during
 * chunk meshing.
 */
@Mixin(TorchBlock.class)
public abstract class TorchParticleMixin {

    @Shadow
    private SimpleParticleType particle;

    @Inject(method = "randomDisplayTick", at = @At("HEAD"), cancellable = true)
    private void slabbed$offsetParticles(BlockState state, World world, BlockPos pos, Random random, CallbackInfo ci) {
        if (!SlabSupport.shouldOffset(world, pos, state)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.7 - 0.5;
        double z = pos.getZ() + 0.5;
        world.addParticleClient(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
        world.addParticleClient(this.particle, x, y, z, 0.0, 0.0, 0.0);
        ci.cancel();
    }
}
