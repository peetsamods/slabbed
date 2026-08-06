package com.slabbed.client;

import com.slabbed.client.model.OffsetBlockStateModel;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

final class SlabbedModelLoadingPluginTest {
    @Test
    void preservesInventoryModelIdentityWhileWrappingOrdinaryBlockModels() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("compat_test", "foreign_item");
        BakedModel customRenderer = new StubBakedModel(true);
        BakedModel ordinaryItemModel = new StubBakedModel(false);
        BakedModel ordinaryBlockModel = new StubBakedModel(false);

        BakedModel preservedCustomItem = SlabbedModelLoadingPlugin.wrapModel(
                ModelResourceLocation.inventory(id), customRenderer);
        BakedModel preservedOrdinaryItem = SlabbedModelLoadingPlugin.wrapModel(
                ModelResourceLocation.inventory(id), ordinaryItemModel);
        BakedModel wrapped = SlabbedModelLoadingPlugin.wrapModel(
                ModelResourceLocation.vanilla("stone", "facing=north"), ordinaryBlockModel);

        assertSame(customRenderer, preservedCustomItem,
                "custom-rendered models must retain their exact runtime identity");
        assertSame(ordinaryItemModel, preservedOrdinaryItem,
                "ordinary inventory models are not block-state render targets");
        assertInstanceOf(OffsetBlockStateModel.class, wrapped,
                "ordinary block-state models must remain eligible for Slabbed offsets");
    }

    private static final class StubBakedModel implements BakedModel {
        private final boolean customRenderer;

        private StubBakedModel(boolean customRenderer) {
            this.customRenderer = customRenderer;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction direction, RandomSource random) {
            return List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean usesBlockLight() {
            return false;
        }

        @Override
        public boolean isCustomRenderer() {
            return customRenderer;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return null;
        }

        @Override
        public ItemOverrides getOverrides() {
            return null;
        }
    }
}
