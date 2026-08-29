package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Support and shape policy on {@link BlockBehaviour.BlockStateBase}:
 *
 * <ol>
 *   <li><b>isFaceSturdy</b> — makes bottom slabs report their UP face as
 *       solid for every {@link SupportType}, enabling placement.</li>
 *   <li><b>isValidSpawn</b> — keeps that placement support from making bottom
 *       slabs valid mob-spawn floors.</li>
 *   <li><b>getShape</b> — shifts the outline (hit-box wireframe)
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

    /**
     * Connection blocks (fence/wall/pane) render <em>un-lowered</em> — {@code
     * OffsetBlockStateModel.emitBlockQuads} forces their dy to 0 unless they are a
     * recognised contact object. Their outline/raycast shape must therefore NOT be
     * offset either, or the authoritative nearest-hit raycast (the activated
     * {@link com.slabbed.util.SlabbedOffsetRaycast}) would target a phantom outline
     * 0.5–1.0 below the rendered block. Mirrors the render zeroing exactly by reusing
     * the same predicate, so outline and model can never disagree. State-only (pure
     * {@code instanceof}); safe to call on async outline workers (no world/chunk read).
     */
    private static boolean slabbed$isRenderZeroedConnectionBlock(BlockState state) {
        Block block = state.getBlock();
        boolean connection = block instanceof FenceBlock
                || block instanceof WallBlock
                || block instanceof IronBarsBlock;
        return connection && !SlabSupport.isBeta35FenceWallVariantContactObject(state);
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

    private static boolean slabbed$isTopHalfTrapdoor(BlockState state) {
        return state != null
                && state.getBlock() instanceof TrapDoorBlock
                && state.hasProperty(BlockStateProperties.HALF)
                && state.getValue(BlockStateProperties.HALF) == Half.TOP;
    }

    private static boolean slabbed$coherentNonEmptyTriadBasis(VoxelShape outline, VoxelShape collision) {
        if (outline == null || collision == null || outline.isEmpty() || collision.isEmpty()) {
            return false;
        }
        return Math.abs(outline.bounds().minX - collision.bounds().minX) <= 1.0e-6
                && Math.abs(outline.bounds().minY - collision.bounds().minY) <= 1.0e-6
                && Math.abs(outline.bounds().minZ - collision.bounds().minZ) <= 1.0e-6
                && Math.abs(outline.bounds().maxX - collision.bounds().maxX) <= 1.0e-6
                && Math.abs(outline.bounds().maxY - collision.bounds().maxY) <= 1.0e-6
                && Math.abs(outline.bounds().maxZ - collision.bounds().maxZ) <= 1.0e-6;
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

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void slabbed$lanternLoweredSlabUndersideSurvival(
            LevelReader world,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockState self = (BlockState) (Object) this;
        if ((!self.is(Blocks.LANTERN) && !self.is(Blocks.SOUL_LANTERN))
                || !self.hasProperty(BlockStateProperties.HANGING)
                || !self.getValue(BlockStateProperties.HANGING)) {
            return;
        }
        BlockPos supportPos = pos.above();
        BlockState supportState = world.getBlockState(supportPos);
        if (supportState.getBlock() instanceof SlabBlock
                && supportState.hasProperty(SlabBlock.TYPE)
                && supportState.getFluidState().isEmpty()
                && (supportState.getValue(SlabBlock.TYPE) == SlabType.TOP
                || supportState.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                || supportState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/SupportType;)Z", at = @At("HEAD"), cancellable = true)
    private void slabbed$slabTopSolid(BlockGetter world, BlockPos pos, Direction direction, SupportType shapeType, CallbackInfoReturnable<Boolean> cir) {
        BlockState self = (BlockState) (Object) this;
        if (direction == Direction.UP && slabbed$isStandableSlabTopFace(self)) {
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
        if (direction == Direction.UP && slabbed$isStandableSlabTopFace(self)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Vanilla asks whether a face is sturdy through two overloads and different callers reach
     * different ones, so both must answer alike or which caller happens to be on the stack decides
     * whether a block is standable. Geometric per LAW.md clause 2; the named compat arm stays
     * beside it (maintainer ruling, 2026-08-21) so a compat surface outside the vanilla slabs tag
     * is not narrowed out of the seat it already has.
     */
    @Unique
    private static boolean slabbed$isStandableSlabTopFace(BlockState state) {
        return SlabSupport.isSupportFaceBottomSlab(state)
                || CompatHooks.customSlabSurfaceKind(state) == CompatSlabSurfaceKind.BOTTOM_LIKE;
    }

    @Inject(
            method = "isValidSpawn(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/EntityType;)Z",
            at = @At("RETURN"),
            cancellable = true
    )
    private void slabbed$bottomSlabSpawnProofing(
            BlockGetter world,
            BlockPos pos,
            EntityType<?> entityType,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockState self = (BlockState) (Object) this;
        if (Boolean.TRUE.equals(cir.getReturnValue())
                && SlabSupport.isSpawnProofBottomSlabSurface(self)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getInteractionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetRaycast(BlockGetter world, BlockPos pos,
                                       CallbackInfoReturnable<VoxelShape> cir) {
        BlockState self = (BlockState) (Object) this;
        VoxelShape shape = cir.getReturnValue();
        if (slabbed$isTopHalfTrapdoor(self) && (shape == null || shape.isEmpty())) {
            VoxelShape outline = self.getShape(world, pos, CollisionContext.empty());
            VoxelShape collision = self.getCollisionShape(world, pos, CollisionContext.empty());
            if (slabbed$coherentNonEmptyTriadBasis(outline, collision)) {
                cir.setReturnValue(outline);
                return;
            }
        }
        if ((shape == null || shape.isEmpty())
                && (self.is(Blocks.LANTERN) || self.is(Blocks.SOUL_LANTERN))
                && self.hasProperty(BlockStateProperties.HANGING)
                && self.getValue(BlockStateProperties.HANGING)) {
            BlockPos supportPos = pos.above();
            BlockState supportState = world.getBlockState(supportPos);
            if (SlabSupport.isCeilingBridgedVerticalChainColumnMember(world, supportPos, supportState)) {
                cir.setReturnValue(self.getShape(world, pos, CollisionContext.empty()));
                return;
            }
        }

        // Fence/wall/pane render un-lowered (see OffsetBlockStateModel.emitBlockQuads);
        // their raycast shape must match, so do not offset it. Mirrors the render path.
        if (slabbed$isRenderZeroedConnectionBlock(self)) {
            return;
        }

        double yOff = SlabSupport.getYOffset(world, pos, self);
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
            } else if (slabbed$needsLoweredFullBlockRaycastBasis(world, pos, self, yOff, shape)) {
                // The raycast basis stands in for THIS block's own outline, so read that block's
                // own RAW shape and let the unconditional move below lower it exactly once.
                //
                // Do NOT re-add Shapes.block() here. A hardcoded unit cube equals the outline only
                // for a block that already fills its cell; for anything shorter it stands
                // (1 - rawMaxY) proud of the rendered top. Vanilla's
                // BlockGetter.clipWithInteractionOverride keeps the OUTLINE hit's location but
                // grafts THIS shape's Direction onto it whenever this one is nearer, so an
                // oversized basis hands a horizontal face to a hit that landed on the top plane -
                // and placement reads clickedPos.relative(face), which puts the block beside the
                // target instead of on it. Measured live: 2046 frames, a cube standing half a
                // block proud of a lowered slab.
                //
                // Read RAW (empty view, origin) rather than the lowered outline: this must stay a
                // pure function of the state. The lowered getShape path returns an UNLOWERED shape
                // under three guards - async-unsafe view, raw-shape probe, collision-query - and
                // one of them clears a thread-local as a side effect, so reading it here would
                // make the basis depend on which lane happened to call first.
                shape = self.getShape(
                        EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
            }
            shape = shape.move(0.0, yOff, 0.0);
            if (SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(world, pos, self)) {
                shape = SlabSupport.ceilingBridgedVerticalChainSelectionShape(world, pos, self, shape);
            }
            cir.setReturnValue(shape);
            return;
        }
        if (SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(world, pos, self)) {
            cir.setReturnValue(SlabSupport.ceilingBridgedVerticalChainSelectionShape(world, pos, self, shape));
        }
    }

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
            at = @At("RETURN"))
    private void slabbed$collisionQueryExit(BlockGetter world, BlockPos pos, CollisionContext ctx,
                                            CallbackInfoReturnable<VoxelShape> cir) {
        SLABBED$IN_COLLISION_QUERY.set(Boolean.FALSE);
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetOakFenceAndGrindstoneCollision(BlockGetter world, BlockPos pos, CollisionContext ctx,
                                                              CallbackInfoReturnable<VoxelShape> cir) {
        if (SlabSupport.isRawShapeProbeActive() || SlabSupport.isVanillaCollisionShapeQuery()) {
            return;
        }
        BlockState self = (BlockState) (Object) this;
        if (self.getBlock() instanceof StairBlock) {
            double yOff = SlabSupport.getYOffset(world, pos, self);
            if (yOff < -1.0e-6d) {
                VoxelShape shape = cir.getReturnValue();
                if (shape != null && !shape.isEmpty()) {
                    cir.setReturnValue(shape.move(0.0d, yOff, 0.0d));
                }
            }
            return;
        }
        if (!SlabSupport.isBeta35FenceWallVariantContactObject(self)
                && !SlabSupport.isBeta35FenceGateContactObject(self)
                && !self.is(Blocks.GRINDSTONE)) {
            return;
        }
        double yOff = SlabSupport.getYOffset(world, pos, self);
        if (yOff != 0.0) {
            cir.setReturnValue(cir.getReturnValue().move(0.0, yOff, 0.0));
        }
    }

    // ── outline (hit-box) offset ──────────────────────────────────────

    @Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetOutline(BlockGetter world, BlockPos pos, CollisionContext ctx,
                                       CallbackInfoReturnable<VoxelShape> cir) {
        // Server-side background shape queries must never enter offset resolution: that path reads
        // chunk attachments and support state, which may synchronously request a server-owned chunk.
        // Keep the legacy named-worker fallback for non-server worlds whose owner is unavailable.
        if (slabbed$isUnsafeAsyncShapeContext(world)) {
            return;
        }

        if (SlabSupport.isRawShapeProbeActive()) {
            return;
        }

        if (Boolean.TRUE.equals(SLABBED$IN_COLLISION_QUERY.get())) {
            SLABBED$IN_COLLISION_QUERY.set(Boolean.FALSE);
            return;
        }

        BlockState self = (BlockState) (Object) this;

        // CarpetBlock retains its dedicated client outline owner. Other geometry-equivalent
        // thin floor followers use this common state path; identity only selects the mixin owner,
        // never the height or eligibility.
        Block block = self.getBlock();
        if (block instanceof net.minecraft.world.level.block.CarpetBlock
                && SlabSupport.isSupportFollowingFloorLayer(world, pos, self)) {
            return;
        }

        // Fence/wall/pane render un-lowered; keep their outline un-offset to match
        // (else the authoritative nearest-hit raycast targets a phantom shape below).
        if (slabbed$isRenderZeroedConnectionBlock(self)) {
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

        if (SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(world, pos, self)) {
            shape = SlabSupport.ceilingBridgedVerticalChainSelectionShape(world, pos, self, shape);
            changed = true;
        }

        // (Removed) the slab-side torch comfort-overlay union: with the offset-aware
        // nearest-hit raycast now authoritative (GameRendererPickOffsetRaycastMixin /
        // SlabbedOffsetRaycast), the torch is targeted via its own offset shape — no
        // fattened slab phantom is needed, and unioning one would mis-target the slab.
        if (changed) {
            cir.setReturnValue(shape);
        }
    }

    private static boolean slabbed$isUnsafeAsyncShapeContext(BlockGetter world) {
        if (world instanceof ServerLevel serverLevel) {
            return !serverLevel.getServer().isSameThread();
        }
        String name = Thread.currentThread().getName();
        return name.startsWith("Worker-Main") || name.contains("ForkJoinPool");
    }
}
