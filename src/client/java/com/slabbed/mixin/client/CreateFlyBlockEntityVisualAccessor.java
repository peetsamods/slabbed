package com.slabbed.mixin.client;

import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Supplies the source block entity for optional Create Fly visuals. */
@Pseudo
@Mixin(targets = "com.zurrtum.create.client.flywheel.lib.visual.AbstractBlockEntityVisual", remap = false)
public interface CreateFlyBlockEntityVisualAccessor {
    @Accessor("blockEntity") BlockEntity slabbed$getBlockEntity();
}
