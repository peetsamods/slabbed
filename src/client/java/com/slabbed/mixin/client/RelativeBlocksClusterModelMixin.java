package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.client.model.OffsetBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

/** The cluster wrapper owns its seat; nested members must not translate it again (LAW.md). */
@Pseudo
@Mixin(targets = "com.lukeolafp.relativeblocks.blocks.BlockClusterBakedModel", remap = false)
public abstract class RelativeBlocksClusterModelMixin {
    @WrapOperation(method = "emitQuads", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;emitQuads("
                    + "Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/QuadEmitter;"
                    + "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;"
                    + "Ljava/util/function/Predicate;)V"))
    private void slabbed$emitMemberWithOneSeat(
            BlockStateModel model, QuadEmitter emitter, BlockAndTintGetter view, BlockPos pos,
            BlockState state, RandomSource random, Predicate<Direction> cullTest,
            Operation<Void> original) {
        double memberDy = model instanceof OffsetBlockStateModel
                ? OffsetBlockStateModel.appliedModelDy(view, pos, state) : 0.0;
        if (memberDy == 0.0) {
            original.call(model, emitter, view, pos, state, random, cullTest);
            return;
        }
        // Cancel only the nested translation. Keep the member's tint, culling and shape modifiers.
        emitter.pushTransform(quad -> {
            for (int vertex = 0; vertex < 4; vertex++) {
                quad.pos(vertex, quad.x(vertex), (float) (quad.y(vertex) - memberDy), quad.z(vertex));
            }
            return true;
        });
        try {
            original.call(model, emitter, view, pos, state, random, cullTest);
        } finally {
            emitter.popTransform();
        }
    }
}
