package com.slabbed.mixin;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.BlockAttachedEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Popping law is untouched: a seated painting judges the GRID cells behind it, where its wall
 * actually is, not the cells behind its seated box. Vanilla's attachment check derives the cells
 * from the bounding box, so the box is unseated for the check and restored after. Item frames
 * override the check on their own attachment cell and never reach this.
 */
@Mixin(AbstractDecorationEntity.class)
public abstract class HangingSurvivalOnGridMixin extends BlockAttachedEntity {

    @Unique
    private Box slabbed$seatedBoxDuringSurvival;

    protected HangingSurvivalOnGridMixin(EntityType<? extends BlockAttachedEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "canStayAttached()Z", at = @At("HEAD"))
    private void slabbed$judgeOnGridCells(CallbackInfoReturnable<Boolean> cir) {
        if (!(this instanceof HangingSeatDyHolder holder) || !holder.slabbed$hasHangSeat()) {
            return;
        }
        double dy = holder.slabbed$hangSeatDy();
        if (Math.abs(dy) >= 1.0e-6d) {
            slabbed$seatedBoxDuringSurvival = getBoundingBox();
            setBoundingBox(slabbed$seatedBoxDuringSurvival.offset(0.0d, -dy, 0.0d));
        }
    }

    @Inject(method = "canStayAttached()Z", at = @At("RETURN"))
    private void slabbed$restoreSeatedBox(CallbackInfoReturnable<Boolean> cir) {
        if (slabbed$seatedBoxDuringSurvival != null) {
            setBoundingBox(slabbed$seatedBoxDuringSurvival);
            slabbed$seatedBoxDuringSurvival = null;
        }
    }
}
