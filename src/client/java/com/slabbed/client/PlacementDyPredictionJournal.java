package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.network.PlacementDyCorrectionPayload;
import com.slabbed.network.PlacementDyPredictionBridge;
import com.slabbed.network.PlacementDyPredictionEnvelopePayload;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sequence-owned client prediction overlay above the untouched authoritative placement-dy map.
 * Correction and vanilla acknowledgement are independent signals; only their rendezvous may
 * reconcile exact backing facts and retire one matching group.
 */
public final class PlacementDyPredictionJournal {
    public enum ReconcileBranch {
        CAPTURED_PRIOR_APPLIED,
        ALREADY_EQUAL,
        THIRD_STATE_PRESERVED,
        SUPERSEDED
    }

    public record CellDebug(
            boolean groupPresent,
            boolean overlayOwned,
            int highWaterSequence,
            boolean correctionBuffered,
            boolean acknowledged,
            SlabAnchorAttachment.PlacementDyFact effective,
            SlabAnchorAttachment.PlacementDyFact backing
    ) {
    }

    private static final class Group {
        final ClientLevel level;
        final long generation;
        final PlacementDyPredictionBridge.GroupSignature signature;
        final LinkedHashMap<Long, PlacementDyPredictionBridge.PredictedCell> cells;

        Group(
                ClientLevel level,
                long generation,
                PlacementDyPredictionBridge.PredictedBatch batch
        ) {
            this.level = level;
            this.generation = generation;
            this.signature = batch.signature();
            this.cells = new LinkedHashMap<>();
            for (PlacementDyPredictionBridge.PredictedCell cell : batch.cells()) {
                this.cells.put(cell.pos().asLong(), cell);
            }
        }
    }

    private static final Map<Integer, PlacementDyPredictionBridge.PredictedBatch> STAGED = new HashMap<>();
    private static final Map<PlacementDyPredictionBridge.GroupSignature, Group> GROUPS = new LinkedHashMap<>();
    // Primitive-keyed (no Long boxing) and never iterated, so the open-hash order is irrelevant.
    private static final Long2ObjectOpenHashMap<PlacementDyPredictionBridge.GroupSignature> OVERLAY_OWNER =
            new Long2ObjectOpenHashMap<>();
    private static final Long2IntOpenHashMap HIGH_WATER = new Long2IntOpenHashMap();
    private static final Map<PlacementDyPredictionBridge.GroupSignature, PlacementDyCorrectionPayload>
            CORRECTIONS = new HashMap<>();

    /**
     * The published, immutable, lock-free view of the effective overlay for the chunk-mesh worker
     * threads (render-path fix-round F1).
     *
     * <p>WHY THIS EXISTS. {@link #effectiveFact} is reached from
     * {@code SlabSupport.getYOffset} → {@code SlabAnchorAttachment.storedPlacementDy} for every non-air
     * block during section meshing — roughly a dozen times per block once the seam probe, the cull test
     * and the per-quad cull predicate are counted. It used to be {@code static synchronized}, so every
     * one of those reads took the class-wide monitor and boxed two {@code Long} map keys, on worker
     * threads, in the hottest loop the client has.
     *
     * <p>WHY THE MONITOR COULD NOT SIMPLY BE DROPPED. {@link #GROUPS}, {@link #OVERLAY_OWNER} and
     * {@link #HIGH_WATER} are plain hash maps genuinely mutated from the client main thread while those
     * reads run. Racing a plain hash map is not "a slightly stale answer", it is undefined behaviour.
     * And the overlay exists precisely so a just-placed block renders at its predicted height
     * immediately: {@code commitAfterPrediction} schedules a targeted rerender right after
     * {@link #installGroup}, so the install MUST be visible to the very next mesh-thread read of that
     * cell or the placement flicker this class was built to prevent comes back.
     *
     * <p>HOW BOTH ARE KEPT. Every main-thread mutation still happens under the existing class monitor;
     * at the end of each one, {@link #publishOverlayCellsLocked()} rebuilds this snapshot and stores it
     * into the {@code volatile} field below. The snapshot's arrays are filled before that store and
     * never touched afterwards, so the volatile write/read pair is a happens-before edge: a worker
     * thread that reads a snapshot reference sees every array element written before it was published,
     * and it can never observe a partially-built or concurrently-mutated structure. The read side is
     * one volatile load, one reference compare against {@link #EMPTY_OVERLAY} (the common case: no
     * prediction in flight), and — only when non-empty — a binary search over a primitive {@code long[]}
     * of packed positions. Zero locks, zero boxing, zero allocation.
     *
     * <p>The {@code PlacementDyFact} for each cell is built once at publication, so even an overlay HIT
     * on a mesh thread allocates nothing. This is the same volatile-gate + published-sorted-primitive-
     * array shape the model-bake sentinel already uses for its armed-key set, for the same reason.
     */
    private record OverlayCells(
            ClientLevel level,
            long[] keys,
            SlabAnchorAttachment.PlacementDyFact[] facts
    ) {
    }

