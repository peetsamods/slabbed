package com.slabbed.client;

import com.slabbed.network.PlacementDyPredictionBridge;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** GameTest-owned orchestration and snapshots for the production journal. */
public final class PlacementDyPredictionJournalGameTestAccess {
    private static boolean probeEnabled;
    private static boolean failNextDeclaration;
    private static int backingWrites;
    private static boolean authorityObservedWhileOwned;
    private static final LinkedHashMap<Long, PlacementDyPredictionJournal.ReconcileBranch>
            BRANCHES = new LinkedHashMap<>();
    private static final Field STAGED = field("STAGED");
    private static final Field GROUPS = field("GROUPS");
    private static final Field OVERLAY_OWNER = field("OVERLAY_OWNER");
    private static final Field CORRECTIONS = field("CORRECTIONS");
    private static final Field ACKNOWLEDGED_THROUGH = field("acknowledgedThrough");
    private static final Field GENERATION = field("generation");
    private static final Method RESET = method("reset", ClientLevel.class);
    private static final Method ENSURE_CURRENT_LEVEL = method("ensureCurrentLevel", ClientLevel.class);
    private static final Method INSTALL_GROUP = method(
            "installGroup",
            ClientLevel.class,
            long.class,
            PlacementDyPredictionBridge.PredictedBatch.class);
    private static final Method CLEANUP_CHUNK =
            method("cleanupChunk", ClientLevel.class, int.class, int.class);

    private PlacementDyPredictionJournalGameTestAccess() {
    }

    public record TestSnapshot(
            int stagedGroups,
            int activeGroups,
            int overlayOwners,
            int bufferedCorrections,
            int acknowledgedThrough,
            int backingWrites,
            boolean authorityObservedWhileOwned,
            Map<Long, PlacementDyPredictionJournal.ReconcileBranch> branches) {
        public TestSnapshot {
            branches = Map.copyOf(branches);
        }
    }

    public static synchronized void beginTestProbe(ClientLevel level) {
        invokeVoid(RESET, level);
        probeEnabled = true;
        failNextDeclaration = false;
        backingWrites = 0;
        authorityObservedWhileOwned = false;
        BRANCHES.clear();
        SlabAnchorClientSync.beginC3RerenderProbeForTests();
    }

    public static synchronized void endTestProbe(ClientLevel level) {
        invokeVoid(RESET, level);
        probeEnabled = false;
        failNextDeclaration = false;
        backingWrites = 0;
        authorityObservedWhileOwned = false;
        BRANCHES.clear();
        SlabAnchorClientSync.stopC3RerenderProbeForTests();
    }

    public static synchronized void resetForTests(ClientLevel level) {
        invokeVoid(RESET, level);
    }

    public static synchronized void failNextDeclarationForTests() {
        failNextDeclaration = true;
    }

    public static TestSnapshot testSnapshot() {
        synchronized (PlacementDyPredictionJournal.class) {
            return new TestSnapshot(
                    staged().size(),
                    groups().size(),
                    overlayOwnersForMixin().size(),
                    corrections().size(),
                    integer(ACKNOWLEDGED_THROUGH),
                    backingWrites,
                    authorityObservedWhileOwned,
                    BRANCHES);
        }
    }

    public static void installBatchForTests(
            ClientLevel level,
            PlacementDyPredictionBridge.PredictedBatch batch) {
        List<BlockPos> rerenders;
        synchronized (PlacementDyPredictionJournal.class) {
            invokeVoid(ENSURE_CURRENT_LEVEL, level);
            rerenders = installGroup(
                    level,
                    longValue(GENERATION),
                    batch);
        }
        rerenders.forEach(SlabAnchorClientSync::scheduleExactPlacementDyRerender);
    }

    public static void commitStagedBatchForTests(ClientLevel level, int sequence) {
        List<BlockPos> rerenders;
        synchronized (PlacementDyPredictionJournal.class) {
            invokeVoid(ENSURE_CURRENT_LEVEL, level);
            PlacementDyPredictionBridge.PredictedBatch batch = staged().remove(sequence);
            if (batch == null || batch.signature().sequence() != sequence) {
                throw new IllegalStateException("missing staged C3 test batch " + sequence);
            }
            rerenders = installGroup(
                    level,
                    longValue(GENERATION),
                    batch);
        }
        rerenders.forEach(SlabAnchorClientSync::scheduleExactPlacementDyRerender);
    }

    public static void unloadChunkForTests(
            ClientLevel level,
            int chunkX,
            int chunkZ) {
        invokeVoid(CLEANUP_CHUNK, level, chunkX, chunkZ);
    }

    public static synchronized boolean consumeFailNextDeclaration() {
        if (!failNextDeclaration) {
            return false;
        }
        failNextDeclaration = false;
        return true;
    }

    public static synchronized void recordBranch(
            long key,
            PlacementDyPredictionJournal.ReconcileBranch branch) {
        if (probeEnabled) {
            BRANCHES.put(key, branch);
        }
    }

    public static synchronized void recordApply(
            PlacementDyPredictionBridge.GroupSignature signature,
            int appliedWriteCount,
            List<BlockPos> ownedCells,
            Map<Long, PlacementDyPredictionBridge.GroupSignature> overlayOwners) {
        if (!probeEnabled) {
            return;
        }
        backingWrites += appliedWriteCount;
        authorityObservedWhileOwned |= !ownedCells.isEmpty()
                && ownedCells.stream().allMatch(pos ->
                signature.equals(overlayOwners.get(pos.asLong())));
    }

    @SuppressWarnings("unchecked")
    public static Map<Long, PlacementDyPredictionBridge.GroupSignature> overlayOwnersForMixin() {
        return (Map<Long, PlacementDyPredictionBridge.GroupSignature>) get(OVERLAY_OWNER);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, PlacementDyPredictionBridge.PredictedBatch> staged() {
        return (Map<Integer, PlacementDyPredictionBridge.PredictedBatch>) get(STAGED);
    }

    private static Map<?, ?> groups() {
        return (Map<?, ?>) get(GROUPS);
    }

    private static Map<?, ?> corrections() {
        return (Map<?, ?>) get(CORRECTIONS);
    }

    @SuppressWarnings("unchecked")
    private static List<BlockPos> installGroup(
            ClientLevel level,
            long generation,
            PlacementDyPredictionBridge.PredictedBatch batch) {
        return (List<BlockPos>) invoke(INSTALL_GROUP, level, generation, batch);
    }

    private static int integer(Field field) {
        return (int) get(field);
    }

    private static long longValue(Field field) {
        return (long) get(field);
    }

    private static Field field(String name) {
        try {
            Field field = PlacementDyPredictionJournal.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Method method(String name, Class<?>... parameterTypes) {
        try {
            Method method = PlacementDyPredictionJournal.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Object get(Field field) {
        try {
            return field.get(null);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("GameTest journal field access failed", exception);
        }
    }

    private static void invokeVoid(Method method, Object... arguments) {
        invoke(method, arguments);
    }

    private static Object invoke(Method method, Object... arguments) {
        try {
            return method.invoke(null, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("GameTest journal method access failed", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("GameTest journal method failed", cause);
        }
    }
}
