package com.slabbed.mixin;

import net.minecraft.world.level.ClipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to a {@link ClipContext}'s block-shape mode.
 *
 * <p>Exists for one load-bearing check: the offset-aware collider clip must augment ONLY
 * {@code COLLIDER}-mode rays. On this line the mode is not knowable from the call site — 26.2's
 * {@code ProjectileUtil.getHitResult} and {@code LivingEntity.hasLineOfSight} both take the mode as
 * a PARAMETER, so the redirected {@code Level.clip} can legitimately receive OUTLINE or VISUAL
 * contexts. Augmenting one of those with collision geometry would block a query that is asking a
 * different question. Vanilla exposes no getter for the field; this accessor is the smallest
 * possible answer.
 */
@Mixin(ClipContext.class)
public interface ClipContextBlockAccessor {

    @Accessor("block")
    ClipContext.Block slabbed$blockMode();
}
