package com.slabbed.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Locale;

/**
 * Dev HUD readout of the crosshair-targeted block: position, name, source
 * (vanilla / other mod), the visual dy from {@link SlabSupport#getYOffset},
 * and LOWERED / RAISED / flush + hit side.
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

    private static boolean enabled = SlabbedClientFlags.TARGET_DY_OVERLAY;

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
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String namespace = id == null ? "?" : id.getNamespace();

        double dy = SlabSupport.getYOffset(client.level, pos, state);
        String status = dy < -1.0e-6 ? "LOWERED" : (dy > 1.0e-6 ? "RAISED" : "flush");
        String source = "minecraft".equals(namespace) ? "VANILLA" : "MOD:" + namespace;

        // Which half of the targeted block the crosshair is on, relative to its VISUAL shape
        // (the lowered/offset outline). Drives slab top/bottom placement.
        String half = "?";
        VoxelShape outline = state.getShape(client.level, pos);
        if (!outline.isEmpty()) {
            double mid = pos.getY() + (outline.min(Direction.Axis.Y) + outline.max(Direction.Axis.Y)) / 2.0;
            half = blockHit.getLocation().y >= mid ? "UPPER" : "LOWER";
        }

        // Why is the block at this dy? — distinguishes a frozen/anchored placement from live
        // geometric/compound-side lowering (the snapping we are diagnosing).
        String why;
        if (SlabAnchorAttachment.isFrozenFlat(client.level, pos)) {
            why = "FROZEN-FLAT";
        } else if (SlabAnchorAttachment.isAnchored(client.level, pos)) {
            why = "ANCHORED";
        } else if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(client.level, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(client.level, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(client.level, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(client.level, pos, state)) {
            why = "compound-side";
        } else {
            why = (dy < -1.0e-6 || dy > 1.0e-6) ? "geometric" : "-";
        }

        String line1 = "[slabdy] " + pos.toShortString() + "  " + state.getBlock().getName().getString();
        String line2 = "  " + source + " · dy=" + format(dy) + " " + status
                + " · side=" + blockHit.getDirection().getName() + " · half=" + half;
        String line3 = "  src=" + why;
        int color = dy == 0.0d ? 0xffd7d7d7 : (dy < 0.0d ? 0xffffd166 : 0xffff8866);
        drawLine(context, client, line1, 8, 8, color);
        drawLine(context, client, line2, 8, 20, color);
        drawLine(context, client, line3, 8, 32, color);
    }

    private static void drawLine(GuiGraphicsExtractor context, Minecraft client, String line, int x, int y, int color) {
        context.fill(x - 3, y - 3, x + client.font.width(line) + 3, y + 11, 0x99000000);
        context.text(client.font, line, x, y, color, true);
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "NaN";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
