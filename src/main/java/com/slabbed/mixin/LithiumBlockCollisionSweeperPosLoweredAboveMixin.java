package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lithium half of the COLLISION-FOLLOW broadphase, position-yielding sweeper (maintainer ruling,
 * 2026-09-02).
 *
 * <p>With Lithium installed, entity-versus-block collision never passes vanilla's
 * {@code BlockCollisions} iterator: Lithium overwrites {@code Level.noCollision},
 * {@code Level.findSupportingBlock} and {@code Entity.collideBoundingBox} and walks its own
 * chunk-aware sweepers instead, so {@link BlockCollisionsLoweredAboveMixin}'s hanging-shape union
 * was never consulted and a lowered block's lower half was walk-through under Lithium. Each
 * sweeper asks the SAME per-cell question vanilla asks, {@code CollisionContext.getCollisionShape},
 * for every cell of the queried box (air included), so this redirects that one call the same way.
 *
 * <p>Admitted by {@link SlabbedMixinConfigPlugin} ONLY after it has confirmed from the sweeper's
 * own bytes that this exact method and this exact call are still there (the injection point
 * strings below are the plugin's own constants, so probe and injection cannot drift apart) —
 * keyed on Lithium's code, never its mod id; otherwise
 * withheld, with a warning when Lithium's mod id is loaded. A Lithium refactor must degrade to
 * Lithium's rules, never crash the game. {@code require = 1} stays as the backstop for anything
 * the byte check did not foresee.
 *
 * <p>Known boundary: Lithium skips chunk sections that are entirely air before iterating cells,
 * so a lowered block hanging into an all-air section is still missed by a box lying wholly inside
 * that section. Vanilla's iterator has no such skip.
 */
@Mixin(targets = "net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeperBlockPos", remap = false)
public abstract class LithiumBlockCollisionSweeperPosLoweredAboveMixin {

    @Redirect(
            method = SlabbedMixinConfigPlugin.SWEEPER_POS_ENTRY,
            require = 1,
            at = @At(
                    value = "INVOKE",
                    target = SlabbedMixinConfigPlugin.COLLISION_SHAPE_CALL_TARGET
            )
    )
    private VoxelShape slabbed$addHangingLoweredAbove(CollisionContext ctx, BlockState state,
                                                      CollisionGetter getter, BlockPos pos) {
        VoxelShape own = ctx.getCollisionShape(state, getter, pos);
        return SlabSupport.withHangingLoweredCollisionFromAbove(own, getter, pos);
    }
}
