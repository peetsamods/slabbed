package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.BrewingStandBlock;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Live-reported bug (2026-07-04, "Lowered brewing stand's particles are also too high"): a
 * LOWERED brewing stand renders its model by dy (OffsetBlockStateModel applies
 * getVisualYOffset to every block) but vanilla {@code BrewingStandBlock.randomDisplayTick}
 * spawns its ambient smoke particle at grid height (real bytecode, confirmed via {@code javap}:
 * {@code World.addParticleClient} called with {@code pos.getY() + 0.7 + ...}, no dy awareness) —
 * same disease class as the redstone-torch and candle particle fixes.
 *
 * <p>Redirects the particle spawn and adds {@code dy} to its Y, preserving vanilla's exact
 * position/jitter and a strict no-op when dy == 0.
 */
@Mixin(BrewingStandBlock.class)
public abstract class BrewingStandParticleMixin {

    @Redirect(
            method = "randomDisplayTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;addParticleClient(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
            )
    )
    private void slabbed$offsetBrewingStandSmoke(World redirectWorld, ParticleEffect effect,
                                                 double x, double y, double z,
                                                 double vx, double vy, double vz,
                                                 BlockState state, World world, BlockPos pos, Random random) {
        double dy = SlabSupport.getVisualYOffset(world, pos, state);
        redirectWorld.addParticleClient(effect, x, y + dy, z, vx, vy, vz);
    }
}
