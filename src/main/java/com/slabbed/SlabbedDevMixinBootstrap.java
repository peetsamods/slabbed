package com.slabbed;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.spongepowered.asm.mixin.Mixins;

/**
 * Arms the DEV-ONLY mixin configs. Nothing here runs in a release build.
 *
 * <p>The live-cursor recorder's five mixins ({@code com.slabbed.mixin.recorder.**} and
 * {@code com.slabbed.mixin.client.recorder.**}) have no functional effect whatsoever — every
 * one of them exists solely to feed {@code LiveCursorIntentRecorder}, which lives in
 * {@code com/slabbed/dev/audit/**} and is excluded from the release jar by build.gradle's
 * pre-release hygiene gate. Before this class existed they were listed in
 * {@code slabbed.mixins.json} / {@code slabbed.client.mixins.json} with
 * {@code "required": true}, so in a release build they applied to every block placement and
 * every block interaction, allocated a {@code Class<?>[]} and a varargs {@code Object[]} at
 * each call site BEFORE the bridge's null-guard could discard them, and threw the result away
 * because their sink was not in the jar. That is the PERF hygiene gate's exact failure shape.
 *
 * <p>They are now in their own configs, which are NOT declared in {@code fabric.mod.json} and
 * are excluded from both release artifacts. They are added here, at preLaunch, only when
 * {@link FabricLoader#isDevelopmentEnvironment()} — which is where the recorder is the
 * campaign's primary diagnostic instrument and must keep working. preLaunch runs after
 * {@code FabricMixinBootstrap} and before the game class is loaded, so a config added here is
 * still in time for mixin selection.
 *
 * <p>The resource-presence check is belt and braces: a config that is not on the classpath is
 * skipped silently rather than aborting the launch, so an unexpected build shape degrades to
 * "no recorder" instead of "no game".
 *
 * <p>NOT wired here on purpose: {@code slabbed.debug.mixins.json}. It has been unreferenced
 * for its whole life and an inert mixin may well be intentional — wiring it up is a separate
 * decision with its own live risk, not a side effect of this change.
 */
public final class SlabbedDevMixinBootstrap implements PreLaunchEntrypoint {

    private static final String RECORDER_CONFIG = "slabbed.recorder.mixins.json";
    private static final String RECORDER_CLIENT_CONFIG = "slabbed.recorder.client.mixins.json";

    @Override
    public void onPreLaunch() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }
        addIfPresent(RECORDER_CONFIG);
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            addIfPresent(RECORDER_CLIENT_CONFIG);
        }
    }

    private static void addIfPresent(String config) {
        if (SlabbedDevMixinBootstrap.class.getClassLoader().getResource(config) == null) {
            Slabbed.LOGGER.info("Slabbed dev mixin config {} is not on the classpath; skipping", config);
            return;
        }
        Mixins.addConfiguration(config);
        Slabbed.LOGGER.info("Slabbed dev mixin config {} armed (development environment)", config);
    }
}
