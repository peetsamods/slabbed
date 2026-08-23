package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.client.SlabbedDebugToolBridge;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * THE DIAGNOSTICS COMPANION MUST ACTUALLY LOAD, not merely compile. Found 2026-08-23: the
 * companion (target-dy overlay, live-cursor recorder — {@code src/diagnostics/}) is registered as
 * a Loom mod ({@code loom.mods.slabbed_diagnostics}), which generates the {@code
 * fabric.classPathGroups} entry that groups its classes with its resources — and that alone LOOKS
 * like enough. It is not: {@code classPathGroups} only groups directories already ON a run's
 * classpath, it does not ADD them. The INTERACTIVE {@code runClient}/{@code runServer} launches had
 * no such addition anywhere, so the companion's {@code fabric.mod.json} was never reachable by
 * Fabric Loader's mod scan there — no error, no crash, just {@code SlabbedDebugToolBridge} staying
 * uninstalled and {@code /slabdy}/{@code /slabdev} answering "not available in this build" forever.
 * Caught only by reading the "Loading N mods" log line by hand and noticing {@code
 * slabbed_diagnostics} was absent from it.
 *
 * <p>{@code clientGameTest} was never actually affected by that gap — it reuses the headless
 * gametest source set's own compiled output (no separate compile task exists for it), and that
 * source set already carried diagnostics explicitly. An early attempt to "fix" clientGameTest the
 * same way as client/server ({@code source sourceSets.diagnostics} directly on the run) was reverted
 * the same day: it broke the run's own test-harness main class, so the JVM silently booted a bare
 * client instead — EVERY entrypoint, not just this one, went from PASS to never running, with
 * "BUILD SUCCESSFUL" and zero {@code CLIENT_GAMETEST} lines. See build.gradle's comment on this run.
 *
 * <p>So this row exists to prove clientGameTest's EXISTING wiring keeps working, not to guard a fix
 * applied here — the interactive runs have no headless proxy for the same claim, so re-check them by
 * hand after touching the diagnostics source set or its consumers.
 *
 * <p>Round-trips the overlay flag rather than only checking {@link SlabbedDebugToolBridge#available()}
 * — a provider could be installed but wired to dead methods, and a bare availability check would
 * not catch that.
 */
public final class DevDiagnosticsCompanionLoadsClientGameTest implements FabricClientGameTest {

    private static final String CLIENT_GAMETEST_PASS =
            "CLIENT_GAMETEST | DevDiagnosticsCompanionLoadsClientGameTest | PASS";

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            if (!SlabbedDebugToolBridge.available()) {
                throw new AssertionError("[DEV_DIAGNOSTICS_NOT_LOADED] SlabbedDebugToolBridge has no "
                        + "provider installed — the diagnostics companion (src/diagnostics/) never "
                        + "initialized under this run. Check that the gametest source set in "
                        + "build.gradle still adds sourceSets.diagnostics.output to its "
                        + "compile/runtimeClasspath, and that the companion's fabric.mod.json still "
                        + "declares the client entrypoint. Do NOT fix this by adding "
                        + "source(sourceSets.diagnostics) to loom.runs.clientGameTest directly — that "
                        + "was tried and it broke the whole client-gametest run instead.");
            }

            boolean before = SlabbedDebugToolBridge.overlayEnabled();
            try {
                SlabbedDebugToolBridge.setOverlayEnabled(!before);
                if (SlabbedDebugToolBridge.overlayEnabled() == before) {
                    throw new AssertionError("[DEV_DIAGNOSTICS_NOT_LOADED] a provider answered "
                            + "available()=true but the overlay flag did not move — installed but "
                            + "wired to a dead implementation.");
                }
            } finally {
                SlabbedDebugToolBridge.setOverlayEnabled(before);
            }
        });
        Slabbed.LOGGER.info(CLIENT_GAMETEST_PASS);
    }
}
