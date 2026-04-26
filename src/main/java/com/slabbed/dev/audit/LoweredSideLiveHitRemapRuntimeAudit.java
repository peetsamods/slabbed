package com.slabbed.dev.audit;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabbedDebug;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class LoweredSideLiveHitRemapRuntimeAudit {

    private static final ThreadLocal<AuditFrame> FRAME = new ThreadLocal<>();

    private LoweredSideLiveHitRemapRuntimeAudit() {
    }

    public static void recordRemapAttempt(
            ItemUsageContext context,
            boolean itemIsSlab,
            boolean faceHorizontal,
            boolean targetIsSolid,
            boolean targetHasBlockEntity,
            boolean targetIsCraftingTable,
            double yOffset,
            boolean ordinaryLoweredFullBlockGuard,
            boolean remapGuardMatched,
            String failedGuard,
            Vec3d remappedHitPos,
            Direction effectiveRemapFace,
            String remapMode
    ) {
        if (!SlabbedDebug.DEBUG_SBSB) {
            return;
        }

        Direction side = context.getSide();
        BlockPos targetPos = context.getBlockPos();
        BlockState targetState = context.getWorld().getBlockState(targetPos);
        Vec3d originalHit = context.getHitPos();
        double originalRelativeY = originalHit.y - targetPos.getY();
        String itemId = Registries.ITEM.getId(context.getStack().getItem()).toString();
        String targetBlockId = Registries.BLOCK.getId(targetState.getBlock()).toString();

        AuditFrame frame = new AuditFrame();
        frame.itemId = itemId;
        frame.targetBlockId = targetBlockId;
        frame.targetBlockPos = targetPos.toShortString();
        frame.hitFace = side.asString();
        frame.originalHitX = originalHit.x;
        frame.originalHitY = originalHit.y;
        frame.originalHitZ = originalHit.z;
        frame.originalHitRelativeY = originalRelativeY;
        frame.targetHasBlockEntity = targetHasBlockEntity;
        frame.targetIsCraftingTable = targetIsCraftingTable;
        frame.faceHorizontal = faceHorizontal;
        frame.itemIsSlab = itemIsSlab;
        frame.yOffset = yOffset;
        frame.ordinaryLoweredFullBlockGuard = ordinaryLoweredFullBlockGuard;
        frame.remapGuardMatched = remapGuardMatched;
        frame.failedGuard = failedGuard;
        frame.effectiveRemapFace = effectiveRemapFace == null ? null : effectiveRemapFace.asString();
        frame.remapMode = remapMode;

        if (remappedHitPos != null) {
            frame.remappedHitX = remappedHitPos.x;
            frame.remappedHitY = remappedHitPos.y;
            frame.remappedHitZ = remappedHitPos.z;
            frame.remappedHitRelativeY = remappedHitPos.y - targetPos.getY();
        }

        FRAME.set(frame);
    }

    public static void recordPlacementContext(ItemPlacementContext ctx) {
        if (!SlabbedDebug.DEBUG_SBSB) {
            return;
        }
        AuditFrame frame = FRAME.get();
        if (frame == null) {
            return;
        }
        frame.placementContextPos = ctx.getBlockPos().toShortString();
    }

    public static void recordPlacementResult(ItemPlacementContext ctx, ActionResult result) {
        if (!SlabbedDebug.DEBUG_SBSB) {
            return;
        }

        AuditFrame frame = FRAME.get();
        if (frame == null) {
            return;
        }

        try {
            BlockPos placedPos = ctx.getBlockPos();
            BlockState placedState = ctx.getWorld().getBlockState(placedPos);
            frame.finalPlacedBlockPos = placedPos.toShortString();
            frame.finalPlacedBlockId = Registries.BLOCK.getId(placedState.getBlock()).toString();
            frame.actionResult = result == null ? "null" : result.toString();

            if (placedState.getBlock() instanceof SlabBlock && placedState.contains(SlabBlock.TYPE)) {
                SlabType type = placedState.get(SlabBlock.TYPE);
                frame.finalPlacedSlabType = type.name().toLowerCase(Locale.ROOT);
            } else {
                frame.finalPlacedSlabType = "none";
            }

            if (frame.remapGuardMatched && frame.targetBlockPos != null && frame.effectiveRemapFace != null) {
                BlockPos target = parseBlockPos(frame.targetBlockPos);
                Direction face = Direction.byId(frame.effectiveRemapFace);
                if (target != null && face != null) {
                    BlockPos expected = target.offset(face);
                    frame.verdict = expected.equals(placedPos)
                            ? "guard_matched_expected_side_placement"
                            : "guard_matched_unexpected_placement";
                } else {
                    frame.verdict = "guard_matched_unknown_expected_pos";
                }
            } else if (!frame.remapGuardMatched) {
                frame.verdict = "guard_not_matched:" + nullToEmpty(frame.failedGuard);
            } else {
                frame.verdict = "indeterminate";
            }

            writeJson(frame);
        } finally {
            FRAME.remove();
        }
    }

    public static String firstFailedGuard(
            boolean itemIsSlab,
            boolean faceHorizontal,
            boolean targetIsSolid,
            boolean targetHasBlockEntity,
            boolean targetIsCraftingTable,
            double yOffset
    ) {
        if (!itemIsSlab) {
            return "item_not_slab";
        }
        if (!faceHorizontal) {
            return "face_not_horizontal";
        }
        if (!targetIsSolid) {
            return "target_not_solid";
        }
        if (targetHasBlockEntity) {
            return "target_has_block_entity";
        }
        if (targetIsCraftingTable) {
            return "target_is_crafting_table";
        }
        if (yOffset != -0.5d) {
            return "y_offset_not_-0.5";
        }
        return "none";
    }

    public static boolean hasBlockEntityProvider(BlockState state) {
        return state.getBlock() instanceof BlockEntityProvider;
    }

    private static void writeJson(AuditFrame frame) {
        Path path = FabricLoader.getInstance()
                .getGameDir()
                .resolve("screenshots")
                .resolve("lowered_side_live_hit_remap_runtime_values.json");

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, toJson(frame));
        } catch (IOException e) {
            Slabbed.LOGGER.warn("[Slabbed] failed to write lowered_side_live_hit_remap_runtime_values audit", e);
        }
    }

    private static String toJson(AuditFrame f) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendField(sb, "itemId", f.itemId, true);
        appendField(sb, "targetBlockId", f.targetBlockId, true);
        appendField(sb, "targetBlockPos", f.targetBlockPos, true);
        appendField(sb, "hitFace", f.hitFace, true);
        appendField(sb, "originalHitX", formatDouble(f.originalHitX), true);
        appendField(sb, "originalHitY", formatDouble(f.originalHitY), true);
        appendField(sb, "originalHitZ", formatDouble(f.originalHitZ), true);
        appendField(sb, "originalHitYRelativeToTargetY", formatDouble(f.originalHitRelativeY), true);
        appendField(sb, "clickedBlockHasBlockEntity", Boolean.toString(f.targetHasBlockEntity), true);
        appendField(sb, "clickedBlockIsCraftingTable", Boolean.toString(f.targetIsCraftingTable), true);
        appendField(sb, "faceIsHorizontal", Boolean.toString(f.faceHorizontal), true);
        appendField(sb, "itemIsSlab", Boolean.toString(f.itemIsSlab), true);
        appendField(sb, "slabSupportYOffset", formatDouble(f.yOffset), true);
        appendField(sb, "ordinarySolidLoweredFullBlockGuard", Boolean.toString(f.ordinaryLoweredFullBlockGuard), true);
        appendField(sb, "remapGuardMatched", Boolean.toString(f.remapGuardMatched), true);
        appendField(sb, "failedGuard", f.failedGuard, true);
        appendField(sb, "effectiveRemapFace", f.effectiveRemapFace, true);
        appendField(sb, "remapMode", f.remapMode, true);
        appendField(sb, "remappedHitX", formatNullableDouble(f.remappedHitX), true);
        appendField(sb, "remappedHitY", formatNullableDouble(f.remappedHitY), true);
        appendField(sb, "remappedHitZ", formatNullableDouble(f.remappedHitZ), true);
        appendField(sb, "remappedHitYRelativeToTargetY", formatNullableDouble(f.remappedHitRelativeY), true);
        appendField(sb, "placementContextPos", f.placementContextPos, true);
        appendField(sb, "finalPlacedBlockPos", f.finalPlacedBlockPos, true);
        appendField(sb, "finalPlacedBlockId", f.finalPlacedBlockId, true);
        appendField(sb, "finalPlacedSlabType", f.finalPlacedSlabType, true);
        appendField(sb, "actionResult", f.actionResult, true);
        appendField(sb, "verdict", f.verdict, false);
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String key, String value, boolean comma) {
        sb.append("  \"").append(escape(key)).append("\": ");
        if (value == null) {
            sb.append("null");
        } else if (isBoolean(value) || isNumber(value)) {
            sb.append(value);
        } else {
            sb.append("\"").append(escape(value)).append("\"");
        }
        if (comma) {
            sb.append(",");
        }
        sb.append("\n");
    }

    private static boolean isBoolean(String v) {
        return "true".equals(v) || "false".equals(v);
    }

    private static boolean isNumber(String v) {
        if (v == null || v.isEmpty()) return false;
        int start = (v.charAt(0) == '-') ? 1 : 0;
        boolean dot = false;
        for (int i = start; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '.') {
                if (dot) return false;
                dot = true;
                continue;
            }
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }

    private static String formatDouble(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static String formatNullableDouble(Double v) {
        return v == null ? null : formatDouble(v);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static BlockPos parseBlockPos(String shortPos) {
        try {
            String[] parts = shortPos.split(",");
            if (parts.length != 3) {
                return null;
            }
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            return new BlockPos(x, y, z);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class AuditFrame {
        String itemId;
        String targetBlockId;
        String targetBlockPos;
        String hitFace;
        double originalHitX;
        double originalHitY;
        double originalHitZ;
        double originalHitRelativeY;
        boolean targetHasBlockEntity;
        boolean targetIsCraftingTable;
        boolean faceHorizontal;
        boolean itemIsSlab;
        double yOffset;
        boolean ordinaryLoweredFullBlockGuard;
        boolean remapGuardMatched;
        String failedGuard;
        String effectiveRemapFace;
        String remapMode;
        Double remappedHitX;
        Double remappedHitY;
        Double remappedHitZ;
        Double remappedHitRelativeY;
        String placementContextPos;
        String finalPlacedBlockPos;
        String finalPlacedBlockId;
        String finalPlacedSlabType;
        String actionResult;
        String verdict;
    }
}
