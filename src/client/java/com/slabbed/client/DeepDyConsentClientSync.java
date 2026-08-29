package com.slabbed.client;

import com.slabbed.anchor.DeepDyConsentAttachment;
import com.slabbed.util.SlabSupport;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Keeps the client deep-resolution cache scoped to one server connection. */
public final class DeepDyConsentClientSync {
    private static Consumer<Boolean> proofStateObserver = enabled -> { };
    private static Consumer<Boolean> proofRefreshScheduledObserver = enabled -> { };
    private static Consumer<Boolean> proofRefreshExecutedObserver = enabled -> { };
    private static IntConsumer proofResetObserver = generation -> { };
    private static int connectionGeneration;
    private static boolean initialized;

    private DeepDyConsentClientSync() {
    }

    public static void init(IEventBus eventBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        DeepDyConsentAttachment.installClientStateObserver(
                DeepDyConsentClientSync::onSynchronizedState);
        eventBus.addListener(DeepDyConsentClientSync::onLoggingOut);
    }

    private static void onSynchronizedState(boolean enabled) {
        SlabSupport.armDeepAlphabet(enabled);
        proofStateObserver.accept(enabled);
        Minecraft minecraft = Minecraft.getInstance();
        proofRefreshScheduledObserver.accept(enabled);
        minecraft.tell(() -> {
            if (minecraft.level != null && minecraft.levelRenderer != null) {
                minecraft.levelRenderer.allChanged();
                proofRefreshExecutedObserver.accept(enabled);
            }
        });
    }

    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SlabSupport.armDeepAlphabet(false);
        connectionGeneration++;
        proofResetObserver.accept(connectionGeneration);
    }

    static void installProofObservers(
            Consumer<Boolean> stateObserver,
            Consumer<Boolean> refreshScheduledObserver,
            Consumer<Boolean> refreshExecutedObserver,
            IntConsumer resetObserver
    ) {
        proofStateObserver = stateObserver;
        proofRefreshScheduledObserver = refreshScheduledObserver;
        proofRefreshExecutedObserver = refreshExecutedObserver;
        proofResetObserver = resetObserver;
    }

    static int connectionGeneration() {
        return connectionGeneration;
    }
}
