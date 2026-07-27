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

    /**
     * RED-before/GREEN-after: clicking the OUTER half of a lowered/cantilevered TOP
     * slab's side face must extend a new slab into the adjacent cell, not merge the
     * existing cantilever into a DOUBLE at the same cell. Vanilla SlabBlock.canBeReplaced
     * decides combine-vs-extend from a RAW (dy-uncorrected) Y fraction against the
     * block's integer grid Y; for a lowered TOP slab (dy=-0.5) the entire visible band
     * sits below the raw 0.5 threshold, so every horizontal click satisfies vanilla's
     * combine condition regardless of which half is visually aimed at -- a cantilever
     * can never be extended sideways with a single click, only merged in place. This is
     * the "second cantilevered slab places underneath instead of perpendicular" bug.
     */
    @GameTest(template = "empty")
    public void cantileveredTopSlabExtendsSidewaysInsteadOfCombining(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos supportPos = ctx.absolutePos(new BlockPos(1, 1, 1));
        BlockPos stonePos = supportPos.above();
        BlockPos cantileverPos = stonePos.west();
        BlockPos extendPos = cantileverPos.west();

        BlockState bottomSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        world.setBlock(supportPos, bottomSlab, Block.UPDATE_ALL);

        BlockState stoneState = placeStoneItem(world, supportPos, stonePos);
        ctx.assertTrue(stoneState.is(Blocks.STONE), "fixture stone must be minecraft:stone at " + shortPos(stonePos));
        double stoneDy = SlabSupport.getYOffset(world, stonePos, stoneState);
        ctx.assertTrue(near(stoneDy, -0.5d), "fixture stone must be lowered dy=-0.5, got " + text(stoneDy));

        // First placement: cantilever an oak_slab off the stone's west face, aiming at the
        // visually UPPER portion of the stone's actual lowered span [1.5,2.5] so the already-
        // proven SlabBlockPlacementFixMixin selects TYPE=TOP.
        BlockState cantileverState = placeSlabAgainstFace(
                world, stonePos, cantileverPos, Direction.WEST, stonePos.getY() + 0.8d);
        ctx.assertTrue(cantileverState.getBlock() instanceof SlabBlock,
                "fixture cantilever must be a slab at " + shortPos(cantileverPos));
        ctx.assertTrue(cantileverState.getValue(SlabBlock.TYPE) == SlabType.TOP,
                "fixture cantilever must be TYPE=TOP, got " + cantileverState.getValue(SlabBlock.TYPE));
        double cantileverDy = SlabSupport.getYOffset(world, cantileverPos, cantileverState);
        ctx.assertTrue(near(cantileverDy, -0.5d),
                "fixture cantilever must be lowered dy=-0.5, got " + text(cantileverDy));

        // Second click: aim at the cantilever's OWN west face at cantileverPos.y+0.4 --
        // this sits within its actual visible span [+0.0,+0.5] (TOP shifted by dy=-0.5) and
        // is the exact RED/GREEN discriminator: raw fraction 0.4 (<=0.5) says COMBINE
        // (the bug); dy-corrected fraction 0.4-(-0.5)=0.9 (>0.5) says EXTEND (fixed).
        placeSlabAgainstFace(world, cantileverPos, extendPos, Direction.WEST, cantileverPos.getY() + 0.4d);

        BlockState cantileverAfter = world.getBlockState(cantileverPos);
        BlockState extendedState = world.getBlockState(extendPos);

        System.out.println("[FORGE_CANTILEVER_EXTEND_ROW]"
                + " cantileverPos=" + shortPos(cantileverPos)
                + " cantileverTypeAfter=" + (cantileverAfter.hasProperty(SlabBlock.TYPE) ? cantileverAfter.getValue(SlabBlock.TYPE) : "?")
                + " extendPos=" + shortPos(extendPos)
                + " extendedBlock=" + extendedState.getBlock()
                + " proofScope=forge_server_gametest_cantilever_extend_vs_combine");

        ctx.assertTrue(cantileverAfter.hasProperty(SlabBlock.TYPE) && cantileverAfter.getValue(SlabBlock.TYPE) == SlabType.TOP,
                "cantilevered slab must stay TYPE=TOP (not merge to DOUBLE) when the outer half is clicked, got "
                        + (cantileverAfter.hasProperty(SlabBlock.TYPE) ? cantileverAfter.getValue(SlabBlock.TYPE) : cantileverAfter));
        ctx.assertTrue(extendedState.getBlock() instanceof SlabBlock,
                "clicking the outer half of a cantilevered slab must extend into the adjacent cell, got "
                        + extendedState + " at " + shortPos(extendPos));

        ctx.succeed();
    }

    private static BlockState placeSlabAgainstFace(
            ServerLevel world, BlockPos targetPos, BlockPos newPos, Direction face, double clickY) {
        Player player = FakePlayerFactory.getMinecraft(world);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.OAK_SLAB));
        Vec3 hitLoc = new Vec3(
                targetPos.getX() + 0.5d + face.getStepX() * 0.5d,
                clickY,
                targetPos.getZ() + 0.5d + face.getStepZ() * 0.5d);
        BlockHitResult hit = new BlockHitResult(hitLoc, face, targetPos, false);
        InteractionResult result = ForgeHooks.onPlaceItemIntoWorld(
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        if (!result.consumesAction()) {
            throw new AssertionError("slab placement did not consume action, result=" + result
                    + " target=" + targetPos + " face=" + face);
        }
        return world.getBlockState(newPos);
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
