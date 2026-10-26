package com.slabbed.mixin.client;

import com.slabbed.compat.sable.SableHitGeometry;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The F3 "Targeted Block" readout must name the block the crosshair targets.
 *
 * <p>{@code DebugScreenOverlay.render} does not reuse the crosshair pick; it performs its own
 * {@code Entity.pick} pair (one block pass, one fluid pass). The vanilla block pass walks grid
 * cells and tests each cell's own outline, so a block drawn half a cell below its grid cell (a
 * slab placed on a slab) is never tested when the ray only passes through the cell it is drawn
 * in: the readout then names the cell below, while the crosshair (see
 * {@link GameRendererPickOffsetRaycastMixin}) is already correct. The block pass is redirected to
 * the same offset-aware raycast the crosshair uses, composed with an external pick owner exactly
 * as the crosshair composes it; the fluid pass stays vanilla.
 */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugHudOffsetRaycastMixin {

    private static final String SABLE_MOD_ID = "sable";

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"
            ),
            require = 1
    )
    private HitResult slabbed$offsetAwareDebugPick(Entity camera, double maxDistance, float tickDelta, boolean includeFluids) {
        if (!SlabbedOffsetRaycast.ENABLED
                || includeFluids
                || !(camera.level() instanceof ClientLevel level)) {
            return camera.pick(maxDistance, tickDelta, includeFluids);
        }
        Vec3 eye = camera.getEyePosition(tickDelta);
        Vec3 look = camera.getViewVector(tickDelta);
        Vec3 end = eye.add(look.x * maxDistance, look.y * maxDistance, look.z * maxDistance);
        BlockHitResult offset = SlabbedOffsetRaycast.raycast(level, eye, end, CollisionContext.of(camera));
        boolean externalOwnerLoaded = ModList.get().isLoaded(SABLE_MOD_ID);
        HitResult externalHit = externalOwnerLoaded
                ? camera.pick(maxDistance, tickDelta, includeFluids)
                : null;
        return externalOwnerLoaded
                ? SableHitGeometry.composePick(level, eye, externalHit, offset)
                : offset;
    }
}
