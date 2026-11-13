package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Suffocation follows remembered occupied geometry, not the owner's unshifted logical cell. */
public final class LoweredBlockSuffocationTest {
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void standingOnLoweredTopDoesNotSuffocate(GameTestHelper helper) {
        frozen(() -> {
            for (double dy : new double[]{-0.5, -1.0, -1.5, -2.0}) {
                BlockPos owner = owner(helper, Blocks.STONE.defaultBlockState(), dy);
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                player.setPos(owner.getX() + 0.5, owner.getY() + 1 + dy, owner.getZ() + 0.5);
                expect(helper, player, false, "standing above shifted stone");
            }
        });
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void insideLoweredSolidFindsOwnerAboveEyeCell(GameTestHelper helper) {
        frozen(() -> {
            for (double dy : new double[]{-0.5, -1.0, -1.5, -2.0}) {
                BlockPos owner = owner(helper, Blocks.STONE.defaultBlockState(), dy);
                Player player = eyeAt(helper, owner, owner.getY() + dy + 0.25);
                expect(helper, player, true, "inside shifted stone, including owners across air");
            }
        });
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void insideOrdinarySolidStillSuffocates(GameTestHelper helper) {
        frozen(() -> {
            BlockPos owner = owner(helper, Blocks.STONE.defaultBlockState(), 0.0);
            expect(helper, eyeAt(helper, owner, owner.getY() + 0.5), true, "inside ordinary stone");
        });
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredNonSuffocatingMaterialsStaySafe(GameTestHelper helper) {
        frozen(() -> {
            for (BlockState state : new BlockState[]{Blocks.GLASS.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState()}) {
                BlockPos owner = owner(helper, state, -2.0);
                expect(helper, eyeAt(helper, owner, owner.getY() - 1.75), false, "non-suffocating shifted material");
                expect(helper, eyeAt(helper, owner, owner.getY() + 0.25), false, "non-suffocating logical cell");
            }
        });
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void noPhysicsStillBypassesSuffocation(GameTestHelper helper) {
        frozen(() -> {
            BlockPos owner = owner(helper, Blocks.STONE.defaultBlockState(), -2.0);
            Player player = eyeAt(helper, owner, owner.getY() - 1.75);
            player.noPhysics = true;
            expect(helper, player, false, "noPhysics bypass");
        });
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void shiftedOwnerDoesNotSuffocateOutsideItsBody(GameTestHelper helper) {
        frozen(() -> {
            BlockPos owner = owner(helper, Blocks.STONE.defaultBlockState(), -2.0);
            expect(helper, eyeAt(helper, owner, owner.getY() - 2.25), false, "below shifted body");
            expect(helper, eyeAt(helper, owner, owner.getY() - 0.75), false, "above shifted body");
            Player beside = eyeAt(helper, owner, owner.getY() - 1.75);
            beside.setPos(owner.getX() + 1.5, beside.getY(), beside.getZ());
            expect(helper, beside, false, "beside shifted body");
        });
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void ownerWindowIncludesItsUpperBoundary(GameTestHelper helper) {
        frozen(() -> {
            BlockPos owner = helper.absolutePos(new BlockPos(2, 20, 2));
            helper.getLevel().setBlock(owner, Blocks.STONE.defaultBlockState(), 3);
            SlabAnchorAttachment.writePlacementDy(helper.getLevel(), owner, -16.0);
            expect(helper, eyeAt(helper, owner, owner.getY() - 15.75), true, "owner at movement window boundary");
            SlabAnchorAttachment.writePlacementDy(helper.getLevel(), owner, -17.0);
            expect(helper, eyeAt(helper, owner, owner.getY() - 16.75), false, "owner beyond bounded movement window");
        });
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void eachOwnerKeepsItsOwnSuffocationEligibility(GameTestHelper helper) {
        frozen(() -> {
            BlockPos glass = owner(helper, Blocks.GLASS.defaultBlockState(), -2.0);
            BlockPos stone = glass.above();
            helper.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
            SlabAnchorAttachment.writePlacementDy(helper.getLevel(), stone, -0.5);
            expect(helper, eyeAt(helper, glass, glass.getY() - 1.75), false,
                    "nonintersecting stone must not lend eligibility to intersecting glass");
            helper.getLevel().setBlock(glass, Blocks.STONE.defaultBlockState(), 3);
            SlabAnchorAttachment.writePlacementDy(helper.getLevel(), glass, -2.0);
            helper.getLevel().setBlock(stone, Blocks.GLASS.defaultBlockState(), 3);
            SlabAnchorAttachment.writePlacementDy(helper.getLevel(), stone, -0.5);
            expect(helper, eyeAt(helper, glass, glass.getY() - 1.75), true,
                    "intersecting stone keeps eligibility beside nonintersecting glass");
        });
        helper.succeed();
    }

    private static BlockPos owner(GameTestHelper helper, BlockState state, double dy) {
        BlockPos rel = new BlockPos(2, 5, 2);
        for (int y = 0; y < 5; y++) helper.setBlock(new BlockPos(2, y, 2), Blocks.AIR.defaultBlockState());
        helper.setBlock(rel, state);
        BlockPos pos = helper.absolutePos(rel);
        SlabAnchorAttachment.writePlacementDy(helper.getLevel(), pos, dy);
        return pos;
    }

    private static Player eyeAt(GameTestHelper helper, BlockPos owner, double eyeY) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(owner.getX() + 0.5, eyeY - player.getEyeHeight(), owner.getZ() + 0.5);
        return player;
    }

    private static void expect(GameTestHelper helper, Player player, boolean expected, String context) {
        if (player.isInWall() != expected) throw helper.assertionException(context);
    }

    private static void frozen(Runnable body) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try { body.run(); } finally { SlabAnchorAttachment.FROZEN_DY_ENABLED = previous; }
    }
}
