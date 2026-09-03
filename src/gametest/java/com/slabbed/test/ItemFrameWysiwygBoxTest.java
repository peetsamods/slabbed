package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

/**
 * Item frames hang on the DRAWN face of their support (maintainer ruling, 2026-09-01): the
 * BOUNDING BOX shifts by the support's dy while the entity's REAL position stays at grid
 * height — moving the position corrupts the frame's derived grid cell (BlockPos.containing in
 * the attachment math and every server position packet) and survives() then judges the wrong
 * support. The flush control pins that an ordinary support keeps the vanilla box exactly.
 *
 * <p>Headless boundary: these rows pin the entity-side box/position split only. The drawn
 * frame's shift is a render-layer mixin (ItemFrameDrawnFaceRenderMixin) that no server
 * gametest can observe — a double-offset there is only visible to eyes on a real frame.
 */
public final class ItemFrameWysiwygBoxTest {

    private static final double EPS = 1.0e-6;

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void frameBoxFollowsLoweredSupportPositionStaysGrid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // Anchored lowered support (the established fixture: seat on a bottom slab, anchor, clear).
        BlockPos supportRel = new BlockPos(2, 3, 2);
        helper.setBlock(supportRel.below(), bottomSlab());
        helper.setBlock(supportRel, Blocks.OAK_PLANKS.defaultBlockState());
        BlockPos supportAbs = helper.absolutePos(supportRel);
        SlabAnchorAttachment.addAnchor(level, supportAbs, level.getBlockState(supportAbs));
        helper.setBlock(supportRel.below(), Blocks.AIR.defaultBlockState());

        // Flush control support, same shape, no anchor.
        BlockPos controlRel = new BlockPos(2, 3, 5);
        helper.setBlock(controlRel, Blocks.OAK_PLANKS.defaultBlockState());
        BlockPos controlAbs = helper.absolutePos(controlRel);

        ItemFrame lowered = new ItemFrame(level, supportAbs.relative(Direction.EAST), Direction.EAST);
        ItemFrame control = new ItemFrame(level, controlAbs.relative(Direction.EAST), Direction.EAST);
        level.addFreshEntity(lowered);
        level.addFreshEntity(control);

        AABB loweredBox = lowered.getBoundingBox();
        AABB controlBox = control.getBoundingBox();

        // The box carries the support's drawn height...
        if (Math.abs((loweredBox.minY - controlBox.minY) + 0.5) > EPS
                || Math.abs((loweredBox.maxY - controlBox.maxY) + 0.5) > EPS) {
            throw helper.assertionException(supportRel,
                    "frame box beside a -0.5 support must sit exactly 0.5 below the flush control's box; got minY delta "
                            + (loweredBox.minY - controlBox.minY) + " maxY delta " + (loweredBox.maxY - controlBox.maxY));
        }
        // ...while the entity position does not differ (the no-position-shift invariant).
        if (Math.abs(lowered.getY() - control.getY()) > EPS) {
            throw helper.assertionException(supportRel,
                    "frame entity Y must stay at grid height; lowered frame Y "
                            + lowered.getY() + " vs control " + control.getY());
        }
        // And both frames still judge their real supports.
        if (!lowered.survives() || !control.survives()) {
            throw helper.assertionException(supportRel,
                    "both frames must survive on their supports (lowered=" + lowered.survives()
                            + " control=" + control.survives() + ")");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void frameBoxOnFlushSupportKeepsVanillaBox(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos supportRel = new BlockPos(2, 3, 2);
        helper.setBlock(supportRel, Blocks.OAK_PLANKS.defaultBlockState());
        BlockPos supportAbs = helper.absolutePos(supportRel);

        ItemFrame frame = new ItemFrame(level, supportAbs.relative(Direction.EAST), Direction.EAST);
        level.addFreshEntity(frame);

        // NEGATIVE CONTROL: a flush support leaves the box exactly where the entity position
        // implies it — box center Y equals the entity Y (vanilla frame geometry is centred).
        AABB box = frame.getBoundingBox();
        double boxCenterY = (box.minY + box.maxY) / 2.0;
        if (Math.abs(boxCenterY - frame.getY()) > EPS) {
            throw helper.assertionException(supportRel,
                    "flush-support frame box must stay centred on the entity position; center "
                            + boxCenterY + " vs entity Y " + frame.getY());
        }
        helper.succeed();
    }
}
