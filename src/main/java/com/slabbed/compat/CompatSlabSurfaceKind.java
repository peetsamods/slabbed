package com.slabbed.compat;

/**
 * Named compat-only slab surface role for DIRECT object-support decisions. A compat mod's
 * proven slab surface can act as a direct seat for curated standing objects and vanilla slabs
 * without re-entering Slabbed's generic support rules, which deliberately keep skipping the
 * compat namespace (maintainer ruling, 2026-08-21, porting the reference line's lane).
 */
public enum CompatSlabSurfaceKind {
    NONE,
    BOTTOM_LIKE,
    TOP_LIKE,
    DOUBLE_LIKE,
    UNKNOWN
}
