package com.slabbed.mixin.client;

import com.slabbed.anchor.SlabAnchorSync;
import java.util.function.Consumer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldChunk.class)
public abstract class ClientWorldChunkPlacementSnapshotMixin {
    @Inject(method = "loadFromPacket", at = @At("HEAD"))
    private void slabbed$readInitialPlacement(PacketByteBuf sections, NbtCompound heightmaps,
            Consumer<ChunkData.BlockEntityVisitor> blockEntities, CallbackInfo ci) {
        SlabAnchorSync.readInitialPlacement((WorldChunk) (Object) this, heightmaps);
    }
}
