package com.slabbed.client;

import com.slabbed.Slabbed;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.InvocationTargetException;

public final class SlabbedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // FIRST, before anything can resolve a height: teach the common resolver to recognise the
        // chunk-meshing view. SlabSupport must not link RenderSectionRegion itself (it is
        // client-only), so the instanceof is supplied from here. Until this runs, the detector
        // answers false and every out-of-bounds read rethrows — which is the safe default, but it
        // means registration must never be moved later or made conditional.
        com.slabbed.util.SlabSupport.registerChunkRendererRegionDetector(
                view -> view instanceof net.minecraft.client.renderer.chunk.RenderSectionRegion);
        PlacementDyPredictionJournal.init();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.slabbed.network.PlacementDyCorrectionPayload.TYPE,
                (payload, context) -> {
                    com.slabbed.network.PlacementDyPredictionBridge.traceCorrectionWire(
                            "RECEIVE", payload.signature());
                    PlacementDyPredictionJournal.onCorrection(context.client().level, payload);
                });
        initRuntimeDiagnostics("logInspectSessionStart", "inspect diagnostics",
                Boolean.getBoolean("slabbed.inspect") || Boolean.getBoolean("slabbed.b2.live.trace"));
        SlabbedModelLoadingPlugin.init();
        SlabAnchorClientSync.init();
        initRuntimeDiagnostics("initBsFbLiveTraceClient", "BS/FB live trace client",
                Boolean.getBoolean("slabbed.bsfb.live.trace"));
        initDyFingerprintDump();
        BetaNoticeClient.init();
        // Ships in EVERY jar, default off — the standing debug-tooling rule, under the maintainer's
        // 2026-08-07 reading that the command must be INVOCABLE on a shipped jar rather than merely
        // present as bytes. Unconditional on purpose: no isDevelopmentEnvironment() guard and no
        // reflective hook, because either one is exactly how /slabdy and /slabdev came to be
        // unreachable on this line. Cost is two Brigadier trees built once at client init; the
        // commands install no tick hook, no HUD element and no world-save writer, and touch nothing
        // until someone types them. The /slabrig family does NOT follow them out of the gate — it
        // stays dev-gated in Slabbed.initDevFeatures and excluded from the release artifacts.
        SlabbedDebugCommands.register();
    }

    private static void initDyFingerprintDump() {
        // Tier-2 client dy-fingerprint dump (RELEASE_SANITY_CHECKLIST §3); dev-only, excluded from the jar.
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        invokeStaticInit("com.slabbed.client.DyFingerprintDump", "dy fingerprint dump");
    }

    private static void initRuntimeDiagnostics(String methodName, String label, boolean enabled) {
        if (!enabled) {
            return;
        }
        invokeStaticNoArg("com.slabbed.util.RuntimeDiagnostics", methodName, label);
    }

    // The ScreenshotCaptureService and GapFillerOverlay reflective hooks were removed by the
    // release-allowlist ruling: both target classes are compile-excluded from the client source set
    // on this line (build.gradle sourceSets.client excludes), so Class.forName could NEVER succeed —
    // the hooks were permanently dangling and only logged a warning. GapFillerOverlay's removal also
    // lets SlabbedClientFlags (whose only member fed that hook) leave the release artifacts without
    // stranding a getstatic on a missing class.

    private static void invokeStaticInit(String className, String label) {
        invokeStaticNoArg(className, "init", label);
    }

    private static void invokeStaticNoArg(String className, String methodName, String label) {
        try {
            Class<?> hookClass = Class.forName(className);
            hookClass.getMethod(methodName).invoke(null);
        } catch (ClassNotFoundException e) {
            Slabbed.LOGGER.warn("{} is unavailable in this environment", label);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            Slabbed.LOGGER.warn("Failed to initialize {}", label, e);
        }
    }
}
