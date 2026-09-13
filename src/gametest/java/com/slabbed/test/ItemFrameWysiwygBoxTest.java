package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Item frames hang on the DRAWN face of their support (maintainer ruling, 2026-09-01): the
 * BOUNDING BOX shifts by the support's remembered height while the entity's REAL position stays at
 * grid height — moving the position corrupts the frame's derived grid cell ({@code
 * BlockPos.containing} in the attachment math and every server position packet) and {@code
 * survives()} then judges the wrong support. The flush control pins that an ordinary support keeps
 * the vanilla box exactly.
 *
 * <p>MUTATION that must redden the first row: delete the box shift in
 * {@code HangingEntityRememberedSeatMixin.slabbed$hangBoxOnRememberedSeat}. The flush control row
 * is the negative half and is deliberately inert under that mutation.
 *
 * <p>Headless boundary: these rows pin the entity-side box/position split only. The drawn frame's
 * shift is a render-layer mixin ({@code ItemFrameDrawnFaceRenderMixin}) that no server gametest can
 * observe — a double offset there is only visible to eyes on a real frame.
 */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class ItemFrameWysiwygBoxTest {

    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6d;

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /** Builds a support that reads −0.5 by really placing stone on a bottom slab's top face. */
    private static BlockPos loweredSupport(GameTestHelper ctx, BlockPos slabRel) {
        ServerLevel level = ctx.getLevel();
        BlockPos slab = ctx.absolutePos(slabRel);
        BlockPos support = slab.above();
        level.setBlock(slab, bottomSlab(), Block.UPDATE_ALL);
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(support.getX() + 0.5d, support.getY(), support.getZ() + 0.5d);
        ItemStack stack = new ItemStack(Blocks.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(slab), Direction.UP, slab, false)));
        ctx.assertTrue(result.consumesAction(), "premise: the support placement must be accepted");
        double dy = SlabSupport.getYOffset(level, support, level.getBlockState(support));
        ctx.assertTrue(Math.abs(dy + 0.5d) <= EPS, "premise: the placed support must read -0.5, got " + dy);
        // Clear the slab the placement was seated on. The stored height must survive its support
        // being taken away (Law 1) - which is also what lets a later rebuild of this cell read
        // flush, so a row can tell "remembered" from "re-read the wall" at all.
        level.setBlock(slab, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        double afterClear = SlabSupport.getYOffset(level, support, level.getBlockState(support));
        ctx.assertTrue(Math.abs(afterClear + 0.5d) <= EPS,
                "premise: the placed support must keep -0.5 after its slab is removed, got " + afterClear);
        return support;
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void frameBoxFollowsLoweredSupportPositionStaysGrid(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        BlockPos supportAbs = loweredSupport(ctx, new BlockPos(2, 2, 2));

        // Flush control support, same shape, no placement fact.
        BlockPos controlAbs = ctx.absolutePos(new BlockPos(2, 3, 5));
        level.setBlock(controlAbs, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);

        ItemFrame lowered = new ItemFrame(level, supportAbs.relative(Direction.EAST), Direction.EAST);
        ItemFrame control = new ItemFrame(level, controlAbs.relative(Direction.EAST), Direction.EAST);
        level.addFreshEntity(lowered);
        level.addFreshEntity(control);

        AABB loweredBox = lowered.getBoundingBox();
        AABB controlBox = control.getBoundingBox();

        // The box carries the support's drawn height...
        ctx.assertTrue(Math.abs((loweredBox.minY - controlBox.minY) + 0.5d) <= EPS
                        && Math.abs((loweredBox.maxY - controlBox.maxY) + 0.5d) <= EPS,
                "frame box beside a -0.5 support must sit exactly 0.5 below the flush control's box; got minY delta "
                        + (loweredBox.minY - controlBox.minY) + " maxY delta " + (loweredBox.maxY - controlBox.maxY));
        // ...while the entity position does not differ (the no-position-shift invariant).
        ctx.assertTrue(Math.abs(lowered.getY() - control.getY()) <= EPS,
                "frame entity Y must stay at grid height; lowered frame Y " + lowered.getY()
                        + " vs control " + control.getY());
        // And both frames still judge their real supports.
        ctx.assertTrue(lowered.survives() && control.survives(),
                "both frames must survive on their supports (lowered=" + lowered.survives()
                        + " control=" + control.survives() + ")");
        // Cross-version tripwire: on some Minecraft versions the entity xyz is re-derived FROM the
        // box, so a box shift feeds back into the position on a later recalculation. Re-assert the
        // split after a few ticks, not only at spawn.
        ctx.runAfterDelay(5, () -> {
            ctx.assertTrue(Math.abs(lowered.getY() - control.getY()) <= EPS,
                    "frame entity Y drifted after ticks - a deferred recalculation derived the position "
                            + "from the shifted box; lowered Y " + lowered.getY() + " vs control " + control.getY());
            ctx.succeed();
        });
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void frameBoxOnFlushSupportKeepsVanillaBox(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        BlockPos supportAbs = ctx.absolutePos(new BlockPos(2, 3, 2));
        level.setBlock(supportAbs, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);

        ItemFrame frame = new ItemFrame(level, supportAbs.relative(Direction.EAST), Direction.EAST);
        level.addFreshEntity(frame);

        // NEGATIVE CONTROL: a flush support leaves the box exactly where the entity position
        // implies it - box center Y equals the entity Y (vanilla frame geometry is centred).
        AABB box = frame.getBoundingBox();
        double boxCenterY = (box.minY + box.maxY) / 2.0d;
        ctx.assertTrue(Math.abs(boxCenterY - frame.getY()) <= EPS,
                "flush-support frame box must stay centred on the entity position; center "
                        + boxCenterY + " vs entity Y " + frame.getY());
        ctx.succeed();
    }
}
