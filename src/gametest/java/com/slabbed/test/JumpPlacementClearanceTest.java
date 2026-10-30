package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Placement clearance follows the candidate's landing body, including the placing player. */
public final class JumpPlacementClearanceTest {
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void jumpingAboveLoweredTopAllowsPlacement(GameTestHelper helper) {
        check(helper, true, 0.75d, false, false, true);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void standingInsideLoweredBodyRejectsPlacement(GameTestHelper helper) {
        check(helper, true, -0.5d, false, false, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void ordinaryTopStillRejectsPlayerOverlap(GameTestHelper helper) {
        check(helper, false, 0.75d, false, false, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void jumpingAboveOrdinaryTopAllowsPlacement(GameTestHelper helper) {
        check(helper, false, 1.05d, false, false, true);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anotherPlayerInsideLoweredBodyRejectsPlacement(GameTestHelper helper) {
        check(helper, true, 0.75d, true, false, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void occupiedTargetRejectsPlacement(GameTestHelper helper) {
        check(helper, true, 0.75d, false, true, false);
    }

    private static void check(GameTestHelper helper, boolean slab, double feetAboveCell,
                              boolean otherPlayer, boolean occupied, boolean accepted) {
        boolean prior = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        ServerPlayer player = null;
        ServerPlayer obstruction = null;
        try {
            ServerLevel level = helper.getLevel();
            BlockPos support = helper.absolutePos(new BlockPos(3, 3, 3));
            BlockPos target = support.above();
            BlockState supportState = slab
                    ? Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                    : Blocks.STONE.defaultBlockState();
            level.setBlock(support, supportState, 3);
            SlabAnchorAttachment.writePlacementDy(level, support, 0.0d);
            level.setBlock(target, occupied ? Blocks.GOLD_BLOCK.defaultBlockState()
                    : Blocks.AIR.defaultBlockState(), 3);
            if (occupied) SlabAnchorAttachment.writePlacementDy(level, target, 0.0d);
            player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(target.getX() + 0.5d, target.getY() + feetAboveCell, target.getZ() + 0.5d);
            player.setNoGravity(true);
            require(helper, level.getEntities(null, player.getBoundingBox()).contains(player),
                    "the placing player must participate in world entity collision queries");
            if (otherPlayer) {
                obstruction = helper.makeMockServerPlayerInLevel();
                obstruction.setPos(target.getX() + 0.5d, target.getY(), target.getZ() + 0.5d);
                obstruction.setNoGravity(true);
            }
            ItemStack held = new ItemStack(Items.STONE);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            BlockHitResult hit = new BlockHitResult(new Vec3(support.getX() + 0.5d,
                    support.getY() + (slab ? 0.5d : 1.0d), support.getZ() + 0.5d),
                    Direction.UP, support, false);
            player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND, hit);
            BlockState result = level.getBlockState(target);
            require(helper, result.is(accepted ? Blocks.STONE : occupied ? Blocks.GOLD_BLOCK : Blocks.AIR),
                    "placement must respect the final body and target occupancy; got " + result);
            require(helper, held.getCount() == (accepted ? 0 : 1),
                    "only an accepted placement may consume the held block");
            if (accepted) {
                require(helper, SlabAnchorAttachment.storedPlacementDy(level, target) == (slab ? -0.5d : 0.0d),
                        "accepted placement must store its intended landing height");
            }
        } finally {
            if (obstruction != null) obstruction.discard();
            if (player != null) player.discard();
            SlabAnchorAttachment.FROZEN_DY_ENABLED = prior;
        }
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) throw helper.assertionException(message);
    }
}
