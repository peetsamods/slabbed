package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Breaking particles above a lowered body sample its vacated band as open space. */
@Mixin(Particle.class)
public abstract class TerrainParticleLightProbeMixin {
    @Shadow @Final protected ClientLevel level;

    @Redirect(method = "getLightCoords", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;containing(DDD)Lnet/minecraft/core/BlockPos;"))
    private BlockPos slabbed$terrainLightProbe(double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!((Object) this instanceof TerrainParticle)) return pos;
        // Bound the same lowered-owner window used by the geometry checks.
        for (int step = 0; step < 16 && level.hasChunkAt(pos); step++) {
            var state = level.getBlockState(pos);
            if (state.isAir()) return pos;
            double dy = SlabSupport.getYOffset(level, pos, state);
            if (!Double.isFinite(dy) || dy >= 0 || y < pos.getY() + 1 + dy) return pos;
            pos = pos.above();
        }
        return pos;
    }
}
