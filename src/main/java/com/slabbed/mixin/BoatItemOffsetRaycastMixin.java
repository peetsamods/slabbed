package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BoatItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Boat placement uses the visible stored block surface while preserving vanilla fluid hits. */
@Mixin(BoatItem.class)
public abstract class BoatItemOffsetRaycastMixin {

    @Redirect(method = "use", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/item/BoatItem;raycast(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/RaycastContext$FluidHandling;)Lnet/minecraft/util/hit/BlockHitResult;"))
    private BlockHitResult slabbed$raycastVisibleBoatSurface(
            World world, PlayerEntity player, RaycastContext.FluidHandling fluidHandling) {
        Vec3d start = player.getEyePos();
        Vec3d end = start.add(player.getRotationVector(player.getPitch(), player.getYaw())
                .multiply(player.getBlockInteractionRange()));
        BlockHitResult vanilla = world.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.OUTLINE, fluidHandling, player));
        BlockHitResult offset = SlabbedOffsetRaycast.raycast(world, start, end, ShapeContext.of(player));
        // Only a hit on a cell with modern provenance owns a stored visible surface; a legacy cell
        // keeps the vanilla hit.
        if (offset.getType() == HitResult.Type.MISS
                || !SlabAnchorAttachment.usesFrozenPlacementHeight(world, offset.getBlockPos())) {
            return vanilla;
        }
        if (vanilla.getType() != HitResult.Type.MISS
                && vanilla.getPos().squaredDistanceTo(start) <= offset.getPos().squaredDistanceTo(start)) {
            return vanilla;
        }
        return offset;
    }
}
