package com.slabbed.mixin;

import com.slabbed.util.SlabbedOffsetColliderClip;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A lowered block shelters what hides behind its drawn body (maintainer ruling, 2026-08-29).
 *
 * <p>{@code getSeenPercent} fires a grid of rays from sample points across the victim's bounding
 * box toward the blast centre and returns the unoccluded fraction, which scales damage and
 * knockback. Every one of those rays asked vanilla's un-lowered collision shape, so cover that is
 * visibly solid sheltered nobody.
 *
 * <p>Scope is the method: {@code getSeenPercent} holds exactly one {@code Level.clip}, hardcoded
 * to {@code ClipContext.Block.COLLIDER}. The static target is the sole entry, so no gate is needed
 * and no other explosion behaviour is reached — notably not the block-destruction pass, which
 * chooses what to break by resistance rather than by clipping.
 *
 * <p>Routed through the OCCLUSION entry point: it is the ray grid that makes this the most
 * frequent of the three consumers, and there the fast path pays for itself — inside terrain most
 * sample rays already terminate on ordinary geometry and cost nothing extra.
 */
@Mixin(Explosion.class)
public abstract class ExplosionOcclusionOffsetClipMixin {

    @Redirect(
            method = "getSeenPercent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
            )
    )
    private static BlockHitResult slabbed$offsetAwareExplosionClip(Level level, ClipContext context) {
        BlockHitResult vanillaHit = level.clip(context);
        Entity viewer = null;
        return SlabbedOffsetColliderClip.clipForOcclusion(level, context, viewer, vanillaHit);
    }
}
