package com.slabbed.client;

import com.slabbed.client.model.OffsetBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.block.Block;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.block.BlockState;

public final class SlabbedModelLoadingPlugin {
    private SlabbedModelLoadingPlugin() {
    }

    public static void init() {
        ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register(ModelModifier.WRAP_PHASE, (model, context) -> {
            BlockState state = context.state();
            if (state == null) {
                return model;
            }

            Block block = state.getBlock();
            if (!(block instanceof TorchBlock || block instanceof WallTorchBlock)) {
                return model;
            }

            if (model instanceof FabricBlockStateModel) {
                return new OffsetBlockStateModel((BlockStateModel) model);
            }

            return model;
        }));
    }
}
