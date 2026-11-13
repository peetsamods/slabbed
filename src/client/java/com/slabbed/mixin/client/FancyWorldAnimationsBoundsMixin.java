package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.slabbed.util.SlabSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Culling and rendering use the same translated bounds for a hanging column. */
@Pseudo
@Mixin(targets = "fr.madu59.fwa.anims.Animation", remap = false)
public abstract class FancyWorldAnimationsBoundsMixin {
    @ModifyReturnValue(method = "getBoundingBox", at = @At("RETURN"))
    private AABB slabbed$placedBounds(AABB original) {
        var world = Minecraft.getInstance().level;
        if (world == null || original == null || !((Object) this instanceof FancyWorldAnimationsChainAccess)) return original;
        var pos = ((FancyWorldAnimationsAccessor) this).slabbed$getPos();
        double dy = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        return dy == 0 ? original : original.move(0, dy, 0);
    }
}
