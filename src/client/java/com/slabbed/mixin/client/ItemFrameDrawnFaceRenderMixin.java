package com.slabbed.mixin.client;

import com.slabbed.util.HangingSeatDyHolder;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws an item frame on its support's DRAWN face (maintainer ruling, 2026-09-01). The
 * entity's real position deliberately stays at grid height — see {@code ItemFrameWysiwygMixin}
 * for why moving it corrupts the frame's derived grid cell — so the render layer applies the
 * same support-cell offset the bounding box carries; the two must stay in step.
 *
 * <p>The offset is the frame's REMEMBERED seat (see {@code HangingEntityRememberedSeatMixin}),
 * never a fresh read of the support: the same number the bounding box carries, so box and drawing
 * cannot disagree and neither follows a wall rebuilt later. The predecessor of this mixin read the
 * frame's OWN cell and hardcoded −0.5; do not regress to either.
 */
@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameDrawnFaceRenderMixin {
    @Unique
    private static final RenderStateDataKey<Vec3> SLABBED_DRAWN_FACE_OFFSET =
            RenderStateDataKey.create(() -> "slabbed:item_frame_drawn_face_offset");
    @Unique
    private static final Vec3 SLABBED_NO_OFFSET = new Vec3(0.0, 0.0, 0.0);

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
            at = @At("TAIL"))
    private void slabbed$extractDrawnFaceOffset(ItemFrame entity,
                                                ItemFrameRenderState state,
                                                float tickDelta,
                                                CallbackInfo ci) {
        state.setData(SLABBED_DRAWN_FACE_OFFSET, SLABBED_NO_OFFSET);
        // The REMEMBERED seat (synced entity data), the same number the bounding box carries.
        // Do not re-read the support here: the seat is minted once when the frame is hung and
        // must not follow a wall rebuilt at a different height (LAW 1; ruling 2026-09-13).
        double dy = ((HangingSeatDyHolder) entity).slabbed$hangSeatDy();
        if (Math.abs(dy) >= 1.0e-6d) {
            state.setData(SLABBED_DRAWN_FACE_OFFSET, new Vec3(0.0, dy, 0.0));
        }
    }

    @Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"),
            cancellable = true)
    private void slabbed$applyDrawnFaceOffset(ItemFrameRenderState state,
                                              CallbackInfoReturnable<Vec3> cir) {
        Vec3 offset = state.getDataOrDefault(SLABBED_DRAWN_FACE_OFFSET, SLABBED_NO_OFFSET);
        if (offset.x() != 0.0 || offset.y() != 0.0 || offset.z() != 0.0) {
            cir.setReturnValue(cir.getReturnValue().add(offset));
        }
    }
}
