package com.slabbed.test.mixin;

import com.slabbed.test.LoweredCarpetVibrationTest;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class FootstepCaptureGameTestMixin {
    @Redirect(method = "vibrationAndSoundEffectsFromBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V"))
    private void slabbed$captureNativeStep(Level level, Holder<GameEvent> event, Vec3 position,
                                          GameEvent.Context context) {
        if (event == GameEvent.STEP) LoweredCarpetVibrationTest.capture((Entity) (Object) this, context);
        level.gameEvent(event, position, context);
    }
}
