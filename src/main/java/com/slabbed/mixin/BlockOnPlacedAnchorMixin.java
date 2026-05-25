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
    }
}
