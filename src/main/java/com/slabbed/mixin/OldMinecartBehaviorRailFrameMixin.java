package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.slabbed.util.MinecartRailFrame;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The DEFAULT rail solver computes in the LOGICAL grid frame while the cart's real position stays
 * PHYSICAL (maintainer ruling, 2026-09-06).
 *
 * <p>{@code moveAlongTrack} becomes symmetric once both ends are converted: every cart Y it READS
 * feeds rail-cell math, and every Y it WRITES back is already a rail-frame value. Two injectors, no
 * ordinals — the read handler covers all four reads and the write handler all four writes.
 *
 * <p>INVARIANT: do NOT widen the method list. {@code tick()} reads only X and Z, and
 * {@code stepAlongTrack} returns a constant, so a wider scope would convert values that are not in
 * the rail frame. {@code getPos}/{@code getPosOffs} take their coordinates as arguments and must
 * stay unconverted here — {@code moveAlongTrack} already hands them a logical Y, and the renderer
 * converts at its own call sites.
 *
 * <p>INVARIANT: convert a POSITION, never a DIFFERENCE. The two {@code getPos} results this method
 * subtracts from one another are both logical, so their difference is frame-free either way.
 */
@Mixin(OldMinecartBehavior.class)
public abstract class OldMinecartBehaviorRailFrameMixin extends MinecartBehavior {

    protected OldMinecartBehaviorRailFrameMixin(AbstractMinecart minecart) {
        super(minecart);
    }

    @ModifyExpressionValue(
            method = "moveAlongTrack(Lnet/minecraft/server/level/ServerLevel;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;getY()D"))
    private double slabbed$railFrameRead(double physicalY) {
        return MinecartRailFrame.toLogicalY(this.minecart, physicalY);
    }

    @ModifyArg(
            method = "moveAlongTrack(Lnet/minecraft/server/level/ServerLevel;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior;"
                           + "setPos(DDD)V"),
            index = 1)
    private double slabbed$railFrameWrite(double logicalY) {
        return MinecartRailFrame.toPhysicalY(this.minecart, logicalY);
    }
}
