package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabSupportClient;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optimization for subchunk building. Building subchunk with a slabbed block is currently very expensive.
 * Thanks to toggling, only few subchunks will have slabbed block, so we don't need to check every block then.
 * We set a flag per subchunk and skip expensive checks if no slabbed blocks can exist.
 */
@Mixin(SectionBuilder.class)
public abstract class SectionBuilderMixin
{
    @Inject(method = "build", at = @At("HEAD"))
    private void slabbed$captureWorld(
            ChunkSectionPos sectionPos,
            ChunkRendererRegion region,
            VertexSorter sorter,
            BlockBufferAllocatorStorage allocators,
            CallbackInfoReturnable<?> cir
    ) {
        World world = ((ChunkRendererRegionAccessor) region).getWorld();
        ChunkPos chunkPos = sectionPos.toChunkPos();
        WorldChunk worldChunk = world.getChunk(chunkPos.x, chunkPos.z);
        int currentYIndex = world.sectionCoordToIndex(sectionPos.getSectionY());
        int topIndex = world.sectionCoordToIndex(world.getTopSectionCoord());
        boolean hasSlabbedBlock = worldChunk.getSection(currentYIndex).hasAny(SlabSupport::isSupportingSlab) ||
            (currentYIndex > 0 && worldChunk.getSection(currentYIndex - 1).hasAny(SlabSupport::isSupportingSlab)) ||
            (currentYIndex < topIndex && worldChunk.getSection(currentYIndex + 1).hasAny(SlabSupport::isSupportingSlab));
        SlabSupportClient.HAS_SLABBED_BLOCK.set(hasSlabbedBlock);
    }

    @Inject(method = "build", at = @At("RETURN"))
    private void slabbed$releaseWorld(
            ChunkSectionPos sectionPos,
            ChunkRendererRegion region,
            VertexSorter sorter,
            BlockBufferAllocatorStorage allocators,
            CallbackInfoReturnable<?> cir
    ) {
        SlabSupportClient.HAS_SLABBED_BLOCK.remove();
    }
}
