package com.slabbed.client.model;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChainBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.BlockView;

import java.util.List;

public final class ChainCeilingGeometry {
    public static final Identifier MODEL_ID = Identifier.of(Slabbed.MOD_ID, "block/chain_ceiling_support");
    public static final ExtraModelKey<BlockStateModel> KEY = ExtraModelKey.create(() -> "slabbed:chain_ceiling_support");

    private static volatile BlockStateModel model;
    private static volatile List<BlockModelPart> parts;

    private ChainCeilingGeometry() {
    }

    public static boolean usesAlternateGeometry(BlockView world, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof ChainBlock
                && state.contains(Properties.AXIS)
                && state.get(Properties.AXIS) == Direction.Axis.Y
                && SlabSupport.isTopSlab(world.getBlockState(pos.up()));
    }

    public static boolean applyIfPresent(List<BlockModelPart> parts, BlockView world, BlockPos pos, BlockState state) {
        if (!usesAlternateGeometry(world, pos, state)) {
            return false;
        }
        List<BlockModelPart> alternate = alternateParts();
        if (alternate == null || alternate.isEmpty()) {
            return false;
        }
        try {
            parts.clear();
            parts.addAll(alternate);
            return true;
        } catch (UnsupportedOperationException ex) {
            return false;
        }
    }

    public static boolean emitIfPresent(FabricBlockStateModel fallback, net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter emitter,
                                        BlockRenderView world, BlockPos pos, BlockState state, Random random,
                                        java.util.function.Predicate<Direction> cullTest) {
        if (!usesAlternateGeometry(world, pos, state)) {
            return false;
        }
        FabricBlockStateModel alternate = alternateModel();
        if (alternate == null) {
            return false;
        }
        alternate.emitQuads(emitter, world, pos, state, random, cullTest);
        return true;
    }

    private static List<BlockModelPart> alternateParts() {
        List<BlockModelPart> cached = parts;
        if (cached != null) {
            return cached;
        }
        BlockStateModel alternate = alternateModelRaw();
        if (alternate == null) {
            return null;
        }
        List<BlockModelPart> built = List.copyOf(alternate.getParts(Random.create()));
        parts = built;
        return built;
    }

    private static FabricBlockStateModel alternateModel() {
        BlockStateModel alternate = alternateModelRaw();
        if (alternate instanceof FabricBlockStateModel fabric) {
            return fabric;
        }
        return null;
    }

    private static BlockStateModel alternateModelRaw() {
        BlockStateModel cached = model;
        if (cached != null) {
            return cached;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getBakedModelManager() == null) {
            return null;
        }
        BlockStateModel baked = ((FabricBakedModelManager) client.getBakedModelManager()).getModel(KEY);
        if (baked != null) {
            model = baked;
        }
        return baked;
    }
}
