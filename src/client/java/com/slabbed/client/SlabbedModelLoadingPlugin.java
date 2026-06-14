package com.slabbed.client;

import com.slabbed.client.model.OffsetBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.render.model.BlockStateModel;

public final class SlabbedModelLoadingPlugin {
    private SlabbedModelLoadingPlugin() {
    }

    public static void init() {
        ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register(ModelModifier.WRAP_PHASE, (model, context) -> {
            if (model instanceof FabricBlockStateModel) {
                // Terrain Slabs wraps the blocks it positions itself (vegetation — short grass, fern,
                // tall grass, …) in its own net.countered.terrainslabs.model.SlabOffsetModel BEFORE
                // Slabbed's WRAP_PHASE. Wrapping that again applies the lowering dy TWICE (TS offsets
                // the model + Slabbed's YOffsetEmitter offsets it again), so the lowered tuft sinks to
                // 2× and disappears into the block below. Let Terrain Slabs own the MODEL offset for
                // those blocks; Slabbed still drives the matching outline/raycast via getYOffset.
                Class<?> modelClass = model.getClass();
                boolean terrainSlabsOwnsOffset = "SlabOffsetModel".equals(modelClass.getSimpleName())
                        && modelClass.getName().contains("terrainslabs");
                if (terrainSlabsOwnsOffset) {
                    return model;
                }
                return new OffsetBlockStateModel((BlockStateModel) model);
            }
            return model;
        }));
    }
}
