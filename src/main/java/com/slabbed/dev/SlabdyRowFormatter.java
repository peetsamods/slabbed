package com.slabbed.dev;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Re-derived (not ported — Forge 1.20.1 vs Fabric 1.21.11 have nothing in common at the
 * API level) from {@code TargetDyOverlay.targetLines} on
 * codex/forge-1.20.1-backport-from-neoforge-042-beta2, which Maintainer pointed at as "the
 * slabdy debug info that is helpful" after main's terser first cut fell short.
 *
 * <p>Kept free of any client-only type (no {@code MinecraftClient}) so the field
 * computation is headlessly testable: a gametest can call {@link #formatRow} directly
 * with a synthetic world/pos/state and assert on the string. Only the wiring — reading
 * the live crosshair target, arming/reading the render-thread model trace, and printing
 * to chat/HUD — is client-only and therefore live-only.
 */
public final class SlabdyRowFormatter {

    private SlabdyRowFormatter() {
    }

    /**
     * @param modelTraceLine  a pre-formatted model-trace line from the caller (client-only
     *                        {@code OffsetBlockStateModel.RenderOffsetTrace}), or "missing"
     *                        if no trace was armed for this position.
     * @param modelTraceArmedPos  the position the caller just armed the trace for (echoed
     *                            back so a tester can tell whether the NEXT render frame's
     *                            trace will actually correspond to this dump), or null.
     */
    public static List<String> formatRow(BlockView world, BlockPos pos, BlockState state,
                                          Direction face, Vec3d hit, ItemStack held,
                                          String modelTraceLine, BlockPos modelTraceArmedPos) {
        String id = blockId(state);
        double dy = SlabSupport.getVisualYOffset(world, pos, state);
        String status = dy < -1.0e-6 ? "LOWERED" : (dy > 1.0e-6 ? "RAISED" : "flush");
        String src = dySource(world, pos, state, dy);
        VoxelShape outline = outlineAt(world, pos, state, dy);
        Box outlineBox = outline.isEmpty() ? null : outline.getBoundingBox();
        String half = targetHalf(hit, pos, outlineBox);
        Vec3d local = hit == null ? null : hit.subtract(pos.getX(), pos.getY(), pos.getZ());

        BlockPos placePos = face == null ? null : pos.offset(face);
        BlockState placeState = placePos == null ? null : world.getBlockState(placePos);

        BlockPos belowPos = pos.down();
        BlockState belowState = world.getBlockState(belowPos);
        double belowDy = SlabSupport.getVisualYOffset(world, belowPos, belowState);
        String belowSrc = dySource(world, belowPos, belowState, belowDy);

        List<String> lines = new ArrayList<>();
        lines.add("[slabdy] target=" + pos.toShortString() + " " + id);
        lines.add("  owner=" + pos.toShortString() + " * " + sourceLabel(id)
                + " * dy=" + format(dy) + " " + status
                + " * src=" + src);
        lines.add("  face=" + (face == null ? "none" : face)
                + " * half=" + half
                + " * hit=" + formatVec(hit)
                + " * local=" + formatVec(local));
        lines.add("  outline=" + formatBox(outlineBox)
                + " * outlineMinY=" + (outlineBox == null ? "NaN" : format(outlineBox.minY))
                + " * outlineMaxY=" + (outlineBox == null ? "NaN" : format(outlineBox.maxY)));
        lines.add("  modelTrace=" + modelTraceLine
                + " * modelTraceArmed=" + (modelTraceArmedPos == null ? "-" : modelTraceArmedPos.toShortString()));
        lines.add("  held=" + itemId(held)
                + " * expectedPlace=" + (placePos == null ? "none" : placePos.toShortString())
                + " " + (placeState == null ? "?" : blockId(placeState)));
        lines.add("  below=" + belowPos.toShortString() + " " + blockId(belowState)
                + " * dy=" + format(belowDy)
                + " * src=" + belowSrc);
        return lines;
    }

    private static VoxelShape outlineAt(BlockView world, BlockPos pos, BlockState state, double dy) {
        VoxelShape outline = state.getOutlineShape(world, pos);
        if (dy != 0.0d && !outline.isEmpty()) {
            outline = outline.offset(0.0d, dy, 0.0d);
        }
        return outline;
    }

    private static String targetHalf(Vec3d hit, BlockPos pos, Box outlineBox) {
        if (hit == null || outlineBox == null) {
            return "?";
        }
        // outlineBox is in the shape's own LOCAL coordinate frame (roughly 0..1, already
        // shifted by dy in outlineAt), NOT world-absolute — add pos.getY() once, don't
        // subtract it back out first.
        double middle = pos.getY() + (outlineBox.minY + outlineBox.maxY) / 2.0d;
        return hit.y >= middle ? "UPPER" : "LOWER";
    }

    /**
     * Best-effort classification of WHY this block has the dy it has. Mirrors the Forge
     * dySource ladder (FROZEN-FLAT / ANCHORED / geometric / -) using main's actual anchor
     * methods; main has no compound-visible-side classification yet (a Forge-only feature),
     * so that tier is simply absent here rather than faked.
     */
    private static String dySource(BlockView world, BlockPos pos, BlockState state, double dy) {
        if (SlabAnchorAttachment.isFrozenFlat(world, pos)) {
            return "FROZEN-FLAT";
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return "ANCHORED";
        }
        return (dy < -1.0e-6 || dy > 1.0e-6) ? "geometric" : "-";
    }

    private static String blockId(BlockState state) {
        var id = Registries.BLOCK.getId(state.getBlock());
        return id == null ? "?" : id.toString();
    }

    private static String sourceLabel(String blockId) {
        int separator = blockId.indexOf(':');
        String namespace = separator >= 0 ? blockId.substring(0, separator) : blockId;
        return "minecraft".equals(namespace) ? "VANILLA" : "MOD:" + namespace;
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        var id = Registries.ITEM.getId(stack.getItem());
        return id == null ? "?" : id.toString();
    }

    private static String formatVec(Vec3d vec) {
        if (vec == null) {
            return "none";
        }
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f", vec.x, vec.y, vec.z);
    }

    private static String formatBox(Box box) {
        if (box == null) {
            return "empty";
        }
        return String.format(Locale.ROOT, "[%.3f,%.3f,%.3f -> %.3f,%.3f,%.3f]",
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "NaN";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
