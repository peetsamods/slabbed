package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * GH#24 (github.com/peetsamods/slabbed/issues/24): "Invisible side faces of top slabs when placed
 * mid air next to a full block that is placed on top of a bottom slab." Reported against, and fixed
 * on, the 1.21.11 sibling branch (its {@code SlabHeightStepCullTest}) as failure-mode L12: there
 * {@link SlabSupport#isSlabHeightStepFace} carried an {@code isOpaqueFullCube()} subject/neighbour
 * gate — true for a DOUBLE slab but false for BOTTOM/TOP — so a slab's own face toward a lowered
 * full-block neighbour was never evaluated for a height-step redraw, leaving a see-through seam. The
 * sibling fixed it by widening that gate to also accept slab states.
 *
 * <p><b>This 1.21.1 branch has NO such gate — nothing to widen, no bug.</b> Verified against current
 * source (2026-07-05): {@link SlabSupport#isSlabHeightStepFace} (both overloads) and its private
 * {@code slabHeightStepFaceAgainst} helper purely compare live {@code getYOffset} values —
 * {@code |getYOffset(self) - getYOffset(neighbour)| > 1e-6} — with NO type check on either subject or
 * neighbour. A slab subject is a first-class citizen by construction; {@code getYOffset} is computed
 * for any block type. The live render call site in {@code OffsetBlockStateModel} (the per-quad
 * {@code cullFace}-clearing transform, and {@code slabbed$hasLoweredStepFace} which drives it) applies
 * no type filter either. GH#24 therefore cannot occur here by architecture, and no code change was
 * required; this class is the permanent regression guard proving it, exercising a SLAB as the
 * predicate SUBJECT — a case the pre-existing {@code SlabbedLabFixtureTest#slabHeightStepFacePredicate}
 * truth table never covered (its subjects and neighbours were all full blocks).
 *
 * <p><b>Two distinct, both-correct branch behaviours are pinned below</b> (measured, not assumed):
 * <ul>
 *   <li><b>Genuine slab-subject STEP (X/Y):</b> a slab resting on SOLID GROUND beside a lowered full
 *       block stays FLUSH ({@code dy=0}) — the solid support short-circuits the branch's
 *       adjacent-side-slab-lowered walk — so a real height step forms and the ungated predicate
 *       correctly returns {@code true} for the slab's OWN face (the direct GH#24 proof). Both slab
 *       types behave identically here.</li>
 *   <li><b>Homogenize, no step (H):</b> a slab placed WITHOUT solid support directly beside a lowered
 *       full block instead side-lowers to the same {@code -0.5} (this branch's homogenize path), so
 *       there is NO height step and the predicate correctly returns {@code false}. That is also
 *       ghost-window-free: the slab is drawn lowered, flush with its neighbour, leaving no exposed
 *       seam — a different, equally-correct resolution than the sibling's force-draw.</li>
 * </ul>
 *
 * <p>The grounded (X/Y) fixtures are deliberately built on a solid full block so the slab's dy is
 * decided by the local support, not by a horizontal slab-lane walk that could otherwise read across a
 * neighbouring gametest structure — keeping the assertions deterministic and isolation-immune.
 *
 * <p>Pure {@code (BlockView, BlockPos, BlockState, Direction) -> boolean} logic (no client
 * dependency), so headlessly provable even though the rendered pixels are not.
 */
public final class SlabHeightStepCullGh24Test {

    private static final double EPS = 1.0e-6;

    private static BlockState slab(Block slabBlock, SlabType type) {
        return slabBlock.getDefaultState().with(SlabBlock.TYPE, type);
    }

    private static BlockState bottomSlab(Block slabBlock) {
        return slab(slabBlock, SlabType.BOTTOM);
    }

    /**
     * GH#24 core proof, TOP slab as SUBJECT: a TOP slab resting on solid ground, beside a full block
     * lowered ({@code -0.5}) onto a bottom slab. The solid ground keeps the slab flush ({@code dy=0}),
     * so a genuine height step exists and the ungated predicate MUST detect the slab's own face — the
     * exact evaluation the sibling's {@code isOpaqueFullCube()} gate skipped there. The full block's
     * own face toward the slab is detected too, symmetrically.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void groundedTopSlabBesideLoweredFullBlockIsStepFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos slabPos = ground.up();               // TOP slab on solid ground -> stays flush
        BlockPos fullSupportSlab = ground.east();     // bottom slab under the lowered full block
        BlockPos fullPos = fullSupportSlab.up();       // full block lowered -0.5, east of the slab

        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(slabPos, slab(Blocks.OAK_SLAB, SlabType.TOP), Block.NOTIFY_LISTENERS);
        w.setBlockState(fullSupportSlab, bottomSlab(Blocks.SMOOTH_STONE_SLAB), Block.NOTIFY_LISTENERS);
        w.setBlockState(fullPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, fullPos, w.getBlockState(fullPos));

        ctx.assertTrue(!w.getBlockState(slabPos).isOpaqueFullCube(w, slabPos),
                "setup: a TOP slab must not be an opaque full cube (the sibling's L12 gap was keyed on exactly this)");
        double slabDy = SlabSupport.getYOffset(w, slabPos, w.getBlockState(slabPos));
        double fullDy = SlabSupport.getYOffset(w, fullPos, w.getBlockState(fullPos));
        ctx.assertTrue(Math.abs(slabDy) <= EPS,
                "setup: a TOP slab on solid ground beside a lowered full block must stay flush at 0.0; got " + slabDy);
        ctx.assertTrue(Math.abs(fullDy + 0.5) <= EPS, "setup: the full block must be lowered -0.5; got " + fullDy);

        ctx.assertTrue(SlabSupport.isSlabHeightStepFace(w, slabPos, w.getBlockState(slabPos), Direction.EAST),
                "GH#24 PROOF: a flush TOP slab beside a lowered full block is a slab-SUBJECT step face — the "
                        + "ungated predicate must detect it (there is no opaque-full-cube gate to skip slab subjects)");
        ctx.assertTrue(SlabSupport.isSlabHeightStepFace(w, fullPos, w.getBlockState(fullPos), Direction.WEST),
                "symmetry: the full block's own face toward the flush slab must also be a step face");
        ctx.complete();
    }

    /** Same GH#24 proof with a BOTTOM slab subject — the coverage is symmetric, not TOP-specific. */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void groundedBottomSlabBesideLoweredFullBlockIsStepFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos slabPos = ground.up();
        BlockPos fullSupportSlab = ground.east();
        BlockPos fullPos = fullSupportSlab.up();

        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(slabPos, bottomSlab(Blocks.OAK_SLAB), Block.NOTIFY_LISTENERS);
        w.setBlockState(fullSupportSlab, bottomSlab(Blocks.SMOOTH_STONE_SLAB), Block.NOTIFY_LISTENERS);
        w.setBlockState(fullPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, fullPos, w.getBlockState(fullPos));

        ctx.assertTrue(!w.getBlockState(slabPos).isOpaqueFullCube(w, slabPos),
                "setup: a BOTTOM slab must not be an opaque full cube");
        double slabDy = SlabSupport.getYOffset(w, slabPos, w.getBlockState(slabPos));
        ctx.assertTrue(Math.abs(slabDy) <= EPS,
                "setup: a BOTTOM slab on solid ground beside a lowered full block must stay flush at 0.0; got " + slabDy);

        ctx.assertTrue(SlabSupport.isSlabHeightStepFace(w, slabPos, w.getBlockState(slabPos), Direction.EAST),
                "GH#24 PROOF (BOTTOM): a flush BOTTOM slab beside a lowered full block is a slab-SUBJECT step face; "
                        + "the ungated predicate must detect it");
        ctx.complete();
    }

    /**
     * Homogenize path: a TOP slab placed WITHOUT solid support directly beside a lowered full block
     * side-lowers to the same {@code -0.5} (this branch's adjacent-side-slab-lowered behaviour), so
     * there is NO height step and the predicate correctly returns {@code false} on both shared faces.
     * That is the correct ghost-window-free outcome (the slab is drawn lowered to match its neighbour),
     * and it is exactly why GH#24's see-through seam cannot form on this branch: the slab never stays
     * at a different height from the lowered full block it abuts.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void ungroundedSlabHomogenizesWithLoweredFullBlockNoStep(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos supportSlab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos fullPos = supportSlab.up();          // lowered full block
        BlockPos slabPos = fullPos.east();            // TOP slab beside it, NO solid support below -> homogenizes

        w.setBlockState(supportSlab, bottomSlab(Blocks.SMOOTH_STONE_SLAB), Block.NOTIFY_LISTENERS);
        w.setBlockState(fullPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, fullPos, w.getBlockState(fullPos));
        w.setBlockState(slabPos, slab(Blocks.OAK_SLAB, SlabType.TOP), Block.NOTIFY_LISTENERS);

        double fullDy = SlabSupport.getYOffset(w, fullPos, w.getBlockState(fullPos));
        double slabDy = SlabSupport.getYOffset(w, slabPos, w.getBlockState(slabPos));
        ctx.assertTrue(Math.abs(fullDy + 0.5) <= EPS, "setup: the full block must be lowered -0.5; got " + fullDy);
        ctx.assertTrue(Math.abs(slabDy + 0.5) <= EPS,
                "branch behaviour: an unsupported slab beside a lowered full block homogenizes to -0.5; got " + slabDy);

        ctx.assertTrue(!SlabSupport.isSlabHeightStepFace(w, slabPos, w.getBlockState(slabPos), Direction.WEST),
                "homogenized slab (both -0.5): the slab's face toward the lowered full block must NOT be a step face "
                        + "(no ghost window — the slab is drawn lowered to match)");
        ctx.assertTrue(!SlabSupport.isSlabHeightStepFace(w, fullPos, w.getBlockState(fullPos), Direction.EAST),
                "homogenized pair (both -0.5): the full block's face toward the slab must NOT be a step face");
        ctx.complete();
    }

    /**
     * Regression guard: two flush TOP slabs on solid ground side by side (neither lowered) must NOT be
     * a step face — the ungated predicate must not force redraws onto ordinary, unlowered slab terrain.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void twoFlushGroundedTopSlabsNeverStep(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos groundA = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos groundB = groundA.east();
        BlockPos a = groundA.up();
        BlockPos b = groundB.up();
        w.setBlockState(groundA, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(groundB, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(a, slab(Blocks.OAK_SLAB, SlabType.TOP), Block.NOTIFY_LISTENERS);
        w.setBlockState(b, slab(Blocks.OAK_SLAB, SlabType.TOP), Block.NOTIFY_LISTENERS);

        double aDy = SlabSupport.getYOffset(w, a, w.getBlockState(a));
        double bDy = SlabSupport.getYOffset(w, b, w.getBlockState(b));
        ctx.assertTrue(Math.abs(aDy) <= EPS && Math.abs(bDy) <= EPS,
                "setup: two grounded TOP slabs must both be flush (0.0); got a=" + aDy + " b=" + bDy);
        ctx.assertTrue(!SlabSupport.isSlabHeightStepFace(w, a, w.getBlockState(a), Direction.EAST),
                "two flush TOP slabs (no step) must never be a step face");
        ctx.complete();
    }

    /**
     * Regression guard: two slabs EQUALLY lowered to {@code -0.5} (both homogenized beside the same
     * lowered full block) must NOT be a step face against each other — the predicate keys on the
     * height STEP, not on "is a slab lowered". (Guards against a false-positive that would force
     * redraws across a flush lowered terrace.)
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void twoEquallyLoweredSlabsNeverStep(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos supportSlab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos fullPos = supportSlab.up();
        BlockPos slabAPos = fullPos.east();   // homogenizes to -0.5
        BlockPos slabBPos = slabAPos.east();  // homogenizes to -0.5 via the slab lane

        w.setBlockState(supportSlab, bottomSlab(Blocks.SMOOTH_STONE_SLAB), Block.NOTIFY_LISTENERS);
        w.setBlockState(fullPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, fullPos, w.getBlockState(fullPos));
        w.setBlockState(slabAPos, slab(Blocks.OAK_SLAB, SlabType.TOP), Block.NOTIFY_LISTENERS);
        w.setBlockState(slabBPos, slab(Blocks.OAK_SLAB, SlabType.TOP), Block.NOTIFY_LISTENERS);

        double aDy = SlabSupport.getYOffset(w, slabAPos, w.getBlockState(slabAPos));
        double bDy = SlabSupport.getYOffset(w, slabBPos, w.getBlockState(slabBPos));
        ctx.assertTrue(Math.abs(aDy + 0.5) <= EPS && Math.abs(bDy + 0.5) <= EPS,
                "setup: both TOP slabs must homogenize to -0.5; got a=" + aDy + " b=" + bDy);
        ctx.assertTrue(!SlabSupport.isSlabHeightStepFace(w, slabAPos, w.getBlockState(slabAPos), Direction.EAST),
                "two EQUALLY lowered slabs (no height step) must not be a step face; the predicate keys on the step, not on 'lowered'");
        ctx.complete();
    }
}
