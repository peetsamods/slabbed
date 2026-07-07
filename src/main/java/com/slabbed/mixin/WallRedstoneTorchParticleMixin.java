package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Port of the 1.21.11 wall-redstone-torch particle fix (donor {@code WallRedstoneTorchParticleMixin},
 * commit {@code a414ad3d} — the siblings-not-subclasses trap: {@code RedstoneWallTorchBlock} overrides
 * {@code animateTick} with its own wall-offset formula, so {@link RedstoneTorchParticleMixin} on the
 * standing block does NOT cover it; this branch's own javadoc there documented the gap). Redirect the
 * dust spawn and add {@code dy} to Y, preserving vanilla's wall offsets, jitter and LIT gate.
 */
@Mixin(RedstoneWallTorchBlock.class)
public abstract class WallRedstoneTorchParticleMixin {

    @Redirect(
            method = "animateTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
            )
    )
    private void slabbed$offsetWallRedstoneDust(Level redirectLevel, ParticleOptions effect,
                                                double x, double y, double z,
                                                double vx, double vy, double vz,
                                                BlockState state, Level level, BlockPos pos, RandomSource random) {
        double dy = SlabSupport.getYOffset(level, pos, state);
        redirectLevel.addParticle(effect, x, y + dy, z, vx, vy, vz);
    }
}
