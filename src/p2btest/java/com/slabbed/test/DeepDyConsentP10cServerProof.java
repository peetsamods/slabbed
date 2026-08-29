package com.slabbed.test;

import com.slabbed.anchor.DeepDyConsentAttachment;
import com.slabbed.anchor.DeepDyConsentAttachment.GrantResult;
import com.slabbed.anchor.DeepDyConsentAttachment.State;
import com.slabbed.util.SlabSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Drives the dedicated-server halves of the isolated deep-consent lifecycle proof. */
public final class DeepDyConsentP10cServerProof {
    public static final String PHASE_PROPERTY = "slabbed.p10c.phase";
    public static final String OFF_WORLD = "p10c-off";
    public static final String ENABLED_COPY_WORLD = "p10c-enabled-copy";
    private static boolean registered;
    private static boolean stopPending;
    private static String phase;

    private DeepDyConsentP10cServerProof() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        if (SlabSupport.DEEP_DY_ALPHABET) {
            throw new IllegalStateException("P10C proof requires the deep override to be off");
        }
        phase = System.getProperty(PHASE_PROPERTY);
        if (!"off-writer".equals(phase)
                && !"enabled-writer".equals(phase)
                && !"client".equals(phase)) {
            throw new IllegalStateException("Unknown P10C proof phase");
        }
        registered = true;
        if (!"client".equals(phase)) {
            NeoForge.EVENT_BUS.addListener(DeepDyConsentP10cServerProof::onServerStarted);
            NeoForge.EVENT_BUS.addListener(DeepDyConsentP10cServerProof::onServerTick);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        require(DeepDyConsentAttachment.state(level) == State.DISABLED,
                phase + "_starts_disabled");
        require(exact(SlabSupport.minResolvedDy(), -1.0d), phase + "_starts_shipped");

        if ("enabled-writer".equals(phase)) {
            require(DeepDyConsentAttachment.grant(server) == GrantResult.ENABLED_NOW,
                    "enabled_writer_grant");
            require(DeepDyConsentAttachment.state(level) == State.ENABLED,
                    "enabled_writer_state");
            require(exact(SlabSupport.minResolvedDy(), -2.0d), "enabled_writer_cap");
        }

        require(server.saveAllChunks(false, true, true), phase + "_disk_save");
        writeReceipt(phase + ".ok", phase.replace('-', '_') + "=true\n");
        stopPending = true;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (stopPending) {
            stopPending = false;
            event.getServer().halt(false);
        }
    }

    public static void writeReceipt(String fileName, String content) {
        try {
            Path proofDirectory = Path.of("proof");
            Files.createDirectories(proofDirectory);
            Files.writeString(proofDirectory.resolve(fileName), content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write the P10C proof receipt", exception);
        }
    }

    private static boolean exact(double actual, double expected) {
        return Double.doubleToLongBits(actual) == Double.doubleToLongBits(expected);
    }

    private static void require(boolean condition, String check) {
        if (!condition) {
            throw new IllegalStateException("P10C proof check failed: " + check);
        }
    }
}
