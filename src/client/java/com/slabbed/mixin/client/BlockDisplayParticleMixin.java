package com.slabbed.mixin.client;

import com.slabbed.particle.BlockDisplayParticleContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Translates every particle emitted by a vanilla block display tick exactly once. */
@Mixin(ClientWorld.class)
public abstract class BlockDisplayParticleMixin {

    @Redirect(
            method = "randomBlockDisplayTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;randomDisplayTick(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)V"
            )
    )
    private void slabbed$runAtStoredHeight(
            Block block, BlockState state, World world, BlockPos pos, Random random
    ) {
        BlockDisplayParticleContext.runBlockDisplayTick(block, state, world, pos, random);
    }

    @ModifyVariable(
            method = {
                    "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V",
                    "addParticle(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V",
                    "addImportantParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V",
                    "addImportantParticle(Lnet/minecraft/particle/ParticleEffect;ZDDDDDD)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1
    )
    private double slabbed$translateParticleY(double y) {
        return BlockDisplayParticleContext.translateActiveY(y);
    }
}
