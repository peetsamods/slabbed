package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offsets block-entity rendering (signs, banners, heads, beds, etc.) down by
 * 0.5 when the block entity sits above a bottom slab.
 *
 * <p>Block entities are rendered via {@link BlockEntityRenderDispatcher}, NOT
 * through chunk meshing, so the {@code getModelOffset} mixin has no effect
 * on them. The caller ({@code WorldRenderer.renderBlockEntities}) already
 * wraps each render call in {@code push/pop}, so we only need to add an
 * extra translate — no cleanup required.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityOffsetMixin {

    @Inject(method = "submit", at = @At("HEAD"))
    private <S extends BlockEntityRenderState> void slabbed$offsetBlockEntity(
            S renderState, PoseStack matrices,
            SubmitNodeCollector collector, CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        BlockPos pos = renderState.blockPos;
        if (pos == null) {
            return;
        }

        Level world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }

        BlockState blockState = world.getBlockState(pos);
        double yOff = SlabSupport.getYOffset(world, pos, blockState);
        if (yOff != 0.0) {
            matrices.translate(0.0, yOff, 0.0);
        }
    }
}
