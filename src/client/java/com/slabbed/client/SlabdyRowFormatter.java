package com.slabbed.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
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
 * Full diagnostic-row formatter for {@code /slabdy row} and the passive {@code /slabdy} overlay
 * on the 1.21.1 Fabric branch.
 *
 * <p>Field-parity port of the 1.21.11 sibling's {@code com.slabbed.dev.SlabdyRowFormatter},
 * reconciled to THIS branch's independently-evolved dy system (see HANDOFF, 2026-07-05
 * "/slabdy field-parity reconciliation"). Package differs deliberately: on this branch
 * {@code build.gradle} strips {@code com/slabbed/dev/**} from the release jar wholesale, and
 * {@code /slabdy} must ship in every build (standing debug-tooling rule), so the always-shipped
 * formatter lives in {@code com.slabbed.client} rather than {@code com.slabbed.dev}. It references
 * only always-shipped types.
 *
 * <p>Kept free of any client-only type (no {@code MinecraftClient}) so the field computation is
 * headlessly testable: a gametest calls {@link #formatRow} directly with a synthetic
 * world/pos/state and asserts on the string. Only the wiring — reading the live crosshair target,
 * arming/reading the render-thread model trace, and printing to chat/HUD — is client-only and
 * therefore live-only (that lives in {@link SlabbedClient}).
 *
 * <p><b>Field reconciliation vs. the sibling</b> (added / already-equivalent / scoped-out):
 * <ul>
 *   <li>{@code owner}/{@code dy}/status/{@code src}/{@code face}/{@code half}/{@code hit}/
 *       {@code local}/{@code outline}/{@code outlineMinY}/{@code outlineMaxY}/{@code held}/
 *       {@code expectedPlace}/{@code below}: ADDED, using this branch's real methods
 *       ({@code SlabSupport.getYOffset} — this branch's single authoritative dy, the sibling's
 *       {@code getVisualYOffset} equivalent; {@code SlabAnchorAttachment.isFrozenFlat/isAnchored}).</li>
 *   <li>{@code modelTrace}/{@code modelTraceArmed}: ADDED via this branch's richer
 *       {@code OffsetBlockStateModel.RenderOffsetSample} (fields modelDy/clientDy/slabSupportDy/
 *       excludedByWrapper). NOTE: this branch only records that sample for {@code ChainBlock}
 *       under {@code -Dslabbed.render.offset.trace=true}, so it reads "missing" for other blocks —
 *       that is honest, not a gap.</li>
 *   <li>{@code cache:} line ({@code formatCacheComparison}): SCOPED OUT — NOT APPLICABLE. This
 *       branch has no client-side visual-dy cache (no {@code SlabSupport.CLIENT_VISUAL_Y_OFFSETS},
 *       no {@code peekCachedClientVisualYOffset}); {@code getYOffset} is computed live every read.
 *       The sibling's cache-vs-fresh staleness check has nothing to compare against here, so the
 *       line is omitted rather than faked. See {@link #cacheLineExplanation()}.</li>
 * </ul>
 */
public final class SlabdyRowFormatter {

    private SlabdyRowFormatter() {
    }

    /**
     * @param modelTraceLine     a pre-formatted model-trace line from the caller (client-only
     *                           {@code OffsetBlockStateModel.RenderOffsetSample}), or "missing"
     *                           if no trace was armed/seen for this position.
     * @param modelTraceArmedPos the position the caller just armed the trace for (echoed back so a
     *                           tester can tell whether the NEXT render frame's trace will
     *                           correspond to this dump), or null.
     */
    public static List<String> formatRow(BlockView world, BlockPos pos, BlockState state,
                                         Direction face, Vec3d hit, ItemStack held,
                                         String modelTraceLine, BlockPos modelTraceArmedPos) {
        String id = blockId(state);
        double dy = SlabSupport.getYOffset(world, pos, state);
        String status = dy < -1.0e-6 ? "LOWERED" : (dy > 1.0e-6 ? "RAISED" : "flush");
        String src = dySource(world, pos, dy);
        VoxelShape outline = outlineAt(world, pos, state);
        Box outlineBox = outline.isEmpty() ? null : outline.getBoundingBox();
        String half = targetHalf(hit, pos, outlineBox);
        Vec3d local = hit == null ? null : hit.subtract(pos.getX(), pos.getY(), pos.getZ());

        BlockPos placePos = face == null ? null : pos.offset(face);
        BlockState placeState = placePos == null ? null : world.getBlockState(placePos);

        BlockPos belowPos = pos.down();
        BlockState belowState = world.getBlockState(belowPos);
        double belowDy = SlabSupport.getYOffset(world, belowPos, belowState);
        String belowSrc = dySource(world, belowPos, belowDy);

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

    /**
     * Why the sibling's {@code cache:} line has no equivalent here. Exposed as a method (rather than
     * a buried comment) so a live tester who expects the sibling's cache row and doesn't see it gets
     * a documented reason, not silence. Not emitted into {@link #formatRow} — a permanently-constant
     * line would just be noise on every dump.
     */
    public static String cacheLineExplanation() {
        return "cache: n/a on 1.21.1 — no client-side visual-dy cache exists (getYOffset is computed "
                + "live per read; there is no CLIENT_VISUAL_Y_OFFSETS / peekCachedClientVisualYOffset "
                + "to compare a cached value against, so no cache-vs-fresh staleness check is possible)";
    }

    /**
     * On this branch {@code state.getOutlineShape(world, pos)} is ALREADY shifted by
     * {@code SlabSupport.getYOffset} via {@code SlabSupportStateMixin.slabbed$offsetOutline} (an
     * {@code @Inject at RETURN}) — that mixin is the reason the outline/hitbox tracks the model.
     * Applying {@code dy} to it again here would double-count the offset (the exact double-offset
     * bug the sibling's formatter was fixed for; kept correct-by-construction here by never
     * re-offsetting).
     */
    private static VoxelShape outlineAt(BlockView world, BlockPos pos, BlockState state) {
        return state.getOutlineShape(world, pos, ShapeContext.absent());
    }

    private static String targetHalf(Vec3d hit, BlockPos pos, Box outlineBox) {
        if (hit == null || outlineBox == null) {
            return "?";
        }
        // outlineBox is in the shape's own LOCAL coordinate frame (roughly 0..1, already shifted by
        // dy in outlineAt), NOT world-absolute — add pos.getY() once, don't subtract it back out.
        double middle = pos.getY() + (outlineBox.minY + outlineBox.maxY) / 2.0d;
        return hit.y >= middle ? "UPPER" : "LOWER";
    }

    /**
     * Best-effort classification of WHY this block has the dy it has. Mirrors the sibling's
     * dySource ladder (FROZEN-FLAT / ANCHORED / geometric / -) using this branch's actual anchor
     * methods.
     */
    private static String dySource(BlockView world, BlockPos pos, double dy) {
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
