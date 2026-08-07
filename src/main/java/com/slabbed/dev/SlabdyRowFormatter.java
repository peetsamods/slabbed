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
        VoxelShape outline = outlineAt(world, pos, state);
        Box outlineBox = outline.isEmpty() ? null : outline.getBoundingBox();
        String half = targetHalf(hit, pos, outlineBox);
        Vec3d local = hit == null ? null : hit.subtract(pos.getX(), pos.getY(), pos.getZ());

        BlockPos placePos = face == null ? null : pos.offset(face);
        BlockState placeState = placePos == null ? null : world.getBlockState(placePos);

        BlockPos belowPos = pos.down();
        BlockState belowState = world.getBlockState(belowPos);
        double belowDy = SlabSupport.getVisualYOffset(world, belowPos, belowState);
        String belowSrc = dySource(world, belowPos, belowState, belowDy);

        // Cache-vs-fresh diagnostic (2026-07-04, live-reported hanging-lantern gap where a
        // support's dy differed depending on who read it): the client-side visual-dy cache
        // (SlabSupport.CLIENT_VISUAL_Y_OFFSETS) is what the chunk-mesh render worker prefers
        // instead of recomputing. If a cached value disagrees with a fresh recompute, the
        // render worker may be baking a stale mesh even though every live query looks correct.
        // Checked for BOTH the target and the block above it (the common "hanger reads its
        // support" relationship) so a tester can point at either half of the pair.
        BlockPos abovePos = pos.up();
        BlockState aboveState = world.getBlockState(abovePos);
        String cacheLine = "  cache: target=" + formatCacheComparison(world, pos, state)
                + " | above=" + formatCacheComparison(world, abovePos, aboveState);

        List<String> lines = new ArrayList<>();
        lines.add("[slabdy] target=" + pos.toShortString() + " " + id);
        lines.add("  owner=" + pos.toShortString() + " * " + sourceLabel(id)
                + " * dy=" + format(dy) + " " + status
                + " * src=" + src);
        lines.add("  face=" + (face == null ? "none" : face)
                + " * half=" + half
                + " * hit=" + formatVec(hit)
                + " * local=" + formatVec(local));
        // A null box here means the outline shape was asked and answered EMPTY — a measurement,
        // not a missing reading. Printed with the same word SlabbedDiagnostics uses so the two
        // readouts cannot be confused with each other or with an unsampled leg.
        lines.add("  outline=" + formatBox(outlineBox)
                + " * outlineMinY=" + (outlineBox == null ? "EMPTY" : format(outlineBox.minY))
                + " * outlineMaxY=" + (outlineBox == null ? "EMPTY" : format(outlineBox.maxY)));
        lines.add("  modelTrace=" + modelTraceLine
                + " * modelTraceArmed=" + (modelTraceArmedPos == null ? "-" : modelTraceArmedPos.toShortString()));
        lines.add("  held=" + itemId(held)
                + " * expectedPlace=" + (placePos == null ? "none" : placePos.toShortString())
                + " " + (placeState == null ? "?" : blockId(placeState)));
        lines.add("  below=" + belowPos.toShortString() + " " + blockId(belowState)
                + " * dy=" + format(belowDy)
                + " * src=" + belowSrc);
        lines.add(cacheLine);
        return lines;
    }

    /**
     * "pos(block) cache=<value or MISS> fresh=<value>", with "*** STALE ***" appended when a
     * cache entry exists and disagrees with a fresh {@link SlabSupport#getYOffset} recompute by
     * more than epsilon. {@code getYOffset} never itself consults the cache, so it is a true
     * "what would this recompute to right now" baseline to compare the cached value against.
     */
    private static String formatCacheComparison(BlockView world, BlockPos pos, BlockState state) {
        Double cached = SlabSupport.peekCachedClientVisualYOffset(pos);
        double fresh = SlabSupport.getYOffset(world, pos, state);
        String cachedStr = cached == null ? "MISS" : format(cached);
        boolean stale = cached != null && Math.abs(cached - fresh) > 1.0e-6;
        return pos.toShortString() + "(" + blockId(state) + ") cache=" + cachedStr
                + " fresh=" + format(fresh) + (stale ? " *** STALE ***" : "");
    }


    /**
     * {@code state.getOutlineShape(world, pos)} is ALREADY shifted by {@code getVisualYOffset}
     * via {@code SlabSupportStateMixin.slabbed$offsetOutline} (an {@code @Inject at RETURN}) —
     * that mixin is the entire reason the outline/hitbox tracks the model. Applying {@code dy}
     * to it again here double-counts the offset (a real bug this session, found via a live
     * report of an "internally inconsistent" block: dy=-0.500 in text, outline shifted -1.000).
     */
    private static VoxelShape outlineAt(BlockView world, BlockPos pos, BlockState state) {
        return state.getOutlineShape(world, pos);
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
            return SlabbedDiagnostics.format(value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
