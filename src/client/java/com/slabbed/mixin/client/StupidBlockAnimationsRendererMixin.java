package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.slabbed.util.SlabSupport;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The animation renderer owns moving-model translation, including the saved placement height. */
@Pseudo
@Mixin(targets = "fnn.stupidblockplacement.animation.PlacementAnimationRenderer", remap = false)
public abstract class StupidBlockAnimationsRendererMixin {
    @Inject(method = "render(Lnet/fabricmc/fabric/api/client/rendering/v1/level/LevelRenderContext;)V",
            at = @At(value = "INVOKE", target = "Lfnn/stupidblockplacement/animation/PlacementAnimationState;applyLocal(Lcom/mojang/blaze3d/vertex/PoseStack;JF)V"),
            require = 1)
    private static void slabbed$seatAnimatedBlock(LevelRenderContext context, CallbackInfo ci,
                                                 @Local PoseStack poses, @Local ClientLevel world,
                                                 @Local BlockPos pos, @Local BlockState state) {
        // Apply before local rotations so the animation's pivot follows the placed block.
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy != 0.0) poses.translate(0.0, dy, 0.0);
    }
}
