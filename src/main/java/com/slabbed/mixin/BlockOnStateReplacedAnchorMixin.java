package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears the placement fact and every anchor/marker at {@code pos} when the block there stops
 * being that block — broken, replaced by a different block, or pushed out by a piston.
 *
 * <p>INVARIANT: a same-block state transition is the block STAYING, and its placed height stays
 * with it. Fences, walls and panes are rewritten in place by vanilla's neighbour update when
 * something appears beside them (same block, new connection shape); waterlogging, redstone power
 * and stair shape are the same kind of transition. None of those may clear the fact — a height
 * that vanishes on a reshape is a neighbour edit moving a placed block, which LAW.md forbids.
 *
 * <p>On this Minecraft version {@code WorldChunk.setBlockState} invokes {@code onStateReplaced} for
 * EVERY server-side state change, same block or not; vanilla's own implementation applies its
 * "different block?" test inside the method body, so a head injection sees every reshape. The
 * guard below is load-bearing. Do not remove it on the strength of a claim that the hook only
 * fires on a block-kind change — it does not, and the law gate's {@code fence_on_marked_slab}
 * subject reddens the moment the guard is gone.
 *
 * <p>This hook fires on the OLD state at {@code pos} only; it never fires for a neighbour's edit
 * (the supporting slab below being broken, for example), so anchor persistence across neighbour
 * edits holds by construction.
 */
@Mixin(AbstractBlock.class)
public abstract class BlockOnStateReplacedAnchorMixin {

    @Inject(method = "onStateReplaced", at = @At("HEAD"))
    private void slabbed$clearSlabAnchor(BlockState oldState, World world, BlockPos pos,
                                         BlockState newState, boolean moved, CallbackInfo ci) {
        // Same block, new state: the block stayed, so its placed height and markers stay with it.
        if (oldState.isOf(newState.getBlock())) {
            return;
        }
        // Different block, SAME shape: the placed thing is still there, transformed in place — a
        // compat grass slab becoming its dirt slab when covered. Its height stays (LAW.md corollary,
        // maintainer ruling 2026-09-13; live, the converted slab popped to grid height). A change of
        // shape (slab to carpet, block to slab) is a different thing and still clears.
        if (slabbed$sameShape(oldState, newState)) {
            return;
        }
        SlabAnchorAttachment.removeAnchor(world, pos);
    }

    /** Context-free shapes, so the lowered-shape mixins cannot feed shifted geometry back in. */
    @Unique
    private static boolean slabbed$sameShape(BlockState oldState, BlockState newState) {
        if (oldState.isAir() || newState.isAir() || !newState.getFluidState().isEmpty()) {
            return false;
        }
        VoxelShape before = oldState.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        VoxelShape after = newState.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        if (before.isEmpty() || after.isEmpty()) {
            before = oldState.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
            after = newState.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
            if (before.isEmpty() || after.isEmpty()) {
                return false;
            }
        }
        return !VoxelShapes.matchesAnywhere(before, after, BooleanBiFunction.NOT_SAME);
    }
}
