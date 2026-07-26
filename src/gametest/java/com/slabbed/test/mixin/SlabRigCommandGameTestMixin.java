package com.slabbed.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.command.SlabRigCommand;
import com.slabbed.test.SlabRigGameTestSeams;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scopes GameTest probes around SlabRig's exact production placement attempt. */
@Mixin(SlabRigCommand.class)
public abstract class SlabRigCommandGameTestMixin {
    @WrapOperation(
            method = "placeViaDetailed",
            at = @At(value = "NEW",
                    target = "(Lnet/minecraft/world/level/ItemLike;)"
                            + "Lnet/minecraft/world/item/ItemStack;")
    )
    private static ItemStack slabbed$placementProbeItem(
            ItemLike itemLike,
            Operation<ItemStack> original,
            @Local(argsOnly = true) ServerLevel world,
            @Local(argsOnly = true) Player player,
            @Local(argsOnly = true, ordinal = 1) BlockPos target) {
        Item productionItem = (Item) itemLike;
        return original.call(
                SlabRigGameTestSeams.placementItem(world, player, target, productionItem));
    }

    @WrapOperation(
            method = "placeViaDetailed",
            at = @At(value = "INVOKE",
                    target = "Lcom/slabbed/util/SlabbedDiagnosticsBridge;withActionOrigin(Ljava/lang/String;Ljava/lang/Runnable;)V")
    )
    private static void slabbed$afterProductionProxyUse(
            String origin,
            Runnable action,
            Operation<Void> original,
            @Local(argsOnly = true) ServerLevel world,
            @Local(argsOnly = true) Player player,
            @Local(argsOnly = true, ordinal = 1) BlockPos target) {
        original.call(origin, action);
        SlabRigGameTestSeams.afterProductionProxyUse(world, player, target);
    }

    @Inject(method = "placeViaDetailed", at = @At("RETURN"))
    private static void slabbed$captureProductionAttempt(
            CallbackInfoReturnable<Object> cir,
            @Local(argsOnly = true) ServerLevel world,
            @Local(argsOnly = true) Player player,
            @Local(argsOnly = true, ordinal = 1) BlockPos target) {
        SlabRigGameTestSeams.captureProductionAttempt(
                world, player, target, cir.getReturnValue());
    }
}
