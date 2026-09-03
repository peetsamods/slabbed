package com.slabbed.mixin.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * An entity standing in the open air above a lowered block is lit like open air (WYSIWYG lighting;
 * maintainer ruling, 2026-09-03).
 *
 * <p>Vanilla samples an entity's light at the cell containing its light-probe point. A lowered block
 * keeps its state in the grid cell while its body is drawn below, so a cushion, a dropped item or a
 * small mob resting on the drawn top has its probe inside an opaque cell and renders black, even
 * though it visibly sits in daylight. While the probe point lies above the drawn top of the block in
 * its cell, sample the cell above instead; bounded by the lowering envelope. An unlowered block never
 * satisfies the test, so vanilla lighting is untouched everywhere else.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityLightProbeLoweredBandMixin {

    /** The deepest a placed block may sit below its cell (maintainer ruling, 2026-08-24), in cells. */
    private static final int LOWERING_ENVELOPE_CELLS = 3;

    @Redirect(
            method = "getPackedLightCoords",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;containing(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;"
            )
    )
    private BlockPos slabbed$probeAboveTheDrawnTop(Position probe, Entity entity, float partialTick) {
        BlockPos pos = BlockPos.containing(probe);
        Level level = entity.level();
        if (level == null) {
            return pos;
        }
        for (int step = 0; step < LOWERING_ENVELOPE_CELLS; step++) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return pos;
            }
            double dy = SlabSupport.getYOffset(level, pos, state);
            if (dy >= 0.0 || probe.y() < pos.getY() + 1.0 + dy) {
                return pos;
            }
            pos = pos.above();
        }
        return pos;
    }
}
