package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
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

@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgeOrdinaryFullBlockAnchorGameTest {
    private static final double EPSILON = 1.0e-6d;

    @GameTest(template = "empty")
    public void ordinaryStoneOnBottomSlabPlacesAnchorAndDy(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos supportPos = ctx.absolutePos(new BlockPos(1, 1, 1));
        BlockPos objectPos = supportPos.above();

        BlockState supportState = Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        world.setBlock(supportPos, supportState, Block.UPDATE_ALL);

        BlockState objectState = placeStoneItem(world, supportPos, objectPos);

        ctx.assertTrue(world.getBlockState(supportPos).is(Blocks.STONE_SLAB),
                "fixture support must be minecraft:stone_slab at " + shortPos(supportPos));
        ctx.assertTrue(world.getBlockState(supportPos).getValue(SlabBlock.TYPE) == SlabType.BOTTOM,
                "fixture support must be a bottom slab at " + shortPos(supportPos));
        ctx.assertTrue(objectState.is(Blocks.STONE),
                "fixture object must be minecraft:stone at " + shortPos(objectPos));
        ctx.assertTrue(SlabAnchorAttachment.qualifiesForDirectAnchor(world, objectPos, objectState),
                "fixture object must qualify for direct bottom-slab anchor");

        boolean anchored = SlabAnchorAttachment.isAnchored(world, objectPos);
        double dy = SlabSupport.getYOffset(world, objectPos, objectState);
        double supportDy = SlabSupport.getYOffset(world, supportPos, world.getBlockState(supportPos));
        double supportTopWorldY = supportPos.getY()
                + supportDy
                + SlabSupport.getSupportYOffset(world.getBlockState(supportPos));
        double objectBottomWorldY = objectPos.getY() + dy;
        double overlap = supportTopWorldY - objectBottomWorldY;

        System.out.println("[FORGE_ORDINARY_FULL_BLOCK_ANCHOR_ROW]"
                + " scenario=A_ordinary_bottom_slab_full_block"
                + " support=minecraft:stone_slab[type=bottom]"
                + " object=minecraft:stone"
                + " expectedLane=DIRECT_BOTTOM_SLAB_ANCHORED_FULL_BLOCK"
                + " anchored=" + anchored
                + " dy=" + text(dy)
                + " supportDy=" + text(supportDy)
                + " supportTopWorldY=" + text(supportTopWorldY)
                + " objectBottomWorldY=" + text(objectBottomWorldY)
                + " overlap=" + text(overlap)
                + " proofScope=forge_server_gametest_one_fixture_only");

        ctx.assertTrue(anchored,
                "ordinary stone placed on a bottom slab must create server anchor truth");
        ctx.assertTrue(near(dy, -0.5d),
                "ordinary stone placed on a bottom slab must have dy=-0.5, got " + text(dy));
        ctx.assertTrue(overlap <= EPSILON,
                "ordinary stone bottom must not overlap bottom-slab support top, overlap=" + text(overlap));

        ctx.succeed();
    }

    private static BlockState placeStoneItem(ServerLevel world, BlockPos supportPos, BlockPos objectPos) {
        Player player = FakePlayerFactory.getMinecraft(world);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.STONE));
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(supportPos).add(0.0d, 0.5d, 0.0d),
                Direction.UP,
                supportPos,
                false);
        InteractionResult result = ForgeHooks.onPlaceItemIntoWorld(
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        if (!result.consumesAction()) {
            throw new AssertionError("stone item placement did not consume action, result=" + result);
        }
        return world.getBlockState(objectPos);
    }

    private static boolean near(double actual, double expected) {
        return Double.isFinite(actual)
                && Double.isFinite(expected)
                && Math.abs(actual - expected) <= EPSILON;
    }

    private static String text(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
