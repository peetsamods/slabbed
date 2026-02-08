package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupportClient;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.ChunkSectionPos;
import com.mojang.blaze3d.systems.VertexSorter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures the {@link ChunkRendererRegion} into a thread-local during chunk
 * meshing so that model-offset mixins can look up neighboring block states.
 */
@Mixin(SectionBuilder.class)
public abstract class SectionBuilderMixin {

    @Inject(method = "build", at = @At("HEAD"))
    private void slabbed$captureWorld(
            ChunkSectionPos sectionPos,
            ChunkRendererRegion region,
            VertexSorter sorter,
            BlockBufferAllocatorStorage allocators,
            CallbackInfoReturnable<?> cir
    ) {
        SlabSupportClient.CHUNK_BUILD_WORLD.set(region);
    }

    @Inject(method = "build", at = @At("RETURN"))
    private void slabbed$releaseWorld(
            ChunkSectionPos sectionPos,
            ChunkRendererRegion region,
            VertexSorter sorter,
            BlockBufferAllocatorStorage allocators,
            CallbackInfoReturnable<?> cir
    ) {
        SlabSupportClient.CHUNK_BUILD_WORLD.remove();
    }
}
