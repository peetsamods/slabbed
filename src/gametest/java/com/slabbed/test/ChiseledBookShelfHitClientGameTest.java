package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.test.support.TestConnections;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Physical front-face hits select the same inventory and visible slot after a packet round trip. */
public final class ChiseledBookShelfHitClientGameTest implements FabricClientGameTest {
    private static final int ALL_SLOTS = (1 << 6) - 1;
    private static final int EXPECTED_CASES = 6 * 4 * 2 * 2;
    private static final int TIMEOUT_TICKS = 200;

    @Override
    public void runTest(ClientGameTestContext context) {
        require(SlabAnchorAttachment.FROZEN_DY_ENABLED, "frozen placement height must be enabled");
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(true).create()) {
            TestConnections.waitForChunksDownload(singleplayer);
            context.waitFor(client -> client.level != null && client.player != null
                    && client.gameMode != null, TIMEOUT_TICKS);
            BlockPos pos = singleplayer.getServer().computeOnServer(server ->
                    server.getPlayerList().getPlayers().getFirst().blockPosition().above().immutable());
            int passed = 0;
            for (double dy : new double[]{0.0d, -0.5d}) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    for (int slot = 0; slot < 6; slot++) {
                        for (boolean removal : new boolean[]{false, true}) {
                            runCase(context, singleplayer, pos, facing, slot, dy, removal);
                            Slabbed.LOGGER.info(
                                    "SLABBED_CLIENT_GAMETEST_CASE:ChiseledBookShelfHitClientGameTest"
                                            + " count={} dy={} facing={} slot={} removal={}",
                                    ++passed, dy, facing, slot, removal);
                        }
                    }
                }
            }
            require(passed == EXPECTED_CASES, "incomplete bookshelf interaction matrix");
            Slabbed.LOGGER.info(
                    "SLABBED_CLIENT_GAMETEST_CASE_COUNT:ChiseledBookShelfHitClientGameTest:{}", passed);
            Slabbed.LOGGER.info("SLABBED_CLIENT_GAMETEST_PASS:ChiseledBookShelfHitClientGameTest");
            Slabbed.LOGGER.info("CLIENT_GAMETEST | ChiseledBookShelfHitClientGameTest | PASS");
        } catch (AssertionError error) {
            throw error;
        } catch (Exception error) {
            throw new RuntimeException("bookshelf client interaction matrix failed", error);
        }
    }

    private static void runCase(ClientGameTestContext context, TestSingleplayerContext singleplayer,
            BlockPos pos, Direction facing, int slot, double dy, boolean removal) throws Exception {
        String label = "dy=" + dy + " facing=" + facing + " slot=" + slot + " removal=" + removal;
        Vec3 standing = Vec3.atBottomCenterOf(pos.relative(facing, 3).below());
        int initialMask = removal ? 1 << slot : ALL_SLOTS ^ (1 << slot);
        int expectedMask = removal ? 0 : ALL_SLOTS;
        singleplayer.getServer().runOnServer(server -> {
            var level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            // Fixture replacement must not drop the preceding case's books into the player's inventory.
            if (level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity previousShelf) {
                previousShelf.clearContent();
            }
            // A clear front approach and solid floor keep reach and movement checks independent of slot selection.
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    level.setBlock(pos.offset(x, -2, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                    for (int y = -1; y <= 2; y++) {
                        level.setBlock(pos.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
            level.setBlock(pos, Blocks.CHISELED_BOOKSHELF.defaultBlockState()
                    .setValue(ChiseledBookShelfBlock.FACING, facing), Block.UPDATE_ALL);
            SlabAnchorAttachment.writePlacementDy(level, pos, dy);
            ChiseledBookShelfBlockEntity shelf = shelf(level, pos);
            for (int index = 0; index < 6; index++) {
                if ((initialMask & (1 << index)) != 0) shelf.setItem(index, slotBook(index));
            }
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND,
                    removal ? ItemStack.EMPTY : new ItemStack(Items.BOOK));
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.teleportTo(standing.x, standing.y, standing.z);
            player.inventoryMenu.sendAllDataToRemote();
            require(inventoryMask(shelf) == initialMask && stateMask(level.getBlockState(pos)) == initialMask,
                    "server fixture occupancy mismatch: " + label);
            require(hasDy(level, pos, dy), "server fixture height mismatch: " + label);
        });
        context.waitFor(client -> synchronizedShelf(client.level, pos, facing, dy, initialMask)
                && client.player.position().distanceToSqr(standing) < 0.01d
                && client.player.getOffhandItem().isEmpty()
                && (removal ? client.player.getMainHandItem().isEmpty()
                        : client.player.getMainHandItem().is(Items.BOOK)
                                && client.player.getMainHandItem().getCount() == 1), TIMEOUT_TICKS);
        context.runOnClient(client -> {
            var hit = ChiseledBookShelfHitTest.hit(pos, facing, slot, dy);
            Vec3 physicalHit = hit.getLocation();
            require(client.player.getEyePosition().distanceToSqr(physicalHit) < 4.0d * 4.0d,
                    "fixture hit outside survival reach: " + label);
            BlockState beforeState = client.level.getBlockState(pos);
            ItemStack beforeHeld = client.player.getMainHandItem().copy();
            ItemStack beforeOffhand = client.player.getOffhandItem().copy();
            // Opposite occupancy in every other slot makes a wrong client-local row return a different result.
            InteractionResult localResult = removal
                    ? beforeState.useWithoutItem(client.level, client.player, hit)
                    : beforeState.useItemOn(client.player.getMainHandItem(), client.level,
                            client.player, InteractionHand.MAIN_HAND, hit);
            require(localResult == InteractionResult.SUCCESS,
                    "client-local slot selection returned " + localResult + ": " + label);
            require(client.level.getBlockState(pos).equals(beforeState)
                            && sameStack(client.player.getMainHandItem(), beforeHeld)
                            && sameStack(client.player.getOffhandItem(), beforeOffhand),
                    "client-local slot check changed state or held items: " + label);
            Slabbed.LOGGER.info(
                    "SLABBED_CLIENT_GAMETEST_LOCAL_SLOT:ChiseledBookShelfHitClientGameTest"
                            + " dy={} facing={} slot={} removal={}", dy, facing, slot, removal);
            // The separate packet interaction must still reach server inventory and synchronize visible slots.
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
            require(hit.getLocation().equals(physicalHit), "physical hit changed: " + label);
        });

        ServerResult result = null;
        for (int tick = 0; tick < TIMEOUT_TICKS; tick++) {
            result = singleplayer.getServer().computeOnServer(server -> {
                var level = server.overworld();
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ChiseledBookShelfBlockEntity shelf = shelf(level, pos);
                boolean exactItems = true;
                for (int index = 0; index < 6; index++) {
                    ItemStack actual = shelf.getItem(index);
                    boolean occupied = (expectedMask & (1 << index)) != 0;
                    if (occupied) {
                        ItemStack expected = index == slot ? new ItemStack(Items.BOOK) : slotBook(index);
                        exactItems &= actual.getCount() == 1
                                && ItemStack.isSameItemSameComponents(actual, expected);
                    } else {
                        exactItems &= actual.isEmpty();
                    }
                }
                ItemStack expectedHeld = removal ? slotBook(slot) : ItemStack.EMPTY;
                ItemStack held = player.getMainHandItem();
                boolean exactHeld = removal ? held.getCount() == 1
                        && ItemStack.isSameItemSameComponents(held, expectedHeld) : held.isEmpty();
                return new ServerResult(inventoryMask(shelf), stateMask(level.getBlockState(pos)),
                        shelf.getLastInteractedSlot(), exactItems, exactHeld,
                        hasDy(level, pos, dy), player.getOffhandItem().isEmpty(),
                        held + " customName=" + held.get(DataComponents.CUSTOM_NAME));
            });
            if (result.inventoryMask() != initialMask) break;
            context.waitTicks(1);
        }
        require(result != null && result.inventoryMask() == expectedMask,
                "server selected wrong inventory slot or rejected packet: " + label + " result=" + result);
        require(result.stateMask() == expectedMask && result.lastSlot() == slot && result.exactItems(),
                "server inventory/state disagreement: " + label + " result=" + result);
        require(result.exactHeld() && result.emptyOffhand() && result.exactDy(),
                "server item transfer or frozen height mismatch: " + label + " result=" + result);
        context.waitFor(client -> synchronizedShelf(client.level, pos, facing, dy, expectedMask)
                && (removal ? client.player.getMainHandItem().getCount() == 1
                        && ItemStack.isSameItemSameComponents(client.player.getMainHandItem(), slotBook(slot))
                        : client.player.getMainHandItem().isEmpty()), TIMEOUT_TICKS);
        context.runOnClient(client -> require(stateMask(client.level.getBlockState(pos)) == expectedMask,
                "synced client occupied slots disagree with server inventory: " + label));
    }

    private static ItemStack slotBook(int slot) {
        ItemStack book = new ItemStack(Items.BOOK);
        book.set(DataComponents.CUSTOM_NAME, Component.literal("shelf-slot-" + slot));
        return book;
    }

    private static boolean sameStack(ItemStack actual, ItemStack expected) {
        return actual.getCount() == expected.getCount()
                && ItemStack.isSameItemSameComponents(actual, expected);
    }

    private static ChiseledBookShelfBlockEntity shelf(BlockGetter level, BlockPos pos) {
        require(level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity,
                "missing chiseled bookshelf block entity");
        return (ChiseledBookShelfBlockEntity) level.getBlockEntity(pos);
    }

    private static int inventoryMask(ChiseledBookShelfBlockEntity shelf) {
        int mask = 0;
        for (int index = 0; index < 6; index++) {
            if (!shelf.getItem(index).isEmpty()) mask |= 1 << index;
        }
        return mask;
    }

    private static int stateMask(BlockState state) {
        require(state.is(Blocks.CHISELED_BOOKSHELF), "expected chiseled bookshelf state");
        int mask = 0;
        for (int index = 0; index < 6; index++) {
            if (state.getValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(index))) mask |= 1 << index;
        }
        return mask;
    }

    private static boolean hasDy(BlockGetter level, BlockPos pos, double dy) {
        var fact = SlabAnchorAttachment.rawPlacementDyFact(level, pos);
        return fact.present() && fact.rawBits() == Double.doubleToRawLongBits(dy);
    }

    private static boolean synchronizedShelf(BlockGetter level, BlockPos pos,
            Direction facing, double dy, int mask) {
        if (level == null) return false;
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.CHISELED_BOOKSHELF)
                && state.getValue(ChiseledBookShelfBlock.FACING) == facing
                && stateMask(state) == mask && hasDy(level, pos, dy);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private record ServerResult(int inventoryMask, int stateMask, int lastSlot,
            boolean exactItems, boolean exactHeld, boolean exactDy, boolean emptyOffhand,
            String heldDescription) {
    }
}
