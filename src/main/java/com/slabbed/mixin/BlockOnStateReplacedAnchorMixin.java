package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
        // D1 port (donor: 1.21.11 78ec0ac4): only clear the height-lock when the block genuinely
        // LEAVES this cell. An in-place block-KIND transform to another lock-eligible block
        // (grass_block -> dirt from a random tick, log -> stripped_log, copper oxidation) keeps the
        // lock so the block does not un-lower / jitter with no player action (the state-change jitter
        // defense the port was missing — audit D1). A piston move (moved), a real break (-> air /
        // fluid), or a replacement with a non-lock block still clears it. The hook fires AFTER the new
        // state is set, so getBlockState(pos) here is the replacement.
        if (!moved && SlabAnchorAttachment.replacementPreservesAnchor(
                world, pos, oldState, world.getBlockState(pos))) {
            return;
        }
        SlabAnchorAttachment.removeAnchor(world, pos);
    }
}
