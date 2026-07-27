package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.client.model.OffsetBlockStateModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.concurrent.atomic.AtomicInteger;

public final class SlabbedModelLoadingPlugin {
    private static final String MODEL_WRAPPER_PROOF_PROPERTY = "slabbed.forge.modelWrapperProof";

    private SlabbedModelLoadingPlugin() {
    }

    public static void init(IEventBus modEventBus) {
        Slabbed.LOGGER.info("[Slabbed] ModelLoadingPlugin init: registering baked model wrapper");
        modEventBus.addListener(SlabbedModelLoadingPlugin::modifyBakingResult);
    }

    private static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
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
                "[MC1201_FORGE_MODEL_WRAPPER_PROOF] route=runClient event=ModelEvent.ModifyBakingResult totalModels={} wrappedModels={} skippedModels={} diagnosticsOnly=true semanticsChanged=false",
                total.get(),
                wrapped.get(),
                skipped.get());
    }

    private static BakedModel wrapModel(ResourceLocation id, BakedModel model) {
        if (model == null || model instanceof OffsetBlockStateModel) {
            return model;
        }
        return new OffsetBlockStateModel(model);
    }
}
