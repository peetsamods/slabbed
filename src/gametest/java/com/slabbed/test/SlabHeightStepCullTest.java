package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
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
}
