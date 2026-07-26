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
        // /slabrig — the standard live-test rig builder. Dev tooling that SHIPS in every jar (present,
        // behaviour-neutral until invoked, op-gated). Server command: it authors real block state and
        // genuine anchor attachments, so it registers unconditionally here (mirrors the always-on
        // client /slabdev registration), NOT behind the dev-environment gate below.
        com.slabbed.command.SlabRigCommand.register();
        // /slabkit + /slabcheck — the rest of the live-test cockpit (same op-gated, ships-in-every-jar
        // convention as /slabrig): /slabkit fills the inventory with one representative per category,
        // /slabcheck scans for placed cells whose height has drifted from its stored placement value.
        com.slabbed.command.SlabKitCommand.register();
        com.slabbed.command.SlabCheckCommand.register();
        // Recorder break capture (TEST (3)-triage upgrade): the recorder was break-blind — it caused
        // the "data-destructive downgrade" false alarm, and Maintainer's tower-churn "jumping when I break
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
