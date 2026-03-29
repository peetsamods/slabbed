package com.slabbed.util;

/**
 * Client-side thread-local state for slab support during chunk meshing.
 */
public final class SlabSupportClient
{
    private SlabSupportClient()
    {
    }

    public static final ThreadLocal<Boolean> HAS_SLABBED_BLOCK = new ThreadLocal<>();
}
