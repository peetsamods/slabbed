package com.slabbed.anchor;

import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Contract proofs for {@code slabbed:placement_dy} — the store that closes {@code LAW.md} lane G.
 *
 * <p>Lane G is the case an anchor cannot reach: a cell that IS anchored, keeps its anchor through a
 * neighbour edit, and still changes height, because the anchor set records presence and the
 * magnitude was derived afresh from a support the edit destroyed.
 * {@code NeighborUpdateInvarianceTest} proves that end-to-end through the real placement path; this
 * file proves the store's own contract, including the two halves a matrix row cannot state:
 *
 * <ul>
 *   <li><b>Old worlds.</b> Every world saved before this attachment existed carries anchors and no
 *       store, and must resolve EXACTLY as it did before — no migration, no silent re-interpretation
 *       of somebody's build. That row deliberately asserts the pre-store answer, including the lane
 *       G movement, because pinning old behaviour is the point; deciding whether old worlds are
 *       ever migrated is reserved, not assumed here.</li>
 *   <li><b>Scope.</b> The store is written only where a cell earns an anchor. An unanchored lowered
 *       cell must have no fact, so lanes A-F stay exactly as documented rather than being quietly
 *       half-closed.</li>
 * </ul>
 */
public final class PlacementDyStoreTest {
    private static final double EPS = 1.0e-6;

    /**
     * LANE G, CLOSED. The subject is anchored AND carries its placement height; breaking the seat
     * it was placed on must not move it.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void storedHeightSurvivesTheSeatBeingBroken(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos subject = buildMinusOneSubject(ctx, 1, 1, true);

        ctx.assertTrue(SlabPlacementDyAttachment.hasStoredDy(w, subject),
                "setup: an anchored lowered cell must carry its placement height");
        double before = dy(w, subject);
        ctx.assertTrue(Math.abs(before + 1.0) <= EPS,
                "fixture: the anchored subject must read -1.0, got " + before);

        w.breakBlock(subject.down(), false);

        double after = dy(w, subject);
        ctx.assertTrue(sameHeight(before, after),
                "LANE G: breaking the seat below moved a placed block from " + before + " to "
                        + after + " — the stored placement height must win over every live lane");
        ctx.complete();
    }

    /**
     * MIGRATION SAFETY — the row that matters more than it looks, because every existing player
     * world is this shape: anchors present, no store at all. Absence must be indistinguishable from
     * the behaviour that shipped, which means this row asserts the OLD answer on purpose: -1.0
     * before the seat is broken and -0.5 after, exactly as this line resolved it before the store
     * existed. Whether old worlds are ever migrated is a separate, reserved decision.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchorWithoutStoredHeightResolvesExactlyAsBefore(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos subject = buildMinusOneSubject(ctx, 1, 1, false);

        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, subject),
                "setup: the old-world shape is an anchor with no store");
        ctx.assertTrue(!SlabPlacementDyAttachment.hasStoredDy(w, subject),
                "setup: this row must carry NO stored height, or it proves nothing about old worlds");

        double before = dy(w, subject);
        ctx.assertTrue(Math.abs(before + 1.0) <= EPS,
                "old world: the anchored subject must still read -1.0, got " + before);

        w.breakBlock(subject.down(), false);

        double after = dy(w, subject);
        ctx.assertTrue(Math.abs(after + 0.5) <= EPS,
                "old world: with no stored height the subject must resolve to the pre-store -0.5 "
                        + "floor, got " + after + " — a different answer here means the store "
                        + "changed worlds it never touched");
        ctx.complete();
    }

    /**
     * SCOPE. This slice stores a height only where a cell earns an anchor. A lowered cell with no
     * anchor must have no fact, so the lanes {@code LAW.md} lists as still open stay open and
     * visible instead of being half-closed by accident.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unanchoredLoweredCellHasNoStoredHeight(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(5, 1, 5);
        place(w, base, Blocks.STONE.getDefaultState());
        BlockPos slabPos = base.up();
        place(w, slabPos, bottomSlab());
        BlockPos subject = slabPos.up();
        place(w, subject, Blocks.STONE.getDefaultState());

        double subjectDy = dy(w, subject);
        ctx.assertTrue(Math.abs(subjectDy + 0.5) <= EPS,
                "fixture: an unanchored block on a bottom slab still reads -0.5, got " + subjectDy);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, subject),
                "fixture: setBlockState never runs onPlaced, so this cell must be unanchored");
        ctx.assertTrue(!SlabPlacementDyAttachment.hasStoredDy(w, subject),
                "scope: an unanchored cell must carry NO stored height in this slice");
        ctx.complete();
    }

    /** The stored height dies with the block, so a fresh placement is measured from scratch. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void breakingTheAnchoredBlockClearsItsStoredHeight(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 1, 3);
        place(w, base, Blocks.STONE.getDefaultState());
        BlockPos slabPos = base.up();
        place(w, slabPos, bottomSlab());
        BlockPos subject = slabPos.up();
        place(w, subject, Blocks.STONE.getDefaultState());
        SlabAnchorAttachment.addAnchor(w, subject, w.getBlockState(subject));

        ctx.assertTrue(SlabPlacementDyAttachment.hasStoredDy(w, subject),
                "setup: the anchored block must carry its placement height");

        w.breakBlock(subject, false);

        ctx.assertTrue(!SlabPlacementDyAttachment.hasStoredDy(w, subject),
                "breaking the block must clear its stored height, or a later placement in the same "
                        + "cell would take a dead block's height");
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, subject),
                "breaking the block must clear its anchor too (pre-existing contract)");
        ctx.complete();
    }

    /**
     * The stored grid is sixteenths of a block and the exactness test is a round-trip, not a
     * tolerance: a height that does not land on the grid is DECLINED rather than rounded into a
     * different height, and a height that does reads back bit-identical.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void offGridHeightIsDeclinedAndOnGridHeightIsBitIdentical(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(7, 3, 7);

        ctx.assertTrue(!SlabPlacementDyAttachment.record(w, pos, -0.3),
                "a height off the sixteenths grid must be declined, not rounded");
        ctx.assertTrue(!SlabPlacementDyAttachment.hasStoredDy(w, pos),
                "a declined height must leave no fact behind");

        for (double height : new double[] {-0.5, -1.0, -0.0625}) {
            ctx.assertTrue(SlabPlacementDyAttachment.record(w, pos, height),
                    "an on-grid height (" + height + ") must be stored");
            double read = SlabPlacementDyAttachment.storedDy(w, pos);
            ctx.assertTrue(sameHeight(height, read),
                    "stored " + height + " read back as " + read + " — the store must be exact");
        }
        SlabPlacementDyAttachment.clear(w, pos);
        ctx.complete();
    }

    // ── fixture ───────────────────────────────────────────────────────

    /**
     * The proven {@code follower_on_minus_one} scene (shared with
     * {@code AnchoredFollowerSupportDyTest}): a source column lowered to -0.5, a seat slab beside it
     * that reads -0.5, and a log standing on that seat at -1.0.
     *
     * @param throughAddAnchor true to anchor through {@code addAnchor} (a real placement, which
     *                         also records the placement height); false to write the raw anchor
     *                         attachment directly, which is the shape of a world saved before the
     *                         store existed
     */
    private BlockPos buildMinusOneSubject(TestContext ctx, int x, int z, boolean throughAddAnchor) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(x, 1, z);
        BlockPos source = base.add(0, 0, 1);

