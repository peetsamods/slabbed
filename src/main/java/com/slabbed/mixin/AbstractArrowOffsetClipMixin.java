package com.slabbed.mixin;

import com.slabbed.util.SlabbedOffsetColliderClip;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Arrows-only offset-aware block collision (maintainer ruling, 2026-08-28). Scope is exact, not
 * conventional: {@code AbstractArrow.tick()} contains exactly one {@code Level.clip} call, and it
 * is the sole block-collision test for arrows, spectral arrows, and tridents (all three override
 * {@code tick()} but call {@code super.tick()} unconditionally). No gate is needed here — the
 * target method IS the scope, and the call site is unconditionally
 * {@code ClipContext.Block.COLLIDER} in vanilla source, never any other mode.
 *
 * <p>See {@link SlabbedOffsetColliderClip} for why this is architecturally safe where lowering the
 * per-state collision shape itself would not be.
 */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowOffsetClipMixin {

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
            )
    )
    private BlockHitResult slabbed$offsetAwareArrowClip(Level level, ClipContext context) {
        BlockHitResult vanillaHit = level.clip(context);
        return SlabbedOffsetColliderClip.clip(level, context, (AbstractArrow) (Object) this, vanillaHit);
    }
}
