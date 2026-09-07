package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.slabbed.util.MinecartRailFrame;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The experimental-movement rail solver computes in the LOGICAL grid frame (maintainer ruling,
 * 2026-09-06). Same split as the default solver, scoped to the two methods that are actually rail
 * geometry.
 *
 * <p>{@code adjustToRails} and {@code stepAlongTrack} both measure the cart against
 * {@code Vec3.atBottomCenterOf(railPos)} and both write a railPos-derived Y straight back, so both
 * belong in the grid frame.
 *
 * <p>INVARIANT: {@code moveAlongTrack} is DELIBERATELY EXCLUDED. Its single position read is both
 * the base of a delta against the cart's old position and the position of the lerp steps it builds;
 * converting one side of a delta is a bug. {@code lerpClientPositionAndRotation} is the client
 * interpolation write and is likewise excluded.
 *
 * <p>INVARIANT: the ONE lerp step built inside {@code adjustToRails} is converted back to PHYSICAL
 * on the way out, so the whole interpolation and render pipeline stays in one frame and this path
 * needs no client-side code.
 */
@Mixin(NewMinecartBehavior.class)
public abstract class NewMinecartBehaviorRailFrameMixin extends MinecartBehavior {

    protected NewMinecartBehaviorRailFrameMixin(AbstractMinecart minecart) {
        super(minecart);
    }

    @ModifyExpressionValue(
            method = {
                    "adjustToRails(Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;Z)V",
                    "stepAlongTrack(Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/block/state/properties/RailShape;D)D"
            },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior;"
                           + "position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 slabbed$railFrameRead(Vec3 physical) {
        return MinecartRailFrame.toLogical(this.minecart, physical);
    }

    @ModifyArg(
            method = "adjustToRails(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior;"
                           + "setPos(Lnet/minecraft/world/phys/Vec3;)V"))
    private Vec3 slabbed$railFrameWriteVec(Vec3 logical) {
        return MinecartRailFrame.toPhysical(this.minecart, logical);
    }

    @ModifyArg(
            method = "stepAlongTrack(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/properties/RailShape;D)D",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior;"
                           + "setPos(DDD)V"),
            index = 1)
    private double slabbed$railFrameWriteY(double logicalY) {
        return MinecartRailFrame.toPhysicalY(this.minecart, logicalY);
    }

    /**
     * The lerp step is what the server sends the client. It must leave this method in the same frame
     * every other lerp value uses: physical.
     */
    @ModifyArg(
            method = "adjustToRails(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior$MinecartStep;"
                           + "<init>(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;FFF)V"),
            index = 0)
    private Vec3 slabbed$lerpStepStaysPhysical(Vec3 logical) {
        return MinecartRailFrame.toPhysical(this.minecart, logical);
    }
}
