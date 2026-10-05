package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.client.model.ChainCeilingGeometry;
import com.slabbed.client.model.OffsetBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.MultipartBakedModel;
import net.minecraft.client.render.model.WeightedBakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;

public final class SlabbedModelLoadingPlugin {
    private SlabbedModelLoadingPlugin() {
    }

    public static void init() {
        Slabbed.LOGGER.info("[Slabbed] ModelLoadingPlugin init: registering baked model wrapper");
        ModelLoadingPlugin.register(plugin -> {
            // Force-load and bake the elongated chain model used as alternate geometry for a
            // vertical chain hanging directly under a slab ceiling support. Retrieved at render
            // time via BakedModelManager#getModel(Identifier) (Fabric-injected overload).
            plugin.addModels(ChainCeilingGeometry.MODEL_ID);

            plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE,
                    (model, context) -> wrapModel(context.id(), model));
        });
    }

    static BakedModel wrapModel(Identifier topLevelId, BakedModel model) {
        // 1.20.1: the context hands back a plain Identifier that is a ModelIdentifier for block-state
        // variants; the inventory variant is the literal "inventory" there.
        if (model == null
                || (topLevelId instanceof ModelIdentifier variantId && "inventory".equals(variantId.getVariant()))
                || model instanceof OffsetBlockStateModel
                || model instanceof MultipartBakedModel
                || model instanceof WeightedBakedModel) {
            return model;
        }

        return new OffsetBlockStateModel(model);
    }
}
