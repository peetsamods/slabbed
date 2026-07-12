package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.command.SlabRigHangingDirectEntityGate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Captures only the vanilla painting-item spawn performed by {@link Painting#dropItem}. */
@Mixin(Painting.class)
public abstract class PaintingRigDropCaptureMixin {

    @WrapOperation(
            method = "dropItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/decoration/painting/Painting;"
                            + "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;"
                            + "Lnet/minecraft/world/level/ItemLike;)"
                            + "Lnet/minecraft/world/entity/item/ItemEntity;"
            )
    )
    private ItemEntity slabbed$captureDirectRigPaintingDrop(
            Painting painting, ServerLevel level, ItemLike item,
            Operation<ItemEntity> original) {
        return SlabRigHangingDirectEntityGate.capturePaintingDrop(
                painting, level, () -> original.call(painting, level, item));
    }
}
