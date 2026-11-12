package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.client.model.ChainCeilingGeometry;
import com.slabbed.client.model.OffsetBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.MultipartBakedModel;
import net.minecraft.client.render.model.WeightedBakedModel;
import net.minecraft.client.util.ModelIdentifier;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;

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
                    (model, context) -> wrapModel(context.topLevelId(), model));
        });
    }

    static BakedModel wrapModel(ModelIdentifier topLevelId, BakedModel model) {
        return wrapModel(topLevelId, model, () -> ContinuitySupport.MODEL_TYPE);
    }

    static BakedModel wrapModel(ModelIdentifier topLevelId, BakedModel model,
                                Supplier<Class<?>> emissiveType) {
        if (model == null
                || (topLevelId != null && ModelIdentifier.INVENTORY_VARIANT.equals(topLevelId.getVariant()))
                || model instanceof OffsetBlockStateModel
                || model instanceof MultipartBakedModel
                || model instanceof WeightedBakedModel) {
            return model;
        }

        if (hasKnownOffsetOwner(model, emissiveType.get())) {
            return model;
        }
        return new OffsetBlockStateModel(model);
    }

    private static boolean hasKnownOffsetOwner(BakedModel model, Class<?> emissiveType) {
        Set<BakedModel> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int depth = 0; model != null && depth < 64 && seen.add(model); depth++) {
            if (model instanceof OffsetBlockStateModel) {
                return true;
            }
            // Only the verified single-delegate emissive model preserves this owner's geometry.
            // Arbitrary forwarding models and composite children can add independent geometry.
            if (emissiveType == null || model.getClass() != emissiveType
                    || !(model instanceof ForwardingBakedModel forwarding)) {
                return false;
            }
            try {
                model = forwarding.getWrappedModel();
            } catch (LinkageError unavailableAccessor) {
                return false;
            }
        }
        return false;
    }

    static Class<?> knownEmissiveType(String version, ClassLoader loader) {
        if (!"3.0.0+1.21".equals(version)) {
            return null;
        }
        try {
            Class<?> type = Class.forName(
                    "me.pepperbell.continuity.client.model.EmissiveBakedModel", false, loader);
            return type.getSuperclass() == ForwardingBakedModel.class ? type : null;
        } catch (ClassNotFoundException | LinkageError | SecurityException unavailableModel) {
            return null;
        }
    }

    private static final class ContinuitySupport {
        private static final Class<?> MODEL_TYPE = FabricLoader.getInstance()
                .getModContainer("continuity")
                .map(mod -> knownEmissiveType(mod.getMetadata().getVersion().getFriendlyString(),
                        SlabbedModelLoadingPlugin.class.getClassLoader()))
                .orElse(null);
    }
}
