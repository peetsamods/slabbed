package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
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
 * FLUSH-SEAT GUARD ({@code docs/process/LIVE_LEDGER.md} second pass, 2026-08-05 evening —
 * "interpenetration row"): the mega board's z=14 compound row showed anchored {@code stone_slab}
 * at -0.5 rendered fully INSIDE its own direct support, a FLUSH stone (dy 0.0) — z-fighting.
 *
 * <p><b>Principle under test:</b> side-adjacency lowering is legal only when the destination
 * volume is free. A slab may not resolve, or qualify for an anchor, at a height that intersects
 * its own direct support:
 * <ul>
 *   <li>READ time — {@code loweredFollowerDy}'s "anchored ⇒ at least -0.5" floor must not apply
 *       when {@code supportSeatDy} finds a FLUSH full-height seat (solid non-slab block, or a
 *       flush TOP/DOUBLE slab whose top face is at the cell top): the follower reads 0.0 and
 *       rises OUT of its support. Air / non-solid below keeps the -0.5 floor (the legitimate
 *       cantilever alignment); half-height bottom-slab seats are untouched.</li>
 *   <li>PLACEMENT time — {@code isAdjacentSideSlabLowered} must refuse to qualify a BOTTOM or
 *       DOUBLE slab whose direct support is a flush seat, which also stops
 *       {@code qualifiesForLoweredSideSlabAnchor} from anchoring that placement (WYSIWYG at the
 *       source). A TOP slab is exempt: at -0.5 it occupies the LOWER half of its own cell and
 *       rests ON the flush seat (the BS-FB-1S alignment) — it never enters it.</li>
 * </ul>
 *
 * <p>The anchored cells here follow the standing rule for the anchor lane: {@code isAnchored} is
 * asserted before any dy read, so a fixture that silently loses its anchor cannot false-green.
 * The legacy cells force the raw chunk attachment (not {@code addAnchor}) because they model an
 * ALREADY-BUILT world: after the fix the qualifier refuses this shape, but worlds anchored before
 * the fix still carry the attachment and must read 0.0 from it.
 */
public final class FlushSeatGuardTest {

    private static final double EPS = 1.0e-6;

    // ------------------------------------------------------------------
    // RED A — the exact live tower (rig recipe pre-fix): stone / stone / anchored
    // bottom slab, beside a source column stone / bottom slab / stone.
    // ------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredSlabOverFlushStoneReadsFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(1, 1, 1);
        buildLoweredSideSource(ctx, base.add(0, 0, 1));

