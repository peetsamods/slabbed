package com.slabbed.util;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.HangingSignBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Central helper for slab support semantics.
 */
public final class SlabSupport {
    private SlabSupport() {
    }

    /**
     * Returns true if the block is a thin top-layer block (snow layers, carpet,
     * pale moss carpet) that should never be visually offset by slab logic.
     * These blocks sit flush on the surface and are not structural.
     */
    public static boolean isThinTopLayer(BlockState state) {
        Block block = state.getBlock();
        return block instanceof SnowLayerBlock
                || block instanceof CarpetBlock
                || block instanceof MossyCarpetBlock;
    }

    /**
     * Returns true if the block at {@code pos} is a slab whose top face can provide support.
     */
    public static boolean isSupportingSlab(LevelReader world, BlockPos pos) {
        return isSupportingSlab(world.getBlockState(pos));
    }

    /** Overload for BlockGetter contexts (shapes). */
    public static boolean isSupportingSlab(BlockGetter world, BlockPos pos) {
        return isSupportingSlab(world.getBlockState(pos));
    }

    /**
     * Returns true if the state is a slab with a defined type.
     */
    public static boolean isSupportingSlab(BlockState state) {
        return state.getBlock() instanceof SlabBlock && state.hasProperty(SlabBlock.TYPE);
    }

