package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.client.model.ChainCeilingGeometry;
import com.slabbed.client.model.OffsetBlockStateModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.event.ModelEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SlabbedModelLoadingPlugin {
    private static final String MODEL_WRAPPER_PROOF_PROPERTY = "slabbed.neoforge.modelWrapperProof";
    private static final String FABRIC_BAKED_MODEL_INTERFACE =
            "net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel";
    private static final Map<Class<?>, Optional<Method>> VANILLA_ADAPTER_METHODS =
            new ConcurrentHashMap<>();
    private static final Set<Class<?>> FAILED_EXTERNAL_EMITTER_CHECKS = ConcurrentHashMap.newKeySet();

    private SlabbedModelLoadingPlugin() {
    }

    public static void init(IEventBus modEventBus) {
        Slabbed.LOGGER.info("[Slabbed] ModelLoadingPlugin init: registering baked model wrapper");
        modEventBus.addListener(ChainCeilingGeometry::registerAdditional);
        modEventBus.addListener(SlabbedModelLoadingPlugin::modifyBakingResult);
    }

    private static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ChainCeilingGeometry.captureBakedModel(event.getModels());
        if (!Boolean.getBoolean(MODEL_WRAPPER_PROOF_PROPERTY)) {
            event.getModels().replaceAll(SlabbedModelLoadingPlugin::wrapModel);
            return;
        }

        AtomicInteger total = new AtomicInteger();
        AtomicInteger wrapped = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        event.getModels().replaceAll((id, model) -> {
            total.incrementAndGet();
            BakedModel wrappedModel = wrapModel(id, model);
            if (wrappedModel == model) {
                skipped.incrementAndGet();
            } else {
                wrapped.incrementAndGet();
            }
            return wrappedModel;
        });
        Slabbed.LOGGER.info(
                "[MC1211_NEOFORGE_MODEL_WRAPPER_PROOF] route=runClient event=ModelEvent.ModifyBakingResult totalModels={} wrappedModels={} skippedModels={} diagnosticsOnly=true semanticsChanged=false",
                total.get(),
                wrapped.get(),
                skipped.get());
    }

    static BakedModel wrapModel(ModelResourceLocation id, BakedModel model) {
        if (model == null
                || ChainCeilingGeometry.isModelLocation(id)
                || ModelResourceLocation.INVENTORY_VARIANT.equals(id.getVariant())
                || model.isCustomRenderer()
                || (model instanceof IDynamicBakedModel && !isNamedVanillaComposite(model))
                || model instanceof BakedModelWrapper<?>
                || usesExternalQuadEmitter(model)
                || model instanceof OffsetBlockStateModel) {
            return model;
        }
        return new OffsetBlockStateModel(model);
    }

    /**
     * Named adapters for the vanilla composite bakers. The loader marks {@code WeightedBakedModel}
     * and {@code MultiPartBakedModel} as dynamic, but their quads are plain vanilla quads, and an
     * unwrapped block model has NO dy owner on the chunk-mesh path — so skipping them renders
     * unstored geometric cells at grid height while the outline lowers (the L1 absolute-WYSIWYG
     * violation; maintainer ruling, 2026-08-16). Exact class identity keeps unknown subclasses on
     * the named-adapter-required policy.
     */
    private static boolean isNamedVanillaComposite(BakedModel model) {
        return model.getClass() == WeightedBakedModel.class
                || model.getClass() == MultiPartBakedModel.class;
    }

    private static boolean usesExternalQuadEmitter(BakedModel model) {
        Optional<Method> method = VANILLA_ADAPTER_METHODS.computeIfAbsent(
                model.getClass(), SlabbedModelLoadingPlugin::findVanillaAdapterMethod);
        if (method.isEmpty()) {
            return false;
        }
        try {
            return Boolean.FALSE.equals(method.get().invoke(model));
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (FAILED_EXTERNAL_EMITTER_CHECKS.add(model.getClass())) {
                Slabbed.LOGGER.warn(
                        "Could not query the external quad-emission capability for {}; preserving renderer ownership",
                        model.getClass().getName(),
                        exception);
            }
            return true;
        }
    }

    private static Optional<Method> findVanillaAdapterMethod(Class<?> modelClass) {
        for (Class<?> current = modelClass; current != null; current = current.getSuperclass()) {
            Optional<Method> method = findVanillaAdapterInterface(current.getInterfaces());
            if (method.isPresent()) {
                return method;
            }
        }
        return Optional.empty();
    }

    private static Optional<Method> findVanillaAdapterInterface(Class<?>[] interfaces) {
        for (Class<?> candidate : interfaces) {
            if (FABRIC_BAKED_MODEL_INTERFACE.equals(candidate.getName())) {
                try {
                    Method method = candidate.getMethod("isVanillaAdapter");
                    if (method.getParameterCount() == 0 && method.getReturnType() == boolean.class) {
                        return Optional.of(method);
                    }
                } catch (NoSuchMethodException | SecurityException ignored) {
                    return Optional.empty();
                }
            }
            Optional<Method> inherited = findVanillaAdapterInterface(candidate.getInterfaces());
            if (inherited.isPresent()) {
                return inherited;
            }
        }
        return Optional.empty();
    }
}
