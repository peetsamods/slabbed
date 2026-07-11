package com.slabbed.test;

import com.slabbed.util.LiveCursorIntentRecorder;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/**
 * HEADLESS capture-path proof for the revived {@link LiveCursorIntentRecorder}.
 *
 * <p>The pre-existing contract test ({@code SlabbedLabLiveCursorIntentRecorderContractClientGameTest})
 * is a {@link net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest} and needs the client
 * gametest harness ({@code runClientGameTest}); this branch's legacy client tests are Yarn-mapped and
 * deferred, so that harness is not exercised in the normal {@code runGameTest} suite. This headless
 * server {@code GameTest} drives the recorder's public API directly and asserts the on-disk capture
 * files, so the revival's core capture path is proven by the reliable server harness regardless of
 * the client harness's state.
 *
 * <p>Against the pre-revival inert stub every {@code record*} method was a no-op, so NONE of the
 * asserted files would ever be written — this test fails RED. After the revival it goes GREEN.
 */
public final class LiveCursorIntentRecorderCaptureGameTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void recorderWritesSessionAndSummaryWhenEnabled(GameTestHelper helper) {
        Path dir = Path.of("build", "gametest-live-cursor-recorder", "capture-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, dir.toString());
        try {
            LiveCursorIntentRecorder.resetForTests();
            if (!LiveCursorIntentRecorder.enabled()) {
                throw helper.assertionException(
                        "recorder must report enabled() when the JVM property is set — the volatile "
                                + "field should track the property at class-init");
            }

            LinkedHashMap<String, String> cursor = new LinkedHashMap<>();
            cursor.put("tick", "1");
            cursor.put("heldItem", "minecraft:stone");
            cursor.put("finalHitType", "BLOCK");
            cursor.put("finalHitPos", "4,-60,30");
            cursor.put("finalOwnerLaneKind", "persistent_lowered_slab_carrier");
            cursor.put("finalOutlineReplayHit", "hit=true pos=4,-60,30 side=east");
            cursor.put("finalRaycastReplayHit", "miss(empty)");
            cursor.put("outlineBounds", "min=(0.000000,0.000000,0.000000),max=(1.000000,1.000000,1.000000)");
            LiveCursorIntentRecorder.recordCursor(cursor);

            LinkedHashMap<String, String> action = new LinkedHashMap<>();
            action.put("actionType", "place_block");
            action.put("heldItem", "minecraft:stone_slab");
            action.put("clickedOwnerPos", "4,-60,30");
            action.put("clickedFace", "EAST");
            action.put("clickedOwnerLaneKind", "persistent_lowered_slab_carrier");
            action.put("placementPos", "5,-60,30");
            action.put("afterDy", "0.000000");
            LiveCursorIntentRecorder.recordAction(action);
            LiveCursorIntentRecorder.flushSummaryForTests();

            assertContains(helper, dir.resolve("session.jsonl"), "\"type\":\"cursor\"");
            assertContains(helper, dir.resolve("session.jsonl"), "LIVE_CURSOR_GHOST_SURFACE");
            assertContains(helper, dir.resolve("actions.tsv"), "place_block");
            assertContains(helper, dir.resolve("actions.tsv"), "PLAYER_AUTHORED");
            assertContains(helper, dir.resolve("actions.tsv"),
                    "LIVE_PLACEMENT_VANILLA_DY_FROM_LOWERED_OWNER");
            assertContains(helper, dir.resolve("summary.md"), "cursorRows=1");
            assertContains(helper, dir.resolve("summary.md"), "actionRows=1");
            assertContains(helper, dir.resolve("summary.md"), "playerAuthoredActionRows=1");
            assertContains(helper, dir.resolve("summary.md"), "autoUseOnProxyActionRows=0");
            assertContains(helper, dir.resolve("summary.md"), "ghostSurfaceRows=1");
        } finally {
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void manifestIsWrittenWithRedactedJavaCommand(GameTestHelper helper) {
        Path dir = Path.of("build", "gametest-live-cursor-recorder", "manifest-" + System.nanoTime());
        System.setProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY, "true");
        System.setProperty(LiveCursorIntentRecorder.DIR_PROPERTY, dir.toString());
        String priorCommand = System.getProperty("sun.java.command");
        System.setProperty("sun.java.command",
                "net.minecraft.client.main.Main --username the maintainer "
                        + "--accessToken eyJhbGciOiJSUzI1NiJ9.super-secret-jwt-token --userType msa");
        try {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw helper.assertionException("could not create legacy recorder fixture: " + e);
            }
            String legacyHeader = "actionId\tcursorRowId\tactionType\theldItem\nlegacy-schema-2-row\n";
            String legacyManifest = "{\"schemaVersion\":\"2\"}\n";
            write(helper, dir.resolve("actions.tsv"), legacyHeader);
            write(helper, dir.resolve("manifest.json"), legacyManifest);
            LiveCursorIntentRecorder.resetForTests();
            // bootstrap() must preserve the existing schema-2 evidence and choose a fresh schema-3
            // child directory instead of appending a 13-column row beneath the 12-column header.
            LiveCursorIntentRecorder.bootstrap();

            Path actualDir = Path.of(LiveCursorIntentRecorder.currentLogPathDisplay());
            Path requestedDir = dir.toAbsolutePath().normalize();
            if (actualDir.equals(requestedDir) || !actualDir.getParent().equals(requestedDir)
                    || !actualDir.getFileName().toString().startsWith("schema-3-")) {
                throw helper.assertionException(
                        "existing recorder evidence must trigger non-destructive schema-3 child isolation; got "
                                + actualDir);
            }
            if (!read(helper, dir.resolve("actions.tsv")).equals(legacyHeader)
                    || !read(helper, dir.resolve("manifest.json")).equals(legacyManifest)) {
                throw helper.assertionException("schema isolation must preserve old evidence byte-for-byte");
            }
            Path manifest = actualDir.resolve("manifest.json");
            assertContains(helper, manifest, "\"recorder\":\"LiveCursorIntentRecorder\"");
            assertContains(helper, manifest, "\"schemaVersion\":\"3\"");
            assertContains(helper, manifest,
                    "\"actionOriginContract\":\"PLAYER_AUTHORED|AUTO_USEON_PROXY\"");
            assertContains(helper, actualDir.resolve("actions.tsv"),
                    "actionId\tcursorRowId\tactionType\tactionOrigin\theldItem");
            assertContains(helper, manifest, "--accessToken [REDACTED]");
            String text = read(helper, manifest);
            if (text.contains("super-secret-jwt-token")) {
                throw helper.assertionException(
                        "manifest.json must NOT contain the raw accessToken value — got: " + text);
            }
            if (!text.contains("--username the maintainer")) {
                throw helper.assertionException(
                        "manifest.json's javaCommand must keep non-sensitive args — got: " + text);
            }
        } finally {
            if (priorCommand == null) {
                System.clearProperty("sun.java.command");
            } else {
                System.setProperty("sun.java.command", priorCommand);
            }
            System.clearProperty(LiveCursorIntentRecorder.ENABLE_PROPERTY);
            System.clearProperty(LiveCursorIntentRecorder.DIR_PROPERTY);
            LiveCursorIntentRecorder.resetForTests();
        }
        helper.succeed();
    }

    private static String read(GameTestHelper helper, Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw helper.assertionException("expected recorder output file to exist and be readable: "
                    + path + " (" + e + ")");
        }
    }

    private static void write(GameTestHelper helper, Path path, String text) {
        try {
            Files.writeString(path, text);
        } catch (IOException e) {
            throw helper.assertionException("could not write recorder fixture " + path + ": " + e);
        }
    }

    private static void assertContains(GameTestHelper helper, Path path, String needle) {
        String text = read(helper, path);
        if (!text.contains(needle)) {
            throw helper.assertionException("missing '" + needle + "' in " + path);
        }
    }
}
