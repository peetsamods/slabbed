package com.slabbed.command;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Pure, bounded implementation behind the permission-gated Phase 6C operator commands. */
public final class SlabbedOperatorTools {
    public static final int MIN_RADIUS = 1;
    public static final int DEFAULT_RADIUS = 6;
    public static final int LARGE_SCAN_RADIUS = 8;
    public static final int MAX_RADIUS = 16;
    public static final int MAX_SAMPLES = 12;

    private static final double LOWERED_EPSILON = 1.0e-6d;

    /**
     * The stable Mega/slabkit palette: the full 26.2 category repertoire in the same order. Mega
     * iterates the whole list; slabkit fills only genuinely empty inventory slots and reports any
     * remainder when the palette is larger than the main inventory. Flowing-fluid buckets are not a
     * generic Mega subject because their later spread can escape the exact owned-cell envelope.
     * Never shrink or reorder this list without changing the coverage contract.
     */
    private static final List<Item> PALETTE = List.of(
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

    private SlabbedOperatorTools() {
    }

    public static List<Item> paletteItems() {
        return PALETTE;
    }

    /**
     * Adds one fresh stack for each missing palette item, and only into a genuinely empty main
     * inventory slot. Existing stacks are never merged, moved, replaced, cleared, or dropped.
     */
    public static KitResult grantMissing(Inventory inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("inventory must not be null");
        }
        int added = 0;
        int alreadyPresent = 0;
        int noRoom = 0;
        for (Item item : PALETTE) {
            if (containsItem(inventory, item)) {
                alreadyPresent++;
                continue;
            }
            int freeSlot = inventory.getFreeSlot();
            if (freeSlot < 0) {
                noRoom++;
                continue;
            }
            inventory.setItem(freeSlot, new ItemStack(item));
            added++;
        }
        return new KitResult(PALETTE.size(), added, alreadyPresent, noRoom);
    }

    private static boolean containsItem(Inventory inventory, Item item) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Classifies one server cell without changing it.
     *
     * <ul>
     *   <li>hard desync: stored raw bits exist, but the public server dy authority does not
     *       return those bits;</li>
     *   <li>would move: stored raw bits differ from the store-blind geometric answer;</li>
     *   <li>unpinned lowered: geometry lowers the cell, but no authored dy is stored.</li>
     * </ul>
     *
     * <p>The categories are independent. In particular, this is a server-truth scanner; it does
     * not claim to inspect a remote client's mirror.</p>
     */
    public static CellClassification classifyAt(ServerLevel world, BlockPos pos) {
        if (world == null || pos == null) {
            throw new IllegalArgumentException("world and pos must not be null");
        }
        BlockState state = world.getBlockState(pos);
        SlabAnchorAttachment.PlacementDyFact stored =
                SlabAnchorAttachment.storedPlacementDyFact(world, pos);
        double authorityDy = SlabSupport.getYOffset(world, pos, state);
        double geometricDy = SlabSupport.getUnstoredYOffset(world, pos, state);
        boolean hardDesync = stored.present()
                && Double.doubleToRawLongBits(authorityDy) != stored.rawBits();
        boolean wouldMove = stored.present()
                && Double.doubleToRawLongBits(geometricDy) != stored.rawBits();
        boolean unpinnedLowered = !stored.present()
                && Double.isFinite(geometricDy)
                && geometricDy < -LOWERED_EPSILON;
        return new CellClassification(
                stored,
                authorityDy,
                geometricDy,
                hardDesync,
                wouldMove,
                unpinnedLowered);
    }

    /** Scans a bounded cube, skipping unloaded chunk columns before any world read. */
    public static ScanReport scan(ServerLevel world, BlockPos center, int radius) {
        if (world == null || center == null) {
            throw new IllegalArgumentException("world and center must not be null");
        }
        requireRadius(radius);
        int visitedCells = 0;
        int skippedUnloadedCells = 0;
        int examinedCells = 0;
        int hardDesync = 0;
        int wouldMove = 0;
        int unpinnedLowered = 0;
        List<Finding> samples = new ArrayList<>(MAX_SAMPLES);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!world.hasChunkAt(cursor)) {
                        skippedUnloadedCells++;
                        continue;
                    }
                    visitedCells++;
                    CellClassification cell = classifyAt(world, cursor);
                    BlockState state = world.getBlockState(cursor);
                    if (!state.isAir() || cell.storedDy().present()) {
                        examinedCells++;
                    }
                    if (cell.hardDesync()) {
                        hardDesync++;
                    }
                    if (cell.wouldMove()) {
                        wouldMove++;
                    }
                    if (cell.unpinnedLowered()) {
                        unpinnedLowered++;
                    }
                    if (cell.hasFinding() && samples.size() < MAX_SAMPLES) {
                        samples.add(new Finding(cursor.immutable(), cell));
                    }
                }
            }
        }
        return new ScanReport(
                center.immutable(),
                radius,
                visitedCells,
                skippedUnloadedCells,
                examinedCells,
                hardDesync,
                wouldMove,
                unpinnedLowered,
                radius > LARGE_SCAN_RADIUS,
                samples);
    }

    public static void requireRadius(int radius) {
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            throw new IllegalArgumentException(
                    "radius must be between " + MIN_RADIUS + " and " + MAX_RADIUS + ", got " + radius);
        }
    }

    public record KitResult(int paletteSize, int added, int alreadyPresent, int noRoom) {
        public KitResult {
            if (paletteSize < 0 || added < 0 || alreadyPresent < 0 || noRoom < 0
                    || added + alreadyPresent + noRoom != paletteSize) {
                throw new IllegalArgumentException("kit result counts must partition the palette");
            }
        }
    }

    public record CellClassification(
            SlabAnchorAttachment.PlacementDyFact storedDy,
            double authorityDy,
            double geometricDy,
            boolean hardDesync,
            boolean wouldMove,
            boolean unpinnedLowered
    ) {
        public boolean hasFinding() {
            return hardDesync || wouldMove || unpinnedLowered;
        }

        public String labels() {
            List<String> labels = new ArrayList<>(3);
            if (hardDesync) {
                labels.add("HARD_DESYNC");
            }
            if (wouldMove) {
                labels.add("WOULD_MOVE");
            }
            if (unpinnedLowered) {
                labels.add("UNPINNED_LOWERED");
            }
            return String.join(",", labels);
        }
    }

    public record Finding(BlockPos pos, CellClassification classification) {
    }

    public record ScanReport(
            BlockPos center,
            int radius,
            int visitedCells,
            int skippedUnloadedCells,
            int examinedCells,
            int hardDesync,
            int wouldMove,
            int unpinnedLowered,
            boolean largeScanWarning,
            List<Finding> samples
    ) {
        public ScanReport {
            samples = List.copyOf(samples);
        }
    }
}
