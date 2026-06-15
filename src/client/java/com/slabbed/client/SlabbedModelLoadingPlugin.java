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
        // Standalone extra model for the chain-ceiling-support extended geometry (ChainCeilingGeometry).
        ModelLoadingPlugin.register(plugin -> plugin.addModel(ChainCeilingGeometry.KEY,
                SimpleUnbakedExtraModel.blockStateModel(ChainCeilingGeometry.MODEL_ID)));
        ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register(ModelModifier.WRAP_PHASE, (model, context) -> {
            if (model instanceof FabricBlockStateModel) {
                return new OffsetBlockStateModel((BlockStateModel) model);
            }
            return model;
        }));
    }
}
