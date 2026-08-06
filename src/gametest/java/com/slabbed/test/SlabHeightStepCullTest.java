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
import net.minecraft.util.math.Direction;

/**
 * Live goblin-test finding (2026-07-04): {@code isSlabHeightStepFace}'s see-through-hole
 * ("doom-infinity window") mitigation only recognised a block lowered via
 * {@code isDirectCustomSlabSupportedObject} (resting directly on a Terrain-Slabs-owned custom
 * surface) — NOT an ordinary full block anchored on a plain VANILLA slab, which is this mod's
 * own core, day-one product intent (RULES.md §1 "global slab support"). A live session found
 * ~80 opaque-full-cube-at-nonzero-dy diagnostic hits on plain anchored dirt with zero Terrain
 * Slabs involvement — the exact class this mitigation exists for, just via a different (more
 * common) lowering mechanism than the one it checked.
 *
 * <p>{@code isSlabHeightStepFace} is a pure {@code (BlockView, BlockPos, BlockState, Direction)
 * -> boolean} function (no client dependency), so this is headlessly provable directly, even
 * though the actual rendered pixels are not.
 */
public final class SlabHeightStepCullTest {

    // THE FIX: an anchored (persistently lowered) dirt cube beside a flush (unlowered) dirt
    // cube must have its shared face redrawn (previously: false, since neither is a "direct
    // custom slab supported object" — dirt-on-a-vanilla-slab isn't a Terrain Slabs surface).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredFullCubeBesideFlushCubeRedrawsSteppedFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos loweredPos = slabPos.up();
        BlockPos flushPos = loweredPos.east();

        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(loweredPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, loweredPos, w.getBlockState(loweredPos));
        double dy = SlabSupport.getYOffset(w, loweredPos, w.getBlockState(loweredPos));
        ctx.assertTrue(Math.abs(dy + 0.5) <= 1.0e-6,
                "setup: anchored dirt on a vanilla bottom slab should render -0.5, got " + dy);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, loweredPos), "setup: dirt must be anchored");

        w.setBlockState(flushPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, flushPos), "setup: the flush neighbour must NOT be anchored");

        boolean stepFace = SlabSupport.isSlabHeightStepFace(w, loweredPos, w.getBlockState(loweredPos), Direction.EAST);
        ctx.assertTrue(stepFace,
                "an anchored, lowered opaque full cube beside a flush cube must redraw its "
                        + "stepped face (live see-through-hole bug on plain anchored dirt, not "
                        + "Terrain-Slabs-related); got " + stepFace);
        ctx.complete();
    }

    // REGRESSION GUARD: two flush (neither anchored/lowered) dirt cubes side by side must NOT
    // redraw anything — the fix must not force extra faces onto ordinary, unlowered terrain.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void twoFlushCubesNeverRedraw(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos a = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 2);
        BlockPos b = a.east();
        w.setBlockState(a, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(b, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, a) && !SlabAnchorAttachment.isAnchored(w, b),
                "setup: neither cube may be anchored");
        boolean stepFace = SlabSupport.isSlabHeightStepFace(w, a, w.getBlockState(a), Direction.EAST);
        ctx.assertTrue(!stepFace, "two flush cubes must never redraw a stepped face; got " + stepFace);
        ctx.complete();
    }

    // REGRESSION GUARD: two EQUALLY anchored/lowered cubes side by side (a flush terrace, both
    // lowered the same amount) must NOT redraw — only a HEIGHT STEP (disagreement) should.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void twoEquallyAnchoredCubesNeverRedraw(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 4);
        BlockPos a = slabPos.up();
        BlockPos slabPosB = slabPos.east();
        BlockPos b = slabPosB.up();
        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(slabPosB, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(a, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(b, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, a, w.getBlockState(a));
        SlabAnchorAttachment.addAnchor(w, b, w.getBlockState(b));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, a) && SlabAnchorAttachment.isAnchored(w, b),
                "setup: both cubes must be anchored");
        boolean stepFace = SlabSupport.isSlabHeightStepFace(w, a, w.getBlockState(a), Direction.EAST);
        ctx.assertTrue(!stepFace, "two EQUALLY lowered cubes (no height step) must not redraw; got " + stepFace);
        ctx.complete();
    }

    // GH#24 (github.com/peetsamods/slabbed/issues/24): "Invisible side faces of top slabs when
    // placed mid air next to a full block that is placed on top of a bottom slab." isSlabHeightStepFace's
    // subject gate required state.isOpaqueFullCube() — TRUE for a DOUBLE slab (probe-confirmed),
    // but FALSE for BOTTOM and TOP slabs, so a slab's OWN face toward a lowered full-block
    // neighbour was NEVER checked for a height-step redraw. The full block's own face toward the
    // slab WAS already correctly redrawn (this is what made the bug easy to miss — half the seam
    // looked fine). THE FIX: widen the subject/neighbour gate to also accept any slab that is not
    // already an opaque full cube, using isLoweredSideSlabVisual (L8's established "is this slab
    // visually lowered" authority) as its lowered/flush classification.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void topSlabBesideLoweredFullBlockRedrawsSteppedFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 5);
        BlockPos loweredFullBlockPos = slabPos.up();
        BlockPos topSlabPos = loweredFullBlockPos.east();

        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(loweredFullBlockPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, loweredFullBlockPos, w.getBlockState(loweredFullBlockPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, loweredFullBlockPos), "setup: dirt must be anchored/lowered");

        w.setBlockState(topSlabPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(!w.getBlockState(topSlabPos).isOpaqueFullCube(),
                "setup: a TOP slab must not be an opaque full cube (that's the whole gap)");
        // Deliberately NOT anchored: a slab placed beside an already-lowered full block is
        // exactly the build order a real player hits often (the full block was lowered first;
        // the slab's own onPlaced anchor evaluation never re-runs later, per the never-pop law),
        // and it's the exact configuration the confirmed probe reproduced this bug in.
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, topSlabPos), "setup: slab must NOT be anchored (matches the reported build order)");

        boolean fullBlockFace = SlabSupport.isSlabHeightStepFace(w, loweredFullBlockPos, w.getBlockState(loweredFullBlockPos), Direction.EAST);
        ctx.assertTrue(fullBlockFace, "sanity: the full block's own face toward the slab was already correctly redrawn");

        boolean topSlabFace = SlabSupport.isSlabHeightStepFace(w, topSlabPos, w.getBlockState(topSlabPos), Direction.WEST);
        ctx.assertTrue(topSlabFace,
                "THE FIX (GH#24): a flush TOP slab beside a lowered full block must ALSO redraw "
                        + "its own stepped face — previously a slab subject was never covered by this "
                        + "check at all, only the opposite (full-block) side was; got " + topSlabFace);
        ctx.complete();
    }

    // Same GH#24 gap, BOTTOM-type slab — the generalization is symmetric, not TOP-specific.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bottomSlabBesideLoweredFullBlockRedrawsSteppedFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 6);
        BlockPos loweredFullBlockPos = slabPos.up();
        BlockPos bottomSlabPos = loweredFullBlockPos.east();

        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(loweredFullBlockPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, loweredFullBlockPos, w.getBlockState(loweredFullBlockPos));

        w.setBlockState(bottomSlabPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, bottomSlabPos), "setup: slab must NOT be anchored (matches the reported build order)");

        boolean bottomSlabFace = SlabSupport.isSlabHeightStepFace(w, bottomSlabPos, w.getBlockState(bottomSlabPos), Direction.WEST);
        ctx.assertTrue(bottomSlabFace,
                "a flush BOTTOM slab beside a lowered full block must also redraw its stepped face; got " + bottomSlabFace);
        ctx.complete();
    }

    // REGRESSION GUARD: two flush TOP slabs side by side must never redraw — the fix must not
    // force extra faces onto ordinary, unlowered slab terrain.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void twoFlushTopSlabsNeverRedraw(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos a = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 7);
        BlockPos b = a.east();
        w.setBlockState(a, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP), Block.NOTIFY_LISTENERS);
        w.setBlockState(b, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP), Block.NOTIFY_LISTENERS);
        boolean stepFace = SlabSupport.isSlabHeightStepFace(w, a, w.getBlockState(a), Direction.EAST);
        ctx.assertTrue(!stepFace, "two flush TOP slabs must never redraw a stepped face; got " + stepFace);
        ctx.complete();
    }

    // REGRESSION GUARD: a slab beside ANOTHER slab that is EQUALLY lowered (both anchored via the
    // same adjacent full-block source) must NOT redraw — only a genuine height STEP should.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void twoEquallyLoweredSlabsNeverRedraw(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 1);
        BlockPos dirtPos = slabPos.up();
        BlockPos slabAPos = dirtPos.east();
        BlockPos slabBPos = slabAPos.east();

        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));

        w.setBlockState(slabAPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, slabAPos, w.getBlockState(slabAPos));
        w.setBlockState(slabBPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, slabBPos, w.getBlockState(slabBPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, slabAPos) && SlabAnchorAttachment.isAnchored(w, slabBPos),
                "setup: both slabs must anchor via horizontal adjacency to the anchored dirt / each other");

        boolean stepFace = SlabSupport.isSlabHeightStepFace(w, slabAPos, w.getBlockState(slabAPos), Direction.EAST);
        ctx.assertTrue(!stepFace, "two EQUALLY lowered slabs (no height step) must not redraw; got " + stepFace);
        ctx.complete();
    }

    // ------------------------------------------------------------------------
    // MAGNITUDE BLINDNESS (Maintainer, 2026-08-06, live on the /slabrig mega board: "back row has
    // DODOs still" — REAL see-through holes). The recorder board (session cc01ab17) shows five
    // laterally-adjacent pairs of ANCHORED stone at alternating -1.0 / -0.5 along the back row,
    // e.g. (104,-57,1)=-1.0 beside (105,-57,1)=-0.5. Each pair exposes a 0.5-block seam.
    //
    // isLoweredOpaqueFullCubeForStepCull returns a BOOLEAN, so both a -1.0 and a -0.5 anchored
    // cube read `true`, and `selfLowered != neighborLowered` evaluates `true != true` = FALSE —
    // no step face, seam culled, see-through hole. The predicate knows THAT a block is lowered
    // but not BY HOW MUCH.
    //
    // Commit 3a3f57e7 (TS-compat 1.21.11 line, 2026-06-12) fixed exactly this with a dy-difference
    // comparison; this line never received it and independently evolved the boolean form, which
    // gained the widened slab eligibility above and lost magnitude sensitivity.
    // ------------------------------------------------------------------------

    private static final double EPS = 1.0e-6;

    // THE RED: two ANCHORED opaque full cubes side by side at -1.0 and -0.5. Both are "lowered",
    // so the boolean form sees no disagreement and culls the real 0.5 seam between them.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void minusOneCubeBesideMinusHalfCubeRedrawsSteppedFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos deep = buildAnchoredMinusOneCube(ctx, 2, 2);
        BlockPos shallow = buildAnchoredMinusHalfNeighbor(ctx, deep.east(), Blocks.STONE.getDefaultState(), -0.5);

        boolean deepFace = SlabSupport.isSlabHeightStepFace(w, deep, w.getBlockState(deep), Direction.EAST);
        ctx.assertTrue(deepFace,
                "an anchored -1.0 cube beside an anchored -0.5 cube exposes a real 0.5 seam and must "
                        + "redraw its stepped face — both sides are 'lowered', so the boolean "
                        + "lowered/not-lowered form is magnitude-blind and culls it (Maintainer's live "
                        + "back-row DODOs); got " + deepFace);

        // The GH#24 lesson: BOTH sides of the seam must be redrawn, not just one.
        boolean shallowFace = SlabSupport.isSlabHeightStepFace(w, shallow, w.getBlockState(shallow), Direction.WEST);
        ctx.assertTrue(shallowFace,
                "the -0.5 cube's own face toward the -1.0 cube must be redrawn too; got " + shallowFace);
        ctx.complete();
    }

    // Magnitude blindness combined with THIS line's widened slab eligibility: an anchored SLAB at
    // -0.5 beside an anchored cube at -1.0. Guards that restoring magnitude sensitivity does not
    // cost the slab-as-subject/neighbour widening (which commit 3a3f57e7 never had).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void minusOneCubeBesideAnchoredMinusHalfSlabRedrawsSteppedFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos deep = buildAnchoredMinusOneCube(ctx, 2, 2);
        BlockPos slab = buildAnchoredMinusHalfNeighbor(ctx, deep.east(), bottomSlab(Blocks.BIRCH_SLAB), -0.5);
        ctx.assertTrue(!w.getBlockState(slab).isOpaqueFullCube(),
                "setup: the neighbour must be a real slab, not an opaque full cube (that is the widening)");

        boolean slabFace = SlabSupport.isSlabHeightStepFace(w, slab, w.getBlockState(slab), Direction.WEST);
        ctx.assertTrue(slabFace,
                "an anchored -0.5 SLAB beside an anchored -1.0 cube must redraw its stepped face — "
                        + "magnitude sensitivity must not cost the widened slab eligibility; got " + slabFace);

        boolean cubeFace = SlabSupport.isSlabHeightStepFace(w, deep, w.getBlockState(deep), Direction.EAST);
        ctx.assertTrue(cubeFace, "the cube's own face toward the lowered slab must be redrawn too; got " + cubeFace);
        ctx.complete();
    }

    // PERF GUARD (this is the chunk-render hot path; the project has shipped a lag regression
    // twice). Height resolution is a bounded column walk — it must happen ONLY when both sides are
    // lowered candidates and the answer genuinely depends on the magnitudes. Ordinary terrain, and
    // the "exactly one side lowered" case that the boolean form already answered, must both resolve
    // ZERO heights. Counter-based, never wall-clock: a fixed-ms budget false-REDs on a loaded
    // machine. The final cell is a same-run CALIBRATION — without it a permanently-broken counter
    // would make every zero assertion vacuously green.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fastPathsResolveZeroHeights(TestContext ctx) {
        ServerWorld w = ctx.getWorld();

        // (a) ordinary terrain: neither side lowered.
        BlockPos flushA = ctx.getAbsolutePos(BlockPos.ORIGIN).add(0, 6, 0);
        BlockPos flushB = flushA.east();
        w.setBlockState(flushA, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(flushB, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);

        // (b) exactly one side lowered — already decidable without any height.
        BlockPos oneSlab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(5, 5, 5);
        BlockPos oneLowered = oneSlab.up();
        BlockPos oneFlush = oneLowered.east();
        w.setBlockState(oneSlab, bottomSlab(Blocks.SMOOTH_STONE_SLAB), Block.NOTIFY_LISTENERS);
        w.setBlockState(oneLowered, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, oneLowered, w.getBlockState(oneLowered));
        w.setBlockState(oneFlush, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, oneLowered) && !SlabAnchorAttachment.isAnchored(w, oneFlush),
                "setup: exactly one of the pair must be a lowered candidate");

        // (c) calibration: both sides lowered at different magnitudes — the only case that may pay.
        BlockPos deep = buildAnchoredMinusOneCube(ctx, 2, 2);
        BlockPos shallow = buildAnchoredMinusHalfNeighbor(ctx, deep.east(), Blocks.STONE.getDefaultState(), -0.5);

        SlabSupport.beginStepCullHeightResolutionCount();
        try {
            SlabSupport.isSlabHeightStepFace(w, flushA, w.getBlockState(flushA), Direction.EAST);
            long afterFlush = SlabSupport.stepCullHeightResolutionCount();
            ctx.assertTrue(afterFlush == 0L,
                    "PERF: ordinary terrain (neither side lowered) must resolve ZERO heights on the "
                            + "chunk-render hot path; resolved " + afterFlush);

            SlabSupport.isSlabHeightStepFace(w, oneLowered, w.getBlockState(oneLowered), Direction.EAST);
            long afterOne = SlabSupport.stepCullHeightResolutionCount();
            ctx.assertTrue(afterOne == 0L,
                    "PERF: 'exactly one side lowered' is decidable from the cheap prefilter alone and "
                            + "must resolve ZERO heights; resolved " + afterOne);

            SlabSupport.isSlabHeightStepFace(w, deep, w.getBlockState(deep), Direction.EAST);
            long afterBoth = SlabSupport.stepCullHeightResolutionCount();
            ctx.assertTrue(afterBoth == 2L,
                    "CALIBRATION: the both-sides-lowered case must resolve exactly the two heights it "
                            + "compares — otherwise the zero assertions above are vacuous (a dead "
                            + "counter reads 0 forever); resolved " + afterBoth
                            + ", shallow neighbour at " + shallow.toShortString());
        } finally {
            SlabSupport.endStepCullHeightResolutionCount();
        }
        ctx.complete();
    }

    // ------------------------------------------------------------------------

    /**
     * Builds the proven vanilla-only {@code follower_on_minus_one} recipe (the same scene
     * {@code AnchoredFollowerSupportDyTest} keeps green) at plot-relative {@code (x, z)},
     * occupying {@code z} and {@code z + 1}, and returns an ANCHORED opaque full cube at -1.0.
     * Terrain Slabs cannot load on this line, so the -1.0 must come from a vanilla stack.
     */
    private BlockPos buildAnchoredMinusOneCube(TestContext ctx, int x, int z) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(x, 1, z);
        BlockPos source = base.add(0, 0, 1);

        // Source column: stone / bottom slab / stone — the top stone is lowered -0.5 by the slab.
        w.setBlockState(source, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(source.up(), bottomSlab(Blocks.STONE_SLAB), Block.NOTIFY_LISTENERS);
        BlockPos sourceTop = source.up(2);
        w.setBlockState(sourceTop, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, sourceTop, w.getBlockState(sourceTop));

        // Seat column: ground stone, AIR, then the seat slab beside the lowered source — a
        // legitimate cantilever (air under the seat), not the interpenetration shape the
        // flush-seat guard outlaws.
        w.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos seat = base.up(2);
        w.setBlockState(seat, bottomSlab(Blocks.STONE_SLAB), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, seat, w.getBlockState(seat));

        BlockPos subject = seat.up();
        w.setBlockState(subject, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, subject, w.getBlockState(subject));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, subject),
                "fixture: the -1.0 subject must carry a real anchor — an un-anchored scene takes a "
                        + "different lane and FALSE-GREENS this test");
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(dy + 1.0) <= EPS,
                "fixture: the subject cube must render -1.0, got " + dy);
        return subject;
    }

    /**
     * Stands {@code state} at {@code pos} on a flush bottom slab over solid ground and anchors it,
     * so it renders {@code expectedDy} (the shallow half of the alternating back-row pair).
     */
    private BlockPos buildAnchoredMinusHalfNeighbor(TestContext ctx, BlockPos pos, BlockState state, double expectedDy) {
        ServerWorld w = ctx.getWorld();
        w.setBlockState(pos.down(3), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(pos.down(2), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(pos.down(), bottomSlab(Blocks.STONE_SLAB), Block.NOTIFY_LISTENERS);
        w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, pos, w.getBlockState(pos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, pos),
                "fixture: the shallow neighbour must be anchored — that is what makes BOTH sides "
                        + "'lowered' and exposes the magnitude blindness");
        double dy = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        ctx.assertTrue(Math.abs(dy - expectedDy) <= EPS,
                "fixture: the shallow neighbour must render " + expectedDy + ", got " + dy);
        return pos;
    }

    private static BlockState bottomSlab(Block slab) {
        return slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }
}
