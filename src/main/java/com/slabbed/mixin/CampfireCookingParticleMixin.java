package com.slabbed.mixin;

import com.slabbed.particle.BlockDisplayParticleContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps cooking-food smoke at the campfire's stored visual height. */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireCookingParticleMixin {

    @Redirect(
            method = "clientTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
            )
    )
    private static void slabbed$offsetCookingSmoke(
            World redirectWorld, ParticleEffect effect,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            World world, BlockPos pos, BlockState state, CampfireBlockEntity blockEntity
    ) {
        redirectWorld.addParticle(effect, x,
                BlockDisplayParticleContext.translateY(world, pos, state, y), z,
                velocityX, velocityY, velocityZ);
    }
}
