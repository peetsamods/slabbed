package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 26.3 content (T2): a cushion is an entity placed by raycast that "sits on top of the supporting
 * surface". On a lowered block the aimed surface is the DRAWN top, which is below the block's cell, and
 * vanilla's anchor probe scans only the cells directly under the cushion — the lowered block's cell is
 * cell asks "does this cell's block suffocate me?" and a full cube says yes from its shape cache even
 * though its drawn body is below. WYSIWYG says the cushion sits on the drawn top and stays.
 *
 * <p>MUTATION that must redden both lowered rows: withhold {@code CushionRestsOnDrawnTopMixin}.
 */
public final class CushionOnLoweredBlockTest {

    private static final double EPS = 1.0e-6;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cushionSitsOnTheDrawnTopOfALoweredBlockAndSurvives(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slabRel = new BlockPos(2, 2, 2);
        BlockPos slab = helper.absolutePos(slabRel);
        BlockPos block = slab.above();
        FrozenDySceneFixture.authored(helper, () -> {
            helper.setBlock(slabRel.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(slabRel,
                    Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            helper.setBlock(slabRel.above(), Blocks.STONE.defaultBlockState());
        });
        double dy = SlabSupport.getYOffset(level, block, level.getBlockState(block));
        if (Math.abs(dy + 0.5) > EPS) {
            throw helper.assertionException("precondition: stone on a bottom slab reads lowered, got dy=" + dy);
        }
        double drawnTop = block.getY() + 1.0 + dy;
        Cushion cushion = placeCushion(helper, block, drawnTop);
        if (cushion == null) {
            throw helper.assertionException(
                    "WYSIWYG: a cushion aimed at the drawn top of a lowered block must be placed there");
        }
        if (Math.abs(cushion.getY() - drawnTop) > EPS) {
            throw helper.assertionException("cushion must sit on the drawn top " + drawnTop
                    + ", sits at " + cushion.getY());
        }
        if (!cushion.survives()) {
            throw helper.assertionException("the placed cushion must pass its own support check");
        }
        helper.succeed();
    }


    /**
     * A block lowered a whole cell (stone on a lowered slab on a lowered stone on a slab) draws its
     * top exactly at its cell's bottom boundary — the other probe geometry (the -0.5 row's drawn top is
     * mid-cell). Both must hold; vanilla's outline-based anchor scan reaches the block in both cases.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cushionSitsOnTheDrawnTopOfABlockLoweredAWholeCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos baseRel = new BlockPos(2, 2, 2);
        BlockPos top = helper.absolutePos(baseRel.above(4));
        FrozenDySceneFixture.authored(helper, () -> {
            helper.setBlock(baseRel, Blocks.STONE.defaultBlockState());
            helper.setBlock(baseRel.above(1),
                    Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            helper.setBlock(baseRel.above(2), Blocks.STONE.defaultBlockState());
            helper.setBlock(baseRel.above(3),
                    Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            helper.setBlock(baseRel.above(4), Blocks.STONE.defaultBlockState());
        });
        double dy = SlabSupport.getYOffset(level, top, level.getBlockState(top));
        if (Math.abs(dy + 1.0) > EPS) {
            throw helper.assertionException("precondition: the top stone of an SBSB stack reads -1.0, got dy=" + dy);
        }
        double drawnTop = top.getY() + 1.0 + dy;
        Cushion cushion = placeCushion(helper, top, drawnTop);
        if (cushion == null || Math.abs(cushion.getY() - drawnTop) > EPS || !cushion.survives()) {
            throw helper.assertionException("WYSIWYG at -1.0: cushion must sit on the drawn top " + drawnTop
                    + " and survive; got " + (cushion == null ? "none" : cushion.getY()));
        }
        helper.succeed();
    }

    /** Control: the same click on a flush block places at the grid top — the input path itself works. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cushionOnAFlushBlockStillSitsOnItsTop(GameTestHelper helper) {
        BlockPos blockRel = new BlockPos(2, 2, 2);
        BlockPos block = helper.absolutePos(blockRel);
        helper.setBlock(blockRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(blockRel, Blocks.STONE.defaultBlockState());
        double top = block.getY() + 1.0;
        Cushion cushion = placeCushion(helper, block, top);
        if (cushion == null || Math.abs(cushion.getY() - top) > EPS || !cushion.survives()) {
            throw helper.assertionException("control: a cushion on a flush block sits on its top; got "
                    + (cushion == null ? "none" : cushion.getY()));
        }
        helper.succeed();
    }


    private static Cushion placeCushion(GameTestHelper helper, BlockPos clicked, double aimedTopY) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(Items.CUSHION.pick(net.minecraft.world.item.DyeColor.WHITE));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = new Vec3(clicked.getX() + 0.5, aimedTopY, clicked.getZ() + 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, clicked, false)));
        AABB around = new AABB(clicked).inflate(0.0, 2.0, 0.0);
        List<Cushion> cushions = level.getEntitiesOfClass(Cushion.class, around);
        return cushions.isEmpty() ? null : cushions.get(0);
    }
}
