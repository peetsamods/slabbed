package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabSupportClient;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shifts the rendered block model down by 0.5 blocks for <b>every</b> block
 * sitting above a bottom slab (including full-cube blocks like furnaces).
 *
 * <p>Fabric Indigo calls {@code blockState.getModelOffset(pos)} during chunk
 * meshing. By intercepting the return value we shift the baked vertex data
 * in the chunk vertex buffer.
 *
 * <p>World context is provided via {@link SlabSupportClient#CHUNK_BUILD_WORLD},
 * populated by {@link SectionBuilderMixin}.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class TorchModelOffsetMixin {

    @Inject(method = "getModelOffset", at = @At("RETURN"), cancellable = true)
    private void slabbed$slabModelOffset(BlockPos pos, CallbackInfoReturnable<Vec3d> cir) {
        BlockRenderView world = SlabSupportClient.CHUNK_BUILD_WORLD.get();
        if (world == null) {
            return;
        }

        BlockState self = (BlockState) (Object) this;
        double yOff = SlabSupport.getYOffset(world, pos, self);
        if (yOff != 0.0) {
            cir.setReturnValue(cir.getReturnValue().add(0.0, yOff, 0.0));
        }
    }
}
