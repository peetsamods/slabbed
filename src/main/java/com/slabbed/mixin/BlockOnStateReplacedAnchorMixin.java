package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
    private void slabbed$clearSlabAnchor(BlockState oldState, World world, BlockPos pos,
                                         BlockState newState, boolean moved, CallbackInfo ci) {
        SlabAnchorAttachment.removeAnchor(world, pos);
    }
}
