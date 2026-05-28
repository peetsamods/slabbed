package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import com.slabbed.util.TorchParticleTrace;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallRedstoneTorchBlock;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WallRedstoneTorchBlock.class)
public abstract class WallRedstoneTorchParticleMixin {

    @Inject(method = "randomDisplayTick", at = @At("HEAD"), cancellable = true)
    private void slabbed$offsetParticles(BlockState state, World world, BlockPos pos, Random random, CallbackInfo ci) {
        if (!state.get(WallRedstoneTorchBlock.LIT)) {
            return;
        }
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy == 0.0) {
            return;
        }
        Direction facingOpp = state.get(Properties.HORIZONTAL_FACING).getOpposite();
        double x = pos.getX() + 0.5
                + (random.nextDouble() - 0.5) * 0.2
                + 0.27 * facingOpp.getOffsetX();
        double y = pos.getY() + 0.7
                + (random.nextDouble() - 0.5) * 0.2
                + 0.22
                + dy;
        double z = pos.getZ() + 0.5
                + (random.nextDouble() - 0.5) * 0.2
                + 0.27 * facingOpp.getOffsetZ();
        if (TorchParticleTrace.enabled()) {
            TorchParticleTrace.recordHook(pos, dy, y);
            System.out.println("TERRAIN_SLABS_WALL_REDSTONE_TORCH_PARTICLE_LIVE_TRACE pos=" + pos.toShortString()
                    + " dy=" + dy
                    + " particleY=" + y
                    + " state=" + state);
        }
        world.addParticleClient(DustParticleEffect.DEFAULT, x, y, z, 0.0, 0.0, 0.0);
        ci.cancel();
    }
}
