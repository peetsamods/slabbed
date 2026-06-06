package com.slabbed.dev.audit;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dev-only append-only recorder for manual branch-client placement/targeting runs.
 *
 * <p>Enabled with {@code -Dslabbed.liveCursorIntentRecorder=true}. The output
 * directory is {@code -Dslabbed.liveCursorIntentRecorderDir=/abs/path}; otherwise
 * it falls back to {@code run/live-cursor-recorder}.
 */
public final class LiveCursorIntentRecorder {

    private static final boolean ENABLED = Boolean.getBoolean("slabbed.liveCursorIntentRecorder");
    private static final String DIR_PROPERTY = "slabbed.liveCursorIntentRecorderDir";
    private static final String SCHEMA_VERSION = "3";
    private static final String RECORDER_VERSION = "3-target-semantics";
    private static final String RUN_ID = UUID.randomUUID().toString();
    private static final String[] RED_COUNTERS = {
            "same_cell_double_combine",
            "placed_above_intended",
            "rawFinalTargetVsInteractionHitMismatch",
            "finalTargetPlacementSemanticMismatch",
            "clientServerPlacePosMismatch",
            "loweredDyLostAfterDoubleTarget"
    };
    private static final String[] HEALTH_COUNTERS = {
            "sessionRows",
            "actionRows",
            "mismatchRows",
            "summaryWrites",
            "summaryWriteFailures",
            "manifestWrites",
            "manifestWriteFailures",
            "ioErrors",
            "appendFailures",
            "droppedRows",
            "sequenceBackfills",
            "clientActionsWithoutSequence",
            "serverOnlySequences",
            "uncorrelatedFrames"
    };

    private static final AtomicLong NEXT_ACTION = new AtomicLong();
    private static final AtomicLong NEXT_ROW = new AtomicLong();
    private static final ThreadLocal<ActionFrame> CURRENT = new ThreadLocal<>();
    private static final Map<Integer, String> CLIENT_ACTION_BY_SEQUENCE = new ConcurrentHashMap<>();
    private static final Map<Integer, PlacementSummary> CLIENT_PLACE_BY_SEQUENCE = new ConcurrentHashMap<>();
    private static final Map<Integer, PlacementSummary> SERVER_PLACE_BY_SEQUENCE = new ConcurrentHashMap<>();
    private static final Map<String, ActionFrame> ACTION_LEDGER = new LinkedHashMap<>();
    private static final Set<String> MISMATCH_KEYS = new LinkedHashSet<>();
    private static final Map<String, Integer> COUNTERS = new LinkedHashMap<>();
    private static final Map<String, Integer> HEALTH = new LinkedHashMap<>();

    private static volatile TargetFrame lastTarget;
    private static boolean initialized;
    private static boolean shutdownHookRegistered;
    private static boolean runEnded;
    private static boolean ioDisabled;
    private static boolean ioErrorLogged;
    private static Path outputDir;
    private static Path manifestPath;
    private static Path sessionPath;
    private static Path actionsPath;
    private static Path mismatchesPath;
    private static Path summaryPath;
    private static Path summaryJsonPath;

    static {
        for (String counter : RED_COUNTERS) {
            COUNTERS.put(counter, 0);
        }
        for (String counter : HEALTH_COUNTERS) {
            HEALTH.put(counter, 0);
        }
    }

    private LiveCursorIntentRecorder() {
    }

    public static void bootstrap() {
        if (!ENABLED) {
            return;
        }
        synchronized (LiveCursorIntentRecorder.class) {
            ensureInitialized();
            registerShutdownHook();
            writeManifest();
            writeSession("session_start", null, null, fields(
                    "recorder", "LiveCursorIntentRecorder",
                    "recorderVersion", RECORDER_VERSION,
                    "enabled", "true",
                    "dir", outputDir.toAbsolutePath().toString()));
            writeSummary();
            Slabbed.LOGGER.info("[LIVE_CURSOR_INTENT_RECORDER_START] dir={}", outputDir.toAbsolutePath());
        }
    }

    public static void recordShutdown() {
        if (!ENABLED) {
            return;
        }
        synchronized (LiveCursorIntentRecorder.class) {
            if (runEnded) {
                return;
            }
            ensureInitialized();
            runEnded = true;
            writeSession("run_end", null, null, fields(
                    "recorder", "LiveCursorIntentRecorder",
                    "dir", outputDir.toAbsolutePath().toString(),
                    "health", compactMap(HEALTH),
                    "redCounters", compactMap(COUNTERS)));
            writeActionsSnapshot();
            writeSummary();
        }
    }

