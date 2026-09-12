package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.client.ClientDy;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.util.math.Direction;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.function.Predicate;

/** Aligns BBE's extra chunk geometry with the same height as the selection outline. */
@Pseudo
@Mixin(targets = "betterblockentities.client.chunk.pipeline.BBEEmitter", remap = false)
public abstract class BetterBlockEntitiesMeshOffsetMixin {
    @Unique private Object slabbed$renderer;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void slabbed$captureRenderer(@Coerce Object renderer, CallbackInfo ci) {
        slabbed$renderer = renderer;
    }

    @WrapMethod(method = "emit", remap = false)
    private void slabbed$offsetExtraMesh(ArrayList<BlockModelPart> parts, Predicate<Direction> cullTest,
            @Coerce Object bufferer, Operation<Void> original) {
        SodiumRenderContextAccessor context = (SodiumRenderContextAccessor) slabbed$renderer;
        double dy = ClientDy.dyFor(context.slabbed$getLevel(), context.slabbed$getPos(),
                context.slabbed$getState());
        Vector3f origin = ((SodiumBlockOriginAccessor) slabbed$renderer).slabbed$getPosOffset();
        float previousY = origin.y;
        origin.y = previousY + (float) dy;
        try {
            original.call(parts, cullTest, bufferer);
        } finally {
            origin.y = previousY;
        }
    }
}