    private static final OverlayCells EMPTY_OVERLAY =
            new OverlayCells(null, new long[0], new SlabAnchorAttachment.PlacementDyFact[0]);
    private static volatile OverlayCells overlayCells = EMPTY_OVERLAY;

    private static ClientLevel currentLevel;
    private static long generation;
    private static int acknowledgedThrough = -1;
    private static boolean initialized;

    private PlacementDyPredictionJournal() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PlacementDyPredictionBridge.installClientBatchConsumer(PlacementDyPredictionJournal::stage);
        ClientPlayConnectionEvents.INIT.register((handler, client) -> reset(null));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset(null));
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> reset(level));
        ClientChunkEvents.CHUNK_UNLOAD.register(PlacementDyPredictionJournal::onChunkUnload);
    }

    /** Stage only. The batch is not effective and owns no cell until its envelope is sent. */
    public static synchronized void stage(PlacementDyPredictionBridge.PredictedBatch batch) {
        Objects.requireNonNull(batch, "batch");
        ClientLevel level = Minecraft.getInstance().level;
        ensureCurrentLevel(level);
        if (level == null || !level.dimension().toString().equals(batch.signature().dimension())) {
            throw new IllegalStateException("C3 prediction batch has no matching current client level");
        }
        int sequence = batch.signature().sequence();
        if (PlacementDyPredictionBridge.currentSequence() != sequence) {
            throw new IllegalStateException("C3 prediction batch escaped its vanilla sequence scope");
        }
        if (STAGED.putIfAbsent(sequence, batch) != null) {
            throw new IllegalStateException("duplicate C3 staged prediction sequence " + sequence);
        }
    }

    /**
     * Send the exact declaration before vanilla receives its packet, then atomically install the
     * complete overlay group. Any unsupported/failing boundary leaves the backing map untouched and
     * owns no overlay cell.
     */
    public static Packet<ServerGamePacketListener> commitAfterPrediction(
            int sequence,
            Packet<ServerGamePacketListener> packet
    ) {
        PlacementDyPredictionBridge.PredictedBatch batch;
        ClientLevel level;
        long batchGeneration;
        synchronized (PlacementDyPredictionJournal.class) {
            batch = STAGED.remove(sequence);
            if (batch == null) {
                return packet;
            }
            level = Minecraft.getInstance().level;
            ensureCurrentLevel(level);
            batchGeneration = generation;
            if (!matchesVanillaPacket(level, batch, packet)) {
                Slabbed.LOGGER.warn("[C3] staged prediction did not match returned vanilla use-item packet");
                return packet;
            }
            try {
                validateInstall(level, batchGeneration, batch);
            } catch (RuntimeException exception) {
                Slabbed.LOGGER.warn("[C3] prediction batch could not be installed; declaration not sent", exception);
                return packet;
            }
        }

        PlacementDyPredictionEnvelopePayload envelope = new PlacementDyPredictionEnvelopePayload(
                batch.signature(),
                batch.cells().stream().map(cell -> cell.pos().asLong()).toList());
        try {
            if (!ClientPlayNetworking.canSend(PlacementDyPredictionEnvelopePayload.TYPE)) {
                Slabbed.LOGGER.warn("[C3] prediction declaration channel unavailable; overlay not installed");
                return packet;
            }
            ClientPlayNetworking.send(envelope);
        } catch (RuntimeException exception) {
            Slabbed.LOGGER.warn("[C3] prediction declaration send failed; overlay not installed", exception);
            return packet;
        }

        List<BlockPos> rerenders;
        synchronized (PlacementDyPredictionJournal.class) {
            if (level != currentLevel || batchGeneration != generation) {
                Slabbed.LOGGER.warn("[C3] client level changed after declaration send; overlay not installed");
                return packet;
            }
            try {
                rerenders = installGroup(level, batchGeneration, batch);
            } catch (RuntimeException exception) {
                rollbackInstall(batch);
                Slabbed.LOGGER.warn("[C3] prediction overlay install failed", exception);
                return packet;
            }
        }
        rerenders.forEach(SlabAnchorClientSync::scheduleExactPlacementDyRerender);
        Runnable postInstallAction = batch.postInstallAction();
        if (postInstallAction != null) {
            try {
                postInstallAction.run();
            } catch (RuntimeException exception) {
                Slabbed.LOGGER.warn("[C3] post-install recorder action failed", exception);
            }
        }
        return packet;
    }

    public static synchronized void abortStaged(int sequence) {
        STAGED.remove(sequence);
    }

    public static synchronized void onCorrection(
            ClientLevel level,
            PlacementDyCorrectionPayload payload
    ) {
        if (level == null || payload == null) {
            return;
        }
        ensureCurrentLevel(level);
        PlacementDyPredictionBridge.GroupSignature signature = payload.signature();
        if (!level.dimension().toString().equals(signature.dimension())) {
            Slabbed.LOGGER.warn("[C3] ignored correction for a different client dimension");
            return;
        }
        Group group = GROUPS.get(signature);
        if (group == null || group.level != level || group.generation != generation) {
            Slabbed.LOGGER.warn("[C3] ignored correction for an unowned prediction group sequence={}",
                    signature.sequence());
            return;
        }
        Map<Long, PlacementDyCorrectionPayload.CellFact> payloadFacts = indexFacts(payload);
        for (long cell : group.cells.keySet()) {
            if (!payloadFacts.containsKey(cell)) {
                Slabbed.LOGGER.error("[C3] correction omitted declared prediction cell {}", BlockPos.of(cell));
                retireProtocolFailure(signature, group);
                return;
            }
        }
        if (CORRECTIONS.putIfAbsent(signature, payload) != null) {
            Slabbed.LOGGER.error("[C3] duplicate correction for prediction sequence {}", signature.sequence());
            retireProtocolFailure(signature, group);
            return;
        }
        reconcileReadyGroups();
    }

    /** Called only from ClientLevel.handleBlockChangedAck RETURN, after vanilla syncBlockState work. */
    public static synchronized void onVanillaAcknowledgement(ClientLevel level, int sequence) {
        if (level == null) {
            return;
        }
        ensureCurrentLevel(level);
        acknowledgedThrough = Math.max(acknowledgedThrough, sequence);
        reconcileReadyGroups();
    }

    /**
     * Overlay-first effective fact; stale, partial, retired, or superseded groups fall back to backing.
     *
     * <p>Lock-free and allocation-free (render-path fix-round F1) — see {@link OverlayCells}. This is
     * the same decision the previous {@code synchronized} body made, only pre-resolved: a cell is in the
     * published snapshot exactly when its owner group is live on the current level and generation, the
     * group still declares that cell, and the cell's high-water sequence is still the owner's. Any edit
     * to any of those republishes the snapshot under the lock, and {@link #reset} — the only writer of
     * {@code currentLevel}/{@code generation} — publishes {@link #EMPTY_OVERLAY}, so a non-empty
     * snapshot always carries the current level and generation by construction.
     */
    public static SlabAnchorAttachment.PlacementDyFact effectiveFact(
            BlockGetter view,
            BlockPos pos
    ) {
        if (pos == null) {
            return SlabAnchorAttachment.PlacementDyFact.absent();
        }
        OverlayCells overlay = overlayCells;
        if (overlay != EMPTY_OVERLAY) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && level == overlay.level()) {
                int index = Arrays.binarySearch(overlay.keys(), pos.asLong());
                if (index >= 0) {
                    return overlay.facts()[index];
                }
            }
        }
        return backingFact(view, pos);
    }

    /**
     * Rebuilds and publishes the mesh-thread snapshot. MUST be called while holding the class monitor,
     * from every path that edits {@link #GROUPS}, {@link #OVERLAY_OWNER}, {@link #HIGH_WATER},
     * {@code currentLevel} or {@code generation}. Main-thread only and rare (one placement batch, one
     * correction, one chunk unload), so rebuilding from scratch is free; correctness comes from the
     * predicate below being the literal transcription of the old per-read test.
     */
    private static void publishOverlayCellsLocked() {
        if (currentLevel == null || GROUPS.isEmpty()) {
            overlayCells = EMPTY_OVERLAY;
            return;
        }
        Long2LongOpenHashMap effective = new Long2LongOpenHashMap();
        for (Map.Entry<PlacementDyPredictionBridge.GroupSignature, Group> entry : GROUPS.entrySet()) {
            PlacementDyPredictionBridge.GroupSignature signature = entry.getKey();
            Group group = entry.getValue();
            if (group.level != currentLevel || group.generation != generation) {
                continue;
            }
            for (Map.Entry<Long, PlacementDyPredictionBridge.PredictedCell> cellEntry
                    : group.cells.entrySet()) {
                long key = cellEntry.getKey();
                if (!signature.equals(OVERLAY_OWNER.get(key))
                        || !HIGH_WATER.containsKey(key)
                        || HIGH_WATER.get(key) != signature.sequence()) {
                    continue;
                }
                effective.put(key, cellEntry.getValue().predictedRawBits());
            }
        }
        if (effective.isEmpty()) {
            overlayCells = EMPTY_OVERLAY;
            return;
        }
        long[] keys = effective.keySet().toLongArray();
        Arrays.sort(keys);
        SlabAnchorAttachment.PlacementDyFact[] facts =
                new SlabAnchorAttachment.PlacementDyFact[keys.length];
        for (int i = 0; i < keys.length; i++) {
            facts[i] = new SlabAnchorAttachment.PlacementDyFact(true, effective.get(keys[i]));
        }
        // The volatile store is the publication point: everything above happened-before it.
        overlayCells = new OverlayCells(currentLevel, keys, facts);
    }

    /** Direct authoritative backing read. This method never consults overlay ownership. */
    public static SlabAnchorAttachment.PlacementDyFact backingFact(BlockGetter view, BlockPos pos) {
        if (pos == null) {
            return SlabAnchorAttachment.PlacementDyFact.absent();
        }
        if (view instanceof ClientLevel level) {
            return SlabAnchorAttachment.rawPlacementDyFact(level, pos);
        }
        ClientLevel level = Minecraft.getInstance().level;
        return level == null
                ? SlabAnchorAttachment.PlacementDyFact.absent()
                : SlabAnchorAttachment.rawPlacementDyFact(level, pos);
    }

    public static synchronized CellDebug debugCell(BlockPos pos) {
        long key = pos.asLong();
        PlacementDyPredictionBridge.GroupSignature owner = OVERLAY_OWNER.get(key);
        Group group = owner == null ? null : GROUPS.get(owner);
        return new CellDebug(
                group != null,
                group != null && group.cells.containsKey(key),
                HIGH_WATER.getOrDefault(key, -1),
                owner != null && CORRECTIONS.containsKey(owner),
                owner != null && owner.sequence() <= acknowledgedThrough,
                effectiveFact(currentLevel, pos),
                backingFact(currentLevel, pos));
    }

    private static void validateInstall(
            ClientLevel level,
            long expectedGeneration,
            PlacementDyPredictionBridge.PredictedBatch batch
    ) {
        if (level == null || level != currentLevel || expectedGeneration != generation) {
            throw new IllegalStateException("C3 prediction install crossed a client level generation");
        }
        if (GROUPS.containsKey(batch.signature())) {
            throw new IllegalStateException("duplicate C3 prediction group signature");
        }
        int sequence = batch.signature().sequence();
        for (PlacementDyPredictionBridge.PredictedCell cell : batch.cells()) {
            long key = cell.pos().asLong();
            if (HIGH_WATER.containsKey(key) && HIGH_WATER.get(key) > sequence) {
                throw new IllegalStateException("out-of-order C3 prediction sequence");
            }
        }
    }

    private static List<BlockPos> installGroup(
            ClientLevel level,
            long expectedGeneration,
            PlacementDyPredictionBridge.PredictedBatch batch
    ) {
        validateInstall(level, expectedGeneration, batch);
        Group group = new Group(level, expectedGeneration, batch);
        GROUPS.put(group.signature, group);
        ArrayList<BlockPos> rerenders = new ArrayList<>(group.cells.size());
        for (PlacementDyPredictionBridge.PredictedCell cell : group.cells.values()) {
            long key = cell.pos().asLong();
            HIGH_WATER.put(key, group.signature.sequence());
            OVERLAY_OWNER.put(key, group.signature);
            rerenders.add(cell.pos());
        }
        // Publish BEFORE the caller schedules the targeted rerender: the mesh thread that services it
        // must already see this install, or the just-placed block bakes at its pre-prediction height.
        publishOverlayCellsLocked();
        return List.copyOf(rerenders);
    }

    private static void rollbackInstall(PlacementDyPredictionBridge.PredictedBatch batch) {
        PlacementDyPredictionBridge.GroupSignature signature = batch.signature();
        GROUPS.remove(signature);
        CORRECTIONS.remove(signature);
        for (PlacementDyPredictionBridge.PredictedCell cell : batch.cells()) {
            long key = cell.pos().asLong();
            OVERLAY_OWNER.remove(key, signature);
            HIGH_WATER.remove(key, signature.sequence());
        }
        publishOverlayCellsLocked();
    }

    private static boolean matchesVanillaPacket(
            ClientLevel level,
            PlacementDyPredictionBridge.PredictedBatch batch,
            Packet<ServerGamePacketListener> packet
    ) {
        if (level == null || !(packet instanceof ServerboundUseItemOnPacket useItemOn)) {
            return false;
        }
        PlacementDyPredictionBridge.GroupSignature signature = batch.signature();
        return signature.sequence() == useItemOn.getSequence()
                && signature.hand() == useItemOn.getHand()
                && signature.originalHitBlockPos() == useItemOn.getHitResult().getBlockPos().asLong()
                && signature.originalHitFace() == useItemOn.getHitResult().getDirection()
                && signature.dimension().equals(level.dimension().toString());
    }

    private static Map<Long, PlacementDyCorrectionPayload.CellFact> indexFacts(
            PlacementDyCorrectionPayload payload
    ) {
        LinkedHashMap<Long, PlacementDyCorrectionPayload.CellFact> indexed = new LinkedHashMap<>();
        for (PlacementDyCorrectionPayload.CellFact fact : payload.facts()) {
            indexed.put(fact.pos(), fact);
        }
        return indexed;
    }

    private static void retireProtocolFailure(
            PlacementDyPredictionBridge.GroupSignature signature,
            Group group
    ) {
        GROUPS.remove(signature, group);
        CORRECTIONS.remove(signature);
        ArrayList<BlockPos> rerenders = new ArrayList<>();
        for (Map.Entry<Long, PlacementDyPredictionBridge.PredictedCell> entry : group.cells.entrySet()) {
            if (OVERLAY_OWNER.remove(entry.getKey().longValue(), signature)) {
                rerenders.add(entry.getValue().pos());
            }
        }
        publishOverlayCellsLocked();
        rerenders.forEach(SlabAnchorClientSync::scheduleExactPlacementDyRerender);
    }

    private static void reconcileReadyGroups() {
        ArrayList<PlacementDyPredictionBridge.GroupSignature> ready = new ArrayList<>();
        for (Map.Entry<PlacementDyPredictionBridge.GroupSignature, Group> entry : GROUPS.entrySet()) {
            PlacementDyPredictionBridge.GroupSignature signature = entry.getKey();
            if (signature.sequence() <= acknowledgedThrough && CORRECTIONS.containsKey(signature)) {
                ready.add(signature);
            }
        }
        for (PlacementDyPredictionBridge.GroupSignature signature : ready) {
            reconcile(signature);
        }
    }

    private static void reconcile(PlacementDyPredictionBridge.GroupSignature signature) {
        Group group = GROUPS.get(signature);
        PlacementDyCorrectionPayload correction = CORRECTIONS.get(signature);
        if (group == null || correction == null || group.level != currentLevel || group.generation != generation) {
            return;
        }
        Map<Long, PlacementDyCorrectionPayload.CellFact> payloadFacts = indexFacts(correction);
        LinkedHashMap<BlockPos, SlabAnchorAttachment.PlacementDyFact> apply = new LinkedHashMap<>();
        ArrayList<BlockPos> ownedCells = new ArrayList<>();

        for (Map.Entry<Long, PlacementDyPredictionBridge.PredictedCell> entry : group.cells.entrySet()) {
            long key = entry.getKey();
            PlacementDyPredictionBridge.PredictedCell cell = entry.getValue();
            if (!signature.equals(OVERLAY_OWNER.get(key))
                    || HIGH_WATER.getOrDefault(key, -1) != signature.sequence()) {
                continue;
            }
            ownedCells.add(cell.pos());
            PlacementDyCorrectionPayload.CellFact wireFact = payloadFacts.get(key);
            if (wireFact == null) {
                Slabbed.LOGGER.error("[C3] correction lost declared cell during reconciliation {}", cell.pos());
                return;
            }
            SlabAnchorAttachment.PlacementDyFact desired = new SlabAnchorAttachment.PlacementDyFact(
                    wireFact.present(), wireFact.present() ? wireFact.rawBits() : 0L);
            SlabAnchorAttachment.PlacementDyFact current = backingFact(group.level, cell.pos());
            SlabAnchorAttachment.PlacementDyFact captured = cell.capturedBacking();
            ReconcileBranch branch;
            if (current.equals(desired)) {
                branch = ReconcileBranch.ALREADY_EQUAL;
            } else if (current.equals(captured)) {
                branch = ReconcileBranch.CAPTURED_PRIOR_APPLIED;
                apply.put(cell.pos(), desired);
            } else {
                branch = ReconcileBranch.THIRD_STATE_PRESERVED;
            }
            if (!placementEquivalent(group.level.getBlockState(cell.pos()), cell.expectedState())) {
                Slabbed.LOGGER.debug("[C3] post-ack structural state differs for {} branch={}", cell.pos(), branch);
            }
        }

        int appliedWrites = 0;
        if (!apply.isEmpty()) {
            SlabAnchorClientSync.beginJournalAuthoritativeApply();
            try {
                appliedWrites = SlabAnchorAttachment.applyClientAuthoritativePlacementDy(group.level, apply);
            } finally {
                SlabAnchorClientSync.endJournalAuthoritativeApply();
            }
        }
        if (!ownedCells.isEmpty()) {
            PlacementDyPredictionBridge.traceCorrectionWire("APPLY", signature);
        }

        GROUPS.remove(signature);
        CORRECTIONS.remove(signature);
        for (long key : group.cells.keySet()) {
            OVERLAY_OWNER.remove(key, signature);
        }
        // Ordering note (F1). The backing write above and this overlay retirement are no longer one
        // atomic step from a lock-free mesh-thread reader's point of view: for the microseconds between
        // them, a read of one of these cells still answers with the PREDICTED height. That is the
        // deliberate ordering — the predicted height is exactly what is already on screen, so the
        // window shows continuity rather than a flash of the pre-correction backing value; and every
        // owned cell is force-rerendered on the next line, after the publication, so the authoritative
        // height always wins. The alternative (publish first) would widen the same window onto the
        // STALE backing value, which is strictly worse.
        publishOverlayCellsLocked();
        ownedCells.forEach(SlabAnchorClientSync::scheduleExactPlacementDyRerender);
    }

    private static boolean placementEquivalent(BlockState actual, BlockState expected) {
        if (actual == null || expected == null || actual.getBlock() != expected.getBlock()) {
            return false;
        }
        for (Property<?> property : expected.getProperties()) {
            if ((expected.getBlock() instanceof DoorBlock
                    && (property.equals(BlockStateProperties.OPEN)
                    || property.equals(BlockStateProperties.POWERED)))
                    || (expected.getBlock() instanceof BedBlock
                    && property.equals(BlockStateProperties.OCCUPIED))
                    || property.equals(BlockStateProperties.WATERLOGGED)) {
                continue;
            }
            if (!sameProperty(actual, expected, property)) {
                return false;
            }
        }
        return true;
    }

    private static <T extends Comparable<T>> boolean sameProperty(
            BlockState actual,
            BlockState expected,
            Property<T> property
    ) {
        return actual.hasProperty(property)
                && Objects.equals(actual.getValue(property), expected.getValue(property));
    }

    private static void onChunkUnload(ClientLevel level, LevelChunk chunk) {
        if (chunk != null) {
            cleanupChunk(level, chunk.getPos().x(), chunk.getPos().z());
        }
    }

    private static void cleanupChunk(ClientLevel level, int unloadingX, int unloadingZ) {
        ArrayList<BlockPos> rerenders = new ArrayList<>();
        synchronized (PlacementDyPredictionJournal.class) {
            if (level == null || level != currentLevel) {
                return;
            }
            STAGED.entrySet().removeIf(entry -> batchTouchesChunk(entry.getValue(), unloadingX, unloadingZ));
            ArrayList<PlacementDyPredictionBridge.GroupSignature> retire = new ArrayList<>();
            for (Map.Entry<PlacementDyPredictionBridge.GroupSignature, Group> entry : GROUPS.entrySet()) {
                if (groupTouchesChunk(entry.getValue(), unloadingX, unloadingZ)) {
                    retire.add(entry.getKey());
                }
            }
            for (PlacementDyPredictionBridge.GroupSignature signature : retire) {
                Group group = GROUPS.remove(signature);
                CORRECTIONS.remove(signature);
                for (PlacementDyPredictionBridge.PredictedCell cell : group.cells.values()) {
                    long key = cell.pos().asLong();
                    OVERLAY_OWNER.remove(key, signature);
                    HIGH_WATER.remove(key, signature.sequence());
                    int cellChunkX = cell.pos().getX() >> 4;
                    int cellChunkZ = cell.pos().getZ() >> 4;
                    if ((cellChunkX != unloadingX || cellChunkZ != unloadingZ)
                            && level.getChunkSource().hasChunk(cellChunkX, cellChunkZ)) {
                        rerenders.add(cell.pos());
                    }
                }
            }
            publishOverlayCellsLocked();
        }
        rerenders.forEach(SlabAnchorClientSync::scheduleExactPlacementDyRerender);
    }

    private static boolean batchTouchesChunk(
            PlacementDyPredictionBridge.PredictedBatch batch,
            int chunkX,
            int chunkZ
    ) {
        return batch.cells().stream().anyMatch(cell ->
                (cell.pos().getX() >> 4) == chunkX && (cell.pos().getZ() >> 4) == chunkZ);
    }

    private static boolean groupTouchesChunk(Group group, int chunkX, int chunkZ) {
        return group.cells.values().stream().anyMatch(cell ->
                (cell.pos().getX() >> 4) == chunkX && (cell.pos().getZ() >> 4) == chunkZ);
    }

    private static void ensureCurrentLevel(ClientLevel level) {
        if (level != currentLevel) {
            reset(level);
        }
    }

    private static synchronized void reset(ClientLevel level) {
        generation++;
        currentLevel = level;
        acknowledgedThrough = -1;
        STAGED.clear();
        GROUPS.clear();
        OVERLAY_OWNER.clear();
        HIGH_WATER.clear();
        CORRECTIONS.clear();
        // The only writer of currentLevel/generation, so this is what keeps a published non-empty
        // snapshot permanently in step with them.
        publishOverlayCellsLocked();
    }
}
