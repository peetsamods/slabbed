package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
 * <p>The offset reads the SUPPORT cell, {@code pos.relative(direction.getOpposite())}. The
 * predecessor of this mixin (the deleted {@code ItemFrameRenderOffsetMixin}) read the frame's
 * OWN cell — usually air, so it never fired for wall frames — and hardcoded −0.5 instead of
 * the support's exact height; do not regress to either. The extract runs on the entity side
 * of the render-state split, where world access is legal; an unloaded support chunk skips the
 * shift rather than answering a wrong height.
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
        Level world = entity.level();
        BlockPos framePos = entity.getPos();
        if (world == null || framePos == null || entity.getDirection() == null) {
            return;
        }
        BlockPos supportPos = framePos.relative(entity.getDirection().getOpposite());
        if (!world.hasChunkAt(supportPos)) {
            return;
        }
        BlockState support = world.getBlockState(supportPos);
        double dy = SlabSupport.getYOffset(world, supportPos, support);
        if (Double.isFinite(dy) && Math.abs(dy) >= 1.0e-6d) {
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
