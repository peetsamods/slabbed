package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offsets torch flame and smoke particles by the same dy used for the model
 * (SlabSupport.getYOffset), so particles stay flush with the torch head in all
 * slab contexts including the compound adjacent-side-slab case (-1.0).
 */
@Mixin(TorchBlock.class)
public abstract class TorchParticleMixin {

    @Shadow
    private SimpleParticleType flameParticle;

    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void slabbed$offsetParticles(BlockState state, Level world, BlockPos pos, RandomSource random, CallbackInfo ci) {
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy == 0.0) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.7 + dy;
        double z = pos.getZ() + 0.5;
        world.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
        world.addParticle(this.flameParticle, x, y, z, 0.0, 0.0, 0.0);
        ci.cancel();
    }
}
