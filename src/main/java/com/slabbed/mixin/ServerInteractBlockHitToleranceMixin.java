package com.slabbed.mixin;

import com.slabbed.util.SlabbedServerHitValidation;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minimal server hit-tolerance lane for lowered owners.
 *
 * <p>{@code onPlayerInteractBlock} rejects a use packet when any axis of
 * {@code |hit - Vec3d.ofCenter(pos)|} reaches {@code 1.0000001}. A Slabbed-lowered
 * owner's legal hit band sits below the vanilla cell (down to {@code 1.5} below the
 * center for a {@code -1.0} compound owner), so honest clicks on the visible body are
 * silently swallowed. This redirect substitutes the visually-correct center via
 * {@link SlabbedServerHitValidation#shiftedValidationCenter}; vanilla's own tolerance
 * test then runs unchanged against it. Non-lowered targets get the vanilla center —
 * no behavior change.
 *
 * <p>Kill switch: {@code -Dslabbed.serverHitTolerance=false} restores the plain
 * vanilla center.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerInteractBlockHitToleranceMixin {

    private static final boolean SLABBED_SERVER_HIT_TOLERANCE_ENABLED =
            Boolean.parseBoolean(System.getProperty("slabbed.serverHitTolerance", "true"));

    @Shadow public ServerPlayerEntity player;

    @Redirect(
            method = "onPlayerInteractBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Vec3d;ofCenter(Lnet/minecraft/util/math/Vec3i;)Lnet/minecraft/util/math/Vec3d;")
    )
    private Vec3d slabbed$shiftValidationCenterForLoweredOwner(Vec3i pos) {
        if (!SLABBED_SERVER_HIT_TOLERANCE_ENABLED
                || this.player == null
                || !(pos instanceof BlockPos blockPos)) {
            return Vec3d.ofCenter(pos);
        }
        ServerWorld world = this.player.getEntityWorld();
        if (world == null) {
            return Vec3d.ofCenter(pos);
        }
        return SlabbedServerHitValidation.shiftedValidationCenter(world, blockPos);
    }
}
