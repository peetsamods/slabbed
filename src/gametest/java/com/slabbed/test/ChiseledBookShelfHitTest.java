package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Slot selection follows the drawn shelf, including its remembered seat. */
public final class ChiseledBookShelfHitTest {
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredShelfInsertionSelectsAllSixDrawnSlots(GameTestHelper helper) {
        matrix(helper, false, new double[]{-0.5d, -1.5d});
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredShelfRemovalSelectsAllSixDrawnSlots(GameTestHelper helper) {
        matrix(helper, true, new double[]{-0.5d, -1.5d});
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flushShelfInsertionAndRemovalKeepVanillaSlots(GameTestHelper helper) {
        matrix(helper, false, new double[]{0.0d});
        matrix(helper, true, new double[]{0.0d});
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bookshelfBackAndSideFacesStillRefuseBooks(GameTestHelper helper) {
        boolean prior = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            var level = helper.getLevel();
            BlockPos pos = helper.absolutePos(new BlockPos(3, 5, 3));
            ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
            for (double dy : new double[]{0.0d, -0.5d}) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    for (Direction face : Direction.values()) {
                        if (face == facing) continue;
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        level.setBlock(pos, Blocks.CHISELED_BOOKSHELF.defaultBlockState()
                                .setValue(ChiseledBookShelfBlock.FACING, facing), 3);
                        SlabAnchorAttachment.writePlacementDy(level, pos, dy);
                        ItemStack book = new ItemStack(Items.BOOK);
                        player.setItemInHand(InteractionHand.MAIN_HAND, book);
                        player.gameMode.useItemOn(player, level, book, InteractionHand.MAIN_HAND,
                                hit(pos, facing, 1, dy).withDirection(face));
                        var shelf = (ChiseledBookShelfBlockEntity) level.getBlockEntity(pos);
                        require(helper, shelf != null && shelf.isEmpty(), "non-front face inserted a book");
                        require(helper, book.getCount() == 1, "non-front face consumed a book");
                    }
                }
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = prior;
        }
        helper.succeed();
    }

    private static void matrix(GameTestHelper helper, boolean removal, double[] offsets) {
        boolean prior = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            var level = helper.getLevel();
            BlockPos pos = helper.absolutePos(new BlockPos(3, 5, 3));
            ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
            for (double dy : offsets) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    for (int slot = 0; slot < 6; slot++) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        level.setBlock(pos, Blocks.CHISELED_BOOKSHELF.defaultBlockState()
                                .setValue(ChiseledBookShelfBlock.FACING, facing), 3);
                        SlabAnchorAttachment.writePlacementDy(level, pos, dy);
                        var shelf = (ChiseledBookShelfBlockEntity) level.getBlockEntity(pos);
                        require(helper, shelf != null, "missing shelf block entity");
                        if (removal) for (int index = 0; index < 6; index++)
                            shelf.setItem(index, new ItemStack(Items.BOOK));
                        ItemStack held = removal ? ItemStack.EMPTY : new ItemStack(Items.BOOK);
                        player.setItemInHand(InteractionHand.MAIN_HAND, held);
                        BlockHitResult hit = hit(pos, facing, slot, dy);
                        Vec3 physicalHit = hit.getLocation();
                        player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND, hit);
                        String context = "dy=" + dy + " face=" + facing + " slot=" + slot + " remove=" + removal;
                        require(helper, shelf.getLastInteractedSlot() == slot, "wrong selected slot: " + context);
                        for (int index = 0; index < 6; index++) {
                            boolean occupied = removal ? index != slot : index == slot;
                            require(helper, shelf.getItem(index).isEmpty() != occupied,
                                    "wrong inventory slot " + index + ": " + context);
                            require(helper, level.getBlockState(pos).getValue(
                                    ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(index)) == occupied,
                                    "wrong visible occupied slot " + index + ": " + context);
                        }
                        require(helper, removal || held.isEmpty(), "survival insertion did not consume book");
                        require(helper, !removal || player.getMainHandItem().is(Items.BOOK),
                                "removed book was not returned to player");
                        require(helper, hit.getLocation().equals(physicalHit), "physical hit was mutated");
                        require(helper, SlabAnchorAttachment.storedPlacementDy(level, pos) == dy,
                                "interaction changed remembered seat");
                    }
                }
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = prior;
        }
    }

    static BlockHitResult hit(BlockPos pos, Direction facing, int slot, double dy) {
        double column = (slot % 3 + 0.5d) / 3.0d;
        double x = switch (facing) { case NORTH -> 1.0d - column; case SOUTH -> column;
            case WEST -> 0.0d; case EAST -> 1.0d; default -> throw new IllegalArgumentException(); };
        double z = switch (facing) { case NORTH -> 0.0d; case SOUTH -> 1.0d;
            case WEST -> column; case EAST -> 1.0d - column; default -> throw new IllegalArgumentException(); };
        return new BlockHitResult(new Vec3(pos.getX() + x,
                pos.getY() + dy + (slot < 3 ? 0.75d : 0.25d), pos.getZ() + z), facing, pos, false);
    }

    private static void require(GameTestHelper helper, boolean value, String message) {
        if (!value) throw helper.assertionException(message);
    }
}
