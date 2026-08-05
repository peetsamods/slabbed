package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.PlacementDyOverlay;
import com.slabbed.anchor.SlabAnchorAttachment;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Slice 2i — CLIENT PREDICTION OVERLAY core proofs.
 *
 * <p>The overlay exists to erase the placement "snap-down": the client renders a fresh placement flat
 * until the server's attachment sync lands, so the block visibly jumps by the placement height. These
 * rows pin the data structure that holds the client's own answer in between — and, just as
 * importantly, pin that it is an OVERLAY and never a write to the client chunk's {@code PLACEMENT_DY}
 * attachment, so a placement the server refuses self-heals instead of leaving an orphan fact.
 *
 * <p>WHY THIS IS A SERVER GAMETEST. The client harness is broken on this line, so anything in
 * {@code src/client} cannot be covered at all. The overlay's data structure and all of its logic
 * therefore live in {@code src/main} ({@link PlacementDyOverlay}), addressed by packed positions and
 * an OPAQUE level handle, and that is exactly what these rows drive — with plain {@link Object}
 * handles, which also proves the core never needs to know what a level is. The thin client wiring
 * (level identity, events, rerender scheduling, the read hook, the acknowledgement mixin) is the only
 * part left uncovered; it is listed in the slice report.
 *
 * <p>Each row runs to completion synchronously inside one server-tick invocation and restores the
 * global overlay state in a {@code finally}, so concurrently batched rows cannot observe each other.
 */
public final class PlacementDyOverlayTest {

    private static final String PREFIX = "slabbed_gametest:placement_dy_overlay_test_";

    private static final double PREDICTED_DEEP = -1.0d;
    private static final double PREDICTED_HALF = -0.5d;

    /** Mutable stand-in for the authoritative backing store the real probe reads from the chunk. */
    private static final class FakeBacking {
        private final LongOpenHashSet present = new LongOpenHashSet();

        SlabAnchorAttachment.PlacementDyFact backingFact(long packedPos) {
            return present.contains(packedPos)
                    ? SlabAnchorAttachment.PlacementDyFact.present(0.25d)
                    : SlabAnchorAttachment.PlacementDyFact.absent();
        }

        void arrive(BlockPos... positions) {
            for (BlockPos pos : positions) {
                present.add(pos.asLong());
            }
        }
    }

