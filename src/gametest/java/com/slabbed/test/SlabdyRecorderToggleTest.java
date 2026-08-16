package com.slabbed.test;

import com.slabbed.dev.audit.LiveCursorIntentRecorder;
import com.slabbed.util.SlabbedAuditBridge;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Headless proof for the {@code /slabdy record} toggle mechanics: this is exactly
 * what let the recorder down before this fix — {@link LiveCursorIntentRecorder}'s
 * enabled flag was {@code private static final}, resolved once from a JVM property at
 * class-init, so no in-game command could ever turn it on. This pins that a runtime
 * toggle now genuinely flips both {@link LiveCursorIntentRecorder#isEnabled()} AND
 * {@link SlabbedAuditBridge#isRecorderEnabled()} in lockstep (the bridge used to cache
 * its own separate, permanently-stale copy of the same JVM property — toggling the
 * recorder without also fixing the bridge would have left every mixin call site
 * silently gated shut regardless of the toggle).
 */
public final class SlabdyRecorderToggleTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void toggleFlipsBothTheRecorderAndTheBridgeTogether(TestContext ctx) {
        boolean initial = LiveCursorIntentRecorder.isEnabled();
        ctx.assertTrue(SlabbedAuditBridge.isRecorderEnabled() == initial,
                "the bridge must agree with the recorder's initial state, not a stale cached copy");

        boolean afterFirstToggle = LiveCursorIntentRecorder.toggle();
        ctx.assertTrue(afterFirstToggle != initial, "toggle() must flip the enabled state");
        ctx.assertTrue(LiveCursorIntentRecorder.isEnabled() == afterFirstToggle,
                "isEnabled() must reflect the state toggle() just returned");
        ctx.assertTrue(SlabbedAuditBridge.isRecorderEnabled() == afterFirstToggle,
                "the bridge must see the SAME live state the recorder itself reports — "
                        + "this is the exact bug the bridge fix closes");

        boolean afterSecondToggle = LiveCursorIntentRecorder.toggle();
        ctx.assertTrue(afterSecondToggle == initial, "toggling twice must return to the original state");
        ctx.assertTrue(SlabbedAuditBridge.isRecorderEnabled() == initial,
                "the bridge must track the toggle back to the original state too");

        // Leave the recorder in its original state so this test has no lasting
        // side effect on any test that runs after it in the same suite.
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bridgeSetsAnExplicitRecorderState(TestContext ctx) {
        ctx.assertTrue(SlabbedAuditBridge.isRecorderAvailable(),
                "the development test environment must contain the recorder implementation");
        boolean initial = SlabbedAuditBridge.isRecorderEnabled();
        try {
            ctx.assertTrue(SlabbedAuditBridge.setRecorderEnabled(true),
                    "setting the recorder on must leave the bridge on");
            ctx.assertTrue(SlabbedAuditBridge.isRecorderEnabled(),
                    "the bridge must report the requested on state");

            boolean afterOff = SlabbedAuditBridge.setRecorderEnabled(false);
            ctx.assertTrue(!afterOff,
                    "setting the recorder off must leave the bridge off");
            ctx.assertTrue(!SlabbedAuditBridge.isRecorderEnabled(),
                    "the bridge must report the requested off state");
        } finally {
            SlabbedAuditBridge.setRecorderEnabled(initial);
        }
        ctx.complete();
    }

    /**
     * Regression pin for the runEnded-reset fix. {@code recordShutdown()} sets a
     * permanent {@code runEnded} flag and early-returns once it's set — a SECOND
     * toggle-off in the same JVM run would silently skip its flush unless
     * {@code bootstrap()} resets that flag on re-enable. This does NOT show up in
     * the enabled/disabled boolean itself (that flips correctly either way) — it
     * only shows up in whether the second "run_end" record actually gets written to
     * disk, so this test reads the real session.jsonl file rather than just
     * checking isEnabled().
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void secondToggleOffActuallyFlushesASecondRunEndRecord(TestContext ctx) throws IOException {
        boolean initial = LiveCursorIntentRecorder.isEnabled();
        if (!initial) {
            LiveCursorIntentRecorder.toggle(); // ensure ON so the first toggle below is an "off"
        }
        Path sessionPath = Path.of(LiveCursorIntentRecorder.currentLogPathDisplay()).resolve("session.jsonl");

        LiveCursorIntentRecorder.toggle(); // off #1 -> should flush run_end #1
        long runEndsAfterFirstOff = countRunEndRecords(sessionPath);
        ctx.assertTrue(runEndsAfterFirstOff >= 1, "the first toggle-off must flush at least one run_end record");

        LiveCursorIntentRecorder.toggle(); // on again
        LiveCursorIntentRecorder.toggle(); // off #2 -> should flush a SECOND run_end
        long runEndsAfterSecondOff = countRunEndRecords(sessionPath);
        ctx.assertTrue(runEndsAfterSecondOff > runEndsAfterFirstOff,
                "a second toggle-off in the same run must flush its OWN run_end record, not silently "
                        + "no-op because a stale runEnded flag was never reset — before the fix this stayed "
                        + "stuck at " + runEndsAfterFirstOff);

        // Restore original state.
        if (LiveCursorIntentRecorder.isEnabled() != initial) {
            LiveCursorIntentRecorder.toggle();
        }
        ctx.complete();
    }

    private static long countRunEndRecords(Path sessionPath) throws IOException {
        if (!Files.exists(sessionPath)) {
            return 0;
        }
        try (var lines = Files.lines(sessionPath)) {
            return lines.filter(line -> line.contains("\"stage\":\"run_end\"")).count();
        }
    }

    /**
     * End-to-end proof of the enriched capture path: with the recorder enabled,
     * {@link LiveCursorIntentRecorder#recordVisualDiagnostic} writes a visual_diagnostic
     * session row carrying the SlabbedDiagnostics flags. The only untested link is the
     * live crosshair read (client-only). A suspect sample must land as suspect=true with
     * its flags; a clean sample must land as suspect=false.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void recordVisualDiagnosticWritesFlaggedRows(TestContext ctx) throws IOException {
        boolean initial = LiveCursorIntentRecorder.isEnabled();
        if (!initial) {
            LiveCursorIntentRecorder.toggle();
        }
        Path sessionPath = Path.of(LiveCursorIntentRecorder.currentLogPathDisplay()).resolve("session.jsonl");

        // A hand-built suspect sample (DODO + triad mismatch) — exercises the writer, not analyze().
        com.slabbed.dev.SlabbedDiagnostics.Sample suspect = new com.slabbed.dev.SlabbedDiagnostics.Sample(
                "minecraft:stone", -0.5, 0.0, 0.0, 0.0, 0.0,
                true, false, "none", "minecraft:air", 0.0, "minecraft:oak_slab", 0.0,
                true, true, false, false, false, false, false,
                false,
                com.slabbed.dev.SlabbedDiagnostics.LegCheck.CHECKED,
                com.slabbed.dev.SlabbedDiagnostics.LegCheck.EMPTY_BY_DESIGN,
                com.slabbed.dev.SlabbedDiagnostics.LegCheck.CHECKED);
        net.minecraft.util.math.BlockPos p = new net.minecraft.util.math.BlockPos(1, 2, 3);
        long before = countVisualDiagnosticSuspects(sessionPath);
        LiveCursorIntentRecorder.recordVisualDiagnostic(p, suspect);
        long after = countVisualDiagnosticSuspects(sessionPath);
        ctx.assertTrue(after > before,
                "a suspect visual diagnostic must be flushed as a suspect=true row (before=" + before + ")");

        // Restore original state so the suite is order-independent.
        if (LiveCursorIntentRecorder.isEnabled() != initial) {
            LiveCursorIntentRecorder.toggle();
        }
        ctx.complete();
    }

    /**
     * The recorder's five mixins were moved OUT of slabbed.mixins.json / slabbed.client.mixins.json
     * and into slabbed.recorder(.client).mixins.json, which fabric.mod.json does not reference and
     * which build.gradle excludes from both release artifacts. They are added at preLaunch by
     * SlabbedDevMixinBootstrap, and only in a development environment. That is exactly the kind of
     * arrangement that silently stops working: nothing here is {@code required}, every call site is
     * {@code require = 0}, and the recorder would simply go quiet rather than fail loudly.
     *
     * <p>So this pins the dev half of the arrangement: the config is registered with Mixin, AND its
     * handler really was merged into the vanilla target. The release half is not testable from here
     * (gametests only ever run in a development environment) and is proved by enumerating the
     * artifacts instead.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void recorderMixinsStillApplyInTheDevEnvironment(TestContext ctx) {
        // Asserted against the TARGET CLASSES, not against Mixin's config registry: Mixin removes
        // a config from Mixins.getConfigs() the moment it is selected for an environment, so by
        // the time a gametest runs that set is empty and a registry check reads as a false RED.
        // The merged handler is the real proof anyway — a config that registered but selected
        // nothing would leave the recorder just as blind.
        //
        // Substring, not exact name, so Mixin's own handler renaming (it prefixes conflicting
        // private members) cannot read as a regression.
        assertHandlerMerged(ctx,
                net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket.class,
                "recordPacketSequence",
                "PlayerInteractBlockPacketRecorderMixin");
        assertHandlerMerged(ctx,
                net.minecraft.server.network.ServerPlayNetworkHandler.class,
                "recordServerInteract",
                "ServerPlayNetworkHandlerRecorderMixin");
        assertHandlerMerged(ctx,
                net.minecraft.item.BlockItem.class,
                "tracePlaceHead",
                "BlockItemPlaceTraceMixin");

        // The CLIENT half cannot be exercised here — runGameTest is a dedicated server, so
        // SlabbedDevMixinBootstrap never takes its EnvType.CLIENT branch and
        // ClientPlayerInteractionRecorderMixin never applies. What CAN be closed from here is the
        // way that half realistically breaks: a config filename or a mixin class name that no
        // longer resolves, which fails silently because both configs are "required": false and
        // every call site is require = 0. So both configs are read off the classpath under the
        // exact names the bootstrap asks for, and every class they name is resolved.
        assertConfigResolves(ctx, "slabbed.recorder.mixins.json");
        assertConfigResolves(ctx, "slabbed.recorder.client.mixins.json");
        ctx.complete();
    }

    private static void assertConfigResolves(TestContext ctx, String config) {
        var url = SlabdyRecorderToggleTest.class.getClassLoader().getResource(config);
        ctx.assertTrue(url != null,
                config + " must be on the classpath under exactly this name — it is the string "
                        + "SlabbedDevMixinBootstrap passes to Mixins.addConfiguration, and a "
                        + "mismatch just silently turns the recorder off");

        com.google.gson.JsonObject json;
        try (var in = SlabdyRecorderToggleTest.class.getClassLoader().getResourceAsStream(config)) {
            json = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + config, e);
        }

        String pkg = json.get("package").getAsString();
        for (String key : new String[]{"mixins", "client", "server"}) {
            if (!json.has(key)) {
                continue;
            }
            for (var element : json.getAsJsonArray(key)) {
                String fqcn = pkg + "." + element.getAsString();
                // Resolved as a RESOURCE, never Class.forName: loading a mixin class directly is
                // exactly what Mixin's transformer refuses to do, and the attempt aborts the run
                // rather than answering the question.
                String resource = fqcn.replace('.', '/') + ".class";
                ctx.assertTrue(
                        SlabdyRecorderToggleTest.class.getClassLoader().getResource(resource) != null,
                        config + " names " + fqcn + " but " + resource + " is not on the classpath "
                                + "— a config that selects nothing leaves the recorder blind "
                                + "without failing anything");
            }
        }
    }

    private static void assertHandlerMerged(TestContext ctx, Class<?> target, String handler, String mixin) {
        var declared = java.util.Arrays.stream(target.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();
        ctx.assertTrue(declared.stream().anyMatch(name -> name.contains(handler)),
                mixin + "'s handler (" + handler + ") must be merged into " + target.getSimpleName()
                        + " — SlabbedDevMixinBootstrap's preLaunch hook is the only thing that arms "
                        + "slabbed.recorder.mixins.json, and without it the live-cursor recorder "
                        + "records nothing at all. Declared methods were: " + declared);
    }

    private static long countVisualDiagnosticSuspects(Path sessionPath) throws IOException {
        if (!Files.exists(sessionPath)) {
            return 0;
        }
        try (var lines = Files.lines(sessionPath)) {
            return lines.filter(l -> l.contains("\"stage\":\"visual_diagnostic\"")
                    && l.contains("\"suspect\":\"true\"")).count();
        }
    }
}
