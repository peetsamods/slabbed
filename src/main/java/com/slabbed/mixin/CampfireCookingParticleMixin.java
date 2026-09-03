package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The cooking-food smoke half of {@link CampfireParticleMixin}: {@code particleTick} spawns
 * per-item smoke directly and needs the same drawn-height offset.
 */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireCookingParticleMixin {

    @WrapOperation(
            method = "particleTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private static void slabbed$offsetCookingSmoke(
            Level level, ParticleOptions type,
            double x, double y, double z, double vx, double vy, double vz,
            Operation<Void> original,
            @Local(argsOnly = true) BlockPos pos
    ) {
        double dy = SlabSupport.getYOffset(level, pos, level.getBlockState(pos));
        original.call(level, type,
                x, y + (Double.isFinite(dy) ? dy : 0.0d), z, vx, vy, vz);
    }
}
