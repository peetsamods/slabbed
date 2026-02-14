package com.slabbed.mixin;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SideShapeType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two-part mixin on {@link AbstractBlock.AbstractBlockState}:
 *
 * <ol>
 *   <li><b>isSideSolid</b> — makes bottom slabs report their UP face as
 *       solid for every {@link SideShapeType}, enabling placement.</li>
 *   <li><b>getOutlineShape</b> — shifts the outline (hit-box wireframe)
 *       down by 0.5 for blocks sitting above a bottom slab so the wireframe
 *       matches the model offset and interactions work at the visual
 *       position.</li>
 * </ol>
 *
 * <p><b>Note:</b> collision shapes are intentionally NOT offset. Offsetting
 * them causes the player to clip into full blocks when walking onto them
 * from the slab surface (the step-up from slab top to collision bottom
 * exceeds MC's 0.6 step height for full blocks).
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class SlabSupportStateMixin {

    // ── placement / survival support ──────────────────────────────────

    @Inject(method = "isSideSolid", at = @At("HEAD"), cancellable = true)
    private void slabbed$slabTopSolid(BlockView world, BlockPos pos, Direction direction, SideShapeType shapeType, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        boolean slabSupport = direction == Direction.UP && SlabSupport.isBottomSlab(self);
        if (!slabSupport) {
            return;
        }

        if (SlabSupport.DEBUG_GHOSTING) {
            World w = world instanceof World ww ? ww : null;
            BlockPos belowPos = pos.down();
            Slabbed.LOGGER.info("[SLABBED][GHOSTING] slab-top support accepted pos={} placing={} belowState={} slabSupport={} worldClass={} client={} neighborsNotified=false after-accept placement", pos, self.getBlock(),
                    world.getBlockState(belowPos), SlabSupport.isSupportingSlab(world, belowPos),
                    w != null ? w.getClass().getSimpleName() : world.getClass().getSimpleName(), w != null && w.isClient());
        }

        cir.setReturnValue(true);
    }

    @Inject(method = "isSideSolid", at = @At("HEAD"), cancellable = true)
    private void slabbed$ceilingSupport(BlockView world, BlockPos pos, Direction direction, SideShapeType shapeType, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (SlabSupport.isTopSlabUndersideSupport(self, direction)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isSideSolidFullSquare", at = @At("HEAD"), cancellable = true)
    private void slabbed$ceilingSolidFullSquare(BlockView world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (SlabSupport.isTopSlabUndersideSupport(self, direction)) {
            cir.setReturnValue(true);
        }
    }

    // ── outline (hit-box) offset ──────────────────────────────────────

    @Inject(method = "getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetOutline(BlockView world, BlockPos pos, ShapeContext ctx,
                                       CallbackInfoReturnable<VoxelShape> cir) {
        BlockState self = (BlockState) (Object) this;
        double yOff = SlabSupport.getYOffset(world, pos, self);
        if (yOff != 0.0) {
            cir.setReturnValue(cir.getReturnValue().offset(0.0, yOff, 0.0));
        }
    }
}
