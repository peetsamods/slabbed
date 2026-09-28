package com.slabbed.mixin;

import com.slabbed.util.RailSlopeProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A rail's outline and raycast box follow the slope it is drawn with (maintainer ruling,
 * 2026-09-28).
 *
 * <p>Vanilla sizes the box from the rail's own shape alone: 2/16 tall for a flat rail, 8/16 for a
 * full-block ramp. When {@link RailSlopeProfile} fits the rail's slope to a neighbour at a different
 * seat, the drawn rail may rise half a block above a "flat" box or stop well short of a "ramp" box,
 * so the player could not target the rail where it is drawn. The box is sized from the fitted
 * profile instead; the state mixin then moves it by the rail's seat exactly as before.
 *
 * <p>INVARIANT: the box is derived from the same profile the model is drawn from, so outline,
 * raycast and drawn geometry cannot disagree. Rails carry no collision, so this changes no movement.
 *
 * <p>INVARIANT (LAW.md): a read of stored seats only; no rail's own height is written or re-derived.
 */
@Mixin(BaseRailBlock.class)
public abstract class BaseRailBlockSlopeShapeMixin {

    @Inject(method = "getShape(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$boxFollowsTheDrawnSlope(BlockState state, BlockGetter world, BlockPos pos,
                                                 CollisionContext context,
                                                 CallbackInfoReturnable<VoxelShape> cir) {
        // The state cache is initialised at bootstrap against an empty view: no neighbour can exist
        // there, so the answer is vanilla's by construction and the reads are skipped.
        if (world instanceof EmptyBlockGetter) {
            return;
        }
        RailSlopeProfile.Profile profile;
        try {
            profile = RailSlopeProfile.resolve(world, pos, state);
        } catch (IndexOutOfBoundsException boundedView) {
            // A bounds-limited view that cannot answer for the neighbour gets vanilla's box, the
            // same fallback the render path takes at a region border.
            return;
        }
        if (profile == null || profile.isVanilla()) {
            return;
        }
        cir.setReturnValue(RailSlopeProfile.outlineShape(profile));
    }
}
