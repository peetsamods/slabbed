package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.util.MinecartRailFrame;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Rail snapping and slope tilt follow a cart down onto the rail it is drawn on (maintainer ruling,
 * 2026-09-06).
 *
 * <p>The default minecart's render state does its OWN rail-cell derivation from the lerped physical
 * render position, not through the cart's Y accessor: it feeds that position into
 * {@code getPos}/{@code getPosOffs}, which resolve a rail cell and return null when there is none.
 * For a cart seated below its rail those calls answer null, and the renderer's null guard then
 * drops the lateral rail snap AND the slope tilt entirely — the cart would sit at the right height
 * but off-centre through curves and flat on slopes.
 *
 * <p>INVARIANT: convert at the CALL SITES only. {@code getPos} and {@code getPosOffs} must stay
 * unconverted internally — the server solver already hands them a logical Y, and a conversion inside
 * them would double-apply the seat.
 *
 * <p>Null-safe in both directions: an unresolved probe stays null so the renderer's own guard and
 * its front/back fallbacks behave exactly as vanilla.
 */
@Mixin(AbstractMinecartRenderer.class)
public abstract class MinecartRendererRailFrameMixin {

    @WrapOperation(
            method = "oldExtractState(Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;"
                    + "Lnet/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior;"
                    + "Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;F)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior;"
                           + "getPos(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 slabbed$railSnapAtDrawnHeight(
            OldMinecartBehavior behavior, double x, double y, double z,
            Operation<Vec3> original,
            @Local(argsOnly = true) AbstractMinecart cart) {
        double dy = MinecartRailFrame.dyOf(cart);
        if (dy == 0.0d) {
            return original.call(behavior, x, y, z);
        }
        return MinecartRailFrame.toPhysical(cart, original.call(behavior, x, y - dy, z));
    }

    /** Two call sites — the front and back slope probes. One handler covers both; no ordinal. */
    @WrapOperation(
            method = "oldExtractState(Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;"
                    + "Lnet/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior;"
                    + "Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;F)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior;"
                           + "getPosOffs(DDDD)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 slabbed$railProbeAtDrawnHeight(
            OldMinecartBehavior behavior, double x, double y, double z, double offs,
            Operation<Vec3> original,
            @Local(argsOnly = true) AbstractMinecart cart) {
        double dy = MinecartRailFrame.dyOf(cart);
        if (dy == 0.0d) {
            return original.call(behavior, x, y, z, offs);
        }
        return MinecartRailFrame.toPhysical(cart, original.call(behavior, x, y - dy, z, offs));
    }
}
