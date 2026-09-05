package com.slabbed.mixin;

import com.slabbed.particle.BlockDisplayParticleContext;
import net.minecraft.block.CampfireBlock;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps campfire smoke at the stored height without double-shifting ambient display ticks. */
@Mixin(CampfireBlock.class)
public abstract class CampfireSmokeParticleMixin {

    @Redirect(
            method = "spawnSmokeParticle",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;addImportantParticle(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V"
            ),
            require = 1,
            expect = 1,
            allow = 1
    )
    private static void slabbed$offsetImportantSmoke(
            World redirectWorld, ParticleEffect effect, boolean alwaysSpawn,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            World world, BlockPos pos, boolean signal, boolean extraSmoke
    ) {
        redirectWorld.addImportantParticle(effect, alwaysSpawn, x,
                slabbed$translatedY(world, pos, y), z,
                velocityX, velocityY, velocityZ);
    }

    @Redirect(
            method = "spawnSmokeParticle",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
            ),
            require = 1,
            expect = 1,
            allow = 1
    )
    private static void slabbed$offsetExtraSmoke(
            World redirectWorld, ParticleEffect effect,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            World world, BlockPos pos, boolean signal, boolean extraSmoke
    ) {
        redirectWorld.addParticle(effect, x, slabbed$translatedY(world, pos, y), z,
                velocityX, velocityY, velocityZ);
    }

    private static double slabbed$translatedY(World world, BlockPos pos, double y) {
        return BlockDisplayParticleContext.isActive()
                ? y
                : BlockDisplayParticleContext.translateY(
                        world, pos, world.getBlockState(pos), y);
    }
}
