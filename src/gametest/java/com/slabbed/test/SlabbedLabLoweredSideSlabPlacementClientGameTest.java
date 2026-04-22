package com.slabbed.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Focused client GameTest for the BS > FB > lower-half side slab placement repro.
 */
public final class SlabbedLabLoweredSideSlabPlacementClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
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
        }
    }
}
