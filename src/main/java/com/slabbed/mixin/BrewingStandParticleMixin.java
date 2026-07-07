package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Port of the 1.21.11 brewing-stand particle fix (donor {@code BrewingStandParticleMixin}, commit
 * {@code 88ee4aed}, live-reported "Lowered brewing stand's particles are also too high"): vanilla
 * {@code BrewingStandBlock.animateTick} spawns its ambient smoke at grid height
 * ({@code pos.getY() + 0.7 + ...} — 26.2 bytecode confirmed via javap, {@code Level.addParticle} at
 * :85) with no dy awareness. Redirect the spawn and add {@code dy} to Y, preserving vanilla's exact
 * position/jitter; strict no-op when dy == 0.
 */
@Mixin(BrewingStandBlock.class)
public abstract class BrewingStandParticleMixin {

    @Redirect(
            method = "animateTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
            )
    )
    private void slabbed$offsetBrewingStandSmoke(Level redirectLevel, ParticleOptions effect,
                                                 double x, double y, double z,
                                                 double vx, double vy, double vz,
                                                 BlockState state, Level level, BlockPos pos, RandomSource random) {
        double dy = SlabSupport.getYOffset(level, pos, state);
        redirectLevel.addParticle(effect, x, y + dy, z, vx, vy, vz);
    }
}
