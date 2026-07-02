package com.slabbed.dev;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

/**
 * Pure formatter for {@code /slabdy row} — the single-line current-target dump.
 *
 * <p>Kept free of any client-only type (no {@code MinecraftClient}, no
 * {@code FabricClientCommandSource}) so the field computation is headlessly testable:
 * a gametest can call {@link #formatRow} directly with a synthetic world/pos/state and
 * assert on the string, without needing a real client crosshair. Only the wiring —
 * reading the live crosshair target and printing to chat — is client-only and therefore
 * live-only.
 */
public final class SlabdyRowFormatter {

    private SlabdyRowFormatter() {
    }

    public static String formatRow(BlockView world, BlockPos pos, BlockState state,
                                    Direction face, Vec3d hitVec, ItemStack heldItem) {
        double worldDy = SlabSupport.getYOffset(world, pos, state);
        double visualDy = SlabSupport.getVisualYOffset(world, pos, state);
        String source = supportSource(world, pos, state);
        return String.format(
                "pos=%s state=%s support=%s worldDy=%.3f visualDy=%.3f face=%s hit=%s held=%s",
                pos.toShortString(),
                state.toString(),
                source,
                worldDy,
                visualDy,
                face,
                formatHitVec(hitVec),
                heldItem == null || heldItem.isEmpty() ? "empty" : heldItem.getItem().toString());
    }

    private static String formatHitVec(Vec3d hitVec) {
        if (hitVec == null) {
            return "none";
        }
        return String.format("%.3f,%.3f,%.3f", hitVec.x, hitVec.y, hitVec.z);
    }

    /**
     * Best-effort classification of WHY this block has the dy it has, for diagnostic
     * readability. Not exhaustive — falls back to "none"/"unknown" rather than guessing.
     */
    private static String supportSource(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return "unknown";
        }
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy == 0.0) {
            return "none";
        }
        if (SlabSupport.isDirectCustomSlabSupportedObject(world, pos, state)) {
            return "direct-custom-slab-support";
        }
        return "column-walk";
    }
}
