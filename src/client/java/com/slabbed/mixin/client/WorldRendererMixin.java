package com.slabbed.mixin.client;

import com.slabbed.client.ClientDy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.OutlineRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Shadow private ClientWorld world;

    @Unique
    private static long SLABBED$LAST_OUTLINE_LOG = 0L;

    @Redirect(
            method = "drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;DDDLnet/minecraft/client/render/state/OutlineRenderState;IF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/state/OutlineRenderState;shape()Lnet/minecraft/util/shape/VoxelShape;")
    )
    private VoxelShape slabbed$offsetOutlineShape(OutlineRenderState outlineRenderState) {
        VoxelShape shape = outlineRenderState.shape();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return shape;
        }
        ClientWorld clientWorld = mc.world;
        HitResult hit = mc.crosshairTarget;
        if (clientWorld == null || !(hit instanceof BlockHitResult blockHit)) {
            return shape;
        }
        BlockPos pos = blockHit.getBlockPos();
        double dy = ClientDy.dyFor(clientWorld, pos, clientWorld.getBlockState(pos));
        return dy != 0.0D ? shape.offset(0.0D, dy, 0.0D) : shape;
    }
}