    public static void recordCrosshairTarget(
            HitResult initialTarget,
            HitResult finalTarget,
            String decision,
            boolean sideSlabRetargetFired,
            boolean slabHeld,
            Vec3d eye,
            Vec3d look,
            ItemStack held,
            float tickProgress
    ) {
        if (!ENABLED) {
            return;
        }
        synchronized (LiveCursorIntentRecorder.class) {
            ensureInitialized();
            TargetFrame frame = new TargetFrame(
                    hitSummary(initialTarget),
                    hitSummary(finalTarget),
                    blockPosOf(finalTarget),
                    sideOf(finalTarget),
                    decision,
                    sideSlabRetargetFired,
                    slabHeld,
                    vec(eye),
                    vec(look),
                    itemId(held),
                    tickProgress);
            lastTarget = frame;
            writeSession("crosshair_target", null, "CLIENT", fields(
                    "initial", frame.initialHit,
                    "final", frame.finalHit,
                    "decision", frame.decision,
                    "sideSlabRetargetFired", Boolean.toString(frame.sideSlabRetargetFired),
                    "slabHeld", Boolean.toString(frame.slabHeld),
                    "eye", frame.eye,
                    "look", frame.look,
                    "heldItem", frame.heldItem,
                    "tickProgress", formatDouble(frame.tickProgress)));
        }
    }

