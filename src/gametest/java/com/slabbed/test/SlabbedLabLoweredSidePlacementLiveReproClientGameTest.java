package com.slabbed.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Legacy audit helper for the lowered-side-slab live placement failure (items 2–3).
 *
 * <p>This class is intentionally not a {@code FabricClientGameTest}; it is not
 * registered in {@code fabric.mod.json} and records diagnostic notes only.
 *
 * <p>The existing proof ({@link SlabbedLabLoweredSideSlabPlacementClientGameTest})
 * constructs a synthetic {@code BlockHitResult} at {@code Y = blockY - 0.25} (below the
 * vanilla block floor, in the lowered visual space) and calls {@code interactBlock}
 * directly, bypassing the live raycast entirely. The proof screenshot camera (yaw=180,
 * looking north) cannot physically aim at the east face of the full block.
 *
 * <p>This diagnostic performs two explicit raycasts to expose the divergence:
 * <ul>
 *   <li><b>Camera-A</b> — the proof screenshot angle (yaw=180, looking north from south).
 *       Expected result: MISS — the camera cannot reach the east face.</li>
 *   <li><b>Camera-B</b> — east of block, looking west (yaw=90) at the vanilla hitbox
 *       midpoint (Y+0.5). Expected result: hits east face at vanilla Y, not at Y-0.25.</li>
 * </ul>
 *
 * <p>Findings are recorded in
 * {@code build/run/clientGameTest/screenshots/live_repro_side_placement_audit.json}.
 * This diagnostic does NOT throw; it is an audit-only run and does not claim proof bundle
 * verification.
 *
 * <p>Do not fix behavior in this slice. If the audit JSON confirms the divergence, the
 * next slice should address hitbox alignment separately.
 */
public final class SlabbedLabLoweredSidePlacementLiveReproClientGameTest {

    private SlabbedLabLoweredSidePlacementLiveReproClientGameTest() {
    }

    static void runLegacyLoweredSidePlacementLiveRepro(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            Path screenshotDir = SlabbedLabClientGameTest.resolveClientGameTestScreenshotDir();
            Set<String> knownScreenshotFiles = SlabbedLabClientGameTest.listScreenshotFileNames(screenshotDir);
            List<SlabbedLabClientGameTest.ManifestArtifact> artifacts = new ArrayList<>();

            singleplayer.getClientWorld().waitForChunksRender();
            SlabbedLabClientGameTest.runLoweredSidePlacementLiveRepro(
                    ctx,
                    singleplayer,
                    screenshotDir,
                    knownScreenshotFiles,
                    artifacts);
        }
    }
}
