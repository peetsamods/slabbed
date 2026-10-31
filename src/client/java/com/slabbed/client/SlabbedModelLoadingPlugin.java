package com.slabbed.client;

import com.slabbed.client.model.ChainCeilingGeometry;
import com.slabbed.client.model.OffsetBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.resources.Identifier;

public final class SlabbedModelLoadingPlugin {
    private static final Identifier OFFSET_PHASE = Identifier.fromNamespaceAndPath("slabbed", "saved_height");

    private SlabbedModelLoadingPlugin() {
    }

    public static void init() {
        // Standalone extra models for the chain-ceiling-support extended geometry (ChainCeilingGeometry) —
        // one per chain texture (iron + the four copper weather states) so a bridged copper chain keeps
        // its own texture instead of rendering as plain iron.
        for (var variant : ChainCeilingGeometry.variants()) {
            ModelLoadingPlugin.register(plugin -> plugin.addModel(ChainCeilingGeometry.keyFor(variant),
                    SimpleUnbakedExtraModel.blockStateModel(ChainCeilingGeometry.modelIdFor(variant))));
        }
        ModelLoadingPlugin.register(plugin -> {
            var modifiers = plugin.modifyBlockModelAfterBake();
            // Replacement models can emit their own vertices without delegating to the earlier model.
            // Keep the saved-height transform outside those replacements, with one owner per model.
            modifiers.addPhaseOrdering(ModelModifier.WRAP_LAST_PHASE, OFFSET_PHASE);
            modifiers.register(OFFSET_PHASE, (model, context) -> {
                if (model instanceof FabricBlockStateModel && !(model instanceof OffsetBlockStateModel)) {
                    return new OffsetBlockStateModel((BlockStateModel) model);
                }
                return model;
            });
        });
    }
}
