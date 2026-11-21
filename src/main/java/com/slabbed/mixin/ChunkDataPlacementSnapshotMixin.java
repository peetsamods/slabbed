package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorSync;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkData.class)
public abstract class ChunkDataPlacementSnapshotMixin {
    @Shadow @Final private NbtCompound heightmap;

    @Inject(method = "<init>(Lnet/minecraft/world/chunk/WorldChunk;)V", at = @At("RETURN"))
    private void slabbed$writeInitialPlacement(WorldChunk chunk, CallbackInfo ci) {
        SlabAnchorSync.writeInitialPlacement(chunk, heightmap);
    }
}
