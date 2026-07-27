package com.slabbed.anchor;

/**
 * Named server-side storage buckets mirrored from the NeoForge chunk data
 * attachment donor. The public gameplay decisions that write/read these
 * markers stay in later Book III slices; this enum only preserves the storage
 * shape inside one Forge chunk capability.
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
}