        // Seat column: stone / stone / bottom slab — the live z=14 tower.
        place(w, base, Blocks.STONE.getDefaultState());
        place(w, base.up(), Blocks.STONE.getDefaultState());
        BlockPos seat = base.up(2);
        place(w, seat, bottomSlab(Blocks.STONE_SLAB));
        forceLegacyAnchor(w, seat);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, seat),
                "setup: the seat slab must carry the (legacy, pre-fix-world) anchor — this test "
                        + "exercises the ANCHOR read lane");

        double dy = SlabSupport.getYOffset(w, seat, w.getBlockState(seat));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "FLUSH-SEAT GUARD: an anchored bottom slab whose direct support is FLUSH stone "
                        + "(dy 0.0) must read 0.0 — not sink -0.5 fully inside its own support "
                        + "(the live z=14 interpenetration row) — got " + dy);
        ctx.complete();
    }

    // ------------------------------------------------------------------
    // RED B — the real-placement shape: one click onto flush stone beside a
    // lowered side-slab source must neither sink nor anchor via the side lane.
    // ------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placedSlabOnFlushStoneBesideLoweredSourceStaysFlat(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(1, 1, 1);
        buildLoweredSideSource(ctx, base.add(0, 0, 1));

        place(w, base, Blocks.STONE.getDefaultState());
        place(w, base.up(), Blocks.STONE.getDefaultState());
        BlockPos seat = base.up(2);
        // The exact BlockOnPlacedAnchorMixin.onPlaced sequence a real click runs.
        place(w, seat, bottomSlab(Blocks.STONE_SLAB));
        SlabAnchorAttachment.addAnchor(w, seat, w.getBlockState(seat));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, seat, w.getBlockState(seat));

        boolean anchored = SlabAnchorAttachment.isAnchored(w, seat);
        double dy = SlabSupport.getYOffset(w, seat, w.getBlockState(seat));
        ctx.assertTrue(!anchored && Math.abs(dy) <= EPS,
                "FLUSH-SEAT GUARD: a slab clicked onto FLUSH stone beside a lowered side-slab "
                        + "source must NOT sink into the stone it was clicked onto and must NOT "
                        + "anchor via the side lane (WYSIWYG) — got anchored=" + anchored
                        + " dy=" + dy);
        ctx.complete();
    }

    // ------------------------------------------------------------------
    // RED (seat-shape completeness) — a flush TOP slab's top face is at the cell
    // top; a -0.5 follower would interpenetrate its upper half exactly like stone.
    // ------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredSlabOverFlushTopSlabSeatReadsFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(4, 1, 1);
        place(w, base, Blocks.STONE.getDefaultState());
        place(w, base.up(), topSlab(Blocks.STONE_SLAB));   // flush TOP-slab seat
        BlockPos subject = base.up(2);
        place(w, subject, bottomSlab(Blocks.STONE_SLAB));
        forceLegacyAnchor(w, subject);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, subject),
                "setup: the subject slab must carry the (legacy) anchor");

        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "FLUSH-SEAT GUARD: a flush TOP-slab support's top face is at the cell top, so an "
                        + "anchored slab on it must read 0.0 — a -0.5 read interpenetrates the "
                        + "TOP slab's upper half exactly like full stone — got " + dy);
        ctx.complete();
    }

    // ------------------------------------------------------------------
    // GUARD (must stay green pre- AND post-fix) — the legitimate cantilever:
    // air below means there is no seat to interpenetrate; -0.5 alignment stands.
    // ------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cantileverWithAirBelowStillLowersAndAnchors(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(1, 1, 4);
        buildLoweredSideSource(ctx, base.add(0, 0, 1));

        place(w, base, Blocks.STONE.getDefaultState());
        // base.up() stays AIR — the legitimate cantilever destination volume is free.
        BlockPos seat = base.up(2);
        place(w, seat, bottomSlab(Blocks.STONE_SLAB));
        SlabAnchorAttachment.addAnchor(w, seat, w.getBlockState(seat));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, seat, w.getBlockState(seat));

        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, seat),
                "guard: the AIR-below cantilever slab must still anchor via the side lane");
        double dy = SlabSupport.getYOffset(w, seat, w.getBlockState(seat));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "guard: the legitimate cantilever (air below, beside a lowered source) must keep "
                        + "its -0.5 alignment — got " + dy);
        ctx.complete();
    }

    // ------------------------------------------------------------------
    // GUARD (must stay green pre- AND post-fix) — the TOP-slab exemption: at -0.5
    // a TOP slab fills the LOWER half of its own cell, resting ON the flush stone
    // below it (BS-FB-1S). Its destination volume is free, so it keeps lowering.
    // ------------------------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void topSlabBesideLoweredSourceOverFlushStoneKeepsAlignment(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(4, 1, 4);
        buildLoweredSideSource(ctx, base.add(0, 0, 1));

        place(w, base, Blocks.STONE.getDefaultState());
        place(w, base.up(), Blocks.STONE.getDefaultState());
        BlockPos seat = base.up(2);
        place(w, seat, topSlab(Blocks.STONE_SLAB));
        SlabAnchorAttachment.addAnchor(w, seat, w.getBlockState(seat));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, seat, w.getBlockState(seat));

        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, seat),
                "guard: the TOP side slab must still anchor via the side lane (its lowered "
                        + "volume rests ON the flush seat, never inside it)");
        double dy = SlabSupport.getYOffset(w, seat, w.getBlockState(seat));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "guard: a TOP slab beside a lowered source over flush stone keeps the BS-FB-1S "
                        + "-0.5 alignment (it seats ON the stone) — got " + dy);
        ctx.complete();
    }

    // ------------------------------------------------------------------

    /**
     * Source column at {@code sourcePos}: stone / bottom slab / stone. The top stone is lowered
     * -0.5 by the slab beneath it, making it a lowered side-slab source for the cell beside it.
     * Fixture-asserted so a broken source can never false-green a guard cell.
     */
    private static void buildLoweredSideSource(TestContext ctx, BlockPos sourcePos) {
        ServerWorld w = ctx.getWorld();
        place(w, sourcePos, Blocks.STONE.getDefaultState());
        place(w, sourcePos.up(), bottomSlab(Blocks.STONE_SLAB));
        BlockPos top = sourcePos.up(2);
        place(w, top, Blocks.STONE.getDefaultState());
        double dy = SlabSupport.getYOffset(w, top, w.getBlockState(top));
        ctx.assertTrue(Math.abs(dy + 0.5) <= EPS,
                "fixture: the side-source top stone must render -0.5, got " + dy);
    }

    /**
     * Simulates a world anchored BEFORE the flush-seat guard existed: writes the raw chunk
     * attachment directly, bypassing {@code addAnchor}'s qualifiers (which, post-fix, correctly
     * refuse this shape — but legacy worlds still carry the attachment and must read 0.0).
     */
    private static void forceLegacyAnchor(ServerWorld w, BlockPos pos) {
        WorldChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        LongOpenHashSet existing = chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE);
        LongOpenHashSet set = existing == null ? new LongOpenHashSet() : new LongOpenHashSet(existing);
        set.add(pos.asLong());
        chunk.setAttached(SlabAnchorAttachment.ANCHOR_TYPE, set);
    }

    private static void place(ServerWorld w, BlockPos pos, BlockState state) {
        w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }

    private static BlockState bottomSlab(Block slab) {
        return slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState topSlab(Block slab) {
        return slab.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
    }
}
