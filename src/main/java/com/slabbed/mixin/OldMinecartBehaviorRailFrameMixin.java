package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.util.MinecartRailFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The DEFAULT rail solver computes in the LOGICAL grid frame while the cart's real position stays
 * PHYSICAL (maintainer ruling, 2026-09-06), and the cart rides the DRAWN slope of a fitted rail
 * (maintainer ruling, 2026-09-28).
 *
 * <p>{@code moveAlongTrack} reads the cart's Y four times and writes it back four times. Every READ
 * converts with the seat the cart's position was last written with. The WRITES differ by what they
 * are for, in the order vanilla makes them:
 * <ol>
 *   <li>the pre-collision placement: the cart is put at the TOP of the rail's drawn profile, exactly
 *       as vanilla lifts a cart to the high end of a ramp cell before its collision sweep;</li>
 *   <li>and <li>the two ramp re-snaps after the sweep: same seat as the placement they follow;</li>
 *   <li>the final snap onto the rail: the seat of the drawn slope AT THE CART'S NEW PLACE, which is
 *       the position the client is sent and the one the next tick starts from.</li>
 * </ol>
 * Each write that changes the seat binds the new value on the cart, so the reads that follow it in
 * the same tick stay in one frame.
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

    private static final String MOVE_ALONG_TRACK = "moveAlongTrack(Lnet/minecraft/server/level/ServerLevel;)V";
    private static final String SET_POS = "Lnet/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior;setPos(DDD)V";

    protected OldMinecartBehaviorRailFrameMixin(AbstractMinecart minecart) {
        super(minecart);
    }

    @ModifyExpressionValue(
            method = MOVE_ALONG_TRACK,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;getY()D"))
    private double slabbed$railFrameRead(double physicalY) {
        return MinecartRailFrame.toLogicalY(this.minecart, physicalY);
    }

    /** Write 1: the placement the collision sweep runs at — the top of the drawn profile. */
    @WrapOperation(method = MOVE_ALONG_TRACK, at = @At(value = "INVOKE", target = SET_POS, ordinal = 0))
    private void slabbed$placeAtTheDrawnTopForCollision(OldMinecartBehavior self, double x, double logicalY,
                                                        double z, Operation<Void> original) {
        Level level = this.minecart.level();
        BlockPos cell = MinecartRailFrame.railCellAt(level, x, logicalY, z);
        double seat = cell == null
                ? MinecartRailFrame.dyOf(this.minecart)
                : MinecartRailFrame.topSeatAt(level, cell, level.getBlockState(cell));
        MinecartRailFrame.bind(this.minecart, seat);
        original.call(self, x, logicalY + seat, z);
    }

    /** Writes 2 and 3: the ramp re-snaps, in the frame of the placement they follow. */
    @WrapOperation(method = MOVE_ALONG_TRACK, at = {
            @At(value = "INVOKE", target = SET_POS, ordinal = 1),
            @At(value = "INVOKE", target = SET_POS, ordinal = 2)})
    private void slabbed$railFrameWrite(OldMinecartBehavior self, double x, double logicalY, double z,
                                        Operation<Void> original) {
        original.call(self, x, MinecartRailFrame.toPhysicalY(this.minecart, logicalY), z);
    }

    /** Write 4: the final snap onto the rail — the drawn slope at the cart's new place. */
    @WrapOperation(method = MOVE_ALONG_TRACK, at = @At(value = "INVOKE", target = SET_POS, ordinal = 3))
    private void slabbed$snapOntoTheDrawnSlope(OldMinecartBehavior self, double x, double logicalY, double z,
                                               Operation<Void> original) {
        Level level = this.minecart.level();
        BlockPos cell = MinecartRailFrame.railCellAt(level, x, logicalY, z);
        double seat = cell == null
                ? MinecartRailFrame.dyOf(this.minecart)
                : MinecartRailFrame.seatAt(level, cell, level.getBlockState(cell), x, z);
        MinecartRailFrame.bind(this.minecart, seat);
        original.call(self, x, logicalY + seat, z);
    }
}
