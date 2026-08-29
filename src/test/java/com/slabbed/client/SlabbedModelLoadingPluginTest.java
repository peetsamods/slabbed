package com.slabbed.client;

import com.slabbed.client.model.OffsetBlockStateModel;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
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

    @Test
    void preservesUnknownWorldModelIdentityUntilAnAdapterOwnsIt() {
        ModelResourceLocation worldId = ModelResourceLocation.vanilla("stone", "facing=north");
        BakedModel customRenderer = new StubBakedModel(true);
        BakedModel dynamicComposite = new DynamicStubBakedModel();
        BakedModel foreignWrapper = new ForeignWrapper(new StubBakedModel(false));
        BakedModel externalEmitter = new ExternalEmitterModel(false);
        BakedModel vanillaAdapter = new ExternalEmitterModel(true);
        BakedModel failingExternalEmitter = new FailingExternalEmitterModel();

        assertSame(customRenderer, SlabbedModelLoadingPlugin.wrapModel(worldId, customRenderer),
                "custom-rendered world models must keep their exact runtime identity");
        assertSame(dynamicComposite, SlabbedModelLoadingPlugin.wrapModel(worldId, dynamicComposite),
                "unknown dynamic/composite models require a named adapter before replacement");
        assertInstanceOf(OffsetBlockStateModel.class,
                SlabbedModelLoadingPlugin.wrapModel(worldId, weightedComposite()),
                "the vanilla weighted composite is a named adapter and must gain a dy owner");
        assertInstanceOf(OffsetBlockStateModel.class,
                SlabbedModelLoadingPlugin.wrapModel(worldId, multiPartComposite()),
                "the vanilla multipart composite is a named adapter and must gain a dy owner");
        BakedModel weightedSubclass = new ForeignWeightedModel();
        assertSame(weightedSubclass, SlabbedModelLoadingPlugin.wrapModel(worldId, weightedSubclass),
                "a subclassed weighted composite is unknown and keeps its renderer-owned identity");
        assertSame(foreignWrapper, SlabbedModelLoadingPlugin.wrapModel(worldId, foreignWrapper),
                "an unknown model wrapper must not be wrapped a second time");
        assertSame(externalEmitter, SlabbedModelLoadingPlugin.wrapModel(worldId, externalEmitter),
                "a non-vanilla quad emitter must retain its renderer-owned identity");
        assertInstanceOf(OffsetBlockStateModel.class,
                SlabbedModelLoadingPlugin.wrapModel(worldId, vanillaAdapter),
                "a declared vanilla adapter remains eligible for Slabbed offsets");
        assertSame(failingExternalEmitter,
                SlabbedModelLoadingPlugin.wrapModel(worldId, failingExternalEmitter),
                "a failed external-emitter query must preserve renderer ownership");
    }

    private static WeightedBakedModel weightedComposite() {
        return new WeightedBakedModel(List.of(WeightedEntry.wrap(new StubBakedModel(false), 1)));
    }

    private static MultiPartBakedModel multiPartComposite() {
        Predicate<BlockState> always = state -> true;
        return new MultiPartBakedModel(List.of(
                org.apache.commons.lang3.tuple.Pair.of(always, new StubBakedModel(false))));
    }

    private static final class ForeignWeightedModel extends WeightedBakedModel {
        private ForeignWeightedModel() {
            super(List.of(WeightedEntry.wrap(new StubBakedModel(false), 1)));
        }
    }

    private static class StubBakedModel implements BakedModel {
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

    private static final class DynamicStubBakedModel extends StubBakedModel implements IDynamicBakedModel {
        private DynamicStubBakedModel() {
            super(false);
        }

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random,
                ModelData modelData,
                RenderType renderType
        ) {
            return getQuads(state, direction, random);
        }
    }

    private static final class ForeignWrapper extends BakedModelWrapper<BakedModel> {
        private ForeignWrapper(BakedModel originalModel) {
            super(originalModel);
        }
    }

    static final class ExternalEmitterModel extends StubBakedModel implements FabricBakedModel {
        private final boolean vanillaAdapter;

        private ExternalEmitterModel(boolean vanillaAdapter) {
            super(false);
            this.vanillaAdapter = vanillaAdapter;
        }

        public boolean isVanillaAdapter() {
            return vanillaAdapter;
        }
    }

    static final class FailingExternalEmitterModel extends StubBakedModel implements FabricBakedModel {
        private FailingExternalEmitterModel() {
            super(false);
        }

        public boolean isVanillaAdapter() {
            throw new IllegalStateException("capability_query_failed");
        }
    }
}
