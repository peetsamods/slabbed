package com.slabbed.test.mixin;

import com.slabbed.test.RenderAttachmentReadProbe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Worker read assertions fail before a live marker query can reach its chunk attachment map. */
@Mixin(Level.class)
public abstract class RenderChunkLookupGameTestMixin {
    @Inject(method = "getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;", at = @At("HEAD"))
    private void slabbed$forbidWorkerChunkLookup(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        RenderAttachmentReadProbe.check();
    }
}
