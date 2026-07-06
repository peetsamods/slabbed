package com.slabbed.client;

import com.slabbed.client.model.ChainCeilingGeometry;
import com.slabbed.client.model.OffsetBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;

public final class SlabbedModelLoadingPlugin {
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
        ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register(ModelModifier.WRAP_PHASE, (model, context) -> {
            if (model instanceof FabricBlockStateModel) {
                return new OffsetBlockStateModel((BlockStateModel) model);
            }
            return model;
        }));
    }
}
