package com.slabbed.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class PlacementIntentState {
    private static final Object LOCK = new Object();
    private static final long MAX_SNAPSHOT_LIFETIME_NANOS = 5_000_000_000L;
    private static Snapshot snapshot;
    private static long sequence;

    private PlacementIntentState() {
    }

    public enum Mode {
        NORMAL_FINAL_TARGET,
        FINAL_TARGET_UNKNOWN_EXPECTED_PLACE_KNOWN
    }

    public record Snapshot(
            long sequence,
            long nanoTime,
            String dimensionId,
            String heldItemId,
            BlockPos originalVisibleOwnerPos,
            Direction originalVisibleFace,
            Vec3 originalVisibleHit,
            String originalVisibleState,
            BlockPos expectedPlacePos,
            Direction expectedPlaceFace,
            BlockPos finalTargetPos,
            Direction finalTargetFace,
            Vec3 finalTargetHit,
            String finalTargetState,
            Mode mode,
            String reason
    ) {
    }

    public static void set(
            String dimensionId,
            String heldItemId,
            BlockPos originalVisibleOwnerPos,
            Direction originalVisibleFace,
            Vec3 originalVisibleHit,
            String originalVisibleState,
            BlockPos expectedPlacePos,
            Direction expectedPlaceFace,
            BlockPos finalTargetPos,
            Direction finalTargetFace,
            Vec3 finalTargetHit,
            String finalTargetState,
            String reason
    ) {
        set(
                dimensionId,
                heldItemId,
                originalVisibleOwnerPos,
                originalVisibleFace,
                originalVisibleHit,
                originalVisibleState,
                expectedPlacePos,
                expectedPlaceFace,
                finalTargetPos,
                finalTargetFace,
                finalTargetHit,
                finalTargetState,
                Mode.NORMAL_FINAL_TARGET,
                reason);
    }

    public static void set(
            String dimensionId,
            String heldItemId,
            BlockPos originalVisibleOwnerPos,
            Direction originalVisibleFace,
            Vec3 originalVisibleHit,
            String originalVisibleState,
            BlockPos expectedPlacePos,
            Direction expectedPlaceFace,
            BlockPos finalTargetPos,
            Direction finalTargetFace,
            Vec3 finalTargetHit,
            String finalTargetState,
            Mode mode,
            String reason
    ) {
        Mode snapshotMode = mode == null ? Mode.NORMAL_FINAL_TARGET : mode;
        boolean finalTargetKnown = finalTargetPos != null
                && finalTargetFace != null
                && finalTargetHit != null
                && finalTargetState != null;
        boolean finalTargetUnknownExpectedPlaceKnown = snapshotMode == Mode.FINAL_TARGET_UNKNOWN_EXPECTED_PLACE_KNOWN;
        if (dimensionId == null
                || heldItemId == null
                || originalVisibleOwnerPos == null
                || originalVisibleFace == null
                || originalVisibleHit == null
                || originalVisibleState == null
                || expectedPlacePos == null
                || expectedPlaceFace == null
                || (!finalTargetKnown && !finalTargetUnknownExpectedPlaceKnown)
                || (finalTargetUnknownExpectedPlaceKnown && !isPlacementRelevantHeldItem(heldItemId))) {
            clear();
            return;
        }
        Snapshot next = new Snapshot(
                ++sequence,
                System.nanoTime(),
                dimensionId,
                heldItemId,
                originalVisibleOwnerPos.immutable(),
                originalVisibleFace,
                originalVisibleHit,
                originalVisibleState,
                expectedPlacePos.immutable(),
                expectedPlaceFace,
                finalTargetPos == null ? null : finalTargetPos.immutable(),
                finalTargetFace,
                finalTargetHit,
                finalTargetState,
                snapshotMode,
                reason == null ? "unknown" : reason);
        synchronized (LOCK) {
            snapshot = next;
        }
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            return snapshot;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            snapshot = null;
        }
    }

    public static void clear(String reason) {
        clear();
    }

    public static void onPickStart(String details) {
        Snapshot current;
        synchronized (LOCK) {
            current = snapshot;
        }
        if (current != null && System.nanoTime() - current.nanoTime() > MAX_SNAPSHOT_LIFETIME_NANOS) {
            clear();
        }
    }

    public static void auditConsume(Snapshot current, String details) {
    }

    public static void auditReject(String rejectReason, Snapshot current, String details) {
    }

    public static void auditApply(Snapshot current, String details) {
    }

    public static void auditProducerEval(String decision, String reason, String details) {
    }

    public static void auditProducerSkip(String decision, String reason, String details) {
    }

    public static void auditBoundary(String event, String reason, String details) {
    }

    public static String lastProducerDetails() {
        return "";
    }

    public static boolean isAuditEnabled() {
        return false;
    }

    private static boolean isPlacementRelevantHeldItem(String heldItemId) {
        return "minecraft:stone".equals(heldItemId) || "minecraft:stone_slab".equals(heldItemId);
    }
}
