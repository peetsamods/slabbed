package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records a persistent slab-anchor when an ordinary full block is placed directly on a
 * bottom slab or lowered full-block slab chain. Server-side only;
 * {@link SlabAnchorAttachment#addAnchor} no-ops on client worlds and on positions
 * that do not satisfy the anchor qualifier.
 */
@Mixin(Block.class)
public abstract class BlockOnPlacedAnchorMixin {

    @Inject(method = "setPlacedBy", at = @At("HEAD"))
    private void slabbed$recordSlabAnchor(Level world, BlockPos pos, BlockState state,
                                          LivingEntity placer, ItemStack stack,
                                          CallbackInfo ci) {
        SlabAnchorAttachment.addAnchor(world, pos, state);
        // FREEZE-ON-PLACE (Maintainer's law): lock this placement's height so it never autonomously
        // pops afterwards — a lowered piece freezes its anchor (can't pop up when a source is
        // removed), and a flat structural piece records FROZEN_FLAT (a slab placed under/beside it
        // later can't pull it down). No-op for decorative followers and non-structural blocks.
        SlabAnchorAttachment.freezeLoweredOnPlace(world, pos, state);
        // FROZEN-DY (LAW.md restoration, Step 0): capture the exact height this placement landed at,
        // AFTER the markers above, so every later read returns it verbatim. The read short-circuit is
        // flag-gated (-Dslabbed.frozenDy), so this is a no-op store until the switch is on.
        SlabAnchorAttachment.capturePlacementDy(world, pos, state);
    }
}
