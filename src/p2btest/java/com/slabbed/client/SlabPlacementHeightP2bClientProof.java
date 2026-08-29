package com.slabbed.client;

import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.test.SlabPlacementHeightP2bProof;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Drives the physical-client half of the isolated P2b lifecycle proof. */
public final class SlabPlacementHeightP2bClientProof {
    private static final int ABSENT = 1_000;
    private static final int UNAVAILABLE = 1_001;
    private static final int PHASE_TIMEOUT_TICKS = 1_200;
    private static final List<RefreshObservation> REFRESHES = new ArrayList<>();
    private static Phase phase = Phase.WAIT_INITIAL;
    private static boolean registered;
    private static int phaseTicks;
    private static int loginCount;
    private static int resetCount;
    private static int resetBaseline;
    private static boolean initialWorldOpenQueued;
    private static boolean logoutQueued;
    private static boolean reopenQueued;
    private static long refreshSerial;
    private static long refreshBaseline;
    private static ClientLevel initialLevel;
    private static volatile boolean updateIssued;
    private static volatile boolean removalIssued;
    private static volatile boolean saveComplete;
    private static volatile String serverFailure;

    private SlabPlacementHeightP2bClientProof() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        if (!"client".equals(System.getProperty(SlabPlacementHeightP2bProof.PHASE_PROPERTY))) {
            throw new IllegalStateException("The P2b client proof requires the client phase");
        }
        registered = true;
        SlabPlacementHeightClientSync.installProofObservers(
                SlabPlacementHeightP2bClientProof::onRefresh,
                generation -> resetCount++);
        NeoForge.EVENT_BUS.addListener(SlabPlacementHeightP2bClientProof::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(SlabPlacementHeightP2bClientProof::onClientTick);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        loginCount++;
    }

