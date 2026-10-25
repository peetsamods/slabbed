package com.slabbed.mixin.client;

import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The F3 "Targeted Block" readout must name the block the crosshair targets.
 *
 * <p>{@code DebugHud.render} does not reuse the crosshair pick; it performs its own
 * {@code Entity.raycast} pair (one block pass, one fluid pass). The vanilla block pass walks grid
 * cells and tests each cell's own outline, so a block drawn half a cell below its grid cell (a
 * slab placed on a slab) is never tested when the ray only passes through the cell it is drawn
 * in: the readout then names the cell below, while the crosshair outline (see
 * {@link GameRendererPickOffsetRaycastMixin}) is already correct. The block pass is redirected to
 * the same offset-aware nearest-hit raycast the crosshair uses; the fluid pass stays vanilla.
 */
@Mixin(DebugHud.class)
public abstract class DebugHudOffsetRaycastMixin {

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/DrawContext;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;raycast(DFZ)Lnet/minecraft/util/hit/HitResult;"
            )
    )
    private HitResult slabbed$offsetAwareDebugPick(Entity camera, double maxDistance, float tickDelta, boolean includeFluids) {
        if (includeFluids) {
            return camera.raycast(maxDistance, tickDelta, true);
        }
        Vec3d eye = camera.getCameraPosVec(tickDelta);
        Vec3d look = camera.getRotationVec(tickDelta);
        Vec3d end = eye.add(look.x * maxDistance, look.y * maxDistance, look.z * maxDistance);
        return SlabbedOffsetRaycast.raycast(camera.getWorld(), eye, end, ShapeContext.of(camera));
    }
}
