package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.util.BlockDisplayParticleContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Ambient particles follow the drawn height: one scope around the vanilla block display tick, one
 * translation in the client's single particle funnel.
 *
 * <p>Invariants:
 * <ul>
 *   <li>Do not re-add a per-block ambient particle mixin. Every block whose display tick emits is
 *       covered here exactly once. A per-block hook that must stay for a reason the funnel cannot
 *       serve — it also moves a SOUND, or its helper is shared with a route outside the display
 *       tick — declares {@code BlockDisplayParticleContext.beginOwnedEmission()}/
 *       {@code endOwnedEmission()} around its emission instead of standing the funnel down.</li>
 *   <li>The scope covers the block display-tick call ONLY. The fluid display tick, the fluid drip
 *       tail, the block-marker particle and the environment ambient-particle loop sit in the same
 *       vanilla method but outside the wrapped call, and must never inherit a block's dy.</li>
 *   <li>No call-site ordinal anywhere: the wrapped invoke is the sole occurrence in its method, and
 *       the funnel hook keys on an ARGUMENT ordinal fixed by the signature, not on a call index.
 *       Both fail the build loudly under {@code defaultRequire} if the target moves.</li>
 * </ul>
 * (maintainer ruling, 2026-09-06)
 */
@Mixin(ClientLevel.class)
public abstract class BlockDisplayParticleMixin {

    @WrapOperation(
            method = "doAnimateTick(IIIILnet/minecraft/util/RandomSource;"
                    + "Lnet/minecraft/world/level/block/Block;"
                    + "Lnet/minecraft/core/BlockPos$MutableBlockPos;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;animateTick("
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/world/level/Level;"
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/util/RandomSource;)V"
            ),
            require = 1
    )
    private void slabbed$runBlockDisplayTickAtStoredHeight(
            Block block, BlockState state, Level level, BlockPos pos, RandomSource random,
            Operation<Void> original) {
        BlockDisplayParticleContext.open(level, pos, state);
        try {
            original.call(block, state, level, pos, random);
        } finally {
            BlockDisplayParticleContext.close();
        }
    }

    /**
     * One target, not four: every public particle overload funnels here and nothing else calls it.
     * {@code argsOnly} ordinal 1 is the second double in the descriptor, which is Y. HEAD is
     * upstream of the camera-distance and particle-status early returns, so a culled particle costs
     * no dy read beyond the first in its scope.
     */
    @ModifyVariable(
            method = "doAddParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1,
            require = 1
    )
    private double slabbed$translateBlockDisplayParticleY(double y) {
        return BlockDisplayParticleContext.translateActiveY(y);
    }
}
