package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two-part mixin on {@link BlockBehaviour.AbstractBlockState}:
 *
 * <ol>
 *   <li><b>isSideSolid</b> — makes bottom slabs report their UP face as
 *       solid for every {@link SupportType}, enabling placement.</li>
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
@Mixin(BlockBehaviour.BlockStateBase.class)
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
            Block.box(6.0, 0.0, 6.0, 10.0, 10.0, 10.0);

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
        return yOff < 0.0 && state != null && (state.is(Blocks.CANDLE) || state.is(Blocks.FLOWER_POT));
    }

    private static boolean slabbed$isLoweredBeta35OakTrapdoorContactObject(BlockState state, double yOff) {
        return yOff < 0.0
                && state != null
                && state.is(Blocks.OAK_TRAPDOOR)
                && state.hasProperty(BlockStateProperties.HALF)
                && state.getValue(BlockStateProperties.HALF) == Half.BOTTOM;
    }

    private static boolean slabbed$isLoweredBeta35RegularDoorContactObject(BlockState state, double yOff) {
        return yOff < 0.0
                && state != null
                && state.getBlock() instanceof DoorBlock
                && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && (state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                        || state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER);
    }

    private static boolean slabbed$isLoweredBeta35StandingOakSignContactObject(BlockState state, double yOff) {
        return yOff < 0.0
                && state != null
                && state.is(Blocks.OAK_SIGN);
    }

    private static boolean slabbed$isLoweredBeta35FenceWallVariantContactObject(BlockState state, double yOff) {
        return yOff < 0.0 && SlabSupport.isBeta35FenceWallVariantContactObject(state);
    }

    private static boolean slabbed$isLoweredBeta35FenceGateContactObject(BlockState state, double yOff) {
        return yOff < 0.0 && SlabSupport.isBeta35FenceGateContactObject(state);
    }

    private static boolean slabbed$isBeta35SpecialFullblockRaycastFallbackObject(BlockState state) {
        return state != null
                && (state.is(Blocks.CHEST)
                        || state.is(Blocks.BARREL)
                        || state.is(Blocks.ENCHANTING_TABLE)
                        || state.is(Blocks.STONECUTTER)
                        || state.is(Blocks.ANVIL)
                        || state.is(Blocks.GRINDSTONE));
    }

    private static boolean slabbed$needsLoweredFullBlockRaycastBasis(
            BlockGetter world,
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
                || state.is(Blocks.FURNACE);
    }

    /**
     * Builds the torch comfort overlay in {@code slabPos}'s voxel frame, or returns
     * {@code null} if the block above {@code slabPos} is not a lowered floor torch.
     *
     * <p>Torch comfort shape is voxel-relative Y=0–1 in the torch's frame. Translated
     * to the slab's frame the comfort shape sits at Y=(1+torchDy) to (2+torchDy),
     * so we offset {@link #SLABBED$COMFORT_TORCH_SHAPE} by {@code 1.0 + torchDy}.
     */
    private static VoxelShape slabbed$slabTorchComfortOverlay(BlockGetter world, BlockPos slabPos) {
        if (world == null || slabPos == null) {
            return null;
        }
        BlockPos abovePos = slabPos.above();
        BlockState above = world.getBlockState(abovePos);
        Block aboveBlock = above.getBlock();
        if (!(aboveBlock instanceof TorchBlock) || aboveBlock instanceof WallTorchBlock) {
            return null;
        }
        double torchDy = SlabSupport.getYOffset(world, abovePos, above);
        if (torchDy >= 0.0) {
            return null;
        }
        return SLABBED$COMFORT_TORCH_SHAPE.move(0.0, 1.0 + torchDy, 0.0);
    }

    // ── placement / survival support ──────────────────────────────────

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void slabbed$flowerPotFloorTopSurvival(
            LevelReader world,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockState self = (BlockState) (Object) this;
        if (!self.is(Blocks.FLOWER_POT)) {
            return;
        }
        BlockPos below = pos.below();
        BlockState belowState = world.getBlockState(below);
        cir.setReturnValue(SlabSupport.canTreatAsSolidTopFace(world, below)
                || belowState.isFaceSturdy(world, below, Direction.UP));
    }

    @Inject(method = "isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/SupportType;)Z", at = @At("HEAD"), cancellable = true)
    private void slabbed$slabTopSolid(BlockGetter world, BlockPos pos, Direction direction, SupportType shapeType, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (direction == Direction.UP && SlabSupport.isBottomSlab(self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/SupportType;)Z", at = @At("HEAD"), cancellable = true)
    private void slabbed$ceilingSupport(BlockGetter world, BlockPos pos, Direction direction, SupportType shapeType, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (SlabSupport.isTopSlabUndersideSupport(self, direction)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void slabbed$ceilingSolidFullSquare(BlockGetter world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (SlabSupport.isTopSlabUndersideSupport(self, direction)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void slabbed$slabTopSolidFullSquare(BlockGetter world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (direction == Direction.UP && SlabSupport.isBottomSlab(self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getInteractionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetRaycast(BlockGetter world, BlockPos pos,
                                       CallbackInfoReturnable<VoxelShape> cir) {
        BlockState self = (BlockState) (Object) this;

        double yOff = SlabSupport.getYOffset(world, pos, self);
        if (yOff != 0.0) {
            VoxelShape shape = cir.getReturnValue();
            if (slabbed$isLoweredFloorTorch(self, yOff)) {
                shape = SLABBED$COMFORT_TORCH_SHAPE;
            } else if (slabbed$isLoweredBeta35FloorTopContactObject(self, yOff) && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getShape(world, pos, CollisionContext.empty()));
                return;
            } else if (slabbed$isLoweredBeta35OakTrapdoorContactObject(self, yOff) && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getShape(world, pos, CollisionContext.empty()));
                return;
            } else if (slabbed$isLoweredBeta35RegularDoorContactObject(self, yOff)
                    && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getShape(world, pos, CollisionContext.empty()));
                return;
            } else if (slabbed$isLoweredBeta35StandingOakSignContactObject(self, yOff)
                    && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getShape(world, pos, CollisionContext.empty()));
                return;
            } else if (yOff < 0.0
                    && slabbed$isBeta35SpecialFullblockRaycastFallbackObject(self)
                    && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getShape(world, pos, CollisionContext.empty()));
                return;
            } else if (slabbed$isLoweredBeta35FenceWallVariantContactObject(self, yOff)
                    && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getShape(world, pos, CollisionContext.empty()));
                return;
            } else if (slabbed$isLoweredBeta35FenceGateContactObject(self, yOff)
                    && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getShape(world, pos, CollisionContext.empty()));
                return;
            } else if (slabbed$needsLoweredFullBlockRaycastBasis(world, pos, self, yOff, shape)) {
                shape = Shapes.block();
            }
            cir.setReturnValue(shape.move(0.0, yOff, 0.0));
        }
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetOakFenceAndGrindstoneCollision(BlockGetter world, BlockPos pos, CollisionContext ctx,
                                                              CallbackInfoReturnable<VoxelShape> cir) {
        BlockState self = (BlockState) (Object) this;
        if (!SlabSupport.isBeta35FenceWallVariantContactObject(self)
                && !SlabSupport.isBeta35FenceGateContactObject(self)
                && !self.is(Blocks.GRINDSTONE)) {
            return;
        }
        double yOff = SlabSupport.getYOffset(world, pos, self);
        if (yOff != 0.0) {
            if (SlabSupport.isBeta35FenceWallVariantContactObject(self) && yOff < 0.0) {
                cir.setReturnValue(self.getShape(world, pos, ctx));
                return;
            }
            cir.setReturnValue(cir.getReturnValue().move(0.0, yOff, 0.0));
        }
    }

    // ── outline (hit-box) offset ──────────────────────────────────────

    @Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetOutline(BlockGetter world, BlockPos pos, CollisionContext ctx,
                                       CallbackInfoReturnable<VoxelShape> cir) {
        BlockState self = (BlockState) (Object) this;

        Block block = self.getBlock();
        if (block instanceof CarpetBlock || block instanceof MossyCarpetBlock) {
            return;
        }

        VoxelShape shape = cir.getReturnValue();
        boolean changed = false;

        double yOff = SlabSupport.getYOffset(world, pos, self);
        if (yOff != 0.0) {
            if (slabbed$isLoweredFloorTorch(self, yOff)) {
                shape = SLABBED$COMFORT_TORCH_SHAPE;
            }
            shape = shape.move(0.0, yOff, 0.0);
            changed = true;
        }

        if (block instanceof SlabBlock) {
            VoxelShape overlay = slabbed$slabTorchComfortOverlay(world, pos);
            if (overlay != null) {
                shape = Shapes.or(shape, overlay);
                changed = true;
            }
        }

        if (changed) {
            cir.setReturnValue(shape);
        }
    }
}
