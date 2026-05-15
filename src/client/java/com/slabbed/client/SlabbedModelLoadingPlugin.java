package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.client.model.OffsetBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.render.model.BakedModel;

public final class SlabbedModelLoadingPlugin {
    private SlabbedModelLoadingPlugin() {
    }

    public static void init() {
        Slabbed.LOGGER.info("[Slabbed] ModelLoadingPlugin init: registering baked model wrapper");
        ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE, (model, context) -> {
            if (model == null || model instanceof OffsetBlockStateModel) {
                return model;
            }

            BakedModel bakedModel = model;
            return new OffsetBlockStateModel(bakedModel);
        }));
    }
}
