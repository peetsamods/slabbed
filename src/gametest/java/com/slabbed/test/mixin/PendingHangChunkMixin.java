package com.slabbed.test.mixin;

import com.slabbed.test.PendingHangChunkProbe;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While {@link PendingHangChunkProbe} is active on a chunk cache, every nonblocking chunk lookup
 * answers "not ready" and every blocking chunk request is counted. Inert otherwise.
 */
@Mixin(ServerChunkCache.class)
public abstract class PendingHangChunkMixin {
    @Inject(method = "getChunkNow(II)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("HEAD"), cancellable = true)
    private void slabbed$unavailable(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        if ((Object) this == PendingHangChunkProbe.manager) {
            PendingHangChunkProbe.nonblockingReads++;
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"))
    private void slabbed$countBlockingReads(int x, int z, ChunkStatus status, boolean create, CallbackInfoReturnable<ChunkAccess> cir) {
        if ((Object) this == PendingHangChunkProbe.manager) {
            PendingHangChunkProbe.blockingReads++;
        }
    }
}
