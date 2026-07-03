package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears the persistent slab-anchor at {@code pos} when the anchored block itself is
 * broken or replaced.
 *
 * <p>Vanilla 1.21 only invokes {@code onStateReplaced} when the {@link net.minecraft.block.Block
 * block} kind changes — property-only updates do not fire this hook, so the anchor
 * survives state transitions on the same block. Crucially, this hook fires on the OLD
 * state at {@code pos} (the anchored block) — it does NOT fire when a neighbour like
 * the supporting bottom slab below is broken, so anchor persistence is preserved.
 */
@Mixin(AbstractBlock.class)
public abstract class BlockOnStateReplacedAnchorMixin {

    @Inject(method = "onStateReplaced", at = @At("HEAD"))
    private void slabbed$clearSlabAnchor(BlockState oldState, ServerWorld world, BlockPos pos,
                                         boolean moved, CallbackInfo ci) {
        // Only clear the height-lock when the block genuinely LEAVES this cell. An in-place
        // block-KIND transform to another lock-eligible block (grass_block -> dirt, log ->
        // stripped_log) keeps the lock so the block does not un-lower / jitter (WYSIWYG — the
        // grass-tower conversion jitter). A piston move (moved), a real break (-> air / fluid),
        // or a replacement with a non-lock block still clears it. onStateReplaced fires AFTER
        // the new state is set, so getBlockState(pos) here is the replacement.
        if (!moved && SlabAnchorAttachment.replacementPreservesAnchor(world, pos, world.getBlockState(pos))) {
            return;
        }
        SlabAnchorAttachment.removeAnchor(world, pos);
    }
}
