package com.slabbed.mixin;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.TorchParticleTrace;
import net.minecraft.block.BlockState;
import net.minecraft.block.TorchBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offsets torch flame and smoke particles by the same dy used for the model
 * (SlabSupport.getYOffset), so particles stay flush with the torch head in all
 * slab contexts including the compound adjacent-side-slab case (-1.0).
 */
@Mixin(TorchBlock.class)
public abstract class TorchParticleMixin {

    @Shadow
    private SimpleParticleType particle;

    @Inject(method = "randomDisplayTick", at = @At("HEAD"), cancellable = true)
    private void slabbed$offsetParticles(BlockState state, World world, BlockPos pos, Random random, CallbackInfo ci) {
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy == 0.0) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.7 + dy;
        double z = pos.getZ() + 0.5;
        if (TorchParticleTrace.enabled()) {
            TorchParticleTrace.recordHook(pos, dy, y);
            Slabbed.LOGGER.info("TERRAIN_SLABS_TORCH_PARTICLE_LIVE_TRACE pos={} dy={} particleY={} state={}",
                    pos.toShortString(), dy, y, state);
        }
        world.addParticleClient(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
        world.addParticleClient(this.particle, x, y, z, 0.0, 0.0, 0.0);
        ci.cancel();
    }
}
