package com.slabbed.debug;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * Temporary live diagnostic for slab targeting, placement, dy, and ghost-window investigations.
 *
 * <p>Enable with {@code -Dslabbed.inspect=true}. The older
 * {@code -Dslabbed.b2.live.trace=true} flag is accepted as an alias.
 * Default OFF. No gameplay behavior should depend on this class.
 */
public final class SlabbedInspect {
    private SlabbedInspect() {}

    public static final boolean ENABLED = Boolean.getBoolean("slabbed.inspect")
            || Boolean.getBoolean("slabbed.b2.live.trace");

    private static String lastTargetKey = "";
    private static long lastTargetNanos = 0L;

    public static void logClientTarget(
            World world,
            Vec3d eye,
            Vec3d rayEnd,
            float yaw,
            float pitch,
            ItemStack held,
            HitResult initialTarget,
            HitResult finalTarget,
            String decision,
            boolean sideSlabRetargetFired
    ) {
        if (!ENABLED || world == null) return;
        BlockPos targetPos = blockPos(finalTarget);
        String targetKey = hit(finalTarget) + "|" + item(held) + "|" + decision;
        if (!shouldLogTarget(targetKey)) {
            return;
        }

        Slabbed.LOGGER.info("[SLABBED-INSPECT][TARGET] side={} eye={} yaw={} pitch={} rayEnd={} heldItem={} "
                        + "initial={} final={} decision={} sideSlabRetargetFired={} target={}",
                side(world),
                fmt(eye),
                String.format("%.3f", yaw),
                String.format("%.3f", pitch),
                fmt(rayEnd),
                item(held),
                hit(initialTarget),
                hit(finalTarget),
                decision,
                sideSlabRetargetFired,
                block(world, targetPos));
        if (isInteresting(world, targetPos)) {
            logNearby(world, "TARGET", targetPos, null);
        }
    }

    public static void logIntent(ItemUsageContext incoming, ItemUsageContext outgoing, String reason) {
        if (!ENABLED || incoming == null || incoming.getWorld() == null) return;
        World world = incoming.getWorld();
        BlockPos incomingPos = incoming.getBlockPos();
        BlockPos outgoingPos = outgoing == null ? null : outgoing.getBlockPos();
        BlockPos incomingPlacePos = placePos(incoming);
        BlockPos outgoingPlacePos = outgoing == null ? null : placePos(outgoing);
        boolean transformed = outgoing != null
                && (!incomingPos.equals(outgoingPos)
                || incoming.getSide() != outgoing.getSide()
                || !incoming.getHitPos().equals(outgoing.getHitPos()));

        Slabbed.LOGGER.info("[SLABBED-INSPECT][INTENT] side={} reason={} transformed={} heldItem={} "
                        + "incomingTarget={} incomingFace={} incomingHit={} incomingBlock={} incomingPlacePos={} incomingPlaceBlock={} "
                        + "outgoingTarget={} outgoingFace={} outgoingHit={} outgoingBlock={} outgoingPlacePos={} outgoingPlaceBlock={}",
                side(world),
                reason,
                transformed,
                item(incoming.getStack()),
                pos(incomingPos),
                incoming.getSide(),
                fmt(incoming.getHitPos()),
                block(world, incomingPos),
                pos(incomingPlacePos),
                block(world, incomingPlacePos),
                pos(outgoingPos),
                outgoing == null ? "none" : outgoing.getSide(),
                outgoing == null ? "none" : fmt(outgoing.getHitPos()),
                block(world, outgoingPos),
                pos(outgoingPlacePos),
                block(world, outgoingPlacePos));
        logNearby(world, "INTENT", incomingPos, incomingPlacePos);
        if (outgoing != null && transformed) {
            logNearby(world, "INTENT_TRANSFORMED", outgoingPos, outgoingPlacePos);
        }
    }

    public static void logPlacement(
            String stage,
            World world,
            Identifier itemId,
            ItemPlacementContext ctx,
            BlockPos hitPos,
            BlockPos placePos,
            ActionResult result
    ) {
        if (!ENABLED || world == null || ctx == null) return;
        Slabbed.LOGGER.info("[SLABBED-INSPECT][PLACE_{}] side={} item={} heldItem={} face={} hitPos={} "
                        + "hitBlock={} placePos={} placeBlock={} result={} success={}",
                stage,
                side(world),
                itemId == null ? "unknown" : itemId,
                item(ctx.getStack()),
                ctx.getSide(),
                pos(hitPos),
                block(world, hitPos),
                pos(placePos),
                block(world, placePos),
                result == null ? "pending" : result,
                result == null ? "pending" : result.isAccepted());
        logNearby(world, "PLACE_" + stage, hitPos, placePos);
    }

