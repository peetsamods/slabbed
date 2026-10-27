package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.slabbed.client.model.OffsetBlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps replacement and appended pack models in the same saved-height frame as block models. */
@Pseudo
@Mixin(targets = "com.supermartijn642.fusion.model.modifiers.block.BlockModelModifierReloadListener$1", remap = false)
public abstract class FusionBlockModelModifierMixin {
    // Fusion bakes modifier models after the ordinary Fabric block-model wrapping phase.
    // Wrap each modifier leaf, preserving Fusion's outer dispatch and random-offset handling.
    @ModifyReturnValue(
            method = "apply(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;",
            at = @At("RETURN"),
            require = 1)
    private BlockStateModel slabbed$offsetModifierModel(BlockStateModel model) {
        return model == null || model instanceof OffsetBlockStateModel ? model : new OffsetBlockStateModel(model);
    }
}
