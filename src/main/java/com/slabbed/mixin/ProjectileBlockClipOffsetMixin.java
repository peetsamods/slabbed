package com.slabbed.mixin;

import com.slabbed.util.SlabbedOffsetColliderClip;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A projectile hits a lowered block where it is drawn (maintainer ruling, 2026-08-29).
 *
 * <p>Seam divergence from the NeoForge sibling, deliberate: their line's arrow block clip lives in
 * {@code AbstractArrow.tick}, so their fix is arrows-only. 26.2 routes ALL projectile block
 * clipping through the private static funnel {@code ProjectileUtil.getHitResult} (verified in
 * bytecode: three {@code Level.clipIncludingBorder} calls — NOT plain {@code clip}; the first version of this mixin targeted {@code clip}, matched nothing, and SILENTLY no-opped through green suites because {@code @Redirect} without {@code require} tolerates zero matches. Every clip redirect in this mod now carries {@code require = 1} for exactly that reason), so targeting it covers arrows,
 * tridents, snowballs, and fireballs in one exact place.
 *
 * <p>The funnel takes its clip MODE as a parameter, so this redirect can legitimately observe
 * non-COLLIDER contexts; {@link SlabbedOffsetColliderClip} gates on the mode itself and passes
 * those through untouched.
 */
@Mixin(ProjectileUtil.class)
public abstract class ProjectileBlockClipOffsetMixin {

    @Redirect(
            require = 1,
            method = "getHitResult(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/Level;FLnet/minecraft/world/level/ClipContext$Block;)Lnet/minecraft/world/phys/HitResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clipIncludingBorder(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
            )
    )
    private static BlockHitResult slabbed$offsetAwareProjectileClip(Level level, ClipContext context) {
        BlockHitResult vanillaHit = level.clip(context);
        return SlabbedOffsetColliderClip.clipForProjectile(level, context, vanillaHit);
    }
}
