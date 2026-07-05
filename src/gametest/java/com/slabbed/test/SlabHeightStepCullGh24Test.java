package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * GH#24 (github.com/peetsamods/slabbed/issues/24): "Invisible side faces of top slabs when placed
 * mid air next to a full block that is placed on top of a bottom slab." Fixed on the 1.21.11
 * sibling branch (failure mode L12 in the cross-port matrix) by widening an {@code isOpaqueFullCube()}
 * -gated cull predicate ({@code isSlabHeightStepFace}) so slab subjects/neighbours are no longer
 * skipped.
 *
 * <p><b>This 26.2 branch has NO equivalent {@code SlabSupport} predicate to widen — the mechanism is
 * architecturally different.</b> Verified against current source (2026-07-05): the step/DODO
 * ghost-window cull lives entirely in the client render layer,
 * {@code OffsetBlockStateModel.slabbed$anyMismatchedNeighborDy} (currently lines 164-172 of
 * {@code src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java}), live-wired at
 * {@code emitQuads} (lines 76-78: {@code stepSeam = dy != 0.0f || slabbed$anyMismatchedNeighborDy(...)}).
 * That method is a PURE dy-comparison across all 6 {@link net.minecraft.core.Direction} neighbours —
 * {@code Math.abs(neighborDy - dy) > 1e-6}, where both {@code dy} and {@code neighborDy} come from
 * {@code slabbed$modelDy}, which for any non-carpet block delegates straight to
 * {@link SlabSupport#getYOffset}. There is NO {@code isOpaqueFullCube()} (or any other type) gate on
 * either the subject or the neighbour anywhere in that call chain — a slab subject is a first-class
 * citizen by construction, exactly like the sibling's fixed behaviour, just reached by a different
 * (render-side, dy-comparison) architecture rather than a widened {@code SlabSupport} predicate.
 *
 * <p>{@code OffsetBlockStateModel} lives in the {@code client} source set and this project's gametest
 * source set only wires the server-side {@code fabric-gametest} entrypoint (no working
 * {@code fabric-client-gametest} registration currently exists on this branch — see build.gradle's
 * gametest {@code include(...)} allowlist), so {@code slabbed$anyMismatchedNeighborDy} itself cannot be
 * invoked directly from a headless test here. Instead, following the same "prove the invariant the
 * render call site is built from" precedent {@code GhostLoweredCollisionProofTest} already uses on
 * this branch, this test proves the underlying, server-testable invariant the cull decision is a pure
 * function of: for the GH#24 scenario (a slab beside a lowered/anchored full block), {@code
 * SlabSupport.getYOffset} for the slab and its lowered neighbour DIFFER by more than the epsilon the
 * render layer uses — i.e. {@code slabbed$anyMismatchedNeighborDy} for that pair is guaranteed to
 * return {@code true} by construction, so the step face is never silently culled. A false-green here
 * would require {@code getYOffset} itself to stop reporting the height difference, which would be
 * visible as a scientific dy-table regression (see {@code Slabbed2612DyFingerprintTest}), not a
 * silently reintroduced GH#24.
 *
 * <p>Pure {@code (BlockGetter, BlockPos, BlockState) -> double} logic (no client dependency), so
 * headlessly provable even though the rendered pixels are not.
 */
public final class SlabHeightStepCullGh24Test {

    private static final double EPS = 1.0e-6;

