package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A retracting piston head leaves no stored height behind in its vacated cell.
 *
 * <p>The extended head inherits its base's placed height (maintainer ruling, 2026-09-06), so the
 * head cell carries a fact while the piston is extended. Retraction removes the head with an
 * ordinary air write, which normally reaches the base-class removal hook that
 * {@code BlockOnStateReplacedAnchorMixin} clears the height from — but {@code PistonHeadBlock}
 * OVERRIDES that hook without calling the base version, so for a head that clear never runs and the
 * empty cell would keep the height for whatever lands there next.
 *
 * <p>Same rule as the base-class hook, applied at the override: a real departure clears; an in-place
 * hand-off to a lock-eligible occupant keeps. Do not fold this into the base-class mixin — a HEAD
 * injection on {@code BlockBehaviour} cannot see a call that never reaches {@code BlockBehaviour}.
 */
@Mixin(PistonHeadBlock.class)
public abstract class PistonHeadRemovalAnchorMixin {

    @Inject(method = "affectNeighborsAfterRemoval", at = @At("HEAD"))
    private void slabbed$clearHeadAnchor(BlockState oldState, ServerLevel world, BlockPos pos,
                                         boolean moved, CallbackInfo ci) {
        if (!moved && SlabAnchorAttachment.replacementPreservesAnchor(
                world, pos, oldState, world.getBlockState(pos))) {
            return;
        }
        SlabAnchorAttachment.removeAnchor(world, pos);
    }
}
