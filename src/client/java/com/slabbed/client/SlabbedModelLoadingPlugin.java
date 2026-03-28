package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.client.model.OffsetBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.block.Blocks;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.client.render.model.BlockStateModel;

public final class SlabbedModelLoadingPlugin {
    private SlabbedModelLoadingPlugin() {
    }

    public static void init()
    {
        Slabbed.LOGGER.info("[Slabbed] ModelLoadingPlugin init: registering AfterBakeBlock modifier");
        ModelLoadingPlugin.register(plugin -> plugin
            .modifyBlockModelAfterBake()
            .register(ModelModifier.WRAP_PHASE, (model, context) -> new OffsetBlockStateModel(model)));
    }
}
