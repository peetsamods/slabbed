package com.slabbed.client;

import com.mojang.brigadier.context.CommandContext;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.dev.SlabbedDiagnostics;
import com.slabbed.dev.SlabdyRowFormatter;
import com.slabbed.util.SlabbedAuditBridge;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * {@code /slabdy} — re-derived from Forge 1.20.1's {@code TargetDyOverlay} (chosen as the
 * reference by maintainer ruling), not ported: Forge's
 * event bus / GuiGraphics / KeyMapping.click() have no Fabric equivalent, so this is a
 * fresh implementation of the same behavior using Fabric's client-command and
 * HudRenderCallback APIs.
 *
 * <p>Always present in every build (see build.gradle — com/slabbed/dev/** and this class
 * are never excluded). No ambient cost when unused: the HUD overlay only draws when
 * toggled on by typing the bare {@code /slabdy}, and {@code row}/{@code use} only run
 * when explicitly invoked.
 *
 * <ul>
 *   <li>passive: the on-screen target-dy overlay is available in the UL corner and updates
 *       live as the crosshair moves when enabled.</li>
 *   <li>{@code /slabdy} — toggle that overlay off/on.</li>
 *   <li>{@code /slabdy row} — print the current target's full diagnostic dump to chat.</li>
 *   <li>{@code /slabdy use} — perform a real use/place against the current target and
 *       report the block before/after at the expected placement position.</li>
 *   <li>{@code /slabdy record} — toggle the existing, far more capable append-only
 *       placement/targeting recorder. {@code /slabdy record on} and {@code off} set an
 *       explicit state for a test session.</li>
 *   <li>Recorder commands explain when the current build intentionally lacks the dev-only
 *       recorder instead of reporting a misleading disabled state.</li>
 *   <li>The recorder is accessed via {@link SlabbedAuditBridge} reflection because it is a
 *       dev-only tool excluded from the release jar — see build.gradle's pre-release hygiene
 *       gate.</li>
 * </ul>
 */
public final class SlabdyClientCommands {

    // Defaults OFF for a release build: /slabdy always SHIPS (per the standing debug-tooling
    // rule — it must never be compiled out), but a player shouldn't see a diagnostic overlay
    // in the corner of their screen unless they explicitly asked for it via bare `/slabdy`.
    // Force it on for a dev/test session with -Dslabbed.targetDyOverlay=true.
    private static boolean overlayEnabled =
            Boolean.parseBoolean(System.getProperty("slabbed.targetDyOverlay", "false"));

    private SlabdyClientCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        literal("slabdy")
                                .executes(SlabdyClientCommands::toggleOverlay)
                                .then(literal("row").executes(SlabdyClientCommands::runRow))
                                .then(literal("use").executes(SlabdyClientCommands::runUse))
                                .then(literal("record")
                                        .executes(SlabdyClientCommands::runRecord)
                                        .then(literal("on").executes(SlabdyClientCommands::runRecordOn))
                                        .then(literal("off").executes(SlabdyClientCommands::runRecordOff)))));
        // HudRenderCallback is @Deprecated (and dead) in fabric-rendering-v1 16.x — the reason
        // the overlay never showed. Register through the current HudElementRegistry instead;
        // addLast draws our element after all vanilla HUD elements (on top), every frame.
        HudElementRegistry.addLast(
                Identifier.of("slabbed", "target_dy_overlay"),
                SlabdyClientCommands::renderOverlay);
        // While the recorder is enabled, capture a full SlabbedDiagnostics sample every time
        // the crosshair target changes — deduped so look-drift doesn't spam the log. This is
        // the enriched capture (visual triad + DODO/smoosh/gap/triad-mismatch flags), and it
        // fires independently of whether the overlay is drawn.
        ClientTickEvents.END_CLIENT_TICK.register(SlabdyClientCommands::maybeRecordTargetDiagnostic);
    }

    private static String lastRecordedSignature = "";

    private static void maybeRecordTargetDiagnostic(MinecraftClient client) {
        if (!SlabbedAuditBridge.isRecorderEnabled()) {
            return;
        }
        if (client == null || client.world == null) {
            return;
        }
        BlockHitResult blockHit = currentBlockTarget(client);
        if (blockHit == null) {
            if (!"none".equals(lastRecordedSignature)) {
                lastRecordedSignature = "none";
            }
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        double modelDy = modelDyFor(pos);
        String sig = pos.toShortString() + "|" + state + "|" + SlabbedDiagnostics.format(modelDy);
        if (sig.equals(lastRecordedSignature)) {
            return;
        }
        lastRecordedSignature = sig;
        SlabbedDiagnostics.Sample sample = SlabbedDiagnostics.analyze(client.world, pos, state, modelDy);
        SlabbedAuditBridge.recordVisualDiagnostic(pos, sample);

        // The model leg is the only one that cannot be read on demand: quads are emitted when a
        // chunk section is meshed, not per frame, so a static cell holds no sample. Arm the capture
        // for this cell and ask for a re-mesh; the value lands on a later tick, and because the
        // dedupe signature above includes the model dy, that tick emits a SECOND row for the same
        // cell carrying the real number. Only arm when we still have nothing — once the number is
        // in hand the signature stops changing and no further re-mesh is requested.
        if (!SlabbedDiagnostics.isMeasured(modelDy)) {
            OffsetBlockStateModel.resetRenderOffsetTrace(pos);
            client.worldRenderer.scheduleBlockRenders(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ());
        }
    }

    /**
     * The render-path model dy for pos if a capture was armed for it and a chunk-section mesh has
     * happened since, else {@link SlabbedDiagnostics#NOT_SAMPLED} — which is a DIFFERENT token from
     * the {@code EMPTY} a shape leg reports, so a reader can tell the two apart.
     */
    private static double modelDyFor(BlockPos pos) {
        OffsetBlockStateModel.RenderOffsetTrace trace = OffsetBlockStateModel.snapshotRenderOffsetTrace();
        if (trace != null && trace.seen() && pos.toShortString().equals(trace.pos())) {
            return trace.modelDy();
        }
        return SlabbedDiagnostics.NOT_SAMPLED;
    }

    private static int toggleOverlay(CommandContext<FabricClientCommandSource> ctx) {
        overlayEnabled = !overlayEnabled;
        ctx.getSource().sendFeedback(Text.literal("Slabbed target dy overlay: " + (overlayEnabled ? "on" : "off")));
        return 1;
    }

    private static int runRow(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            ctx.getSource().sendError(Text.literal("[slabdy row] no client world"));
            return 0;
        }
        BlockHitResult blockHit = currentBlockTarget(client);
        if (blockHit == null) {
            ctx.getSource().sendError(Text.literal("[slabdy row] target is not a block"));
            return 0;
        }
        List<String> lines = buildRow(client, blockHit, true);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        ctx.getSource().sendFeedback(Text.literal(sb.toString().stripTrailing()));
        return 1;
    }

    private static int runUse(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null || client.interactionManager == null) {
            ctx.getSource().sendError(Text.literal("[slabdy use] no client world"));
            return 0;
        }
        BlockHitResult blockHit = currentBlockTarget(client);
        if (blockHit == null) {
            ctx.getSource().sendError(Text.literal("[slabdy use] target is not a block"));
            return 0;
        }
        BlockPos targetPos = blockHit.getBlockPos();
        BlockPos expectedPlacePos = targetPos.offset(blockHit.getSide());
        String before = blockId(client.world.getBlockState(expectedPlacePos));

        ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, blockHit);

        String after = blockId(client.world.getBlockState(expectedPlacePos));
        ctx.getSource().sendFeedback(Text.literal("[slabdy use] target=" + targetPos.toShortString()
                + " face=" + blockHit.getSide()
                + " expectedPlace=" + expectedPlacePos.toShortString()
                + " before=" + before
                + " after=" + after
                + " result=" + result));
        return 1;
    }

    private static int runRecord(CommandContext<FabricClientCommandSource> ctx) {
        if (!SlabbedAuditBridge.isRecorderAvailable()) {
            return recorderUnavailable(ctx);
        }
        return reportRecorderState(ctx, SlabbedAuditBridge.toggleRecorder());
    }

    private static int runRecordOn(CommandContext<FabricClientCommandSource> ctx) {
        return setRecorderState(ctx, true);
    }

    private static int runRecordOff(CommandContext<FabricClientCommandSource> ctx) {
        return setRecorderState(ctx, false);
    }

    private static int setRecorderState(CommandContext<FabricClientCommandSource> ctx, boolean enabled) {
        if (!SlabbedAuditBridge.isRecorderAvailable()) {
            return recorderUnavailable(ctx);
        }
        boolean nowOn = SlabbedAuditBridge.setRecorderEnabled(enabled);
        if (nowOn != enabled) {
            ctx.getSource().sendError(Text.literal("Slabbed recorder could not be set "
                    + (enabled ? "on" : "off")));
            return 0;
        }
        return reportRecorderState(ctx, nowOn);
    }

    private static int recorderUnavailable(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendError(Text.literal(
                "Slabbed recorder is unavailable in this release build; use the branch dev client for recording."));
        return 0;
    }

    private static int reportRecorderState(CommandContext<FabricClientCommandSource> ctx, boolean nowOn) {
        ctx.getSource().sendFeedback(Text.literal("Slabbed recorder: "
                + (nowOn ? "on -> " + SlabbedAuditBridge.recorderLogPathDisplay() : "off")));
        return 1;
    }

    private static void renderOverlay(DrawContext context, RenderTickCounter tickCounter) {
        if (!overlayEnabled) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        BlockHitResult blockHit = currentBlockTarget(client);
        if (blockHit == null) {
            drawLine(context, client, "[slabdy] target: none", 8, 8, 0xffd7d7d7);
            return;
        }
        List<String> lines = buildRow(client, blockHit, false);
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        double dy = com.slabbed.util.SlabSupport.getVisualYOffset(client.world, pos, state);
        int color = dy == 0.0d ? 0xffd7d7d7 : (dy < 0.0d ? 0xffffd166 : 0xffff8866);
        for (int i = 0; i < lines.size(); i++) {
            drawLine(context, client, lines.get(i), 8, 8 + (i * 12), color);
        }
        // Surface the SlabbedDiagnostics red flags live: a bright warning line under the row
        // whenever the current target trips a DODO / smoosh / gap / triad-mismatch check.
        SlabbedDiagnostics.Sample s = SlabbedDiagnostics.analyze(client.world, pos, state, modelDyFor(pos));
        int row = lines.size();
        if (s.anySuspect()) {
            drawLine(context, client, "[slabdy] FLAGS: " + s.flagSummary(), 8, 8 + (row++ * 12), 0xffff4d4d);
        }
        // ALWAYS drawn, suspect or not. A clean triad line is only reassuring if the tester can
        // see how many legs it actually covered — "no flag" over one verified leg is the exact
        // misreading this line exists to prevent.
        drawLine(context, client, "[slabdy] triad: " + s.triadLegsVerified() + "/3 verified * "
                        + s.triadCoverage(),
                8, 8 + (row * 12), s.triadLegsVerified() == 3 ? 0xff8fd694 : 0xffd7a15a);
    }

    private static List<String> buildRow(MinecraftClient client, BlockHitResult blockHit, boolean armModelTrace) {
        BlockPos pos = blockHit.getBlockPos();
        var state = client.world.getBlockState(pos);
        ItemStack held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandStack();

        // Snapshot whatever trace was captured on a PRIOR render frame (if any) before
        // (re-)arming it — the trace this call arms will only be fresh on the NEXT frame,
        // exactly mirroring the Forge original's two-call arm/read cycle.
        OffsetBlockStateModel.RenderOffsetTrace trace = OffsetBlockStateModel.snapshotRenderOffsetTrace();
        String modelTraceLine = formatModelTrace(pos, trace);
        BlockPos armedPos = null;
        if (armModelTrace) {
            OffsetBlockStateModel.resetRenderOffsetTrace(pos);
            client.worldRenderer.scheduleBlockRenders(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ());
            armedPos = pos;
        }

        return SlabdyRowFormatter.formatRow(
                client.world, pos, state, blockHit.getSide(), blockHit.getPos(), held,
                modelTraceLine, armedPos);
    }

    private static String formatModelTrace(BlockPos pos, OffsetBlockStateModel.RenderOffsetTrace trace) {
        if (trace == null || !trace.seen() || !pos.toShortString().equals(trace.pos())) {
            return "missing";
        }
        return "seen"
                + " view=" + trace.viewClass()
                + " modelDy=" + String.format(java.util.Locale.ROOT, "%.3f", trace.modelDy())
                + " clientDy=" + String.format(java.util.Locale.ROOT, "%.3f", trace.clientDy())
                + " slabSupportDy=" + String.format(java.util.Locale.ROOT, "%.3f", trace.slabSupportDy())
                + " excludedByWrapper=" + trace.excludedByWrapper();
    }

    private static BlockHitResult currentBlockTarget(MinecraftClient client) {
        HitResult target = client.crosshairTarget;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return blockHit;
    }

    private static void drawLine(DrawContext context, MinecraftClient client, String line, int x, int y, int color) {
        context.fill(x - 3, y - 3, x + client.textRenderer.getWidth(line) + 3, y + 11, 0x99000000);
        context.drawText(client.textRenderer, line, x, y, color, true);
    }

    private static String blockId(net.minecraft.block.BlockState state) {
        var id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
        return id == null ? "?" : id.toString();
    }
}
