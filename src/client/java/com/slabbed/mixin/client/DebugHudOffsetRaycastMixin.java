package com.slabbed.mixin.client;

import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The F3 "Targeted Block" readout and its block-tag lines must name the block the crosshair
 * targets.
 *
 * <p>The looking-at debug entry does not reuse the crosshair pick: its block-state and block-tag
 * infos each perform their own {@code Entity.pick} (the fluid infos are separate). The vanilla
 * pass walks grid cells and tests each cell's own outline, so a block drawn half a cell below its
 * grid cell (a slab placed on a slab) is never tested when the ray only passes through the cell it
 * is drawn in: the readout then names the cell below, while the crosshair (see
 * {@link LocalPlayerPickOffsetRaycastMixin}) is already correct. Both block passes are redirected
 * to the same offset-aware nearest-hit raycast the crosshair uses, under the same switch.
 */
@Mixin(targets = {
        "net.minecraft.client.gui.components.debug.DebugEntryLookingAt$BlockStateInfo",
        "net.minecraft.client.gui.components.debug.DebugEntryLookingAt$BlockTagInfo"})
public abstract class DebugHudOffsetRaycastMixin {

    @Redirect(
            method = "getHitResult(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/HitResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"),
            require = 1)
    private HitResult slabbed$offsetAwareDebugPick(
            Entity entity, double maxDistance, float tickProgress, boolean includeFluids) {
        if (!SlabbedOffsetRaycast.ENABLED
                || includeFluids
                || entity == null
                || !(entity.level() instanceof ClientLevel level)) {
            return entity.pick(maxDistance, tickProgress, includeFluids);
        }
        Vec3 eye = entity.getEyePosition(tickProgress);
        Vec3 view = entity.getViewVector(tickProgress);
        Vec3 end = eye.add(view.x * maxDistance, view.y * maxDistance, view.z * maxDistance);
        return SlabbedOffsetRaycast.raycast(level, eye, end, CollisionContext.of(entity));
    }
}
