package com.slabbed.client;

import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabdyRowFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/**
 * Dev HUD readout of the crosshair-targeted block: position, name, source
 * (vanilla / other mod), the visual dy from {@link SlabSupport#getYOffset},
 * LOWERED / RAISED / flush, hit side/half, the hit vector, the outline box,
 * the held item, the expected-placement neighbour, and the block below — the
 * per-field line-building is done headlessly in {@link SlabdyRowFormatter} (so
 * the exact strings shown here are gametest-assertable); this class only wires
 * the live crosshair target in and draws.
 *
 * <p>Rendered by {@code TargetDyHudMixin} at the tail of
 * {@code Gui.extractRenderState} (no Fabric rendering-API dependency, which
 * this port's non-standard Loom setup does not expose to the client source
 * set). 26.1.2 uses {@code GuiGraphicsExtractor} (the 26.x refactor of
 * {@code GuiGraphics}); text/fill are {@code text(...)} / {@code fill(...)}.
 *
 * <p>Off by default for player-facing builds; enable with
 * {@code /slabdev debug on} or {@code -Dslabbed.targetDyOverlay=true}.
 */
public final class TargetDyOverlay {

    private static boolean enabled = Boolean.getBoolean("slabbed.targetDyOverlay");

    private TargetDyOverlay() {
    }

    /** Flips the overlay; returns the new state. */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        TargetDyOverlay.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void render(GuiGraphicsExtractor context) {
        if (!enabled) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.font == null) {
            return;
        }
        HitResult target = client.hitResult;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            drawLine(context, client, "[slabdy] target: none", 8, 8, 0xffd7d7d7);
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.level.getBlockState(pos);
        ItemStack held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandItem();

        List<String> lines = SlabdyRowFormatter.formatRow(
                client.level, pos, state, blockHit.getDirection(), blockHit.getLocation(), held);

        // Colour keyed on the visual dy — yellow when lowered, orange when raised, grey when flush.
        double dy = SlabSupport.getYOffset(client.level, pos, state);
        int color = dy == 0.0d ? 0xffd7d7d7 : (dy < 0.0d ? 0xffffd166 : 0xffff8866);
        for (int i = 0; i < lines.size(); i++) {
            drawLine(context, client, lines.get(i), 8, 8 + (i * 12), color);
        }
    }

    private static void drawLine(GuiGraphicsExtractor context, Minecraft client, String line, int x, int y, int color) {
        context.fill(x - 3, y - 3, x + client.font.width(line) + 3, y + 11, 0x99000000);
        context.text(client.font, line, x, y, color, true);
    }
}
