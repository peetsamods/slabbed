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
    // MAGNITUDE BLINDNESS (the maintainer, 2026-08-06, live on the /slabrig mega board: "back row has
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
                        + "lowered/not-lowered form is magnitude-blind and culls it (the maintainer's live "
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

    // ------------------------------------------------------------------------
    // ANCHOR-BOOLEAN ELIGIBILITY (the maintainer, live re-test 2026-08-06: the back-row see-through holes
    // SURVIVED the magnitude fix). Recorder session 8248751f pins the surviving pair:
    //
    //     dy -0.5 (anchor=none)  |  dy -1.0 (anchor=none)     -> both UNANCHORED
    //
    // and her /slabdy readout on the deep cell reads dy=-1.000, src=geometric, FLAGS: DODO.
    //
    // isSlabHeightStepFace dispatched on isLoweredOpaqueFullCubeForStepCull, which is
    //   isDirectCustomSlabSupportedObject(..) || ((opaqueFullCube || slab) && isAnchored(..))
    // — an ANCHOR BOOLEAN. Both cells are lowered PURELY GEOMETRICALLY (column inheritance; no
    // anchor was ever recorded), so both read "not lowered", the pair fell into the neither-lowered
    // tier, and the magnitude comparison the previous commit added was never reached.
    //
    // MAINTAINER'S LAW (2026-08-06): "everything should be able to lower; no exceptions" — eligibility
    // for lowering-related behaviour may not depend on a block having earned an anchor. So the
    // step test must key on RESOLVED HEIGHT, not on anchor status.
    // ------------------------------------------------------------------------

    // THE RED: the maintainer's exact live pair, rebuilt with NO anchors anywhere in the scene. Both cubes
    // are lowered by pure column geometry (-0.5 and -1.0), and both assert !isAnchored so the test
    // cannot silently pass through the anchored (tier 3) lane that already worked.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unanchoredGeometricMinusOneBesideMinusHalfRedrawsSteppedFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        GeometricStepPair pair = buildUnanchoredGeometricStepPair(ctx, 1, 0);

        boolean shallowFace = SlabSupport.isSlabHeightStepFace(
                w, pair.shallow(), w.getBlockState(pair.shallow()), Direction.WEST);
        ctx.assertTrue(shallowFace,
                "THE RED (the maintainer live 2026-08-06, recorder 8248751f): a GEOMETRICALLY lowered -0.5 "
                        + "cube with anchor=none beside a GEOMETRICALLY lowered -1.0 cube with "
                        + "anchor=none exposes a real 0.5 seam and must redraw its stepped face. "
                        + "Step-cull eligibility keyed on an ANCHOR BOOLEAN, so neither side counted "
                        + "as 'lowered' and the seam stayed culled; got " + shallowFace);

        // The GH#24 lesson: BOTH sides of the seam must be redrawn, not just one.
        boolean deepFace = SlabSupport.isSlabHeightStepFace(
                w, pair.deep(), w.getBlockState(pair.deep()), Direction.EAST);
        ctx.assertTrue(deepFace,
                "the unanchored -1.0 cube's own face toward the unanchored -0.5 cube must be "
                        + "redrawn too; got " + deepFace);
        ctx.complete();
    }

    // Same law, the MIXED anchor state: one side anchored, the other lowered to a DIFFERENT depth
    // by pure geometry. The old form answered this one correctly by accident (exactly one side
    // read "lowered" -> true), so it is a keep-green guard that the new height-based dispatch must
    // not lose while it stops keying on the anchor.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredCubeBesideUnanchoredGeometricCubeRedrawsSteppedFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        // Same scene as the RED, then anchor ONLY the deep side — a mixed-state pair.
        GeometricStepPair pair = buildUnanchoredGeometricStepPair(ctx, 1, 3);
        SlabAnchorAttachment.addAnchor(w, pair.deep(), w.getBlockState(pair.deep()));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, pair.deep())
                        && !SlabAnchorAttachment.isAnchored(w, pair.shallow()),
                "setup: exactly one side of the step must carry an anchor (the MIXED state)");

        boolean shallowFace = SlabSupport.isSlabHeightStepFace(
                w, pair.shallow(), w.getBlockState(pair.shallow()), Direction.WEST);
        ctx.assertTrue(shallowFace,
                "an unanchored, geometrically lowered cube beside an ANCHORED deeper cube must "
                        + "redraw its stepped face; got " + shallowFace);

        boolean deepFace = SlabSupport.isSlabHeightStepFace(
                w, pair.deep(), w.getBlockState(pair.deep()), Direction.EAST);
        ctx.assertTrue(deepFace,
                "and the anchored side's own face toward it too; got " + deepFace);
        ctx.complete();
    }

    // REGRESSION GUARD for the new tier: two cubes lowered EQUALLY by pure geometry (both -0.5,
    // both anchor=none) must still NOT redraw. Making eligibility height-based must not turn every
    // slab-topped terrace into extra faces — only a genuine height STEP counts.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void twoEquallyGeometricallyLoweredCubesNeverRedraw(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos a = ctx.getAbsolutePos(BlockPos.ORIGIN).add(4, 4, 6);
        BlockPos b = a.east();
        for (BlockPos p : new BlockPos[] {a, b}) {
            w.setBlockState(p.down(2), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            w.setBlockState(p.down(), bottomSlab(Blocks.STONE_SLAB), Block.NOTIFY_LISTENERS);
            w.setBlockState(p, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, a) && !SlabAnchorAttachment.isAnchored(w, b),
                "setup: neither cube may be anchored");
        double dyA = SlabSupport.getYOffset(w, a, w.getBlockState(a));
        double dyB = SlabSupport.getYOffset(w, b, w.getBlockState(b));
        ctx.assertTrue(Math.abs(dyA - dyB) <= EPS && dyA < -EPS,
                "setup: both cubes must be geometrically lowered to the SAME depth, got "
                        + dyA + " and " + dyB);

        boolean stepFace = SlabSupport.isSlabHeightStepFace(w, a, w.getBlockState(a), Direction.EAST);
        ctx.assertTrue(!stepFace,
                "two EQUALLY (geometrically) lowered cubes have no height step and must not "
                        + "redraw; got " + stepFace);
        ctx.complete();
    }

    // PERF GUARD (this is the chunk-render hot path; the project has shipped a lag regression
    // twice). Height resolution is a bounded walk that takes a global monitor — it must happen ONLY
    // when the answer genuinely depends on the magnitudes. Ordinary terrain, and the "exactly one
    // side lowered" case that the boolean form already answered, must both resolve ZERO heights;
    // the paths that DO pay must be bounded and lazily one-sided. Counter-based, never wall-clock:
    // a fixed-ms budget false-REDs on a loaded machine. The calibration cells are same-run — without
    // them a permanently-broken counter would make every zero assertion vacuously green.
    //
    // FIXTURE CHANGE (2026-08-06, reported, not silently adjusted): cell (a) previously stood its
    // "ordinary terrain" pair in MID-AIR. Air below a block is not ordinary terrain — it is the
    // gap-fill / cantilever lowering lane, which renders -0.5 with anchor=none — so that fixture
    // never tested the thing it named, and the height-based tier legitimately pays there. It now
    // stands on solid ground, which is what ordinary terrain means; the mid-air case is asserted
    // separately in cell (e) as a BOUNDED cost rather than a free one.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fastPathsResolveZeroHeights(TestContext ctx) {
        ServerWorld w = ctx.getWorld();

        // (a) ordinary terrain: two solid cubes standing on solid ground, nothing lowered.
        BlockPos flushA = ctx.getAbsolutePos(BlockPos.ORIGIN).add(0, 5, 0);
        BlockPos flushB = flushA.east();
        w.setBlockState(flushA.down(), Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(flushB.down(), Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
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

        // (c) calibration: both sides ANCHOR-lowered at different magnitudes.
        BlockPos deep = buildAnchoredMinusOneCube(ctx, 2, 2);
        BlockPos shallow = buildAnchoredMinusHalfNeighbor(ctx, deep.east(), Blocks.STONE.getDefaultState(), -0.5);

        // (d) the NEW tier: both sides lowered by pure GEOMETRY, no anchors. Bounded at two.
        GeometricStepPair geometric = buildUnanchoredGeometricStepPair(ctx, 5, 7);

        // (e) one-sided laziness: a geometrically lowered cube beside genuine flush terrain. The
        // flush side is screened out structurally and must NOT be resolved, so this costs ONE.
        BlockPos loweredSide = ctx.getAbsolutePos(BlockPos.ORIGIN).add(0, 3, 3);
        BlockPos flushSide = loweredSide.east();
        w.setBlockState(loweredSide.down(2), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(loweredSide.down(), bottomSlab(Blocks.STONE_SLAB), Block.NOTIFY_LISTENERS);
        w.setBlockState(loweredSide, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(flushSide.down(2), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(flushSide.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(flushSide, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        SlabSupport.beginStepCullHeightResolutionCount();
        try {
            SlabSupport.isSlabHeightStepFace(w, flushA, w.getBlockState(flushA), Direction.EAST);
            long afterFlush = SlabSupport.stepCullHeightResolutionCount();
            ctx.assertTrue(afterFlush == 0L,
                    "PERF: ordinary terrain (solid cubes on solid ground, nothing lowered) must "
                            + "resolve ZERO heights on the chunk-render hot path — the structural "
                            + "screen has to decide it from the block below alone; resolved " + afterFlush);

            SlabSupport.isSlabHeightStepFace(w, oneLowered, w.getBlockState(oneLowered), Direction.EAST);
            long afterOne = SlabSupport.stepCullHeightResolutionCount();
            ctx.assertTrue(afterOne == 0L,
                    "PERF: 'exactly one side definitely lowered' is decidable from the cheap "
                            + "prefilter alone and must resolve ZERO heights; resolved " + afterOne);

            SlabSupport.isSlabHeightStepFace(w, deep, w.getBlockState(deep), Direction.EAST);
            long afterBoth = SlabSupport.stepCullHeightResolutionCount();
            ctx.assertTrue(afterBoth == 2L,
                    "CALIBRATION: the both-sides-anchored case must resolve exactly the two heights "
                            + "it compares — otherwise the zero assertions above are vacuous (a dead "
                            + "counter reads 0 forever); resolved " + afterBoth
                            + ", shallow neighbour at " + shallow.toShortString());

            SlabSupport.isSlabHeightStepFace(w, geometric.shallow(), w.getBlockState(geometric.shallow()),
                    Direction.WEST);
            long afterGeometric = SlabSupport.stepCullHeightResolutionCount();
            ctx.assertTrue(afterGeometric - afterBoth == 2L,
                    "PERF: the NEW unanchored-geometric tier must be BOUNDED at two heights per "
                            + "face (one per side), never an unbounded search; resolved "
                            + (afterGeometric - afterBoth));

            SlabSupport.isSlabHeightStepFace(w, loweredSide, w.getBlockState(loweredSide), Direction.EAST);
            long afterLazy = SlabSupport.stepCullHeightResolutionCount();
            ctx.assertTrue(afterLazy - afterGeometric == 1L,
                    "PERF: a side the structural screen rules out is known flush and must contribute "
                            + "0.0 WITHOUT a resolution, so a lowered cube beside plain terrain costs "
                            + "ONE height, not two; resolved " + (afterLazy - afterGeometric));
        } finally {
            SlabSupport.endStepCullHeightResolutionCount();
        }
        ctx.complete();
    }

    // ------------------------------------------------------------------------

    /** The two laterally-adjacent, ANCHOR-FREE cubes of the maintainer's live pair. */
    private record GeometricStepPair(BlockPos shallow, BlockPos deep) { }

    /**
     * Builds the maintainer's live failing pair with ZERO anchors anywhere, at plot-relative {@code (x, z)}
     * and {@code (x + 1, z)}:
     *
     * <pre>
     *   y+3          [stone -1.0]  [stone -0.5]   &lt;- the adjacent subjects
     *   y+2          [bottom slab] [bottom slab]
     *   y+1          [stone -0.5]  [stone  0.0]
     *   y            [bottom slab] [stone  0.0]
     * </pre>
     *
     * <p>Left (deep) column: the documented stacked/mixed lane — a bottom slab under a stone under
     * another bottom slab compounds to -1.0 at the top.
     * <br>Right (shallow) column: a plain stone on a bottom slab on solid ground -> -0.5. Its slab
     * sits on a genuinely FLUSH seat, so the flush-seat guard keeps it from inheriting the deep
     * column's drop sideways and the two columns stay at different heights.
     *
     * <p>Nothing here is ever {@code addAnchor}ed: every dy is pure live geometry, which is exactly
     * what {@code /slabdy} reported as {@code src=geometric, anchor=none}.
     */
    private GeometricStepPair buildUnanchoredGeometricStepPair(TestContext ctx, int x, int z) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(x, 1, z);

        w.setBlockState(base, bottomSlab(Blocks.STONE_SLAB), Block.NOTIFY_LISTENERS);
        w.setBlockState(base.up(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(base.up(2), bottomSlab(Blocks.STONE_SLAB), Block.NOTIFY_LISTENERS);
        BlockPos deep = base.up(3);
        w.setBlockState(deep, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        BlockPos shallow = deep.east();
        w.setBlockState(shallow.down(3), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(shallow.down(2), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(shallow.down(), bottomSlab(Blocks.STONE_SLAB), Block.NOTIFY_LISTENERS);
        w.setBlockState(shallow, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, shallow) && !SlabAnchorAttachment.isAnchored(w, deep),
                "fixture: NEITHER subject may be anchored — an anchored scene takes the tier-3 lane "
                        + "that already worked and FALSE-GREENS this test");
        double shallowDy = SlabSupport.getYOffset(w, shallow, w.getBlockState(shallow));
        ctx.assertTrue(Math.abs(shallowDy + 0.5) <= EPS,
                "fixture: the shallow subject must render -0.5 by pure geometry, got " + shallowDy);
        double deepDy = SlabSupport.getYOffset(w, deep, w.getBlockState(deep));
        ctx.assertTrue(Math.abs(deepDy + 1.0) <= EPS,
                "fixture: the deep subject must render -1.0 by pure geometry, got " + deepDy);
        return new GeometricStepPair(shallow, deep);
    }


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
