package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item frames (and glow frames, which inherit this box math) hang on the DRAWN face of their
 * support (maintainer ruling, 2026-09-01: WYSIWYG applies to hung decorations). A frame's
 * geometry is re-derived from its grid cell on every recalculation, so without this a frame
 * aimed at a lowered block's visible face hung a full block above it.
 *
 * <p>Only the BOUNDING BOX is shifted — the interaction and projectile surface — never the
 * entity's real position. The position feeds {@code BlockPos.containing} in
 * {@code BlockAttachedEntity.setPos} and in every position packet the server ever sends, so
 * moving it by a full block (a −1.0 face is legal under the ruling) silently re-derives the
 * frame's grid cell one block low, and {@code survives()} then judges the wrong support. The
 * DRAWN frame is shifted by the render layer instead ({@code ItemFrameDrawnFaceRenderMixin}),
 * which reads the same support cell; the two must stay in step.
 *
 * <p>On this Minecraft line {@code recalculateBoundingBox} is final and derives the entity's
 * xyz FROM the box center before storing the box, so the shift must land AFTER that
 * derivation — shifting inside {@code calculateBoundingBox} moves the position with the box
 * and reopens the exact grid-cell corruption above (caught by the lifecycle row's
 * positions-must-not-differ arm on first port). The mixin therefore targets the parent's
 * final method at TAIL, bounded to item frames; paintings are deferred by the same ruling.
 *
 * <p>The support cell may sit in a neighbouring chunk (wall frames on a chunk border), and
 * this derivation runs during entity deserialization — reading an unloaded chunk there forces
 * a synchronous chunk load on the server and answers a wrong height from an empty chunk on the
 * client. An unloaded support skips the shift; the next recalculation re-derives.
 *
 * <p>Recomputed fresh each call, so the shift never compounds. {@code survives()} keeps
 * reading the support's grid cell through {@code calculateSupportBox}, which is where the
 * support block actually is regardless of how low it draws — popping law is untouched.
 */
@Mixin(HangingEntity.class)
public abstract class ItemFrameWysiwygMixin extends BlockAttachedEntity {

    @Shadow
    protected Direction direction;

    protected ItemFrameWysiwygMixin(EntityType<? extends BlockAttachedEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "recalculateBoundingBox", at = @At("TAIL"))
    private void slabbed$hangBoxOnDrawnFace(CallbackInfo ci) {
        if (!((Object) this instanceof ItemFrame) || this.pos == null || this.direction == null
                || this.level() == null) {
            return;
        }
        BlockPos supportPos = this.pos.relative(this.direction.getOpposite());
        if (!this.level().hasChunkAt(supportPos)) {
            return;
        }
        BlockState support = this.level().getBlockState(supportPos);
        double dy = SlabSupport.getYOffset(this.level(), supportPos, support);
        if (!Double.isFinite(dy) || Math.abs(dy) < 1.0e-6d) {
            return;
        }
        this.setBoundingBox(this.getBoundingBox().move(0.0d, dy, 0.0d));
    }
}
