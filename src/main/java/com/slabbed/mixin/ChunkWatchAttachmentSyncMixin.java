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
 * same connection (TAIL: always after the chunk packet itself), so the client never renders a
 * freshly received chunk without its stored heights. 1.20.1 sends chunk data from
 * {@code ThreadedAnvilChunkStorage}; later versions moved this into {@code ChunkDataSender}.
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
