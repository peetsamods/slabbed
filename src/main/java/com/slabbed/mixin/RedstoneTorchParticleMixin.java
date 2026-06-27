package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redstone torches own their particle tick instead of inheriting TorchBlock's
 * flame/smoke path, so they need their own Slabbed dy hook.
 */
@Mixin(RedstoneTorchBlock.class)
public abstract class RedstoneTorchParticleMixin {

    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void slabbed$offsetRedstoneParticles(
            BlockState state,
            Level world,
            BlockPos pos,
            RandomSource random,
            CallbackInfo ci
    ) {
        if (state.hasProperty(RedstoneTorchBlock.LIT) && !state.getValue(RedstoneTorchBlock.LIT)) {
            return;
        }
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy == 0.0d) {
            return;
        }
        double x = pos.getX() + 0.5d + (random.nextDouble() - 0.5d) * 0.2d;
        double y = pos.getY() + 0.7d + dy + (random.nextDouble() - 0.5d) * 0.2d;
        double z = pos.getZ() + 0.5d + (random.nextDouble() - 0.5d) * 0.2d;
        world.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0.0d, 0.0d, 0.0d);
        ci.cancel();
    }
}
