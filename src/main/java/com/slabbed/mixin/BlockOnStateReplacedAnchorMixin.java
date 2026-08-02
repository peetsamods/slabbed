package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabbedDiagnosticsBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears ALL placement truth at {@code pos} — the anchor-family markers, the carrier marker, and
 * the stored placement dy — when the block there is GENUINELY removed, and deliberately preserves
 * it across in-place replacements that keep the block lock-eligible.
 *
 * <p>On 1.20.1 this hook fires for EVERY server-side state change at {@code pos}, including
 * property-only updates: {@code LevelChunk.setBlockState}'s only early-out is reference identity,
 * then {@code onRemove} dispatches unconditionally, and none of the common blocks override it
 * (Phase I audit, disassembled from the locked toolchain jar). An earlier revision of this class
 * claimed property-only updates never arrive here — that was the donor runtime's behaviour, not
 * this one's, and the unconditional clear it justified erased anchors whenever a lamp lit, snow
 * settled on grass, or leaves ticked. {@link SlabAnchorAttachment#replacementPreservesAnchor}
 * now decides: same-block property changes and lock-eligible kind swaps (grass→dirt,
 * log→stripped, copper oxidation, pot swaps, connecting structural) preserve; air, fluid, and
 * piston moves clear.
 *
 * <p>This hook fires on the OLD state at {@code pos} — it does NOT fire when a neighbour like the
 * supporting slab below is broken, so placement truth survives neighbour edits (the R2 law).
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockOnStateReplacedAnchorMixin {

    @Inject(method = "onRemove", at = @At("HEAD"))
    private void slabbed$clearSlabAnchor(BlockState oldState, Level world, BlockPos pos,
                                         BlockState newState, boolean moved, CallbackInfo ci) {
        boolean preserved = !moved
                && SlabAnchorAttachment.replacementPreservesAnchor(world, pos, oldState, newState);
        if (!preserved) {
            SlabAnchorAttachment.clearPlacementTruth(world, pos);
        }
        if (SlabbedDiagnosticsBridge.isRecorderEnabled() && !world.isClientSide()) {
            SlabbedDiagnosticsBridge.log("remove", "pos=" + pos.toShortString()
                    + " oldState=" + oldState
                    + " newState=" + newState
                    + " moved=" + moved
                    + " preserved=" + preserved);
        }
    }
}
