package com.slabbed.mixin.client;

import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The single ownership rule for Slabbed crosshair targeting (MC 1.21.1).
 *
 * <p>Analog of the 1.21.11 {@code ClientPickOffsetRaycastMixin}. In 1.21.1 the client
 * pick is {@code GameRenderer.updateCrosshairTarget} -> {@code findCrosshairTarget},
 * where the block portion is a single {@code camera.raycast(reach, tickDelta, false)}
 * call. That call delegates to {@code BlockView.raycast}, whose voxel DDA returns the
 * first cell with a hit rather than the globally nearest — which mistargets Slabbed's
 * vertically offset blocks (see {@link SlabbedOffsetRaycast}).
 *
 * <p>This redirect replaces <em>only</em> that block raycast with the offset-aware
 * nearest-hit raycast, preserving the vanilla block-vs-entity merge and reach clamp.
 *
 * <p><b>ACTIVE</b> (registered in slabbed.client.mixins.json). The 1.21.1 overhaul
 * cutover registered this mixin, removed {@code GameRendererCrosshairRetargetMixin}
 * (+ {@code LoweredSideSlabRetargeter}) and the slab-side comfort overlay from
 * {@code SlabSupportStateMixin}, and added the fence/wall/pane outline gate —
 * mirroring commit 39a345e7 on the 1.21.11 branch. The {@link SlabbedOffsetRaycast}
 * util it depends on is verified on 1.21.1 by the server gametests in
 * {@code OffsetRaycastTargetingTest}. Final client-pick acceptance is Julia's live
 * {@code runClient} (fabric-client-gametest is broken on 1.21.1).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPickOffsetRaycastMixin {

    @Redirect(
            method = "findCrosshairTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;raycast(DFZ)Lnet/minecraft/util/hit/HitResult;"
            )
    )
    private HitResult slabbed$offsetAwarePick(Entity camera, double maxDistance, float tickDelta, boolean includeFluids) {
        Vec3d eye = camera.getCameraPosVec(tickDelta);
        Vec3d look = camera.getRotationVec(tickDelta);
        Vec3d end = eye.add(look.x * maxDistance, look.y * maxDistance, look.z * maxDistance);
        return SlabbedOffsetRaycast.raycast(camera.getWorld(), eye, end, ShapeContext.of(camera));
    }
}
