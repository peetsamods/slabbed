package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.particle.BlockDisplayParticleContext;
import com.slabbed.util.SlabSupport;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps shape-distributed particles within the translated object's local height. */
@Mixin(ParticleUtil.class)
public abstract class ParticleUtilOffsetMixin {

    @ModifyExpressionValue(
            method = "spawnParticlesAround(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;ILnet/minecraft/particle/ParticleEffect;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/shape/VoxelShape;getMax(Lnet/minecraft/util/math/Direction$Axis;)D"
            )
    )
    private static double slabbed$restoreObjectLocalHeight(
            double translatedMaxY,
            @Local(argsOnly = true) WorldAccess world,
            @Local(argsOnly = true) BlockPos pos
    ) {
        double dy = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        return Double.isFinite(dy) ? translatedMaxY - dy : translatedMaxY;
    }

    @WrapOperation(
            method = "spawnParticlesAround(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;IDDZLnet/minecraft/particle/ParticleEffect;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldAccess;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
            )
    )
    private static void slabbed$translateDistributedParticle(
            WorldAccess world, ParticleEffect effect,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            Operation<Void> original,
            @Local(argsOnly = true) BlockPos pos
    ) {
        double translatedY = BlockDisplayParticleContext.isActive()
                ? y
                : BlockDisplayParticleContext.translateY(
                        world, pos, world.getBlockState(pos), y);
        original.call(world, effect, x, translatedY, z, velocityX, velocityY, velocityZ);
    }
}
