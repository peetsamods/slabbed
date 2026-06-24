package com.slabbed.client.model;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.List;
import java.util.Map;

/**
 * NeoForge-native alternate baked geometry for a vertical chain under a slab ceiling.
 */
public final class ChainCeilingGeometry {
    public static final ResourceLocation MODEL_ID = ResourceLocation.fromNamespaceAndPath(
            Slabbed.MOD_ID,
            "block/chain_ceiling_support");
    public static final ModelResourceLocation MODEL_LOCATION = ModelResourceLocation.standalone(MODEL_ID);

    private static volatile BakedModel model;

    private ChainCeilingGeometry() {
    }

    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        event.register(MODEL_LOCATION);
    }

    public static void captureBakedModel(Map<ModelResourceLocation, BakedModel> models) {
        model = models.get(MODEL_LOCATION);
    }

    public static boolean isModelLocation(ModelResourceLocation location) {
        return MODEL_LOCATION.equals(location);
    }

    public static boolean usesAlternateGeometry(BlockGetter world, BlockPos pos, BlockState state) {
        try {
            return SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(world, pos, state);
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return false;
        }
    }

    public static List<BakedQuad> quadsIfPresent(
            BlockGetter world,
            BlockPos pos,
            BlockState state,
            Direction side,
            RandomSource random,
            RenderType renderType
    ) {
        if (!usesAlternateGeometry(world, pos, state)) {
            return null;
        }
        BakedModel alternate = model;
        if (alternate == null) {
            return null;
        }
        return alternate.getQuads(state, side, random, ModelData.EMPTY, renderType);
    }
}
