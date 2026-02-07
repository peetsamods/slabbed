package com.slabbed.util;

import net.minecraft.world.BlockRenderView;

/**
 * Client-side thread-local state for slab support during chunk meshing.
 *
 * <p>During chunk rebuilds, {@link net.minecraft.client.render.chunk.SectionBuilder#build}
 * provides a {@link net.minecraft.client.render.chunk.ChunkRendererRegion} that we capture
 * here so that {@link net.minecraft.block.AbstractBlock.AbstractBlockState#getModelOffset}
 * (which has no world parameter) can look up neighboring blocks.
 */
public final class SlabSupportClient {
    private SlabSupportClient() {
    }

    /**
     * The world view available during chunk meshing on the current thread.
     * Set at the start of SectionBuilder.build(), cleared at the end.
     */
    public static final ThreadLocal<BlockRenderView> CHUNK_BUILD_WORLD = new ThreadLocal<>();
}
