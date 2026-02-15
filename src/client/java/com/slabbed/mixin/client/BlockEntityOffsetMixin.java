package com.slabbed.mixin.client;

import com.slabbed.client.RenderOffsetAllowlist;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offsets block-entity rendering (signs, banners, heads, beds, etc.) down by
 * 0.5 when the block entity sits above a bottom slab.
 *
 * <p>Block entities are rendered via {@link BlockEntityRenderManager}, NOT
 * through chunk meshing, so the {@code getModelOffset} mixin has no effect
 * on them. The caller ({@code WorldRenderer.renderBlockEntities}) already
 * wraps each render call in {@code push/pop}, so we only need to add an
 * extra translate — no cleanup required.
 */
@Mixin(BlockEntityRenderManager.class)
public abstract class BlockEntityOffsetMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private <S extends BlockEntityRenderState> void slabbed$offsetBlockEntity(
            S renderState, MatrixStack matrices,
            OrderedRenderCommandQueue queue, CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        BlockPos pos = renderState.pos;
        BlockState blockState = renderState.blockState;

        if (pos == null || blockState == null) {
            return;
        }

        World world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }

        if (!RenderOffsetAllowlist.allows(blockState)) {
            return;
        }

        double yOff = SlabSupport.getYOffset(world, pos, blockState);
        if (yOff != 0.0) {
            matrices.translate(0.0, yOff, 0.0);
        }
    }
}
