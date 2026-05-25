package com.slabbed.debug;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Temporary live diagnostic for slab targeting, placement, dy, and ghost-window investigations.
 *
 * <p>Enable with {@code -Dslabbed.inspect=true}. The older
 * {@code -Dslabbed.b2.live.trace=true} flag is accepted as an alias.
 * Default OFF. No gameplay behavior should depend on this class.
 */
public final class SlabbedInspect {
    private SlabbedInspect() {}

    private record TargetSnapshot(HitResult initialTarget, HitResult finalTarget, String decision, boolean sideSlabRetargetFired) {}

    public static final boolean ENABLED = Boolean.getBoolean("slabbed.inspect")
            || Boolean.getBoolean("slabbed.b2.live.trace");
    private static final AtomicBoolean SESSION_MARKED = new AtomicBoolean(false);
    private static final AtomicInteger CLICK_IDS = new AtomicInteger();
    private static final ThreadLocal<Integer> CURRENT_CLICK = new ThreadLocal<>();
    private static final AtomicReference<TargetSnapshot> LAST_CLIENT_TARGET = new AtomicReference<>();

    private static String lastTargetKey = "";
    private static long lastTargetNanos = 0L;
    private static final double LEGAL_TOP_NEGATIVE_DY = -0.5d;

    public static void logClientTarget(
            Level world,
            Vec3 eye,
            Vec3 rayEnd,
            float yaw,
            float pitch,
            ItemStack held,
            HitResult initialTarget,
            HitResult finalTarget,
            String decision,
            boolean sideSlabRetargetFired
    ) {
        if (!ENABLED || world == null) return;
        LAST_CLIENT_TARGET.set(new TargetSnapshot(initialTarget, finalTarget, decision, sideSlabRetargetFired));
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

    public static void logClickPair(UseOnContext context, Identifier itemId) {
        if (!ENABLED || context == null || context.getLevel() == null) return;
        int click = CLICK_IDS.incrementAndGet();
        CURRENT_CLICK.set(click);

        Level world = context.getLevel();
        TargetSnapshot target = LAST_CLIENT_TARGET.get();
        HitResult previewOwner = target == null ? null : target.finalTarget();
        BlockPos previewOwnerPos = blockPos(previewOwner);
        BlockPos contextTarget = context.getClickedPos();
        BlockPos contextPlacePos = placePos(context);
        boolean previewMatchesTarget = previewOwnerPos != null && previewOwnerPos.equals(contextTarget);
        boolean previewMatchesPlacement = previewOwnerPos != null && previewOwnerPos.equals(contextPlacePos);

        Slabbed.LOGGER.info("[SLABBED-INSPECT][CLICK_PAIR] click={} side={} item={} heldItem={} "
                        + "previewOwner={} previewDecision={} previewSideSlabRetargetFired={} "
                        + "contextTarget={} contextFace={} contextHit={} contextBlock={} contextPlacePos={} contextPlaceBlock={} "
                        + "previewMatchesTarget={} previewMatchesPlacement={} previewPlacementMismatch={}",
                click,
                side(world),
                itemId == null ? "unknown" : itemId,
                item(context.getItemInHand()),
                hit(previewOwner),
                target == null ? "none" : target.decision(),
                target != null && target.sideSlabRetargetFired(),
                pos(contextTarget),
                context.getClickedFace(),
                fmt(context.getClickLocation()),
                block(world, contextTarget),
                pos(contextPlacePos),
                block(world, contextPlacePos),
                previewMatchesTarget,
                previewMatchesPlacement,
                previewOwnerPos != null && !previewMatchesPlacement);
    }

    public static void clearClickPair() {
        CURRENT_CLICK.remove();
    }

    public static void logIntent(UseOnContext incoming, UseOnContext outgoing, String reason) {
        if (!ENABLED || incoming == null || incoming.getLevel() == null) return;
        Level world = incoming.getLevel();
        BlockPos incomingPos = incoming.getClickedPos();
        BlockPos outgoingPos = outgoing == null ? null : outgoing.getClickedPos();
        BlockPos incomingPlacePos = placePos(incoming);
        BlockPos outgoingPlacePos = outgoing == null ? null : placePos(outgoing);
        boolean transformed = outgoing != null
                && (!incomingPos.equals(outgoingPos)
                || incoming.getClickedFace() != outgoing.getClickedFace()
                || !incoming.getClickLocation().equals(outgoing.getClickLocation()));

        Slabbed.LOGGER.info("[SLABBED-INSPECT][INTENT] click={} side={} reason={} transformed={} heldItem={} "
                        + "incomingTarget={} incomingFace={} incomingHit={} incomingBlock={} incomingPlacePos={} incomingPlaceBlock={} "
                        + "outgoingTarget={} outgoingFace={} outgoingHit={} outgoingBlock={} outgoingPlacePos={} outgoingPlaceBlock={}",
                click(),
                side(world),
                reason,
                transformed,
                item(incoming.getItemInHand()),
                pos(incomingPos),
                incoming.getClickedFace(),
                fmt(incoming.getClickLocation()),
                block(world, incomingPos),
                pos(incomingPlacePos),
                block(world, incomingPlacePos),
                pos(outgoingPos),
                outgoing == null ? "none" : outgoing.getClickedFace(),
                outgoing == null ? "none" : fmt(outgoing.getClickLocation()),
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
            Level world,
            Identifier itemId,
            BlockPlaceContext ctx,
            BlockPos hitPos,
            BlockPos placePos,
            InteractionResult result
    ) {
        if (!ENABLED || world == null || ctx == null) return;
        Slabbed.LOGGER.info("[SLABBED-INSPECT][PLACE_{}] click={} side={} item={} heldItem={} face={} hitPos={} "
                        + "hitBlock={} placePos={} placeBlock={} result={} success={}",
                stage,
                click(),
                side(world),
                itemId == null ? "unknown" : itemId,
                item(ctx.getItemInHand()),
                ctx.getClickedFace(),
                pos(hitPos),
                block(world, hitPos),
                pos(placePos),
                block(world, placePos),
                result == null ? "pending" : result,
                result == null ? "pending" : result.consumesAction());
        logNearby(world, "PLACE_" + stage, hitPos, placePos);
    }

    public static void logSessionStart() {
        if (!ENABLED || !SESSION_MARKED.compareAndSet(false, true)) {
            return;
        }

        Slabbed.LOGGER.info("[SLABBED-INSPECT][SESSION] startedAt={} gitHead={} inspect={}",
                Instant.now(),
                detectGitHead(),
                Boolean.toString(ENABLED));
    }

    public static void logPlacementNoReturn(
            Level world,
            Identifier itemId,
            Direction face,
            BlockPos hitPos,
            BlockPos placePos
    ) {
        if (!ENABLED || world == null) return;
        Slabbed.LOGGER.info("[SLABBED-INSPECT][PLACE_NO_RETURN] click={} side={} item={} heldItem={} face={} hitPos={} "
                        + "hitBlock={} placePos={} placeBlock={} reason=use_on_block_returned_without_place_return",
                click(),
                side(world),
                itemId == null ? "unknown" : itemId,
                itemId == null ? "unknown" : itemId,
                face,
                pos(hitPos),
                block(world, hitPos),
                pos(placePos),
                block(world, placePos));
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

    private static String click() {
        Integer click = CURRENT_CLICK.get();
        return click == null ? "none" : click.toString();
    }

    private static void logNearby(Level world, String reason, BlockPos targetPos, BlockPos placePos) {
        if (!ENABLED || world == null || (targetPos == null && placePos == null)) return;
        Slabbed.LOGGER.info("[SLABBED-INSPECT][NEARBY] side={} reason={} target={} targetNeighbors={} placePos={} placeNeighbors={}",
                side(world),
                reason,
                pos(targetPos),
                neighbors(world, targetPos),
                pos(placePos),
                neighbors(world, placePos));
    }

    private static String neighbors(Level world, BlockPos center) {
        if (center == null) return "none";
        StringBuilder out = new StringBuilder(768);
        appendNeighbor(out, "self", world, center);
        appendNeighbor(out, "down", world, center.below());
        appendNeighbor(out, "up", world, center.above());
        appendNeighbor(out, "north", world, center.north());
        appendNeighbor(out, "south", world, center.south());
        appendNeighbor(out, "east", world, center.east());
        appendNeighbor(out, "west", world, center.west());
        return out.toString();
    }

    private static void appendNeighbor(StringBuilder out, String label, Level world, BlockPos pos) {
        if (!out.isEmpty()) {
            out.append(" | ");
        }
        out.append(label).append("=").append(block(world, pos));
    }

    private static BlockPos placePos(UseOnContext context) {
        return context == null ? null : context.getClickedPos().relative(context.getClickedFace());
    }

    private static boolean isInteresting(Level world, BlockPos pos) {
        if (world == null || pos == null) return false;
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof SlabBlock
                || SlabSupport.getYOffset(world, pos, state) != 0.0d
                || SlabAnchorAttachment.isAnchored(world, pos)
                || state.isRedstoneConductor(world, pos);
    }

    private static String side(Level world) {
        return world.isClientSide() ? "CLIENT" : "SERVER";
    }

    private static String item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String hit(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return hit == null ? "null" : hit.getType().toString();
        }
        return "BLOCK pos=" + pos(blockHit.getBlockPos())
                + " face=" + blockHit.getDirection().getSerializedName()
                + " vec=" + fmt(blockHit.getLocation());
    }

    private static BlockPos blockPos(HitResult hit) {
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                ? blockHit.getBlockPos()
                : null;
    }

    private static String block(Level world, BlockPos pos) {
        if (world == null || pos == null) return "none";
        BlockState state = world.getBlockState(pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        return "pos=" + pos(pos)
                + " id=" + BuiltInRegistries.BLOCK.getId(state.getBlock())
                + " state=" + state
                + " dy=" + String.format("%.3f", dy)
                + " outline=" + outline(world, pos, state)
                + " slabType=" + slabType(state)
                + " anchored=" + SlabAnchorAttachment.isAnchored(world, pos)
                + " lowered=" + (dy != 0.0d)
                + " solid=" + state.isRedstoneConductor(world, pos)
                + " fullCube=" + state.isSolidRender()
                + warning(state, dy);
    }

    private static String outline(Level world, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getShape(world, pos, CollisionContext.empty());
        if (shape.isEmpty()) return "empty";
        var box = shape.bounds();
        return String.format("%.3f..%.3f", pos.getY() + box.minY, pos.getY() + box.maxY);
    }

    private static String slabType(BlockState state) {
        if (state == null || !(state.getBlock() instanceof SlabBlock) || !state.hasProperty(SlabBlock.TYPE)) {
            return "none";
        }
        return state.getValue(SlabBlock.TYPE).toString();
    }

    private static String warning(BlockState state, double dy) {
        if (state == null || !(state.getBlock() instanceof SlabBlock) || !state.hasProperty(SlabBlock.TYPE)) {
            return "";
        }
        SlabType type = state.getValue(SlabBlock.TYPE);
        if (type == SlabType.TOP && dy < 0.0d && Math.abs(dy - LEGAL_TOP_NEGATIVE_DY) > 1.0e-9) {
            return " warning=TOP_SLAB_WITH_UNSUPPORTED_NEGATIVE_DY";
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

    private static String fmt(Vec3 vec) {
        return vec == null
                ? "none"
                : String.format("%.4f,%.4f,%.4f", vec.x, vec.y, vec.z);
    }

    private static String detectGitHead() {
        try {
            Process proc = new ProcessBuilder("git", "rev-parse", "--short", "HEAD").start();
            if (!proc.waitFor(250, TimeUnit.MILLISECONDS)) {
                proc.destroy();
                return "git-timeout";
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                return line == null || line.isBlank() ? "git-unknown" : line.trim();
            }
        } catch (Throwable e) {
            return "git-error";
        }
    }
}
