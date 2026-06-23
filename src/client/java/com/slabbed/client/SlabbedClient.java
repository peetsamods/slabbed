package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.util.RuntimeDiagnostics;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.InvocationTargetException;

public final class SlabbedClient {
    private SlabbedClient() {
    }

    public static void init(IEventBus modEventBus) {
        RuntimeDiagnostics.logInspectSessionStart();
        SlabbedModelLoadingPlugin.init(modEventBus);
        SlabAnchorClientSync.init(NeoForge.EVENT_BUS);
        NeoForgeClientProofCanary.init(NeoForge.EVENT_BUS);
        NeoForgeClientWorldProof.init(NeoForge.EVENT_BUS);
        RuntimeDiagnostics.initBsFbLiveTraceClient();
    }

    private static void invokeStaticInit(String className, String label) {
        try {
            Class<?> hookClass = Class.forName(className);
            hookClass.getMethod("init").invoke(null);
        } catch (ClassNotFoundException e) {
            Slabbed.LOGGER.warn("{} is unavailable in this environment", label);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            Slabbed.LOGGER.warn("Failed to initialize {}", label, e);
        }
    }
}
