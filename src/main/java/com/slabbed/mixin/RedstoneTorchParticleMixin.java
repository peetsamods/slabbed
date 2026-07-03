package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A LOWERED redstone torch renders its model by dy (OffsetBlockStateModel applies
 * getVisualYOffset to every block) but vanilla {@code RedstoneTorchBlock.randomDisplayTick}
 * spawns its dust particle at grid height — so the dust floats ~0.5 above the lowered torch
 * head ("particles too high"). {@code TorchParticleMixin} does not cover this: RedstoneTorchBlock
 * is a SIBLING of TorchBlock under AbstractTorchBlock, not a subclass, and has its own
 * randomDisplayTick.
 *
 * <p>Rather than re-implement vanilla's exact jittered formula (risking a wrong constant), this
 * REDIRECTS the particle spawn and simply adds {@code dy} to its Y — preserving vanilla's
 * position, jitter, and LIT gate exactly, and a strict no-op when dy == 0.
 */
@Mixin(RedstoneTorchBlock.class)
public abstract class RedstoneTorchParticleMixin {

    @Redirect(
            method = "randomDisplayTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;addParticleClient(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
            )
    )
    private void slabbed$offsetRedstoneDust(World redirectWorld, ParticleEffect effect,
                                            double x, double y, double z,
                                            double vx, double vy, double vz,
                                            BlockState state, World world, BlockPos pos, Random random) {
        double dy = SlabSupport.getVisualYOffset(world, pos, state);
        redirectWorld.addParticleClient(effect, x, y + dy, z, vx, vy, vz);
    }
}
