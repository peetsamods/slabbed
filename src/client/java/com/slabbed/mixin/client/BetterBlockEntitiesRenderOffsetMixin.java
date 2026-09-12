package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.client.ClientDy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Keeps BBE's animated rendering at the same height as its static chunk geometry. */
@Pseudo
@Mixin(targets = "betterblockentities.render.AltRenderDispatcher", remap = false)
public abstract class BetterBlockEntitiesRenderOffsetMixin {
    @WrapMethod(method = "submit", remap = false)
    private void slabbed$offsetAnimatedModel(BlockEntityRenderState state, MatrixStack matrices,
            OrderedRenderCommandQueue queue, CameraRenderState camera, Operation<Void> original) {
        World world = MinecraftClient.getInstance().world;
        double dy = world == null || state.pos == null || state.blockState == null ? 0.0
                : ClientDy.dyFor(world, state.pos, state.blockState);
        matrices.push();
        try {
            matrices.translate(0.0, dy, 0.0);
            original.call(state, matrices, queue, camera);
        } finally {
            matrices.pop();
        }
    }
}
