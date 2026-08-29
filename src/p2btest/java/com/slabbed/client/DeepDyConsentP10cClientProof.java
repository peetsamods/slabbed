package com.slabbed.client;

import com.slabbed.anchor.DeepDyConsentAttachment;
import com.slabbed.anchor.DeepDyConsentAttachment.State;
import com.slabbed.test.DeepDyConsentP10cServerProof;
import com.slabbed.util.SlabSupport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Drives the physical-client half of the isolated deep-consent lifecycle proof. */
public final class DeepDyConsentP10cClientProof {
    private static final int PHASE_TIMEOUT_TICKS = 1_200;
    private static final List<Observation> SYNCS = new ArrayList<>();
    private static final List<Observation> SCHEDULED = new ArrayList<>();
    private static final List<Observation> EXECUTED = new ArrayList<>();
    private static final List<LoginSnapshot> LOGINS = new ArrayList<>();
    private static Phase phase = Phase.WAIT_ENABLED;
    private static boolean registered;
    private static boolean enabledOpenQueued;
    private static boolean logoutQueued;
    private static boolean offOpenQueued;
    private static int phaseTicks;
    private static int beforeLogoutCount;
    private static int resetCount;
    private static long ordinal;
    private static long offBaselineOrdinal;
    private static ClientLevel enabledLevel;

    private DeepDyConsentP10cClientProof() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        if (!"client".equals(System.getProperty(DeepDyConsentP10cServerProof.PHASE_PROPERTY))) {
            throw new IllegalStateException("The P10C client proof requires the client phase");
        }
        if (SlabSupport.DEEP_DY_ALPHABET) {
            throw new IllegalStateException("P10C proof requires the deep override to be off");
        }
        registered = true;
        DeepDyConsentClientSync.installProofObservers(
                enabled -> record(SYNCS, enabled),
                enabled -> record(SCHEDULED, enabled),
                enabled -> record(EXECUTED, enabled),
                generation -> {
                    resetCount++;
                    if (!exact(SlabSupport.minResolvedDy(), -1.0d)) {
                        fail("logout_reset_cap");
                    }
                });
        NeoForge.EVENT_BUS.addListener(
                EventPriority.HIGHEST,
                DeepDyConsentP10cClientProof::onBeforeLoggingOut);
        NeoForge.EVENT_BUS.addListener(DeepDyConsentP10cClientProof::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(DeepDyConsentP10cClientProof::onClientTick);
    }

    private static void onBeforeLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SlabSupport.armDeepAlphabet(true);
        beforeLogoutCount++;
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft minecraft = Minecraft.getInstance();
        LOGINS.add(new LoginSnapshot(
                nextOrdinal(),
                DeepDyConsentClientSync.connectionGeneration(),
                levelIdentity(minecraft.level),
                SlabSupport.minResolvedDy()));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (phase == Phase.DONE || phase == Phase.FAILED) {
            return;
        }
        phaseTicks++;
        if (phaseTicks > PHASE_TIMEOUT_TICKS) {
            fail("timeout_" + phase.name().toLowerCase());
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        switch (phase) {
            case WAIT_ENABLED -> waitForEnabled(minecraft);
            case WAIT_LOGOUT -> waitForLogout(minecraft);
            case WAIT_OFF -> waitForOff(minecraft);
            case DONE, FAILED -> { }
        }
    }

    private static void waitForEnabled(Minecraft minecraft) {
        if (minecraft.level == null && minecraft.getSingleplayerServer() == null) {
            if (!enabledOpenQueued && minecraft.isGameLoadFinished()) {
                enabledOpenQueued = true;
                minecraft.tell(() -> minecraft.createWorldOpenFlows().openWorld(
                        DeepDyConsentP10cServerProof.ENABLED_COPY_WORLD,
                        () -> fail("enabled_open_failed")));
            }
            return;
        }
        if (LOGINS.isEmpty() || minecraft.level == null
                || minecraft.getSingleplayerServer() == null) {
            return;
        }

        LoginSnapshot login = LOGINS.getFirst();
        Observation sync = first(SYNCS, true, 0L);
        Observation scheduled = first(SCHEDULED, true, 0L);
        Observation executed = first(EXECUTED, true, 0L);
        if (sync == null || scheduled == null || executed == null) {
            return;
        }
        Observation syncAtLogin = firstAtOrBefore(SYNCS, true, login.ordinal());
        if (syncAtLogin != null) {
            require(exact(login.cap(), -2.0d), "login_authoritative_cap");
        }
        require(DeepDyConsentAttachment.state(minecraft.level) == State.ENABLED,
                "enabled_client_state");
        require(exact(SlabSupport.minResolvedDy(), -2.0d), "enabled_live_cap");
        long acceptedTick = nextOrdinal();
        require(sync.generation() == login.generation(), "enabled_sync_generation");
        require(sync.ordinal() < scheduled.ordinal()
                        && scheduled.ordinal() < executed.ordinal()
                        && executed.ordinal() < acceptedTick,
                "enabled_ready_barrier");
        require(sync.generation() == scheduled.generation()
                        && scheduled.generation() == executed.generation(),
                "enabled_refresh_generation");
        require(executed.levelIdentity() == levelIdentity(minecraft.level),
                "enabled_refresh_level");

        enabledLevel = minecraft.level;
        logoutQueued = true;
        advance(Phase.WAIT_LOGOUT);
        minecraft.getSingleplayerServer().halt(false);
        minecraft.tell(() -> minecraft.disconnect(new TitleScreen()));
    }

