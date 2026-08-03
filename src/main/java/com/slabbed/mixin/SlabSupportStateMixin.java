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
import net.minecraft.world.level.block.StairBlock;
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
import net.minecraft.world.entity.EntityType;
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

    private static boolean slabbed$isLawfulLoweredSlabInteractionSurface(
            BlockGetter world,
            BlockPos pos,
            BlockState state,
            double yOff
    ) {
        // F5b: fluid-blind — a waterlogged marked slab renders lowered; refusing it here made the
        // interaction shape EMPTY (untargetable) while model/outline showed the lowered box.
        if (yOff >= 0.0
                || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)) {
            return false;
        }
        return SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state);
    }

    private static boolean slabbed$isUnnamedDy0VanillaSlabInteractionSurface(
            BlockGetter world,
            BlockPos pos,
            BlockState state,
            double yOff
    ) {
        if (yOff != 0.0
                || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)) {
            return false;
        }
        return !SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
                && !SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                && !SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                && !SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                && !SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state);
    }

    private static VoxelShape slabbed$vanillaCompatibleSlabInteractionShape(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        VoxelShape collisionShape = state.getCollisionShape(world, pos, CollisionContext.empty());
        if (collisionShape != null && !collisionShape.isEmpty()) {
            return collisionShape;
        }
        return state.getShape(world, pos, CollisionContext.empty());
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

    // GOES C2 (TEST 18): slabbed$slabTorchComfortOverlay REMOVED — it built the slab-overlay half of the
    // torch comfort shape (a torch post unioned into the slab's outline). See the removal note in
    // slabbed$offsetOutline. The torch's OWN comfort shape (SLABBED$COMFORT_TORCH_SHAPE, applied when the
    // subject itself is the lowered floor torch) is retained.

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

    /**
     * Preserve bottom-like slabs as mob-proof surfaces after the separate
     * placement/survival support override has done its job.
     *
     * <p>Running at RETURN makes this strictly subtractive: entity-specific
     * rules and every already-false block predicate remain false.
     */
    @Inject(method = "isValidSpawn", at = @At("RETURN"), cancellable = true)
    private void slabbed$bottomSlabSpawnProofing(
            BlockGetter world,
            BlockPos pos,
            EntityType<?> entityType,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockState self = (BlockState) (Object) this;
        if (cir.getReturnValue() && SlabSupport.isSpawnProofBottomLikeSurface(self)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getInteractionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetRaycast(BlockGetter world, BlockPos pos,
                                       CallbackInfoReturnable<VoxelShape> cir) {
        BlockState self = (BlockState) (Object) this;

        double yOff = SlabSupport.getYOffset(world, pos, self);
        VoxelShape shape = cir.getReturnValue();
        if (slabbed$isUnnamedDy0VanillaSlabInteractionSurface(world, pos, self, yOff)
                && (shape == null || shape.isEmpty())) {
            cir.setReturnValue(slabbed$vanillaCompatibleSlabInteractionShape(world, pos, self));
            return;
        }
        if (yOff != 0.0) {
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
            } else if (slabbed$isLawfulLoweredSlabInteractionSurface(world, pos, self, yOff)
                    && (shape == null || shape.isEmpty())) {
                cir.setReturnValue(self.getShape(world, pos, CollisionContext.empty()));
                return;
            } else if (slabbed$needsLoweredFullBlockRaycastBasis(world, pos, self, yOff, shape)) {
                shape = Shapes.block();
            }
            shape = shape.move(0.0, yOff, 0.0);
            if (SlabSupport.usesCeilingBridgeGeometry(world, pos, self, yOff)) {
                shape = SlabSupport.ceilingBridgedVerticalChainSelectionShape(world, pos, self, yOff, shape);
            }
            cir.setReturnValue(shape);
            return;
        }
        if (SlabSupport.usesCeilingBridgeGeometry(world, pos, self, yOff)) {
            cir.setReturnValue(SlabSupport.ceilingBridgedVerticalChainSelectionShape(world, pos, self, yOff, shape));
        }
    }

    // ── collision: INTENTIONALLY KEPT VANILLA (within-cell) ───────────
    // Slabbed lowers visual/outline/raycast only; movement collision must stay
    // vanilla so MC's cell-bounded BlockCollisions broadphase samples it.
    //
    // Subtlety on 26.1.2: vanilla's default collision delegates to the outline
    // shape — getCollisionShape(world,pos,ctx) -> Block.getCollisionShape ->
    // (hasCollision ? state.getShape(world,pos) : empty) -> getShape -> our
    // slabbed$offsetOutline. So the outline offset BLEEDS into movement
    // collision, hanging the shape to min y = dy (e.g. -0.5) into the cell
    // below, where the broadphase never samples it -> the lowered slab/block
    // becomes a ghost the player walks through (proven by
    // GhostLoweredCollisionProofTest).
    //
    // Fix: flag the thread while inside a getCollisionShape query so the outline
    // mixin leaves the shape un-offset for that one delegated getShape call.
    // This is SOURCE-SCOPED: blocks that override getCollisionShape (fences,
    // walls, panes) never route through getShape, so they are unaffected. The
    // old getCollisionShape OFFSET inject (slabbed$offsetOakFence...) is gone.
    @Unique
    private static final ThreadLocal<Boolean> SLABBED$IN_COLLISION_QUERY =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"))
    private void slabbed$collisionQueryEnter(BlockGetter world, BlockPos pos, CollisionContext ctx,
                                             CallbackInfoReturnable<VoxelShape> cir) {
        SLABBED$IN_COLLISION_QUERY.set(Boolean.TRUE);
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$collisionQueryExit(BlockGetter world, BlockPos pos, CollisionContext ctx,
                                            CallbackInfoReturnable<VoxelShape> cir) {
        SLABBED$IN_COLLISION_QUERY.set(Boolean.FALSE);
        BlockState self = (BlockState) (Object) this;
        if (SlabSupport.isVanillaCollisionShapeQuery() || !(self.getBlock() instanceof StairBlock)) {
            return;
        }
        double yOff = SlabSupport.getYOffset(world, pos, self);
        if (yOff < -1.0e-6d) {
            VoxelShape shape = cir.getReturnValue();
            if (shape != null && !shape.isEmpty()) {
                cir.setReturnValue(shape.move(0.0d, yOff, 0.0d));
            }
        }
    }

    // ── outline (hit-box) offset ──────────────────────────────────────

    @Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetOutline(BlockGetter world, BlockPos pos, CollisionContext ctx,
                                       CallbackInfoReturnable<VoxelShape> cir) {
        // Movement collision reaches this getShape via vanilla's
        // getCollisionShape -> getShape delegation. Leave the shape vanilla
        // (within-cell) for that path so the cell-bounded broadphase samples it;
        // only the outline/visual gets lowered. Consume the flag so a stray
        // set (e.g. getCollisionShape on a non-colliding block) cannot leak.
        if (Boolean.TRUE.equals(SLABBED$IN_COLLISION_QUERY.get())) {
            SLABBED$IN_COLLISION_QUERY.set(Boolean.FALSE);
            return;
        }

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

        if (SlabSupport.usesCeilingBridgeGeometry(world, pos, self, yOff)) {
            shape = SlabSupport.ceilingBridgedVerticalChainSelectionShape(world, pos, self, yOff, shape);
            changed = true;
        }

        // GOES C2 (TEST 18 finding, ledger 2026-07-10): the SLAB torch-comfort OVERLAY is removed. It
        // unioned a torch post into the slab's own outline so vanilla DDA would produce a slab hit that
        // the legacy rescue-retargeter then bounced up to the torch above. Under the shipping offset
        // raycast (SlabbedOffsetRaycast, default ON) that retarget partner is DEAD, so the overlay only
        // made the slab STEAL torch clicks and MERGE the two outlines. The offset raycast targets the
        // lowered torch directly via its own comfort shape (below, lines that build SLABBED$COMFORT_TORCH_
        // SHAPE for the torch itself), so the slab overlay is pure harm. slabUnderLoweredTorchReturns
        // SlabOnlyOutline pins the slab-only result. (The torch's OWN comfort shape is KEPT: the offset
        // raycast still needs the fuller post to enter the torch's tiny native voxel from natural angles.)

        if (changed) {
            cir.setReturnValue(shape);
        }
    }
}
