package com.slabbed.test.mixin;

import com.slabbed.test.PendingHangChunkProbe;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkManager.class)
public abstract class PendingHangChunkMixin {
    @Inject(method = "getWorldChunk(II)Lnet/minecraft/world/chunk/WorldChunk;", at = @At("HEAD"), cancellable = true)
    private void unavailable(int x, int z, CallbackInfoReturnable<WorldChunk> cir) {
        if ((Object) this == PendingHangChunkProbe.manager) {
            PendingHangChunkProbe.nonblockingReads++;
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", at = @At("HEAD"))
    private void countBlockingReads(int x, int z, ChunkStatus status, boolean create, CallbackInfoReturnable<Chunk> cir) {
        if ((Object) this == PendingHangChunkProbe.manager) {
            PendingHangChunkProbe.blockingReads++;
        }
    }
}