    private static void waitForLogout(Minecraft minecraft) {
        if (!logoutQueued
                || minecraft.level != null
                || minecraft.hasSingleplayerServer()
                || !(minecraft.screen instanceof TitleScreen)
                || beforeLogoutCount < 1
                || resetCount < 1
                || !exact(SlabSupport.minResolvedDy(), -1.0d)) {
            return;
        }
        offBaselineOrdinal = ordinal;
        offOpenQueued = true;
        advance(Phase.WAIT_OFF);
        minecraft.tell(() -> minecraft.createWorldOpenFlows().openWorld(
                DeepDyConsentP10cServerProof.OFF_WORLD,
                () -> fail("off_open_failed")));
    }

    private static void waitForOff(Minecraft minecraft) {
        if (!offOpenQueued || LOGINS.size() < 2 || minecraft.level == null
                || minecraft.level == enabledLevel
                || minecraft.getSingleplayerServer() == null) {
            return;
        }
        LoginSnapshot login = LOGINS.get(1);
        Observation sync = first(SYNCS, false, offBaselineOrdinal);
        Observation scheduled = first(SCHEDULED, false, offBaselineOrdinal);
        Observation executed = first(EXECUTED, false, offBaselineOrdinal);
        if (sync == null || scheduled == null || executed == null) {
            return;
        }
        require(exact(login.cap(), -1.0d), "off_login_cap");
        require(DeepDyConsentAttachment.state(minecraft.level) == State.DISABLED,
                "off_client_state");
        require(exact(SlabSupport.minResolvedDy(), -1.0d), "off_live_cap");
        long acceptedTick = nextOrdinal();
        require(sync.generation() == login.generation(), "off_sync_generation");
        require(sync.ordinal() < scheduled.ordinal()
                        && scheduled.ordinal() < executed.ordinal()
                        && executed.ordinal() < acceptedTick,
                "off_ready_barrier");
        require(sync.generation() == scheduled.generation()
                        && scheduled.generation() == executed.generation(),
                "off_refresh_generation");
        require(executed.levelIdentity() == levelIdentity(minecraft.level), "off_refresh_level");
        require(EXECUTED.stream().noneMatch(observation -> observation.enabled()
                        && observation.generation() == executed.generation()),
                "no_stale_enabled_refresh");

        DeepDyConsentP10cServerProof.writeReceipt(
                "client.ok",
                "restart=true copy=true sync=true refresh=true logout_reset=true off_reconnect=true\n");
        phase = Phase.DONE;
        minecraft.stop();
    }

    private static void record(List<Observation> target, boolean enabled) {
        Minecraft minecraft = Minecraft.getInstance();
        target.add(new Observation(
                enabled,
                nextOrdinal(),
                DeepDyConsentClientSync.connectionGeneration(),
                levelIdentity(minecraft.level)));
    }

    private static Observation first(List<Observation> source, boolean enabled, long after) {
        return source.stream()
                .filter(observation -> observation.enabled() == enabled
                        && observation.ordinal() > after)
                .findFirst()
                .orElse(null);
    }

    private static Observation firstAtOrBefore(
            List<Observation> source,
            boolean enabled,
            long atOrBefore
    ) {
        return source.stream()
                .filter(observation -> observation.enabled() == enabled
                        && observation.ordinal() <= atOrBefore)
                .findFirst()
                .orElse(null);
    }

    private static int levelIdentity(ClientLevel level) {
        return level == null ? 0 : System.identityHashCode(level);
    }

    private static long nextOrdinal() {
        return ++ordinal;
    }

    private static boolean exact(double actual, double expected) {
        return Double.doubleToLongBits(actual) == Double.doubleToLongBits(expected);
    }

    private static void require(boolean condition, String check) {
        if (!condition) {
            fail(check);
            throw new IllegalStateException("P10C proof check failed: " + check);
        }
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
        DeepDyConsentP10cServerProof.writeReceipt("client.failed", "phase=" + reason + "\n");
        Minecraft.getInstance().stop();
    }

    private enum Phase {
        WAIT_ENABLED,
        WAIT_LOGOUT,
        WAIT_OFF,
        DONE,
        FAILED
    }

    private record Observation(
            boolean enabled,
            long ordinal,
            int generation,
            int levelIdentity
    ) {
    }

    private record LoginSnapshot(
            long ordinal,
            int generation,
            int levelIdentity,
            double cap
    ) {
    }
}
