package com.slabbed.mixin.client;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws an item frame on the face it REMEMBERS being hung on (maintainer ruling, 2026-09-01 for
 * frames; 2026-09-13 for the remembered seat). The entity's real position deliberately stays at
 * grid height - see {@code ItemFrameWysiwygMixin} for why moving it corrupts the frame's derived
 * grid cell - so the render layer applies the same seat the bounding box carries; the two must
 * stay in step.
 *
 * <p>The offset is the frame's REMEMBERED seat (see {@code HangingEntityRememberedSeatMixin}),
 * never a fresh read of the support: the same number the bounding box carries, so box and drawing
 * cannot disagree and neither follows a wall rebuilt later. A predecessor of this class read the
 * support cell on every frame; do not regress to that, and do not regress further to reading the
 * frame's OWN cell (usually air, so it never fired for wall frames at all).
 *
 * <p>The hook is {@code getRenderOffset}, which the entity render dispatcher applies and then
 * un-applies around the whole draw. On this line that is the live path: the renderer declares the
 * narrowed override and vanilla's bridge delegates to it. Do NOT also translate the pose stack
 * inside {@code render} - the predecessor did, and keeping both would double the offset.
 */
@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameDrawnFaceRenderMixin {

    @Inject(method = "getRenderOffset(Lnet/minecraft/world/entity/decoration/ItemFrame;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"),
            cancellable = true)
    private void slabbed$drawOnRememberedSeat(ItemFrame entity,
                                              float partialTicks,
                                              CallbackInfoReturnable<Vec3> cir) {
        // The REMEMBERED seat (synced entity data), the same number the bounding box carries.
        // Do not re-read the support here: the seat is minted once when the frame is hung and
        // must not follow a wall rebuilt at a different height (LAW.md, Law 1; ruling 2026-09-13).
        double dy = ((HangingSeatDyHolder) entity).slabbed$hangSeatDy();
        if (Math.abs(dy) >= 1.0e-6d) {
            Vec3 vanilla = cir.getReturnValue();
            cir.setReturnValue(new Vec3(vanilla.x, vanilla.y + dy, vanilla.z));
        }
    }
}
