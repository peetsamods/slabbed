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
        SlabAnchorAttachment.removeAnchor(world, pos);
    }
}
