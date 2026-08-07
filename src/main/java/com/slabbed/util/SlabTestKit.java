package com.slabbed.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The shared Slabbed test-kit: one curated, ordered list of items — one representative per category
 * that matters when live-testing Slabbed placement/height behaviour (slabs, blocks, decorations,
 * redstone, hanging things, block-entities, buckets, …).
 *
 * <p>Single source of truth read by the server-side test-kit commands ({@code /slabkit} and
 * {@code /slabrig mega}), so there is no second copy that can drift.
 *
 * <p>The list is padded with {@code minecraft:air} to exactly {@link #COLUMNS} x {@link #ROWS} = 45
 * cells so the grid is always full; consumers that place/give items skip the air padding.
 */
public final class SlabTestKit {

    /** Columns in the kit grid (matches the vanilla hotbar width). */
    public static final int COLUMNS = 9;

    /** Rows in the kit grid (five stacked hotbars). */
    public static final int ROWS = 5;

    /** Total palette cells; {@link #PALETTE} is padded to exactly this many entries. */
    public static final int SIZE = COLUMNS * ROWS;

    private static final Identifier AIR = Identifier.fromNamespaceAndPath("minecraft", "air");

    /**
     * The curated ids, one representative per Slabbed-relevant category, in a stable display order,
     * then padded with {@code minecraft:air} up to {@link #SIZE}. Never reorder casually — consumers
     * address cells positionally.
     */
    public static final List<Identifier> PALETTE = buildPalette();

    private SlabTestKit() {
    }

    private static List<Identifier> buildPalette() {
        List<Identifier> ids = new ArrayList<>();
        // Slabs (Slabbed's whole reason to exist) + a couple of full/partial support blocks.
        add(ids, "minecraft:smooth_stone_slab");
        add(ids, "minecraft:stone_slab");
        add(ids, "minecraft:oak_slab");
        add(ids, "minecraft:stone");
        add(ids, "minecraft:oak_log");
        add(ids, "minecraft:oak_stairs");
        // Floor / ceiling light-emitters that sit on a support surface.
        add(ids, "minecraft:torch");
        add(ids, "minecraft:lantern");
        add(ids, "minecraft:iron_chain");
        add(ids, "minecraft:flower_pot");
        // Doors / trapdoors — half-height + full-height attach behaviour.
        add(ids, "minecraft:oak_door");
        add(ids, "minecraft:oak_trapdoor");
        // Connecting blocks (state changes on placement, never after).
        add(ids, "minecraft:oak_fence");
        add(ids, "minecraft:oak_fence_gate");
        add(ids, "minecraft:cobblestone_wall");
        add(ids, "minecraft:glass_pane");
        add(ids, "minecraft:iron_bars");
        // Thin top-layer / decorative.
        add(ids, "minecraft:white_carpet");
        add(ids, "minecraft:white_banner");
        add(ids, "minecraft:red_bed");
        add(ids, "minecraft:oak_sign");
        add(ids, "minecraft:oak_hanging_sign");
        // Redstone-attached surface devices.
        add(ids, "minecraft:stone_button");
        add(ids, "minecraft:lever");
        add(ids, "minecraft:stone_pressure_plate");
        add(ids, "minecraft:redstone");
        add(ids, "minecraft:repeater");
        add(ids, "minecraft:comparator");
        add(ids, "minecraft:daylight_detector");
        // Block-entities that snap/orient on placement.
        add(ids, "minecraft:hopper");
        add(ids, "minecraft:chest");
        add(ids, "minecraft:conduit");
        // Special vertical-attach / special-shape items — regression-critical, kept within the first 36
        // placeable entries so /slabkit always hands them out (fix 2).
        add(ids, "minecraft:pointed_dripstone");
        add(ids, "minecraft:ladder");
        add(ids, "minecraft:rail");
        add(ids, "minecraft:powder_snow_bucket");

        // Redundant second-of-a-kind variants, parked AFTER the first 36 placeable entries: they are the
        // ones /slabkit skips for inventory capacity (torch/lantern/oak_door/light already cover them).
        add(ids, "minecraft:soul_torch");
        add(ids, "minecraft:soul_lantern");
        add(ids, "minecraft:birch_door");
        add(ids, "minecraft:candle");

        // Pad to a full grid so positional consumers never see a ragged last row.
        while (ids.size() < SIZE) {
            ids.add(AIR);
        }
        // Guard against ever growing past a full grid.
        if (ids.size() > SIZE) {
            throw new IllegalStateException("SlabTestKit.PALETTE exceeds " + SIZE + " entries: " + ids.size());
        }
        return Collections.unmodifiableList(ids);
    }

    private static void add(List<Identifier> ids, String id) {
        ids.add(Identifier.parse(id));
    }

    /**
     * Resolves every palette id against the item registry, in order. Unknown ids and the
     * {@code minecraft:air} padding resolve to the air {@link Item}; callers that place or give
     * items should skip {@link Item}s whose default stack is empty.
     */
    public static List<Item> resolve() {
        List<Item> items = new ArrayList<>(PALETTE.size());
        for (Identifier id : PALETTE) {
            items.add(BuiltInRegistries.ITEM.getValue(id));
        }
        return items;
    }

    /** True if {@code id} is registered in the item registry (used by validation tests). */
    public static boolean isRegistered(Identifier id) {
        return BuiltInRegistries.ITEM.containsKey(id);
    }

    /**
     * The palette resolved to {@link Item}s with the {@code minecraft:air} padding removed, in palette
     * order. This is the list the server-side commands hand out ({@code /slabkit}) and iterate over when
     * auto-placing one representative per category ({@code /slabrig mega}). Additive — it never
     * reorders or mutates {@link #PALETTE}.
     */
    public static List<Item> placeableItems() {
        List<Item> items = new ArrayList<>();
        for (Identifier id : PALETTE) {
            if (id.equals(AIR)) {
                continue;
            }
            items.add(BuiltInRegistries.ITEM.getValue(id));
        }
        return items;
    }
}
