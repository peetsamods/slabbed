package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RedstoneTorchBlock.class)
public abstract class RedstoneTorchParticleMixin {

    @Inject(method = "randomDisplayTick", at = @At("HEAD"), cancellable = true)
    private void slabbed$offsetParticles(BlockState state, World world, BlockPos pos, Random random, CallbackInfo ci) {
        if (!state.get(RedstoneTorchBlock.LIT)) {
            return;
        }
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy == 0.0) {
            return;
        }
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
        double y = pos.getY() + 0.7 + (random.nextDouble() - 0.5) * 0.2 + dy;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
        world.addParticleClient(DustParticleEffect.DEFAULT, x, y, z, 0.0, 0.0, 0.0);
        ci.cancel();
    }
}
