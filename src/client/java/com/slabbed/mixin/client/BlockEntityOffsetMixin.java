package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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

    @Inject(method = "render", at = @At("HEAD"))
    private <E extends BlockEntity> void slabbed$offsetBlockEntity(
            E blockEntity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers,
            CallbackInfo ci
    ) {
        if (blockEntity == null) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        BlockState blockState = blockEntity.getBlockState();

        if (pos == null || blockState == null) {
            return;
        }

        Level world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }

        double yOff = SlabSupport.getYOffset(world, pos, blockState);
        if (yOff != 0.0) {
            matrices.translate(0.0, yOff, 0.0);
        }
    }
}
