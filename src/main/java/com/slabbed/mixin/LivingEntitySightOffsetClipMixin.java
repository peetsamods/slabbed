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
 * <p>26.2 splits {@code hasLineOfSight} into two overloads; the parameterised one holds the single
 * {@code Level.clip} (verified in bytecode) and receives its clip mode as an argument, so the mode
 * gate lives in {@link SlabbedOffsetColliderClip} rather than being assumed from the call site —
 * a caller asking a VISUAL question passes through untouched.
 *
 * <p>Routed through the OCCLUSION entry point: a ray vanilla already terminated costs nothing, so
 * inside ordinary terrain most sight checks pay zero for this.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySightOffsetClipMixin {

    @Redirect(
            require = 1,
            method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;D)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
            )
    )
    private BlockHitResult slabbed$offsetAwareSightClip(Level level, ClipContext context) {
        BlockHitResult vanillaHit = level.clip(context);
        return SlabbedOffsetColliderClip.clipForOcclusion(level, context, vanillaHit);
    }
}
