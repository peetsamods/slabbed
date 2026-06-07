package com.slabbed.mixin.client;

import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The single ownership rule for Slabbed crosshair targeting.
 *
 * <p>The client pick path is
 * {@code GameRenderer.updateCrosshairTarget} → {@code ClientPlayerEntity.method_76762}
 * → {@code method_76763}, where the block portion of the pick is a single
 * {@code entity.raycast(reach, tickProgress, false)} call. That call delegates to
 * {@code BlockView.raycast}, whose voxel DDA returns the first cell with a hit rather
 * than the globally nearest — which mistargets Slabbed's vertically offset blocks
 * (see {@link SlabbedOffsetRaycast}).
 *
 * <p>This redirect replaces <em>only</em> that block raycast with the offset-aware
 * nearest-hit raycast. Everything else in the vanilla pick is preserved: the
 * block-vs-entity distance merge, the entity raycast, and the reach clamp
 * ({@code method_76764}). Because the returned {@link net.minecraft.util.hit.BlockHitResult}
 * becomes both {@code client.crosshairTarget} (driving the selection outline) and the
 * hit sent to the server on use/place, fixing this one call corrects targeting and
 * placement together — and makes the legacy per-block-type rescue scan obsolete.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPickOffsetRaycastMixin {

    @Redirect(
            method = "method_76763",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;raycast(DFZ)Lnet/minecraft/util/hit/HitResult;"
            )
    )
    private static HitResult slabbed$offsetAwarePick(Entity entity, double maxDistance, float tickProgress, boolean includeFluids) {
        Vec3d eye = entity.getCameraPosVec(tickProgress);
        Vec3d look = entity.getRotationVec(tickProgress);
        Vec3d end = eye.add(look.x * maxDistance, look.y * maxDistance, look.z * maxDistance);
        // includeFluids is always false on the pick path; mirror vanilla by not testing
        // fluid shapes. ShapeContext.of(entity) matches RaycastContext(..., entity).
        return SlabbedOffsetRaycast.raycast(entity.getEntityWorld(), eye, end, ShapeContext.of(entity));
    }
}
