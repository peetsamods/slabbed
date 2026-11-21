package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class LegacyHeightBeforeSupportEditMixin {
    @Inject(method = "setBlockState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/ChunkSection;setBlockState(IIILnet/minecraft/block/BlockState;)Lnet/minecraft/block/BlockState;"))
    private void slabbed$captureBeforeSectionWrite(BlockPos pos, BlockState state, boolean moved,
            CallbackInfoReturnable<BlockState> cir) {
        SlabAnchorAttachment.captureLegacyHeightBeforeSupportEdit((WorldChunk) (Object) this, pos, state);
    }
}