    public static void recordClientInteract(PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!ENABLED) {
            return;
        }
        synchronized (LiveCursorIntentRecorder.class) {
            ensureInitialized();
            ActionFrame frame = new ActionFrame(nextActionId(), "CLIENT");
            rememberFrame(frame);
            frame.hand = safe(hand);
            frame.player = playerSummary(player);
            frame.heldItem = itemId(player == null ? null : player.getStackInHand(hand));
            frame.interactionHit = hitSummary(hit);
            frame.interactionHitPos = hit == null ? null : hit.getBlockPos();
            frame.interactionSide = hit == null ? null : hit.getSide();
            frame.target = lastTarget;
            CURRENT.set(frame);
            writeSession("client_interact_start", frame, "CLIENT", fields(
                    "player", frame.player,
                    "hand", frame.hand,
                    "heldItem", frame.heldItem,
                    "interactionHit", frame.interactionHit,
                    "lastFinalTarget", frame.target == null ? "none" : frame.target.finalHit));
            if (frame.target != null && differs(frame.target.finalBlockPos, frame.target.finalSide,
                    frame.interactionHitPos, frame.interactionSide)) {
                writeMismatch(frame, "rawFinalTargetVsInteractionHitMismatch", "raw client final crosshair target differs from interactBlock hit");
            }
        }
    }

    public static void recordClientInteractResult(ActionResult result) {
        if (!ENABLED) {
            return;
        }
        synchronized (LiveCursorIntentRecorder.class) {
            ActionFrame frame = CURRENT.get();
            if (frame != null) {
                frame.clientReturnRecorded = true;
                if (frame.actionResult.isEmpty()) {
                    frame.actionResult = safe(result);
                }
                if (frame.sequence == null) {
                    incHealth("clientActionsWithoutSequence");
                }
                writeSession("client_interact_return", frame, "CLIENT", fields(
                        "result", safe(result)));
                writeActionsSnapshot();
            }
            CURRENT.remove();
        }
    }

    public static void recordPacketSequence(Hand hand, BlockHitResult hit, int sequence) {
        if (!ENABLED) {
            return;
        }
        synchronized (LiveCursorIntentRecorder.class) {
            ensureInitialized();
            ActionFrame frame = CURRENT.get();
            if (frame == null) {
                frame = new ActionFrame(nextActionId(), "CLIENT");
                rememberFrame(frame);
                CURRENT.set(frame);
                incHealth("uncorrelatedFrames");
            }
            Integer previousSequence = frame.sequence;
            frame.sequence = sequence;
            frame.packetHit = hitSummary(hit);
            frame.packetHitPos = hit == null ? null : hit.getBlockPos();
            frame.packetSide = hit == null ? null : hit.getSide();
            frame.hand = safe(hand);
            CLIENT_ACTION_BY_SEQUENCE.put(sequence, frame.actionId);
            writeSession("packet_sequence", frame, "CLIENT", fields(
                    "hand", frame.hand,
                    "packetHit", frame.packetHit));
            if (previousSequence == null && frame.placementResultRecorded) {
                incHealth("sequenceBackfills");
            }
            if (frame.placePos != null) {
                compareClientServer(frame);
            }
            writeActionsSnapshot();
            writeSummary();
        }
    }

    public static void recordServerInteract(PlayerEntity player, Hand hand, BlockHitResult hit, int sequence) {
        if (!ENABLED) {
            return;
        }
        synchronized (LiveCursorIntentRecorder.class) {
            ensureInitialized();
            String actionId = CLIENT_ACTION_BY_SEQUENCE.get(sequence);
            if (actionId == null) {
                actionId = "server-" + sequence;
                incHealth("serverOnlySequences");
            }
            ActionFrame frame = new ActionFrame(actionId, "SERVER");
            rememberFrame(frame);
            frame.sequence = sequence;
            frame.hand = safe(hand);
            frame.player = playerSummary(player);
            frame.heldItem = itemId(player == null ? null : player.getStackInHand(hand));
            frame.interactionHit = hitSummary(hit);
            frame.interactionHitPos = hit == null ? null : hit.getBlockPos();
            frame.interactionSide = hit == null ? null : hit.getSide();
            CURRENT.set(frame);
            writeSession("server_interact_start", frame, "SERVER", fields(
                    "player", frame.player,
                    "hand", frame.hand,
                    "heldItem", frame.heldItem,
                    "interactionHit", frame.interactionHit));
        }
    }

    public static void recordServerInteractResult(int sequence) {
        if (!ENABLED) {
            return;
        }
        synchronized (LiveCursorIntentRecorder.class) {
            ActionFrame frame = CURRENT.get();
            if (frame != null) {
                frame.serverReturnRecorded = true;
                writeSession("server_interact_return", frame, "SERVER", fields(
                        "serverReturn", "true"));
                writeActionsSnapshot();
            }
            CURRENT.remove();
        }
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
            boolean remapped,
            String rejectionReason,
            Vec3d remappedHitPos,
            Direction effectiveSide,
            String hitDescriptor
    ) {
        if (!ENABLED) {
            return;
        }
        synchronized (LiveCursorIntentRecorder.class) {
            ensureInitialized();
            ActionFrame frame = requireFrame(context.getWorld().isClient() ? "CLIENT" : "SERVER");
            frame.heldItem = itemId(context.getStack());
            frame.remapTargetPos = context.getBlockPos();
            frame.remapOriginalSide = context.getSide();
            frame.remapEffectiveSide = effectiveSide;
            frame.remapHit = hitSummary(new BlockHitResult(
                    context.getHitPos(),
                    context.getSide(),
                    context.getBlockPos(),
                    context.hitsInsideBlock(),
                    false));
            frame.remapMode = hitDescriptor;
            frame.remapRejection = rejectionReason;
            frame.remapAccepted = remapped;
            frame.remappedHit = vec(remappedHitPos);
            frame.targetDy = yOffset;
            writeSession("placement_intent_remap", frame, frame.side, fields(
                    "itemIsSlab", Boolean.toString(itemIsSlab),
                    "faceHorizontal", Boolean.toString(faceHorizontal),
                    "targetIsSolid", Boolean.toString(targetIsSolid),
                    "targetHasBlockEntity", Boolean.toString(targetHasBlockEntity),
                    "targetIsCraftingTable", Boolean.toString(targetIsCraftingTable),
                    "targetDy", formatDouble(yOffset),
                    "ordinaryLoweredFullBlockGuard", Boolean.toString(ordinaryLoweredFullBlockGuard),
                    "remapped", Boolean.toString(remapped),
                    "rejectionReason", rejectionReason,
                    "effectiveSide", safe(effectiveSide),
                    "remapMode", hitDescriptor,
                    "remappedHit", frame.remappedHit));
        }
    }

    public static void recordPlacementContext(ItemPlacementContext ctx) {
        if (!ENABLED) {
            return;
        }
        synchronized (LiveCursorIntentRecorder.class) {
            ensureInitialized();
            ActionFrame frame = requireFrame(ctx.getWorld().isClient() ? "CLIENT" : "SERVER");
            World world = ctx.getWorld();
            BlockPos placePos = ctx.getBlockPos();
            Direction side = ctx.getSide();
            BlockPos hitPos = placePos.offset(side.getOpposite());
            BlockState hitState = world.getBlockState(hitPos);
            BlockState placeState = world.getBlockState(placePos);

            frame.heldItem = itemId(ctx.getStack());
            frame.contextHitPos = hitPos;
            frame.contextSide = side;
            frame.placePos = placePos;
            frame.hitState = stateSummary(hitState);
            frame.placeBeforeState = stateSummary(placeState);
            frame.dyHit = safeDy(world, hitPos, hitState);
            frame.dyPlaceBefore = safeDy(world, placePos, placeState);
            frame.canReplaceExisting = ctx.canReplaceExisting();
            writeSession("placement_context", frame, frame.side, fields(
                    "item", frame.heldItem,
                    "contextSide", safe(side),
                    "contextHitPos", pos(hitPos),
                    "placePos", pos(placePos),
                    "hitState", frame.hitState,
                    "placeBeforeState", frame.placeBeforeState,
                    "dyHit", formatDouble(frame.dyHit),
                    "dyPlaceBefore", formatDouble(frame.dyPlaceBefore),
                    "canReplaceExisting", Boolean.toString(frame.canReplaceExisting)));
        }
    }

    public static void recordSlabPlacementState(ItemPlacementContext ctx, BlockState state) {
        if (!ENABLED) {
            return;
        }
        synchronized (LiveCursorIntentRecorder.class) {
            ensureInitialized();
            ActionFrame frame = requireFrame(ctx.getWorld().isClient() ? "CLIENT" : "SERVER");
            frame.slabPlacementState = stateSummary(state);
            frame.slabPlacementType = slabType(state);
            writeSession("slab_getPlacementState_return", frame, frame.side, fields(
                    "placePos", pos(ctx.getBlockPos()),
                    "contextSide", safe(ctx.getSide()),
                    "state", frame.slabPlacementState,
                    "slabType", frame.slabPlacementType));
        }
    }

    public static void recordPlacementResult(ItemPlacementContext ctx, ActionResult result) {
        if (!ENABLED) {
            return;
        }
        synchronized (LiveCursorIntentRecorder.class) {
            ensureInitialized();
            ActionFrame frame = requireFrame(ctx.getWorld().isClient() ? "CLIENT" : "SERVER");
            World world = ctx.getWorld();
            BlockPos placePos = ctx.getBlockPos();
            BlockState after = world.getBlockState(placePos);
            frame.placePos = placePos;
            frame.placeAfterState = stateSummary(after);
            frame.placeAfterSlabType = slabType(after);
            frame.dyPlaceAfter = safeDy(world, placePos, after);
            frame.actionResult = safe(result);
            frame.neighborhood = neighborhood(world, placePos);
            frame.placementResultRecorded = true;

            writeSession("placement_result", frame, frame.side, fields(
                    "placePos", pos(placePos),
                    "afterState", frame.placeAfterState,
                    "afterSlabType", frame.placeAfterSlabType,
                    "dyPlaceAfter", formatDouble(frame.dyPlaceAfter),
                    "result", frame.actionResult,
                    "postSettleNeighborhood", frame.neighborhood));
            classifyPlacement(frame);
            writeActionsSnapshot();
            compareClientServer(frame);
            writeSummary();
        }
    }

    private static void classifyPlacement(ActionFrame frame) {
        if (isSingleSlab(frame.placeBeforeState) && "double".equals(frame.placeAfterSlabType)) {
            writeMismatch(frame, "same_cell_double_combine", "pre-state single slab became double at same placePos");
            writeFinalTargetSemanticMismatch(frame, "final target resolved to same-cell slab combine");
        }

        BlockPos expected = expectedSidePlace(frame);
        if (expected != null && frame.placePos != null && frame.placePos.equals(expected.up())) {
            writeMismatch(frame, "placed_above_intended", "actual placePos is one block above expected side placement");
            writeFinalTargetSemanticMismatch(frame, "final target resolved one block above expected side placement");
        } else if (frame.contextSide != null && frame.contextSide.getAxis().isHorizontal()
                && frame.contextHitPos != null && frame.placePos != null
                && frame.placePos.getY() > frame.contextHitPos.getY()) {
            writeMismatch(frame, "placed_above_intended", "horizontal placement resolved above hit target");
            writeFinalTargetSemanticMismatch(frame, "horizontal final target resolved above hit target");
        }

        if (isDoubleSlab(frame.hitState) && frame.dyHit == -0.5d && isSlabState(frame.placeAfterState)
                && frame.dyPlaceAfter == 0.0d) {
            writeMismatch(frame, "loweredDyLostAfterDoubleTarget", "lowered double-slab target produced vanilla-dy placed slab");
            writeFinalTargetSemanticMismatch(frame, "lowered double-slab final target produced vanilla-dy placed slab");
        }
    }

    private static void writeFinalTargetSemanticMismatch(ActionFrame frame, String detail) {
        writeMismatch(frame, "finalTargetPlacementSemanticMismatch", detail);
    }

    private static void compareClientServer(ActionFrame frame) {
        if (frame.sequence == null || frame.placePos == null) {
            return;
        }
        PlacementSummary summary = new PlacementSummary(frame.actionId, frame.sequence, frame.side, frame.placePos, frame.placeAfterState);
        Map<Integer, PlacementSummary> own = "CLIENT".equals(frame.side) ? CLIENT_PLACE_BY_SEQUENCE : SERVER_PLACE_BY_SEQUENCE;
        Map<Integer, PlacementSummary> other = "CLIENT".equals(frame.side) ? SERVER_PLACE_BY_SEQUENCE : CLIENT_PLACE_BY_SEQUENCE;
        own.put(frame.sequence, summary);
        PlacementSummary peer = other.get(frame.sequence);
        if (peer != null && (!summary.placePos.equals(peer.placePos) || !safe(summary.afterState).equals(safe(peer.afterState)))) {
            writeMismatch(frame, "clientServerPlacePosMismatch",
                    "client/server placement result split peerSide=" + peer.side
                            + " peerPlacePos=" + pos(peer.placePos)
                            + " peerAfterState=" + safe(peer.afterState));
        }
    }

    private static ActionFrame requireFrame(String side) {
        ActionFrame frame = CURRENT.get();
        if (frame != null) {
            return frame;
        }
        frame = new ActionFrame("uncorrelated-" + NEXT_ACTION.incrementAndGet(), side);
        CURRENT.set(frame);
        rememberFrame(frame);
        incHealth("uncorrelatedFrames");
        return frame;
    }

    private static void rememberFrame(ActionFrame frame) {
        ACTION_LEDGER.put(actionKey(frame), frame);
    }

    private static String actionKey(ActionFrame frame) {
        return frame.actionId + "|" + frame.side;
    }

    private static void writeActionsSnapshot() {
        StringBuilder sb = new StringBuilder();
        sb.append(actionHeader());
        int rows = 0;
        for (ActionFrame frame : ACTION_LEDGER.values()) {
            if (!frame.shouldWriteActionRow()) {
                continue;
            }
            appendActionRow(sb, frame);
            rows++;
        }
        setHealth("actionRows", rows);
        writeAtomic(actionsPath, sb.toString(), "actions");
    }

    private static String actionHeader() {
        return "row\tactionId\tsequence\tside\thand\theldItem\tfinalTarget\tinteractionHit\tpacketHit\tcontextHitPos\tcontextSide\tplacePos\tbeforeState\tslabPlacementState\tafterState\tafterSlabType\tdyHit\tdyPlaceAfter\tremapMode\teffectiveFace\tremapAccepted\tactionResult\n";
    }

    private static void appendActionRow(StringBuilder sb, ActionFrame frame) {
        if (frame.actionRow == 0L) {
            frame.actionRow = rowId();
        }
        sb.append(frame.actionRow).append('\t')
                .append(tsv(frame.actionId)).append('\t')
                .append(tsv(frame.sequence == null ? "" : Integer.toString(frame.sequence))).append('\t')
                .append(tsv(frame.side)).append('\t')
                .append(tsv(frame.hand)).append('\t')
                .append(tsv(frame.heldItem)).append('\t')
                .append(tsv(frame.target == null ? "" : frame.target.finalHit)).append('\t')
                .append(tsv(frame.interactionHit)).append('\t')
                .append(tsv(frame.packetHit)).append('\t')
                .append(tsv(pos(frame.contextHitPos))).append('\t')
                .append(tsv(safe(frame.contextSide))).append('\t')
                .append(tsv(pos(frame.placePos))).append('\t')
                .append(tsv(frame.placeBeforeState)).append('\t')
                .append(tsv(frame.slabPlacementState)).append('\t')
                .append(tsv(frame.placeAfterState)).append('\t')
                .append(tsv(frame.placeAfterSlabType)).append('\t')
                .append(tsv(formatDouble(frame.dyHit))).append('\t')
                .append(tsv(formatDouble(frame.dyPlaceAfter))).append('\t')
                .append(tsv(frame.remapMode)).append('\t')
                .append(tsv(safe(frame.remapEffectiveSide))).append('\t')
                .append(tsv(Boolean.toString(frame.remapAccepted))).append('\t')
                .append(tsv(frame.actionResult))
                .append('\n');
    }

    private static void writeMismatch(ActionFrame frame, String counter, String detail) {
        String key = counter + "|"
                + (frame == null ? "" : frame.actionId) + "|"
                + (frame == null ? "" : frame.side) + "|"
                + (frame == null ? "" : pos(frame.contextHitPos)) + "|"
                + (frame == null ? "" : pos(frame.placePos)) + "|"
                + detail;
        if (!MISMATCH_KEYS.add(key)) {
            return;
        }
        COUNTERS.put(counter, COUNTERS.getOrDefault(counter, 0) + 1);
        incHealth("mismatchRows");
        append(mismatchesPath,
                rowId() + "\t"
                        + tsv(frame == null ? "" : frame.actionId) + "\t"
                        + tsv(frame == null || frame.sequence == null ? "" : Integer.toString(frame.sequence)) + "\t"
                        + tsv(counter) + "\t"
                        + tsv(frame == null ? "" : frame.side) + "\t"
                        + tsv(detail) + "\t"
                        + tsv(frame == null ? "" : pos(frame.contextHitPos)) + "\t"
                        + tsv(frame == null ? "" : pos(frame.placePos)) + "\t"
                        + tsv(frame == null ? "" : frame.placeBeforeState) + "\t"
                        + tsv(frame == null ? "" : frame.placeAfterState)
                        + "\n");
        writeSession("mismatch", frame, frame == null ? "" : frame.side, fields(
                "counter", counter,
                "detail", detail));
    }

    private static void writeSession(String stage, ActionFrame frame, String side, Map<String, String> fields) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        jsonField(sb, "row", Long.toString(NEXT_ROW.incrementAndGet()), false);
        jsonField(sb, "schemaVersion", SCHEMA_VERSION, true);
        jsonField(sb, "runId", RUN_ID, true);
        jsonField(sb, "time", Instant.now().toString(), true);
        jsonField(sb, "stage", stage, true);
        jsonField(sb, "actionId", frame == null ? null : frame.actionId, true);
        jsonField(sb, "sequence", frame == null || frame.sequence == null ? null : Integer.toString(frame.sequence), true);
        jsonField(sb, "side", side, true);
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            jsonField(sb, entry.getKey(), entry.getValue(), true);
        }
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        sb.append("}\n");
        if (append(sessionPath, sb.toString())) {
            incHealth("sessionRows");
        }
    }

    private static void writeSummary() {
        Instant now = Instant.now();
        String counters = compactMap(COUNTERS);
        String health = compactMap(HEALTH);
        String json = "{"
                + jsonPair("schemaVersion", SCHEMA_VERSION) + ","
                + jsonPair("runId", RUN_ID) + ","
                + jsonPair("recorderVersion", RECORDER_VERSION) + ","
                + jsonPair("generatedAt", now.toString()) + ","
                + jsonPair("dir", outputDir.toAbsolutePath().toString()) + ","
                + jsonPair("runEnded", Boolean.toString(runEnded)) + ","
                + "\"redCounters\":" + counters + ","
                + "\"health\":" + health
                + "}\n";

        StringBuilder sb = new StringBuilder();
        sb.append("# Live Cursor Intent Recorder Summary\n\n");
        sb.append("dir: ").append(outputDir.toAbsolutePath()).append('\n');
        sb.append("runId: ").append(RUN_ID).append('\n');
        sb.append("schemaVersion: ").append(SCHEMA_VERSION).append('\n');
        sb.append("recorderVersion: ").append(RECORDER_VERSION).append('\n');
        sb.append("generatedAt: ").append(now).append('\n');
        sb.append("runEnded: ").append(runEnded).append("\n\n");
        sb.append("## Red Counters\n");
        sb.append("note: rawFinalTargetVsInteractionHitMismatch only compares recorded hit identity; ")
                .append("finalTargetPlacementSemanticMismatch tracks target-consistent placement outcomes that still landed wrong.\n");
        for (String counter : RED_COUNTERS) {
            sb.append("- ").append(counter).append(": ").append(COUNTERS.getOrDefault(counter, 0)).append('\n');
        }
        sb.append("\n## Recorder Health\n");
        for (String counter : HEALTH_COUNTERS) {
            sb.append("- ").append(counter).append(": ").append(HEALTH.getOrDefault(counter, 0)).append('\n');
        }
        boolean jsonWritten = writeAtomic(summaryJsonPath, json, "summary_json");
        boolean markdownWritten = writeAtomic(summaryPath, sb.toString(), "summary");
        if (jsonWritten && markdownWritten) {
            incHealth("summaryWrites");
        } else {
            incHealth("summaryWriteFailures");
        }
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        String configured = System.getProperty(DIR_PROPERTY);
        outputDir = configured == null || configured.isBlank()
                ? FabricLoader.getInstance().getGameDir().resolve("live-cursor-recorder")
                : Path.of(configured);
        manifestPath = outputDir.resolve("manifest.json");
        sessionPath = outputDir.resolve("session.jsonl");
        actionsPath = outputDir.resolve("actions.tsv");
        mismatchesPath = outputDir.resolve("mismatches.tsv");
        summaryPath = outputDir.resolve("summary.md");
        summaryJsonPath = outputDir.resolve("summary.json");
        try {
            Files.createDirectories(outputDir);
            ensureHeader(actionsPath, actionHeader());
            ensureHeader(mismatchesPath,
                    "row\tactionId\tsequence\tcounter\tside\tdetail\tcontextHitPos\tplacePos\tbeforeState\tafterState\n");
            if (!Files.exists(sessionPath)) {
                Files.createFile(sessionPath);
            }
        } catch (IOException e) {
            markIoFailure("initialize recorder dir=" + outputDir, e);
        }
        initialized = true;
    }

    private static void ensureHeader(Path path, String header) throws IOException {
        if (!Files.exists(path) || Files.size(path) == 0L) {
            Files.writeString(path, header, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(
                LiveCursorIntentRecorder::recordShutdown,
                "slabbed-live-cursor-recorder-shutdown"));
    }

    private static void writeManifest() {
        String manifest = "{"
                + jsonPair("schemaVersion", SCHEMA_VERSION) + ","
                + jsonPair("runId", RUN_ID) + ","
                + jsonPair("recorder", "LiveCursorIntentRecorder") + ","
                + jsonPair("recorderVersion", RECORDER_VERSION) + ","
                + jsonPair("enabled", Boolean.toString(ENABLED)) + ","
                + jsonPair("createdAt", Instant.now().toString()) + ","
                + jsonPair("dir", outputDir.toAbsolutePath().toString()) + ","
                + jsonPair("gameDir", FabricLoader.getInstance().getGameDir().toAbsolutePath().toString()) + ","
                + jsonPair("javaVersion", System.getProperty("java.version", "unknown")) + ","
                + jsonPair("javaVmName", System.getProperty("java.vm.name", "unknown")) + ","
                + jsonPair("minecraftVersion", modVersion("minecraft")) + ","
                + jsonPair("fabricLoaderVersion", modVersion("fabricloader")) + ","
                + jsonPair("slabbedVersion", modVersion(Slabbed.MOD_ID)) + ","
                + jsonPair("userDir", System.getProperty("user.dir", "")) + ","
                + jsonPair("javaCommand", System.getProperty("sun.java.command", ""))
                + "}\n";
        if (writeAtomic(manifestPath, manifest, "manifest")) {
            incHealth("manifestWrites");
        } else {
            incHealth("manifestWriteFailures");
        }
    }

    private static boolean writeAtomic(Path path, String content, String label) {
        if (ioDisabled) {
            incHealth("droppedRows");
            return false;
        }
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            markIoFailure(label + " " + path, e);
            return false;
        }
    }

    private static void markIoFailure(String operation, IOException e) {
        incHealth("ioErrors");
        incHealth("appendFailures");
        ioDisabled = true;
        if (!ioErrorLogged) {
            ioErrorLogged = true;
            Slabbed.LOGGER.warn("[LIVE_CURSOR_INTENT_RECORDER_IO_ERROR] disabled recorder sink after {}", operation, e);
        }
    }

    private static boolean append(Path path, String line) {
        if (ioDisabled) {
            incHealth("droppedRows");
            return false;
        }
        try {
            Files.writeString(path, line, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            markIoFailure("append " + path, e);
            return false;
        }
    }

    private static void incHealth(String key) {
        HEALTH.put(key, HEALTH.getOrDefault(key, 0) + 1);
    }

    private static void setHealth(String key, int value) {
        HEALTH.put(key, value);
    }

    private static String compactMap(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(json(entry.getKey())).append("\":").append(entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    private static String jsonPair(String key, String value) {
        return "\"" + json(key) + "\":\"" + json(safe(value)) + "\"";
    }

    private static String modVersion(String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static Map<String, String> fields(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    private static void jsonField(StringBuilder sb, String key, String value, boolean commaBefore) {
        if (commaBefore) {
            sb.append(',');
        }
        sb.append('"').append(json(key)).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(json(value)).append('"');
        }
    }

    private static String nextActionId() {
        return "a" + NEXT_ACTION.incrementAndGet();
    }

    private static long rowId() {
        return NEXT_ROW.incrementAndGet();
    }

    private static boolean differs(BlockPos aPos, Direction aSide, BlockPos bPos, Direction bSide) {
        if (aPos == null || bPos == null) {
            return aPos != bPos;
        }
        return !aPos.equals(bPos) || aSide != bSide;
    }

    private static BlockPos expectedSidePlace(ActionFrame frame) {
        if (frame.remapTargetPos != null && frame.remapEffectiveSide != null
                && frame.remapEffectiveSide.getAxis().isHorizontal()) {
            return frame.remapTargetPos.offset(frame.remapEffectiveSide);
        }
        if (frame.contextHitPos != null && frame.contextSide != null
                && frame.contextSide.getAxis().isHorizontal()) {
            return frame.contextHitPos.offset(frame.contextSide);
        }
        return null;
    }

    private static String neighborhood(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        appendNeighbor(sb, world, pos, "center");
        appendNeighbor(sb, world, pos.up(), "up");
        appendNeighbor(sb, world, pos.down(), "down");
        for (Direction direction : Direction.Type.HORIZONTAL) {
            appendNeighbor(sb, world, pos.offset(direction), direction.asString());
        }
        return sb.toString();
    }

    private static void appendNeighbor(StringBuilder sb, World world, BlockPos pos, String label) {
        BlockState state = world.getBlockState(pos);
        if (!sb.isEmpty()) {
            sb.append('|');
        }
        sb.append(label)
                .append('=').append(pos(pos))
                .append(':').append(stateSummary(state))
                .append(":dy=").append(formatDouble(safeDy(world, pos, state)))
                .append(":anchored=").append(SlabAnchorAttachment.isAnchored(world, pos));
    }

    private static String hitSummary(HitResult hit) {
        if (hit == null) {
            return "null";
        }
        if (hit instanceof BlockHitResult blockHit) {
            return "type=" + blockHit.getType()
                    + " pos=" + pos(blockHit.getBlockPos())
                    + " side=" + safe(blockHit.getSide())
                    + " hitVec=" + vec(blockHit.getPos())
                    + " inside=" + blockHit.isInsideBlock();
        }
        return "type=" + hit.getType() + " hitVec=" + vec(hit.getPos());
    }

    private static BlockPos blockPosOf(HitResult hit) {
        return hit instanceof BlockHitResult blockHit ? blockHit.getBlockPos() : null;
    }

    private static Direction sideOf(HitResult hit) {
        return hit instanceof BlockHitResult blockHit ? blockHit.getSide() : null;
    }

    private static String playerSummary(PlayerEntity player) {
        if (player == null) {
            return "null";
        }
        return player.getUuidAsString()
                + " pos=" + vec(new Vec3d(player.getX(), player.getY(), player.getZ()))
                + " eye=" + vec(player.getEyePos())
                + " yaw=" + formatDouble(player.getYaw())
                + " pitch=" + formatDouble(player.getPitch());
    }

    private static double safeDy(World world, BlockPos pos, BlockState state) {
        try {
            return SlabSupport.getYOffset(world, pos, state);
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    private static String itemId(ItemStack stack) {
        if (stack == null) {
            return "null";
        }
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    private static String stateSummary(BlockState state) {
        if (state == null) {
            return "null";
        }
        return state.toString();
    }

    private static boolean isSlabState(String state) {
        return state != null && state.contains("_slab");
    }

    private static boolean isSingleSlab(String state) {
        return state != null && state.contains("_slab") && (state.contains("type=top") || state.contains("type=bottom"));
    }

    private static boolean isDoubleSlab(String state) {
        return state != null && state.contains("_slab") && state.contains("type=double");
    }

    private static String slabType(BlockState state) {
        if (state != null && state.getBlock() instanceof SlabBlock && state.contains(SlabBlock.TYPE)) {
            SlabType type = state.get(SlabBlock.TYPE);
            return type.name().toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private static String pos(BlockPos pos) {
        return pos == null ? "" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String vec(Vec3d vec) {
        return vec == null ? "" : formatDouble(vec.x) + "," + formatDouble(vec.y) + "," + formatDouble(vec.z);
    }

    private static String formatDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.toString(value);
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String tsv(String value) {
        return safe(value).replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class ActionFrame {
        final String actionId;
        final String side;
        long actionRow;
        Integer sequence;
        String hand = "";
        String player = "";
        String heldItem = "";
        TargetFrame target;
        String interactionHit = "";
        BlockPos interactionHitPos;
        Direction interactionSide;
        String packetHit = "";
        BlockPos packetHitPos;
        Direction packetSide;
        BlockPos remapTargetPos;
        Direction remapOriginalSide;
        Direction remapEffectiveSide;
        String remapHit = "";
        String remapMode = "";
        String remapRejection = "";
        boolean remapAccepted;
        String remappedHit = "";
        double targetDy = Double.NaN;
        BlockPos contextHitPos;
        Direction contextSide;
        BlockPos placePos;
        String hitState = "";
        String placeBeforeState = "";
        String slabPlacementState = "";
        String slabPlacementType = "";
        String placeAfterState = "";
        String placeAfterSlabType = "";
        double dyHit = Double.NaN;
        double dyPlaceBefore = Double.NaN;
        double dyPlaceAfter = Double.NaN;
        boolean canReplaceExisting;
        String actionResult = "";
        String neighborhood = "";
        boolean placementResultRecorded;
        boolean clientReturnRecorded;
        boolean serverReturnRecorded;

        ActionFrame(String actionId, String side) {
            this.actionId = actionId;
            this.side = side;
        }

        boolean shouldWriteActionRow() {
            return placementResultRecorded || clientReturnRecorded || serverReturnRecorded;
        }
    }

    private record TargetFrame(
            String initialHit,
            String finalHit,
            BlockPos finalBlockPos,
            Direction finalSide,
            String decision,
            boolean sideSlabRetargetFired,
            boolean slabHeld,
            String eye,
            String look,
            String heldItem,
            float tickProgress
    ) {
    }

    private record PlacementSummary(
            String actionId,
            int sequence,
            String side,
            BlockPos placePos,
            String afterState
    ) {
    }
}
