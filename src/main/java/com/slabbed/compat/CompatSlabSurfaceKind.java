package com.slabbed.compat;

/**
 * Named compat-only role for a custom (Terrain Slabs) slab surface, used by direct
 * object-support decisions. Keeps the generic Slabbed lowering subtractive (
 * {@code shouldSkipSlabSupport}/{@code shouldSkipOffset} stay a blanket exclusion, unchanged)
 * while letting proven named TS surfaces opt back in, narrowly, as a support kind.
 */
public enum CompatSlabSurfaceKind {
    NONE,
    BOTTOM_LIKE,
    TOP_LIKE,
    DOUBLE_LIKE,
    UNKNOWN
}
