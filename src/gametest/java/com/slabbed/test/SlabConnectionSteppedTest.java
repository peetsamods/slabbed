package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WallShape;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/**
 * Headless pins for GH #21 on main: a fence/wall/pane placed on a VANILLA slab must render
 * flush (model dy tracks the outline — a live-only render claim, not tested here), and it must
 * NOT draw a connector arm to a same-Y neighbour that sits at a different visual height.
 *
 * <p>Before this fix, {@code OffsetBlockStateModel.emitQuads} forced a connecting block's model
 * dy back to 0 unless it sat on a named custom Terrain Slabs surface, and there was no
 * BlockState-level mechanism at all to break the connector arm across a height step — these
 * tests exercise the actual placement/neighbor-update pipeline (via
 * {@code FencePaneSlabConnectionMixin}/{@code WallSlabConnectionMixin}), not just the raw
 * {@link SlabSupport#isSteppedConnectingNeighbor} helper in isolation.
 */
public final class SlabConnectionSteppedTest {

    private static BlockState vanillaBottomSlab() {
        return Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /**
     * The WYSIWYG-triad pin the original GH #21 fix was MISSING: it is not enough for
     * the model to render lowered — the OUTLINE (and raycast) shape must be offset by the
     * same dy, or the player aims at the visually-lowered fence and the raycast targets a
     * phantom half a block above it. This directly reproduces the "vanilla slabs fence
     * raycast broken" regression: before the SlabSupportStateMixin fix, the outline of a
     * fence/wall/pane on a vanilla slab was pinned at grid height while the model dropped.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceOutlineFollowsLoweredModelOnVanillaSlab(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        assertOutlineLowered(ctx, w, Blocks.OAK_FENCE.getDefaultState(), "fence");
        assertOutlineLowered(ctx, w, Blocks.COBBLESTONE_WALL.getDefaultState(), "wall");
        assertOutlineLowered(ctx, w, Blocks.GLASS_PANE.getDefaultState(), "pane");
        ctx.complete();
    }

    private static void assertOutlineLowered(TestContext ctx, ServerWorld w, BlockState connector, String label) {
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(slabPos, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        BlockPos connPos = slabPos.up();
        w.setBlockState(connPos, connector, Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getVisualYOffset(w, connPos, w.getBlockState(connPos));
        ctx.assertTrue(dy < -1.0e-6, label + " on a vanilla bottom slab must resolve a lowered dy, got " + dy);

        VoxelShape outline = w.getBlockState(connPos).getOutlineShape(w, connPos);
        ctx.assertTrue(!outline.isEmpty(), label + " outline must not be empty");
        Box box = outline.getBoundingBox();
        // The outline is in the block's LOCAL frame; a grid-height connector starts at
        // minY≈0, a correctly-lowered one starts at minY≈dy (≈ -0.5).
        ctx.assertTrue(Math.abs(box.minY - dy) < 1.0e-6,
                label + " outline minY must follow the lowered dy (" + dy + "), but was " + box.minY
                        + " — the outline/raycast is NOT tracking the lowered model (GH #21 raycast regression)");

        // Clean up so each connector type starts from empty.
        w.setBlockState(connPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(slabPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceConnectionBreaksAcrossVanillaSlabStep(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos groundBase = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(groundBase, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos groundFence = groundBase.up();
        w.setBlockState(groundFence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        // Adjacent column, placed AFTER the ground fence so the ground fence's state updates:
        // a VANILLA oak bottom slab + a fence lowered onto it (same Y as the ground fence).
        BlockPos slabBase = groundBase.east();
        w.setBlockState(slabBase, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        BlockPos loweredFence = slabBase.up();
        w.setBlockState(loweredFence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(
                SlabSupport.getYOffset(w, loweredFence, w.getBlockState(loweredFence)) < -1.0e-6,
                "a fence on a vanilla bottom slab must be lowered (getYOffset < 0) so its model "
                        + "matches its outline (GH #21 — the render half of this needs a live check)");

        BlockState groundFenceState = w.getBlockState(groundFence);
        BlockState loweredFenceState = w.getBlockState(loweredFence);
        ctx.assertTrue(
                !groundFenceState.get(ConnectingBlock.FACING_PROPERTIES.get(Direction.EAST)),
                "a ground fence must NOT draw a connector arm toward a fence lowered onto a "
                        + "VANILLA slab at the same Y (GH #21 height-step regression)");
        ctx.assertTrue(
                !loweredFenceState.get(ConnectingBlock.FACING_PROPERTIES.get(Direction.WEST)),
                "the lowered fence must NOT draw a connector arm back toward the ground fence either");

        // Control: a second ground fence at the same height must still connect normally.
        BlockPos groundBase2 = groundBase.west();
        w.setBlockState(groundBase2, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos groundFence2 = groundBase2.up();
        w.setBlockState(groundFence2, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(
                w.getBlockState(groundFence).get(ConnectingBlock.FACING_PROPERTIES.get(Direction.WEST)),
                "two ground fences at the same height must still connect normally (control)");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void paneConnectionBreaksAcrossVanillaSlabStep(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos groundBase = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(groundBase, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos groundPane = groundBase.up();
        w.setBlockState(groundPane, Blocks.GLASS_PANE.getDefaultState(), Block.NOTIFY_LISTENERS);

        BlockPos slabBase = groundBase.east();
        w.setBlockState(slabBase, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        BlockPos loweredPane = slabBase.up();
        w.setBlockState(loweredPane, Blocks.GLASS_PANE.getDefaultState(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(
                SlabSupport.getYOffset(w, loweredPane, w.getBlockState(loweredPane)) < -1.0e-6,
                "a pane on a vanilla bottom slab must be lowered (getYOffset < 0)");
        ctx.assertTrue(
                !w.getBlockState(groundPane).get(ConnectingBlock.FACING_PROPERTIES.get(Direction.EAST)),
                "a ground pane must NOT connect toward a pane lowered onto a VANILLA slab at the same Y");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void wallConnectionBreaksAcrossVanillaSlabStep(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos groundBase = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(groundBase, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos groundWall = groundBase.up();
        w.setBlockState(groundWall, Blocks.COBBLESTONE_WALL.getDefaultState(), Block.NOTIFY_LISTENERS);

        BlockPos slabBase = groundBase.east();
        w.setBlockState(slabBase, vanillaBottomSlab(), Block.NOTIFY_LISTENERS);
        BlockPos loweredWall = slabBase.up();
        w.setBlockState(loweredWall, Blocks.COBBLESTONE_WALL.getDefaultState(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(
                SlabSupport.getYOffset(w, loweredWall, w.getBlockState(loweredWall)) < -1.0e-6,
                "a wall on a vanilla bottom slab must be lowered (getYOffset < 0)");
        ctx.assertTrue(
                w.getBlockState(groundWall).get(WallBlock.WALL_SHAPE_PROPERTIES_BY_DIRECTION.get(Direction.EAST))
                        == WallShape.NONE,
                "a ground wall must NOT connect toward a wall lowered onto a VANILLA slab at the same Y");
        ctx.complete();
    }
}
