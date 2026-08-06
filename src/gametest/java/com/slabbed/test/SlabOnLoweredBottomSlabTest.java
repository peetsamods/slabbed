package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Maintainer's law item #1 ("everything should be able to lower; no exceptions", 2026-08-06):
 * <b>a slab resting on a LOWERED bottom slab had no lane at all.</b>
 *
 * <p><b>Live evidence.</b> {@code (157,-58,-10) oak_slab dy=0.000 src=FROZEN-FLAT} sitting on
 * {@code (157,-59,-10) stone_slab dy=-0.500 ANCHORED}. The correct value is {@code -1.0} (mega
 * row 2's {@code MEGA_ROW_DY[2]}); the slab was stuck flat on a visibly sunk support.
 *
 * <p><b>Root cause.</b> The case fell between two TYPE-based rejects, neither of which asked
 * whether the support was actually sunk:
 * <ul>
 *   <li>{@code hasLoweredNonSlabTopSupport} rejects any support that is {@code instanceof
 *       SlabBlock};</li>
 *   <li>{@code hasLoweredTopLikeSlabSupport} rejected any support where {@code isBottomSlab(state)}
 *       — unconditionally, on type;</li>
 *   <li>{@code shouldOffset} never offsets slabs, so slabs have no generic-grammar fallback —
 *       their only lanes are {@code getYOffsetInner}'s slab branch.</li>
 * </ul>
 * Everything then fell to the class-based flush guard, which returns a hardcoded {@code 0.0} for
 * {@code SlabBlock}. The resolver already computed the right answer: had any lane reached
 * {@code loweredFollowerDy}, it would resolve {@code supportSeatDy} → {@code
 * loweredBottomSlabSupportDy} ({@code -0.5}) {@code - 0.5} = {@code -1.0}. <b>The gate was broken,
 * not the arithmetic</b> — no new depth math is introduced by the fix.
 *
 * <p><b>Why the pre-existing suite never caught it.</b>
 * {@code SlabOnSlabVerticalAnchorTest#slabOnBottomTypeSupportNeverAnchorsVertically} builds a
 * <b>non-lowered</b> birch bottom slab as its support, so it only ever defended the plain case.
 * Its premise ({@code KNOWN_INCOMPLETE.md} L8 — "a BOTTOM slab isn't itself 'sunk', so nothing
 * should propagate upward from it") is true of a plain bottom slab and <b>false of an anchored one
 * rendering -0.5</b>. {@link #slabOnFlatBottomSlabStaysFlat} pins that plain case here so the two
 * halves of the distinction are asserted side by side.
 *
 * <p><b>The scene</b> is the {@code /slabrig} {@code seatMinusOne} shape: a source column beside
 * the seat (stone / bottom slab / stone) whose top stone renders {@code -0.5}, making the bottom
 * slab beside it a legitimate air-below cantilever seat at {@code -0.5}. Every fixture premise —
 * <b>including the support's own {@code -0.5}</b> — is hard-asserted, so the cell cannot pass
 * vacuously against a seat that never sank.
 */
public final class SlabOnLoweredBottomSlabTest {

    private static final double EPS = 1.0e-6;

    /**
     * RED (anchor lane) — the exact live pair: a vanilla slab on an ANCHORED bottom slab that
     * renders -0.5 must read -1.0. Read 0.0 before the fix.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnAnchoredLoweredBottomSlabInheritsMinusOne(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos seat = buildLoweredSeat(ctx, 1, 1, true);

        BlockPos subject = seat.up();
        place(w, subject, bottomSlab(Blocks.OAK_SLAB));
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "oak_slab resting on an ANCHORED bottom slab that renders -0.5 must read -1.0, got "
                        + dy + " (live (157,-58,-10) dy=0.000 over (157,-59,-10) dy=-0.500: a slab "
                        + "on a LOWERED bottom slab has no lane — hasLoweredNonSlabTopSupport "
                        + "rejects the support on 'instanceof SlabBlock' and "
                        + "hasLoweredTopLikeSlabSupport rejects it on 'isBottomSlab', neither "
                        + "asking whether it is actually sunk)");
        ctx.complete();
    }

    /**
     * RED (geometric twin) — the same scene with no anchor anywhere. Both lanes go through the one
     * shared predicate, so they cannot drift apart (shared-predicate law).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnGeometricallyLoweredBottomSlabInheritsMinusOne(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos seat = buildLoweredSeat(ctx, 4, 1, false);

        BlockPos subject = seat.up();
        place(w, subject, bottomSlab(Blocks.OAK_SLAB));
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, subject),
                "setup: this twin must exercise the GEOMETRIC lane — no anchor anywhere");
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "oak_slab resting on a GEOMETRICALLY lowered bottom slab (-0.5, anchor=none) must "
                        + "read -1.0, got " + dy);
        ctx.complete();
    }

    /**
     * PERSISTENCE — the same predicate feeds {@code isVerticallyLoweredSlabSource} →
     * {@code isLoweredSideSlabVisual} → {@code qualifiesForLoweredSideSlabAnchor}, so the subject
     * must also RECORD the anchor that survives a later support break (never-pop).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnLoweredBottomSlabRecordsItsAnchor(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos seat = buildLoweredSeat(ctx, 4, 4, true);

        BlockPos subject = seat.up();
        place(w, subject, bottomSlab(Blocks.OAK_SLAB));
        SlabAnchorAttachment.addAnchor(w, subject, w.getBlockState(subject));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, subject),
                "a slab placed on a LOWERED bottom slab must record an anchor, or breaking the "
                        + "support later pops it flush even though it was never re-placed");
        ctx.complete();
    }

    /**
     * REGRESSION GUARD — the twin of {@code SlabOnSlabVerticalAnchorTest:113}: a slab on a
     * <b>non</b>-lowered bottom slab keeps 0.0. The predicate must qualify on the support's actual
     * depth, never on its type, in BOTH directions.
     *
     * <p>NOTE (product call for Maintainer, deliberately NOT actioned here):
     * {@code CombinedSlabChainingMatrixTest} records this as {@code Kind.BY_DESIGN}. Under Maintainer's
     * law it is itself a candidate exclusion — the law value would be -0.5.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnFlatBottomSlabStaysFlat(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(1, 1, 5);
        place(w, ground, Blocks.STONE.getDefaultState());
        BlockPos support = ground.up();
        place(w, support, bottomSlab(Blocks.STONE_SLAB));
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        ctx.assertTrue(Math.abs(supportDy) <= EPS,
                "fixture: this guard needs a FLAT support — the bottom slab must render 0.0, got "
                        + supportDy);

        BlockPos subject = support.up();
        place(w, subject, bottomSlab(Blocks.OAK_SLAB));
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy) <= EPS,
                "regression: a slab on a NON-lowered bottom slab must stay 0.0 — the new predicate "
                        + "must qualify on the support's actual depth, not its type — got " + dy);
        ctx.complete();
    }

    // ------------------------------------------------------------------------

    /**
     * Builds the {@code seatMinusOne} shape at plot-relative {@code (x, z)}, occupying {@code z}
     * and {@code z + 1}, and returns the bottom-slab seat that renders {@code -0.5}.
     *
     * <p>Seat column: ground stone at y+1, AIR at y+2, seat slab at y+3 — the donor-correct
     * air-below cantilever (a stone under the seat is the interpenetration state outlawed by the
     * flush-seat guard, 2026-08-05, and would read 0.0 instead).
     */
    private BlockPos buildLoweredSeat(TestContext ctx, int x, int z, boolean anchorSeat) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(x, 1, z);
        BlockPos source = base.add(0, 0, 1);

        // Source column: stone / bottom slab / stone — the top stone is lowered -0.5 by the slab.
        place(w, source, Blocks.STONE.getDefaultState());
        place(w, source.up(), bottomSlab(Blocks.STONE_SLAB));
        BlockPos sourceTop = source.up(2);
        place(w, sourceTop, Blocks.STONE.getDefaultState());
        double sourceTopDy = SlabSupport.getYOffset(w, sourceTop, w.getBlockState(sourceTop));
        ctx.assertTrue(Math.abs(sourceTopDy + 0.5) <= EPS,
                "fixture: the side-source top stone must render -0.5, got " + sourceTopDy);

        place(w, base, Blocks.STONE.getDefaultState());
        BlockPos seat = base.up(2);
        place(w, seat, bottomSlab(Blocks.STONE_SLAB));
        if (anchorSeat) {
            SlabAnchorAttachment.addAnchor(w, seat, w.getBlockState(seat));
            ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, seat),
                    "fixture: the seat slab must anchor via the lowered-side-slab lane");
        }
        // THE PREMISE THIS WHOLE CLASS RESTS ON: the support is a bottom slab that is ACTUALLY
        // SUNK. Without this assert the cells could pass vacuously against a seat at 0.0.
        double seatDy = SlabSupport.getYOffset(w, seat, w.getBlockState(seat));
        ctx.assertTrue(Math.abs(seatDy + 0.5) <= EPS,
                "fixture: the bottom-slab SUPPORT must itself render -0.5, got " + seatDy);
        return seat;
    }

    private static void place(ServerWorld w, BlockPos pos, BlockState state) {
        w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }

    private static BlockState bottomSlab(Block slab) {
        return slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }
}
