package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeverBlock;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps lever dust attached to the lever's frozen visual height. Both click and ambient emissions
 * share vanilla's private {@code spawnParticles} helper, so redirect its single particle call and
 * preserve vanilla's orientation formula, particle options, scale, and velocity unchanged.
 */
@Mixin(LeverBlock.class)
public abstract class LeverParticleMixin {

    @Redirect(
            method = "spawnParticles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldAccess;addParticleClient(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
            ),
            require = 1,
            expect = 1,
            allow = 1
    )
    private static void slabbed$offsetLeverDust(
            WorldAccess redirectWorld, ParticleEffect effect,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            BlockState state, WorldAccess world, BlockPos pos, float scale) {
        double visualDy = SlabSupport.getVisualYOffset(world, pos, state);
        redirectWorld.addParticleClient(
                effect,
                x,
                Double.isFinite(visualDy) ? y + visualDy : y,
                z,
                velocityX,
                velocityY,
                velocityZ);
    }
}
