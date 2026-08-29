package com.slabbed.mixin;

import com.slabbed.util.SlabbedServerHitValidation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Moves vanilla's packet-distance validation center to the selected owner's visible center.
 * Vanilla remains the sole owner of placement, inventory consumption, events, and animation.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerInteractBlockHitToleranceMixin {
    @Shadow @Final public ServerPlayer player;

    @Redirect(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;atCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 slabbed$shiftSelectedOwnerValidationCenter(
            Vec3i blockPos,
            ServerboundUseItemOnPacket packet
    ) {
        Vec3 vanillaCenter = Vec3.atCenterOf(blockPos);
        if (!(blockPos instanceof BlockPos ownerPos) || player == null || packet == null) {
            return vanillaCenter;
        }
        return SlabbedServerHitValidation.validationCenter(
                player.level(), ownerPos, packet.getHitResult(), vanillaCenter);
    }
}
