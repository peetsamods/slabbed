package com.slabbed.compat;

/**
 * Named compat-only role for a custom Terrain Slabs surface. This keeps
 * Terrain Slabs out of generic Slabbed slab support while allowing proven
 * direct-support decisions to opt in narrowly.
 */
public enum CompatSlabSurfaceKind {
    NONE,
    BOTTOM_LIKE,
    TOP_LIKE,
    DOUBLE_LIKE,
    UNKNOWN
}
