package com.slabbed.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Passive {@code /slabdy} target-dy overlay for MC 1.21.1 (Fabric).
 *
 * <p>Renders through {@link HudRenderCallback} — the correct HUD-registration API for THIS
 * branch's {@code fabric-rendering-v1} version, which does NOT contain 1.21.11's
 * {@code HudElementRegistry}. Do not port the sibling's registration approach here (see HANDOFF
 * 2026-07-05 /slabdy field-parity notes).
 *
 * <p>Field-parity upgrade (2026-07-05): the overlay was previously a single terse line
 * ({@code pos name side dy}). It now renders the full {@link SlabdyRowFormatter} multi-line dump
 * (owner/dy/src/face/half/hit/local/outline/held/expectedPlace/below + the model-trace line) and,
 * when the dev-only {@code SlabbedDiagnostics} analysis layer is present (reached release-safely
 * via {@link com.slabbed.util.RuntimeDiagnostics}), a bright FLAGS line whenever the current target
 * trips a DODO / smoosh / gap / triad-mismatch check.
 *
 * <p>Default OFF (see {@link SlabbedClientFlags#TARGET_DY_OVERLAY}); toggled on by bare
 * {@code /slabdy}. Dev sessions can force it on with {@code -Dslabbed.targetDyOverlay=true}
 * (wired into {@code build.gradle}'s {@code runClient} vmArgs).
 */
public final class TargetDyOverlay {
    private static boolean initialized;
    private static boolean enabled = SlabbedClientFlags.TARGET_DY_OVERLAY;

    private TargetDyOverlay() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        HudRenderCallback.EVENT.register(TargetDyOverlay::render);
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!enabled) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.textRenderer == null) {
            return;
        }
        HitResult target = client.crosshairTarget;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            drawLine(context, client, "[slabdy] target: none", 8, 8, 0xffd7d7d7);
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        double dy = com.slabbed.util.SlabSupport.getYOffset(client.world, pos, state);
        int color = dy == 0.0d ? 0xffd7d7d7 : (dy < 0.0d ? 0xffffd166 : 0xffff8866);

        List<String> lines = SlabdyDiagnosticSupport.buildRow(client, blockHit, false);
        for (int i = 0; i < lines.size(); i++) {
            drawLine(context, client, lines.get(i), 8, 8 + (i * 12), color);
        }
        // Live red-flag line from the dev-only SlabbedDiagnostics layer, reached release-safely.
        String flags = SlabdyDiagnosticSupport.suspectFlagLine(client, pos, state);
        if (flags != null) {
            drawLine(context, client, flags, 8, 8 + (lines.size() * 12), 0xffff4d4d);
        }
    }

    private static void drawLine(
            DrawContext context,
            MinecraftClient client,
            String line,
            int x,
            int y,
            int color
    ) {
        context.fill(x - 3, y - 3, x + client.textRenderer.getWidth(line) + 3, y + 11, 0x99000000);
        context.drawText(client.textRenderer, line, x, y, color, true);
    }
}
