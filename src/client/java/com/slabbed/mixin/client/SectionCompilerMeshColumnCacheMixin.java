package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.slabbed.util.MeshColumnCache;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

/** The native compile owns and releases all geometry memo entries, including exceptional exits. */
@Mixin(value = SectionCompiler.class, remap = false)
public abstract class SectionCompilerMeshColumnCacheMixin {
    @WrapMethod(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;")
    private SectionCompiler.Results slabbed$scopedColumns(
            SectionPos section, RenderChunkRegion region, VertexSorting sorting,
            SectionBufferBuilderPack buffers,
            List<AddSectionGeometryEvent.AdditionalSectionRenderer> additional,
            Operation<SectionCompiler.Results> original
    ) {
        // A custom/subclassed view is not a proven immutable snapshot.
        RenderChunkRegion snapshot = region != null && region.getClass() == RenderChunkRegion.class ? region : null;
        try (MeshColumnCache.Scope ignored = MeshColumnCache.open(snapshot)) {
            return original.call(section, region, sorting, buffers, additional);
        }
    }
}
