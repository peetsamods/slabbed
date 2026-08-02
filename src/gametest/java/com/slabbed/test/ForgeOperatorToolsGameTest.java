package com.slabbed.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabAnchorCapabilities;
import com.slabbed.anchor.SlabAnchorStore;
import com.slabbed.command.SlabbedOperatorTools;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Forge-native Phase 6C contract for the small operator tools shipped in the ordinary core. */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgeOperatorToolsGameTest {

    @GameTest(template = "empty")
    public void operatorToolsPreserveInventoryAndOnlyReadWorldTruth(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(world);
        Inventory inventory = player.getInventory();
        List<ItemStack> originalInventory = snapshotInventory(inventory);

        BlockPos wouldMove = ctx.absolutePos(new BlockPos(2, 3, 2));
        BlockPos unpinned = ctx.absolutePos(new BlockPos(4, 3, 2));
        BlockPos healthy = ctx.absolutePos(new BlockPos(6, 3, 2));
        BlockPos hardDesync = ctx.absolutePos(new BlockPos(2, 3, 5));
        List<BlockPos> writtenDy = List.of(wouldMove, healthy, hardDesync);

        try {
            assertCommandTree(ctx, world, player);
            assertPaletteContract(ctx);
            assertInventoryContract(ctx, world, player, inventory);
            assertScannerContract(ctx, world, player, wouldMove, unpinned, healthy, hardDesync);
        } finally {
            restoreInventory(inventory, originalInventory);
            for (BlockPos pos : writtenDy) {
                SlabAnchorAttachment.removePlacementDy(world, pos);
            }
        }
        ctx.succeed();
    }

    private static void assertPaletteContract(GameTestHelper ctx) {
        List<Item> expected = List.of(
                Blocks.SMOOTH_STONE_SLAB.asItem(),
                Blocks.STONE_SLAB.asItem(),
                Blocks.OAK_SLAB.asItem(),
                Blocks.STONE.asItem(),
                Blocks.OAK_LOG.asItem(),
                Blocks.OAK_STAIRS.asItem(),
                Blocks.TORCH.asItem(),
                Blocks.LANTERN.asItem(),
                Blocks.CHAIN.asItem(),
                Blocks.FLOWER_POT.asItem(),
                Blocks.OAK_DOOR.asItem(),
                Blocks.OAK_TRAPDOOR.asItem(),
                Blocks.OAK_FENCE.asItem(),
                Blocks.OAK_FENCE_GATE.asItem(),
                Blocks.COBBLESTONE_WALL.asItem(),
                Blocks.GLASS_PANE.asItem(),
                Blocks.IRON_BARS.asItem(),
                Blocks.WHITE_CARPET.asItem(),
                Blocks.WHITE_BANNER.asItem(),
                Blocks.RED_BED.asItem(),
                Blocks.OAK_SIGN.asItem(),
                Blocks.OAK_HANGING_SIGN.asItem(),
                Blocks.STONE_BUTTON.asItem(),
                Blocks.LEVER.asItem(),
                Blocks.STONE_PRESSURE_PLATE.asItem(),
                Items.REDSTONE,
                Blocks.REPEATER.asItem(),
                Blocks.COMPARATOR.asItem(),
                Blocks.DAYLIGHT_DETECTOR.asItem(),
                Blocks.HOPPER.asItem(),
                Blocks.CHEST.asItem(),
                Blocks.CONDUIT.asItem(),
                Blocks.POINTED_DRIPSTONE.asItem(),
                Blocks.LADDER.asItem(),
                Blocks.RAIL.asItem(),
                Items.POWDER_SNOW_BUCKET,
                Blocks.SOUL_TORCH.asItem(),
                Blocks.SOUL_LANTERN.asItem(),
                Blocks.BIRCH_DOOR.asItem(),
                Blocks.CANDLE.asItem());
        ctx.assertTrue(SlabbedOperatorTools.paletteItems().equals(expected),
                "the Forge palette must preserve all 40 donor categories in stable order");
        ctx.assertTrue(new LinkedHashSet<>(expected).size() == expected.size(),
                "the Mega palette must not spend columns on duplicate items");
    }

    private static void assertCommandTree(
            GameTestHelper ctx,
            ServerLevel world,
            ServerPlayer player
    ) {
        CommandDispatcher<CommandSourceStack> dispatcher =
                world.getServer().getCommands().getDispatcher();
        CommandNode<CommandSourceStack> slabkit = dispatcher.getRoot().getChild("slabkit");
        CommandNode<CommandSourceStack> slabcheck = dispatcher.getRoot().getChild("slabcheck");
        ctx.assertTrue(slabkit != null, "/slabkit must be registered on the real server dispatcher");
        ctx.assertTrue(slabcheck != null, "/slabcheck must be registered on the real server dispatcher");

        CommandSourceStack denied = player.createCommandSourceStack().withPermission(0);
        CommandSourceStack allowed = player.createCommandSourceStack().withPermission(2);
        ctx.assertTrue(!slabkit.canUse(denied) && !slabcheck.canUse(denied),
                "both operator tools must reject permission level 0");
        ctx.assertTrue(slabkit.canUse(allowed) && slabcheck.canUse(allowed),
                "both operator tools must admit permission level 2");

        CommandNode<CommandSourceStack> radiusNode = slabcheck.getChild("radius");
        ctx.assertTrue(radiusNode instanceof ArgumentCommandNode,
                "/slabcheck must expose one bounded integer radius argument");
        IntegerArgumentType radiusType = (IntegerArgumentType)
                ((ArgumentCommandNode<?, ?>) radiusNode).getType();
        ctx.assertTrue(radiusType.getMinimum() == SlabbedOperatorTools.MIN_RADIUS,
                "radius argument minimum must share the service contract");
        ctx.assertTrue(radiusType.getMaximum() == SlabbedOperatorTools.MAX_RADIUS,
                "radius argument maximum must share the service contract");
    }

    private static void assertInventoryContract(
            GameTestHelper ctx,
            ServerLevel world,
            ServerPlayer player,
            Inventory inventory
    ) {
        inventory.clearContent();
        ItemStack sentinel = new ItemStack(Items.DIAMOND_PICKAXE);
        sentinel.setDamageValue(37);
        sentinel.setHoverName(Component.literal("keep me exactly"));
        inventory.setItem(0, sentinel);

        Item preexistingPaletteItem = SlabbedOperatorTools.paletteItems().get(0);
        ItemStack preexisting = new ItemStack(preexistingPaletteItem, 7);
        preexisting.setHoverName(Component.literal("existing palette stack"));
        inventory.setItem(1, preexisting);

        List<ItemStack> beforeKit = snapshotInventory(inventory);
        int freeMainSlots = 34;
        CommandSourceStack allowed = player.createCommandSourceStack()
                .withLevel(world)
                .withPermission(2);
        int commandResult = world.getServer().getCommands()
                .performPrefixedCommand(allowed, "slabkit");
        ctx.assertTrue(commandResult > 0, "/slabkit must execute for an operator player");
        assertStack(ctx, beforeKit.get(0), inventory.getItem(0),
                "/slabkit must preserve the unrelated named/damaged stack");
        assertStack(ctx, beforeKit.get(1), inventory.getItem(1),
                "/slabkit must not merge into or replace an existing palette stack");
        List<Item> palette = SlabbedOperatorTools.paletteItems();
        int expectedPresent = 1 + freeMainSlots;
        for (int index = 0; index < palette.size(); index++) {
            boolean expectedInInventory = index < expectedPresent;
            ctx.assertTrue(containsItem(inventory, palette.get(index)) == expectedInInventory,
                    "/slabkit must fill the ordered palette prefix without displacing later items"
                            + " (index " + index + ")");
        }

        List<ItemStack> once = snapshotInventory(inventory);
        SlabbedOperatorTools.KitResult second = SlabbedOperatorTools.grantMissing(inventory);
        ctx.assertTrue(second.added() == 0
                        && second.alreadyPresent() == expectedPresent
                        && second.noRoom() == palette.size() - expectedPresent,
                "a second kit pass must preserve the full inventory and report the remainder");
        assertInventory(ctx, once, inventory,
                "a second kit pass must not change any slot");

        inventory.clearContent();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack occupied = new ItemStack(Items.COBBLESTONE, (slot % 63) + 1);
            occupied.setHoverName(Component.literal("occupied-" + slot));
            inventory.setItem(slot, occupied);
        }
        List<ItemStack> full = snapshotInventory(inventory);
        SlabbedOperatorTools.KitResult noRoom = SlabbedOperatorTools.grantMissing(inventory);
        ctx.assertTrue(noRoom.added() == 0
                        && noRoom.noRoom() == SlabbedOperatorTools.paletteItems().size(),
                "a full inventory must omit the entire missing palette without replacing or dropping");
        assertInventory(ctx, full, inventory,
                "/slabkit must leave a full inventory byte-for-byte unchanged");
    }

    private static void assertScannerContract(
            GameTestHelper ctx,
            ServerLevel world,
            ServerPlayer player,
            BlockPos wouldMove,
            BlockPos unpinned,
            BlockPos healthy,
            BlockPos hardDesync
    ) {
        world.setBlock(wouldMove.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
        world.setBlock(wouldMove, Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
        SlabAnchorAttachment.writePlacementDy(world, wouldMove, -0.5d);

        world.setBlock(unpinned.below(), Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_NONE);
        world.setBlock(unpinned, Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);

        world.setBlock(healthy.below(), Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_NONE);
        world.setBlock(healthy, Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
        SlabAnchorAttachment.writePlacementDy(world, healthy, -0.5d);

        world.setBlock(hardDesync, Blocks.POWDER_SNOW.defaultBlockState(), Block.UPDATE_NONE);
        LevelChunk hardChunk = world.getChunkAt(hardDesync);
        SlabAnchorStore hardStore = hardChunk
                .getCapability(SlabAnchorCapabilities.SLAB_ANCHOR_STORE)
                .orElseThrow(() -> new AssertionError("fixture chunk has no Slabbed store"));
        hardStore.putPlacementDy(hardDesync.asLong(), -0.25d);

        SlabbedOperatorTools.CellClassification moving =
                SlabbedOperatorTools.classifyAt(world, wouldMove);
        SlabbedOperatorTools.CellClassification loose =
                SlabbedOperatorTools.classifyAt(world, unpinned);
        SlabbedOperatorTools.CellClassification pinned =
                SlabbedOperatorTools.classifyAt(world, healthy);
        SlabbedOperatorTools.CellClassification corrupt =
                SlabbedOperatorTools.classifyAt(world, hardDesync);
        ctx.assertTrue(!moving.hardDesync() && moving.wouldMove() && !moving.unpinnedLowered(),
                "stored -0.5 over flat geometry must classify as would-move only");
        ctx.assertTrue(!loose.hardDesync() && !loose.wouldMove() && loose.unpinnedLowered(),
                "a geometric lowered state without stored authorship must classify unpinned");
        ctx.assertTrue(!pinned.hardDesync() && !pinned.wouldMove() && !pinned.unpinnedLowered(),
                "a stored value agreeing with geometry must be healthy");
        ctx.assertTrue(corrupt.hardDesync(),
                "stored bits hidden by a reader guard must classify as a hard server disagreement");

        Set<LevelChunk> fixtureChunks = new LinkedHashSet<>();
        for (BlockPos pos : List.of(wouldMove, unpinned, healthy, hardDesync)) {
            fixtureChunks.add(world.getChunkAt(pos));
        }
        fixtureChunks.forEach(chunk -> chunk.setUnsaved(false));
        List<BlockSnapshot> before = snapshotBlocks(world,
                List.of(wouldMove, unpinned, healthy, hardDesync));
        SlabbedOperatorTools.ScanReport report =
                SlabbedOperatorTools.scan(world, unpinned, 4);
        player.moveTo(
                unpinned.getX() + 0.5d,
                unpinned.getY(),
                unpinned.getZ() + 0.5d,
                player.getYRot(),
                player.getXRot());
        CommandSourceStack allowed = player.createCommandSourceStack()
                .withLevel(world)
                .withPermission(2);
        int explicitResult = world.getServer().getCommands()
                .performPrefixedCommand(allowed, "slabcheck 4");
        int defaultResult = world.getServer().getCommands()
                .performPrefixedCommand(allowed, "slabcheck");
        List<BlockSnapshot> after = snapshotBlocks(world,
                List.of(wouldMove, unpinned, healthy, hardDesync));
        ctx.assertTrue(report.hardDesync() >= 1
                        && report.wouldMove() >= 1
                        && report.unpinnedLowered() >= 1,
                "the bounded scan must independently count all three classifications");
        ctx.assertTrue(before.equals(after),
                "/slabcheck must not alter blocks or stored raw dy facts");
        ctx.assertTrue(explicitResult > 0 && defaultResult > 0,
                "both explicit-radius and default-radius /slabcheck forms must execute");
        ctx.assertTrue(fixtureChunks.stream().noneMatch(LevelChunk::isUnsaved),
                "/slabcheck must not dirty any fixture chunk");

        assertThrows(ctx, () -> SlabbedOperatorTools.requireRadius(0),
                "the scanner must reject a below-minimum radius");
        assertThrows(ctx, () -> SlabbedOperatorTools.requireRadius(
                        SlabbedOperatorTools.MAX_RADIUS + 1),
                "the scanner must reject an above-maximum radius");

        BlockPos unloaded = new BlockPos(20_000_000, 64, 20_000_000);
        ctx.assertTrue(!world.hasChunkAt(unloaded),
                "fixture precondition: the distant scan chunk must start unloaded");
        SlabbedOperatorTools.ScanReport skipped =
                SlabbedOperatorTools.scan(world, unloaded, SlabbedOperatorTools.MIN_RADIUS);
        ctx.assertTrue(skipped.visitedCells() == 0 && skipped.skippedUnloadedCells() > 0,
                "an unloaded-only scan must report skipped cells honestly");
        ctx.assertTrue(!world.hasChunkAt(unloaded),
                "/slabcheck must not load a chunk merely to inspect it");

        SlabbedOperatorTools.ScanReport large =
                SlabbedOperatorTools.scan(world, unloaded, SlabbedOperatorTools.LARGE_SCAN_RADIUS + 1);
        ctx.assertTrue(large.largeScanWarning(),
                "the scan result must carry the large-scan warning from one source of truth");
    }

    private static boolean containsItem(Inventory inventory, Item item) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    private static List<ItemStack> snapshotInventory(Inventory inventory) {
        List<ItemStack> snapshot = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            snapshot.add(inventory.getItem(slot).copy());
        }
        return snapshot;
    }

    private static void restoreInventory(Inventory inventory, List<ItemStack> snapshot) {
        for (int slot = 0; slot < snapshot.size(); slot++) {
            inventory.setItem(slot, snapshot.get(slot).copy());
        }
    }

    private static void assertInventory(
            GameTestHelper ctx,
            List<ItemStack> expected,
            Inventory actual,
            String message
    ) {
        ctx.assertTrue(expected.size() == actual.getContainerSize(), message + " (size)");
        for (int slot = 0; slot < expected.size(); slot++) {
            assertStack(ctx, expected.get(slot), actual.getItem(slot), message + " (slot " + slot + ")");
        }
    }

    private static void assertStack(
            GameTestHelper ctx,
            ItemStack expected,
            ItemStack actual,
            String message
    ) {
        ctx.assertTrue(ItemStack.matches(expected, actual), message);
    }

    private static void assertThrows(GameTestHelper ctx, Runnable action, String message) {
        try {
            action.run();
            ctx.fail(message + " (no exception)");
        } catch (IllegalArgumentException expected) {
            // Contract green.
        }
    }

    private static List<BlockSnapshot> snapshotBlocks(ServerLevel world, List<BlockPos> positions) {
        List<BlockSnapshot> snapshots = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            snapshots.add(new BlockSnapshot(
                    pos.immutable(),
                    world.getBlockState(pos),
                    SlabAnchorAttachment.storedPlacementDyFact(world, pos)));
        }
        return snapshots;
    }

    private record BlockSnapshot(
            BlockPos pos,
            BlockState state,
            SlabAnchorAttachment.PlacementDyFact storedDy
    ) {
    }
}