    /** True if this state is a bottom slab. */
    public static boolean isBottomSlab(BlockState state) {
        return isSupportingSlab(state) && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /** True if this state is a top slab. */
    public static boolean isTopSlab(BlockState state) {
        return isSupportingSlab(state) && state.getValue(SlabBlock.TYPE) == SlabType.TOP;
    }

    /**
     * Single source of truth: returns true iff the state is a TOP slab
     * and the queried face is DOWN (i.e. the underside of a top slab).
     */
    public static boolean isTopSlabUndersideSupport(BlockState state, Direction face) {
        return face == Direction.DOWN && isTopSlab(state);
    }

    /** True if the block at {@code posAbove} is a top or double slab that can provide ceiling support. */
    public static boolean isCeilingSupportBottomSurface(LevelReader world, BlockPos posAbove) {
        BlockState stateAbove = world.getBlockState(posAbove);
        if (!isSupportingSlab(stateAbove)) {
            return false;
        }
        SlabType type = stateAbove.getValue(SlabBlock.TYPE);
        return type == SlabType.TOP || type == SlabType.DOUBLE;
    }

    /** Overload for shape/world views. */
    public static boolean isCeilingSupportBottomSurface(BlockGetter world, BlockPos posAbove) {
        BlockState stateAbove = world.getBlockState(posAbove);
        if (!isSupportingSlab(stateAbove)) {
            return false;
        }
        SlabType type = stateAbove.getValue(SlabBlock.TYPE);
        return type == SlabType.TOP || type == SlabType.DOUBLE;
    }

    /** True if the block immediately below {@code pos} is a bottom slab providing its top face. */
    public static boolean hasBottomSlabBelow(BlockGetter world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockState below = world.getBlockState(pos.below());
        // Terrain Slabs (no-op without the mod): a TS slab is a self-rendering surface, never a lowering
        // support — so a block placed on it must NOT anchor -0.5. This is the single choke point feeding
        // every anchor-qualification site; without it, onPlaced anchors the block -0.5 SERVER-side while
        // the client reads geometric 0.0 (the column walk already stops flush at a TS slab), so the block
        // visibly SNAPS DOWN into the TS surface when the anchor syncs (Julia's "snapping down after a
        // short delay" on a terrain slab). Vanilla bottom slabs are untouched (shouldSkipSlabSupport keys
        // only on terrain_slabs/terrainslabs ids).
        if (CompatHooks.shouldSkipSlabSupport(below)) {
            return false;
        }
        return isBottomSlab(below);
    }

    /**
     * Effective Y offset of the slab's top face relative to the slab block position.
     * 0.5 for bottom slabs, 1.0 for top/double.
     */
    public static double getSupportYOffset(BlockState state) {
        if (!isSupportingSlab(state)) {
            throw new IllegalArgumentException("Not a supporting slab: " + state);
        }
        SlabType type = state.getValue(SlabBlock.TYPE);
        return switch (type) {
            case BOTTOM -> 0.5;
            case TOP, DOUBLE -> 1.0;
        };
    }

    /**
     * Primary query: should this slab top face count as solid support.
     */
    public static boolean canTreatAsSolidTopFace(LevelReader world, BlockPos pos) {
        return isSupportingSlab(world, pos);
    }

    /** Overload for shape/world views. */
    public static boolean canTreatAsSolidTopFace(BlockGetter world, BlockPos pos) {
        return isSupportingSlab(world, pos);
    }

    public static boolean isFloorTorch(BlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof TorchBlock && !(block instanceof WallTorchBlock);
    }

    private static boolean isBeta35FloorTopContactObject(BlockState state) {
        return state != null && (state.is(Blocks.CANDLE) || state.is(Blocks.FLOWER_POT));
    }

    public static boolean isBeta35FloorButtonContactObject(BlockState state) {
        return state != null
                && state.getBlock() instanceof ButtonBlock
                && state.hasProperty(BlockStateProperties.ATTACH_FACE)
                && state.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.FLOOR;
    }

    public static boolean isBeta35BottomTrapdoorVisibleOwnerObject(BlockState state) {
        return state != null
                && state.getBlock() instanceof TrapDoorBlock
                && state.hasProperty(BlockStateProperties.HALF)
                && state.getValue(BlockStateProperties.HALF) == Half.BOTTOM;
    }

    public static boolean isBeta35VerticalChainVisibleOwnerObject(BlockState state) {
        return state != null
                && state.getBlock() instanceof ChainBlock
                && state.hasProperty(BlockStateProperties.AXIS)
                && state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
    }

    public static boolean isVerticalChainDirectlyUnderCeilingSupport(BlockGetter world, BlockPos pos, BlockState state) {
        return world != null
                && pos != null
                && isBeta35VerticalChainVisibleOwnerObject(state)
                && isCeilingSupportBottomSurface(world, pos.above());
    }

    public static VoxelShape ceilingBridgedVerticalChainSelectionShape(
            BlockGetter world, BlockPos pos, BlockState state, VoxelShape fallback
    ) {
        VoxelShape base = fallback == null ? Shapes.empty() : fallback;
        if (!isVerticalChainDirectlyUnderCeilingSupport(world, pos, state)) {
            return base;
        }
        if (base.isEmpty()) {
            return Block.box(6.5d, 0.0d, 6.5d, 9.5d, 24.0d, 9.5d);
        }

        AABB bounds = base.bounds();
        VoxelShape selection = base;
        if (bounds.minY > 0.0d) {
            selection = Shapes.or(selection, base.move(0.0d, -bounds.minY, 0.0d));
        }
        if (bounds.maxY < 1.5d) {
            selection = Shapes.or(selection, base.move(0.0d, 1.5d - bounds.maxY, 0.0d));
        }
        return selection;
    }

    public static boolean isCeilingBridgedVerticalChainColumnMember(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35VerticalChainVisibleOwnerObject(state)) {
            return false;
        }
        BlockPos cursor = pos;
        BlockState cursorState = state;
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            if (isVerticalChainDirectlyUnderCeilingSupport(world, cursor, cursorState)) {
                return true;
            }
            BlockPos abovePos = cursor.above();
            BlockState above = world.getBlockState(abovePos);
            if (!isBeta35VerticalChainVisibleOwnerObject(above)) {
                return false;
            }
            cursor = abovePos;
            cursorState = above;
        }
        return false;
    }

    public static boolean isBeta35UpwardSpeleothemVisibleOwnerObject(BlockState state) {
        return state != null
                && state.getBlock() instanceof SpeleothemBlock
                && state.hasProperty(BlockStateProperties.VERTICAL_DIRECTION)
                && state.getValue(BlockStateProperties.VERTICAL_DIRECTION) == Direction.UP;
    }

    public static boolean isBeta35RailVisibleOwnerObject(BlockState state) {
        return state != null && state.getBlock() instanceof BaseRailBlock;
    }

    public static boolean isBeta35RegularDoorVisibleOwnerObject(
            BlockGetter world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!(state.getBlock() instanceof DoorBlock) || !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return false;
        }
        double objectDy = getYOffset(world, pos, state);
        return Double.isFinite(objectDy) && objectDy < -1.0e-6d;
    }

    public static boolean isBeta35LoweredRegularDoorServerHitTarget(
            BlockGetter world, BlockPos pos, BlockState state
    ) {
        if (!isBeta35RegularDoorVisibleOwnerObject(world, pos, state)) {
            return false;
        }
        if (!hasConsistentBeta35RegularDoorPair(world, pos, state)) {
            return false;
        }
        double targetDy = getBeta35ShiftedServerValidationYOffset(world, pos, state);
        return Double.isFinite(targetDy) && targetDy < -1.0e-6d;
    }

    private static boolean hasConsistentBeta35RegularDoorPair(
            BlockGetter world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof DoorBlock)
                || !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return false;
        }
        DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
        BlockPos pairedPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        BlockState pairedState = world.getBlockState(pairedPos);
        if (pairedState == null
                || pairedState.getBlock() != state.getBlock()
                || !pairedState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || pairedState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == half) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && pairedState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && state.getValue(BlockStateProperties.HORIZONTAL_FACING) != pairedState.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.DOOR_HINGE)
                && pairedState.hasProperty(BlockStateProperties.DOOR_HINGE)
                && state.getValue(BlockStateProperties.DOOR_HINGE) != pairedState.getValue(BlockStateProperties.DOOR_HINGE)) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.OPEN)
                && pairedState.hasProperty(BlockStateProperties.OPEN)
                && state.getValue(BlockStateProperties.OPEN) != pairedState.getValue(BlockStateProperties.OPEN)) {
            return false;
        }
        return !state.hasProperty(BlockStateProperties.POWERED)
                || !pairedState.hasProperty(BlockStateProperties.POWERED)
                || state.getValue(BlockStateProperties.POWERED) == pairedState.getValue(BlockStateProperties.POWERED);
    }

    public static boolean isBeta35LoweredTrapdoorOrFloorButtonVisibleTarget(
            BlockGetter world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!isBeta35FloorButtonContactObject(state) && !isBeta35BottomTrapdoorVisibleOwnerObject(state)) {
            return false;
        }
        double objectDy = getYOffset(world, pos, state);
        if (!Double.isFinite(objectDy) || objectDy >= -1.0e-6d) {
            return false;
        }
        return true;
    }

    public static boolean isBeta35LoweredTrapdoorOrFloorButtonVisibleOwnerTarget(
            BlockGetter world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!isBeta35FloorButtonContactObject(state) && !isBeta35BottomTrapdoorVisibleOwnerObject(state)) {
            return false;
        }
        return Double.isFinite(beta35FloorButtonContactDy(world, pos, state))
                || Double.isFinite(beta35BottomTrapdoorVisibleOwnerDy(world, pos, state));
    }

    public static boolean isBeta35LoweredBottomTrapdoorServerHitTarget(
            BlockGetter world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || !isBeta35BottomTrapdoorVisibleOwnerObject(state)) {
            return false;
        }
        double objectDy = getBeta35ShiftedServerValidationYOffset(world, pos, state);
        return Double.isFinite(objectDy) && objectDy < -1.0e-6d;
    }

    public static boolean isBeta35LoweredTrapdoorOrFloorButtonServerHitTarget(
            BlockGetter world, BlockPos pos, BlockState state
    ) {
        return isBeta35LoweredTrapdoorOrFloorButtonVisibleOwnerTarget(world, pos, state)
                || isBeta35LoweredBottomTrapdoorServerHitTarget(world, pos, state);
    }

    public static double getBeta35ShiftedServerValidationYOffset(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return Double.NaN;
        }
        if (isBeta35BottomTrapdoorVisibleOwnerObject(state)) {
            double visibleOwnerDy = beta35BottomTrapdoorVisibleOwnerDy(world, pos, state);
            if (Double.isFinite(visibleOwnerDy) && visibleOwnerDy < -1.0e-6d) {
                return visibleOwnerDy;
            }
        }
        return getYOffset(world, pos, state);
    }

    private static double beta35BottomTrapdoorVisibleOwnerDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35BottomTrapdoorVisibleOwnerObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.below();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy - 0.5d;
        }
        return Double.NaN;
    }

    public static boolean isBeta35SlabHeightVisibleOwnerObject(
            BlockGetter world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!isBeta35FloorButtonContactObject(state)
                && !isBeta35BottomTrapdoorVisibleOwnerObject(state)
                && !isBeta35VerticalChainVisibleOwnerObject(state)
                && !isBeta35UpwardSpeleothemVisibleOwnerObject(state)
                && !isBeta35RailVisibleOwnerObject(state)
                && !isBeta35RegularDoorVisibleOwnerObject(world, pos, state)) {
            return false;
        }
        double objectDy = getYOffset(world, pos, state);
        return Double.isFinite(objectDy) && objectDy < -1.0e-6d;
    }

    public static boolean isBeta35FenceWallVariantContactObject(BlockState state) {
        return state != null
                && (state.getBlock() instanceof FenceBlock
                        || state.getBlock() instanceof WallBlock
                        || state.getBlock() instanceof IronBarsBlock);
    }

    public static boolean isBeta35FenceGateContactObject(BlockState state) {
        return state != null && state.getBlock() instanceof FenceGateBlock;
    }

    private static boolean isBeta35OakTrapdoorContactObject(BlockState state) {
        return state != null
                && state.is(Blocks.OAK_TRAPDOOR)
                && state.hasProperty(BlockStateProperties.HALF)
                && state.getValue(BlockStateProperties.HALF) == Half.BOTTOM;
    }

    private static boolean isBeta35RegularDoorContactObject(BlockState state) {
        return state != null
                && state.getBlock() instanceof DoorBlock
                && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
    }

    private static boolean isBeta35StandingOakSignContactObject(BlockState state) {
        return state != null && state.is(Blocks.OAK_SIGN);
    }

    private static double beta35FenceWallVariantContactDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35FenceWallVariantContactObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.below();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = beta35FenceWallVisibleSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            double supportTopOffset = isSupportingSlab(supportState) ? getSupportYOffset(supportState) : 1.0d;
            return supportDy + supportTopOffset - 1.0d;
        }
        return Double.NaN;
    }

    private static double beta35FenceWallVisibleSupportDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || state.isAir() || !state.getFluidState().isEmpty()) {
            return Double.NaN;
        }
        if (isBottomSlab(state)) {
            return floorTorchBottomSlabSupportDy(world, pos, state);
        }
        if (isTopSlab(state) && SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)) {
            return -1.0d;
        }
        if (state.getBlock() instanceof SlabBlock && state.hasProperty(SlabBlock.TYPE)
                && (state.getValue(SlabBlock.TYPE) == SlabType.TOP || state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE)) {
            if (state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE
                    && SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)) {
                return -1.0d;
            }
            BlockPos belowPos = pos.below();
            BlockState below = world.getBlockState(belowPos);
            if (below.getBlock() instanceof SlabBlock && isLoweredDoubleSlabCarrier(world, belowPos, below)) {
                return -0.5d;
            }
            if (hasLoweredCarrierBelow(world, pos) || isAdjacentSideSlabLowered(world, pos, state)) {
                return -0.5d;
            }
        }
        if (isOrdinaryFullBlockWithCompoundDy(world, pos, state)) {
            return -1.0d;
        }
        if (isBeta35FenceWallVariantContactObject(state)) {
            double dy = beta35FenceWallVariantContactDy(world, pos, state);
            if (Double.isFinite(dy) && dy < -1.0e-6d) {
                return dy;
            }
        }
        return Double.NaN;
    }

    private static double beta35FenceGateContactDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35FenceGateContactObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.below();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy - 0.5d;
        }
        return Double.NaN;
    }

    private static double beta35FloorButtonContactDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35FloorButtonContactObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.below();
        BlockState supportState = world.getBlockState(supportPos);
        if (!isSupportingSlab(supportState)) {
            return Double.NaN;
        }
        double supportDy = beta35FenceWallVisibleSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy + getSupportYOffset(supportState) - 1.0d;
        }
        return Double.NaN;
    }

    private static double beta35OakTrapdoorContactDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35OakTrapdoorContactObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.below();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy - 0.5d;
        }
        return Double.NaN;
    }

    private static double beta35RegularDoorContactDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35RegularDoorContactObject(state)) {
            return Double.NaN;
        }
        BlockPos bottomPos = pos;
        if (state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            bottomPos = pos.below();
            BlockState bottomState = world.getBlockState(bottomPos);
            if (!isBeta35RegularDoorContactObject(bottomState)
                    || bottomState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) {
                return Double.NaN;
            }
        }
        BlockPos supportPos = bottomPos.below();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy - 0.5d;
        }
        return Double.NaN;
    }

    private static double beta35StandingOakSignContactDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35StandingOakSignContactObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.below();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy - 0.5d;
        }
        return Double.NaN;
    }

    private static boolean isBeta35OrdinaryFullBlockContactObject(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        Block block = state.getBlock();
        if (!(block instanceof CraftingTableBlock || block instanceof EntityBlock)) {
            return false;
        }
        return state.isSolidRender();
    }

    private static double beta35OrdinaryFullBlockContactDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (!isBeta35OrdinaryFullBlockContactObject(world, pos, state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.below();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy - 0.5d;
        }
        return Double.NaN;
    }

    private static boolean isBeta35SpecialFullblockContactObject(BlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return block == Blocks.CRAFTING_TABLE
                || block == Blocks.FURNACE
                || block == Blocks.BOOKSHELF
                || block == Blocks.CHEST
                || block == Blocks.BARREL
                || block == Blocks.ENCHANTING_TABLE
                || block == Blocks.STONECUTTER
                || block == Blocks.ANVIL
                || block == Blocks.GRINDSTONE;
    }

    private static double beta35SpecialFullblockContactDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !isBeta35SpecialFullblockContactObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.below();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy - 0.5d;
        }
        return Double.NaN;
    }

    public static boolean canTreatAsFloorTorchTopFace(BlockGetter world, BlockPos supportPos, BlockState torchState) {
        if (isRejectedFloorTorchTopFace(world, supportPos, torchState)) {
            return false;
        }
        if (!isFloorTorch(torchState)) {
            return canTreatAsSolidTopFace(world, supportPos);
        }
        if (world == null || supportPos == null) {
            return false;
        }
        return isLegalFloorTorchLoweredBottomSlabSupport(world, supportPos, torchState)
                || isSupportingSlab(world.getBlockState(supportPos));
    }

    public static boolean isLegalFloorTorchLoweredBottomSlabSupport(
            BlockGetter world,
            BlockPos supportPos,
            BlockState torchState
    ) {
        if (!isFloorTorch(torchState) || world == null || supportPos == null) {
            return false;
        }
        BlockState supportState = world.getBlockState(supportPos);
        if (!isBottomSlab(supportState)) {
            return false;
        }
        boolean namedLoweredBottomSupport =
                SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, supportPos, supportState)
                        || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, supportPos, supportState);
        return namedLoweredBottomSupport
                && Math.abs(getYOffset(world, supportPos, supportState) - (-1.0d)) <= 1.0e-6;
    }

    public static boolean isRejectedFloorTorchTopFace(BlockGetter world, BlockPos supportPos, BlockState torchState) {
        if (!isFloorTorch(torchState)) {
            return false;
        }
        if (world == null || supportPos == null) {
            return false;
        }
        BlockState supportState = world.getBlockState(supportPos);
        if (isLegalFloorTorchLoweredBottomSlabSupport(world, supportPos, torchState)) {
            return false;
        }
        return isBottomSlab(supportState)
                && SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, supportPos, supportState);
    }

    /**
     * Absolute Y of the slab's top surface.
     */
    public static double getEffectiveTopY(BlockState state, BlockPos pos) {
        return pos.getY() + getSupportYOffset(state);
    }

    /** Max blocks to walk down when checking chain offset. */
    private static final int MAX_CHAIN_DEPTH = 16;

    /** Recursion guard: prevents StackOverflow when isSolidBlock triggers getOutlineShape → getYOffset. */
    private static final ThreadLocal<Boolean> IN_GET_Y_OFFSET = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Returns true if the block state represents a ceiling-attached block —
     * one that hangs from the block above it by nature.
     */
    public static boolean isCeilingAttached(BlockState state) {
        // HANGING property (lanterns, etc.)
        if (state.hasProperty(BlockStateProperties.HANGING) && state.getValue(BlockStateProperties.HANGING)) {
            return true;
        }
        // Y-axis chains
        if (state.getBlock() instanceof ChainBlock
                && state.hasProperty(BlockStateProperties.AXIS)
                && state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y) {
            return true;
        }
        // Hanging signs
        if (state.getBlock() instanceof HangingSignBlock) {
            return true;
        }
        // Top-half trapdoors (ceiling-mounted)
        if (state.getBlock() instanceof TrapDoorBlock
                && state.hasProperty(BlockStateProperties.HALF)
                && state.getValue(BlockStateProperties.HALF) == Half.TOP) {
            return true;
        }
        // Bells, levers, buttons (can all be ceiling-mounted)
        Block block = state.getBlock();
        if (block instanceof BellBlock
                || block instanceof LeverBlock
                || block instanceof ButtonBlock) {
            return true;
        }
        // Specific ceiling-only block types
        if (block instanceof SporeBlossomBlock
                || block instanceof HangingRootsBlock
                || block instanceof PointedDripstoneBlock
                || block instanceof CaveVinesBlock
                || block instanceof CaveVinesPlantBlock) {
            return true;
        }
        return false;
    }

    /**
     * Master check: should the block at {@code pos} with state {@code state}
     * be visually offset by −0.5 Y?
     *
     * <p>Handles:
     * <ul>
     *   <li><b>Direct</b> — block directly above a bottom slab.</li>
     *   <li><b>Chain (recursive)</b> — block on top of a stack of non-air,
     *       non-slab blocks that ultimately sits on a bottom slab. Fixes
     *       stacking at any height (signs on fences on slabs, 3+ towers).</li>
     *   <li><b>Double-block</b> — upper half checks 2 blocks down.</li>
     *   <li><b>Bed</b> — both halves offset when <em>either</em> half has a
     *       bottom slab below.</li>
     * </ul>
     *
     * @return true if the block should receive a −0.5 Y offset
     */
    public static boolean shouldOffset(BlockGetter world, BlockPos pos, BlockState state) {
        // never offset slabs themselves
        if (state.getBlock() instanceof SlabBlock) {
            return false;
        }

        // never offset thin top-layer blocks (snow layers, carpet) or powder snow — natural surface
        // terrain fill, not structural objects. Powder snow is a FULL CUBE so it is NOT an
        // isThinTopLayer (which keys on SnowBlock), hence the explicit guard (else it steps -0.5 onto
        // a slab while neighbouring powder snow on full ground stays flush — a snowy-terrain DODO).
        if (isThinTopLayer(state) || state.getBlock() instanceof PowderSnowBlock) {
            return false;
        }

        if (CompatHooks.shouldSkipOffset(state)) {
            return false;
        }

        // blocks under a top slab that get +0.5 UP via getYOffset should not
        // also get -0.5 DOWN. Use isCeilingAttached here (safe, no shape calcs)
        // since shouldOffset is called from paths outside the recursion guard.
        if (isCeilingAttached(state) && isTopSlab(world.getBlockState(pos.above()))) {
            return false;
        }

        // ceiling-attached blocks further down a chain of ceiling blocks
        // leading to a top slab also get +0.5 UP; exclude from -0.5
        if (isCeilingAttached(state)) {
            BlockPos cursor = pos.above();
            for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
                BlockState cur = world.getBlockState(cursor);
                if (isTopSlab(cur)) {
                    return false;
                }
                if (isCeilingAttached(cur)) {
                    cursor = cursor.above();
                    continue;
                }
                break;
            }
        }

        // blocks hanging from above (lanterns, etc.) — don't offset DOWN by slab below
        // (they may get a separate +0.5 UP offset via getYOffset)
        if (state.hasProperty(BlockStateProperties.HANGING) && state.getValue(BlockStateProperties.HANGING)) {
            return false;
        }

        // Always-ceiling-hung decorations (hanging roots, spore blossom, hanging signs) attach to
        // the block ABOVE; a slab BELOW them must NOT lower them. They lack the HANGING property, so
        // the guard above misses them and they fell through to the column walk (a lantern placed
        // beneath bridged the downward walk to a slab, wrongly lowering them -0.5). Their real dy is
        // computed by ceilingHungDecorationDy in getYOffsetInner.
        if (isAlwaysCeilingHungDecoration(state)) {
            return false;
        }

        // ── bed: either half has a slab ───────────────────────────────
        if (state.hasProperty(BlockStateProperties.BED_PART)) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            BedPart part = state.getValue(BlockStateProperties.BED_PART);
            BlockPos otherPos;
            if (part == BedPart.FOOT) {
                otherPos = pos.relative(facing);
            } else {
                otherPos = pos.relative(facing.getOpposite());
            }
            return hasSlabInColumn(world, pos) || hasSlabInColumn(world, otherPos);
        }

        // ── double-block: upper half checks two blocks down ───────────
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            if (half == DoubleBlockHalf.UPPER) {
                BlockState belowTwo = world.getBlockState(pos.below(2));
                // Vegetation on a Terrain Slabs surface must sit FLUSH (TS positions vegetation via its
                // own model; Slabbed must not also lower it). The LOWER half is already TS-gated by
                // hasBottomSlabBelow → shouldSkipSlabSupport, so it reads 0.0 on a TS slab. The UPPER half
                // checked pos.below(2) with a BARE isBottomSlab, which returns true for a TS slab (it
                // extends SlabBlock) → shouldOffset(UPPER)=true → the geometric lane returns -0.5 (live:
                // "Sunflower/Tall Grass dy=-0.500 on TS"), splitting the plant (lower 0.0 / upper -0.5).
                // Gate the UPPER half the SAME way for vegetation only. NON-vegetation double-blocks
                // (doors) intentionally keep lowering on TS via the P0.4 directCustom path, so do NOT
                // gate them here — both their halves stay -0.5. No-op without TS loaded
                // (shouldSkipSlabSupport is always false → bare isBottomSlab path unchanged on vanilla).
                if (state.getBlock() instanceof VegetationBlock
                        && CompatHooks.shouldSkipSlabSupport(belowTwo)) {
                    return false;
                }
                return isBottomSlab(belowTwo);
            }
            return hasBottomSlabBelow(world, pos);
        }

        // ── direct + recursive chain ──────────────────────────────────
        // Intended product behavior: ordinary full blocks may anchor to slab
        // columns as long as this remains in slab context.
        if (hasSlabInColumn(world, pos)) {
            return true;
        }

        // ── wall-attached blocks: check the block they're mounted on ──
        Block block = state.getBlock();
        if ((block instanceof WallSignBlock || block instanceof WallBannerBlock
                || block instanceof WallTorchBlock || block instanceof WallHangingSignBlock)
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            BlockPos attachedPos = pos.relative(facing.getOpposite());
            if (hasSlabInColumn(world, attachedPos)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the Y offset for the block at {@code pos}.
     * <ul>
     *   <li>{@code -0.5} for blocks sitting above a bottom slab (or chain).</li>
     *   <li>{@code +0.5} for hanging blocks (HANGING=true) directly below a top slab.</li>
     *   <li>{@code 0.0} otherwise (no offset).</li>
     * </ul>
     */
    public static double getYOffset(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null) {
            return 0.0;
        }
        if (state == null || state.isAir()) {
            return 0.0;
        }
        if (CompatHooks.shouldSkipOffset(state)) {
            return 0.0;
        }

        // Recursion guard: isSolidBlock → getCollisionShape → getOutlineShape (mixin) → getYOffset
        if (IN_GET_Y_OFFSET.get()) {
            return 0.0;
        }
        IN_GET_Y_OFFSET.set(Boolean.TRUE);
        try {
            return getYOffsetInner(world, pos, state);
        } finally {
            IN_GET_Y_OFFSET.set(Boolean.FALSE);
        }
    }

    /**
     * COLLISION-FOLLOW (movement broadphase): toggle with -Dslabbed.collisionFollow=false.
     * Slabbed lowers a block's visual/outline/raycast but leaves its per-state collision shape
     * VANILLA (within-cell) so MC's cell-bounded {@code BlockCollisions} broadphase samples it.
     * That alone leaves a half-block gap: a lowered slab/block's collision sits at its un-lowered
     * height, so the player clips INTO the lowered visual from the side. The visual collision lives
     * partly in the cell BELOW (it hangs down by |dy|), which the cell-bounded broadphase never asks
     * for. {@link #withHangingLoweredCollisionFromAbove} is the neighbour-aware half: when the
     * broadphase queries a cell, it also unions in the hanging part of a lowered block directly
     * above, so the lowered geometry is solid exactly where it is drawn.
     */
    public static final boolean COLLISION_FOLLOW =
            !"false".equalsIgnoreCase(System.getProperty("slabbed.collisionFollow", "true"));

    /**
     * Given a cell's own collision shape (local frame) and position, unions in the part of a
     * LOWERED block directly above that hangs down into this cell, so the broadphase yields the
     * lowered block's collision at its visual position even when the player's box does not reach
     * the lowered block's own cell. Called from {@code BlockCollisionsLoweredAboveMixin} (the single
     * {@code getCollisionShape} site in {@code BlockCollisions.computeNext}). Cheap-gated: returns
     * {@code own} immediately for air-above and non-lowered-above (the overwhelming common case).
     */
    public static VoxelShape withHangingLoweredCollisionFromAbove(
            VoxelShape own, CollisionGetter getter, BlockPos pos) {
        if (!COLLISION_FOLLOW || getter == null || pos == null) {
            return own;
        }
        BlockPos abovePos = pos.above();
        BlockState above = getter.getBlockState(abovePos);
        if (above.isAir() || !above.getFluidState().isEmpty()) {
            return own;
        }
        if (above.getBlock() instanceof ScaffoldingBlock) {
            return own;
        }
        double dy = getYOffset(getter, abovePos, above);
        if (dy >= -1.0e-6) {
            return own;
        }
        // The above block's collision stays VANILLA per-state (its outline/visual is lowered by dy).
        // Its visual collision = vanilla.move(0, dy, 0); the part hanging into THIS cell (the cell
        // below) is that, expressed in this cell's local frame, i.e. vanilla.move(0, dy + 1, 0).
        VoxelShape aboveVanilla = above.getCollisionShape(getter, abovePos, CollisionContext.empty());
        if (aboveVanilla.isEmpty()) {
            return own;
        }
        VoxelShape hanging = aboveVanilla.move(0.0, dy + 1.0, 0.0);
        return own.isEmpty() ? hanging : Shapes.or(own, hanging);
    }

    /**
     * Returns true if {@code slabPos} holds any slab variant (BOTTOM, TOP, or
     * DOUBLE) that is adjacent to a solid full block sitting on a bottom slab —
     * the exact condition that gives the slab its -0.5 adjacent-side-slab dy.
     * Does not call getYOffset (safe inside the IN_GET_Y_OFFSET recursion guard).
     *
     * <p>BOTTOM → BS-FB-0.5S (slab visually fills the lower half beside the
     * lowered FB, world Y span [pos.y - 0.5, pos.y]).
     * <br>TOP → BS-FB-1S (slab visually fills the upper half beside the lowered
     * FB, world Y span [pos.y, pos.y + 0.5]).
     * <br>DOUBLE → full-cube alignment with the lowered FB.
     */
    public static boolean isCompatibleLoweredSlabLane(SlabType existingType, SlabType incomingType) {
        return existingType == incomingType
                || existingType == SlabType.DOUBLE
                || incomingType == SlabType.DOUBLE;
    }

    public record CompoundSlabRemapDecision(
            boolean legal,
            BlockPos sourcePos,
            BlockPos legalLanePos,
            BlockPos candidatePlacementPos,
            SlabType resultType,
            String reason
    ) {
        private static CompoundSlabRemapDecision rejected(
                BlockPos sourcePos,
                BlockPos legalLanePos,
                BlockPos candidatePlacementPos,
                String reason
        ) {
            return new CompoundSlabRemapDecision(false, sourcePos, legalLanePos, candidatePlacementPos, null, reason);
        }
    }

    public static CompoundSlabRemapDecision findLegalCompoundSlabRemap(
            BlockGetter world,
            BlockPos sourcePos,
            BlockState sourceState,
            Direction intendedDirection,
            Vec3 hitPos
    ) {
        if (world == null || sourcePos == null || sourceState == null || intendedDirection == null) {
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    CompoundSlabRemapDecision.rejected(sourcePos, null, null, "missing_context"));
        }
        if (sourceState.getBlock() instanceof SlabBlock
                || !SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, sourcePos, sourceState)
                || !SlabAnchorAttachment.isCompoundFullBlockAnchor(world, sourcePos)
                || Math.abs(getYOffset(world, sourcePos, sourceState) + 1.0d) > 1.0e-6d) {
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    CompoundSlabRemapDecision.rejected(sourcePos, null, null, "source_not_compound_full_block_dy_-1"));
        }
        if (intendedDirection == Direction.UP) {
            BlockPos candidatePlacementPos = sourcePos.above();
            BlockState candidateState = world.getBlockState(candidatePlacementPos);
            if (!candidateState.isAir()) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        CompoundSlabRemapDecision.rejected(
                        sourcePos,
                        sourcePos,
                        candidatePlacementPos,
                        "compound_visible_owner_top_candidate_not_air"));
            }
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    new CompoundSlabRemapDecision(
                    true,
                    sourcePos,
                    sourcePos,
                    candidatePlacementPos,
                    SlabType.BOTTOM,
                    "COMPOUND_VISIBLE_OWNER_TOP_SLAB"));
        }
        if (intendedDirection.getAxis().isVertical()) {
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    CompoundSlabRemapDecision.rejected(sourcePos, null, null, "direction_not_horizontal"));
        }

        BlockPos intendedLanePos = sourcePos.relative(intendedDirection);
        int legalLaneCount = 0;
        BlockPos legalLanePos = null;
        BlockState legalLaneState = null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos lanePos = sourcePos.relative(direction);
            BlockState laneState = world.getBlockState(lanePos);
            if (isLegalCompoundRemapLane(world, lanePos, laneState)) {
                legalLaneCount++;
                legalLanePos = lanePos;
                legalLaneState = laneState;
            }
        }

        if (legalLaneCount == 1 && intendedLanePos.equals(legalLanePos)) {
            BlockPos candidatePlacementPos = legalLanePos.relative(intendedDirection);
            BlockState candidateState = world.getBlockState(candidatePlacementPos);
            if (!candidateState.isAir()) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        CompoundSlabRemapDecision.rejected(
                        sourcePos,
                        legalLanePos,
                        candidatePlacementPos,
                        "candidate_not_air"));
            }

            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    new CompoundSlabRemapDecision(
                    true,
                    sourcePos,
                    legalLanePos,
                    candidatePlacementPos,
                    legalLaneState.getValue(SlabBlock.TYPE),
                    "COMPOUND_HORIZONTAL_CONTINUATION_LANE"));
        }

        if (isCompoundVisibleSideLowerHit(world, sourcePos, sourceState, hitPos)) {
            BlockState candidateState = world.getBlockState(intendedLanePos);
            if (isMarkedCompoundVisibleSideSlab(world, intendedLanePos, candidateState)) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        new CompoundSlabRemapDecision(
                        true,
                        sourcePos,
                        sourcePos,
                        intendedLanePos,
                        SlabType.DOUBLE,
                        "COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB"));
            }
            if (!candidateState.isAir()) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        CompoundSlabRemapDecision.rejected(
                        sourcePos,
                        sourcePos,
                        intendedLanePos,
                        "compound_visible_side_lower_candidate_not_air"));
            }
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    new CompoundSlabRemapDecision(
                    true,
                    sourcePos,
                    sourcePos,
                    intendedLanePos,
                    SlabType.BOTTOM,
                    "COMPOUND_VISIBLE_SIDE_LOWER_SLAB"));
        }
        if (isCompoundVisibleSideUpperHit(world, sourcePos, sourceState, hitPos)) {
            BlockState candidateState = world.getBlockState(intendedLanePos);
            if (isMarkedCompoundVisibleSideSlab(world, intendedLanePos, candidateState)) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        new CompoundSlabRemapDecision(
                        true,
                        sourcePos,
                        sourcePos,
                        intendedLanePos,
                        SlabType.DOUBLE,
                        "COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB"));
            }
            if (!candidateState.isAir()) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        CompoundSlabRemapDecision.rejected(
                        sourcePos,
                        sourcePos,
                        intendedLanePos,
                        "compound_visible_side_upper_candidate_not_air"));
            }
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    new CompoundSlabRemapDecision(
                    true,
                    sourcePos,
                    sourcePos,
                    intendedLanePos,
                    SlabType.TOP,
                    "COMPOUND_VISIBLE_SIDE_UPPER_SLAB"));
        }

        BlockState belowSourceState = world.getBlockState(sourcePos.below());
        BlockPos candidatePlacementPos = intendedLanePos;
        BlockState candidateState = world.getBlockState(candidatePlacementPos);
        if (legalLaneCount == 0 && isLegalCompoundRemapLane(world, sourcePos.below(), belowSourceState)) {
            if (!candidateState.isAir()) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        CompoundSlabRemapDecision.rejected(
                        sourcePos,
                        sourcePos,
                        candidatePlacementPos,
                        "below_lane_candidate_not_air"));
            }
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    new CompoundSlabRemapDecision(
                    true,
                    sourcePos,
                    sourcePos,
                    candidatePlacementPos,
                    compoundBelowLaneResultType(sourcePos, hitPos),
                    "COMPOUND_BELOW_LANE_SIDE_SLAB"));
        }

        if (legalLaneCount == 0 && isPersistentVisibleCompoundOwner(world, sourcePos, sourceState)) {
            if (!candidateState.isAir()) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        CompoundSlabRemapDecision.rejected(
                        sourcePos,
                        sourcePos,
                        candidatePlacementPos,
                        "persistent_visible_owner_candidate_not_air"));
            }
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    new CompoundSlabRemapDecision(
                    true,
                    sourcePos,
                    sourcePos,
                    candidatePlacementPos,
                    compoundBelowLaneResultType(sourcePos, hitPos),
                    "COMPOUND_SUPPORT_MISSING_VISIBLE_OWNER_SIDE_SLAB"));
        }

        return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                CompoundSlabRemapDecision.rejected(
                sourcePos,
                legalLanePos,
                legalLanePos == null ? intendedLanePos : legalLanePos.relative(intendedDirection),
                "legal_lane_count_" + legalLaneCount + "_or_not_in_intended_direction"));
    }

    private static CompoundSlabRemapDecision traceCompoundSlabRemap(
            BlockGetter world,
            BlockPos sourcePos,
            BlockState sourceState,
            Direction intendedDirection,
            Vec3 hitPos,
            CompoundSlabRemapDecision decision
    ) {
        // Beta4ManualLiveTrace is deferred for the 26.1.2 port; keep the decision path unchanged.
        return decision;
    }

    private static SlabType compoundBelowLaneResultType(BlockPos sourcePos, Vec3 hitPos) {
        if (sourcePos != null && hitPos != null && hitPos.y >= sourcePos.getY()) {
            return SlabType.TOP;
        }
        return SlabType.BOTTOM;
    }

    private static boolean isCompoundVisibleSideLowerHit(
            BlockGetter world,
            BlockPos sourcePos,
            BlockState sourceState,
            Vec3 hitPos
    ) {
        if (world == null || sourcePos == null || sourceState == null || hitPos == null) {
            return false;
        }
        double sourceDy = getYOffset(world, sourcePos, sourceState);
        // The compound-visible-side concept applies ONLY to a genuine -1.0 COMPOUND source: its visible
        // body spans a full cell with distinct upper/lower halves a side slab merges into at -1.0. A
        // merely -0.5 lowered block (or a flush block) has no compound side — a slab placed against it
        // must merge at the neighbour's actual magnitude via the normal cantilever lane (RC2-A), NOT be
        // forced to -1.0. Without this gate, aiming at the side of a -0.5 block set the compound marker
        // and the slab overshot to -1.0 (Julia: "upper-half placement lands wrong").
        if (sourceDy > -1.0d + 1.0e-6d) {
            return false;
        }
        double localVisibleY = hitPos.y - (sourcePos.getY() + sourceDy);
        return localVisibleY >= -1.0e-6d && localVisibleY < 0.5d - 1.0e-6d;
    }

    private static boolean isCompoundVisibleSideUpperHit(
            BlockGetter world,
            BlockPos sourcePos,
            BlockState sourceState,
            Vec3 hitPos
    ) {
        if (world == null || sourcePos == null || sourceState == null || hitPos == null) {
            return false;
        }
        double sourceDy = getYOffset(world, sourcePos, sourceState);
        // Compound-visible-side applies ONLY to a genuine -1.0 COMPOUND source (see the LOWER variant):
        // a -0.5 lowered block has no compound side, so a slab placed against its upper half merges at
        // -0.5 via the cantilever lane instead of overshooting to -1.0.
        if (sourceDy > -1.0d + 1.0e-6d) {
            return false;
        }
        double localVisibleY = hitPos.y - (sourcePos.getY() + sourceDy);
        return localVisibleY >= 0.5d - 1.0e-6d && localVisibleY <= 1.0d + 1.0e-6d;
    }

    private static boolean isMarkedCompoundVisibleSideSlab(BlockGetter world, BlockPos pos, BlockState state) {
        return SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state);
    }

    private static boolean isPersistentVisibleCompoundOwner(BlockGetter world, BlockPos pos, BlockState state) {
        return SlabAnchorAttachment.isAnchored(world, pos)
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, pos, state)
                && Math.abs(getYOffset(world, pos, state) + 1.0d) <= 1.0e-6d;
    }

    private static boolean isLegalCompoundRemapLane(BlockGetter world, BlockPos lanePos, BlockState laneState) {
        return laneState != null
                && laneState.getBlock() instanceof SlabBlock
                && laneState.hasProperty(SlabBlock.TYPE)
                && laneState.getValue(SlabBlock.TYPE) != SlabType.DOUBLE
                && laneState.getFluidState().isEmpty()
                && Math.abs(getYOffset(world, lanePos, laneState) + 0.5d) <= 1.0e-6d
                && isLoweredSideLaneSlabCarrier(world, lanePos, laneState);
    }

    private static boolean isCompatibleLoweredSlabLane(BlockState a, BlockState b) {
        if (!a.hasProperty(SlabBlock.TYPE) || !b.hasProperty(SlabBlock.TYPE)) {
            return false;
        }
        SlabType aType = a.getValue(SlabBlock.TYPE);
        SlabType bType = b.getValue(SlabBlock.TYPE);
        return isCompatibleLoweredSlabLane(aType, bType);
    }

    private static boolean hasLoweredSolidSideSupport(BlockGetter world, BlockPos slabPos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = slabPos.relative(dir);
            BlockState neighbor = world.getBlockState(neighborPos);
            if (isFullHeightLoweredCarrierForSideSupport(world, neighborPos, neighbor)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAnchoredLoweredFullBlock(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidRender()) {
            return false;
        }
        return SlabAnchorAttachment.isAnchored(world, pos);
    }

    public static boolean isLoweredDoubleSlabCarrier(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)
                || state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
            return true;
        }
        return SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state);
    }

    public static boolean isFullHeightLoweredCarrier(BlockGetter world, BlockPos pos, BlockState state) {
        return isLoweredFullBlockCarrier(world, pos, state)
                || isLoweredDoubleSlabCarrier(world, pos, state);
    }

    public static boolean isLoweredSideLaneDoubleCarrier(BlockGetter world, BlockPos pos, BlockState state) {
        return isLoweredSideLaneSlabCarrier(world, pos, state)
                && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    public static boolean isLoweredSideLaneSlabCarrier(BlockGetter world, BlockPos pos, BlockState state) {
        return world != null
                && pos != null
                && state != null
                && state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getFluidState().isEmpty()
                && isAdjacentSideSlabLowered(world, pos, state);
    }

    /**
     * Beta4 compound-source predicate: the position is a bottom slab that is itself
     * lowered (persistent lowered slab carrier or adjacent-side-slab lowered), so an
     * ordinary full block placed directly above it is authored at compound lane
     * {@code dy=-1.0}. Mirrors the inline check inside the anchored compound branch
     * of {@link #getYOffsetInner}; exposed publicly so
     * {@link com.slabbed.anchor.SlabAnchorAttachment#qualifiesForCompoundFullBlockAnchor}
     * can decide sidecar authoring without duplicating the logic.
     */
    public static boolean isLoweredCompoundSourceSlab(BlockGetter world, BlockPos pos, BlockState state) {
        return state != null
                && isBottomSlab(state)
                && isAdjacentSideSlabLowered(world, pos, state);
    }

    public static boolean isBottomSlabLoweredByCarrierBelow(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null
                || pos == null
                || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)
                || state.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                || !state.getFluidState().isEmpty()) {
            return false;
        }

        BlockPos belowPos = pos.below();
        BlockState below = world.getBlockState(belowPos);
        boolean backedByLoweredCarrier = below.getBlock() instanceof SlabBlock
                ? isLoweredDoubleSlabCarrier(world, belowPos, below)
                : hasLoweredCarrierBelow(world, pos);
        return backedByLoweredCarrier && getYOffset(world, pos, state) == -0.5d;
    }

    private static boolean isLoweredCarrier(BlockGetter world, BlockPos pos, BlockState state, int depth) {
        return isLoweredCarrier(world, pos, state, depth, true);
    }

    private static boolean isLoweredCarrier(
            BlockGetter world,
            BlockPos pos,
            BlockState state,
            int depth,
            boolean allowSideLane
    ) {
        if (world == null || pos == null || state == null || depth <= 0) {
            return false;
        }
        if (state.getBlock() instanceof SlabBlock) {
            if (!state.hasProperty(SlabBlock.TYPE)
                    || state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE
                    || !state.getFluidState().isEmpty()) {
                return false;
            }
            if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
                return true;
            }
            if (allowSideLane && isAdjacentSideSlabLowered(world, pos, state)) {
                return true;
            }
            BlockPos belowPos = pos.below();
            return isLoweredCarrier(world, belowPos, world.getBlockState(belowPos), depth - 1, allowSideLane);
        }
        return isLoweredFullBlockCarrier(world, pos, state);
    }

    private static boolean isLoweredDoubleSlabCarrierForSideSupport(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)
                || state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
            return true;
        }
        return isLoweredCarrier(world, pos.below(), world.getBlockState(pos.below()), MAX_CHAIN_DEPTH, false);
    }

    private static boolean isFullHeightLoweredCarrierForSideSupport(BlockGetter world, BlockPos pos, BlockState state) {
        return isLoweredFullBlockCarrier(world, pos, state)
                || isLoweredDoubleSlabCarrierForSideSupport(world, pos, state);
    }

    private static boolean isLoweredFullBlockCarrier(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidRender()) {
            return false;
        }
        boolean hasBottomBelow = hasBottomSlabBelow(world, pos);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        return hasBottomBelow || anchored;
    }

    /**
     * A solid, non-slab, non-block-entity full block with AIR directly below it — i.e. a block
     * cantilevered out over empty space, the only case where merging a full block down to a lowered
     * neighbour is wanted (a block resting on solid ground must NOT sink). Recursion-safe
     * ({@link #isSolidRender}-style checks only); never calls {@link #getYOffset}.
     * Port of 1.21.1 {@code 9a24670c}.
     */
    private static boolean isCantileverFullBlockCandidate(BlockGetter world, BlockPos pos, BlockState state) {
        return world != null
                && pos != null
                && state != null
                && !state.isAir()
                && !(state.getBlock() instanceof SlabBlock)
                && !(state.getBlock() instanceof EntityBlock)
                && state.getFluidState().isEmpty()
                && state.isSolidRender()
                && world.getBlockState(pos.below()).isAir();
    }

    /**
     * A full block that is GENUINELY lowered by its own support — a slab directly below, a
     * direct/vertical anchor, or a lowered column reaching a slab beneath it (e.g. an upper log in a
     * lowered trunk). This is the "anchor" a cantilever lane may hang off of; cantilever-lowered
     * blocks themselves are deliberately excluded (they are lane members, reached by the walk, not
     * sources) so lowering can never be self-sustaining without a real support. Recursion-safe:
     * every predicate here is already invoked by {@link #getYOffsetInner} under the
     * {@link #IN_GET_Y_OFFSET} guard and none call {@link #getYOffset}. Port of 1.21.1 {@code 9a24670c}.
     */
    private static boolean isGenuinelyLoweredFullBlockSource(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidRender()) {
            return false;
        }
        return hasBottomSlabBelow(world, pos)
                || SlabAnchorAttachment.isAnchored(world, pos)
                || (shouldOffset(world, pos, state) && slabColumnYOffset(world, pos) < -1.0e-6);
    }

    /**
     * Geometric, recursion-safe replacement for the (removed) stale side-adjacent full-block anchor:
     * a full block cantilevered over air lowers {@code -0.5} to merge flush with a lowered tower it
     * is connected to, computed live so it recomputes whenever the structure changes — it never goes
     * stale and never "pops" out of sync with its neighbours.
     *
     * <p>Breadth-first walk through connected cantilever full blocks (each over air), bounded by
     * {@link #MAX_CHAIN_DEPTH}, returning true as soon as the lane reaches a GENUINE lowered source:
     * a full-block carrier lowered by a slab below it or a direct/vertical anchor
     * ({@link #isGenuinelyLoweredFullBlockSource}), or a lowered slab
     * ({@link #isAdjacentSideSlabLowered}) so mixed slab+block canopies settle at one consistent
     * level. Calls neither {@link #getYOffset} nor itself with circular dependence, so it is safe
     * inside the {@link #IN_GET_Y_OFFSET} guard. Port of 1.21.1 {@code 9a24670c}.
     */
    private static boolean isCantileverLoweredFullBlock(BlockGetter world, BlockPos pos, BlockState state) {
        if (!isCantileverFullBlockCandidate(world, pos, state)) {
            return false;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(pos);
        visited.add(pos.asLong());
        while (!queue.isEmpty() && visited.size() <= MAX_CHAIN_DEPTH) {
            BlockPos cursor = queue.removeFirst();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = cursor.relative(dir);
                BlockState neighbor = world.getBlockState(neighborPos);
                if (neighbor == null || neighbor.isAir()) {
                    continue;
                }
                boolean neighborIsSlab = neighbor.getBlock() instanceof SlabBlock;
                // Genuine lowered source: a full block lowered by its own support
                // (slab-below / anchor / lowered column), or a lowered slab. Reaching
                // one lowers the whole cantilever lane.
                if (!neighborIsSlab && isGenuinelyLoweredFullBlockSource(world, neighborPos, neighbor)) {
                    return true;
                }
                if (neighborIsSlab && isAdjacentSideSlabLowered(world, neighborPos, neighbor)) {
                    return true;
                }
                // Propagate only through further cantilever full blocks (over air).
                if (!neighborIsSlab
                        && isCantileverFullBlockCandidate(world, neighborPos, neighbor)
                        && visited.add(neighborPos.asLong())) {
                    queue.addLast(neighborPos);
                }
            }
        }
        return false;
    }

    // ── RC2 (WYSIWYG cantilever side-merge) ───────────────────────────────────────────────────────

    /**
     * RC2-A: true when a same-Y HORIZONTAL neighbour of {@code pos} is a lowered FULL BLOCK — either a
     * GENUINE lowered source ({@link #isGenuinelyLoweredFullBlockSource}: slab-below / anchor / lowered
     * column) or a member of a cantilever lane that itself reaches a genuine source
     * ({@link #isCantileverLoweredFullBlock}). The geometric basis for lowering a SLAB placed
     * cantilevered over air beside a lowered full block so it lands flush with the aimed -0.5 surface
     * (WYSIWYG). The caller air-gates {@code pos.below()} (NEVER-POP rail); no air-gate is needed here.
     *
     * <p>Recursion-safe: invoked only inside {@link #getYOffsetInner} under the {@link #IN_GET_Y_OFFSET}
     * guard; calls neither {@link #getYOffset} nor itself; both delegated predicates are already used by
     * the existing cantilever clause at the bottom of {@link #getYOffsetInner} and are
     * {@link #MAX_CHAIN_DEPTH}-bounded. No lane self-sustains: this inspects NEIGHBOURS only, and the
     * sources exclude pure cantilever members ({@link #isGenuinelyLoweredFullBlockSource} requires a
     * real support; {@link #isCantileverLoweredFullBlock} bottoms out at a genuine source).
     */
    private static double adjacentLoweredSideMagnitude(BlockGetter world, BlockPos pos) {
        if (world == null || pos == null) {
            return Double.NaN;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos nPos = pos.relative(dir);
            BlockState n = world.getBlockState(nPos);
            if (n == null || n.isAir()) {
                continue;
            }
            if (n.getBlock() instanceof SlabBlock) {
                // GAP-2: a BARE single lowered SLAB neighbour (no full-block column under it) is a valid
                // cantilever source — Julia's scenario #3 in pure form. The old code 'continue'd on every
                // slab neighbour, so this read 0.0 (the slab floated half a block too high). Detect
                // recursion-safely and carry its magnitude out (-0.5, or -1.0 for a compound side slab).
                double m = loweredSlabMagnitude(world, nPos, n);
                if (!Double.isNaN(m)) {
                    return m;
                }
                continue;
            }
            // GAP-1: a lowered full block (genuine source, or a member of a cantilever lane that itself
            // reaches a genuine source) — carry its ACTUAL magnitude out (-1.0 compound, else -0.5),
            // instead of the old hardcoded -0.5 at the call-site.
            if (isGenuinelyLoweredFullBlockSource(world, nPos, n)
                    || isCantileverLoweredFullBlock(world, nPos, n)) {
                double m = loweredFullBlockMagnitude(world, nPos, n);
                return Double.isNaN(m) ? -0.5 : m;
            }
        }
        return Double.NaN;
    }

    /**
     * RC2-B: a connecting block (fence / wall / iron-bars) is a cantilever START/PROPAGATE candidate
     * when it is over AIR, not a slab, not a block-entity, fluid-free. Mirrors
     * {@link #isCantileverFullBlockCandidate} but deliberately DROPS the {@code isSolidRender()} gate —
     * a fence/wall/bar is not solid-render yet is still a structural connector that must merge flush
     * with a lowered neighbour. Air-gated ({@code pos.below()} must be air) → the NEVER-POP rail.
     */
    private static boolean isCantileverConnectingCandidate(BlockGetter world, BlockPos pos, BlockState state) {
        return world != null
                && pos != null
                && state != null
                && !state.isAir()
                && (state.getBlock() instanceof FenceBlock
                        || state.getBlock() instanceof WallBlock
                        || state.getBlock() instanceof IronBarsBlock)
                && !(state.getBlock() instanceof EntityBlock)
                && state.getFluidState().isEmpty()
                && world.getBlockState(pos.below()).isAir();
    }

    /**
     * RC2-B: a fence / wall / iron-bars cantilevered over AIR and connected — through further
     * cantilevered connecting blocks — to a GENUINE lowered source merges -0.5 to sit flush with the
     * aimed lowered surface (WYSIWYG). Connecting-block analogue of {@link #isCantileverLoweredFullBlock};
     * the only difference is the START/PROPAGATE candidate ({@link #isCantileverConnectingCandidate},
     * which drops {@code isSolidRender}). Sources are the SOLID-RENDER full-block sources
     * ({@link #isGenuinelyLoweredFullBlockSource} / {@link #isCantileverLoweredFullBlock}) or a lowered
     * slab ({@link #isAdjacentSideSlabLowered}) — NEVER another cantilever member — so the lane cannot
     * self-sustain. Recursion-safe (no {@link #getYOffset}); {@link #MAX_CHAIN_DEPTH}-bounded BFS that
     * propagates ONLY through further cantilever connecting candidates (each over air).
     */
    private static double cantileverLoweredConnectingMagnitude(BlockGetter world, BlockPos pos, BlockState state) {
        if (!isCantileverConnectingCandidate(world, pos, state)) {
            return Double.NaN;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(pos);
        visited.add(pos.asLong());
        while (!queue.isEmpty() && visited.size() <= MAX_CHAIN_DEPTH) {
            BlockPos cursor = queue.removeFirst();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = cursor.relative(dir);
                BlockState neighbor = world.getBlockState(neighborPos);
                if (neighbor == null || neighbor.isAir()) {
                    continue;
                }
                boolean neighborIsSlab = neighbor.getBlock() instanceof SlabBlock;
                // GENUINE lowered sources only (a cantilever full block itself bottoms out at one).
                // GAP-1: carry the source's ACTUAL magnitude out of the BFS (-1.0 compound, else -0.5).
                if (!neighborIsSlab && (isGenuinelyLoweredFullBlockSource(world, neighborPos, neighbor)
                        || isCantileverLoweredFullBlock(world, neighborPos, neighbor))) {
                    double m = loweredFullBlockMagnitude(world, neighborPos, neighbor);
                    return Double.isNaN(m) ? -0.5 : m;
                }
                if (neighborIsSlab && isAdjacentSideSlabLowered(world, neighborPos, neighbor)) {
                    double m = loweredSlabMagnitude(world, neighborPos, neighbor);
                    return Double.isNaN(m) ? -0.5 : m;
                }
                double connectingSource = loweredSupportedConnectingMagnitude(world, neighborPos, neighbor);
                if (!Double.isNaN(connectingSource)) {
                    return connectingSource;
                }
                // Propagate only through further cantilevered connecting blocks (each over air).
                if (isCantileverConnectingCandidate(world, neighborPos, neighbor)
                        && visited.add(neighborPos.asLong())) {
                    queue.addLast(neighborPos);
                }
            }
        }
        return Double.NaN;
    }

    private static double loweredSupportedConnectingMagnitude(BlockGetter world, BlockPos pos, BlockState state) {
        if (!isBeta35FenceWallVariantContactObject(state)
                || isCantileverConnectingCandidate(world, pos, state)) {
            return Double.NaN;
        }
        double contactDy = beta35FenceWallVariantContactDy(world, pos, state);
        if (Double.isFinite(contactDy) && contactDy < -1.0e-6d) {
            return contactDy;
        }
        if (!shouldOffset(world, pos, state)) {
            return Double.NaN;
        }
        BlockState below = world.getBlockState(pos.below());
        if (isBottomSlab(below) && isAdjacentSideSlabLowered(world, pos.below(), below)) {
            return -1.0d;
        }
        double columnDy = slabColumnYOffset(world, pos);
        if (columnDy < -1.0e-6d) {
            return columnDy;
        }
        return -0.5d;
    }

    /**
     * Returns true when the non-slab solid block at {@code pos} carries compound dy=-1.0 —
     * i.e. the same conditions that cause {@link #getYOffsetInner} to return -1.0 for it.
     * Safe to call inside the IN_GET_Y_OFFSET recursion guard: does not delegate to getYOffset.
     * Used exclusively by the floor-torch full-block support branch.
     */
    private static boolean isOrdinaryFullBlockWithCompoundDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidRender()) {
            return false;
        }
        if (!SlabAnchorAttachment.isAnchored(world, pos)) {
            return false;
        }
        if (SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)) {
            return true;
        }
        BlockState below = world.getBlockState(pos.below());
        return isBottomSlab(below) && isAdjacentSideSlabLowered(world, pos.below(), below);
    }

    /**
     * RC2 GAP-1 magnitude reader. The actual lowered dy of the FULL BLOCK at {@code pos} as a
     * magnitude — {@code -1.0} for a compound source, else {@code -0.5} — or {@code NaN} when it is not a
     * full block at all (air / slab / fluid / non-solid-render). The caller is responsible for first
     * establishing that {@code pos} is a genuine lowered source
     * ({@link #isGenuinelyLoweredFullBlockSource} or {@link #isCantileverLoweredFullBlock}); this only
     * reads how FAR it is lowered.
     *
     * <p>It inspects only the SAME recursion-safe conditions {@link #getYOffsetInner} itself uses to
     * MINT a full block's -1.0: the anchor/compound path ({@link #isOrdinaryFullBlockWithCompoundDy},
     * which already short-circuits the -1.0 compound-anchor + geometric-below-slab cases) AND the
     * geometric compound-on-lowered-bottom-slab path — a full block sitting on a bottom slab whose own
     * lowered dy ({@link #floorTorchBottomSlabSupportDy}) is already < 0 compounds to
     * {@code supportDy - 0.5} (exactly {@link #beta35OrdinaryFullBlockContactDy}'s rule, which mints the
     * -1.0 for an unanchored {@code helper.setBlock} terrain stack as in
     * {@code rc2CompoundStackTopStillMinusOne}). Authored compound stacks fall out the anchor path;
     * geometric compound stacks fall out the support-slab-dy path. Floored at -1.0 (the dy>=-1.0
     * invariant); a non-lowered support yields -0.5 (the caller already proved this is a lowered source).
     *
     * <p>Recursion-safe: calls neither {@link #getYOffset} nor {@link #getYOffsetInner} nor
     * {@link #slabColumnYOffset} nor itself. {@code isOrdinaryFullBlockWithCompoundDy} and
     * {@code floorTorchBottomSlabSupportDy} are both already invoked inside {@link #getYOffsetInner}
     * under the {@link #IN_GET_Y_OFFSET} guard; {@link #MAX_CHAIN_DEPTH}-bounded.
     */
    private static double loweredFullBlockMagnitude(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidRender()) {
            return Double.NaN;
        }
        // -1.0 via the anchor/compound markers (authored compound stacks).
        if (isOrdinaryFullBlockWithCompoundDy(world, pos, state)) {
            return -1.0;
        }
        // Geometric compound: a full block on a bottom slab that is ITSELF lowered compounds to
        // supportDy - 0.5 (the same rule as beta35OrdinaryFullBlockContactDy, which mints -1.0 for an
        // unanchored helper.setBlock terrain stack). floorTorchBottomSlabSupportDy is recursion-safe.
        BlockState below = world.getBlockState(pos.below());
        double supportDy = floorTorchBottomSlabSupportDy(world, pos.below(), below);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6) {
            return Math.max(-1.0, supportDy - 0.5);
        }
        return -0.5;
    }

    /**
     * RC2 GAP-2 magnitude reader. The actual lowered dy of the SLAB at {@code pos} as a magnitude —
     * {@code -1.0} for a compound-visible side slab, else {@code -0.5} when it is genuinely lowered
     * (anchored / side-slab lane / persistent lowered carrier) — or {@code NaN} when the slab is not
     * lowered at all. This is what surfaces a BARE single lowered slab (no full-block column under it)
     * so a slab/connector cantilevered beside it merges flush instead of reading 0.0.
     *
     * <p>Recursion-safe: only the {@code SlabAnchorAttachment.isCompoundVisibleSide*}/{@code isAnchored}
     * chunk-attachment lookups (the SAME markers the slab branch of {@link #getYOffsetInner} reads),
     * {@link #isAdjacentSideSlabLowered}, and {@link #isPersistentLoweredBottomSlabCarrier}-style
     * non-recursive carrier query are consulted; none call {@link #getYOffset}.
     */
    private static double loweredSlabMagnitude(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)) {
            return Double.NaN;
        }
        // -1.0 compound side-slab cases (same markers the slab branch reads to mint -1.0).
        if (SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return -1.0;
        }
        // A genuinely-lowered slab is -0.5: an anchored (freeze-on-place) slab, a side-slab lane slab,
        // or a persistent lowered bottom-slab carrier (a BARE single lowered slab matches one of these).
        if (SlabAnchorAttachment.isAnchored(world, pos)
                || isAdjacentSideSlabLowered(world, pos, state)
                || (state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                        && state.getFluidState().isEmpty()
                        && SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state))) {
            return -0.5;
        }
        return Double.NaN;
    }

    private static boolean hasLoweredCarrierBelow(BlockGetter world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockPos belowPos = pos.below();
        return isLoweredCarrier(world, belowPos, world.getBlockState(belowPos), MAX_CHAIN_DEPTH);
    }

    private static boolean isCompoundVisibleOwnerTopSlab(BlockGetter world, BlockPos pos, BlockState state) {
        return SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state);
    }

    private static boolean hasLoweredSlabLaneSupport(BlockGetter world, BlockPos slabPos, BlockState slabState) {
        if (!(slabState.getBlock() instanceof SlabBlock) || !slabState.hasProperty(SlabBlock.TYPE)) {
            return false;
        }
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(slabPos);
        queue.add(slabPos);
        int explored = 0;

        while (!queue.isEmpty() && explored < MAX_CHAIN_DEPTH) {
            BlockPos cursor = queue.removeFirst();
            explored++;

            BlockState cursorState = world.getBlockState(cursor);
            if (!(cursorState.getBlock() instanceof SlabBlock) || !cursorState.hasProperty(SlabBlock.TYPE)) {
                continue;
            }
            if (cursor.equals(slabPos)
                    && hasLoweredSolidSideSupport(world, cursor)
                    && canUseDirectLoweredSolidSideSupportForSlabLane(world, cursor, cursorState)) {
                return true;
            }
            if (!cursor.equals(slabPos)
                    && isCompatibleLoweredSlabLane(slabState, cursorState)
                    && isLoweredSlabLaneOwnerForSideInheritance(world, cursor, cursorState)) {
                return true;
            }

            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = cursor.relative(dir);
                if (!visited.add(neighborPos)) {
                    continue;
                }
                BlockState neighborState = world.getBlockState(neighborPos);
                if (!(neighborState.getBlock() instanceof SlabBlock)) {
                    continue;
                }
                if (!isCompatibleLoweredSlabLane(cursorState, neighborState)) {
                    continue;
                }
                queue.add(neighborPos);
            }
        }
        return false;
    }

    private static boolean canUseDirectLoweredSolidSideSupportForSlabLane(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        if (state == null || !(state.getBlock() instanceof SlabBlock) || !state.hasProperty(SlabBlock.TYPE)) {
            return false;
        }
        return state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                || isNamedLoweredSlabLane(world, pos, state);
    }

    private static boolean isNamedLoweredSlabLane(BlockGetter world, BlockPos pos, BlockState state) {
        return SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state);
    }

    private static boolean isLoweredSlabLaneOwnerForSideInheritance(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        return world != null
                && pos != null
                && state != null
                && state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getFluidState().isEmpty()
                && !isCompoundVisibleOwnerTopSlab(world, pos, state)
                && isNamedLoweredSlabLane(world, pos, state);
    }

    private static boolean isAdjacentSideSlabLowered(BlockGetter world, BlockPos slabPos, BlockState slabState) {
        if (!slabState.hasProperty(SlabBlock.TYPE)) {
            return false;
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, slabPos, slabState)) {
            return true;
        }
        return hasLoweredSlabLaneSupport(world, slabPos, slabState);
    }

    /**
     * True for a slab whose lowered dy comes ONLY from a lowered SIDE neighbour (slab-lane
     * inheritance), with NO genuine lowered support directly below it. Used by freeze-on-place: a
     * freshly PLACED slab in this state must NOT snap down to the neighbour's level (Julia's
     * NEVER-POP law — a placed block stays where it was put); it freezes flat instead. A slab lowered
     * by genuine support BELOW (a lowered carrier — slab or full block) is excluded here so it still
     * legitimately follows that support down (anchored -0.5).
     */
    public static boolean slabLoweringIsSideInheritedOnly(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || !(state.getBlock() instanceof SlabBlock)) {
            return false;
        }
        // RC2-C (WYSIWYG): a slab placed CANTILEVERED over AIR beside a lowered neighbour is GENUINE
        // cantilever geometry (RC2-A lowered it -0.5 from a lowered full block, or the
        // isAdjacentSideSlabLowered branch from a lowered slab lane), NOT flush-ground side-inheritance.
        // freezeLoweredOnPlace must ANCHOR it -0.5 (its dy<0 branch -> addAnchorUnchecked), NOT mark it
        // FROZEN_FLAT — otherwise the server freezes 0.0 while the client predicts geometric -0.5 and
        // the slab snaps up (the RC1-class desync). A slab on SOLID ground (pos.below() NOT air) is
        // unchanged: it falls through to the original logic and still freezes FLAT (Julia's NEVER-POP
        // rail — a slab on its own flush ground beside a lowered lane stays at dy=0).
        if (world.getBlockState(pos.below()).isAir()) {
            return false;
        }
        if (hasLoweredCarrierBelow(world, pos)) {
            return false;
        }
        return isAdjacentSideSlabLowered(world, pos, state);
    }

    /**
     * Visual dy of a connecting block (fence / wall / iron-bars / glass-pane) — mirrors the model's
     * render offset so the stepped-connection check compares the heights things are actually DRAWN at.
     * Port of the shipped 1.21.1/1.21.11 connecting-block system (GH #21 + break-across-step).
     */
    public static double connectingBlockVisualDy(BlockGetter world, BlockPos pos, BlockState state) {
        return getYOffset(world, pos, state);
    }

    /**
     * True when {@code state} (a fence/wall/pane) and a same-family horizontal neighbour sit at
     * DIFFERENT visual heights (one lowered onto a slab, the other at grid height) — a slab-height
     * step. The connection mixins ({@code WallSlabConnectionMixin}, {@code FencePaneSlabConnectionMixin})
     * suppress the connector arm across such a step so the pair render as clean single posts; a flat run
     * at one height still connects. Port of 1.21.1/1.21.11 (PaneBlock→IronBarsBlock in Mojang names).
     */
    public static boolean isSteppedConnectingNeighbor(BlockGetter world, BlockPos pos, BlockState state,
                                                      BlockPos neighborPos, BlockState neighborState) {
        Block neighbor = neighborState.getBlock();
        if (!(neighbor instanceof FenceBlock || neighbor instanceof WallBlock || neighbor instanceof IronBarsBlock)) {
            return false;
        }
        double selfDy = connectingBlockVisualDy(world, pos, state);
        double neighborDy = connectingBlockVisualDy(world, neighborPos, neighborState);
        return Math.abs(selfDy - neighborDy) > 1.0e-6;
    }

    /**
     * True for an always-ceiling-hung decoration — hanging roots, spore blossom, hanging signs.
     * These attach to the block ABOVE and have no floor variant, so their dy must be a pure
     * function of that support and must never be lowered by a block below them in the column.
     */
    private static boolean isAlwaysCeilingHungDecoration(BlockState state) {
        Block block = state.getBlock();
        return block instanceof HangingRootsBlock
                || block instanceof SporeBlossomBlock
                || block instanceof HangingSignBlock;
    }

    /**
     * Rendered dy for an always-ceiling-hung decoration, decided SOLELY by the support directly
     * ABOVE — never by any block below — so it cannot be dragged down by a carrier lower in the
     * column. Under a LOWERED non-ceiling support it follows that support's dy (a TOP slab adds the
     * +0.5 raised-attach baseline so it sits flush, not 0.5 too low). A Terrain Slabs slab renders
     * FLUSH ({@code shouldSkipOffset}), so it is never treated as a lowered support. Under a normal
     * TOP slab (directly or via a chain of hangers) it floats +0.5; otherwise flush.
     */
    private static double ceilingHungDecorationDy(BlockGetter world, BlockPos pos, BlockState state) {
        BlockPos supportPos = pos.above();
        BlockState above = world.getBlockState(supportPos);
        if (isCeilingBridgedVerticalChainColumnMember(world, supportPos, above)) {
            return 0.0d;
        }
        if (!above.isAir() && !isCeilingAttached(above) && !CompatHooks.shouldSkipOffset(above)) {
            // The IN_GET_Y_OFFSET guard is already set, so this resolves the support's raw shape
            // recursion-safely. Skipping ceiling-attached supports bounds any hanger-chain recursion.
            double supportDy = getYOffsetInner(world, supportPos, above);
            if (supportDy < -1.0e-6d) {
                return isTopSlab(above) ? supportDy + 0.5d : supportDy;
            }
        }
        BlockPos cursor = supportPos;
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = world.getBlockState(cursor);
            if (isTopSlab(cur)) {
                return 0.5d;
            }
            if (isCeilingAttached(cur)) {
                cursor = cursor.above();
                continue;
            }
            break;
        }
        return 0.0d;
    }

    private static double getYOffsetInner(BlockGetter world, BlockPos pos, BlockState state) {
        // Ceiling-hung blocks (hanging roots, spore blossom, hanging signs, AND a HANGING lantern)
        // hang from the block ABOVE, so their dy is a pure function of that support. Dispatch them
        // here, BEFORE every "object resting on a support below" branch — those wrongly lower/raise
        // them: the follow-support tail reads getYOffsetInner of the support above and, for a
        // HANGING lantern not routed here, the lantern instead stays at grid height while its support
        // is lowered, so its chain/top pokes UP into the lowered support (the reported "smoosh"). A
        // HANGING lantern is included via the HANGING property so it FOLLOWS its support down (and
        // hangs flush under a flush support, no -0.5 gap); a STANDING lantern (HANGING=false)
        // legitimately rests on a support below and keeps the normal path. Port of 1.21.1 bbe3deb9.
        if (isAlwaysCeilingHungDecoration(state)
                || (state.hasProperty(BlockStateProperties.HANGING)
                        && state.getValue(BlockStateProperties.HANGING))) {
            return ceilingHungDecorationDy(world, pos, state);
        }
        // Terrain Slabs direct-object lowering (P0.4): an OBJECT (or vanilla slab) resting on a named
        // TS BOTTOM_LIKE surface lowers -0.5 to sit ON it. Dispatched HERE, before the slab/shouldOffset
        // split, so both objects and vanilla slabs on a TS surface get it. GEOMETRIC (no anchor → no
        // snap, RC1 stays fixed). Gated on directCustomSlabSupportDy != NaN, so any column NOT backed by
        // a TS BOTTOM_LIKE surface (incl. every world without TS loaded) takes the identical path below.
        // World-hole (P0.2) preserved: opaque full cubes are not subjects (isSlabSitCandidate), so they
        // never reach here and keep resting flush. A vanilla TOP slab presents its face a cell higher,
        // so it drops an extra -0.5 (clamped to -1.0).
        double directCustomSurfaceDy = directCustomSlabSupportDy(world, pos, state);
        if (!Double.isNaN(directCustomSurfaceDy)) {
            double dy = directCustomSurfaceDy;
            if (state.getBlock() instanceof SlabBlock
                    && state.hasProperty(SlabBlock.TYPE)
                    && state.getValue(SlabBlock.TYPE) == SlabType.TOP) {
                dy += -0.5;
            }
            return dy < -1.0 ? -1.0 : dy;
        }
        // Slab-on-offset-block: a slab placed on top of a solid block that sits on a bottom slab
        // inherits the same -0.5 dy so the stack stays visually continuous (no gap).
        if (state.getBlock() instanceof SlabBlock) {
            if (SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)) {
                return -1.0;
            }
            if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)) {
                return -1.0;
            }
            if (SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)) {
                return -1.0;
            }
            if (SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)) {
                return -1.0;
            }
            // FREEZE-ON-PLACE: a slab locked lowered at placement (freezeLoweredOnPlace) reads its
            // anchor and never recomputes — breaking an adjacent source can no longer pop it back up.
            if (SlabAnchorAttachment.isAnchored(world, pos)) {
                // GAP-1 (one config deeper): an anchored cantilever slab beside a COMPOUND -1.0 neighbour
                // must read -1.0, not the hardcoded -0.5 — the freeze-on-place anchor only records
                // PRESENCE, so the magnitude is read live from the current neighbour. AIR-GATED (this is
                // inside the over-air cantilever region) and bounded via adjacentLoweredSideMagnitude.
                // NEVER-POP preserved: if the -1.0 source is later removed this falls back to the anchored
                // -0.5 floor (still lowered, never pops UP to 0.0); a -0.5 neighbour or none yields -0.5.
                if (state.getFluidState().isEmpty()
                        && world.getBlockState(pos.below()).isAir()
                        && !isCompoundVisibleOwnerTopSlab(world, pos, state)) {
                    double anchoredSideMag = adjacentLoweredSideMagnitude(world, pos);
                    if (anchoredSideMag < -0.5 - 1.0e-6) {
                        return anchoredSideMag;   // -1.0 beside a live compound stack
                    }
                }
                return -0.5;
            }
            // FREEZE-ON-PLACE: a slab locked FLAT at placement stays at 0 — a lowered carrier placed
            // beside/under it later can no longer make it inherit a lowered position (Julia's law).
            if (SlabAnchorAttachment.isFrozenFlat(world, pos)) {
                return 0.0;
            }
            if (state.hasProperty(SlabBlock.TYPE)
                    && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                    && state.getFluidState().isEmpty()
                    && SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state)) {
                return -0.5;
            }
            // RC2-A (WYSIWYG): a slab placed CANTILEVERED over AIR beside a lowered FULL BLOCK must
            // inherit the aimed lowered surface (-0.5) for EITHER TYPE. getYOffset never sees the
            // raycast hit; the TOP/BOTTOM choice was made at placement (upper-half aim -> TOP,
            // lower-half -> BOTTOM), so this lowers both equally — each face follows the lowered
            // neighbour down by 0.5 and lands flush. Placed AFTER the four compound -1.0 markers /
            // anchor / frozen-flat checks above (a compound or recorded placement decision still wins)
            // and BEFORE the canUseInheritedSlabLaneYOffset gate, which would short-circuit a fresh
            // unmarked plain slab to 0.0. AIR-GATED at pos.below(): a slab on its own SOLID ground
            // beside a lowered block keeps dy=0 and stays FROZEN_FLAT (Julia's NEVER-POP rail).
            if (state.getFluidState().isEmpty()
                    && world.getBlockState(pos.below()).isAir()
                    && !isCompoundVisibleOwnerTopSlab(world, pos, state)) {
                // GAP-1: return the NEIGHBOUR's ACTUAL lowered dy (-1.0 beside a compound stack), not a
                // hardcoded -0.5. GAP-2: a bare single lowered slab neighbour now yields -0.5 (was 0.0).
                double sideMag = adjacentLoweredSideMagnitude(world, pos);
                if (!Double.isNaN(sideMag)) {
                    return sideMag;
                }
            }
            if (!canUseInheritedSlabLaneYOffset(world, pos, state)) {
                return 0.0;
            }
            BlockPos belowPos = pos.below();
            BlockState below = world.getBlockState(belowPos);
            Block belowBlock = below.getBlock();
            if (belowBlock instanceof SlabBlock) {
                if (isLoweredDoubleSlabCarrier(world, belowPos, below)) {
                    return -0.5;
                }
            } else if (!isCompoundVisibleOwnerTopSlab(world, pos, state)
                    && hasLoweredCarrierBelow(world, pos)) {
                return -0.5;
            }
            // Adjacent-side-slab alignment: a slab placed at the side of a
            // lowered full block must visually inherit the lowered -0.5 dy so model/outline/
            // raycast align with the neighbor. Use hasBottomSlabBelow directly: calling
            // getYOffset here would be short-circuited to 0.0 by the IN_GET_Y_OFFSET recursion
            // guard since this code runs inside getYOffsetInner.
            if (!isCompoundVisibleOwnerTopSlab(world, pos, state)
                    && isAdjacentSideSlabLowered(world, pos, state)) {
                return -0.5;
            }
        }

        // FREEZE-ON-PLACE: a structural full block locked FLAT at placement stays at 0 — a slab or
        // lowered carrier added under/beside it later can no longer pull it down (Julia's law: a
        // placed block must not autonomously move). Read before any geometric lowering below.
        // Decorative followers are never frozen-flat, so they keep tracking their supports.
        if (!(state.getBlock() instanceof SlabBlock)
                && com.slabbed.anchor.SlabAnchorAttachment.isFrozenFlat(world, pos)) {
            return 0.0;
        }

        // Persistent slab-anchor: an ordinary FB placed directly on a bottom slab is
        // recorded on the chunk via SlabAnchorAttachment at placement time and cleared
        // when the FB itself is broken/replaced. Anchors persist across supporting BS
        // removal so the FB does not visually jump upward.
        // Only honour anchors for non-slab blocks; slabs were handled above.
        if (!(state.getBlock() instanceof SlabBlock)
                && com.slabbed.anchor.SlabAnchorAttachment.isAnchored(world, pos)) {
            // Beta4 sidecar: an authored compound full-block anchor means the block sits on a slab
            // that was ITSELF lowered, so it drops an extra -0.5 below the slab's lowered top (total
            // -1.0). It must FOLLOW the slab directly below it: -1.0 only while that slab is genuinely
            // lowered (-0.5); if the slab was later flushed to 0.0 (frozen-flat by Julia's NEVER-POP
            // law, or its own lowering source removed) the block sits ON it at -0.5 — it must NOT stay
            // stuck at -1.0 and SINK/MERGE into the flush slab (the reported "merging" / "anti-
            // inheritance violated"). When the slab is REMOVED entirely (air/non-slab below) the
            // authored compound is preserved (-1.0) so source removal cannot pop the lane up. Floored
            // at -1.0 so a deeper slab can't push a full block past the dy>=-1.0 invariant.
            if (com.slabbed.anchor.SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)) {
                BlockState compoundBelow = world.getBlockState(pos.below());
                double compoundDy = -1.0;
                if (compoundBelow.getBlock() instanceof SlabBlock) {
                    compoundDy = Math.max(-1.0, getYOffsetInner(world, pos.below(), compoundBelow) - 0.5);
                }
                if (com.slabbed.anchor.SlabAnchorAttachment.TRACE) {
                    String side = (world instanceof net.minecraft.world.level.Level w && w.isClientSide()) ? "CLIENT" : "SERVER";
                    Slabbed.LOGGER.info("[ANCHOR] compound sidecar dy applied side={} pos={} state={} dy={}",
                            side, pos.toShortString(), state, compoundDy);
                }
                return compoundDy;
            }
            // Compound Lowered Full Block on Lowered Bottom Slab Carrier
            // (named legal state). When the bottom slab directly below is itself
            // in the lowered lane (persistent lowered slab carrier or
            // adjacent-side-slab lowered, dy=-0.5), the anchored FB on top of it
            // must drop an additional -0.5 to align with the slab's visual top
            // surface, for a total of -1.0. Without this branch the generic
            // anchor return -0.5 below collapses the freshly placed compound
            // case (live evidence: BETA4_PLACEMENT_AUTHOR_RECORDER at 9bf3bdc).
            // See docs/beta4-compound-lowered-fullblock-height.md.
            BlockPos belowPos = pos.below();
            BlockState belowSlab = world.getBlockState(belowPos);
            if (isBottomSlab(belowSlab) && isAdjacentSideSlabLowered(world, belowPos, belowSlab)) {
                if (com.slabbed.anchor.SlabAnchorAttachment.TRACE) {
                    String side = (world instanceof net.minecraft.world.level.Level w && w.isClientSide()) ? "CLIENT" : "SERVER";
                    Slabbed.LOGGER.info("[ANCHOR] compound dy applied side={} pos={} state={} dy=-1.0 belowSlabPos={} belowSlabState={}",
                            side, pos.toShortString(), state, belowPos.toShortString(), belowSlab);
                }
                return -1.0;
            }
            double specialFullblockContactDy = beta35SpecialFullblockContactDy(world, pos, state);
            if (Double.isFinite(specialFullblockContactDy)) {
                return specialFullblockContactDy;
            }
            double oakTrapdoorContactDy = beta35OakTrapdoorContactDy(world, pos, state);
            if (Double.isFinite(oakTrapdoorContactDy)) {
                return oakTrapdoorContactDy;
            }
            double regularDoorContactDy = beta35RegularDoorContactDy(world, pos, state);
            if (Double.isFinite(regularDoorContactDy)) {
                return regularDoorContactDy;
            }
            double standingOakSignContactDy = beta35StandingOakSignContactDy(world, pos, state);
            if (Double.isFinite(standingOakSignContactDy)) {
                return standingOakSignContactDy;
            }
            double floorButtonContactDy = beta35FloorButtonContactDy(world, pos, state);
            if (Double.isFinite(floorButtonContactDy)) {
                return floorButtonContactDy;
            }
            // GAP-1 (one config deeper): an anchored CONNECTING block (fence / wall / iron-bars)
            // cantilevered over air beside a COMPOUND -1.0 neighbour must read -1.0, not the hardcoded
            // -0.5. The freeze-on-place anchor only records PRESENCE; read the magnitude live from the
            // current connector lane. Gated to over-air connecting candidates (NEVER-POP rail intact),
            // recursion-safe and MAX_CHAIN_DEPTH-bounded via cantileverLoweredConnectingMagnitude.
            // NEVER-POP preserved: if the -1.0 source is later removed this falls back to -0.5.
            if (isCantileverConnectingCandidate(world, pos, state)) {
                double anchoredConnMag = cantileverLoweredConnectingMagnitude(world, pos, state);
                if (anchoredConnMag < -0.5 - 1.0e-6) {
                    return anchoredConnMag;   // -1.0 beside a live compound stack
                }
            }
            // An anchored CONNECTING block (fence/wall) stacked VERTICALLY on a deeper-lowered support
            // below — e.g. a fence on a fence on a lowered bottom slab — must follow the stack to its
            // true magnitude (-1.0), not the generic -0.5 floor below. The anchor records PRESENCE, not
            // depth, so without this it pops UP from its placed -1.0 to -0.5 the instant it connects and
            // re-meshes (Julia: "stacking a lowered fencepost on a lowered fencepost snaps the upper one
            // upward"). Mirrors the GAP-1 cantilever magnitude read for the support-directly-below case
            // via the geometric beta35 reader; the -0.5 floor still covers the cantilever / source-removed
            // NEVER-POP case. Recursion-safe (beta35FenceWallVariantContactDy never calls getYOffset).
            if (isBeta35FenceWallVariantContactObject(state)) {
                double stackedFenceDy = beta35FenceWallVariantContactDy(world, pos, state);
                if (Double.isFinite(stackedFenceDy) && stackedFenceDy < -0.5d - 1.0e-6d) {
                    return stackedFenceDy;
                }
            }
            if (com.slabbed.anchor.SlabAnchorAttachment.TRACE) {
                String side = (world instanceof net.minecraft.world.level.Level w && w.isClientSide()) ? "CLIENT" : "SERVER";
                Slabbed.LOGGER.info("[ANCHOR] dy applied side={} pos={} state={} dy=-0.5",
                        side, pos.toShortString(), state);
            }
            return -0.5;
        }

        // Cantilevered full block (air below, connected to a lowered tower): lower -0.5 to merge,
        // computed GEOMETRICALLY — it recomputes whenever the structure changes, so it never goes
        // stale and never pops out of sync (the replacement for the removed side-adjacent anchor;
        // see SlabAnchorAttachment#qualifiesForSideAdjacentLoweredFullAnchor). Air-gated, so a block
        // resting on solid ground never sinks. Together with the isAdjacentSideSlabLowered slab
        // branch above, mixed canopies of slabs and full blocks settle at one consistent lowered
        // level off the same tower. Port of 1.21.1 9a24670c.
        if (isCantileverLoweredFullBlock(world, pos, state)) {
            return -0.5;
        }

        // RC2-B (WYSIWYG): a connecting block — fence / wall / iron-bars — placed CANTILEVERED over
        // AIR and connected (through further cantilevered connectors) to a lowered neighbour merges
        // -0.5 to land flush with the aimed lowered surface. The existing beta35FenceWallVariantContactDy
        // reader below (:1853) only lowers from a support DIRECTLY BELOW (NaN over air); this is the
        // same-Y SIDE/cantilever case. AIR-GATED inside isCantileverConnectingCandidate (NEVER-POP
        // rail: a connector on solid ground beside a lowered lane stays at dy=0). Reached only when the
        // block is NOT a slab and NOT anchored / frozen-flat (all returned above), and connecting
        // blocks carry no compound -1.0 marker, so there is no ordering conflict. Recursion-safe and
        // MAX_CHAIN_DEPTH-bounded (see cantileverLoweredConnectingMagnitude).
        double connectingMag = cantileverLoweredConnectingMagnitude(world, pos, state);
        if (!Double.isNaN(connectingMag)) {
            // GAP-1: -1.0 beside a compound stack carried out of the BFS, else -0.5.
            return connectingMag;
        }

        if (isFloorTorch(state)) {
            BlockPos supportPos = pos.below();
            BlockState supportState = world.getBlockState(supportPos);
            if (isBottomSlab(supportState)
                    && (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, supportPos, supportState)
                            || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, supportPos, supportState))) {
                return -1.5;
            }
            double loweredBottomSupportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
            if (Double.isFinite(loweredBottomSupportDy) && loweredBottomSupportDy < -1.0e-6d) {
                return loweredBottomSupportDy - 0.5d;
            }
            if (isTopSlab(supportState)
                    && SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, supportPos, supportState)) {
                return -1.0;
            }
            // floor_torch on a lowered ordinary full-block support (supportDy=-1.0):
            // must follow the support down so the torch base contacts the support visual top.
            // Only applies to floor_torch; does not affect wall_torch, lanterns, signs, or chains.
            // dy=-1.0 does not violate the dy>=-1.0 invariant.
            if (isOrdinaryFullBlockWithCompoundDy(world, supportPos, supportState)) {
                return -1.0;
            }
        }

        if (isBeta35FloorTopContactObject(state)) {
            BlockPos supportPos = pos.below();
            BlockState supportState = world.getBlockState(supportPos);
            double loweredBottomSupportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
            if (Double.isFinite(loweredBottomSupportDy) && loweredBottomSupportDy < -1.0e-6d) {
                return loweredBottomSupportDy - 0.5d;
            }
        }

        double floorButtonContactDy = beta35FloorButtonContactDy(world, pos, state);
        if (Double.isFinite(floorButtonContactDy)) {
            return floorButtonContactDy;
        }

        double fenceWallVariantContactDy = beta35FenceWallVariantContactDy(world, pos, state);
        if (Double.isFinite(fenceWallVariantContactDy)) {
            return fenceWallVariantContactDy;
        }

        double fenceGateContactDy = beta35FenceGateContactDy(world, pos, state);
        if (Double.isFinite(fenceGateContactDy)) {
            return fenceGateContactDy;
        }

        double oakTrapdoorContactDy = beta35OakTrapdoorContactDy(world, pos, state);
        if (Double.isFinite(oakTrapdoorContactDy)) {
            return oakTrapdoorContactDy;
        }

        double regularDoorContactDy = beta35RegularDoorContactDy(world, pos, state);
        if (Double.isFinite(regularDoorContactDy)) {
            return regularDoorContactDy;
        }

        double standingOakSignContactDy = beta35StandingOakSignContactDy(world, pos, state);
        if (Double.isFinite(standingOakSignContactDy)) {
            return standingOakSignContactDy;
        }

        double specialFullblockContactDy = beta35SpecialFullblockContactDy(world, pos, state);
        if (Double.isFinite(specialFullblockContactDy)) {
            return specialFullblockContactDy;
        }

        double ordinaryFullBlockContactDy = beta35OrdinaryFullBlockContactDy(world, pos, state);
        if (Double.isFinite(ordinaryFullBlockContactDy)) {
            return ordinaryFullBlockContactDy;
        }

        if (shouldOffset(world, pos, state)) {
            // Compound case: non-slab block above a bottom slab that is itself an adjacent-side
            // slab lowered by -0.5.  The block must drop an additional -0.5 to align with the
            // slab's visual top surface, for a total of -1.0.
            BlockState belowSlab = world.getBlockState(pos.below());
            if (isBottomSlab(belowSlab) && isAdjacentSideSlabLowered(world, pos.below(), belowSlab)) {
                return -1.0;
            }
            double columnDy = slabColumnYOffset(world, pos);
            if (columnDy != 0.0) {
                return columnDy;
            }
            return -0.5;
        }
        // ── ceiling-attached blocks under a top slab: +0.5 UP ────────
        // Only explicit ceiling-mounted cases may float into the slab space.
        // Note: isSolidBlock is safe here because getYOffset has a recursion guard.
        Block blk = state.getBlock();
        if (blk instanceof SlabBlock
                || blk instanceof StairBlock
                || blk instanceof FenceBlock
                || blk instanceof WallBlock
                || blk instanceof IronBarsBlock
                || blk instanceof PowderSnowBlock
                || isThinTopLayer(state)
                || state.isAir()
                || !state.getFluidState().isEmpty()
                || state.isSolidRender()) {
            return 0.0;
        }

        BlockState above = world.getBlockState(pos.above());

        // direct: ceiling-attached blocks directly under a top slab. Track the slab's OWN dy so a
        // LOWERED top slab gives the block a flush 0.0 (slabDy=-0.5 → -0.5+0.5=0.0), NOT +0.5 which
        // would float it UP into the lowered slab (Julia: "trapdoor placed under a lowered slab merges
        // into it; breaking the slab drops it to flush 0.0"). A flush top slab (slabDy=0) keeps +0.5.
        // Recursion-safe: getYOffsetInner runs under the IN_GET_Y_OFFSET guard (mirrors ceilingHungDecorationDy).
        if (isCeilingAttached(state) && isTopSlab(above)) {
            return getYOffsetInner(world, pos.above(), above) + 0.5d;
        }

        // The direct top chain is rendered with a 1.5-block ceiling bridge model. Descendant chains
        // in that column must stay grid-height, otherwise their normal +0.5 model offset overlaps the
        // bridge segment and the column visually merges.
        if (isBeta35VerticalChainVisibleOwnerObject(state)
                && isCeilingBridgedVerticalChainColumnMember(world, pos, state)) {
            return 0.0d;
        }

        // cascading: ceiling-attached block below other ceiling-attached blocks
        // leading up to a top slab (e.g. 2nd dripstone, 2nd vine segment)
        if (isCeilingAttached(state)) {
            BlockPos cursor = pos.above();
            for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
                BlockState cur = world.getBlockState(cursor);
                if (isTopSlab(cur)) {
                    return 0.5;
                }
                if (isCeilingAttached(cur)) {
                    cursor = cursor.above();
                    continue;
                }
                break;
            }
        }

        return 0.0;
    }

    private static boolean canUseInheritedSlabLaneYOffset(BlockGetter world, BlockPos pos, BlockState state) {
        return state != null
                && state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getFluidState().isEmpty()
                && (isNamedLoweredSlabLane(world, pos, state)
                        || SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state));
    }

    private static double floorTorchBottomSlabSupportDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || !isBottomSlab(state) || !state.getFluidState().isEmpty()) {
            return Double.NaN;
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return -1.0d;
        }
        if (SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state)) {
            return -0.5d;
        }
        BlockPos belowPos = pos.below();
        BlockState below = world.getBlockState(belowPos);
        if (below.getBlock() instanceof SlabBlock) {
            if (isLoweredDoubleSlabCarrier(world, belowPos, below)) {
                return -0.5d;
            }
        } else if (!isCompoundVisibleOwnerTopSlab(world, pos, state)
                && hasLoweredCarrierBelow(world, pos)) {
            return -0.5d;
        }
        if (!isCompoundVisibleOwnerTopSlab(world, pos, state)
                && isAdjacentSideSlabLowered(world, pos, state)) {
            return -0.5d;
        }
        return 0.0d;
    }

    /**
     * Shared ownership rule for client raycast/outline retargeting of lowered
     * block-entity-style blocks (e.g. chests) sitting above a bottom slab.
     *
     * <p>When a block-entity block is visually lowered by -0.5 (its model, via
     * {@code BlockEntityOffsetMixin}, and its outline/raycast shapes, via
     * {@code SlabSupportStateMixin}), the lower half of its visible footprint
     * overflows into {@code pos.below()}'s voxel. Vanilla DDA raycast traversal
     * cannot see that overflowed portion at {@code pos} and instead hits the
     * slab below. This helper is the single source of truth for detecting
     * that case so raycast retarget and outline agree.
     *
     * @return true iff {@code state} is a {@link EntityBlock} block
     *         at {@code pos} whose {@link #getYOffset} is exactly {@code -0.5}.
     */
    public static boolean isLoweredBlockEntityVisual(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!(state.getBlock() instanceof EntityBlock)) {
            return false;
        }
        return getYOffset(world, pos, state) == -0.5;
    }

    public static boolean isLoweredTorchVisual(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        Block block = state.getBlock();
        if (!(block instanceof TorchBlock
                || block instanceof WallTorchBlock)) {
            return false;
        }
        // compound dy (-1.0) also qualifies: torch above an adjacent-lowered bottom slab
        return getYOffset(world, pos, state) < 0.0;
    }

    public static boolean isCompoundVisibleSlabLaneOwner(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (getYOffset(world, pos, state) != -1.0d) {
            return false;
        }
        return SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state);
    }

    public static BlockHitResult findCompoundVisibleSlabLaneOwnerTarget(
            BlockGetter world, Entity entity, Vec3 eye, Vec3 end
    ) {
        if (world == null || eye == null || end == null) {
            return null;
        }
        Vec3 ray = end.subtract(eye);
        double reach = ray.length();
        if (reach <= 0.0d) {
            return null;
        }
        Vec3 dir = ray.normalize();
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05d));

        BlockHitResult best = null;
        double bestDist2 = Double.POSITIVE_INFINITY;
        double maxDist2 = reach * reach + 1.0e-6d;
        for (int i = 1; i <= steps; i++) {
            double t = reach * i / steps;
            Vec3 sample = eye.add(dir.scale(t));
            BlockPos samplePos = BlockPos.containing(sample);

            BlockHitResult candidate = raycastCompoundVisibleSlabLaneOwner(world, entity, eye, end, samplePos);
            if (candidate != null && candidate.getLocation().distanceToSqr(eye) <= maxDist2
                    && candidate.getLocation().distanceToSqr(eye) < bestDist2 - 1.0e-6d) {
                best = candidate;
                bestDist2 = candidate.getLocation().distanceToSqr(eye);
            }

            candidate = raycastCompoundVisibleSlabLaneOwner(world, entity, eye, end, samplePos.above());
            if (candidate != null && candidate.getLocation().distanceToSqr(eye) <= maxDist2
                    && candidate.getLocation().distanceToSqr(eye) < bestDist2 - 1.0e-6d) {
                best = candidate;
                bestDist2 = candidate.getLocation().distanceToSqr(eye);
            }
        }
        return best;
    }

    private static BlockHitResult raycastCompoundVisibleSlabLaneOwner(
            BlockGetter world, Entity entity, Vec3 eye, Vec3 end, BlockPos pos
    ) {
        BlockState state = world.getBlockState(pos);
        if (!isCompoundVisibleSlabLaneOwner(world, pos, state)) {
            return null;
        }
        CollisionContext context = entity == null ? CollisionContext.empty() : CollisionContext.of(entity);
        VoxelShape outline = state.getShape(world, pos, context);
        if (outline == null || outline.isEmpty()) {
            return null;
        }
        return outline.clip(eye, end, pos);
    }

    public static boolean isLoweredBedVisual(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        return state.getBlock() instanceof BedBlock
                && state.hasProperty(BlockStateProperties.BED_PART)
                && getYOffset(world, pos, state) == -0.5;
    }

    /**
     * Redstone dust support surface — treat slab tops like valid ground for downward stepping.
     */
    public static boolean isRedstoneSupportTopSurface(BlockGetter world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        if (state.isFaceSturdy(world, pos, Direction.UP)) {
            return true;
        }

        return isSupportingSlab(state) && (isBottomSlab(state) || isTopSlab(state));
    }

    /**
     * Category predicate for the generic slab-column lowering fallback in
     * {@link #shouldOffset}.
     *
     * <p>Returns {@code true} for blocks that should visually sit on slabs
     * when a bottom slab exists somewhere in their column:
     * <ul>
     *   <li>Every {@link EntityBlock} block — chests, hoppers,
     *       furnaces, jukeboxes, spawners, end portal frames, beacons,
     *       banners, signs (standing), etc. This matches the
     *       {@link #isLoweredBlockEntityVisual} contract and ensures
     *       full-cube BE blocks (jukebox, spawner, …) lower alongside
     *       non-full-cube BE blocks (chest, hopper, …).</li>
     *   <li>Any block that is not a full solid cube — fences, walls, panes,
     *       torches, buttons, pressure plates, wall signs, etc.
     *       ({@code !state.isSolidBlock}).</li>
     * </ul>
     *
     * <p>Explicitly excludes plain solid world cubes (stone, dirt, planks,
     * cobblestone, sand, gravel, terracotta, …) so natural terrain does not
     * visually drop when a slab happens to sit below it.
     */
    private static boolean isSlabSitCandidate(BlockGetter world, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof EntityBlock) {
            return true;
        }
        if (block instanceof CraftingTableBlock) {
            return true;
        }
        return !state.isSolidRender();
    }

    // ───────────────────────────────────────────────────────────────────────────────────────────
    // Terrain Slabs direct-object lowering (P0.4). Port of the shipped 1.21.11 compat path
    // (isDirectCustomSlabSupportedObject / directCustomSlabSupportDy). An OBJECT (or vanilla slab)
    // resting on a named Terrain Slabs BOTTOM_LIKE surface lowers -0.5 to sit ON the slab's top
    // face. GEOMETRIC (computed in getYOffsetInner on BOTH client + server → no anchor, no snap, so
    // RC1's "snaps down after a delay" stays fixed). WORLD-HOLE PRESERVED (P0.2): the subject gate
    // routes through isSlabSitCandidate, which EXCLUDES opaque full cubes — natural stone/dirt on a
    // TS slab keeps hitting the flush column walk, so no see-through holes. No-op without TS loaded
    // (customSlabSurfaceKind always NONE → directCustomSlabSupportDy returns NaN → identical path).
    // ───────────────────────────────────────────────────────────────────────────────────────────

    /** True if {@code state} is a vanilla (minecraft-namespace) slab — a valid direct-custom subject. */
    private static boolean isVanillaDirectCustomSlabSubject(BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock) || !state.hasProperty(SlabBlock.TYPE)) {
            return false;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && "minecraft".equals(id.getNamespace());
    }

    /**
     * True if {@code state} is the kind of block that lowers onto a slab support — the same objects
     * that sit on vanilla slabs (block entities, the crafting table, and every non-full-cube block:
     * fences, walls, panes, torches, doors, signs, lanterns, …) plus vanilla slabs. Deliberately
     * EXCLUDES opaque full cubes (via {@link #isSlabSitCandidate}'s {@code !isSolidRender()}): lowering
     * an opaque cube onto a TS slab would tear see-through world holes (P0.2). TS-owned blocks are
     * excluded ({@code shouldSkipOffset}) — they are self-rendering surfaces, not subjects.
     */
    private static boolean isDirectCustomSlabSupportSubject(BlockGetter world, BlockPos pos, BlockState state) {
        if (state.isAir()
                || state.getBlock() instanceof SlabBlock && !isVanillaDirectCustomSlabSubject(state)
                || isThinTopLayer(state)
                || !state.getFluidState().isEmpty()
                || CompatHooks.shouldSkipOffset(state)) {
            return false;
        }
        return isVanillaDirectCustomSlabSubject(state) || isSlabSitCandidate(world, pos, state);
    }

    /**
     * True if the subject at {@code pos} is (directly, or through a stack of further lowered subjects)
     * supported by a named Terrain Slabs {@code BOTTOM_LIKE} surface — so it lowers -0.5 to rest on it.
     * The column walk stops at the first non-subject (terrain cube / air), which keeps anything resting
     * above that at grid height. Double blocks (doors, tall plants): the UPPER half follows its LOWER
     * half's support. Recursion-safe (no {@link #getYOffset}); {@link #MAX_CHAIN_DEPTH}-bounded.
     */
    private static boolean isDirectCustomSlabSupportedObject(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isDirectCustomSlabSupportSubject(world, pos, state)) {
            return false;
        }
        // Vegetation (flowers, grass, tall plants, saplings — all VegetationBlock) on a Terrain Slabs
        // surface must sit FLUSH on top: TS already positions vegetation via its own SlabOffsetModel, so
        // Slabbed must NOT also lower it -0.5 here (the double-offset sinks it; Julia 2026-06-19).
        // Excluding it lets getYOffset fall through to the column walk, which terminates flush at the TS
        // block → dy 0, so TS's offset is the only one applied. No-op without TS (this path needs a TS
        // BOTTOM_LIKE surface to fire at all).
        if (state.getBlock() instanceof VegetationBlock) {
            return false;
        }
        BlockPos supportPos = pos.below();
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            BlockState lowerState = world.getBlockState(pos.below());
            if (lowerState.getBlock() != state.getBlock()
                    || !lowerState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    || lowerState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) {
                return false;
            }
            supportPos = pos.below(2);
        }
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState supportState = world.getBlockState(supportPos);
            if (CompatHooks.customSlabSurfaceKind(supportState) == CompatSlabSurfaceKind.BOTTOM_LIKE) {
                return true;
            }
            if (isDirectCustomSlabSupportSubject(world, supportPos, supportState)) {
                supportPos = supportPos.below();
                continue;
            }
            return false;
        }
        return false;
    }

    /** -0.5 if {@code state} is an object resting on a Terrain Slabs BOTTOM_LIKE surface, else NaN. */
    private static double directCustomSlabSupportDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (!isDirectCustomSlabSupportedObject(world, pos, state)) {
            return Double.NaN;
        }
        return -0.5;
    }

    /**
     * Walks down from {@code pos} through non-air, non-slab blocks looking
     * for a bottom slab. Returns true as soon as one is found.
     *
     * <p>An anchored full block in the column also terminates the walk as a
     * positive — the anchor records that this block is itself lowered by -0.5
     * (its visible top sits at slab height), so anything stacked on top of it
     * inherits the same lowered surface even after the original BS support
     * has been broken.
     *
     * <p>A slab encountered anywhere in the bounded column walk that is itself
     * a lowered adjacent-side slab — i.e. a 1S/0.5S/double slab horizontally
     * beside an anchored or BS-supported FB — also counts as a positive: its
     * visible top face sits at the lowered support height, so anything stacked
     * above it (directly or through intermediate full blocks) must inherit the
     * same -0.5 dy. Vanilla top slabs that are not lowered still terminate the
     * walk false via the slab terminator below. Walk remains bounded by
     * {@link #MAX_CHAIN_DEPTH}.
     */
    private static boolean hasSlabInColumn(BlockGetter world, BlockPos pos) {
        BlockPos cursor = pos.below();
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = world.getBlockState(cursor);
            // Terrain Slabs compat (no-op without the mod): a TS slab is a self-rendering surface that
            // already sits at slab height. Slabbed stays subtractive and must NOT lower terrain onto it —
            // 26.1.2's isBottomSlab() returns true for a TS slab (it extends SlabBlock), so without this
            // guard natural stone/dirt above a TS slab lowered -0.5 and the chunk mesher tore see-through
            // world holes. Terminate the walk FLUSH at a TS block, as if resting on solid ground.
            if (CompatHooks.shouldSkipSlabSupport(cur)) {
                return false;
            }
            if (isBottomSlab(cur)) {
                return true;
            }
            if (SlabAnchorAttachment.isAnchored(world, cursor)) {
                return true;
            }
            if (cur.getBlock() instanceof SlabBlock
                    && isAdjacentSideSlabLowered(world, cursor, cur)) {
                return true;
            }
            if (cur.isAir() || cur.getBlock() instanceof SlabBlock || isThinTopLayer(cur)) {
                return false;
            }
            cursor = cursor.below();
        }
        return false;
    }

    private static double slabColumnYOffset(BlockGetter world, BlockPos pos) {
        BlockPos cursor = pos.below();
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = world.getBlockState(cursor);
            // Terrain Slabs compat (see hasSlabInColumn): never lower an object onto a TS slab — it is a
            // self-rendering surface, and treating it as a vanilla bottom slab tore world holes. Flush.
            if (CompatHooks.shouldSkipSlabSupport(cur)) {
                return 0.0;
            }
            if (cur.getBlock() instanceof SlabBlock
                    && isAdjacentSideSlabLowered(world, cursor, cur)) {
                return isBottomSlab(cur) ? -1.0 : -0.5;
            }
            if (isBottomSlab(cur) || SlabAnchorAttachment.isAnchored(world, cursor)) {
                return -0.5;
            }
            if (cur.isAir() || cur.getBlock() instanceof SlabBlock || isThinTopLayer(cur)) {
                return 0.0;
            }
            cursor = cursor.below();
        }
        return 0.0;
    }
}
