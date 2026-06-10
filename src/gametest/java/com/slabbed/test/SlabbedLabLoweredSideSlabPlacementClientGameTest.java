package com.slabbed.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Legacy helper for the BS > FB > lower-half side slab placement repro.
 *
 * <p>This class is intentionally not a {@code FabricClientGameTest}; it is not
 * registered in {@code fabric.mod.json} and must not be treated as an active
 * proof gate without a fresh registration/proof-authority slice.
 */
public final class SlabbedLabLoweredSideSlabPlacementClientGameTest {

    private SlabbedLabLoweredSideSlabPlacementClientGameTest() {
    }

    static void runLegacyLoweredSideSlabPlacementProof(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            Path screenshotDir = SlabbedLabClientGameTest.resolveClientGameTestScreenshotDir();
            Set<String> knownScreenshotFiles = SlabbedLabClientGameTest.listScreenshotFileNames(screenshotDir);
            List<SlabbedLabClientGameTest.ManifestArtifact> artifacts = new ArrayList<>();

            singleplayer.getClientWorld().waitForChunksRender();
            SlabbedLabClientGameTest.runLoweredSideSlabPlacementRepro(
                    ctx,
                    singleplayer,
                    screenshotDir,
                    knownScreenshotFiles,
                    artifacts);
            SlabbedLabClientGameTest.writeRunManifest(screenshotDir, artifacts);
            SlabbedLabClientGameTest.assertLoweredSideSlabProofArtifacts(screenshotDir);
            SlabbedLabClientGameTest.writeProofSummary(screenshotDir);
            SlabbedLabClientGameTest.writeProofIndex(screenshotDir);
            SlabbedLabClientGameTest.writeLatestProofRun(screenshotDir);
            SlabbedLabClientGameTest.writeProofReceipt(screenshotDir);
            SlabbedLabClientGameTest.assertLoweredSideSlabProofBundle(screenshotDir);
        }
    }
}