    // ── 1. install answers the predicted fact; nothing else is touched ───────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void installAnswersPredictedFactPerCell(TestContext h) {
        Object level = new Object();
        Object otherLevel = new Object();
        BlockPos owner = new BlockPos(10, 70, 10);
        BlockPos placed = owner.up();
        BlockPos untouched = owner.east();
        try {
            arm(new FakeBacking(), null);
            PlacementDyOverlay.installPredictedPlacement(
                    level, owner, Direction.UP, 4, bits(placed, PREDICTED_DEEP));

            h.assertTrue(dy(level, placed) == PREDICTED_DEEP,
                    "installed cell must answer the predicted height; got " + dy(level, placed));
            h.assertTrue(PlacementDyOverlay.overlayFact(level, untouched.asLong()) == null,
                    "a cell with no prediction must have no overlay opinion at all");
            h.assertTrue(PlacementDyOverlay.overlayFact(otherLevel, placed.asLong()) == null,
                    "a prediction must never answer a read on a different level");
            h.assertTrue(PlacementDyOverlay.liveGroupCount() == 1,
                    "exactly one group must be live after one install");
        } finally {
            PlacementDyOverlay.resetForTests();
        }
        pass(h, "install_answers_predicted_fact_per_cell");
    }

    // ── 2. published-snapshot identity and zero per-read allocation ──────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void publishedSnapshotIdentityAndPrebuiltFacts(TestContext h) {
        Object level = new Object();
        BlockPos owner = new BlockPos(-40, 64, 12);
        BlockPos placed = owner.up();
        try {
            FakeBacking backing = new FakeBacking();
            arm(backing, null);

            // With nothing in flight the read path must short-circuit on the ONE shared empty
            // snapshot: that reference compare is the whole cost of the common mesh-thread read.
            h.assertTrue(PlacementDyOverlay.publishedSnapshot() == PlacementDyOverlay.sharedEmptySnapshot(),
                    "with no prediction the published snapshot must BE the shared empty instance");

            PlacementDyOverlay.installPredictedPlacement(
                    level, owner, Direction.UP, 9, bits(placed, PREDICTED_HALF));
            h.assertTrue(PlacementDyOverlay.publishedSnapshot() != PlacementDyOverlay.sharedEmptySnapshot(),
                    "an install must publish a snapshot distinct from the shared empty one");

            // An overlay HIT must hand back a fact built once at publication. Two reads returning the
            // same instance is the proof that the hot path allocates nothing per read.
            SlabAnchorAttachment.PlacementDyFact first =
                    PlacementDyOverlay.overlayFact(level, placed.asLong());
            SlabAnchorAttachment.PlacementDyFact second =
                    PlacementDyOverlay.overlayFact(level, placed.asLong());
            h.assertTrue(first != null && first == second,
                    "an overlay hit must return the SAME pre-built fact instance on every read");

            // ...and once the prediction is done the read path returns to the shared empty instance,
            // so a long session cannot leave a permanently non-empty snapshot behind.
            backing.arrive(placed);
            PlacementDyOverlay.onVanillaAcknowledgement(level, 9);
            h.assertTrue(PlacementDyOverlay.publishedSnapshot() == PlacementDyOverlay.sharedEmptySnapshot(),
                    "after the last group retires the shared empty snapshot must be republished");
        } finally {
            PlacementDyOverlay.resetForTests();
        }
        pass(h, "published_snapshot_identity_and_prebuilt_facts");
    }

    // ── 3. per-cell high-water: newest install wins, older retirement leaves it alone ────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void highWaterProtectsRapidRePlacementIntoOneCell(TestContext h) {
        Object level = new Object();
        BlockPos cell = new BlockPos(7, 71, -3);
        BlockPos olderOwner = cell.down();
        BlockPos newerOwner = cell.north();
        try {
            arm(new FakeBacking(), null);
            PlacementDyOverlay.installPredictedPlacement(
                    level, olderOwner, Direction.UP, 5, bits(cell, PREDICTED_DEEP));
            PlacementDyOverlay.installPredictedPlacement(
                    level, newerOwner, Direction.NORTH, 7, bits(cell, PREDICTED_HALF));

            h.assertTrue(dy(level, cell) == PREDICTED_HALF,
                    "the newer install into a cell must win; got " + dy(level, cell));
            h.assertTrue(PlacementDyOverlay.highWaterSequence(cell.asLong()) == 7,
                    "the cell's high-water mark must be the newer sequence");

            // Acknowledging only the OLDER sequence retires the older group. It no longer owns the
            // cell, so retiring it must not strip the newer group's ownership — that is exactly the
            // rapid re-placement case (place, break, place again into one cell).
            PlacementDyOverlay.onVanillaAcknowledgement(level, 5);
            h.assertTrue(PlacementDyOverlay.liveGroupCount() == 1,
                    "only the older group may retire on an acknowledgement of its own sequence");
            h.assertTrue(dy(level, cell) == PREDICTED_HALF,
                    "retiring the older group must leave the newer prediction standing; got "
                            + dy(level, cell));
            h.assertTrue(PlacementDyOverlay.overlayOwner(cell.asLong()) != null
                            && PlacementDyOverlay.overlayOwner(cell.asLong()).sequence() == 7,
                    "the newer group must still own the cell after the older one retires");

            // The mark is monotonic, so an install that arrives out of order is refused outright.
            PlacementDyOverlay.installPredictedPlacement(
                    level, olderOwner, Direction.UP, 3, bits(cell, 0.5d));
            h.assertTrue(dy(level, cell) == PREDICTED_HALF,
                    "an out-of-order install must not displace a newer prediction; got "
                            + dy(level, cell));
            h.assertTrue(PlacementDyOverlay.liveGroupCount() == 1,
                    "an out-of-order install must not create a group");
        } finally {
            PlacementDyOverlay.resetForTests();
        }
        pass(h, "high_water_protects_rapid_re_placement_into_one_cell");
    }

    // ── 4. whole-group retirement: a pair is never half predicted ────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void pairGroupRetiresWholeNeverHalf(TestContext h) {
        Object level = new Object();
        BlockPos owner = new BlockPos(2, 65, 2);
        BlockPos lower = owner.up();
        BlockPos upper = lower.up();
        try {
            FakeBacking backing = new FakeBacking();
            arm(backing, null);
            Map<BlockPos, Long> pair = new LinkedHashMap<>();
            pair.put(lower, raw(PREDICTED_DEEP));
            pair.put(upper, raw(PREDICTED_DEEP));
            PlacementDyOverlay.installPredictedPlacement(level, owner, Direction.UP, 11, pair);

            h.assertTrue(dy(level, lower) == PREDICTED_DEEP && dy(level, upper) == PREDICTED_DEEP,
                    "both halves of a pair must carry the one predicted height");

            // Half the backing has arrived. Retiring now would leave the pair split across two
            // heights for a frame — the door's top half at the server value and its bottom half at
            // the predicted one — so the group must stay whole.
            backing.arrive(lower);
            PlacementDyOverlay.onVanillaAcknowledgement(level, 11);
            h.assertTrue(PlacementDyOverlay.liveGroupCount() == 1,
                    "a group must not retire while any of its cells still lacks a backing fact");
            h.assertTrue(dy(level, lower) == PREDICTED_DEEP && dy(level, upper) == PREDICTED_DEEP,
                    "a partially backed group must keep BOTH cells predicted, never one");

            backing.arrive(upper);
            PlacementDyOverlay.clientTick(level);
            h.assertTrue(PlacementDyOverlay.liveGroupCount() == 0,
                    "a fully backed acknowledged group must retire");
            h.assertTrue(PlacementDyOverlay.overlayFact(level, lower.asLong()) == null
                            && PlacementDyOverlay.overlayFact(level, upper.asLong()) == null,
                    "retirement must release BOTH cells of the pair in one step");
        } finally {
            PlacementDyOverlay.resetForTests();
        }
        pass(h, "pair_group_retires_whole_never_half");
    }

    // ── 5. lazy retirement: acknowledgement is necessary, never sufficient ───────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void lazyRetirementNeedsBackingOrTimeout(TestContext h) {
        Object level = new Object();
        BlockPos owner = new BlockPos(-9, 68, 40);
        BlockPos placed = owner.up();
        BlockPos refused = owner.south().up();
        try {
            FakeBacking backing = new FakeBacking();
            arm(backing, null);

            // (a) Acknowledged, backing still absent: retiring here would be exactly the bug that
            // exact-on-ack retirement hides — it depends on the attachment sync always beating the
            // acknowledgement packet, and if that ordering ever changed the snap would come back.
            PlacementDyOverlay.installPredictedPlacement(
                    level, owner, Direction.UP, 20, bits(placed, PREDICTED_DEEP));
            PlacementDyOverlay.onVanillaAcknowledgement(level, 20);
            h.assertTrue(PlacementDyOverlay.liveGroupCount() == 1 && dy(level, placed) == PREDICTED_DEEP,
                    "an acknowledgement alone must not retire a prediction whose fact has not arrived");
            // Two ticks short of the timeout, so the next branch is unambiguously the backing one.
            for (int i = 0; i < PlacementDyOverlay.RETIREMENT_TIMEOUT_TICKS - 2; i++) {
                PlacementDyOverlay.clientTick(level);
            }
            h.assertTrue(PlacementDyOverlay.liveGroupCount() == 1,
                    "the prediction must survive right up to the timeout boundary");

            // (b) The fact arrives: retire on the next re-check.
            backing.arrive(placed);
            PlacementDyOverlay.clientTick(level);
            h.assertTrue(PlacementDyOverlay.liveGroupCount() == 0
                            && PlacementDyOverlay.overlayFact(level, placed.asLong()) == null,
                    "acknowledged plus backing present must retire the group");

            // (c) The server refused the placement, so no fact will EVER arrive. The timeout is what
            // stops the prediction becoming permanent; once it retires the read falls through to the
            // backing store, finds nothing, and resolves stable-flat — what the server believes.
            PlacementDyOverlay.installPredictedPlacement(
                    level, owner.south(), Direction.UP, 21, bits(refused, PREDICTED_DEEP));
            PlacementDyOverlay.onVanillaAcknowledgement(level, 21);
            for (int i = 0; i < PlacementDyOverlay.RETIREMENT_TIMEOUT_TICKS; i++) {
                PlacementDyOverlay.clientTick(level);
            }
            h.assertTrue(PlacementDyOverlay.liveGroupCount() == 0
                            && PlacementDyOverlay.overlayFact(level, refused.asLong()) == null,
                    "a refused placement must retire on the timeout even with no backing fact");
        } finally {
            PlacementDyOverlay.resetForTests();
        }
        pass(h, "lazy_retirement_needs_backing_or_timeout");
    }

    // ── 6. level identity / generation reset ─────────────────────────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void levelChangeClearsEverythingAndRepublishesEmpty(TestContext h) {
        Object level = new Object();
        Object nextLevel = new Object();
        BlockPos owner = new BlockPos(31, 63, -18);
        BlockPos placed = owner.up();
        try {
            arm(new FakeBacking(), null);
            PlacementDyOverlay.installPredictedPlacement(
                    level, owner, Direction.UP, 30, bits(placed, PREDICTED_DEEP));
            h.assertTrue(PlacementDyOverlay.liveGroupCount() == 1, "precondition: one live group");

            PlacementDyOverlay.resetForLevel(nextLevel);
            h.assertTrue(PlacementDyOverlay.liveGroupCount() == 0,
                    "a level change must drop every group");
            h.assertTrue(PlacementDyOverlay.highWaterSequence(placed.asLong()) == -1
                            && PlacementDyOverlay.overlayOwner(placed.asLong()) == null,
                    "a level change must drop every ownership and high-water mark");
            h.assertTrue(PlacementDyOverlay.publishedSnapshot() == PlacementDyOverlay.sharedEmptySnapshot(),
                    "a level change must republish the shared empty snapshot");
            h.assertTrue(PlacementDyOverlay.overlayFact(level, placed.asLong()) == null
                            && PlacementDyOverlay.overlayFact(nextLevel, placed.asLong()) == null,
                    "no read on either level may still see the discarded prediction");

            // A disconnect is the same event with a null handle, reached from the client tick once
            // the client has no world: a prediction can never outlive its connection.
            PlacementDyOverlay.installPredictedPlacement(
                    level, owner, Direction.UP, 31, bits(placed, PREDICTED_DEEP));
            PlacementDyOverlay.clientTick(null);
            h.assertTrue(PlacementDyOverlay.liveGroupCount() == 0
                            && PlacementDyOverlay.publishedSnapshot()
                            == PlacementDyOverlay.sharedEmptySnapshot(),
                    "losing the world must clear the overlay");
        } finally {
            PlacementDyOverlay.resetForTests();
        }
        pass(h, "level_change_clears_everything_and_republishes_empty");
    }

    // ── 7. publish-before-rerender ordering, both ways ───────────────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void snapshotIsPublishedBeforeEveryRerenderIsScheduled(TestContext h) {
        Object level = new Object();
        BlockPos owner = new BlockPos(120, 66, 120);
        BlockPos placed = owner.up();
        List<String> seen = new ArrayList<>();
        try {
            FakeBacking backing = new FakeBacking();
            // The sink samples the read path at the instant the rerender is requested. If the
            // snapshot were published after the scheduling call, the mesh thread servicing that
            // rebuild could bake the block at its pre-prediction height and the snap would look
            // unfixed; on retirement the reverse ordering would flash the stale value instead.
            arm(backing, pos -> seen.add(pos.equals(placed)
                    ? String.valueOf(PlacementDyOverlay.overlayFact(level, placed.asLong()) != null)
                    : "other"));

            PlacementDyOverlay.installPredictedPlacement(
                    level, owner, Direction.UP, 44, bits(placed, PREDICTED_DEEP));
            h.assertTrue(seen.equals(List.of("true")),
                    "install must publish the overlay BEFORE scheduling the rerender; saw " + seen);

            seen.clear();
            backing.arrive(placed);
            PlacementDyOverlay.onVanillaAcknowledgement(level, 44);
            h.assertTrue(seen.equals(List.of("false")),
                    "retirement must publish the retired overlay BEFORE scheduling the rerender; saw "
                            + seen);
        } finally {
            PlacementDyOverlay.resetForTests();
        }
        pass(h, "snapshot_is_published_before_every_rerender_is_scheduled");
    }

    // ── 8. chunk unload takes a cross-chunk group down whole ─────────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void chunkUnloadRetiresCrossChunkGroupWhole(TestContext h) {
        Object level = new Object();
        BlockPos owner = new BlockPos(15, 64, 8);
        BlockPos here = new BlockPos(15, 65, 8);
        BlockPos acrossTheBorder = new BlockPos(16, 65, 8);
        try {
            arm(new FakeBacking(), null);
            Map<BlockPos, Long> pair = new LinkedHashMap<>();
            pair.put(here, raw(PREDICTED_DEEP));
            pair.put(acrossTheBorder, raw(PREDICTED_DEEP));
            PlacementDyOverlay.installPredictedPlacement(level, owner, Direction.UP, 50, pair);
            h.assertTrue((here.getX() >> 4) != (acrossTheBorder.getX() >> 4),
                    "precondition: the pair must straddle a chunk border");

            PlacementDyOverlay.onChunkUnload(level, here.getX() >> 4, here.getZ() >> 4);
            h.assertTrue(PlacementDyOverlay.liveGroupCount() == 0,
                    "a group with a cell in an unloading chunk must retire");
            h.assertTrue(PlacementDyOverlay.overlayFact(level, acrossTheBorder.asLong()) == null,
                    "the half in the surviving chunk must retire too, never speak for itself");
            h.assertTrue(PlacementDyOverlay.highWaterSequence(here.asLong()) == -1,
                    "the unloading chunk's high-water marks must be dropped with it");
        } finally {
            PlacementDyOverlay.resetForTests();
        }
        pass(h, "chunk_unload_retires_cross_chunk_group_whole");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    private static void arm(FakeBacking backing, PlacementDyOverlay.RerenderSink sink) {
        PlacementDyOverlay.resetForTests();
        PlacementDyOverlay.installClientHooks(backing::backingFact, sink);
    }

    private static Map<BlockPos, Long> bits(BlockPos pos, double dy) {
        return Map.of(pos, raw(dy));
    }

    private static long raw(double dy) {
        return Double.doubleToRawLongBits(dy);
    }

    /** The overlay's answer as a double, or NaN when it has no opinion about the cell. */
    private static double dy(Object level, BlockPos pos) {
        SlabAnchorAttachment.PlacementDyFact fact = PlacementDyOverlay.overlayFact(level, pos.asLong());
        return fact == null ? Double.NaN : fact.valueOrNaN();
    }

    private static void pass(TestContext h, String suffix) {
        Slabbed.LOGGER.info("C3_FOCUSED | {}{} | PASS", PREFIX, suffix);
        h.complete();
    }
}
