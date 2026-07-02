package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.client.model.ChainCeilingGeometry;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.MultipartBakedModel;
import net.minecraft.client.render.model.WeightedBakedModel;

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

            plugin.modifyModelAfterBake().register(ModelModifier.WRAP_PHASE, (model, context) -> {
                if (model == null || model instanceof OffsetBlockStateModel || model instanceof MultipartBakedModel || model instanceof WeightedBakedModel) {
                    return model;
                }

                // Terrain Slabs wraps the blocks it positions itself (vegetation and other
                // decorations it lowers onto its slabs) in its own offset model BEFORE Slabbed's
                // WRAP_PHASE. Wrapping that again applies the lowering dy TWICE (TS offsets the
                // model + Slabbed's YOffsetEmitter offsets it again), so the lowered object sinks
                // to 2x and disappears into the block below. Let Terrain Slabs own the offset for
                // the models it already wrapped — Slabbed defers entirely for those objects (see
                // CompatHooks.terrainSlabsHandlesObjectOffset in SlabSupport.getYOffset).
                // Ownership probe: TS's wrapper class, not one of ours. Best available signal at
                // bake time — the model object carries no blockstate/property role to key on.
                if (TerrainSlabsCompat.isLoaded() && slabbed$terrainSlabsOwnsOffsetModel(model)) {
                    return model;
                }

                BakedModel bakedModel = model;
                return new OffsetBlockStateModel(bakedModel);
            });
        });
    }

    /**
     * True when this baked model is Terrain Slabs' own offset-model wrapper (its
     * {@code SlabOffsetModel}, package {@code net.countered.terrainslabs...}) — i.e. TS already
     * owns the vertical offset for this model and Slabbed must not wrap it a second time.
     * Class-shape probe with the known caveat that a baked model exposes no better capability
     * signal; both TS namespaces are matched (dual mod-id lesson).
     */
    private static boolean slabbed$terrainSlabsOwnsOffsetModel(BakedModel model) {
        Class<?> modelClass = model.getClass();
        String className = modelClass.getName();
        return "SlabOffsetModel".equals(modelClass.getSimpleName())
                && (className.contains("terrainslabs") || className.contains("terrain_slabs"));
    }
}
