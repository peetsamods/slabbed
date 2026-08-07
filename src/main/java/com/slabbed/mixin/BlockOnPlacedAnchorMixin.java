package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records a persistent slab-anchor when an ordinary full block is placed directly on a
 * bottom slab. Server-side only; {@link SlabAnchorAttachment#addAnchor} no-ops on client
 * worlds and on positions that do not satisfy {@code qualifiesForDirectAnchor}.
 */
@Mixin(Block.class)
public abstract class BlockOnPlacedAnchorMixin {

    @Inject(method = "onPlaced", at = @At("HEAD"))
    private void slabbed$recordSlabAnchor(World world, BlockPos pos, BlockState state,
                                          LivingEntity placer, ItemStack stack,
                                          CallbackInfo ci) {
        SlabAnchorAttachment.addAnchor(world, pos, state);
        // FREEZE-ON-PLACE (LAW 1 (the placement law)): lock this placement's height so it never autonomously
        // pops afterwards — a flat structural piece records FROZEN_FLAT (a slab placed
        // under/beside it later can't pull it down); lowered placements are locked by the
        // qualifier-lane anchors above. No-op for decorative followers and non-structural blocks.
        SlabAnchorAttachment.freezeLoweredOnPlace(world, pos, state);
    }
}
