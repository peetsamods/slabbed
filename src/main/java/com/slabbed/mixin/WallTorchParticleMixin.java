package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offsets wall torch flame and smoke particles by the same dy used for the model
 * (SlabSupport.getYOffset), so the flame stays flush with the torch head when the
 * wall torch's mounting block is itself lowered (e.g., wall torch on a full block
 * lowered onto a bottom slab support).
 *
 * <p>Mirrors {@link TorchParticleMixin} exactly, but for the {@link WallTorchBlock}
 * variant. Without this mixin a wall torch on a lowered mount renders its model in
 * the lowered position while particles spawn at the unoffset Y, producing the
 * "hovering flame" visual.
 *
 * <p>Vanilla particle position formula (decompiled from
     * {@code WallTorchBlock.animateTick}):
 * <pre>{@code
 *   x = pos.getX() + 0.5 + 0.27 * facing.getOpposite().getOffsetX();
 *   y = pos.getY() + 0.7 + 0.22;
 *   z = pos.getZ() + 0.5 + 0.27 * facing.getOpposite().getOffsetZ();
 * }</pre>
 * The mod adds {@code dy} (from {@link SlabSupport#getYOffset}) to the Y axis so
 * the flame tracks the offset model. X/Z are unchanged.
 */
@Mixin(WallTorchBlock.class)
public abstract class WallTorchParticleMixin {

    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void slabbed$offsetParticles(BlockState state, Level world, BlockPos pos, RandomSource random, CallbackInfo ci) {
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy == 0.0) {
            return;
        }
        Direction facingOpp = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        double x = pos.getX() + 0.5 + 0.27 * facingOpp.getStepX();
        double y = pos.getY() + 0.7 + 0.22 + dy;
        double z = pos.getZ() + 0.5 + 0.27 * facingOpp.getStepZ();
        // Particle field is on TorchBlock (parent); read via accessor since Mixin
        // @Shadow does not traverse class hierarchy.
        SimpleParticleType particle = ((TorchParticleAccessor) (Object) this).slabbed$getParticle();
        world.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
        world.addParticle(particle, x, y, z, 0.0, 0.0, 0.0);
        ci.cancel();
    }
}
