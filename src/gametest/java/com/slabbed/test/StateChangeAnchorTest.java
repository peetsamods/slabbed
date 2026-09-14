package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A block whose KIND changes at the same position, with the same shape, keeps the height it was
 * placed at (LAW.md Law 1, maintainer ruling 2026-09-13).
 *
 * <p>The report: a compat grass slab turns into its dirt slab once something covers it, the way
 * vanilla grass turns to dirt. On a lowered stack the converted slab jumped back to grid height,
 * because the replacement seam read the change of block kind as the slab leaving its cell and
 * dropped its recorded seat. A change of SHAPE — a slab becoming a full block — is a different
 * occupant and must still clear.
 */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class StateChangeAnchorTest {

    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6d;
    /** The depth of the stack the subject slab is placed against, and so of its recorded seat. */
    private static final double SEAT = -1.0d;

    /**
     * Builds the reported scene and returns the subject cell: a stone slab placed against the side
     * face of a support that sits one block down, through the real held-item use path, so the seat
     * it carries is the one its own placement recorded.
     */
    private static BlockPos placeALoweredSlab(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        BlockState bottomSlab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        level.setBlock(ctx.absolutePos(new BlockPos(2, 1, 2)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(ctx.absolutePos(new BlockPos(2, 2, 2)), bottomSlab, Block.UPDATE_ALL);
        level.setBlock(ctx.absolutePos(new BlockPos(2, 3, 2)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(ctx.absolutePos(new BlockPos(2, 4, 2)), bottomSlab, Block.UPDATE_ALL);
        BlockPos support = ctx.absolutePos(new BlockPos(2, 5, 2));
        level.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        double supportDy = SlabSupport.getYOffset(level, support, level.getBlockState(support));
        ctx.assertTrue(Math.abs(supportDy - SEAT) <= EPS,
                "premise: the support must present its face one block down: dy=" + supportDy);

        BlockPos subject = support.east();
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(support.getX() + 0.5d, support.getY() + 2.0d, support.getZ() + 0.5d);
        player.setYRot(0.0f);
        ItemStack stack = new ItemStack(Blocks.STONE_SLAB);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        // Aim at the lowered part of the support's east face, which is where the subject's own
        // depth comes from.
        Vec3 hit = new Vec3(support.getX() + 1.0d, support.getY() - 0.75d, support.getZ() + 0.5d);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.EAST, support, false)));
        BlockState placed = level.getBlockState(subject);
        double seat = SlabSupport.getYOffset(level, subject, placed);
        ctx.assertTrue(result.consumesAction()
                        && placed.is(Blocks.STONE_SLAB)
                        && Double.isFinite(SlabPlacementHeightAttachment.storedOffset(level, subject))
                        && Math.abs(seat - SEAT) <= EPS,
                "premise: the slab must be placed through the real use path and record its seat: result="
                        + result + " state=" + placed + " seat=" + seat
                        + " stored=" + SlabPlacementHeightAttachment.storedOffset(level, subject));
        return subject;
    }

    /**
     * The reported case: stone slab to oak slab is a change of KIND at the same position with the
     * same shape, so the recorded seat and the height read from it must both survive it.
     *
     * <p>MUTATION that must redden this row alone: drop the {@code isSameShapeTransform} clause
     * from {@code SlabAnchorAttachment.replacementPreservesPlacementTruth}.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void aSlabThatChangesKindInPlaceWithTheSameShapeKeepsItsSeat(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        BlockPos subject = placeALoweredSlab(ctx);
        BlockState before = level.getBlockState(subject);
        double storedBefore = SlabPlacementHeightAttachment.storedOffset(level, subject);
        double readBefore = SlabSupport.getYOffset(level, subject, before);

        // Same half, so the two states have the same shape and only the block kind differs.
        BlockState converted = Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, before.getValue(SlabBlock.TYPE));
        level.setBlock(subject, converted, Block.UPDATE_ALL);

        BlockState after = level.getBlockState(subject);
        double storedAfter = SlabPlacementHeightAttachment.storedOffset(level, subject);
        double readAfter = SlabSupport.getYOffset(level, subject, after);
        String report = "before state=" + before + " stored=" + storedBefore + " read=" + readBefore
                + " | after state=" + after + " stored=" + storedAfter + " read=" + readAfter;
        ctx.assertTrue(after.is(Blocks.OAK_SLAB), "premise: the cell must hold the converted slab: " + report);
        ctx.assertTrue(Double.isFinite(storedAfter)
                        && Math.abs(storedAfter - storedBefore) <= EPS
                        && Math.abs(readAfter - readBefore) <= EPS,
                "a slab that changes kind in place with the same shape must keep the height it was placed at: "
                        + report);
        ctx.succeed();
    }

    /**
     * The other half of the ruling, pinned so the rule above cannot widen into it: a replacement
     * whose shape differs is a different occupant and still clears the recorded seat. Stone is a
     * full cube, and a full cube is not a slab.
     *
     * <p>MUTATION that must redden this row alone: make
     * {@code SlabAnchorAttachment.isSameShapeTransform} return true without comparing the two
     * shapes.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void aReplacementWithADifferentShapeStillClearsTheSeat(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        BlockPos subject = placeALoweredSlab(ctx);
        level.setBlock(subject, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockState after = level.getBlockState(subject);
        double storedAfter = SlabPlacementHeightAttachment.storedOffset(level, subject);
        ctx.assertTrue(after.is(Blocks.STONE) && !Double.isFinite(storedAfter),
                "a replacement with a different shape must not keep the slab's seat: state=" + after
                        + " stored=" + storedAfter);
        ctx.succeed();
    }
}
