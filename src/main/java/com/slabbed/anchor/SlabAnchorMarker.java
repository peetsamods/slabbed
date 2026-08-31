package com.slabbed.anchor;

/**
 * The eight named per-chunk marker buckets.
 *
 * <p>On the NeoForge line each bucket is its own registered data attachment. Forge 1.20.1 has no
 * attachment registry, so the buckets become keys into a single chunk capability and this enum is
 * the key set. The wire form is the ordinal, so the declaration order is protocol: append only.
 */
public enum SlabAnchorMarker {
    ANCHOR("slab_anchors"),
    FROZEN_FLAT("frozen_flat"),
    LOWERED_SLAB_CARRIER("lowered_slab_carriers"),
    COMPOUND_FULL_BLOCK_ANCHOR("compound_full_block_anchors"),
    COMPOUND_VISIBLE_SIDE_LOWER_SLAB("compound_visible_side_lower_slabs"),
    COMPOUND_VISIBLE_SIDE_UPPER_SLAB("compound_visible_side_upper_slabs"),
    COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB("compound_visible_side_double_slabs"),
    COMPOUND_VISIBLE_OWNER_TOP_SLAB("compound_visible_owner_top_slabs");

    private final String nbtKey;

    SlabAnchorMarker(String nbtKey) {
        this.nbtKey = nbtKey;
    }

    public String nbtKey() {
        return nbtKey;
    }

    /** Resolves a wire ordinal, rejecting anything outside the declared set. */
    public static SlabAnchorMarker byOrdinal(int ordinal) {
        SlabAnchorMarker[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid Slabbed anchor marker ordinal: " + ordinal);
        }
        return values[ordinal];
    }
}
