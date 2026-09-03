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
        SlabAnchorAttachment.removeAnchor(world, pos);
    }
}
