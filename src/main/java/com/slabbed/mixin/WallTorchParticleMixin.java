package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offsets wall torch flame and smoke particles by the same dy used for the model
 * (SlabSupport.getVisualYOffset), so the flame stays flush with the torch head when the
 * wall torch's mounting block is itself lowered (e.g., wall torch on a full block
 * lowered onto a bottom slab support).
 *
 * <p>Mirrors {@link TorchParticleMixin} exactly, but for the {@link WallTorchBlock}
 * variant. Without this mixin a wall torch on a lowered mount renders its model in
 * the lowered position while particles spawn at the unoffset Y, producing the
 * "hovering flame" visual.
 *
 * <p>Vanilla particle position formula (decompiled from
 * {@code WallTorchBlock.randomDisplayTick}):
 * <pre>{@code
 *   x = pos.getX() + 0.5 + 0.27 * facing.getOpposite().getOffsetX();
 *   y = pos.getY() + 0.7 + 0.22;
 *   z = pos.getZ() + 0.5 + 0.27 * facing.getOpposite().getOffsetZ();
 * }</pre>
 * The mod adds {@code dy} (from {@link SlabSupport#getVisualYOffset}) to the Y axis so
 * the flame tracks the offset model. X/Z are unchanged.
 */
@Mixin(WallTorchBlock.class)
public abstract class WallTorchParticleMixin {

    @Inject(method = "randomDisplayTick", at = @At("HEAD"), cancellable = true)
    private void slabbed$offsetParticles(BlockState state, World world, BlockPos pos, Random random, CallbackInfo ci) {
        double dy = SlabSupport.getVisualYOffset(world, pos, state);
        if (dy == 0.0) {
            return;
        }
        Direction facingOpp = state.get(Properties.HORIZONTAL_FACING).getOpposite();
        double x = pos.getX() + 0.5 + 0.27 * facingOpp.getOffsetX();
        double y = pos.getY() + 0.7 + 0.22 + dy;
        double z = pos.getZ() + 0.5 + 0.27 * facingOpp.getOffsetZ();
        // Particle field is on TorchBlock (parent); read via accessor since Mixin
        // @Shadow does not traverse class hierarchy.
        SimpleParticleType particle = ((TorchParticleAccessor) (Object) this).slabbed$getParticle();
        world.addParticleClient(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
        world.addParticleClient(particle, x, y, z, 0.0, 0.0, 0.0);
        ci.cancel();
    }
}