    private static BlockState slab(Block slabBlock, SlabType type) {
        return slabBlock.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static BlockState bottomSlab(Block slabBlock) {
        return slab(slabBlock, SlabType.BOTTOM);
    }

    /**
     * GH#24 core proof, TOP slab as SUBJECT: a TOP slab resting on solid ground, beside a full block
     * lowered ({@code -0.5}) onto a bottom slab. The solid ground keeps the slab flush ({@code dy=0}),
     * so a genuine height step exists — {@code getYOffset} for the slab and its lowered neighbour
     * differ by 0.5, well past the render layer's 1e-6 epsilon, guaranteeing
     * {@code slabbed$anyMismatchedNeighborDy} (and therefore the cull-clearing {@code stepSeam} path)
     * fires for the slab's own face.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void groundedTopSlabBesideLoweredFullBlockIsStepFace(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockPos slabPos = ground.above();             // TOP slab on solid ground -> stays flush
        BlockPos fullSupportSlab = ground.east();      // bottom slab under the lowered full block
        BlockPos fullPos = fullSupportSlab.above();    // full block lowered -0.5, east of the slab

        w.setBlock(ground, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        w.setBlock(slabPos, slab(Blocks.OAK_SLAB, SlabType.TOP), Block.UPDATE_ALL);
        w.setBlock(fullSupportSlab, bottomSlab(Blocks.SMOOTH_STONE_SLAB), Block.UPDATE_ALL);
        w.setBlock(fullPos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        SlabAnchorAttachment.addAnchor(w, fullPos, w.getBlockState(fullPos));

        double slabDy = SlabSupport.getYOffset(w, slabPos, w.getBlockState(slabPos));
        double fullDy = SlabSupport.getYOffset(w, fullPos, w.getBlockState(fullPos));
        if (Math.abs(slabDy) > EPS) {
            throw helper.assertionException(slabPos,
                    "setup: a TOP slab on solid ground beside a lowered full block must stay flush at 0.0; got " + slabDy);
        }
        if (Math.abs(fullDy + 0.5) > EPS) {
            throw helper.assertionException(fullPos, "setup: the full block must be lowered -0.5; got " + fullDy);
        }

        double mismatch = Math.abs(slabDy - fullDy);
        if (mismatch <= EPS) {
            throw helper.assertionException(slabPos,
                    "GH#24 PROOF FAILED: the flush TOP slab and its lowered full-block neighbour must have "
                            + "a dy mismatch that slabbed$anyMismatchedNeighborDy's 1e-6 epsilon detects; got "
                            + mismatch);
        }
        helper.succeed();
    }

    /** Same GH#24 proof with a BOTTOM slab subject — the coverage is symmetric, not TOP-specific. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void groundedBottomSlabBesideLoweredFullBlockIsStepFace(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockPos slabPos = ground.above();
        BlockPos fullSupportSlab = ground.east();
        BlockPos fullPos = fullSupportSlab.above();

        w.setBlock(ground, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        w.setBlock(slabPos, bottomSlab(Blocks.OAK_SLAB), Block.UPDATE_ALL);
        w.setBlock(fullSupportSlab, bottomSlab(Blocks.SMOOTH_STONE_SLAB), Block.UPDATE_ALL);
        w.setBlock(fullPos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        SlabAnchorAttachment.addAnchor(w, fullPos, w.getBlockState(fullPos));

        double slabDy = SlabSupport.getYOffset(w, slabPos, w.getBlockState(slabPos));
        if (Math.abs(slabDy) > EPS) {
            throw helper.assertionException(slabPos,
                    "setup: a BOTTOM slab on solid ground beside a lowered full block must stay flush at 0.0; got " + slabDy);
        }
        double fullDy = SlabSupport.getYOffset(w, fullPos, w.getBlockState(fullPos));

        double mismatch = Math.abs(slabDy - fullDy);
        if (mismatch <= EPS) {
            throw helper.assertionException(slabPos,
                    "GH#24 PROOF FAILED (BOTTOM): the flush BOTTOM slab and its lowered full-block neighbour "
                            + "must have a detectable dy mismatch; got " + mismatch);
        }
        helper.succeed();
    }

    /**
     * Homogenize path: a TOP slab placed WITHOUT solid support directly beside a lowered full block
     * side-lowers to the same {@code -0.5} (this branch's adjacent-side-slab-lowered behaviour), so
     * there is NO height step and {@code getYOffset} for the pair is EQUAL — the render layer correctly
     * treats this as "no mismatch" (no forced step-seam redraw needed; the pair is already visually
     * flush with each other, so there's no seam to expose).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void ungroundedSlabHomogenizesWithLoweredFullBlockNoStep(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos supportSlab = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockPos fullPos = supportSlab.above();          // lowered full block
        BlockPos slabPos = fullPos.east();               // TOP slab beside it, NO solid support below -> homogenizes

        w.setBlock(supportSlab, bottomSlab(Blocks.SMOOTH_STONE_SLAB), Block.UPDATE_ALL);
        w.setBlock(fullPos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        SlabAnchorAttachment.addAnchor(w, fullPos, w.getBlockState(fullPos));
        w.setBlock(slabPos, slab(Blocks.OAK_SLAB, SlabType.TOP), Block.UPDATE_ALL);

        double fullDy = SlabSupport.getYOffset(w, fullPos, w.getBlockState(fullPos));
        double slabDy = SlabSupport.getYOffset(w, slabPos, w.getBlockState(slabPos));
        if (Math.abs(fullDy + 0.5) > EPS) {
            throw helper.assertionException(fullPos, "setup: the full block must be lowered -0.5; got " + fullDy);
        }
        if (Math.abs(slabDy + 0.5) > EPS) {
            throw helper.assertionException(slabPos,
                    "branch behaviour: an unsupported slab beside a lowered full block homogenizes to -0.5; got " + slabDy);
        }

        double mismatch = Math.abs(slabDy - fullDy);
        if (mismatch > EPS) {
            throw helper.assertionException(slabPos,
                    "homogenized slab (both -0.5) must NOT report a dy mismatch to its neighbour "
                            + "(no ghost window — the slab is drawn lowered to match); got " + mismatch);
        }
        helper.succeed();
    }

    /**
     * Regression guard: two flush TOP slabs on solid ground side by side (neither lowered) must NOT
     * report a dy mismatch — the underlying invariant must not force redraws onto ordinary,
     * unlowered slab terrain.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void twoFlushGroundedTopSlabsNeverStep(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos groundA = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockPos groundB = groundA.east();
        BlockPos a = groundA.above();
        BlockPos b = groundB.above();
        w.setBlock(groundA, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        w.setBlock(groundB, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        w.setBlock(a, slab(Blocks.OAK_SLAB, SlabType.TOP), Block.UPDATE_ALL);
        w.setBlock(b, slab(Blocks.OAK_SLAB, SlabType.TOP), Block.UPDATE_ALL);

        double aDy = SlabSupport.getYOffset(w, a, w.getBlockState(a));
        double bDy = SlabSupport.getYOffset(w, b, w.getBlockState(b));
        if (Math.abs(aDy) > EPS || Math.abs(bDy) > EPS) {
            throw helper.assertionException(a,
                    "setup: two grounded TOP slabs must both be flush (0.0); got a=" + aDy + " b=" + bDy);
        }
        if (Math.abs(aDy - bDy) > EPS) {
            throw helper.assertionException(a, "two flush TOP slabs (no step) must never report a dy mismatch");
        }
        helper.succeed();
    }

    /**
     * Regression guard: two slabs EQUALLY lowered to {@code -0.5} (both homogenized directly beside
     * the SAME lowered full block, one on each side) must NOT report a mismatch against each other —
     * the invariant keys on the height STEP, not on "is a slab lowered".
     *
     * <p>Deliberately places both slabs directly adjacent to {@code fullPos} (east and west) rather
     * than chaining a second slab off the first (a multi-hop "slab lane" walk) — on THIS branch that
     * lane-propagation is placement-path-driven (see {@code Slabbed2612UseOnPlacementTest}'s RC2/RC3
     * useOn coverage and its own note that plain {@code helper.setBlock} "never calls onPlaced and
     * stays geometric"), so a second slab set via {@code setBlock} one hop further out does NOT
     * homogenize the same way a real placement would. Both slabs here sit at the same one-hop
     * geometric distance from the anchored full block, which IS proven to homogenize via plain
     * {@code setBlock} (matching {@link #ungroundedSlabHomogenizesWithLoweredFullBlockNoStep}).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void twoEquallyLoweredSlabsNeverStep(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos supportSlab = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockPos fullPos = supportSlab.above();
        BlockPos slabAPos = fullPos.east();   // homogenizes to -0.5
        BlockPos slabBPos = fullPos.west();   // homogenizes to -0.5, independently, same one-hop distance

        w.setBlock(supportSlab, bottomSlab(Blocks.SMOOTH_STONE_SLAB), Block.UPDATE_ALL);
        w.setBlock(fullPos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        SlabAnchorAttachment.addAnchor(w, fullPos, w.getBlockState(fullPos));
        w.setBlock(slabAPos, slab(Blocks.OAK_SLAB, SlabType.TOP), Block.UPDATE_ALL);
        w.setBlock(slabBPos, slab(Blocks.OAK_SLAB, SlabType.TOP), Block.UPDATE_ALL);

        double aDy = SlabSupport.getYOffset(w, slabAPos, w.getBlockState(slabAPos));
        double bDy = SlabSupport.getYOffset(w, slabBPos, w.getBlockState(slabBPos));
        if (Math.abs(aDy + 0.5) > EPS || Math.abs(bDy + 0.5) > EPS) {
            throw helper.assertionException(slabAPos,
                    "setup: both TOP slabs must homogenize to -0.5; got a=" + aDy + " b=" + bDy);
        }
        if (Math.abs(aDy - bDy) > EPS) {
            throw helper.assertionException(slabAPos,
                    "two EQUALLY lowered slabs (no height step) must not report a dy mismatch to each other");
        }
        helper.succeed();
    }
}
