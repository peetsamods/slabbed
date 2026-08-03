package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Two-row truth gate used before another full Phase 6E Mega drive. */
@GameTestHolder("slabbed_truth")
@PrefixGameTestTemplate(false)
public final class ForgeRecorderTruthLaneGameTest {
    @GameTest(template = "empty", batch = "slabbed_truth_lanes")
    public void realUseOnProvesOnlyFlushAndLegalMinusHalf(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(world);
        ItemStack original = player.getMainHandItem().copy();
        BlockPos flushSupport = ctx.absolutePos(new BlockPos(1, 1, 1));
        BlockPos flushSubject = flushSupport.above();
        BlockPos loweredSupport = ctx.absolutePos(new BlockPos(4, 1, 1));
        BlockPos loweredSubject = loweredSupport.above();

        try {
            world.setBlock(flushSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(
                    loweredSupport,
                    Blocks.STONE_SLAB.defaultBlockState()
                            .setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.UPDATE_ALL);

            placeStone(world, player, flushSupport, flushSupport.getY() + 1.0d);
            placeStone(world, player, loweredSupport, loweredSupport.getY() + 0.5d);

            assertExactLane(ctx, world, flushSubject, 0.0d, false, "dy=0 control");
            assertExactLane(ctx, world, loweredSubject, -0.5d, true, "legal dy=-0.5");

            System.out.println("[SLABBED_RECORDER_TRUTH_LANES]"
                    + " controlDy=" + SlabSupport.getYOffset(
                            world, flushSubject, world.getBlockState(flushSubject))
                    + " loweredDy=" + SlabSupport.getYOffset(
                            world, loweredSubject, world.getBlockState(loweredSubject))
                    + " proof=real_forge_itemstack_use_on_two_rows_only");
            ctx.succeed();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
            SlabAnchorAttachment.clearPlacementTruth(world, flushSubject);
            SlabAnchorAttachment.clearPlacementTruth(world, loweredSubject);
        }
    }

    private static void placeStone(
            ServerLevel world,
            ServerPlayer player,
            BlockPos support,
            double hitY) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.STONE));
        BlockHitResult hit = new BlockHitResult(
                new Vec3(support.getX() + 0.5d, hitY, support.getZ() + 0.5d),
                Direction.UP,
                support,
                false);
        InteractionResult result = ForgeHooks.onPlaceItemIntoWorld(
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        if (!result.consumesAction()) {
            throw new AssertionError("real useOn refused truth-lane placement at "
                    + support.toShortString() + ": " + result);
        }
    }

    private static void assertExactLane(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos subject,
            double expectedDy,
            boolean expectedAnchored,
            String label) {
        BlockState state = world.getBlockState(subject);
        SlabAnchorAttachment.PlacementDyFact stored =
                SlabAnchorAttachment.storedPlacementDyFact(world, subject);
        double liveDy = SlabSupport.getYOffset(world, subject, state);
        long expectedBits = Double.doubleToRawLongBits(expectedDy);
        ctx.assertTrue(state.is(Blocks.STONE), label + " must place minecraft:stone");
        ctx.assertTrue(stored.present() && stored.rawBits() == expectedBits,
                label + " must publish exact stored bits, got " + stored);
        ctx.assertTrue(Double.doubleToRawLongBits(liveDy) == expectedBits,
                label + " must read exact live dy, got " + liveDy);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, subject) == expectedAnchored,
                label + " anchor truth disagrees");
    }
}
