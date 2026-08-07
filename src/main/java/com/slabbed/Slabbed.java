package com.slabbed;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

public class Slabbed implements ModInitializer {
    public static final String MOD_ID = "slabbed";
    public static final Logger LOGGER = LoggerFactory.getLogger(Slabbed.class);

    @Override
    public void onInitialize() {
        LOGGER.info("Slabbed initialized");
        com.slabbed.anchor.SlabAnchorAttachment.register();
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(
                com.slabbed.network.PlacementDyPredictionEnvelopePayload.TYPE,
                com.slabbed.network.PlacementDyPredictionEnvelopePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(
                com.slabbed.network.PlacementDyCorrectionPayload.TYPE,
                com.slabbed.network.PlacementDyCorrectionPayload.CODEC);
        com.slabbed.network.PlacementDyCorrectionServer.registerReceiver();
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> com.slabbed.network.PlacementDyCorrectionServer.clearPlayer(handler.player));
        // Recorder break capture (TEST (3)-triage upgrade): the recorder was break-blind — it caused
        // the "data-destructive downgrade" false alarm, and the maintainer's tower-churn "jumping when I break
        // things" report left ZERO rows. Observation only: the handler must ALWAYS return true (never
        // cancel the break), and gates on the recorder flag in one volatile read.
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
                (world, player, pos, state, blockEntity) -> {
                    if (com.slabbed.util.SlabbedDiagnosticsBridge.enabled()) {
                        com.slabbed.util.SlabbedDiagnosticsBridge.recordBreakEvent(world, pos, state,
                                player == null ? "none" : player.getName().getString());
                        // Phase 1.5: breaks reshuffle neighbor dys — classify the neighborhood's
                        // ensemble coherence (TEST (6): 40/94 breaks touched lowered geometry with
                        // zero measurement).
                        com.slabbed.util.SlabbedDiagnosticsBridge.armBreakNeighborhood(
                                world, pos, world.getGameTime());
                    }
                    return true;
                });
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            initDevFeatures();
        }
    }

    private static void initDevFeatures() {
        registerDevHook("com.slabbed.dev.SlabbedDevCommands", "register");
        registerDevHook("com.slabbed.dev.SlabbedLab", "register");
        // The /slabrig live-test rig family is DEV-ONLY (the maintainer's release-allowlist ruling: the
        // ships-in-every-jar standing rule covers /slabdy and /slabdev, NOT the rig). Registration
        // is gated here so none of the rig's unconditional hooks (server tick, lifecycle,
        // entity load/unload, the ALLOW_LOAD spawn veto, reconstruction disk reads and world-save
        // writes) can run in a release launch; the classes are also excluded from the release
        // artifacts (see releaseArtifactExclusions in build.gradle). runGameTest is a dev
        // environment, so the rig gametests still exercise the real registration path —
        // SlabRigCommandSmokeTest executes /slabrig through the PRODUCTION dispatcher and fails
        // if this gate ever stops firing there. Same reflective-hook pattern as the dev hooks
        // above (proven structure on the 1.21.11 line): the release Slabbed.class keeps no hard
        // link to classes that are absent from the release jar.
        registerDevHook("com.slabbed.command.SlabRigCommand", "register");
        registerDevHook("com.slabbed.command.SlabKitCommand", "register");
        registerDevHook("com.slabbed.command.SlabCheckCommand", "register");
    }

    private static void registerDevHook(String className, String methodName) {
        try {
            Class<?> hookClass = Class.forName(className);
            hookClass.getMethod(methodName).invoke(null);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Dev hook {} is unavailable in this environment", className);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            LOGGER.warn("Failed to initialize dev hook {}", className, e);
        }
    }
}
