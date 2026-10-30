package com.slabbed.mixin;

import com.slabbed.util.SlabbedOffsetColliderClip;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Arrows and tridents hit a lowered block where it is drawn (maintainer ruling, 2026-08-29).
 *
 * <p><b>Correction of record (2026-08-29):</b> this line's arrow block clip does NOT route through
 * {@code ProjectileUtil} — 26.2 moved {@code AbstractArrow} to the {@code projectile.arrow}
 * subpackage and its {@code tick()} clips via {@code Level.clipIncludingBorder} (border-aware — a nearer owner hit still wins the distance comparison over a border hit), verified in bytecode
 * AFTER a first javap against the old package name failed silently and its empty output was read
 * as "no clip here". The {@code ProjectileUtil} redirect covers the throwable family (snowballs,
 * eggs, potions via {@code ThrowableProjectile}); THIS mixin covers arrows, spectral arrows, and
 * tridents, which extend {@code AbstractArrow} and call {@code super.tick()} unconditionally.
 *
 * <p>Mode gate lives in {@link SlabbedOffsetColliderClip}; the helper passes non-COLLIDER
 * contexts through untouched.
 */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowOffsetClipMixin {

    @Redirect(
            method = "tick",
            require = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clipIncludingBorder(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
            )
    )
    private BlockHitResult slabbed$offsetAwareArrowClip(Level level, ClipContext context) {
        BlockHitResult vanillaHit = level.clip(context);
        return SlabbedOffsetColliderClip.clipForProjectile(level, context, vanillaHit);
    }

    /**
     * A resting arrow must be judged against the body it rests on. {@code tick}'s embedded-body
     * probe reads the TWO-argument {@code getCollisionShape(level, pos)}, which answers from the
     * state cache (the raw cube) and so never sees a lowered body; its flying clip and
     * {@code shouldFall} do. An arrow inside the grid cell but above the drawn top was therefore
     * grounded by the probe and released by {@code shouldFall} every tick: frozen in the air, life
     * reset, never despawning (GitHub #121). The probe is sent through the three-argument form with
     * the same empty context the cache was built with, so unlowered blocks answer exactly as before.
     */
    @Redirect(
            method = "tick",
            require = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
            )
    )
    private VoxelShape slabbed$restingBodyShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getCollisionShape(level, pos, CollisionContext.empty());
    }
}
