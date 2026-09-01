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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Gesture-contract pins for the donor's up-face edge-click combine family. The donor line
 * shipped an edge-band inference that redirected an ambiguous top-face click on a lowered slab
 * into side placement, and needed two guards to stop that inference from silently combining a
 * slab the player never aimed at. This line never ported the inference — but the PLAYER GESTURES
 * exist regardless of mechanism, so these rows pin the player-visible contract itself: an
 * ambiguous top-face click near a lowered slab's edge must never silently turn any single slab
 * into a DOUBLE, and a literal, deliberate horizontal combine click keeps vanilla behavior. If a
 * future port of the edge inference (or a remap defect) reintroduces the silent combine, these
 * rows go red.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class UpFaceEdgeCombineGuardTest {
    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-9;

    /** Edge click on a lowered slab whose inferred-direction neighbor already holds a same-material slab. */
    @GameTest(template = TEMPLATE)
    public void upFaceEdgeClickNearOccupiedNeighborDoesNotCombine(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos clicked = buildLoweredTopSlab(ctx, new BlockPos(3, 2, 1));
        BlockPos neighbor = clicked.west();
        world.setBlock(neighbor,
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_ALL);

        double clickedBefore = dy(world, clicked);
        useHeldOakSlab(ctx, clicked, Direction.UP,
                new Vec3(clicked.getX() + 0.05, clicked.getY() + 0.5, clicked.getZ() + 0.5));

        ctx.assertTrue(!isDouble(world.getBlockState(neighbor)),
                "an ambiguous top-face edge click must NOT silently combine an unrelated pre-existing"
                        + " neighbor slab into a DOUBLE; neighbor=" + world.getBlockState(neighbor));
        ctx.assertTrue(!isDouble(world.getBlockState(clicked)),
                "the clicked lowered slab must not silently combine either; clicked=" + world.getBlockState(clicked));
        ctx.assertTrue(Math.abs(dy(world, clicked) - clickedBefore) <= EPS,
                "the clicked slab's height must not move across the gesture");
        ctx.succeed();
    }

    /** Same edge click with an EMPTY inferred neighbor: the clicked slab itself must not combine. */
    @GameTest(template = TEMPLATE)
    public void upFaceEdgeClickWithEmptyNeighborDoesNotCombineClickedSlab(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos clicked = buildLoweredTopSlab(ctx, new BlockPos(3, 2, 4));
        ctx.assertTrue(world.getBlockState(clicked.west()).isAir(),
                "fixture: the west neighbor must start EMPTY (isolates this from the occupied case)");

        useHeldOakSlab(ctx, clicked, Direction.UP,
                new Vec3(clicked.getX() + 0.05, clicked.getY() + 0.5, clicked.getZ() + 0.5));

        ctx.assertTrue(!isDouble(world.getBlockState(clicked)),
                "an ambiguous top-face edge click must NOT combine the CLICKED slab in place even"
                        + " when the inferred neighbor is empty; clicked=" + world.getBlockState(clicked));
        ctx.succeed();
    }

    /** A literal, deliberate horizontal combine click keeps vanilla's normal behavior. */
    @GameTest(template = TEMPLATE)
    public void literalHorizontalClickStillCombinesNormally(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos ground = ctx.absolutePos(new BlockPos(5, 2, 1));
        BlockPos slab = ground.above();
        world.setBlock(ground, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(slab,
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);

        InteractionResult result = useHeldOakSlab(ctx, slab, Direction.NORTH,
                new Vec3(slab.getX() + 0.5, slab.getY() + 0.75, slab.getZ()));
        ctx.assertTrue(result.consumesAction(), "literal horizontal-face click must place; got " + result);
        ctx.assertTrue(isDouble(world.getBlockState(slab)),
                "a literal horizontal click against a BOTTOM slab at an upper-half fraction must"
                        + " still combine to DOUBLE (vanilla preserved); got " + world.getBlockState(slab));
        ctx.succeed();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** A TOP oak slab authored lowered (-0.5 stored fact), the donor scene's clicked subject. */
    private static BlockPos buildLoweredTopSlab(GameTestHelper ctx, BlockPos relative) {
        ServerLevel world = ctx.getLevel();
        BlockPos base = ctx.absolutePos(relative);
        world.setBlock(base,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
        BlockPos fullBlock = base.above();
        world.setBlock(fullBlock, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos slab = fullBlock.above();
        world.setBlock(slab,
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_ALL);
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(world.getChunkAt(slab), slab, -1),
                "fixture: the clicked slab must accept a -0.5 stored fact");
        double visual = dy(world, slab);
        ctx.assertTrue(Math.abs(visual + 0.5) <= EPS,
                "fixture: the clicked TOP slab must render lowered -0.5; got " + visual);
        return slab;
    }

    private static InteractionResult useHeldOakSlab(GameTestHelper ctx, BlockPos clicked,
                                                    Direction face, Vec3 hit) {
        Player player = ctx.makeMockPlayer();
        ItemStack stack = new ItemStack(Items.OAK_SLAB);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    private static boolean isDouble(BlockState state) {
        return state.hasProperty(SlabBlock.TYPE) && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    private static double dy(ServerLevel world, BlockPos pos) {
        return SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
    }
}