        place(w, source, Blocks.STONE.getDefaultState());
        place(w, source.up(), bottomSlab());
        BlockPos sourceTop = source.up(2);
        place(w, sourceTop, Blocks.STONE.getDefaultState());
        anchor(w, sourceTop, throughAddAnchor);

        place(w, base, Blocks.STONE.getDefaultState());
        BlockPos seat = base.up(2);
        place(w, seat, bottomSlab());
        anchor(w, seat, throughAddAnchor);
        double seatDy = dy(w, seat);
        ctx.assertTrue(Math.abs(seatDy + 0.5) <= EPS,
                "fixture: the seat slab must read -0.5, got " + seatDy);

        BlockPos subject = seat.up();
        place(w, subject, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
        anchor(w, subject, throughAddAnchor);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, subject),
                "fixture: the subject must be anchored");
        return subject;
    }

    private static void anchor(ServerWorld w, BlockPos pos, boolean throughAddAnchor) {
        if (throughAddAnchor) {
            SlabAnchorAttachment.addAnchor(w, pos, w.getBlockState(pos));
            return;
        }
        // The old-world shape: the raw anchor attachment with no companion store, exactly as
        // FlushSeatGuardTest models a world anchored before a later guard existed.
        WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        LongOpenHashSet existing = chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE);
        LongOpenHashSet set = existing == null ? new LongOpenHashSet() : new LongOpenHashSet(existing);
        set.add(pos.asLong());
        chunk.setAttached(SlabAnchorAttachment.ANCHOR_TYPE, set);
    }

    private static double dy(ServerWorld w, BlockPos pos) {
        return SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
    }

    /** Exact height identity, with -0.0 normalised to 0.0 — the S-2 gate's own comparison. */
    private static boolean sameHeight(double a, double b) {
        return Double.doubleToRawLongBits(a == 0.0 ? 0.0 : a)
                == Double.doubleToRawLongBits(b == 0.0 ? 0.0 : b);
    }

    private static void place(ServerWorld w, BlockPos pos, BlockState state) {
        w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }
}
