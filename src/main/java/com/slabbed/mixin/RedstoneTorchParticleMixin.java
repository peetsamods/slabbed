package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offsets the lit redstone torch's dust particle by the model's dy ({@link SlabSupport#getYOffset}) so
 * the particle stays at the torch head when the torch is lowered on a slab.
 *
 * <p>{@link RedstoneTorchBlock} extends {@code BaseTorchBlock} (NOT {@code TorchBlock}) and has its own
 * {@code animateTick}, so {@link TorchParticleMixin} (on {@code TorchBlock}) does not cover it — without
 * this the redstone dust emits 0.5 above the lowered model (the regular torch was already correct;
 * Julia 2026-06-19). Mirrors vanilla's position + 0.2 jitter, only shifted by dy, and only when LIT.
 * The wall variant ({@code RedstoneWallTorchBlock}) overrides {@code animateTick} and is unaffected.
 */
@Mixin(RedstoneTorchBlock.class)
public abstract class RedstoneTorchParticleMixin {

    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void slabbed$offsetRedstoneTorchParticle(BlockState state, Level world, BlockPos pos,
                                                     RandomSource random, CallbackInfo ci) {
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy == 0.0) {
            return;   // vanilla position is already correct
        }
        if (!state.getValue(BlockStateProperties.LIT)) {
            return;   // unlit emits nothing — let vanilla handle it
        }
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
        double y = pos.getY() + 0.7 + dy + (random.nextDouble() - 0.5) * 0.2;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
        world.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0.0, 0.0, 0.0);
        ci.cancel();
    }
}
