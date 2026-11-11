package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.slabbed.compat.CinematicTableOffset;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Grid items, work samples and cursor picking use an owner-local height without changing pack layouts. */
@Pseudo
@Mixin(targets = "dev.dyslexic.cinematic.client.TableLayout", remap = false)
public abstract class CinematicTableLayoutMixin {
    @ModifyReturnValue(method = "surfaceY(Lnet/minecraft/core/BlockPos;)D", at = @At("RETURN"), require = 1)
    private double slabbed$seatTableSurface(double height, BlockPos pos) {
        return height + CinematicTableOffset.at(pos);
    }
}
