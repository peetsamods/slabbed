package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

/**
 * Item frames hang on the DRAWN face of their support (maintainer ruling, 2026-09-01): the
 * BOUNDING BOX shifts by the support's dy while the entity's REAL position stays at grid
 * height — moving the position corrupts the frame's derived grid cell and {@code canStayAttached}
 * then judges the wrong support. The flush control pins that an ordinary support keeps the vanilla
 * box exactly.
 *
 * <p>Headless boundary: these rows pin the entity-side box/position split only. The drawn
 * frame's shift is a render-layer mixin ({@code ItemFrameDrawnFaceRenderMixin}) that no server
 * gametest can observe — a double-offset there is only visible to eyes on a real frame.
 */
public final class ItemFrameWysiwygBoxTest {

    private static final double EPS = 1.0e-6;

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void frameBoxFollowsLoweredSupportPositionStaysGrid(TestContext ctx) {
        ServerWorld world = ctx.getWorld();

        // Anchored lowered support (the established fixture: seat on a bottom slab, anchor, clear).
        BlockPos supportRel = new BlockPos(2, 3, 2);
        ctx.setBlockState(supportRel.down(), bottomSlab());
        ctx.setBlockState(supportRel, Blocks.OAK_PLANKS.getDefaultState());
        BlockPos supportAbs = ctx.getAbsolutePos(supportRel);
        SlabAnchorAttachment.addAnchor(world, supportAbs, world.getBlockState(supportAbs));
        ctx.setBlockState(supportRel.down(), Blocks.AIR.getDefaultState());

        // Flush control support, same shape, no anchor.
        BlockPos controlRel = new BlockPos(2, 3, 5);
        ctx.setBlockState(controlRel, Blocks.OAK_PLANKS.getDefaultState());
        BlockPos controlAbs = ctx.getAbsolutePos(controlRel);

        ItemFrameEntity lowered = new ItemFrameEntity(world, supportAbs.offset(Direction.EAST), Direction.EAST);
        ItemFrameEntity control = new ItemFrameEntity(world, controlAbs.offset(Direction.EAST), Direction.EAST);
        world.spawnEntity(lowered);
        world.spawnEntity(control);

        Box loweredBox = lowered.getBoundingBox();
        Box controlBox = control.getBoundingBox();

        // The box carries the support's drawn height...
        ctx.assertTrue(Math.abs((loweredBox.minY - controlBox.minY) + 0.5) <= EPS
                        && Math.abs((loweredBox.maxY - controlBox.maxY) + 0.5) <= EPS,
                "frame box beside a -0.5 support must sit exactly 0.5 below the flush control's box; got minY delta "
                        + (loweredBox.minY - controlBox.minY) + " maxY delta " + (loweredBox.maxY - controlBox.maxY));
        // ...while the entity position does not differ (the no-position-shift invariant).
        ctx.assertTrue(Math.abs(lowered.getY() - control.getY()) <= EPS,
                "frame entity Y must stay at grid height; lowered frame Y "
                        + lowered.getY() + " vs control " + control.getY());
        // And both frames still judge their real supports.
        ctx.assertTrue(lowered.canStayAttached() && control.canStayAttached(),
                "both frames must survive on their supports (lowered=" + lowered.canStayAttached()
                        + " control=" + control.canStayAttached() + ")");
        // Cross-version tripwire (sibling-line finding, 2026-09-02): on some MC versions the
        // entity xyz is re-derived FROM the box, so a box shift feeds back into the position on
        // a later recalculation. Re-assert the split after a few ticks, not only at spawn.
        ctx.runAtTick(5, () -> {
            ctx.assertTrue(Math.abs(lowered.getY() - control.getY()) <= EPS,
                    "frame entity Y drifted after ticks — a deferred recalculation derived the "
                            + "position from the shifted box; lowered Y " + lowered.getY()
                            + " vs control " + control.getY());
            ctx.complete();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void frameBoxOnFlushSupportKeepsVanillaBox(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos supportRel = new BlockPos(2, 3, 2);
        ctx.setBlockState(supportRel, Blocks.OAK_PLANKS.getDefaultState());
        BlockPos supportAbs = ctx.getAbsolutePos(supportRel);

        ItemFrameEntity frame = new ItemFrameEntity(world, supportAbs.offset(Direction.EAST), Direction.EAST);
        world.spawnEntity(frame);

        // NEGATIVE CONTROL: a flush support leaves the box exactly where the entity position
        // implies it — box center Y equals the entity Y (vanilla frame geometry is centred).
        Box box = frame.getBoundingBox();
        double boxCenterY = (box.minY + box.maxY) / 2.0;
        ctx.assertTrue(Math.abs(boxCenterY - frame.getY()) <= EPS,
                "flush-support frame box must stay centred on the entity position; center "
                        + boxCenterY + " vs entity Y " + frame.getY());
        ctx.complete();
    }
}
