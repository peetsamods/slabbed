package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.Slabbed;
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

@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class NeighborUpdateInvarianceTest {
    private static final String TEMPLATE = "empty";
    private static final int SUPPORT_Y = 2;

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void anchoredPlacementSurvivesDirectSupportBreak(GameTestHelper ctx) {
        BlockPos support = ctx.absolutePos(new BlockPos(2, SUPPORT_Y, 2));
        BlockPos subject = support.above();
        ServerLevel world = ctx.getLevel();

        placeBottomSlab(world, support);
        placeStoneWithHeldItem(ctx, subject, support);
        double before = renderedBottomY(world, subject);
        double expectedBottom = subject.getY() - 0.5D;
        ctx.assertTrue(Double.compare(before, expectedBottom) == 0,
                "server-thread outline must retain the placed half-block offset before support removal");
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, subject), "placed block must receive its placement anchor");

        world.setBlock(support, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        double after = renderedBottomY(world, subject);
        boolean preserved = Double.compare(before, after) == 0;
        Slabbed.LOGGER.info("[LAW-GATE] row=anchored-support-break before={} after={} preserved={}", before, after, preserved);

        if (lawGateEnabled()) {
            ctx.assertTrue(Double.compare(after, expectedBottom) == 0,
                    "server-thread outline must retain the placed half-block offset after support removal");
            ctx.assertTrue(preserved, "breaking direct support moved a player-placed block");
        }
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void directSupportBreakReachesUnprotectedResolver(GameTestHelper ctx) {
        BlockPos support = ctx.absolutePos(new BlockPos(2, SUPPORT_Y, 2));
        BlockPos subject = support.above();
        ServerLevel world = ctx.getLevel();

        placeBottomSlab(world, support);
        placeStoneWithHeldItem(ctx, subject, support);
        double before = renderedBottomY(world, subject);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, subject), "counterfactual requires a placement anchor before removal");

        SlabAnchorAttachment.removeAnchor(world, subject);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, subject), "counterfactual must remove the placement anchor");
        ctx.assertTrue(SlabPlacementHeightAttachment.remove(
                        world.getChunk(subject.getX() >> 4, subject.getZ() >> 4), subject),
                "counterfactual must remove the numeric placement-height fact");
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(
                        world.getChunk(subject.getX() >> 4, subject.getZ() >> 4), subject).isEmpty(),
                "counterfactual must leave neither placement authority behind");
        world.setBlock(support, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        double after = renderedBottomY(world, subject);
        boolean resolverMoved = Double.compare(before, after) != 0;
        Slabbed.LOGGER.info("[LAW-GATE] row=unprotected-support-break before={} after={} moved={}", before, after, resolverMoved);

        ctx.assertTrue(resolverMoved, "counterfactual did not reach the unprotected neighbor-update resolver");
        ctx.succeed();
    }

    private static void placeBottomSlab(ServerLevel world, BlockPos pos) {
        world.setBlock(pos, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
    }

    private static void placeStoneWithHeldItem(GameTestHelper ctx, BlockPos subject, BlockPos hitPos) {
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(subject.getX() + 0.5D, subject.getY(), subject.getZ() + 0.5D);
        ItemStack stack = new ItemStack(Blocks.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(hitPos),
                Direction.UP,
                hitPos,
                false
        );
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        ctx.assertTrue(result.consumesAction(), "held-item placement must be accepted");
        ctx.assertTrue(ctx.getLevel().getBlockState(subject).is(Blocks.STONE), "held-item placement must create the subject block");
    }

    private static double renderedBottomY(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getShape(world, pos).bounds().minY + pos.getY();
    }

    private static boolean lawGateEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("slabbed.lawGate", "true"));
    }
}
