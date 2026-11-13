package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.slabbed.util.SlabSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Animation roots use the same remembered world-space seat as their static models. */
@Pseudo
@Mixin(targets = "fr.madu59.fwa.FancyWorldAnimationsClient", remap = false)
public abstract class FancyWorldAnimationsRendererMixin {
    @Inject(method = "renderAnimation", at = @At(value = "INVOKE",
            target = "Lfr/madu59/fwa/anims/Animation;render(Lfr/madu59/fwa/rendering/AnimationRenderingContext;)V"))
    private static void slabbed$seatAnimation(@Coerce Object animation, @Coerce Object context, CallbackInfo ci, @Local BlockPos pos, @Local PoseStack poses) {
        var world = Minecraft.getInstance().level;
        if (world != null && animation instanceof FancyWorldAnimationsChainAccess) poses.translate(0, SlabSupport.getYOffset(world, pos, world.getBlockState(pos)), 0);
    }
}
