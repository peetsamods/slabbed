package com.slabbed.mixin.client;

import com.slabbed.compat.CinematicTableOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Camera, hands and result share the table's physical top, while its storage owner is unchanged. */
@Pseudo
@Mixin(targets = "dev.dyslexic.cinematic.client.TableStation", remap = false)
public abstract class CinematicTableStationMixin {
    @Shadow @Final @Mutable public Vec3 top;

    @Inject(method = "<init>(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)V",
            at = @At("RETURN"), require = 1)
    private void slabbed$seatTableFrame(BlockPos pos, Direction side, CallbackInfo ci) {
        top = top.add(0.0d, CinematicTableOffset.at(pos), 0.0d);
    }
}