    private static void onRefresh(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        refreshSerial++;
        REFRESHES.add(new RefreshObservation(
                refreshSerial,
                pos.asLong(),
                clientHalfSteps(minecraft, pos),
                minecraft.level == null ? 0 : System.identityHashCode(minecraft.level),
                SlabPlacementHeightClientSync.connectionGeneration()));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (phase == Phase.DONE || phase == Phase.FAILED) {
            return;
        }
        if (serverFailure != null) {
            fail("server_" + serverFailure);
            return;
        }
        phaseTicks++;
        if (phaseTicks > PHASE_TIMEOUT_TICKS) {
            fail("timeout_" + phase.name().toLowerCase());
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        switch (phase) {
            case WAIT_INITIAL -> waitForInitial(minecraft);
            case WAIT_UPDATE -> waitForUpdate(minecraft);
            case WAIT_REMOVAL -> waitForRemoval(minecraft);
            case WAIT_SAVE -> waitForSave(minecraft);
            case WAIT_LOGOUT -> waitForLogout(minecraft);
            case WAIT_RECONNECT -> waitForReconnect(minecraft);
            case DONE, FAILED -> { }
        }
    }

    private static void waitForInitial(Minecraft minecraft) {
        if (minecraft.level == null && minecraft.getSingleplayerServer() == null) {
            if (!initialWorldOpenQueued && minecraft.isGameLoadFinished()) {
                initialWorldOpenQueued = true;
                minecraft.tell(() -> minecraft.createWorldOpenFlows().openWorld(
                        SlabPlacementHeightP2bProof.WORLD_NAME,
                        () -> fail("initial_open_failed")));
            }
            return;
        }
        if (loginCount < 1 || minecraft.level == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        if (clientHalfSteps(minecraft, SlabPlacementHeightP2bProof.RETAINED_POS) != -2
                || clientHalfSteps(minecraft, SlabPlacementHeightP2bProof.REMOVED_POS) != -1
                || clientHalfSteps(minecraft, SlabPlacementHeightP2bProof.LEGACY_POS) != ABSENT
                || !hasRefreshAfter(0, SlabPlacementHeightP2bProof.RETAINED_POS, -2, minecraft.level)
                || !hasRefreshAfter(0, SlabPlacementHeightP2bProof.REMOVED_POS, -1, minecraft.level)) {
            return;
        }
        initialLevel = minecraft.level;
        refreshBaseline = refreshSerial;
        runOnServer(minecraft, "update", level -> {
            LevelChunk chunk = level.getChunkAt(SlabPlacementHeightP2bProof.RETAINED_POS);
            requireServer(SlabPlacementHeightAttachment.putHalfSteps(
                    chunk, SlabPlacementHeightP2bProof.RETAINED_POS, -1), "update_write");
            updateIssued = true;
        });
        advance(Phase.WAIT_UPDATE);
    }

    private static void waitForUpdate(Minecraft minecraft) {
        if (!updateIssued || minecraft.level == null
                || clientHalfSteps(minecraft, SlabPlacementHeightP2bProof.RETAINED_POS) != -1
                || !hasRefreshAfter(refreshBaseline,
                        SlabPlacementHeightP2bProof.RETAINED_POS, -1, minecraft.level)) {
            return;
        }
        refreshBaseline = refreshSerial;
        runOnServer(minecraft, "removal", level -> {
            LevelChunk chunk = level.getChunkAt(SlabPlacementHeightP2bProof.REMOVED_POS);
            requireServer(SlabPlacementHeightAttachment.remove(
                    chunk, SlabPlacementHeightP2bProof.REMOVED_POS), "removal_write");
            removalIssued = true;
        });
        advance(Phase.WAIT_REMOVAL);
    }

    private static void waitForRemoval(Minecraft minecraft) {
        if (!removalIssued || minecraft.level == null
                || clientHalfSteps(minecraft, SlabPlacementHeightP2bProof.REMOVED_POS) != ABSENT
                || !hasRefreshAfter(refreshBaseline,
                        SlabPlacementHeightP2bProof.REMOVED_POS, ABSENT, minecraft.level)) {
            return;
        }
        runOnServer(minecraft, "save", level -> {
            requireServer(level.getServer().saveAllChunks(false, true, true), "client_disk_save");
            saveComplete = true;
        });
        advance(Phase.WAIT_SAVE);
    }

    private static void waitForSave(Minecraft minecraft) {
        if (!saveComplete || logoutQueued) {
            return;
        }
        logoutQueued = true;
        resetBaseline = resetCount;
        refreshBaseline = refreshSerial;
        if (minecraft.getSingleplayerServer() == null) {
            fail("logout_server_absent");
            return;
        }
        minecraft.getSingleplayerServer().halt(false);
        advance(Phase.WAIT_LOGOUT);
        minecraft.tell(() -> minecraft.disconnect(new TitleScreen()));
    }

    private static void waitForLogout(Minecraft minecraft) {
        if (minecraft.level != null || minecraft.hasSingleplayerServer()
                || resetCount <= resetBaseline
                || !SlabPlacementHeightClientSync.proofCachesEmpty()
                || !(minecraft.screen instanceof TitleScreen)
                || reopenQueued) {
            return;
        }
        reopenQueued = true;
        advance(Phase.WAIT_RECONNECT);
        minecraft.tell(() -> minecraft.createWorldOpenFlows().openWorld(
                SlabPlacementHeightP2bProof.WORLD_NAME,
                () -> fail("reopen_failed")));
    }

    private static void waitForReconnect(Minecraft minecraft) {
        if (loginCount < 2 || minecraft.level == null || minecraft.level == initialLevel
                || minecraft.getSingleplayerServer() == null) {
            return;
        }
        int generation = SlabPlacementHeightClientSync.connectionGeneration();
        if (clientHalfSteps(minecraft, SlabPlacementHeightP2bProof.RETAINED_POS) != -1
                || clientHalfSteps(minecraft, SlabPlacementHeightP2bProof.REMOVED_POS) != ABSENT
                || clientHalfSteps(minecraft, SlabPlacementHeightP2bProof.LEGACY_POS) != ABSENT
                || !hasRefreshAfter(refreshBaseline,
                        SlabPlacementHeightP2bProof.RETAINED_POS, -1, minecraft.level)
                || hasPositionRefreshAfter(refreshBaseline,
                        SlabPlacementHeightP2bProof.REMOVED_POS, minecraft.level, generation)) {
            return;
        }
        SlabPlacementHeightP2bProof.writeReceipt(
                "client.ok",
                "initial=true update=true removal=true reset=true reconnect=true\n");
        phase = Phase.DONE;
        minecraft.stop();
    }

    private static void runOnServer(
            Minecraft minecraft,
            String operation,
            java.util.function.Consumer<ServerLevel> action
    ) {
        if (minecraft.getSingleplayerServer() == null) {
            fail(operation + "_server_absent");
            return;
        }
        minecraft.getSingleplayerServer().execute(() -> {
            try {
                action.accept(minecraft.getSingleplayerServer().overworld());
            } catch (RuntimeException exception) {
                serverFailure = operation;
            }
        });
    }

    private static int clientHalfSteps(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level == null) {
            return UNAVAILABLE;
        }
        LevelChunk chunk = minecraft.level.getChunkSource().getChunk(
                pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        if (chunk == null) {
            return UNAVAILABLE;
        }
        return SlabPlacementHeightAttachment.storedHalfSteps(chunk, pos).orElse(ABSENT);
    }

    private static boolean hasRefreshAfter(
            long serial,
            BlockPos pos,
            int halfSteps,
            ClientLevel level
    ) {
        int levelIdentity = System.identityHashCode(level);
        int generation = SlabPlacementHeightClientSync.connectionGeneration();
        return REFRESHES.stream().anyMatch(observation -> observation.serial() > serial
                && observation.packedPos() == pos.asLong()
                && observation.halfSteps() == halfSteps
                && observation.levelIdentity() == levelIdentity
                && observation.generation() == generation);
    }

    private static boolean hasPositionRefreshAfter(
            long serial,
            BlockPos pos,
            ClientLevel level,
            int generation
    ) {
        int levelIdentity = System.identityHashCode(level);
        return REFRESHES.stream().anyMatch(observation -> observation.serial() > serial
                && observation.packedPos() == pos.asLong()
                && observation.levelIdentity() == levelIdentity
                && observation.generation() == generation);
    }

    private static void advance(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    private static void fail(String reason) {
        if (phase == Phase.FAILED) {
            return;
        }
        phase = Phase.FAILED;
        SlabPlacementHeightP2bProof.writeReceipt("client.failed", "phase=" + reason + "\n");
        Minecraft.getInstance().stop();
    }

    private static void requireServer(boolean condition, String check) {
        if (!condition) {
            throw new IllegalStateException(check);
        }
    }

    private enum Phase {
        WAIT_INITIAL,
        WAIT_UPDATE,
        WAIT_REMOVAL,
        WAIT_SAVE,
        WAIT_LOGOUT,
        WAIT_RECONNECT,
        DONE,
        FAILED
    }

    private record RefreshObservation(
            long serial,
            long packedPos,
            int halfSteps,
            int levelIdentity,
            int generation
    ) {
    }
}
