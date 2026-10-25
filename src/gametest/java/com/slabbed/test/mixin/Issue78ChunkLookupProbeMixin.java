package com.slabbed.test.mixin;

import com.slabbed.test.Issue78LightShapeLookupProbe;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts waiting chunk requests without allowing the intercepted request to wait. */
@Mixin(ServerChunkManager.class)
public abstract class Issue78ChunkLookupProbeMixin {
    @Inject(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;",
            at = @At("HEAD"), cancellable = true)
    private void intercept(int x, int z, ChunkStatus status, boolean create,
                           CallbackInfoReturnable<Chunk> cir) {
        if ((Object) this == Issue78LightShapeLookupProbe.interceptedManager) {
            Issue78LightShapeLookupProbe.blockingReads.incrementAndGet();
            cir.setReturnValue(null);
        }
    }
}
