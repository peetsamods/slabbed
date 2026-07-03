package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallRedstoneTorchBlock;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Wall variant of {@link RedstoneTorchParticleMixin}. WallRedstoneTorchBlock overrides
 * randomDisplayTick with its own wall-offset formula, so a mixin on RedstoneTorchBlock alone
 * does NOT cover it — this one is required too. Same safe approach: redirect the particle spawn
 * and add {@code dy} to Y, preserving vanilla's wall offsets, jitter and LIT gate.
 */
@Mixin(WallRedstoneTorchBlock.class)
public abstract class WallRedstoneTorchParticleMixin {

    @Redirect(
            method = "randomDisplayTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;addParticleClient(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
            )
    )
    private void slabbed$offsetWallRedstoneDust(World redirectWorld, ParticleEffect effect,
                                                double x, double y, double z,
                                                double vx, double vy, double vz,
                                                BlockState state, World world, BlockPos pos, Random random) {
        double dy = SlabSupport.getVisualYOffset(world, pos, state);
        redirectWorld.addParticleClient(effect, x, y + dy, z, vx, vy, vz);
    }
}
