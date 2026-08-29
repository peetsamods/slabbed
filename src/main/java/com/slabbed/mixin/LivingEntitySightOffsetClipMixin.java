package com.slabbed.mixin;

import com.slabbed.util.SlabbedOffsetColliderClip;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mob line of sight sees a lowered block's drawn body (maintainer ruling, 2026-08-29).
 *
 * <p>Scope is the method, not a runtime test: {@code hasLineOfSight} holds exactly one
 * {@code Level.clip} call, hardcoded to {@code ClipContext.Block.COLLIDER}, and nothing else in
 * {@code LivingEntity} clips for visibility. Targeting the method is therefore exact — no gate can
 * be wrong because no other caller reaches this injector.
 *
 * <p>Routed through the OCCLUSION entry point, which returns immediately when vanilla already
 * found an obstruction. A boolean consumer cannot distinguish which obstruction blocked it, so a
 * ray that already terminates costs nothing extra — see {@link SlabbedOffsetColliderClip} for why
 * that fast path is sound here and deliberately absent for arrows.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySightOffsetClipMixin {

    @Redirect(
            method = "hasLineOfSight",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
            )
    )
    private BlockHitResult slabbed$offsetAwareSightClip(Level level, ClipContext context) {
        BlockHitResult vanillaHit = level.clip(context);
        return SlabbedOffsetColliderClip.clipForOcclusion(
                level, context, (LivingEntity) (Object) this, vanillaHit);
    }
}
