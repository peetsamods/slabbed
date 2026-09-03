package com.slabbed.mixin;

import com.slabbed.util.SlabbedOffsetColliderClip;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A lowered block shelters what hides behind its drawn body (maintainer ruling, 2026-08-29).
 *
 * <p>{@code getSeenPercent} fires a grid of rays from the victim's bounding box toward the blast
 * centre; the unoccluded fraction scales damage and knockback. Every ray asked collision geometry
 * that ignored the drawn lower half of a lowered block, so visibly solid cover sheltered nobody.
 *
 * <p>Seam divergence from the NeoForge sibling, deliberate: 26.2 hosts this method on
 * {@code ServerExplosion}, not {@code Explosion} (verified in bytecode — the class's single
 * {@code Level.clip}). Routed through the OCCLUSION entry point; the ray grid makes this the most
 * frequent consumer, and rays vanilla already terminated cost nothing.
 *
 * <p><b>Withheld while Lithium is present</b> (maintainer ruling, 2026-09-02): Lithium's
 * {@code world.explosions.entity_raycast} module redirects this exact call, and two redirects
 * cannot share one call site — the second scans zero targets and, with {@code require = 1}, fails
 * the injection check before the title screen. {@link SlabbedMixinConfigPlugin} skips this mixin
 * alone under the mod id {@code lithium}; explosion occlusion then follows vanilla rules.
 * {@code require = 1} stays — do not lower it to "tolerate" the conflict: a tolerated zero-match is
 * a silent loss of the fix behind a green suite.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionOcclusionOffsetClipMixin {

    @Redirect(
            require = 1,
            method = "getSeenPercent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
            )
    )
    private static BlockHitResult slabbed$offsetAwareExplosionClip(Level level, ClipContext context) {
        BlockHitResult vanillaHit = level.clip(context);
        return SlabbedOffsetColliderClip.clipForOcclusion(level, context, vanillaHit);
    }
}
