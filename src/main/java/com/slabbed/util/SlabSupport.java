package com.slabbed.util;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Central helper for slab support semantics.
 */
public final class SlabSupport {
    private static final String BOTTOM_PERSISTENT_TRACE_OPT_IN = "slabbed.bottomPersistentTrace";

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
                || isPaleMossCarpet(block);
    }

    private static boolean isPaleMossCarpet(Block block) {
        return block == BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecraft", "pale_moss_carpet"));
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
        if (CompatHooks.shouldSkipSlabSupport(state)) {
            return false;
        }
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
     * Primary query: should this slab top face count as solid support. Also recognizes a
     * named Terrain Slabs BOTTOM_LIKE surface as valid top support (a separate, narrow
     * opt-in -- see {@link #isDirectObjectSupportSurface}), not a change to the blanket
     * shouldSkipSlabSupport exclusion.
     */
    public static boolean canTreatAsSolidTopFace(LevelReader world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return isSupportingSlab(state) || isDirectObjectSupportSurface(state);
    }

    /** Overload for shape/world views. */
    public static boolean canTreatAsSolidTopFace(BlockGetter world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return isSupportingSlab(state) || isDirectObjectSupportSurface(state);
    }

    /**
     * True when {@code state} is a named Terrain Slabs BOTTOM_LIKE surface -- a proven,
     * named custom slab surface (not every TS block) that may carry a directly-placed
     * object/full-block the same way a vanilla bottom slab does.
     */
    public static boolean isDirectObjectSupportSurface(BlockState state) {
        return CompatHooks.customSlabSurfaceKind(state) == CompatSlabSurfaceKind.BOTTOM_LIKE;
    }

    public static boolean isFloorTorch(BlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof net.minecraft.world.level.block.TorchBlock && !(block instanceof WallTorchBlock);
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
            BlockGetter world,
            BlockPos pos,
            BlockState state,
            VoxelShape fallback
    ) {
        VoxelShape base = fallback == null ? Shapes.empty() : fallback;
        if (!isVerticalChainDirectlyUnderCeilingSupport(world, pos, state)) {
            return base;
        }
        if (base.isEmpty()) {
            return Block.box(6.5d, 0.0d, 6.5d, 9.5d, 24.0d, 9.5d);
        }

        VoxelShape selection = base;
        AABB bounds = base.bounds();
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

    public static boolean isBeta35UpwardPointedDripstoneVisibleOwnerObject(BlockState state) {
        return state != null
                && state.getBlock() instanceof PointedDripstoneBlock
                && state.hasProperty(BlockStateProperties.VERTICAL_DIRECTION)
                && state.getValue(BlockStateProperties.VERTICAL_DIRECTION) == Direction.UP;
    }

    public static boolean isBeta35PointedDripstoneServerHitTarget(
            BlockGetter world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null || !(state.getBlock() instanceof PointedDripstoneBlock)) {
            return false;
        }
        double targetDy = getBeta35ShiftedServerValidationYOffset(world, pos, state);
        return Double.isFinite(targetDy) && Math.abs(targetDy) > 1.0e-6d;
    }

    private static boolean isDownwardPointedDripstone(BlockState state) {
        return state != null
                && state.getBlock() instanceof PointedDripstoneBlock
                && state.hasProperty(BlockStateProperties.VERTICAL_DIRECTION)
                && state.getValue(BlockStateProperties.VERTICAL_DIRECTION) == Direction.DOWN;
    }

    public static boolean isBeta35RailVisibleOwnerObject(BlockState state) {
        return state != null && state.getBlock() instanceof BaseRailBlock;
    }

    private static boolean verticalChainColumnRootsAtTopSlab(
            BlockGetter world, BlockPos supportPos, BlockState supportState
    ) {
        if (world == null || supportPos == null || !isBeta35VerticalChainVisibleOwnerObject(supportState)) {
            return false;
        }
        BlockPos cursor = supportPos;
        BlockState cur = supportState;
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            if (isTopSlab(cur)) {
                return true;
            }
            if (!isBeta35VerticalChainVisibleOwnerObject(cur)) {
                return false;
            }
            cursor = cursor.above();
            cur = world.getBlockState(cursor);
        }
        return false;
    }

    private static boolean downwardPointedDripstoneColumnRootsAtTopSlab(
            BlockGetter world, BlockPos supportPos, BlockState supportState
    ) {
        if (world == null || supportPos == null || !isDownwardPointedDripstone(supportState)) {
            return false;
        }
        BlockPos cursor = supportPos;
        BlockState cur = supportState;
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            if (isTopSlab(cur)) {
                return true;
            }
            if (verticalChainColumnRootsAtTopSlab(world, cursor, cur)) {
                return true;
            }
            if (!isDownwardPointedDripstone(cur)) {
                return false;
            }
            cursor = cursor.above();
            cur = world.getBlockState(cursor);
        }
        return false;
    }

    private static boolean downwardPointedDripstoneColumnRootsThroughTopSlabChain(
            BlockGetter world, BlockPos supportPos, BlockState supportState
    ) {
        if (world == null || supportPos == null || !isDownwardPointedDripstone(supportState)) {
            return false;
        }
        BlockPos cursor = supportPos;
        BlockState cur = supportState;
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            if (verticalChainColumnRootsAtTopSlab(world, cursor, cur)) {
                return true;
            }
            if (!isDownwardPointedDripstone(cur)) {
                return false;
            }
            cursor = cursor.above();
            cur = world.getBlockState(cursor);
        }
        return false;
    }

    private static double downwardPointedDripstoneLoweredCeilingSupportDy(
            BlockGetter world, BlockPos supportPos, BlockState supportState
    ) {
        if (world == null || supportPos == null || supportState == null) {
            return Double.NaN;
        }
        BlockPos cursor = supportPos;
        BlockState cur = supportState;
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            if (isDownwardPointedDripstone(cur) || isBeta35VerticalChainVisibleOwnerObject(cur)) {
                cursor = cursor.above();
                cur = world.getBlockState(cursor);
                continue;
            }
            if (cur.isAir() || cur.getBlock() instanceof SlabBlock || isCeilingAttached(cur)
                    || CompatHooks.shouldSkipOffset(cur)) {
                return Double.NaN;
            }
            double supportDy = getYOffsetInner(world, cursor, cur);
            return supportDy < -1.0e-6d ? supportDy : Double.NaN;
        }
        return Double.NaN;
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
        if (isAnchoredLoweredFullBlock(world, supportPos, supportState)) {
            return SlabAnchorAttachment.isCompoundFullBlockAnchor(world, supportPos) ? -1.0d : -0.5d;
        }
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
                && !isBeta35UpwardPointedDripstoneVisibleOwnerObject(state)
                && !isBeta35RailVisibleOwnerObject(state)
                && !isBeta35RegularDoorVisibleOwnerObject(world, pos, state)
                && !isBeta35LoweredSlabUndersideVisibleOwnerObject(world, pos, state)) {
            return false;
        }
        double objectDy = getYOffset(world, pos, state);
        return Double.isFinite(objectDy) && objectDy < -1.0e-6d;
    }

    private static boolean isPaleHangingMossBlock(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null || !"minecraft".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return "pale_hanging_moss".equals(path) || "pale_hanging_moss_tip".equals(path);
    }

    private static boolean isBeta35LoweredSlabUndersideVisibleOwnerObject(
            BlockGetter world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null || state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        Block block = state.getBlock();
        boolean supportedCeilingObject = state.is(Blocks.LANTERN)
                || state.is(Blocks.SOUL_LANTERN)
                || block instanceof SporeBlossomBlock
                || block instanceof HangingRootsBlock
                || isPaleHangingMossBlock(state);
        if (!supportedCeilingObject) {
            return false;
        }
        BlockPos supportPos = pos.above();
        BlockState supportState = world.getBlockState(supportPos);
        if (!(supportState.getBlock() instanceof SlabBlock) || !supportState.hasProperty(SlabBlock.TYPE)) {
            return false;
        }
        double supportDy = loweredSlabUndersideSupportDy(world, supportPos, supportState);
        return Double.isFinite(supportDy) && supportDy < -1.0e-6d;
    }

    /**
     * Full-block analogue of {@link #isBeta35LoweredSlabUndersideVisibleOwnerObject}.
     * True when a decorative hanger (lantern / soul lantern / spore blossom /
     * hanging roots / pale hanging moss — NOT chains) hangs directly beneath a
     * SOLID, NON-SLAB full block that itself renders lowered. Such a hanger must
     * follow the support down so it stays flush instead of clipping up into the
     * lowered block. Chains are intentionally excluded so they keep extending to
     * connect to the support.
     */
    private static boolean isBeta35LoweredFullBlockUndersideVisibleOwnerObject(
            BlockGetter world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null || state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        Block block = state.getBlock();
        boolean supportedCeilingObject = state.is(Blocks.LANTERN)
                || state.is(Blocks.SOUL_LANTERN)
                || block instanceof SporeBlossomBlock
                || block instanceof HangingRootsBlock
                || isPaleHangingMossBlock(state);
        if (!supportedCeilingObject) {
            return false;
        }
        BlockPos supportPos = pos.above();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = loweredFullBlockUndersideSupportDy(world, supportPos, supportState);
        return Double.isFinite(supportDy) && supportDy < -1.0e-6d;
    }

    private static boolean isAlwaysCeilingHungDecoration(BlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof HangingRootsBlock
                || block instanceof SporeBlossomBlock
                || block instanceof CeilingHangingSignBlock
                || isPaleHangingMossBlock(state);
    }

    /**
     * Dy for objects whose only legal support is the block above them. A hanging
     * lantern below a ceiling-bridged chain is not another raised chain segment;
     * the chain bridge is already raised, so the lantern stays grid-height and
     * hangs as an addendum below it.
     */
    private static double ceilingHungDecorationDy(BlockGetter world, BlockPos pos, BlockState state) {
        BlockPos supportPos = pos.above();
        BlockState above = world.getBlockState(supportPos);
        if (isCeilingBridgedVerticalChainColumnMember(world, supportPos, above)) {
            return 0.0d;
        }
        if (isDownwardPointedDripstone(state)
                && downwardPointedDripstoneColumnRootsThroughTopSlabChain(world, supportPos, above)) {
            return 0.0d;
        }
        if (!above.isAir() && !isCeilingAttached(above) && !CompatHooks.shouldSkipOffset(above)) {
            double supportDy = getYOffsetInner(world, supportPos, above);
            if (supportDy < -1.0e-6d) {
                return isTopSlab(above) ? supportDy + 0.5d : supportDy;
            }
        }
        BlockPos cursor = supportPos;
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = world.getBlockState(cursor);
            if (isCeilingBridgedVerticalChainColumnMember(world, cursor, cur)) {
                return 0.0d;
            }
            if (isTopSlab(cur)) {
                return 0.5d;
            }
            if (isCeilingAttached(cur)) {
                cursor = cursor.above();
                continue;
            }
            if (!cur.isAir() && !CompatHooks.shouldSkipOffset(cur)) {
                double supportDy = getYOffsetInner(world, cursor, cur);
                if (supportDy < -1.0e-6d) {
                    return supportDy;
                }
            }
            break;
        }
        return 0.0d;
    }

    public static boolean isBeta35FenceWallVariantContactObject(BlockState state) {
        return state != null
                && (state.getBlock() instanceof FenceBlock
                        || state.getBlock() instanceof WallBlock
                        || state.getBlock() instanceof IronBarsBlock);
    }

    /**
     * Visual dy of a connecting block (fence/wall/pane), as used by the stepped-connection check.
     */
    public static double connectingBlockVisualDy(BlockGetter world, BlockPos pos, BlockState state) {
        return getYOffset(world, pos, state);
    }

    /**
     * True if a same-family connector arm would visually advertise the wrong owner.
     * Height-step joins are broken, but ordinary solid side owners such as logs remain
     * vanilla-legal: WYSIWYG controls where the fence is authored, not whether a placed
     * fence may connect to its neighbor.
     */
    public static boolean isSteppedConnectingNeighbor(BlockGetter world, BlockPos pos, BlockState state,
                                                      BlockPos neighborPos, BlockState neighborState) {
        Block neighbor = neighborState.getBlock();
        if (neighbor instanceof FenceBlock || neighbor instanceof WallBlock || neighbor instanceof IronBarsBlock) {
            double selfDy = connectingBlockVisualDy(world, pos, state);
            double neighborDy = connectingBlockVisualDy(world, neighborPos, neighborState);
            return Math.abs(selfDy - neighborDy) > 1.0e-6;
        }
        return false;
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

    private static double loweredSlabUndersideSupportDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || !(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE) || !state.getFluidState().isEmpty()) {
            return Double.NaN;
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return -1.0d;
        }
        SlabType type = state.getValue(SlabBlock.TYPE);
        if (type == SlabType.BOTTOM) {
            return floorTorchBottomSlabSupportDy(world, pos, state);
        }
        if (isAdjacentSideSlabLowered(world, pos, state)) {
            return -0.5d;
        }
        return 0.0d;
    }

    /**
     * Recursion-safe analogue of {@link #loweredSlabUndersideSupportDy} for a
     * SOLID, NON-SLAB full-block ceiling support. Returns EXACTLY the dy that
     * {@link #getYOffsetInner} assigns the full block at {@code pos}, computed
     * without delegating to {@link #getYOffset} so it is safe to call inside the
     * {@link #IN_GET_Y_OFFSET} recursion guard. Decorative hangers
     * (lantern / soul lantern / spore blossom / hanging roots / pale hanging
     * moss) follow this value down so they hang flush under a lowered support
     * instead of clipping up into it.
     *
     * <p>Every predicate invoked here ({@code isAnchored},
     * {@code isCompoundFullBlockAnchor}, {@link #isOrdinaryFullBlockWithCompoundDy},
     * the {@code beta35*ContactDy} family, {@link #shouldOffset},
     * {@link #slabColumnYOffset}) is already invoked by {@link #getYOffsetInner}
     * under the same guard, so this mirror adds no new recursion risk and returns
     * the support's true rendered dy by construction.
     *
     * <p>Only ever returns {@code 0.0}, a negative lowered dy ({@code -0.5} /
     * {@code -1.0}), or {@link Double#NaN} (not a lowered full block — the hanger
     * keeps its natural dy). Never returns a positive dy: top-slab {@code +0.5}
     * adherence is a separate downstream branch and full blocks are not top slabs.
     */
    private static double loweredFullBlockUndersideSupportDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidRender(world, pos)) {
            return Double.NaN;
        }
        // ── anchored full block: mirror the getYOffsetInner anchor branch ──────
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            // compound (-1.0): compound anchor OR resting on an adjacent-side-lowered bottom slab
            if (isOrdinaryFullBlockWithCompoundDy(world, pos, state)) {
                return -1.0d;
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
            return -0.5d;
        }
        // ── non-anchored: mirror the standalone contact dys in getYOffsetInner ──
        // (only special/ordinary full-block contacts can be finite for a solid
        // full block; the button/fence/gate/trapdoor/door/sign contacts require
        // non-solid owner types and would be NaN here.)
        double specialFullblockContactDy = beta35SpecialFullblockContactDy(world, pos, state);
        if (Double.isFinite(specialFullblockContactDy)) {
            return specialFullblockContactDy;
        }
        double ordinaryFullBlockContactDy = beta35OrdinaryFullBlockContactDy(world, pos, state);
        if (Double.isFinite(ordinaryFullBlockContactDy)) {
            return ordinaryFullBlockContactDy;
        }
        // ── non-anchored full block lowered by a slab in its column ─────────────
        if (shouldOffset(world, pos, state)) {
            BlockPos belowPos = pos.below();
            BlockState below = world.getBlockState(belowPos);
            if (isBottomSlab(below) && isAdjacentSideSlabLowered(world, belowPos, below)) {
                return -1.0d;
            }
            double columnDy = slabColumnYOffset(world, pos);
            if (columnDy != 0.0d) {
                return columnDy;
            }
            return -0.5d;
        }
        return Double.NaN;
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
        return state.isSolidRender(world, pos);
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

    /** Kill switch for the slab-height step-face cull relaxation ({@link #isSlabHeightStepFace}). */
    private static final boolean STEP_CULL_DISABLED = Boolean.getBoolean("slabbed.disableStepCull");

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
        if (state.getBlock() instanceof CeilingHangingSignBlock || state.getBlock() instanceof WallHangingSignBlock) {
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

        // never offset thin top-layer blocks (snow layers, carpet) or powder snow — they are
        // natural surface/terrain fill, not structural objects. Powder snow is a full cube so it
        // is NOT an isThinTopLayer (which keys on SnowBlock), hence the explicit guard.
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

        // Terrain Slabs compat — DO NOT double-offset. TS lowers its OWN decoration blocks
        // (lanterns, flowers, mushrooms, grass, …) onto its slabs through its own "offset=ontop"
        // blockstate + ontop model (a -0.5 visual drop it renders itself). If Slabbed also drops
        // them -0.5 the object buries a full block into the slab (the "smoosh"). When TS is
        // already offsetting the object, defer entirely so TS's ontop model is the single source
        // of the drop. Objects TS does NOT wrap (full blocks, signs, vanilla slabs — no offset
        // property) are unaffected and still lower via the normal path below. See the
        // "TS already wraps veg; don't double-wrap" compat lesson.
        if (CompatHooks.terrainSlabsHandlesObjectOffset(state)) {
            return 0.0;
        }

        // Powder snow is a FULL CUBE, so unlike snow layers it matches the "full block on a slab"
        // lowering branch and Slabbed was dropping it -0.5 onto a slab while neighbouring powder
        // snow on full ground stayed flush — leaving a half-block step / DODO across snowy terrain.
        // PowderSnowBlock is NOT a SnowBlock, so isThinTopLayer never excluded it. It is natural
        // terrain fill (Terrain Slabs likewise does not lower it), so never offset it: keep all
        // powder snow flush and consistent.
        if (state.getBlock() instanceof PowderSnowBlock) {
            return 0.0;
        }

        // Recursion guard: isSolidBlock → getCollisionShape → getOutlineShape (mixin) → getYOffset
        if (IN_GET_Y_OFFSET.get()) {
            if (state.getBlock() instanceof SlabBlock
                    && state.hasProperty(SlabBlock.TYPE)
                    && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                    && isBottomPersistentTracePos(pos)) {
                Slabbed.LOGGER.info("[BOTTOM_PERSISTENT] getYOffset early_guard_return pos=" + shortPos(pos)
                        + " state=" + state
                        + " slabType=" + state.getValue(SlabBlock.TYPE)
                        + " fluidEmpty=" + state.getFluidState().isEmpty()
                        + " guard=" + IN_GET_Y_OFFSET.get()
                        + " worldClass=" + world.getClass().getName());
            }
            return 0.0;
        }
        IN_GET_Y_OFFSET.set(Boolean.TRUE);
        try {
            return getYOffsetInner(world, pos, state);
        } finally {
            IN_GET_Y_OFFSET.set(Boolean.FALSE);
        }
    }

    private static final boolean COLLISION_FOLLOW =
            !"false".equalsIgnoreCase(System.getProperty("slabbed.collisionFollow", "true"));

    private static final ThreadLocal<Boolean> VANILLA_COLLISION_SHAPE_QUERY =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean isVanillaCollisionShapeQuery() {
        return Boolean.TRUE.equals(VANILLA_COLLISION_SHAPE_QUERY.get());
    }

    private static VoxelShape vanillaCollisionShape(BlockState state, BlockGetter getter, BlockPos pos) {
        boolean previous = isVanillaCollisionShapeQuery();
        VANILLA_COLLISION_SHAPE_QUERY.set(Boolean.TRUE);
        try {
            return state.getCollisionShape(getter, pos, CollisionContext.empty());
        } finally {
            VANILLA_COLLISION_SHAPE_QUERY.set(previous);
        }
    }

    /**
     * Unions the hanging collision from a lowered block directly above the
     * broadphase cell currently being sampled.
     */
    public static VoxelShape withHangingLoweredCollisionFromAbove(VoxelShape own, BlockGetter getter, BlockPos pos) {
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
        if (dy >= -1.0e-6d) {
            return own;
        }

        VoxelShape aboveVanilla = vanillaCollisionShape(above, getter, abovePos);
        if (aboveVanilla.isEmpty()) {
            return own;
        }
        VoxelShape hanging = aboveVanilla.move(0.0d, dy + 1.0d, 0.0d);
        return own.isEmpty() ? hanging : Shapes.or(own, hanging);
    }

    /**
     * Render cull-relaxation predicate: returns true when {@code direction}'s HORIZONTAL side
     * face of the block at {@code pos} sits at a SLAB-HEIGHT STEP against its neighbour — i.e.
     * the two blocks render at different heights ({@code |getYOffset(self) − getYOffset(neighbour)|
     * > ε}). Vanilla / Indigo / Sodium all cull that shared side face (both are full cubes in
     * their voxel cells), but the model offset drops one of them, exposing a strip that was never
     * meshed → a see-through "ghost window". A client cull mixin is expected to force-draw the
     * face when this returns true.
     *
     * <p><b>Contract:</b> only ever used to flip cull→draw (never draw→cull), so it cannot create
     * new culling/z-fight artifacts: the two coplanar seam faces face opposite ways (GPU
     * back-face-culls the far one) and the still-occluded portion hides behind the opaque
     * neighbour body. Uses the SAME {@link #getYOffset} signal the offset model renders with
     * (via {@code ClientDy.dyFor}), so the un-culled face matches the shifted geometry exactly.
     *
     * <p>Horizontal faces only (the common terrace/canopy window). Vertical steps (a frozen-flat
     * block directly above a lowered one) are a documented follow-up; see
     * {@code docs/CULL-WINDOW-FIX-DESIGN.md}. Disabled by {@code -Dslabbed.disableStepCull}.
     *
     * <p>No render wiring calls this yet — it is pure, recursion-guarded ({@link #getYOffset})
     * logic, safe to call from the chunk-mesh BlockGetter context.
     */
    public static boolean isSlabHeightStepFace(BlockGetter world, BlockPos pos, BlockState state, Direction direction) {
        if (STEP_CULL_DISABLED || world == null || pos == null || state == null || direction == null) {
            return false;
        }
        if (!direction.getAxis().isHorizontal()) {
            return false;
        }
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighbor = world.getBlockState(neighborPos);
        if (neighbor.isAir()) {
            return false;
        }
        double selfDy = getYOffset(world, pos, state);
        double neighborDy = getYOffset(world, neighborPos, neighbor);
        return Math.abs(selfDy - neighborDy) > 1.0e-6;
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
                || incomingType == SlabType.DOUBLE
                || (existingType == SlabType.TOP && incomingType == SlabType.BOTTOM)
                || (existingType == SlabType.BOTTOM && incomingType == SlabType.TOP);
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
        boolean compoundFullBlockSource = isCompoundVisibleFullBlockSource(world, sourcePos, sourceState);
        boolean compoundVisibleSlabLaneSource = sourceState.getBlock() instanceof SlabBlock
                && isCompoundVisibleSlabLaneOwner(world, sourcePos, sourceState)
                && Math.abs(getYOffset(world, sourcePos, sourceState) + 1.0d) <= 1.0e-6d;
        if (!compoundFullBlockSource && !compoundVisibleSlabLaneSource) {
            return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                    CompoundSlabRemapDecision.rejected(sourcePos, null, null, "source_not_compound_full_block_dy_-1"));
        }
        if (intendedDirection == Direction.UP) {
            if (!compoundFullBlockSource) {
                return traceCompoundSlabRemap(world, sourcePos, sourceState, intendedDirection, hitPos,
                        CompoundSlabRemapDecision.rejected(sourcePos, null, null, "source_not_compound_full_block_dy_-1"));
            }
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
        RuntimeDiagnostics.logSlabSupportDecision(world, sourcePos, sourceState, intendedDirection, hitPos, decision);
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

    public static boolean isCompoundVisibleFullBlockSource(BlockGetter world, BlockPos pos, BlockState state) {
        return world != null
                && pos != null
                && state != null
                && !(state.getBlock() instanceof SlabBlock)
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, pos, state)
                && Math.abs(getYOffset(world, pos, state) + 1.0d) <= 1.0e-6d;
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
                || !state.isSolidRender(world, pos)) {
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
        return isLoweredCarrier(world, pos, state, MAX_CHAIN_DEPTH);
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
                || !state.isSolidRender(world, pos)) {
            return false;
        }
        boolean hasBottomBelow = hasBottomSlabBelow(world, pos);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        return hasBottomBelow || anchored;
    }

    /**
     * A solid, non-slab, non-block-entity full block with AIR directly below it —
     * i.e. a block cantilevered out over empty space, the only case where merging a
     * full block down to a lowered neighbour is wanted (a block resting on solid
     * ground must NOT sink). Recursion-safe ({@code isSolidBlock} is guarded by
     * {@link #IN_GET_Y_OFFSET}); never calls {@link #getYOffset}.
     */
    private static boolean isCantileverFullBlockCandidate(BlockGetter world, BlockPos pos, BlockState state) {
        return world != null
                && pos != null
                && state != null
                && !state.isAir()
                && !(state.getBlock() instanceof SlabBlock)
                && !(state.getBlock() instanceof EntityBlock)
                && state.getFluidState().isEmpty()
                && state.isSolidRender(world, pos)
                && world.getBlockState(pos.below()).isAir();
    }

    /**
     * A full block that is GENUINELY lowered by its own support — a slab directly
     * below, a direct/vertical anchor, or a lowered column reaching a slab beneath it
     * (e.g. an upper log in a lowered trunk). This is the "anchor" a cantilever lane
     * may hang off of; cantilever-lowered blocks themselves are deliberately excluded
     * (they are lane members, reached by the walk, not sources) so lowering can never
     * be self-sustaining without a real support. Recursion-safe: every predicate here
     * is already invoked by {@link #getYOffsetInner} under the {@link #IN_GET_Y_OFFSET}
     * guard and none call {@link #getYOffset}.
     */
    private static boolean isGenuinelyLoweredFullBlockSource(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidRender(world, pos)) {
            return false;
        }
        return hasBottomSlabBelow(world, pos)
                || SlabAnchorAttachment.isAnchored(world, pos)
                || (shouldOffset(world, pos, state) && slabColumnYOffset(world, pos) < -1.0e-6);
    }

    /**
     * Geometric, recursion-safe replacement for the (removed) stale side-adjacent
     * full-block anchor: a full block cantilevered over air lowers {@code -0.5} to
     * merge flush with a lowered tower it is connected to, computed live so it
     * recomputes whenever the structure changes — it never goes stale and never
     * "pops" out of sync with its neighbours.
     *
     * <p>Breadth-first walk through connected cantilever full blocks (each over air),
     * bounded by {@link #MAX_CHAIN_DEPTH}, returning true as soon as the lane reaches
     * a GENUINE lowered source: a full-block carrier lowered by a slab below it or a
     * direct/vertical anchor ({@link #isLoweredFullBlockCarrier}), or a lowered slab
     * ({@link #isAdjacentSideSlabLowered}) so mixed slab+block canopies settle at one
     * consistent level. Mirrors the slab lane BFS ({@code hasLoweredSlabLaneSupport})
     * and the proven 1.21.11 {@code isAdjacentToLoweredFullBlock} model. Calls neither
     * {@link #getYOffset} nor itself with circular dependence, so it is safe inside the
     * {@link #IN_GET_Y_OFFSET} guard.
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
                && !isNoSnapConnectorFlatLane(world, pos, state)
                && world.getBlockState(pos.below()).isAir();
    }

    private static boolean isNoSnapConnectorUnderLoweredSlab(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null
                || pos == null
                || !isBeta35FenceWallVariantContactObject(state)
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        BlockPos abovePos = pos.above();
        BlockState above = world.getBlockState(abovePos);
        return above != null
                && above.getBlock() instanceof SlabBlock
                && above.hasProperty(SlabBlock.TYPE)
                && above.getFluidState().isEmpty()
                && !Double.isNaN(loweredSlabMagnitude(world, abovePos, above));
    }

    private static boolean isNoSnapConnectorFlatLane(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null
                || pos == null
                || !isBeta35FenceWallVariantContactObject(state)
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        BlockPos cursor = pos;
        BlockState cursorState = state;
        for (int depth = 0; depth < MAX_CHAIN_DEPTH; depth++) {
            if (isNoSnapConnectorUnderLoweredSlab(world, cursor, cursorState)) {
                return true;
            }
            BlockPos abovePos = cursor.above();
            BlockState above = world.getBlockState(abovePos);
            if (!isBeta35FenceWallVariantContactObject(above) || !isSameConnectorFlatLaneFamily(state, above)) {
                return false;
            }
            cursor = abovePos;
            cursorState = above;
        }
        return false;
    }

    private static boolean isSameConnectorFlatLaneFamily(BlockState state, BlockState other) {
        Block block = state.getBlock();
        Block otherBlock = other.getBlock();
        return (block instanceof FenceBlock && otherBlock instanceof FenceBlock)
                || (block instanceof WallBlock && otherBlock instanceof WallBlock)
                || (block instanceof IronBarsBlock && otherBlock instanceof IronBarsBlock);
    }

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
                if (!neighborIsSlab && (isGenuinelyLoweredFullBlockSource(world, neighborPos, neighbor)
                        || isCantileverLoweredFullBlock(world, neighborPos, neighbor))) {
                    double magnitude = loweredFullBlockMagnitude(world, neighborPos, neighbor);
                    return Double.isNaN(magnitude) ? -0.5d : magnitude;
                }
                if (neighborIsSlab && isAdjacentSideSlabLowered(world, neighborPos, neighbor)) {
                    double magnitude = loweredSlabMagnitude(world, neighborPos, neighbor);
                    return Double.isNaN(magnitude) ? -0.5d : magnitude;
                }
                double connectingSource = loweredSupportedConnectingMagnitude(world, neighborPos, neighbor);
                if (!Double.isNaN(connectingSource)) {
                    return connectingSource;
                }
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
                || isNoSnapConnectorFlatLane(world, pos, state)
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
                || !state.isSolidRender(world, pos)) {
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

    private static double adjacentLoweredSideMagnitude(BlockGetter world, BlockPos pos) {
        if (world == null || pos == null) {
            return Double.NaN;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighbor = world.getBlockState(neighborPos);
            if (neighbor == null || neighbor.isAir()) {
                continue;
            }
            if (neighbor.getBlock() instanceof SlabBlock) {
                double magnitude = loweredSlabMagnitude(world, neighborPos, neighbor);
                if (!Double.isNaN(magnitude)) {
                    return magnitude;
                }
                continue;
            }
            if (isGenuinelyLoweredFullBlockSource(world, neighborPos, neighbor)
                    || isCantileverLoweredFullBlock(world, neighborPos, neighbor)) {
                double magnitude = loweredFullBlockMagnitude(world, neighborPos, neighbor);
                return Double.isNaN(magnitude) ? -0.5d : magnitude;
            }
        }
        return Double.NaN;
    }

    private static double loweredFullBlockMagnitude(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidRender(world, pos)) {
            return Double.NaN;
        }
        if (isOrdinaryFullBlockWithCompoundDy(world, pos, state)) {
            return -1.0d;
        }
        BlockState below = world.getBlockState(pos.below());
        double supportDy = floorTorchBottomSlabSupportDy(world, pos.below(), below);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return Math.max(-1.0d, supportDy - 0.5d);
        }
        return -0.5d;
    }

    private static double loweredSlabMagnitude(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)) {
            return Double.NaN;
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return -1.0d;
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)
                || isAdjacentSideSlabLowered(world, pos, state)
                || (state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                        && state.getFluidState().isEmpty()
                        && SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state))) {
            return -0.5d;
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
        if (hasVanillaFullBlockSupportBelow(world, slabPos)) {
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
            if (hasVanillaFullBlockSupportBelow(world, cursor)) {
                continue;
            }
            if (isCompatibleLoweredSlabLane(slabState, cursorState)
                    && hasExplicitLoweredSlabLaneAuthority(world, cursor, cursorState)) {
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

    private static boolean hasVanillaFullBlockSupportBelow(BlockGetter world, BlockPos slabPos) {
        if (world == null || slabPos == null) {
            return false;
        }
        BlockPos belowPos = slabPos.below();
        BlockState below = world.getBlockState(belowPos);
        return below != null
                && !below.isAir()
                && !(below.getBlock() instanceof SlabBlock)
                && below.getFluidState().isEmpty()
                && below.isSolidRender(world, belowPos)
                && !isFullHeightLoweredCarrierForSideSupport(world, belowPos, below);
    }

    private static boolean isLoweredSlabLaneOwnerForSideInheritance(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        return hasExplicitLoweredSlabLaneAuthority(world, pos, state);
    }

    private static boolean hasExplicitLoweredSlabLaneAuthority(
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
                && (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
                        || SlabAnchorAttachment.isAnchored(world, pos)
                        || hasLoweredCarrierBelow(world, pos)
                        || SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                        || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                        || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state));
    }

    public static boolean hasLoweredSideLaneCarrierAuthoringSupport(
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
                && !hasVanillaFullBlockSupportBelow(world, pos)
                && hasLoweredSolidSideSupport(world, pos);
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

    private static double getYOffsetInner(BlockGetter world, BlockPos pos, BlockState state) {
        // Ceiling-hung blocks hang from the block above, so they must not be
        // reclassified by floor/support-below branches. This mirrors the 26.2
        // source line and keeps hanging lanterns below raised chain bridges from
        // inheriting +0.5 and merging into the chain model.
        if (isAlwaysCeilingHungDecoration(state)
                || isDownwardPointedDripstone(state)
                || (state.hasProperty(BlockStateProperties.HANGING)
                        && state.getValue(BlockStateProperties.HANGING))) {
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
            // FREEZE-ON-PLACE: a slab locked lowered at placement (freezeLoweredOnPlace) reads
            // its anchor and never recomputes — so breaking an adjacent source can no longer
            // pop it back up, and its rendered mesh never drifts from the value.
            if (SlabAnchorAttachment.isAnchored(world, pos)) {
                if (state.getFluidState().isEmpty()
                        && world.getBlockState(pos.below()).isAir()
                        && !isCompoundVisibleOwnerTopSlab(world, pos, state)) {
                    double anchoredSideMagnitude = adjacentLoweredSideMagnitude(world, pos);
                    if (anchoredSideMagnitude < -0.5d - 1.0e-6d) {
                        return anchoredSideMagnitude;
                    }
                }
                return -0.5;
            }
            // FREEZE-ON-PLACE: a slab locked FLAT at placement stays at 0 — a lowered carrier
            // placed beside/under it later can no longer make it inherit a lowered position.
            if (SlabAnchorAttachment.isFrozenFlat(world, pos)) {
                return 0.0;
            }
            if (state.hasProperty(SlabBlock.TYPE)
                    && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                    && isBottomPersistentTracePos(pos)) {
                boolean persistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state);
                boolean nonRecursiveBottomCarrier =
                        SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state);
                boolean branchReached = state.getFluidState().isEmpty() && nonRecursiveBottomCarrier;
                Slabbed.LOGGER.info("[BOTTOM_PERSISTENT] getYOffsetInner pos=" + shortPos(pos)
                        + " state=" + state
                        + " slabType=" + state.getValue(SlabBlock.TYPE)
                        + " fluidEmpty=" + state.getFluidState().isEmpty()
                        + " worldClass=" + world.getClass().getName()
                        + " guard=" + IN_GET_Y_OFFSET.get()
                        + " persistentLoweredSlabCarrier=" + persistentCarrier
                        + " nonRecursiveBottomCarrier=" + nonRecursiveBottomCarrier
                        + " branchReached=" + branchReached);
            }
            if (state.hasProperty(SlabBlock.TYPE)
                    && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                    && state.getFluidState().isEmpty()
                    && SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state)) {
                if (isBottomPersistentTracePos(pos)) {
                    Slabbed.LOGGER.info("[BOTTOM_PERSISTENT] branch=return_-0.5 pos=" + shortPos(pos));
                }
                return -0.5;
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
            // Adjacent-side-slab alignment fallback: non-compound side lanes inherit -0.5.
            // Compound over-air lanes returned the neighbor's actual magnitude above.
            if (!isCompoundVisibleOwnerTopSlab(world, pos, state)
                    && isAdjacentSideSlabLowered(world, pos, state)) {
                return -0.5;
            }
        }

        // WYSIWYG no-snap lane: a connector authored directly under a lowered slab,
        // plus same-family connector descendants in that vertical lane, stay at the
        // vanilla grid height even if stale anchors or old lowered connectors would
        // otherwise pull them through the stacked-fence contact path.
        if (isNoSnapConnectorFlatLane(world, pos, state)) {
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
                            side, shortPos(pos), state);
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
                            side, shortPos(pos), state, shortPos(belowPos), belowSlab);
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
            if (isCantileverConnectingCandidate(world, pos, state)) {
                double anchoredConnectingMagnitude = cantileverLoweredConnectingMagnitude(world, pos, state);
                if (anchoredConnectingMagnitude < -0.5d - 1.0e-6d) {
                    return anchoredConnectingMagnitude;
                }
            }
            if (isBeta35FenceWallVariantContactObject(state)) {
                double stackedFenceDy = beta35FenceWallVariantContactDy(world, pos, state);
                if (Double.isFinite(stackedFenceDy) && stackedFenceDy < -0.5d - 1.0e-6d) {
                    return stackedFenceDy;
                }
            }
            if (com.slabbed.anchor.SlabAnchorAttachment.TRACE) {
                String side = (world instanceof net.minecraft.world.level.Level w && w.isClientSide()) ? "CLIENT" : "SERVER";
                Slabbed.LOGGER.info("[ANCHOR] dy applied side={} pos={} state={} dy=-0.5",
                        side, shortPos(pos), state);
            }
            return -0.5;
        }

        // FREEZE-ON-PLACE: a structural full block locked FLAT at placement stays at 0 — a
        // slab or lowered carrier added under/beside it later can no longer pull it down
        // (Julia's law: a placed block must not autonomously move). Read before any of the
        // geometric lowering below. Decorative followers are never frozen-flat, so they keep
        // tracking their supports.
        if (SlabAnchorAttachment.isFrozenFlat(world, pos)) {
            return 0.0;
        }

        // Cantilevered full block (air below, connected to a lowered tower): lower -0.5 to
        // merge, computed GEOMETRICALLY — it recomputes whenever the structure changes, so it
        // never goes stale and never pops out of sync (the replacement for the removed
        // side-adjacent anchor). Air-gated, so a block resting on solid ground never sinks.
        // Together with the isAdjacentSideSlabLowered slab branch above, mixed canopies of
        // slabs and full blocks settle at one consistent lowered level off the same tower.
        if (isCantileverLoweredFullBlock(world, pos, state)) {
            return -0.5;
        }

        if (!isNoSnapConnectorFlatLane(world, pos, state)) {
            double connectingMagnitude = cantileverLoweredConnectingMagnitude(world, pos, state);
            if (!Double.isNaN(connectingMagnitude)) {
                return connectingMagnitude;
            }
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

        // Direct Terrain Slabs custom support: an ordinary object/full block/vanilla slab
        // sitting directly on a named TS BOTTOM_LIKE surface lowers -0.5, matching how it
        // already lowers onto a vanilla bottom slab. Deliberately separate from the generic
        // shouldOffset/hasSlabInColumn path below (which never recognizes TS -- that blanket
        // exclusion stays unchanged) -- see isDirectCustomSlabSupportSubject's javadoc for why.
        double directCustomSurfaceDy = directCustomSlabSupportDy(world, pos, state);
        if (!Double.isNaN(directCustomSurfaceDy)) {
            double dy = directCustomSurfaceDy;
            // A vanilla TOP slab caps from the UPPER half of its own block, so it needs an
            // extra -0.5 to sit flush on a BOTTOM_LIKE surface (else a half-block gap shows
            // underneath). BOTTOM/DOUBLE slabs and non-slab objects are unaffected.
            if (state.getBlock() instanceof SlabBlock
                    && state.hasProperty(SlabBlock.TYPE)
                    && state.getValue(SlabBlock.TYPE) == SlabType.TOP) {
                dy -= 0.5d;
            }
            return Math.max(dy, -1.0d);
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

        // Top-half trapdoors under lowered slab undersides should follow the
        // support lane height, not force vanilla top-slab +0.5.
        if (state.getBlock() instanceof TrapDoorBlock
                && state.hasProperty(BlockStateProperties.HALF)
                && state.getValue(BlockStateProperties.HALF) == Half.TOP) {
            BlockPos supportPos = pos.above();
            BlockState supportState = world.getBlockState(supportPos);
            if (supportState.getBlock() instanceof SlabBlock && supportState.hasProperty(SlabBlock.TYPE)) {
                SlabType supportType = supportState.getValue(SlabBlock.TYPE);
                if (supportType == SlabType.TOP || supportType == SlabType.BOTTOM || supportType == SlabType.DOUBLE) {
                    double supportDy = loweredSlabUndersideSupportDy(world, supportPos, supportState);
                    if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
                        return supportType == SlabType.TOP ? supportDy + 0.5d : supportDy;
                    }
                }
            }
        }

        // Ceiling-attached visual owners under lowered slab undersides inherit
        // the slab lane dy to stay attached. A hanger attaches at its own
        // block-top (P.y+1); a slab's underside sits at P.y+1 for BOTTOM/DOUBLE
        // (full-height bottom face) but at P.y+1.5 for a TOP slab (mid-block
        // underside). So under a lowered TOP slab the hanger's correct dy is the
        // slab dy PLUS the +0.5 raised-attach baseline; BOTTOM/DOUBLE take the
        // slab dy directly. Mirrors the top-trapdoor branch above. Without the
        // +0.5, hangers under a lowered top slab drop 0.5 too far (visible gap).
        if (isBeta35LoweredSlabUndersideVisibleOwnerObject(world, pos, state)) {
            BlockPos supportPos = pos.above();
            BlockState supportState = world.getBlockState(supportPos);
            double supportDy = loweredSlabUndersideSupportDy(world, supportPos, supportState);
            if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
                return supportState.getValue(SlabBlock.TYPE) == SlabType.TOP ? supportDy + 0.5d : supportDy;
            }
        }

        // Decorative hangers under a lowered FULL-BLOCK support inherit the
        // support's exact rendered dy (the full-block analogue of the slab
        // branch above) so they hang flush instead of clipping up into the
        // lowered block. Runs BEFORE the top-slab +0.5 branch; full blocks are
        // never top slabs so +0.5 adherence is untouched, and the helper returns
        // NaN for normal (non-lowered) supports so the already-correct flush case
        // is preserved. Chains are excluded (not in the decorative owner set).
        if (isBeta35LoweredFullBlockUndersideVisibleOwnerObject(world, pos, state)) {
            BlockPos supportPos = pos.above();
            BlockState supportState = world.getBlockState(supportPos);
            double supportDy = loweredFullBlockUndersideSupportDy(world, supportPos, supportState);
            if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
                return supportDy;
            }
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
                || isThinTopLayer(state)
                || state.isAir()
                || !state.getFluidState().isEmpty()
                || state.isSolidRender(world, pos)) {
            return 0.0;
        }

        BlockState above = world.getBlockState(pos.above());

        if (isDownwardPointedDripstone(state)) {
            if (verticalChainColumnRootsAtTopSlab(world, pos.above(), above)
                    || downwardPointedDripstoneColumnRootsThroughTopSlabChain(world, pos.above(), above)) {
                return 0.0d;
            }
            double supportDy = downwardPointedDripstoneLoweredCeilingSupportDy(world, pos.above(), above);
            if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
                return supportDy;
            }
        }

        // A lantern hung from the bottom of a ceiling-bridged chain is a descendant of the
        // raised chain model, not a second raised segment. Keeping it at grid height avoids
        // the visible merge/jitter where its top cap rises into the chain.
        if (state.hasProperty(BlockStateProperties.HANGING)
                && state.getValue(BlockStateProperties.HANGING)
                && isCeilingBridgedVerticalChainColumnMember(world, pos.above(), above)) {
            return 0.0d;
        }

        // direct: ceiling-attached blocks directly under a top slab
        if (isCeilingAttached(state) && isTopSlab(above)) {
            return 0.5;
        }

        // Descendant chains in a ceiling-bridged column stay grid-height; the direct top chain
        // kept the +0.5 ceiling-attach semantic dy above and is model-bypassed on the client.
        if (isBeta35VerticalChainVisibleOwnerObject(state)
                && isCeilingBridgedVerticalChainColumnMember(world, pos, state)) {
            return 0.0d;
        }

        // A Y-axis chain column hanging beneath a LOWERED eave follows the eave DOWN so the
        // whole column renders flush: the top link's top meets the eave underside and each
        // lower link stacks against the one above with no inter-link gap (the reported "gap
        // between chains"). Walk up through same-column chains to the first non-chain support;
        // if that support renders lowered, inherit its dy for THIS segment. Every segment
        // resolves to the SAME support dy, so the column renders as one flush unit.
        // Ordering matters: this runs AFTER the +0.5 top-slab branch and the ceiling-bridged
        // branch, so a column reaching UP toward a slab ceiling keeps its +0.5/0.0 semantics.
        // Top slabs are excluded here (kept on the existing +0.5 raise path so every segment
        // stays consistent); only a lowered BOTTOM/DOUBLE slab or a lowered full-block eave
        // pulls the column down. The helpers return NaN / a non-negative dy for non-lowered
        // supports, so a flush eave leaves the column at grid height (no change). This branch
        // can only ever return a NEGATIVE dy — it never raises. Recursion-safe: the helpers
        // never call getYOffset (see their javadoc).
        if (isBeta35VerticalChainVisibleOwnerObject(state)) {
            BlockPos supportCursor = pos.above();
            for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
                BlockState cur = world.getBlockState(supportCursor);
                if (isBeta35VerticalChainVisibleOwnerObject(cur)) {
                    supportCursor = supportCursor.above();
                    continue;
                }
                if (cur.getBlock() instanceof SlabBlock && cur.hasProperty(SlabBlock.TYPE)
                        && cur.getValue(SlabBlock.TYPE) != SlabType.TOP) {
                    double slabDy = loweredSlabUndersideSupportDy(world, supportCursor, cur);
                    if (Double.isFinite(slabDy) && slabDy < -1.0e-6d) {
                        return slabDy;
                    }
                }
                double fullBlockDy = loweredFullBlockUndersideSupportDy(world, supportCursor, cur);
                if (Double.isFinite(fullBlockDy) && fullBlockDy < -1.0e-6d) {
                    return fullBlockDy;
                }
                break;
            }
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

    private static boolean isBottomPersistentTracePos(BlockPos pos) {
        return Boolean.getBoolean(BOTTOM_PERSISTENT_TRACE_OPT_IN)
                && pos != null && pos.getX() == 0 && pos.getY() == 202 && pos.getZ() == 0;
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
        if (!(block instanceof net.minecraft.world.level.block.TorchBlock
                || block instanceof net.minecraft.world.level.block.WallTorchBlock)) {
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
        return state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                && state.hasProperty(BlockStateProperties.BED_PART)
                && getYOffset(world, pos, state) == -0.5;
    }

    /**
     * Redstone dust support surface — treat slab tops like valid ground for downward stepping.
     */
    public static boolean isRedstoneSupportTopSurface(BlockGetter world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        if (state.isFaceSturdy(world, pos, Direction.UP, SupportType.FULL)) {
            return true;
        }

        return isSupportingSlab(state) && (isBottomSlab(state) || isTopSlab(state));
    }

    /**
     * True if {@code state} at {@code pos} may sit on a Terrain Slabs BOTTOM_LIKE surface
     * (directly, or via a column of other qualifying blocks) and inherit its -0.5 lowering.
     *
     * <p>Deliberately narrow and additive: this does NOT change
     * {@link CompatHooks#shouldSkipSlabSupport}/{@link CompatHooks#shouldSkipOffset} (those
     * stay a blanket exclusion, so TS blocks are never treated as generic vanilla-style slab
     * support -- unrelated logic like the chain-ceiling/cull/isSolidBlock paths that already
     * depend on that blanket exclusion staying blanket is untouched). This is a separate,
     * opt-in query, consulted only from the direct-object-support dy path below and from
     * {@link #canTreatAsSolidTopFace}. A previous attempt on a different Slabbed port blurred
     * this line (made TS both a generic support AND a dy source) and broke TS object-placement
     * plus a chain-ceiling case -- see repo-local lesson memory before broadening this.
     */
    private static boolean isDirectCustomSlabSupportSubject(BlockGetter world, BlockPos pos, BlockState state) {
        if (state == null
                || state.isAir()
                || (state.getBlock() instanceof SlabBlock && !isVanillaDirectCustomSlabSubject(state))
                || isThinTopLayer(state)
                || !state.getFluidState().isEmpty()
                || CompatHooks.shouldSkipOffset(state)) {
            return false;
        }
        return isVanillaDirectCustomSlabSubject(state) || isSlabSitCandidate(world, pos, state);
    }

    private static boolean isVanillaDirectCustomSlabSubject(BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock) || !state.hasProperty(SlabBlock.TYPE)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && "minecraft".equals(id.getNamespace());
    }

    /**
     * Walks down from {@code supportPos} through qualifying direct-custom-slab-support
     * subjects looking for a named TS BOTTOM_LIKE surface. Returns true as soon as one is
     * found; bounded by {@link #MAX_CHAIN_DEPTH}.
     */
    private static boolean hasDirectCustomBottomLikeSupportColumn(BlockGetter world, BlockPos supportPos) {
        BlockPos cursor = supportPos;
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState supportState = world.getBlockState(cursor);
            if (CompatHooks.customSlabSurfaceKind(supportState) == CompatSlabSurfaceKind.BOTTOM_LIKE) {
                return true;
            }
            if (isDirectCustomSlabSupportSubject(world, cursor, supportState)) {
                cursor = cursor.below();
                continue;
            }
            return false;
        }
        return false;
    }

    /**
     * True if {@code state} at {@code pos} directly (or via a column of qualifying blocks)
     * sits on a named Terrain Slabs BOTTOM_LIKE surface and should inherit its -0.5 lowering.
     */
    public static boolean isDirectCustomSlabSupportedObject(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isDirectCustomSlabSupportSubject(world, pos, state)) {
            return false;
        }
        return hasDirectCustomBottomLikeSupportColumn(world, pos.below());
    }

    /**
     * Returns -0.5 when {@code state} qualifies as {@link #isDirectCustomSlabSupportedObject},
     * else {@link Double#NaN} (not a qualifying direct-custom-slab-support case; caller falls
     * through to the generic vanilla-slab column check).
     */
    private static double directCustomSlabSupportDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (!isDirectCustomSlabSupportedObject(world, pos, state)) {
            return Double.NaN;
        }
        return -0.5d;
    }

    /**
     * Which subjects may sit directly on a custom (TS) slab support: any block that already
     * passed {@link #isDirectCustomSlabSupportSubject}'s exclusion guards (not air, not a
     * non-vanilla slab, not a thin top layer, not fluid-filled, not itself a TS block)
     * qualifies -- matching how ordinary full blocks (stone, dirt, grass, ...) already sit on
     * and lower onto a vanilla bottom slab via {@link #hasSlabInColumn}.
     */
    private static boolean isSlabSitCandidate(BlockGetter world, BlockPos pos, BlockState state) {
        return true;
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

    private static String shortPos(BlockPos pos) {
        return pos == null ? "null" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
