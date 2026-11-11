package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.compat.CinematicTableOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Idle and active table items are culled where they are drawn, not at their storage cell. */
@Pseudo
@Mixin(targets = "dev.dyslexic.cinematic.client.TableRenderer", remap = false)
public abstract class CinematicTableRendererMixin {
    @WrapOperation(method = "renderAll", at = @At(value = "NEW",
            target = "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/AABB;"), require = 2)
    private static AABB slabbed$seatTableBounds(BlockPos pos, Operation<AABB> original) {
        return original.call(pos).move(0.0d, CinematicTableOffset.at(pos), 0.0d);
    }
}
