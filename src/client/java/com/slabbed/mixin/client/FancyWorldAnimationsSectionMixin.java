package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.client.FwaPlacementSeat;
import java.util.Map;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** A lowered animation can remain visible after its storage section leaves the frustum. */
@Pseudo
@Mixin(targets = "fr.madu59.fwa.FancyWorldAnimationsClient", remap = false)
public abstract class FancyWorldAnimationsSectionMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/culling/Frustum;isVisible(Lnet/minecraft/world/phys/AABB;)Z",
            ordinal = 0))
    private static boolean slabbed$includePlacedColumns(Frustum frustum, AABB section,
            Operation<Boolean> original, @Local Map.Entry<?, ?> entry) {
        if (original.call(frustum, section)) return true;
        if (entry.getValue() instanceof Map<?, ?> animations) {
            for (Object animation : animations.values()) {
                if (animation instanceof FancyWorldAnimationsAccessor accessor) {
                    FwaPlacementSeat.publish(animation, accessor.slabbed$getPos());
                    AABB bounds = accessor.slabbed$getBounds();
                    if (bounds != null && original.call(frustum, bounds)) return true;
                }
            }
        }
        return false;
    }
}
