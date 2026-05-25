package com.slabbed.mixin.client;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Placeholder retained during 26.1.2 port after removing temporary model-path probes.
 * Model dy authority is SlabbedModelLoadingPlugin -> OffsetBlockStateModel.
 */
@Mixin(ModelBlockRenderer.class)
public class BlockModelDyTranslateMixin {
}
