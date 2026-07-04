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
        BlockPos a = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 8);
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
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 12);
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
}
