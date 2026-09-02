package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item frames (and glow frames, which inherit this box math) hang on the DRAWN face of their
 * support (maintainer ruling, 2026-09-01: WYSIWYG applies to hung decorations). A frame's
 * position is re-derived from its grid cell on every recalculation, so without this a frame
 * aimed at a lowered block's visible face hung a full block above it.
 *
 * <p>Applied at TAIL of the vanilla derivation and recomputed fresh each call, so the shift
 * never compounds. Both logical sides re-derive independently from the same synced height
 * facts. {@code survives()} keeps reading the support's grid cell, which is where the support
 * block actually is regardless of how low it draws — popping law is untouched.
 */
@Mixin(ItemFrame.class)
public abstract class ItemFrameWysiwygMixin extends HangingEntity {

    protected ItemFrameWysiwygMixin(EntityType<? extends HangingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "recalculateBoundingBox", at = @At("TAIL"))
    private void slabbed$hangOnDrawnFace(CallbackInfo ci) {
        if (this.pos == null || this.direction == null || this.level() == null) {
            return;
        }
        BlockPos supportPos = this.pos.relative(this.direction.getOpposite());
        BlockState support = this.level().getBlockState(supportPos);
        double dy = SlabSupport.getYOffset(this.level(), supportPos, support);
        if (!Double.isFinite(dy) || Math.abs(dy) < 1.0e-6d) {
            return;
        }
        this.setPosRaw(this.getX(), this.getY() + dy, this.getZ());
        this.setBoundingBox(this.getBoundingBox().move(0.0d, dy, 0.0d));
    }
}
