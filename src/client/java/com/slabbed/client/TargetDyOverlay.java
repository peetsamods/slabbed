package com.slabbed.client;

import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;

/**
 * HUD readout of the crosshair-targeted block's source classification and visual dy.
 *
 * <p>Toggle in-game with {@code /slabdy}. Defaults on with
 * {@code -Dslabbed.targetDyOverlay=true}.
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
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        String namespace = id == null ? "?" : id.getNamespace();

        double dy = SlabSupport.getYOffset(client.world, pos, state);
        String status = dy < -1.0e-6 ? "LOWERED" : (dy > 1.0e-6 ? "RAISED" : "flush");

        CompatSlabSurfaceKind kind = CompatHooks.customSlabSurfaceKind(state);
        String source;
        if ("minecraft".equals(namespace)) {
            source = "VANILLA";
        } else if (kind != CompatSlabSurfaceKind.NONE) {
            source = "TERRAIN(" + kind + ")";
        } else {
            source = "MOD:" + namespace;
        }

        String line1 = "[slabdy] " + pos.toShortString() + "  " + state.getBlock().getName().getString();
        String line2 = "  " + source + " | dy=" + format(dy) + " " + status
                + " | side=" + blockHit.getSide().asString();
        int color = dy == 0.0d ? 0xffd7d7d7 : (dy < 0.0d ? 0xffffd166 : 0xffff8866);
        drawLine(context, client, line1, 8, 8, color);
        drawLine(context, client, line2, 8, 20, color);
    }

    private static void drawLine(DrawContext context, MinecraftClient client, String line, int x, int y, int color) {
        context.fill(x - 3, y - 3, x + client.textRenderer.getWidth(line) + 3, y + 11, 0x99000000);
        context.drawText(client.textRenderer, line, x, y, color, true);
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "NaN";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
