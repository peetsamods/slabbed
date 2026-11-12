package com.slabbed.client;

import org.apache.commons.lang3.tuple.Pair;
import com.slabbed.client.model.OffsetBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.MultipartBakedModel;
import net.minecraft.client.render.model.WeightedBakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.Weighted;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SlabbedModelLoadingPluginOwnershipTest {
    private static BakedModel basic() {
        return (BakedModel) Proxy.newProxyInstance(BakedModel.class.getClassLoader(),
                new Class<?>[]{BakedModel.class}, (proxy, method, args) -> {
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getName().equals("getQuads")) return List.of();
                    return null;
                });
    }

    private static OffsetBlockStateModel owner() {
        return new OffsetBlockStateModel(basic());
    }

    private static boolean owns(BakedModel model) {
        return SlabbedModelLoadingPlugin.wrapModel(null, model, () -> KnownEmissive.class) == model;
    }

    @Test
    void directOwnerRemainsIdentical() {
        BakedModel model = owner();
        assertSame(model, SlabbedModelLoadingPlugin.wrapModel(null, model));
        assertTrue(owns(model));
    }

    @Test
    void knownSingleDelegateKeepsExistingOwner() {
        BakedModel model = new KnownEmissive(owner());
        assertSame(model, SlabbedModelLoadingPlugin.wrapModel(null, model, () -> KnownEmissive.class));
        assertSame(model, SlabbedModelLoadingPlugin.wrapModel(
                new ModelIdentifier(Identifier.of("test", "direct"), "normal"), model,
                () -> KnownEmissive.class));
    }

    @Test
    void nestedKnownDelegatesKeepExistingOwner() {
        assertTrue(owns(new KnownEmissive(new KnownEmissive(owner()))));
    }

    @Test
    void unownedKnownDelegateNeedsAnOwner() {
        assertFalse(owns(new KnownEmissive(basic())));
    }

    @Test
    void absentOptionalModWrapsResourceAndDirectTopLevelModels() {
        BakedModel resource = SlabbedModelLoadingPlugin.wrapModel(null, basic());
        BakedModel topLevel = SlabbedModelLoadingPlugin.wrapModel(
                new ModelIdentifier(Identifier.of("test", "direct"), "normal"), basic());
        assertInstanceOf(OffsetBlockStateModel.class, resource);
        assertInstanceOf(OffsetBlockStateModel.class, topLevel);
    }

    @Test
    void unknownSubclassAndUnownedModelsGainOuterOwnerWithoutReplacingDelegate() {
        for (BakedModel model : List.of(new CustomForwarding(owner()),
                new EmissiveSubclass(owner()), new KnownEmissive(basic()))) {
            var wrapped = assertInstanceOf(OffsetBlockStateModel.class,
                    SlabbedModelLoadingPlugin.wrapModel(null, model, () -> KnownEmissive.class));
            assertSame(model, wrapped.getWrappedModel());
        }
    }

    @Test
    void exclusionsDoNotResolveOptionalIntegration() {
        java.util.function.Supplier<Class<?>> unexpected = () -> { throw new AssertionError("must not resolve"); };
        BakedModel model = owner();
        assertNull(SlabbedModelLoadingPlugin.wrapModel(null, null, unexpected));
        assertSame(model, SlabbedModelLoadingPlugin.wrapModel(null, model, unexpected));
        BakedModel item = basic();
        assertSame(item, SlabbedModelLoadingPlugin.wrapModel(
                new ModelIdentifier(Identifier.of("test", "item"), ModelIdentifier.INVENTORY_VARIANT), item, unexpected));
    }

    @Test
    void nullAndInventoryAreUntouched() {
        assertNull(SlabbedModelLoadingPlugin.wrapModel(null, null));
        BakedModel model = basic();
        assertSame(model, SlabbedModelLoadingPlugin.wrapModel(
                new ModelIdentifier(Identifier.of("test", "item"), ModelIdentifier.INVENTORY_VARIANT), model));
    }

    @Test
    void nativeMultipartAndWeightedExclusionsAreRetained() {
        BakedModel child = SlabbedModelLoadingPlugin.wrapModel(null, basic());
        BakedModel multipart = new MultipartBakedModel(List.of(Pair.of(state -> true, child)));
        BakedModel weighted = new WeightedBakedModel(List.of(Weighted.of(child, 1)));
        assertSame(multipart, SlabbedModelLoadingPlugin.wrapModel(null, multipart));
        assertSame(weighted, SlabbedModelLoadingPlugin.wrapModel(null, weighted));
        assertInstanceOf(OffsetBlockStateModel.class, child);
    }

    @Test
    void nativeCompositeChildrenAreNotSearched() {
        BakedModel multipart = new MultipartBakedModel(List.of(Pair.of(state -> true, owner())));
        BakedModel weighted = new WeightedBakedModel(List.of(Weighted.of(owner(), 1)));
        assertFalse(owns(new KnownEmissive(multipart)));
        assertFalse(owns(new KnownEmissive(weighted)));
    }

    @Test
    void customForwardingAndKnownSubclassesRemainUnknown() {
        assertFalse(owns(new CustomForwarding(owner())));
        assertFalse(owns(new EmissiveSubclass(owner())));
        assertFalse(owns(new KnownEmissive(new CustomForwarding(owner()))));
    }

    @Test
    void ownerInSiblingDoesNotOwnIndependentGeometry() {
        assertFalse(owns(new SiblingGeometry(basic(), owner())));
        assertFalse(owns(new KnownEmissive(new SiblingGeometry(basic(), owner()))));
    }

    @Test
    void absentAdmissionDoesNotInspectForwarding() {
        BakedModel model = new KnownEmissive(owner());
        assertInstanceOf(OffsetBlockStateModel.class,
                SlabbedModelLoadingPlugin.wrapModel(null, model, () -> null));
    }

    @Test
    void nullDelegateIsConservativelyUnowned() {
        assertFalse(owns(new KnownEmissive(null)));
    }

    @Test
    void selfCycleTerminatesWithoutOwnership() {
        KnownEmissive cycle = new KnownEmissive(null);
        cycle.delegate(cycle);
        assertFalse(owns(cycle));
    }

    @Test
    void identityCycleTerminatesWithoutOwnership() {
        KnownEmissive a = new KnownEmissive(null);
        KnownEmissive b = new KnownEmissive(a);
        a.delegate(b);
        assertFalse(owns(a));
    }

    @Test
    void depthLimitIsConservative() {
        BakedModel model = owner();
        for (int i = 0; i < 63; i++) model = new KnownEmissive(model);
        assertTrue(owns(model));
        assertFalse(owns(new KnownEmissive(model)));
    }

    @Test
    void missingAccessorLinkageFallsBack() {
        BakedModel model = new MissingAccessor(owner());
        assertInstanceOf(OffsetBlockStateModel.class,
                SlabbedModelLoadingPlugin.wrapModel(null, model, () -> MissingAccessor.class));
    }

    @Test
    void unsupportedVersionDoesNotLoadOptionalClass() {
        AtomicBoolean loaded = new AtomicBoolean();
        ClassLoader loader = new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                loaded.set(true);
                throw new ClassNotFoundException(name);
            }
        };
        assertNull(SlabbedModelLoadingPlugin.knownEmissiveType(null, loader));
        assertNull(SlabbedModelLoadingPlugin.knownEmissiveType("3.0.0+1.21.1", loader));
        assertNull(SlabbedModelLoadingPlugin.knownEmissiveType("3.1.0+1.21", loader));
        assertFalse(loaded.get());
    }

    @Test
    void missingOptionalClassFallsBack() {
        assertNull(SlabbedModelLoadingPlugin.knownEmissiveType("3.0.0+1.21", new ClassLoader(null) {}));
    }

    // Structural stand-ins exercise ownership admission, not the optional mod's integration.
    private static class KnownEmissive extends ForwardingBakedModel {
        KnownEmissive(BakedModel model) { wrapped = model; }
        void delegate(BakedModel model) { wrapped = model; }
    }

    private static final class EmissiveSubclass extends KnownEmissive {
        EmissiveSubclass(BakedModel model) { super(model); }
    }

    private static class CustomForwarding extends ForwardingBakedModel {
        CustomForwarding(BakedModel model) { wrapped = model; }
    }

    private static final class SiblingGeometry extends CustomForwarding {
        private final BakedModel sibling;
        SiblingGeometry(BakedModel model, BakedModel sibling) {
            super(model);
            this.sibling = sibling;
        }
    }

    private static final class MissingAccessor extends ForwardingBakedModel {
        MissingAccessor(BakedModel model) { wrapped = model; }
        @Override public BakedModel getWrappedModel() { throw new NoSuchMethodError("unavailable accessor"); }
    }
}
