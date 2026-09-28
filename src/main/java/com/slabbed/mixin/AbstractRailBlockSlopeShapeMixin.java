package com.slabbed.mixin;

import com.slabbed.util.RailSlopeProfile;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;
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
 * <p>INVARIANT (LAW.md): a read of seats only; no rail's own height is written or re-derived.
 */
@Mixin(AbstractRailBlock.class)
public abstract class AbstractRailBlockSlopeShapeMixin {

    @Inject(method = "getOutlineShape(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/BlockView;"
            + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)"
            + "Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$boxFollowsTheDrawnSlope(BlockState state, BlockView world, BlockPos pos,
                                                 ShapeContext context,
                                                 CallbackInfoReturnable<VoxelShape> cir) {
        // The state cache is initialised at bootstrap against an empty view: no neighbour can exist
        // there, so the answer is vanilla's by construction and the reads are skipped.
        if (world instanceof EmptyBlockView) {
            return;
        }
        // Light, opacity and world-generation workers reach shape queries off the server thread,
        // where a neighbour read can wait on a chunk that is still loading. The seat mixin leaves
        // those threads on vanilla geometry (SlabSupportStateMixin); the slope does the same, so the
        // two never disagree.
        String thread = Thread.currentThread().getName();
        if (thread.startsWith("Worker-Main") || thread.contains("ForkJoinPool")) {
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
