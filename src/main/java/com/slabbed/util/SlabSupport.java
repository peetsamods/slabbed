package com.slabbed.util;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TorchBlock;
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
        return isBottomSlab(world.getBlockState(pos.below()));
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
                && !isBeta35RegularDoorVisibleOwnerObject(world, pos, state)) {
            return false;
        }
        double objectDy = getYOffset(world, pos, state);
        return Double.isFinite(objectDy) && objectDy < -1.0e-6d;
    }

    public static boolean isBeta35FenceWallVariantContactObject(BlockState state) {
        return state != null
                && (state.getBlock() instanceof FenceBlock
                        || state.getBlock() instanceof WallBlock);
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
                return isBottomSlab(world.getBlockState(pos.below(2)));
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
        // Always-ceiling-hung decoration (hanging roots, spore blossom, hanging sign) hangs from the
        // block ABOVE and has no floor variant, so its dy is a pure function of that support.
        // Dispatch it here, BEFORE every "object resting on a support below" branch — those wrongly
        // lower it when a carrier sits lower in the column (a placed lantern bridges the downward
        // walk to a slab below, dragging the hanger down through a flush support). Lanterns are NOT
        // here: a standing lantern legitimately rests on a slab, so it keeps the normal path.
        if (isAlwaysCeilingHungDecoration(state)) {
            return ceilingHungDecorationDy(world, pos, state);
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
            // Beta4 sidecar: authored compound full-block anchor preserves dy=-1.0
            // even after the source slab below is removed. Sidecar truth wins over
            // the live below-slab predicate so source removal cannot silently
            // renormalize the authored compound lane.
            if (com.slabbed.anchor.SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)) {
                if (com.slabbed.anchor.SlabAnchorAttachment.TRACE) {
                    String side = (world instanceof net.minecraft.world.level.Level w && w.isClientSide()) ? "CLIENT" : "SERVER";
                    Slabbed.LOGGER.info("[ANCHOR] compound sidecar dy applied side={} pos={} state={} dy=-1.0",
                            side, pos.toShortString(), state);
                }
                return -1.0;
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

        // direct: ceiling-attached blocks directly under a top slab
        if (isCeilingAttached(state) && isTopSlab(above)) {
            return 0.5;
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
