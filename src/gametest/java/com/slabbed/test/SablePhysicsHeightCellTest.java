package com.slabbed.test;

import com.slabbed.compat.sable.SablePhysicsHeight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
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
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The per-cell collision handed to Sable's physics (see {@link SablePhysicsHeight}).
 *
 * <p>Sable itself is not loaded here; these rows pin the Slabbed side of the bridge. A lowered
 * block's own cell collides only up to its drawn top, the slab under it also carries the lowered
 * block's hanging half, and flush cells are left to Sable's own collider. The differential row
 * proves the upload pass's shared screen never disagrees with the mesh screen's answer.
 *
 * <p>REACH: delete the own-height move in {@code SlabSupport.collisionShapeForBroadphaseCell} and
 * the lowered cell's top reads 1.0; make {@code SablePhysicsHeight.Memo.mayBeMoved} answer false
 * for markers and the differential row reports the lowered cells as unchanged.
 */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class SablePhysicsHeightCellTest {

    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6d;

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void loweredBlockCollidesWhereItIsDrawn(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        floor(ctx);
        BlockPos slab = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos lowered = slab.above();
        BlockPos flush = ctx.absolutePos(new BlockPos(5, 2, 2));
        use(ctx, player, Blocks.STONE_SLAB, slab.below(), slab.getY());
        use(ctx, player, Blocks.STONE, slab, slab.getY() + 0.5d);
        use(ctx, player, Blocks.STONE, flush.below(), flush.getY());

        SablePhysicsHeight.CellCollision loweredCell = SablePhysicsHeight.alteredCell(level, lowered);
        if (loweredCell == null || Math.abs(loweredCell.shape().max(Direction.Axis.Y) - 0.5d) > EPS) {
            throw new GameTestAssertException("lowered stone's own cell must collide up to 0.5, got "
                    + (loweredCell == null ? "Sable's own collider" : loweredCell.shape().max(Direction.Axis.Y)));
        }
        SablePhysicsHeight.CellCollision slabCell = SablePhysicsHeight.alteredCell(level, slab);
        if (slabCell == null
                || Math.abs(slabCell.shape().min(Direction.Axis.Y)) > EPS
                || slabCell.shape().max(Direction.Axis.Y) < 1.0d - EPS) {
            // Unclamped here; Sable clamps every collider to its cell when it bakes.
            throw new GameTestAssertException("the slab's cell must also hold the lowered stone's hanging half, got "
                    + (slabCell == null ? "Sable's own collider" : slabCell.shape().bounds()));
        }
        if (SablePhysicsHeight.alteredCell(level, flush) != null
                || SablePhysicsHeight.alteredCell(level, flush.below()) != null) {
            throw new GameTestAssertException("a flush block keeps Sable's own collider");
        }
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void uploadScreenAgreesWithMeshScreenAcrossMixedScene(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        floor(ctx);
        // Placed: a lowered stone, a stone on it, and a slab-stone-slab-stone compound column.
        BlockPos a = ctx.absolutePos(new BlockPos(1, 2, 1));
        use(ctx, player, Blocks.STONE_SLAB, a.below(), a.getY());
        use(ctx, player, Blocks.STONE, a, a.getY() + 0.5d);
        BlockPos b = ctx.absolutePos(new BlockPos(3, 2, 1));
        use(ctx, player, Blocks.STONE_SLAB, b.below(), b.getY());
        use(ctx, player, Blocks.STONE, b, b.getY() + 0.5d);
        use(ctx, player, Blocks.STONE_SLAB, b.above(), b.getY() + 1.5d);
        use(ctx, player, Blocks.STONE, b.above(2), b.getY() + 2.0d);
        // Terrain (no placement record): a stair and a fence on slabs, a chain, a floating block.
        ctx.setBlock(new BlockPos(5, 2, 1), bottomSlab());
        ctx.setBlock(new BlockPos(5, 3, 1), Blocks.OAK_STAIRS.defaultBlockState());
        ctx.setBlock(new BlockPos(1, 2, 4), bottomSlab());
        ctx.setBlock(new BlockPos(1, 3, 4), Blocks.OAK_FENCE.defaultBlockState());
        ctx.setBlock(new BlockPos(3, 2, 4), Blocks.CHAIN.defaultBlockState());
        ctx.setBlock(new BlockPos(3, 3, 4), Blocks.STONE.defaultBlockState());
        ctx.setBlock(new BlockPos(5, 6, 4), Blocks.STONE.defaultBlockState());
        ctx.setBlock(new BlockPos(6, 2, 4), Blocks.SNOW.defaultBlockState());

        SablePhysicsHeight.Memo memo = new SablePhysicsHeight.Memo(level);
        int altered = 0;
        for (int x = 0; x <= 6; x++) {
            for (int y = 1; y <= 7; y++) {
                for (int z = 0; z <= 5; z++) {
                    BlockPos pos = ctx.absolutePos(new BlockPos(x, y, z));
                    SablePhysicsHeight.CellCollision direct = SablePhysicsHeight.alteredCell(level, pos);
                    SablePhysicsHeight.CellCollision shared = memo.cell(pos);
                    if ((direct == null) != (shared == null)
                            || (direct != null && !sameShape(direct.shape(), shared.shape()))) {
                        throw new GameTestAssertException("the upload pass and the mesh screen disagree: direct="
                                + describe(direct) + " shared=" + describe(shared));
                    }
                    if (direct != null) {
                        altered++;
                    }
                }
            }
        }
        if (altered < 4) {
            throw new GameTestAssertException("the scene must move at least the placed lowered cells; altered=" + altered);
        }
        ctx.succeed();
    }

    /**
     * A cantilever lane reaches its lowered source by walking sideways through connected blocks
     * over air, across chunk borders. Here the lane crosses a chunk corner and its source, a stone
     * on a slab, sits in the chunk diagonal to the lane's first cell; the whole-section skip must
     * still see the section as possibly moved. REACH: shrink the skip's neighborhood to the four
     * edge-adjacent chunks and this row fails whenever nothing else lowers nearby.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void cantileverSourceInDiagonalChunkKeepsSectionEligible(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        BlockPos origin = ctx.absolutePos(BlockPos.ZERO);
        // Far from every other test, so nothing else nearby can make the section eligible.
        int cornerX = ((origin.getX() >> 4) + 4096) << 4;
        int cornerZ = ((origin.getZ() >> 4) + 1) << 4;
        int y = origin.getY() + 24;
        for (int cx = (cornerX >> 4) - 4; cx <= (cornerX >> 4) + 1; cx++) {
            for (int cz = (cornerZ >> 4) - 2; cz <= (cornerZ >> 4) + 1; cz++) {
                level.getChunk(cx, cz);
            }
        }
        BlockPos laneA = new BlockPos(cornerX - 1, y, cornerZ - 1);
        BlockPos laneB = new BlockPos(cornerX, y, cornerZ - 1);
        BlockPos source = new BlockPos(cornerX, y, cornerZ);
        BlockPos[] cells = {laneA, laneB, source, source.below(), source.below(2)};
        try {
            level.setBlock(source.below(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(source.below(), bottomSlab(), Block.UPDATE_ALL);
            level.setBlock(source, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(laneB, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(laneA, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            ctx.assertTrue(!SablePhysicsHeight.sectionMayBeAltered(level, (cornerX >> 4) - 3, laneA.getY() >> 4,
                            laneA.getZ() >> 4),
                    "control: a section with nothing lowered anywhere near it is skipped");
            double laneDy = com.slabbed.util.SlabSupport.getYOffset(level, laneA, level.getBlockState(laneA));
            ctx.assertTrue(Math.abs(laneDy + 0.5d) <= EPS,
                    "premise: the lane block across the corner lowers with its source, dy=" + laneDy);
            ctx.assertTrue(SablePhysicsHeight.sectionMayBeAltered(level, laneA.getX() >> 4, laneA.getY() >> 4,
                            laneA.getZ() >> 4),
                    "a section holding a cantilever lane must stay eligible when its source is diagonal");
            ctx.assertTrue(SablePhysicsHeight.alteredCell(level, laneA) != null,
                    "the lowered lane block's own cell must be handed to Sable");
        } finally {
            for (BlockPos cell : cells) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        ctx.succeed();
    }

    private static void floor(GameTestHelper ctx) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 5; z++) {
                ctx.setBlock(new BlockPos(x, 1, z), Blocks.STONE.defaultBlockState());
            }
        }
    }

    private static net.minecraft.world.level.block.state.BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static boolean sameShape(VoxelShape left, VoxelShape right) {
        return !Shapes.joinIsNotEmpty(left, right, BooleanOp.NOT_SAME);
    }

    private static String describe(SablePhysicsHeight.CellCollision cell) {
        return cell == null ? "unchanged" : cell.shape().bounds().toString();
    }

    private static void use(GameTestHelper ctx, Player player, Block held, BlockPos clicked, double hitY) {
        ItemStack stack = new ItemStack(held.asItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(clicked.getX() + 0.5d, hitY, clicked.getZ() + 0.5d),
                        Direction.UP, clicked, false)));
        if (result == null || !result.consumesAction()) {
            throw new GameTestAssertException("real item use must succeed for " + held + "; result=" + result);
        }
    }
}
