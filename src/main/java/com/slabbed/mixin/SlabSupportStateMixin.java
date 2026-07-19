package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SideShapeType;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
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

    /**
     * Comfort selection shape for lowered floor torches.
     *
     * <p>Vanilla {@code AbstractTorchBlock.SHAPE} is a 4-pixel-wide post 10 pixels tall
     * (X/Z 6–10/16, Y 0–10/16). With a negative dy offset the visible torch sits
     * outside its native voxel range. Vanilla DDA only tests a voxel's outline shape
     * when the ray enters that voxel, so rays aimed at the visible torch from natural
     * side angles either (a) miss entirely, or (b) hit the slab below.
     *
     * <p>The fix is two-sided:
     * <ul>
     *   <li><b>Torch outline/raycast</b>: replaced with this 4-pixel-wide post that
     *       fills the entire native voxel (Y 0–16). After the negative dy offset is
     *       applied, the comfort shape spans world Y=torchPos.y+dy to torchPos.y.
     *       This is what the wireframe renderer draws after the rescue retarget.</li>
     *   <li><b>Slab overlay</b>: when a slab has a lowered floor torch directly above,
     *       this same shape is unioned into the slab's outline (in the slab's voxel
     *       frame, translated by 1+torchDy) so vanilla DDA produces a slab hit at the
     *       comfort area. The existing rescue mixin then retargets that slab hit to
     *       the torch above. Without this overlay DDA never enters the torch voxel
     *       and rescue cannot fire.</li>
     * </ul>
     *
     * <p>The comfort area is contained within the same 4-pixel X/Z post column as
     * vanilla's torch SHAPE — the visual triad (model, outline, raycast) all stay
     * aligned, and the click target never extends outside the torch's natural column.
     * Vanilla itself already gives torches a selection larger than the visual sprite
     * (the flame extends above the post outline), so this comfort patch follows the
     * same player-trust precedent.
     */
    private static final VoxelShape SLABBED$COMFORT_TORCH_SHAPE =
            Block.createCuboidShape(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

    /**
     * Returns true iff {@code state} is a floor torch (TorchBlock, not WallTorchBlock)
     * and the resolved dy is negative. WallTorchBlock is excluded explicitly: this
     * comfort path only addresses the floor-torch tiny-shape selection issue.
     */
    private static boolean slabbed$isLoweredFloorTorch(BlockState state, double yOff) {
        if (yOff >= 0.0) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof TorchBlock && !(block instanceof WallTorchBlock);
    }

    // ── placement / survival support ──────────────────────────────────

    @Inject(method = "isSideSolid", at = @At("HEAD"), cancellable = true)
    private void slabbed$slabTopSolid(BlockView world, BlockPos pos, Direction direction, SideShapeType shapeType, CallbackInfoReturnable<Boolean> cir) {
        // Must match slabbed$slabTopSolidFullSquare's gate (canTreatAsSolidTopFace), not the
        // narrower isBottomSlab: AbstractRedstoneGateBlock.canPlaceAbove (repeater/comparator)
        // calls isSideSolid(UP, RIGID) -- NOT isSideSolidFullSquare -- so isBottomSlab's exclusion
        // of Terrain-Slabs-owned states (via isSupportingSlab -> shouldSkipSlabSupport) silently
        // made TS bottom slabs unable to host redstone gates, even though the FULL_SQUARE sibling
        // below already correctly treated them as solid support.
        if (direction == Direction.UP && SlabSupport.canTreatAsSolidTopFace(world, pos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isSideSolid", at = @At("HEAD"), cancellable = true)
    private void slabbed$ceilingSupport(BlockView world, BlockPos pos, Direction direction, SideShapeType shapeType, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (SlabSupport.isTopSlabUndersideSupport(self, direction)
                || (shapeType == SideShapeType.CENTER
                && SlabSupport.isBottomLikeSlabUndersideHookSupport(self, direction))) {
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

    @Inject(method = "isSideSolidFullSquare", at = @At("HEAD"), cancellable = true)
    private void slabbed$slabTopSolidFullSquare(BlockView world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (direction == Direction.UP && SlabSupport.canTreatAsSolidTopFace(world, pos)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Preserve vanilla's bottom-slab mob-proofing after the full-square support
     * override above has done its separate placement/survival job.
     *
     * <p>Running at RETURN makes this strictly subtractive: custom block predicates,
     * luminance checks, and every already-false result stay false.
     */
    @Inject(method = "allowsSpawning", at = @At("RETURN"), cancellable = true)
    private void slabbed$bottomSlabSpawnProofing(
            BlockView world,
            BlockPos pos,
            EntityType<?> entityType,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockState self = (BlockState) (Object) this;
        if (cir.getReturnValue() && SlabSupport.isSpawnProofBottomLikeSurface(self)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getRaycastShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetRaycast(BlockView world, BlockPos pos,
                                       CallbackInfoReturnable<VoxelShape> cir) {
        BlockState self = (BlockState) (Object) this;

        // Fences/walls/panes now render lowered on a vanilla slab too (GH #21 model fix
        // in OffsetBlockStateModel.emitQuads), so their outline AND raycast shape MUST
        // follow getVisualYOffset — the old connection-block bail-out here was the mirror
        // of the model dy=0 suppression that fix removed, and leaving it made the fence
        // raycast/outline a phantom half a block above the visually-lowered post.
        double yOff = SlabSupport.getVisualYOffset(world, pos, self);
        if (yOff != 0.0) {
            VoxelShape shape = cir.getReturnValue();
            if (slabbed$isLoweredFloorTorch(self, yOff)) {
                shape = SLABBED$COMFORT_TORCH_SHAPE;
            }
            cir.setReturnValue(shape.offset(0.0, yOff, 0.0));
        }
    }

    // ── outline (hit-box) offset ──────────────────────────────────────

    @Inject(method = "getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetOutline(BlockView world, BlockPos pos, ShapeContext ctx,
                                       CallbackInfoReturnable<VoxelShape> cir) {
        BlockState self = (BlockState) (Object) this;

        // Avoid carpet recursion: carpets have their own outline mixin and should not be offset here.
        Block block = self.getBlock();
        if (block instanceof net.minecraft.block.CarpetBlock || block instanceof net.minecraft.block.PaleMossCarpetBlock) {
            return;
        }

        double yOff = SlabSupport.getVisualYOffset(world, pos, self);
        if (yOff == 0.0) {
            return;
        }

        VoxelShape shape = cir.getReturnValue();
        // The lowered floor torch keeps a full-height post as its OWN outline so the thin
        // sprite stays hittable after the dy offset. The offset-aware nearest-hit raycast
        // tests this shape directly, so no slab-side comfort union is needed.
        if (slabbed$isLoweredFloorTorch(self, yOff)) {
            shape = SLABBED$COMFORT_TORCH_SHAPE;
        }
        cir.setReturnValue(shape.offset(0.0, yOff, 0.0));
    }
}
