package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SideShapeType;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
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
     *       matches the torch body (Y 0–10/16). After the negative dy offset is
     *       applied, the comfort shape spans world Y=torchPos.y+dy to
     *       torchPos.y+dy+10/16. This is what the wireframe renderer draws after
     *       the rescue retarget.</li>
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
            Block.createCuboidShape(6.0, 0.0, 6.0, 10.0, 10.0, 10.0);

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

    private static boolean slabbed$isLoweredBeta35FloorTopContactObject(BlockState state, double yOff) {
        return yOff < 0.0 && state != null && (state.isOf(Blocks.CANDLE) || state.isOf(Blocks.FLOWER_POT));
    }

    private static boolean slabbed$isLoweredBeta35OakTrapdoorContactObject(BlockState state, double yOff) {
        return yOff < 0.0
                && state != null
                && state.isOf(Blocks.OAK_TRAPDOOR)
                && state.contains(Properties.BLOCK_HALF)
                && state.get(Properties.BLOCK_HALF) == BlockHalf.BOTTOM;
    }

    private static boolean slabbed$isTopHalfTrapdoor(BlockState state) {
        return state != null
                && state.getBlock() instanceof TrapdoorBlock
                && state.contains(Properties.BLOCK_HALF)
                && state.get(Properties.BLOCK_HALF) == BlockHalf.TOP;
    }

    private static boolean slabbed$coherentNonEmptyTriadBasis(VoxelShape outline, VoxelShape collision) {
        if (outline == null || collision == null || outline.isEmpty() || collision.isEmpty()) {
            return false;
        }
        return Math.abs(outline.getBoundingBox().minX - collision.getBoundingBox().minX) <= 1.0e-6
                && Math.abs(outline.getBoundingBox().minY - collision.getBoundingBox().minY) <= 1.0e-6
                && Math.abs(outline.getBoundingBox().minZ - collision.getBoundingBox().minZ) <= 1.0e-6
                && Math.abs(outline.getBoundingBox().maxX - collision.getBoundingBox().maxX) <= 1.0e-6
                && Math.abs(outline.getBoundingBox().maxY - collision.getBoundingBox().maxY) <= 1.0e-6
                && Math.abs(outline.getBoundingBox().maxZ - collision.getBoundingBox().maxZ) <= 1.0e-6;
    }

    private static boolean slabbed$isLoweredBeta35RegularDoorContactObject(BlockState state, double yOff) {
        return yOff < 0.0
                && state != null
                && state.getBlock() instanceof DoorBlock
                && state.contains(Properties.DOUBLE_BLOCK_HALF)
                && (state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                        || state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER);
    }

    private static boolean slabbed$isLoweredBeta35StandingOakSignContactObject(BlockState state, double yOff) {
        return yOff < 0.0
                && state != null
                && state.isOf(Blocks.OAK_SIGN);
    }

    private static boolean slabbed$isLoweredBeta35FenceWallVariantContactObject(BlockState state, double yOff) {
        return yOff < 0.0 && SlabSupport.isBeta35FenceWallVariantContactObject(state);
    }

    private static boolean slabbed$isLoweredBeta35FenceGateContactObject(BlockState state, double yOff) {
        return yOff < 0.0 && SlabSupport.isBeta35FenceGateContactObject(state);
    }

    private static boolean slabbed$isBeta35SpecialFullblockRaycastFallbackObject(BlockState state) {
        return state != null
                && (state.isOf(Blocks.CHEST)
                        || state.isOf(Blocks.BARREL)
                        || state.isOf(Blocks.ENCHANTING_TABLE)
                        || state.isOf(Blocks.STONECUTTER)
                        || state.isOf(Blocks.ANVIL)
                        || state.isOf(Blocks.GRINDSTONE));
    }

    private static boolean slabbed$needsLoweredFullBlockRaycastBasis(
            BlockView world,
            BlockPos pos,
            BlockState state,
            double yOff,
            VoxelShape nativeRaycast
    ) {
        if (yOff >= 0.0d || nativeRaycast == null || !nativeRaycast.isEmpty()) {
            return false;
        }
        if (SlabSupport.isSupportingSlab(state) || SlabSupport.isThinTopLayer(state)) {
            return false;
        }
        return SlabAnchorAttachment.isAnchored(world, pos)
                || state.isOf(Blocks.FURNACE);
    }

    /**
     * Builds the torch comfort overlay in {@code slabPos}'s voxel frame, or returns
     * {@code null} if the block above {@code slabPos} is not a lowered floor torch.
     *
     * <p>Torch comfort shape is voxel-relative Y=0–1 in the torch's frame. Translated
     * to the slab's frame the comfort shape sits at Y=(1+torchDy) to (2+torchDy),
     * so we offset {@link #SLABBED$COMFORT_TORCH_SHAPE} by {@code 1.0 + torchDy}.
     */
    private static VoxelShape slabbed$slabTorchComfortOverlay(BlockView world, BlockPos slabPos) {
        if (world == null || slabPos == null) {
            return null;
        }
        BlockPos abovePos = slabPos.up();
        BlockState above = world.getBlockState(abovePos);
        Block aboveBlock = above.getBlock();
        if (!(aboveBlock instanceof TorchBlock) || aboveBlock instanceof WallTorchBlock) {
            return null;
        }
        double torchDy = SlabSupport.getYOffset(world, abovePos, above);
        if (torchDy >= 0.0) {
            return null;
        }
        return SLABBED$COMFORT_TORCH_SHAPE.offset(0.0, 1.0 + torchDy, 0.0);
    }

    // ── placement / survival support ──────────────────────────────────

    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void slabbed$flowerPotFloorTopSurvival(
            WorldView world,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockState self = (BlockState) (Object) this;
        if (!self.isOf(Blocks.FLOWER_POT)) {
            return;
        }
        BlockPos below = pos.down();
        BlockState belowState = world.getBlockState(below);
        cir.setReturnValue(SlabSupport.canTreatAsSolidTopFace(world, below)
                || belowState.isSideSolidFullSquare(world, below, Direction.UP));
    }

    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void slabbed$lanternLoweredSlabUndersideSurvival(
            WorldView world,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockState self = (BlockState) (Object) this;
        if ((!self.isOf(Blocks.LANTERN) && !self.isOf(Blocks.SOUL_LANTERN))
                || !self.contains(Properties.HANGING)
                || !self.get(Properties.HANGING)) {
            return;
        }
        BlockPos supportPos = pos.up();
        BlockState supportState = world.getBlockState(supportPos);
        if (supportState.getBlock() instanceof SlabBlock
                && supportState.contains(SlabBlock.TYPE)
                && supportState.getFluidState().isEmpty()
                && (supportState.get(SlabBlock.TYPE) == SlabType.TOP
                || supportState.get(SlabBlock.TYPE) == SlabType.BOTTOM
                || supportState.get(SlabBlock.TYPE) == SlabType.DOUBLE)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isSideSolid", at = @At("HEAD"), cancellable = true)
    private void slabbed$slabTopSolid(BlockView world, BlockPos pos, Direction direction, SideShapeType shapeType, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (direction == Direction.UP && SlabSupport.isBottomSlab(self)) {
            cir.setReturnValue(true);
        }
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

    @Inject(method = "isSideSolidFullSquare", at = @At("HEAD"), cancellable = true)
    private void slabbed$slabTopSolidFullSquare(BlockView world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (direction == Direction.UP && SlabSupport.isBottomSlab(self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getRaycastShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetRaycast(BlockView world, BlockPos pos,
                                       CallbackInfoReturnable<VoxelShape> cir) {
        BlockState self = (BlockState) (Object) this;
        VoxelShape shape = cir.getReturnValue();
        if (slabbed$isTopHalfTrapdoor(self) && (shape == null || shape.isEmpty())) {
            VoxelShape outline = self.getOutlineShape(world, pos, ShapeContext.absent());
            VoxelShape collision = self.getCollisionShape(world, pos, ShapeContext.absent());
            if (slabbed$coherentNonEmptyTriadBasis(outline, collision)) {
                cir.setReturnValue(outline);
                return;
            }
        }

        double yOff = SlabSupport.getYOffset(world, pos, self);
        if (yOff != 0.0) {
            if (slabbed$isLoweredFloorTorch(self, yOff)) {
                shape = SLABBED$COMFORT_TORCH_SHAPE;
            } else if (slabbed$isLoweredBeta35FloorTopContactObject(self, yOff) && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getOutlineShape(world, pos, ShapeContext.absent()));
                return;
            } else if (slabbed$isLoweredBeta35OakTrapdoorContactObject(self, yOff) && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getOutlineShape(world, pos, ShapeContext.absent()));
                return;
            } else if (slabbed$isLoweredBeta35RegularDoorContactObject(self, yOff)
                    && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getOutlineShape(world, pos, ShapeContext.absent()));
                return;
            } else if (slabbed$isLoweredBeta35StandingOakSignContactObject(self, yOff)
                    && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getOutlineShape(world, pos, ShapeContext.absent()));
                return;
            } else if (yOff < 0.0
                    && slabbed$isBeta35SpecialFullblockRaycastFallbackObject(self)
                    && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getOutlineShape(world, pos, ShapeContext.absent()));
                return;
            } else if (slabbed$isLoweredBeta35FenceWallVariantContactObject(self, yOff)
                    && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getOutlineShape(world, pos, ShapeContext.absent()));
                return;
            } else if (slabbed$isLoweredBeta35FenceGateContactObject(self, yOff)
                    && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getOutlineShape(world, pos, ShapeContext.absent()));
                return;
            } else if (slabbed$needsLoweredFullBlockRaycastBasis(world, pos, self, yOff, shape)) {
                shape = VoxelShapes.fullCube();
            }
            cir.setReturnValue(shape.offset(0.0, yOff, 0.0));
        }
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetOakFenceAndGrindstoneCollision(BlockView world, BlockPos pos, ShapeContext ctx,
                                                              CallbackInfoReturnable<VoxelShape> cir) {
        BlockState self = (BlockState) (Object) this;
        if (!SlabSupport.isBeta35FenceWallVariantContactObject(self)
                && !SlabSupport.isBeta35FenceGateContactObject(self)
                && !self.isOf(Blocks.GRINDSTONE)) {
            return;
        }
        double yOff = SlabSupport.getYOffset(world, pos, self);
        if (yOff != 0.0) {
            cir.setReturnValue(cir.getReturnValue().offset(0.0, yOff, 0.0));
        }
    }

    // ── outline (hit-box) offset ──────────────────────────────────────

    @Inject(method = "getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetOutline(BlockView world, BlockPos pos, ShapeContext ctx,
                                       CallbackInfoReturnable<VoxelShape> cir) {
        // In 1.21.1, getOutlineShape is called by light/opacity workers (Worker-Main, ForkJoinPool)
        // during spawn-prep. SlabSupport.getYOffset accesses chunk/anchor state and can block via
        // CompletableFuture.join, deadlocking the server. Return vanilla shape on those threads.
        if (slabbed$isUnsafeAsyncShapeContext()) {
            return;
        }

        BlockState self = (BlockState) (Object) this;

        // Avoid carpet recursion: carpets have their own outline mixin and should not be offset here.
        Block block = self.getBlock();
        if (block instanceof net.minecraft.block.CarpetBlock || isPaleMossCarpet(block)) {
            return;
        }

        VoxelShape shape = cir.getReturnValue();
        boolean changed = false;

        double yOff = SlabSupport.getYOffset(world, pos, self);
        if (yOff != 0.0) {
            if (slabbed$isLoweredFloorTorch(self, yOff)) {
                shape = SLABBED$COMFORT_TORCH_SHAPE;
            }
            shape = shape.offset(0.0, yOff, 0.0);
            changed = true;
        }

        // Slab + lowered floor torch comfort overlay: union the torch comfort column
        // into the slab's outline so vanilla DDA produces a slab hit; the existing
        // rescue mixin then retargets that hit to the torch above.
        if (block instanceof SlabBlock) {
            VoxelShape overlay = slabbed$slabTorchComfortOverlay(world, pos);
            if (overlay != null) {
                shape = VoxelShapes.union(shape, overlay);
                changed = true;
            }
        }

        if (changed) {
            cir.setReturnValue(shape);
        }
    }

    private static boolean slabbed$isUnsafeAsyncShapeContext() {
        String name = Thread.currentThread().getName();
        return name.startsWith("Worker-Main") || name.contains("ForkJoinPool");
    }

    private static boolean isPaleMossCarpet(Block block) {
        return block == Registries.BLOCK.get(Identifier.of("minecraft", "pale_moss_carpet"));
    }
}
