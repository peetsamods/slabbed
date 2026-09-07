package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears the persistent slab-anchor at {@code pos} when the anchored block itself is
 * broken or replaced.
 *
 * <p>Vanilla invokes {@code affectNeighborsAfterRemoval} when the {@link net.minecraft.world.level.block.Block
 * block} kind changes — property-only updates do not fire this hook, so the anchor
 * survives state transitions on the same block. Crucially, this hook fires on the OLD
 * state at {@code pos} (the anchored block) — it does NOT fire when a neighbour like
 * the supporting bottom slab below is broken, so anchor persistence is preserved.
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockOnStateReplacedAnchorMixin {

    @Inject(method = "affectNeighborsAfterRemoval", at = @At("HEAD"))
    private void slabbed$clearSlabAnchor(BlockState oldState, ServerLevel world, BlockPos pos,
                                         boolean moved, CallbackInfo ci) {
        // The piston's moving-piston block is not a block a player placed — it is the animation
        // stand-in for the block that is ARRIVING, and the height stored in this cell was written FOR
        // that arriving block (maintainer ruling, 2026-09-06). A cell handing off from the stand-in to
        // a real occupant keeps its height; handing off to AIR is a real departure and still clears —
        // that is the interrupted source piston, which lands air in its own cell.
        //
        // Keyed on the block's IDENTITY, never on an update-flag value, so a vanilla flag reshuffle
        // cannot silently disarm it. Do not narrow this to one of the two moved branches: the two
        // landing paths reach this hook differently — the ticked landing carries the moved-by-piston
        // bit (moved=true, which clears unconditionally below) and the interrupt landing is an
        // ordinary update (moved=false, where a full cube would survive but a slab or carpet would
        // not) — and both are exercised by the piston transfer rows.
        if (oldState.is(Blocks.MOVING_PISTON) && !world.getBlockState(pos).isAir()) {
            return;
        }
        // D1 port (donor: 1.21.11 78ec0ac4): only clear the height-lock when the block genuinely
        // LEAVES this cell. An in-place block-KIND transform to another lock-eligible block
        // (grass_block -> dirt from a random tick, log -> stripped_log, copper oxidation) keeps the
        // lock so the block does not un-lower / jitter with no player action (the state-change jitter
        // defense the port was missing — audit D1). A real break (-> air / fluid) or a replacement
        // with a non-lock block still clears it. The hook fires AFTER the new state is set, so
        // getBlockState(pos) here is the replacement.
        if (!moved && SlabAnchorAttachment.replacementPreservesAnchor(
                world, pos, oldState, world.getBlockState(pos))) {
            return;
        }
        SlabAnchorAttachment.removeAnchor(world, pos);
    }
}
