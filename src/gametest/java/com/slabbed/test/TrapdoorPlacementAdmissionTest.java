package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabEnsembleCoherence;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Admission uses visible bodies while preserving genuine open/close collision hazards. */
public final class TrapdoorPlacementAdmissionTest {
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void trapdoorsAboveFencesIgnoreOnlyTheInvisibleBarrier(GameTestHelper helper) {
        abovePost(helper, Blocks.OAK_FENCE.defaultBlockState());
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void trapdoorsAboveWallsIgnoreOnlyTheInvisibleBarrier(GameTestHelper helper) {
        abovePost(helper, Blocks.COBBLESTONE_WALL.defaultBlockState());
    }

    private static void abovePost(GameTestHelper helper, BlockState support) {
        require(helper, support.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
                        CollisionContext.empty()).max(Direction.Axis.Y) > 1.0d,
                "premise: the support has an above-cell collision barrier");
        require(helper, support.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                        .max(Direction.Axis.Y) <= 1.0d,
                "premise: the support's visible body stays in its own cell");
        boolean prior = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            int row = 0;
            for (double dy : new double[]{0.0d, -0.5d}) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    BlockPos owner = helper.absolutePos(new BlockPos(3 + (row % 4) * 3, 5, 3 + (row / 4) * 4));
                    row++;
                    place(helper, support, owner, Direction.UP, facing, Half.BOTTOM, dy, dy);
                }
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = prior;
        }
        helper.succeed();
    }

    /** Underside controls are independent of the above-post barrier case. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void undersideTrapdoorsKeepTheFullBlockAndSlabControls(GameTestHelper helper) {
        boolean prior = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            BlockState[] supports = {
                    Blocks.STONE.defaultBlockState(),
                    Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                    Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)};
            int row = 0;
            for (BlockState support : supports) {
                for (double dy : new double[]{0.0d, -0.5d}) {
                    for (Direction facing : Direction.Plane.HORIZONTAL) {
                        BlockPos owner = helper.absolutePos(new BlockPos(3 + (row % 4) * 3, 8, 3));
                        row++;
                        double bottom = support.hasProperty(SlabBlock.TYPE)
                                && support.getValue(SlabBlock.TYPE) == SlabType.TOP ? 0.5d : 0.0d;
                        place(helper, support, owner, Direction.DOWN, facing, Half.TOP, dy, dy + bottom);
                    }
                }
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = prior;
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullStoneUndersideWithAdjacentFixturesAcceptsAnOrdinaryTrapdoor(GameTestHelper helper) {
        fullStoneFixture(helper, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullStoneUndersidePreservesAnExistingTrapdoorInTheTargetCell(GameTestHelper helper) {
        fullStoneFixture(helper, true);
    }

    private static void fullStoneFixture(GameTestHelper helper, boolean occupied) {
        boolean prior = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            ServerLevel level = helper.getLevel();
            BlockPos owner = helper.absolutePos(new BlockPos(3, 5, 3));
            BlockPos target = owner.below();
            BlockState existing = Blocks.OAK_TRAPDOOR.defaultBlockState()
                    .setValue(BlockStateProperties.HALF, Half.BOTTOM);
            for (BlockPos stone : new BlockPos[]{owner, owner.east()}) {
                level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(stone.above(), existing.setValue(BlockStateProperties.OPEN, true), 3);
                level.setBlock(stone.below(2), stone.equals(owner)
                        ? Blocks.OAK_FENCE.defaultBlockState()
                        : Blocks.COBBLESTONE_WALL.defaultBlockState(), 3);
                level.setBlock(stone.below(), occupied ? existing : Blocks.AIR.defaultBlockState(), 3);
                SlabAnchorAttachment.writePlacementDy(level, stone, 0.0d);
                if (occupied) SlabAnchorAttachment.writePlacementDy(level, stone.below(), 0.0d);
            }
            ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
            ItemStack held = new ItemStack(Items.OAK_TRAPDOOR);
            require(helper, !held.has(net.minecraft.core.component.DataComponents.BLOCK_STATE),
                    "the ordinary held trapdoor must not override its placement state");
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            player.setPos(owner.getX() + 2.5d, owner.getY() - 2.0d, owner.getZ() + 2.5d);
            BlockHitResult hit = new BlockHitResult(
                    new Vec3(owner.getX() + 0.5d, owner.getY(), owner.getZ() + 0.5d),
                    Direction.DOWN, owner, false);
            BlockPlaceContext placement = new BlockPlaceContext(level, player,
                    InteractionHand.MAIN_HAND, held, hit);
            require(helper, placement.getClickedPos().equals(target),
                    "the underside action must address the cell directly below the stone");
            require(helper, placement.canPlace() == !occupied,
                    "ordinary cell admission must distinguish clear space from an existing trapdoor");
            require(helper, !SlabEnsembleCoherence.canonicalOpenTransitionEnvelopesOverlap(
                            Blocks.STONE.defaultBlockState(), owner, 0.0d,
                            existing.setValue(BlockStateProperties.HALF, Half.TOP), target, 0.0d),
                    "the stone's visible underside must admit the intended trapdoor contact");
            player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND,
                    hit);
            BlockState result = level.getBlockState(target);
            require(helper, result.is(Blocks.OAK_TRAPDOOR)
                            && result.getValue(BlockStateProperties.HALF) == (occupied ? Half.BOTTOM : Half.TOP),
                    occupied ? "an occupied cell must preserve its original lower trapdoor"
                            : "a clear full-stone underside must accept an ordinary upper trapdoor");
            require(helper, held.getCount() == (occupied ? 1 : 0),
                    occupied ? "refusing an occupied cell must not consume the item"
                            : "accepted ordinary underside placement must consume the item");
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = prior;
        }
        helper.succeed();
    }

    private static void place(GameTestHelper helper, BlockState support, BlockPos owner,
                              Direction face, Direction facing, Half half, double ownerDy, double expectedDy) {
        ServerLevel level = helper.getLevel();
        BlockPos target = owner.relative(face);
        level.setBlock(owner.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(owner.below(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(owner, support, 3);
        SlabAnchorAttachment.writePlacementDy(level, owner, ownerDy);
        Player mock = helper.makeMockServerPlayer(GameType.SURVIVAL);
        if (!(mock instanceof ServerPlayer player)) {
            throw helper.assertionException("premise: placement needs a server player");
        }
        ItemStack held = new ItemStack(Items.OAK_TRAPDOOR);
        player.setYRot(facing.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        double plane = face == Direction.UP ? 1.0d
                : support.hasProperty(SlabBlock.TYPE) && support.getValue(SlabBlock.TYPE) == SlabType.TOP ? 0.5d : 0.0d;
        Vec3 hit = new Vec3(owner.getX() + 0.5d, owner.getY() + ownerDy + plane, owner.getZ() + 0.5d);
        BlockState intended = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.HALF, half);
        require(helper, !SlabEnsembleCoherence.canonicalOpenTransitionEnvelopesOverlap(
                        support, owner, ownerDy, intended, target, expectedDy),
                "the admission predicate must accept the intended visible-body contact before useOn");
        player.setPos(hit.x + 2.0d, hit.y - 1.0d, hit.z + 2.0d);
        player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, owner, false));
        BlockState placed = level.getBlockState(target);
        require(helper, placed.is(Blocks.OAK_TRAPDOOR) && placed.getValue(BlockStateProperties.HALF) == half,
                "trapdoor placement was refused or chose the wrong half: " + face + " " + support + " " + ownerDy);
        require(helper, placed.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing,
                "placement must retain facing");
        require(helper, held.getCount() == 0, "an accepted survival placement must consume the held trapdoor");
        require(helper, Double.doubleToRawLongBits(SlabAnchorAttachment.storedPlacementDy(level, target))
                        == Double.doubleToRawLongBits(expectedDy), "placement must store the aimed height");
        require(helper, !SlabEnsembleCoherence.canonicalOpenTransitionEnvelopesOverlap(
                        support, owner, ownerDy, placed, target, expectedDy), "visible bodies must admit open/close contact");
        // Removing and rebuilding the clicked owner reaches the subject's support relationship.
        level.setBlock(owner, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(owner, Blocks.STONE.defaultBlockState(), 3);
        require(helper, Double.doubleToRawLongBits(SlabAnchorAttachment.storedPlacementDy(level, target))
                        == Double.doubleToRawLongBits(expectedDy), "neighbor edits must not change the placed height");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void genuineOpenTransitionOverlapRemainsRejected(GameTestHelper helper) {
        BlockState trapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HALF, Half.BOTTOM);
        BlockPos lower = BlockPos.ZERO;
        BlockPos upper = lower.above();
        require(helper, SlabEnsembleCoherence.canonicalOpenTransitionEnvelopesOverlap(
                        trapdoor, lower, -1.5d, trapdoor, upper, -2.0d),
                "disjoint closed plates must still reject intersecting open states");
        require(helper, !SlabEnsembleCoherence.canonicalOpenTransitionEnvelopesOverlap(
                        trapdoor, lower, -1.5d, trapdoor, upper, -1.5d),
                "equal-depth open envelopes may make face contact");
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
