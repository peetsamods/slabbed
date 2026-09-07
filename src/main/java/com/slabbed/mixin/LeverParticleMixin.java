package com.slabbed.mixin;

import com.slabbed.util.BlockDisplayParticleContext;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps lever dust attached to the lever's frozen visual height. Both click and ambient emissions
 * share vanilla's private {@code makeParticle} helper, so redirect its single particle call and
 * preserve vanilla's orientation formula, particle options, scale, and velocity unchanged.
 *
 * <p>Invariant: {@code makeParticle} is shared by the click route (use without item) and the
 * ambient route (block display tick), so this hook owns BOTH and the two routes stay one formula.
 * It declares an owned emission so the display-tick funnel in
 * {@code BlockDisplayParticleMixin} does not translate the ambient one a second time
 * (maintainer ruling, 2026-09-06).
 */
@Mixin(LeverBlock.class)
public abstract class LeverParticleMixin {

    @Redirect(
            method = "makeParticle",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
            ),
            require = 1,
            expect = 1,
            allow = 1
    )
    private static void slabbed$offsetLeverDust(
            LevelAccessor redirectLevel, ParticleOptions effect,
            double x, double y, double z,
            double vx, double vy, double vz,
            BlockState state, LevelAccessor level, BlockPos pos, float scale) {
        double dy = SlabSupport.getYOffset(level, pos, state);
        BlockDisplayParticleContext.beginOwnedEmission();
        try {
            redirectLevel.addParticle(effect, x, Double.isFinite(dy) ? y + dy : y, z, vx, vy, vz);
        } finally {
            BlockDisplayParticleContext.endOwnedEmission();
        }
    }
}