    private static boolean shouldLogTarget(String targetKey) {
        long now = System.nanoTime();
        if (!targetKey.equals(lastTargetKey) || now - lastTargetNanos >= 500_000_000L) {
            lastTargetKey = targetKey;
            lastTargetNanos = now;
            return true;
        }
        return false;
    }

    private static void logNearby(World world, String reason, BlockPos targetPos, BlockPos placePos) {
        if (!ENABLED || world == null || (targetPos == null && placePos == null)) return;
        Slabbed.LOGGER.info("[SLABBED-INSPECT][NEARBY] side={} reason={} target={} targetNeighbors={} placePos={} placeNeighbors={}",
                side(world),
                reason,
                pos(targetPos),
                neighbors(world, targetPos),
                pos(placePos),
                neighbors(world, placePos));
    }

    private static String neighbors(World world, BlockPos center) {
        if (center == null) return "none";
        StringBuilder out = new StringBuilder(768);
        appendNeighbor(out, "self", world, center);
        appendNeighbor(out, "down", world, center.down());
        appendNeighbor(out, "up", world, center.up());
        appendNeighbor(out, "north", world, center.north());
        appendNeighbor(out, "south", world, center.south());
        appendNeighbor(out, "east", world, center.east());
        appendNeighbor(out, "west", world, center.west());
        return out.toString();
    }

    private static void appendNeighbor(StringBuilder out, String label, World world, BlockPos pos) {
        if (!out.isEmpty()) {
            out.append(" | ");
        }
        out.append(label).append("=").append(block(world, pos));
    }

    private static BlockPos placePos(ItemUsageContext context) {
        return context == null ? null : context.getBlockPos().offset(context.getSide());
    }

    private static boolean isInteresting(World world, BlockPos pos) {
        if (world == null || pos == null) return false;
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof SlabBlock
                || SlabSupport.getYOffset(world, pos, state) != 0.0d
                || SlabAnchorAttachment.isAnchored(world, pos)
                || state.isSolidBlock(world, pos);
    }

    private static String side(World world) {
        return world.isClient() ? "CLIENT" : "SERVER";
    }

    private static String item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    private static String hit(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return hit == null ? "null" : hit.getType().toString();
        }
        return "BLOCK pos=" + pos(blockHit.getBlockPos())
                + " face=" + blockHit.getSide().asString()
                + " vec=" + fmt(blockHit.getPos());
    }

    private static BlockPos blockPos(HitResult hit) {
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                ? blockHit.getBlockPos()
                : null;
    }

    private static String block(World world, BlockPos pos) {
        if (world == null || pos == null) return "none";
        BlockState state = world.getBlockState(pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        return "pos=" + pos(pos)
                + " id=" + Registries.BLOCK.getId(state.getBlock())
                + " state=" + state
                + " dy=" + String.format("%.3f", dy)
                + " outline=" + outline(world, pos, state)
                + " slabType=" + slabType(state)
                + " anchored=" + SlabAnchorAttachment.isAnchored(world, pos)
                + " lowered=" + (dy != 0.0d)
                + " solid=" + state.isSolidBlock(world, pos)
                + " fullCube=" + state.isOpaqueFullCube()
                + warning(state, dy);
    }

    private static String outline(World world, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getOutlineShape(world, pos, ShapeContext.absent());
        if (shape.isEmpty()) return "empty";
        var box = shape.getBoundingBox();
        return String.format("%.3f..%.3f", pos.getY() + box.minY, pos.getY() + box.maxY);
    }

    private static String slabType(BlockState state) {
        if (state == null || !(state.getBlock() instanceof SlabBlock) || !state.contains(SlabBlock.TYPE)) {
            return "none";
        }
        return state.get(SlabBlock.TYPE).toString();
    }

    private static String warning(BlockState state, double dy) {
        if (state == null || !(state.getBlock() instanceof SlabBlock) || !state.contains(SlabBlock.TYPE)) {
            return "";
        }
        SlabType type = state.get(SlabBlock.TYPE);
        if (type == SlabType.TOP && dy < 0.0d) {
            return " warning=TOP_SLAB_WITH_NEGATIVE_DY";
        }
        if (type == SlabType.BOTTOM && dy > 0.0d) {
            return " warning=BOTTOM_SLAB_WITH_POSITIVE_DY";
        }
        if (type == SlabType.BOTTOM && dy != 0.0d && dy != -0.5d) {
            return " warning=BOTTOM_SLAB_UNEXPECTED_DY";
        }
        return "";
    }

    private static String pos(BlockPos pos) {
        return pos == null ? "none" : pos.toShortString();
    }

    private static String fmt(Vec3d vec) {
        return vec == null
                ? "none"
                : String.format("%.4f,%.4f,%.4f", vec.x, vec.y, vec.z);
    }
}
