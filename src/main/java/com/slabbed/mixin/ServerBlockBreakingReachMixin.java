package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Breaking reach follows the drawn owner while retaining vanilla's distance and permission gates. */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerBlockBreakingReachMixin {
    @WrapOperation(method = "handleBlockBreakAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;isWithinBlockInteractionRange(Lnet/minecraft/core/BlockPos;D)Z"))
    private boolean slabbed$visibleBreakingReach(ServerPlayer player, BlockPos pos, double margin,
                                                Operation<Boolean> original) {
        var level = player.level();
        if (!level.hasChunkAt(pos)) return original.call(player, pos, margin);
        double dy = SlabSupport.getYOffset(level, pos, level.getBlockState(pos));
        if (!Double.isFinite(dy) || dy >= 0) return original.call(player, pos, margin);
        double reach = player.blockInteractionRange() + margin;
        return new AABB(pos).move(0, dy, 0).distanceToSqr(player.getEyePosition()) < reach * reach;
    }
}
