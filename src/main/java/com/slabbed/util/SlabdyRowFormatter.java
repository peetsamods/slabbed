package com.slabbed.util;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Headless field-computation for the {@code /slabdev debug} target-dy diagnostic view. Kept free of any client-only type (no
 * {@code Minecraft}) so the exact strings a player sees are the exact strings a gametest can
 * assert: a test calls {@link #formatRow} directly with a synthetic world/pos/state and checks the
 * returned lines. Only the wiring — reading the live crosshair target and printing to the HUD — is
 * client-only and therefore lives in the diagnostic view.
 *
 * <p>Field set reconciled toward the 1.21.11 sibling's canonical {@code SlabdyRowFormatter}
 * (Phase 6, the "reconcile don't replace" pass), keeping only the fields whose underlying API exists
 * on 26.2. NOT ported wholesale: the sibling's {@code modelTrace=} line (no {@code RenderOffsetTrace}
 * API here), {@code cache:} line (no client visual-dy cache here), and {@code FLAGS:} diagnostics
 * line (no {@code SlabbedDiagnostics} here) have no 26.2 analogue and are deliberately absent. This
 * branch's {@code src=} ladder keeps its extra {@code compound-side} tier, which the sibling lacks.
 *
 * <p>Lives in a SHIPPING package ({@code com.slabbed.util}) as shared headless logic in production
 * output; the dev-only diagnostic view consumes it only in development and GameTest runtimes.
 */
public final class SlabdyRowFormatter {

    private static final double EPS = 1.0e-6;

    private SlabdyRowFormatter() {
    }

    /**
     * @param world the block view (client {@code Level} live, or a {@code ServerLevel} in a gametest)
     * @param pos   the crosshair-targeted block position
     * @param state the block state at {@code pos}
     * @param face  the hit face, or {@code null} if unknown
     * @param hit   the world-space hit location, or {@code null} if unknown
     * @param held  the player's main-hand stack, or {@code null}/empty if none
     * @return the multi-line diagnostic dump (the HUD draws one line per element)
     */
    public static List<String> formatRow(BlockGetter world, BlockPos pos, BlockState state,
                                         Direction face, Vec3 hit, ItemStack held) {
        String id = blockId(state);
        double dy = SlabSupport.getYOffset(world, pos, state);
        String status = dy < -EPS ? "LOWERED" : (dy > EPS ? "RAISED" : "flush");
        String src = dySource(world, pos, state, dy);

        VoxelShape outline = state.getShape(world, pos);
        AABB outlineBox = outline.isEmpty() ? null : outline.bounds();
        String half = targetHalf(hit, pos, outlineBox);
        Vec3 local = hit == null ? null : hit.subtract(pos.getX(), pos.getY(), pos.getZ());

        BlockPos placePos = face == null ? null : pos.relative(face);
        BlockState placeState = placePos == null ? null : world.getBlockState(placePos);

        BlockPos belowPos = pos.below();
        BlockState belowState = world.getBlockState(belowPos);
        double belowDy = SlabSupport.getYOffset(world, belowPos, belowState);
        String belowSrc = dySource(world, belowPos, belowState, belowDy);

        List<String> lines = new ArrayList<>();
        lines.add("[slabdy] target=" + pos.toShortString() + " " + id);
        lines.add("  " + sourceLabel(id)
                + " * dy=" + format(dy) + " " + status
                + " * src=" + src);
        lines.add("  face=" + (face == null ? "none" : face.getName())
                + " * half=" + half
                + " * hit=" + formatVec(hit)
                + " * local=" + formatVec(local));
        lines.add("  outline=" + formatBox(outlineBox)
                + " * outlineMinY=" + (outlineBox == null ? "NaN" : format(outlineBox.minY))
                + " * outlineMaxY=" + (outlineBox == null ? "NaN" : format(outlineBox.maxY)));
        lines.add("  held=" + itemId(held)
                + " * expectedPlace=" + (placePos == null ? "none" : placePos.toShortString())
                + " " + (placeState == null ? "?" : blockId(placeState)));
        lines.add("  below=" + belowPos.toShortString() + " " + blockId(belowState)
                + " * dy=" + format(belowDy)
                + " * src=" + belowSrc);
        return lines;
    }

    private static String targetHalf(Vec3 hit, BlockPos pos, AABB outlineBox) {
        if (hit == null || outlineBox == null) {
            return "?";
        }
        // outlineBox is already shifted by dy (SlabSupportStateMixin offsets the shape) and is in the
        // shape's local 0..1 frame — add pos.getY() once to compare against the world-space hit.
        double middle = pos.getY() + (outlineBox.minY + outlineBox.maxY) / 2.0d;
        return hit.y >= middle ? "UPPER" : "LOWER";
    }

    /**
     * Why does this block have the dy it has? FROZEN-FLAT / ANCHORED / compound-side / geometric / -.
     * The {@code compound-side} tier (via {@code isCompoundVisibleSide*}) is a 26.2-specific extra the
     * 1.21.11 sibling's formatter does not have — kept.
     */
    private static String dySource(BlockGetter world, BlockPos pos, BlockState state, double dy) {
        if (SlabAnchorAttachment.isFrozenFlat(world, pos)) {
            return "FROZEN-FLAT";
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return "ANCHORED";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return "compound-side";
        }
        return (dy < -EPS || dy > EPS) ? "geometric" : "-";
    }

    private static String blockId(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
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
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "?" : id.toString();
    }

    private static String formatVec(Vec3 vec) {
        if (vec == null) {
            return "none";
        }
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f", vec.x, vec.y, vec.z);
    }

    private static String formatBox(AABB box) {
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
