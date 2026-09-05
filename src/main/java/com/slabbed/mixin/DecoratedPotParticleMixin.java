package com.slabbed.mixin;

import com.slabbed.particle.BlockDisplayParticleContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps the decorated-pot insertion burst at the pot's stored visual height. */
@Mixin(DecoratedPotBlock.class)
public abstract class DecoratedPotParticleMixin {

    @Redirect(
            method = "onUseWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I"
            )
    )
    private <T extends ParticleEffect> int slabbed$offsetInsertionBurst(
            ServerWorld redirectWorld, T effect,
            double x, double y, double z,
            int count, double spreadX, double spreadY, double spreadZ, double speed,
            ItemStack stack, BlockState state, World world, BlockPos pos,
            PlayerEntity player, Hand hand, BlockHitResult hitResult
    ) {
        return redirectWorld.spawnParticles(effect, x,
                BlockDisplayParticleContext.translateY(world, pos, state, y), z,
                count, spreadX, spreadY, spreadZ, speed);
    }
}
