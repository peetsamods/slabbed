package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorSync;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When a player starts watching a chunk, Slabbed's chunk attachments follow the chunk data on the
 * same connection. The chunk packet itself carries initial placement data for clients that
 * can read it. These messages cover other attachments and remain the fallback for older peers.
 */
@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class ChunkWatchAttachmentSyncMixin {

    @Inject(method = "sendChunkDataPackets", at = @At("TAIL"))
    private void slabbed$sendChunkAttachments(ServerPlayerEntity player,
                                              MutableObject<ChunkDataS2CPacket> cachedPacket,
                                              WorldChunk chunk, CallbackInfo ci) {
        SlabAnchorSync.sendChunk(player, chunk);
    }
}
