package com.slabbed.util;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.CaveVinesBodyBlock;
import net.minecraft.block.CaveVinesHeadBlock;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.HangingRootsBlock;
import net.minecraft.block.HangingSignBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.block.PowderSnowBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.SporeBlossomBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.WallHangingSignBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;

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
        return block instanceof SnowBlock
                || block instanceof CarpetBlock
                || isPaleMossCarpet(block);
    }

    private static boolean isPaleMossCarpet(Block block) {
        return block == Registries.BLOCK.get(Identifier.of("minecraft", "pale_moss_carpet"));
    }

    /**
     * Returns true if the block at {@code pos} is a slab whose top face can provide support.
     */
    public static boolean isSupportingSlab(WorldView world, BlockPos pos) {
        return isSupportingSlab(world.getBlockState(pos));
    }

    /** Overload for BlockView contexts (shapes). */
    public static boolean isSupportingSlab(BlockView world, BlockPos pos) {
        return isSupportingSlab(world.getBlockState(pos));
    }

    /**
     * Returns true if the state is a slab with a defined type.
     */
    public static boolean isSupportingSlab(BlockState state) {
        if (CompatHooks.shouldSkipSlabSupport(state)) {
            return false;
        }
        return state.getBlock() instanceof SlabBlock && state.contains(SlabBlock.TYPE);
    }

    /** True if this state is a bottom slab. */
    public static boolean isBottomSlab(BlockState state) {
        return isSupportingSlab(state) && state.get(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /** True if this state is a top slab. */
    public static boolean isTopSlab(BlockState state) {
        return isSupportingSlab(state) && state.get(SlabBlock.TYPE) == SlabType.TOP;
    }

    /**
     * Single source of truth: returns true iff the state is a TOP slab
     * and the queried face is DOWN (i.e. the underside of a top slab).
     */
    public static boolean isTopSlabUndersideSupport(BlockState state, Direction face) {
        return face == Direction.DOWN && isTopSlab(state);
    }

    /** True if the block at {@code posAbove} is a top or double slab that can provide ceiling support. */
    public static boolean isCeilingSupportBottomSurface(WorldView world, BlockPos posAbove) {
        BlockState stateAbove = world.getBlockState(posAbove);
        if (!isSupportingSlab(stateAbove)) {
            return false;
        }
        SlabType type = stateAbove.get(SlabBlock.TYPE);
        return type == SlabType.TOP || type == SlabType.DOUBLE;
    }

    /** Overload for shape/world views. */
    public static boolean isCeilingSupportBottomSurface(BlockView world, BlockPos posAbove) {
        BlockState stateAbove = world.getBlockState(posAbove);
        if (!isSupportingSlab(stateAbove)) {
            return false;
        }
        SlabType type = stateAbove.get(SlabBlock.TYPE);
        return type == SlabType.TOP || type == SlabType.DOUBLE;
    }

    /** True if the block immediately below {@code pos} is a bottom slab providing its top face. */
    public static boolean hasBottomSlabBelow(BlockView world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockState below = world.getBlockState(pos.down());
        // Terrain Slabs compat (opt-in; NONE/inert without the mod): a custom BOTTOM_LIKE
        // surface acts as a bottom slab for direct object support.
        return isBottomSlab(below)
                || CompatHooks.customSlabSurfaceKind(below) == CompatSlabSurfaceKind.BOTTOM_LIKE;
    }

    /**
     * Effective Y offset of the slab's top face relative to the slab block position.
     * 0.5 for bottom slabs, 1.0 for top/double.
     */
    public static double getSupportYOffset(BlockState state) {
        if (!isSupportingSlab(state)) {
            throw new IllegalArgumentException("Not a supporting slab: " + state);
        }
        SlabType type = state.get(SlabBlock.TYPE);
        return switch (type) {
            case BOTTOM -> 0.5;
            case TOP, DOUBLE -> 1.0;
        };
    }

    /**
     * Top-surface offset a block presents for DIRECT object support: 0.5 for a bottom-like
     * surface (vanilla bottom slab OR a Terrain Slabs BOTTOM_LIKE surface), 1.0 for a
     * top/double-like surface, else 0.0. Opt-in for custom surfaces (NONE without the mod).
     */
    public static double getDirectObjectSupportTopOffset(BlockState state) {
        return switch (CompatHooks.customSlabSurfaceKind(state)) {
            case BOTTOM_LIKE -> 0.5;
            case TOP_LIKE, DOUBLE_LIKE -> 1.0;
            case NONE, UNKNOWN -> {
                if (isBottomSlab(state)) {
                    yield 0.5;
                }
                if (isTopSlab(state) || isSupportingSlab(state) && state.get(SlabBlock.TYPE) == SlabType.DOUBLE) {
                    yield 1.0;
                }
                yield 0.0;
            }
        };
    }

    /** True if this state presents a top surface that can directly support a lowered object. */
    public static boolean isDirectObjectSupportSurface(BlockView world, BlockPos pos, BlockState state) {
        return getDirectObjectSupportTopOffset(state) > 0.0;
    }

    /**
     * Primary query: should this slab top face count as solid support. Includes Terrain
     * Slabs named surfaces (opt-in) so objects can be placed on them.
     */
    public static boolean canTreatAsSolidTopFace(WorldView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return isSupportingSlab(state) || isDirectObjectSupportSurface(world, pos, state);
    }

    /** Overload for shape/world views. */
    public static boolean canTreatAsSolidTopFace(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return isSupportingSlab(state) || isDirectObjectSupportSurface(world, pos, state);
    }

    public static boolean isFloorTorch(BlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof net.minecraft.block.TorchBlock && !(block instanceof WallTorchBlock);
    }

    private static boolean isBeta35FloorTopContactObject(BlockState state) {
        return state != null && (state.isOf(Blocks.CANDLE) || state.isOf(Blocks.FLOWER_POT));
    }

    public static boolean isBeta35FloorButtonContactObject(BlockState state) {
        return state != null
                && state.getBlock() instanceof ButtonBlock
                && state.contains(Properties.BLOCK_FACE)
                && state.get(Properties.BLOCK_FACE) == BlockFace.FLOOR;
    }

    public static boolean isBeta35BottomTrapdoorVisibleOwnerObject(BlockState state) {
        return state != null
                && state.getBlock() instanceof TrapdoorBlock
                && state.contains(Properties.BLOCK_HALF)
                && state.get(Properties.BLOCK_HALF) == BlockHalf.BOTTOM;
    }

    public static boolean isBeta35VerticalChainVisibleOwnerObject(BlockState state) {
        return state != null
                && state.getBlock() instanceof ChainBlock
                && state.contains(Properties.AXIS)
                && state.get(Properties.AXIS) == Direction.Axis.Y;
    }

    public static boolean isBeta35RegularDoorVisibleOwnerObject(
            BlockView world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!(state.getBlock() instanceof DoorBlock) || !state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return false;
        }
        double objectDy = getYOffset(world, pos, state);
        return Double.isFinite(objectDy) && objectDy < -1.0e-6d;
    }

    public static boolean isBeta35LoweredRegularDoorServerHitTarget(
            BlockView world, BlockPos pos, BlockState state
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
            BlockView world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof DoorBlock)
                || !state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            return false;
        }
        DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
        BlockPos pairedPos = half == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
        BlockState pairedState = world.getBlockState(pairedPos);
        if (pairedState == null
                || pairedState.getBlock() != state.getBlock()
                || !pairedState.contains(Properties.DOUBLE_BLOCK_HALF)
                || pairedState.get(Properties.DOUBLE_BLOCK_HALF) == half) {
            return false;
        }
        if (state.contains(Properties.HORIZONTAL_FACING)
                && pairedState.contains(Properties.HORIZONTAL_FACING)
                && state.get(Properties.HORIZONTAL_FACING) != pairedState.get(Properties.HORIZONTAL_FACING)) {
            return false;
        }
        if (state.contains(Properties.DOOR_HINGE)
                && pairedState.contains(Properties.DOOR_HINGE)
                && state.get(Properties.DOOR_HINGE) != pairedState.get(Properties.DOOR_HINGE)) {
            return false;
        }
        if (state.contains(Properties.OPEN)
                && pairedState.contains(Properties.OPEN)
                && state.get(Properties.OPEN) != pairedState.get(Properties.OPEN)) {
            return false;
        }
        return !state.contains(Properties.POWERED)
                || !pairedState.contains(Properties.POWERED)
                || state.get(Properties.POWERED) == pairedState.get(Properties.POWERED);
    }

    public static boolean isBeta35LoweredTrapdoorOrFloorButtonVisibleTarget(
            BlockView world, BlockPos pos, BlockState state
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
            BlockView world, BlockPos pos, BlockState state
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
            BlockView world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || !isBeta35BottomTrapdoorVisibleOwnerObject(state)) {
            return false;
        }
        double objectDy = getBeta35ShiftedServerValidationYOffset(world, pos, state);
        return Double.isFinite(objectDy) && objectDy < -1.0e-6d;
    }

    public static boolean isBeta35LoweredTrapdoorOrFloorButtonServerHitTarget(
            BlockView world, BlockPos pos, BlockState state
    ) {
        return isBeta35LoweredTrapdoorOrFloorButtonVisibleOwnerTarget(world, pos, state)
                || isBeta35LoweredBottomTrapdoorServerHitTarget(world, pos, state);
    }

    public static double getBeta35ShiftedServerValidationYOffset(BlockView world, BlockPos pos, BlockState state) {
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

    private static double beta35BottomTrapdoorVisibleOwnerDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35BottomTrapdoorVisibleOwnerObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.down();
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
            BlockView world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!isBeta35FloorButtonContactObject(state)
                && !isBeta35BottomTrapdoorVisibleOwnerObject(state)
                && !isBeta35VerticalChainVisibleOwnerObject(state)
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
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        if (id == null || !"minecraft".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return "pale_hanging_moss".equals(path) || "pale_hanging_moss_tip".equals(path);
    }

    private static boolean isBeta35LoweredSlabUndersideVisibleOwnerObject(
            BlockView world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null || state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        Block block = state.getBlock();
        boolean supportedCeilingObject = state.isOf(Blocks.LANTERN)
                || state.isOf(Blocks.SOUL_LANTERN)
                || block instanceof SporeBlossomBlock
                || block instanceof HangingRootsBlock
                || isPaleHangingMossBlock(state);
        if (!supportedCeilingObject) {
            return false;
        }
        BlockPos supportPos = pos.up();
        BlockState supportState = world.getBlockState(supportPos);
        if (!(supportState.getBlock() instanceof SlabBlock) || !supportState.contains(SlabBlock.TYPE)) {
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
            BlockView world, BlockPos pos, BlockState state
    ) {
        if (world == null || pos == null || state == null || state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        Block block = state.getBlock();
        boolean supportedCeilingObject = state.isOf(Blocks.LANTERN)
                || state.isOf(Blocks.SOUL_LANTERN)
                || block instanceof SporeBlossomBlock
                || block instanceof HangingRootsBlock
                || isPaleHangingMossBlock(state);
        if (!supportedCeilingObject) {
            return false;
        }
        BlockPos supportPos = pos.up();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = loweredFullBlockUndersideSupportDy(world, supportPos, supportState);
        return Double.isFinite(supportDy) && supportDy < -1.0e-6d;
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
                && state.isOf(Blocks.OAK_TRAPDOOR)
                && state.contains(Properties.BLOCK_HALF)
                && state.get(Properties.BLOCK_HALF) == BlockHalf.BOTTOM;
    }

    private static boolean isBeta35RegularDoorContactObject(BlockState state) {
        return state != null
                && state.getBlock() instanceof DoorBlock
                && state.contains(Properties.DOUBLE_BLOCK_HALF);
    }

    private static boolean isBeta35StandingOakSignContactObject(BlockState state) {
        return state != null && state.isOf(Blocks.OAK_SIGN);
    }

    private static double loweredSlabUndersideSupportDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE) || !state.getFluidState().isEmpty()) {
            return Double.NaN;
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return -1.0d;
        }
        SlabType type = state.get(SlabBlock.TYPE);
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
    private static double loweredFullBlockUndersideSupportDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidBlock(world, pos)) {
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
            BlockPos belowPos = pos.down();
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

    private static double beta35FenceWallVariantContactDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35FenceWallVariantContactObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.down();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = beta35FenceWallVisibleSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            double supportTopOffset = isSupportingSlab(supportState) ? getSupportYOffset(supportState) : 1.0d;
            return supportDy + supportTopOffset - 1.0d;
        }
        return Double.NaN;
    }

    private static double beta35FenceWallVisibleSupportDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || state.isAir() || !state.getFluidState().isEmpty()) {
            return Double.NaN;
        }
        if (isBottomSlab(state)) {
            return floorTorchBottomSlabSupportDy(world, pos, state);
        }
        if (isTopSlab(state) && SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)) {
            return -1.0d;
        }
        if (state.getBlock() instanceof SlabBlock && state.contains(SlabBlock.TYPE)
                && (state.get(SlabBlock.TYPE) == SlabType.TOP || state.get(SlabBlock.TYPE) == SlabType.DOUBLE)) {
            if (state.get(SlabBlock.TYPE) == SlabType.DOUBLE
                    && SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)) {
                return -1.0d;
            }
            BlockPos belowPos = pos.down();
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

    private static double beta35FenceGateContactDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35FenceGateContactObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.down();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy - 0.5d;
        }
        return Double.NaN;
    }

    private static double beta35FloorButtonContactDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35FloorButtonContactObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.down();
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

    private static double beta35OakTrapdoorContactDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35OakTrapdoorContactObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.down();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy - 0.5d;
        }
        return Double.NaN;
    }

    private static double beta35RegularDoorContactDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35RegularDoorContactObject(state)) {
            return Double.NaN;
        }
        BlockPos bottomPos = pos;
        if (state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            bottomPos = pos.down();
            BlockState bottomState = world.getBlockState(bottomPos);
            if (!isBeta35RegularDoorContactObject(bottomState)
                    || bottomState.get(Properties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) {
                return Double.NaN;
            }
        }
        BlockPos supportPos = bottomPos.down();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy - 0.5d;
        }
        return Double.NaN;
    }

    private static double beta35StandingOakSignContactDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isBeta35StandingOakSignContactObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.down();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy - 0.5d;
        }
        return Double.NaN;
    }

    private static boolean isBeta35OrdinaryFullBlockContactObject(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        Block block = state.getBlock();
        if (!(block instanceof CraftingTableBlock || block instanceof BlockEntityProvider)) {
            return false;
        }
        return state.isSolidBlock(world, pos);
    }

    private static double beta35OrdinaryFullBlockContactDy(BlockView world, BlockPos pos, BlockState state) {
        if (!isBeta35OrdinaryFullBlockContactObject(world, pos, state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.down();
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

    private static double beta35SpecialFullblockContactDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !isBeta35SpecialFullblockContactObject(state)) {
            return Double.NaN;
        }
        BlockPos supportPos = pos.down();
        BlockState supportState = world.getBlockState(supportPos);
        double supportDy = floorTorchBottomSlabSupportDy(world, supportPos, supportState);
        if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
            return supportDy - 0.5d;
        }
        return Double.NaN;
    }

    public static boolean canTreatAsFloorTorchTopFace(BlockView world, BlockPos supportPos, BlockState torchState) {
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
            BlockView world,
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

    public static boolean isRejectedFloorTorchTopFace(BlockView world, BlockPos supportPos, BlockState torchState) {
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
        if (state.contains(Properties.HANGING) && state.get(Properties.HANGING)) {
            return true;
        }
        // Y-axis chains
        if (state.getBlock() instanceof ChainBlock
                && state.contains(Properties.AXIS)
                && state.get(Properties.AXIS) == Direction.Axis.Y) {
            return true;
        }
        // Hanging signs
        if (state.getBlock() instanceof HangingSignBlock) {
            return true;
        }
        // Top-half trapdoors (ceiling-mounted)
        if (state.getBlock() instanceof TrapdoorBlock
                && state.contains(Properties.BLOCK_HALF)
                && state.get(Properties.BLOCK_HALF) == BlockHalf.TOP) {
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
                || block instanceof CaveVinesHeadBlock
                || block instanceof CaveVinesBodyBlock) {
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
    public static boolean shouldOffset(BlockView world, BlockPos pos, BlockState state) {
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
        if (isCeilingAttached(state) && isTopSlab(world.getBlockState(pos.up()))) {
            return false;
        }

        // ceiling-attached blocks further down a chain of ceiling blocks
        // leading to a top slab also get +0.5 UP; exclude from -0.5
        if (isCeilingAttached(state)) {
            BlockPos cursor = pos.up();
            for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
                BlockState cur = world.getBlockState(cursor);
                if (isTopSlab(cur)) {
                    return false;
                }
                if (isCeilingAttached(cur)) {
                    cursor = cursor.up();
                    continue;
                }
                break;
            }
        }

        // blocks hanging from above (lanterns, etc.) — don't offset DOWN by slab below
        // (they may get a separate +0.5 UP offset via getYOffset)
        if (state.contains(Properties.HANGING) && state.get(Properties.HANGING)) {
            return false;
        }

        // ── bed: either half has a slab ───────────────────────────────
        if (state.contains(Properties.BED_PART)) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            BedPart part = state.get(Properties.BED_PART);
            BlockPos otherPos;
            if (part == BedPart.FOOT) {
                otherPos = pos.offset(facing);
            } else {
                otherPos = pos.offset(facing.getOpposite());
            }
            return hasSlabInColumn(world, pos) || hasSlabInColumn(world, otherPos);
        }

        // ── double-block: upper half checks two blocks down ───────────
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
            if (half == DoubleBlockHalf.UPPER) {
                return isBottomSlab(world.getBlockState(pos.down(2)));
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
                && state.contains(Properties.HORIZONTAL_FACING)) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            BlockPos attachedPos = pos.offset(facing.getOpposite());
            if (hasSlabInColumn(world, attachedPos)) {
                return true;
            }
        }

        return false;
    }

    /** Kill switch for the slab-height step-face redraw (see {@link #isSlabHeightStepFace}). */
    private static final boolean STEP_CULL_DISABLED = Boolean.getBoolean("slabbed.disableStepCull");

    /**
     * Whether {@code direction}'s horizontal face of the opaque full cube at {@code pos}
     * is a slab-height "step" face — i.e. this block and its neighbour across that face
     * are at different vertical render offsets, so part of the face is visually exposed
     * but the chunk mesher (Indigo) would otherwise cull it (the see-through
     * "window"/"doom-infinity" hole on a lowered opaque cube at a slab step).
     *
     * <p><b>1.21.1 adaptation</b> of the 1.21.11 overhaul helper: here "lowered" is
     * {@code getYOffset < 0} (1.21.11 used the Terrain-Slabs-specific
     * {@code isDirectCustomSlabSupportedObject}, which does not exist on 1.21.1). Only
     * ever used to ADD faces, never remove. Disable with {@code -Dslabbed.disableStepCull=true}.
     *
     * <p><b>Threading:</b> called from the Indigo mesher. {@code getYOffset} is already
     * invoked there by {@code OffsetBlockStateModel.emitBlockQuads} for the current block
     * (self lookup safe); the NEIGHBOUR {@code getYOffset} is the one unproven risk (a
     * chunk-border neighbour mid-load could in theory block) — hence the kill switch and
     * the "needs live confirmation" status.
     */
    public static boolean isSlabHeightStepFace(BlockView world, BlockPos pos, BlockState state, Direction direction) {
        if (STEP_CULL_DISABLED || world == null || pos == null || state == null || direction == null
                || !direction.getAxis().isHorizontal() || !state.isOpaqueFullCube(world, pos)) {
            return false;
        }
        BlockPos neighborPos = pos.offset(direction);
        BlockState neighbor = world.getBlockState(neighborPos);
        if (neighbor == null) {
            return false;
        }
        boolean selfLowered = getYOffset(world, pos, state) < 0.0;
        boolean neighborLowered = getYOffset(world, neighborPos, neighbor) < 0.0;
        return selfLowered != neighborLowered;
    }

    /**
     * Returns the Y offset for the block at {@code pos}.
     * <ul>
     *   <li>{@code -0.5} for blocks sitting above a bottom slab (or chain).</li>
     *   <li>{@code +0.5} for hanging blocks (HANGING=true) directly below a top slab.</li>
     *   <li>{@code 0.0} otherwise (no offset).</li>
     * </ul>
     */
    public static double getYOffset(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null) {
            return 0.0;
        }
        if (state == null || state.isAir()) {
            return 0.0;
        }
        if (CompatHooks.shouldSkipOffset(state)) {
            return 0.0;
        }

        // Terrain Slabs already lowers its own vegetation/snow on slabs; Slabbed must not also
        // offset those or they double-lower and clip into the slab (generated grass on a TS slab).
        if (CompatHooks.isNativelyOffsetOnTop(state)) {
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
                    && state.contains(SlabBlock.TYPE)
                    && state.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && isBottomPersistentTracePos(pos)) {
                System.out.println("[BOTTOM_PERSISTENT] getYOffset early_guard_return pos=" + pos.toShortString()
                        + " state=" + state
                        + " slabType=" + state.get(SlabBlock.TYPE)
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
            BlockView world,
            BlockPos sourcePos,
            BlockState sourceState,
            Direction intendedDirection,
            Vec3d hitPos
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
            BlockPos candidatePlacementPos = sourcePos.up();
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

        BlockPos intendedLanePos = sourcePos.offset(intendedDirection);
        int legalLaneCount = 0;
        BlockPos legalLanePos = null;
        BlockState legalLaneState = null;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos lanePos = sourcePos.offset(direction);
            BlockState laneState = world.getBlockState(lanePos);
            if (isLegalCompoundRemapLane(world, lanePos, laneState)) {
                legalLaneCount++;
                legalLanePos = lanePos;
                legalLaneState = laneState;
            }
        }

        if (legalLaneCount == 1 && intendedLanePos.equals(legalLanePos)) {
            BlockPos candidatePlacementPos = legalLanePos.offset(intendedDirection);
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
                    legalLaneState.get(SlabBlock.TYPE),
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

        BlockState belowSourceState = world.getBlockState(sourcePos.down());
        BlockPos candidatePlacementPos = intendedLanePos;
        BlockState candidateState = world.getBlockState(candidatePlacementPos);
        if (legalLaneCount == 0 && isLegalCompoundRemapLane(world, sourcePos.down(), belowSourceState)) {
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
                legalLanePos == null ? intendedLanePos : legalLanePos.offset(intendedDirection),
                "legal_lane_count_" + legalLaneCount + "_or_not_in_intended_direction"));
    }

    private static CompoundSlabRemapDecision traceCompoundSlabRemap(
            BlockView world,
            BlockPos sourcePos,
            BlockState sourceState,
            Direction intendedDirection,
            Vec3d hitPos,
            CompoundSlabRemapDecision decision
    ) {
        Beta4ManualLiveTrace.logSlabSupportDecision(world, sourcePos, sourceState, intendedDirection, hitPos, decision);
        return decision;
    }

    private static SlabType compoundBelowLaneResultType(BlockPos sourcePos, Vec3d hitPos) {
        if (sourcePos != null && hitPos != null && hitPos.y >= sourcePos.getY()) {
            return SlabType.TOP;
        }
        return SlabType.BOTTOM;
    }

    private static boolean isCompoundVisibleSideLowerHit(
            BlockView world,
            BlockPos sourcePos,
            BlockState sourceState,
            Vec3d hitPos
    ) {
        if (world == null || sourcePos == null || sourceState == null || hitPos == null) {
            return false;
        }
        double sourceDy = getYOffset(world, sourcePos, sourceState);
        double localVisibleY = hitPos.y - (sourcePos.getY() + sourceDy);
        return localVisibleY >= -1.0e-6d && localVisibleY < 0.5d - 1.0e-6d;
    }

    private static boolean isCompoundVisibleSideUpperHit(
            BlockView world,
            BlockPos sourcePos,
            BlockState sourceState,
            Vec3d hitPos
    ) {
        if (world == null || sourcePos == null || sourceState == null || hitPos == null) {
            return false;
        }
        double sourceDy = getYOffset(world, sourcePos, sourceState);
        double localVisibleY = hitPos.y - (sourcePos.getY() + sourceDy);
        return localVisibleY >= 0.5d - 1.0e-6d && localVisibleY <= 1.0d + 1.0e-6d;
    }

    private static boolean isMarkedCompoundVisibleSideSlab(BlockView world, BlockPos pos, BlockState state) {
        return SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state);
    }

    private static boolean isPersistentVisibleCompoundOwner(BlockView world, BlockPos pos, BlockState state) {
        return SlabAnchorAttachment.isAnchored(world, pos)
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, pos, state)
                && Math.abs(getYOffset(world, pos, state) + 1.0d) <= 1.0e-6d;
    }

    private static boolean isLegalCompoundRemapLane(BlockView world, BlockPos lanePos, BlockState laneState) {
        return laneState != null
                && laneState.getBlock() instanceof SlabBlock
                && laneState.contains(SlabBlock.TYPE)
                && laneState.get(SlabBlock.TYPE) != SlabType.DOUBLE
                && laneState.getFluidState().isEmpty()
                && Math.abs(getYOffset(world, lanePos, laneState) + 0.5d) <= 1.0e-6d
                && isLoweredSideLaneSlabCarrier(world, lanePos, laneState);
    }

    private static boolean isCompatibleLoweredSlabLane(BlockState a, BlockState b) {
        if (!a.contains(SlabBlock.TYPE) || !b.contains(SlabBlock.TYPE)) {
            return false;
        }
        SlabType aType = a.get(SlabBlock.TYPE);
        SlabType bType = b.get(SlabBlock.TYPE);
        return isCompatibleLoweredSlabLane(aType, bType);
    }

    private static boolean hasLoweredSolidSideSupport(BlockView world, BlockPos slabPos) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = slabPos.offset(dir);
            BlockState neighbor = world.getBlockState(neighborPos);
            if (isFullHeightLoweredCarrierForSideSupport(world, neighborPos, neighbor)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAnchoredLoweredFullBlock(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidBlock(world, pos)) {
            return false;
        }
        return SlabAnchorAttachment.isAnchored(world, pos);
    }

    public static boolean isLoweredDoubleSlabCarrier(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || state.get(SlabBlock.TYPE) != SlabType.DOUBLE
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
            return true;
        }
        return isLoweredCarrier(world, pos, state, MAX_CHAIN_DEPTH);
    }

    public static boolean isFullHeightLoweredCarrier(BlockView world, BlockPos pos, BlockState state) {
        return isLoweredFullBlockCarrier(world, pos, state)
                || isLoweredDoubleSlabCarrier(world, pos, state);
    }

    public static boolean isLoweredSideLaneDoubleCarrier(BlockView world, BlockPos pos, BlockState state) {
        return isLoweredSideLaneSlabCarrier(world, pos, state)
                && state.get(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    public static boolean isLoweredSideLaneSlabCarrier(BlockView world, BlockPos pos, BlockState state) {
        return world != null
                && pos != null
                && state != null
                && state.getBlock() instanceof SlabBlock
                && state.contains(SlabBlock.TYPE)
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
    public static boolean isLoweredCompoundSourceSlab(BlockView world, BlockPos pos, BlockState state) {
        return state != null
                && isBottomSlab(state)
                && isAdjacentSideSlabLowered(world, pos, state);
    }

    public static boolean isBottomSlabLoweredByCarrierBelow(BlockView world, BlockPos pos, BlockState state) {
        if (world == null
                || pos == null
                || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || state.get(SlabBlock.TYPE) != SlabType.BOTTOM
                || !state.getFluidState().isEmpty()) {
            return false;
        }

        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        boolean backedByLoweredCarrier = below.getBlock() instanceof SlabBlock
                ? isLoweredDoubleSlabCarrier(world, belowPos, below)
                : hasLoweredCarrierBelow(world, pos);
        return backedByLoweredCarrier && getYOffset(world, pos, state) == -0.5d;
    }

    private static boolean isLoweredCarrier(BlockView world, BlockPos pos, BlockState state, int depth) {
        return isLoweredCarrier(world, pos, state, depth, true);
    }

    private static boolean isLoweredCarrier(
            BlockView world,
            BlockPos pos,
            BlockState state,
            int depth,
            boolean allowSideLane
    ) {
        if (world == null || pos == null || state == null || depth <= 0) {
            return false;
        }
        if (state.getBlock() instanceof SlabBlock) {
            if (!state.contains(SlabBlock.TYPE)
                    || state.get(SlabBlock.TYPE) != SlabType.DOUBLE
                    || !state.getFluidState().isEmpty()) {
                return false;
            }
            if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
                return true;
            }
            if (allowSideLane && isAdjacentSideSlabLowered(world, pos, state)) {
                return true;
            }
            BlockPos belowPos = pos.down();
            return isLoweredCarrier(world, belowPos, world.getBlockState(belowPos), depth - 1, allowSideLane);
        }
        return isLoweredFullBlockCarrier(world, pos, state);
    }

    private static boolean isLoweredDoubleSlabCarrierForSideSupport(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.contains(SlabBlock.TYPE)
                || state.get(SlabBlock.TYPE) != SlabType.DOUBLE
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
            return true;
        }
        return isLoweredCarrier(world, pos.down(), world.getBlockState(pos.down()), MAX_CHAIN_DEPTH, false);
    }

    private static boolean isFullHeightLoweredCarrierForSideSupport(BlockView world, BlockPos pos, BlockState state) {
        return isLoweredFullBlockCarrier(world, pos, state)
                || isLoweredDoubleSlabCarrierForSideSupport(world, pos, state)
                || isLoweredConnectingBlockCarrier(world, pos, state);
    }

    /**
     * A lowered fence/wall/pane is also a side-support carrier: a slab placed beside it should
     * inherit the -0.5 (sit flush) instead of floating. Recursion-safe (no getYOffset re-entry).
     */
    private static boolean isLoweredConnectingBlockCarrier(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || !(state.getBlock() instanceof FenceBlock
                        || state.getBlock() instanceof WallBlock
                        || state.getBlock() instanceof PaneBlock)) {
            return false;
        }
        return hasBottomSlabBelow(world, pos)
                || SlabAnchorAttachment.isAnchored(world, pos)
                || slabColumnYOffset(world, pos) < -1.0e-6
                || isDirectCustomSlabSupportedObject(world, pos, state);
    }

    private static boolean isLoweredFullBlockCarrier(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidBlock(world, pos)) {
            return false;
        }
        boolean hasBottomBelow = hasBottomSlabBelow(world, pos);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        // A full block lowered via its column (the top of a stack standing on a bottom slab
        // or a Terrain Slabs surface) is also a lowered carrier, so an adjacent side slab
        // inherits the lowering (fixes the +0.5 "DODO" gap when placing a perpendicular slab
        // beside such a stack). slabColumnYOffset walks down through solid blocks to the slab
        // (covers solid full cubes that are not directCustom subjects) and isDirectCustom...
        // covers non-solid sit objects. Both are recursion-safe (never re-enter getYOffset).
        boolean columnLowered = slabColumnYOffset(world, pos) < -1.0e-6
                || isDirectCustomSlabSupportedObject(world, pos, state);
        return hasBottomBelow || anchored || columnLowered;
    }

    /**
     * Returns true when the non-slab solid block at {@code pos} carries compound dy=-1.0 —
     * i.e. the same conditions that cause {@link #getYOffsetInner} to return -1.0 for it.
     * Safe to call inside the IN_GET_Y_OFFSET recursion guard: does not delegate to getYOffset.
     * Used exclusively by the floor-torch full-block support branch.
     */
    private static boolean isOrdinaryFullBlockWithCompoundDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock
                || !state.getFluidState().isEmpty()
                || !state.isSolidBlock(world, pos)) {
            return false;
        }
        if (!SlabAnchorAttachment.isAnchored(world, pos)) {
            return false;
        }
        if (SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)) {
            return true;
        }
        BlockState below = world.getBlockState(pos.down());
        return isBottomSlab(below) && isAdjacentSideSlabLowered(world, pos.down(), below);
    }

    private static boolean hasLoweredCarrierBelow(BlockView world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockPos belowPos = pos.down();
        return isLoweredCarrier(world, belowPos, world.getBlockState(belowPos), MAX_CHAIN_DEPTH);
    }

    private static boolean isCompoundVisibleOwnerTopSlab(BlockView world, BlockPos pos, BlockState state) {
        return SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state);
    }

    private static boolean hasLoweredSlabLaneSupport(BlockView world, BlockPos slabPos, BlockState slabState) {
        if (!(slabState.getBlock() instanceof SlabBlock) || !slabState.contains(SlabBlock.TYPE)) {
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
            if (hasLoweredSolidSideSupport(world, cursor)) {
                return true;
            }

            BlockState cursorState = world.getBlockState(cursor);
            if (!(cursorState.getBlock() instanceof SlabBlock) || !cursorState.contains(SlabBlock.TYPE)) {
                continue;
            }
            if (!cursor.equals(slabPos)
                    && isCompatibleLoweredSlabLane(slabState, cursorState)
                    && isLoweredSlabLaneOwnerForSideInheritance(world, cursor, cursorState)) {
                return true;
            }

            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos neighborPos = cursor.offset(dir);
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

    private static boolean isLoweredSlabLaneOwnerForSideInheritance(
            BlockView world,
            BlockPos pos,
            BlockState state
    ) {
        return world != null
                && pos != null
                && state != null
                && state.getBlock() instanceof SlabBlock
                && state.contains(SlabBlock.TYPE)
                && state.getFluidState().isEmpty()
                && !isCompoundVisibleOwnerTopSlab(world, pos, state)
                && (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
                        || hasLoweredSolidSideSupport(world, pos)
                        || hasLoweredCarrierBelow(world, pos));
    }

    private static boolean isAdjacentSideSlabLowered(BlockView world, BlockPos slabPos, BlockState slabState) {
        if (!slabState.contains(SlabBlock.TYPE)) {
            return false;
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, slabPos, slabState)) {
            return true;
        }
        return hasLoweredSlabLaneSupport(world, slabPos, slabState);
    }

    // ── Terrain Slabs combining / mixed-slab compound (directCustom lane) ─────────────
    // Ported from the 1.21.11 reference. Lets a vanilla slab (or object) sitting on a
    // Terrain Slabs surface lower onto it (-0.5, "combine into a flush double"), and an
    // object on a MIXED stack (vanilla bottom slab that is itself lowered) compound to -1.0.
    // Vanilla-bottom-slab-only as the compound source — terrain-on-terrain stays deferred.
    private static BlockState getBlockStateOrNull(BlockView world, BlockPos pos) {
        return (world == null || pos == null) ? null : world.getBlockState(pos);
    }

    private static boolean isVanillaDirectCustomSlabSubject(BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock) || !state.contains(SlabBlock.TYPE)) {
            return false;
        }
        var id = Registries.BLOCK.getId(state.getBlock());
        return id != null && "minecraft".equals(id.getNamespace());
    }

    private static boolean isDirectCustomSlabSupportSubject(BlockView world, BlockPos pos, BlockState state) {
        if (state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock && !isVanillaDirectCustomSlabSubject(state)
                || isThinTopLayer(state)
                || !state.getFluidState().isEmpty()
                || CompatHooks.shouldSkipOffset(state)) {
            return false;
        }
        return isVanillaDirectCustomSlabSubject(state) || isSlabSitCandidate(world, pos, state);
    }

    private static boolean hasDirectCustomBottomLikeSupportColumn(BlockView world, BlockPos supportPos) {
        BlockPos cursor = supportPos;
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState supportState = getBlockStateOrNull(world, cursor);
            if (supportState == null) {
                return false;
            }
            if (CompatHooks.customSlabSurfaceKind(supportState) == CompatSlabSurfaceKind.BOTTOM_LIKE) {
                return true;
            }
            if (isDirectCustomSlabSupportSubject(world, cursor, supportState)) {
                cursor = cursor.down();
                continue;
            }
            return false;
        }
        return false;
    }

    /**
     * True when {@code state} at {@code pos} ultimately rests on a Terrain Slabs BOTTOM_LIKE
     * surface (directly or through a column of stacked subjects). Handles beds (two columns)
     * and double-blocks (upper half checks two down).
     */
    public static boolean isDirectCustomSlabSupportedObject(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isDirectCustomSlabSupportSubject(world, pos, state)) {
            return false;
        }
        if (state.contains(Properties.BED_PART) && state.contains(Properties.HORIZONTAL_FACING)) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            BedPart part = state.get(Properties.BED_PART);
            BlockPos otherPos = part == BedPart.FOOT ? pos.offset(facing) : pos.offset(facing.getOpposite());
            return hasDirectCustomBottomLikeSupportColumn(world, pos.down())
                    || hasDirectCustomBottomLikeSupportColumn(world, otherPos.down());
        }
        BlockPos supportPos = pos.down();
        if (state.contains(Properties.DOUBLE_BLOCK_HALF)
                && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            BlockState lowerState = world.getBlockState(pos.down());
            if (lowerState.getBlock() != state.getBlock()
                    || !lowerState.contains(Properties.DOUBLE_BLOCK_HALF)
                    || lowerState.get(Properties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) {
                return false;
            }
            supportPos = pos.down(2);
        }
        return hasDirectCustomBottomLikeSupportColumn(world, supportPos);
    }

    private static double directCustomSlabSupportDy(BlockView world, BlockPos pos, BlockState state) {
        return isDirectCustomSlabSupportedObject(world, pos, state) ? -0.5 : Double.NaN;
    }

    /**
     * Visual dy of a connecting block (fence/wall/pane), mirroring EXACTLY what
     * {@link com.slabbed.client.model.OffsetBlockStateModel#emitBlockQuads} draws so the
     * stepped-connection check stays in lock-step with the render path:
     * <ul>
     *   <li>Fence/wall ({@link #isBeta35FenceWallVariantContactObject}): the RAW
     *       {@link #getYOffset}. A wall lowered onto a vanilla bottom slab and a wall lowered
     *       onto a Terrain Slabs surface BOTH render at the same offset, so both report it here.</li>
     *   <li>Pane ({@link PaneBlock}): FORCED 0.0. The render path force-zeroes panes (they are
     *       not a Beta 3.5 fence/wall variant), so a pane is never visually stepped against
     *       another pane regardless of its slab support.</li>
     *   <li>Anything else: the raw {@link #getYOffset} (unchanged legacy behavior).</li>
     * </ul>
     * Previously this gated on {@link #isDirectCustomSlabSupportedObject} (Terrain-Slabs-only),
     * which desynced from the render path: a fence/wall on a VANILLA slab and a pane on a TS slab
     * were mis-classified, falsely breaking connections between posts that render at one height.
     */
    public static double connectingBlockVisualDy(BlockView world, BlockPos pos, BlockState state) {
        double dy = getYOffset(world, pos, state);
        if (dy == 0.0) {
            return 0.0;
        }
        Block block = state.getBlock();
        // OffsetBlockStateModel force-zeroes the render dy for any fence/wall/pane that is NOT a
        // proven Beta 3.5 fence/wall variant (this excludes ALL panes). Mirror that exactly so the
        // step check sees the same height the block is actually drawn at.
        if ((block instanceof FenceBlock || block instanceof WallBlock || block instanceof PaneBlock)
                && !isBeta35FenceWallVariantContactObject(state)) {
            return 0.0;
        }
        return dy;
    }

    /**
     * True if {@code neighborState} is a fence/wall/pane sitting at a different visual height
     * than {@code state} — i.e. one was lowered onto a slab and the other was not. Such a pair
     * must stay as single posts instead of drawing a connector arm across the height step
     * (the "split" / illegal fence connection). Cross-family joins (fence↔wall, pane↔glass,
     * fence↔solid, …) are left alone because the neighbour is not a connecting block here.
     */
    public static boolean isSteppedConnectingNeighbor(BlockView world, BlockPos pos, BlockState state,
                                                      BlockPos neighborPos, BlockState neighborState) {
        Block neighbor = neighborState.getBlock();
        if (!(neighbor instanceof FenceBlock || neighbor instanceof WallBlock || neighbor instanceof PaneBlock)) {
            return false;
        }
        double selfDy = connectingBlockVisualDy(world, pos, state);
        double neighborDy = connectingBlockVisualDy(world, neighborPos, neighborState);
        return Math.abs(selfDy - neighborDy) > 1.0e-6;
    }

    /**
     * Recursion-safe rendered dy of a VANILLA bottom-or-top-slab support directly beneath an
     * object, used to compound a mixed-slab lowering. For a lowered BOTTOM slab: -0.5 (anchored /
     * directCustom-on-TS / adjacent-side-lowered), 0.0 if flush. For a lowered TOP slab: an extra
     * -0.5 is added on top of that base, because the top slab presents its top surface a full cell
     * above its lowered base, so an object resting on it follows the top slab down (BUG 1 fix).
     * DOUBLE slabs and non-slabs return NaN. Never re-enters getYOffset.
     */
    private static double loweredBottomSlabSupportDy(BlockView world, BlockPos supportPos) {
        BlockState s = getBlockStateOrNull(world, supportPos);
        if (s == null
                || !(s.getBlock() instanceof SlabBlock)
                || !s.contains(SlabBlock.TYPE)
                || s.get(SlabBlock.TYPE) == SlabType.DOUBLE
                || !s.getFluidState().isEmpty()) {
            return Double.NaN;
        }
        double base;
        if (SlabAnchorAttachment.isAnchored(world, supportPos)) {
            base = -0.5;
        } else {
            double directCustomDy = directCustomSlabSupportDy(world, supportPos, s);
            if (Double.isFinite(directCustomDy) && directCustomDy < -1.0e-6) {
                base = directCustomDy;
            } else if (isAdjacentSideSlabLowered(world, supportPos, s)) {
                base = -0.5;
            } else {
                base = 0.0;
            }
        }
        // BUG 1 fix: a lowered TOP slab support presents its top surface a full cell above its
        // lowered base, so an object resting on it drops an extra -0.5 (mirrors the
        // object-on-TOP-slab term in the compound path). Bottom slabs are unaffected; the -1.0
        // clamp in the caller keeps the total bounded.
        if (base < -1.0e-6 && s.get(SlabBlock.TYPE) == SlabType.TOP) {
            base += -0.5;
        }
        return base;
    }

    private static double getYOffsetInner(BlockView world, BlockPos pos, BlockState state) {
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
            if (state.contains(SlabBlock.TYPE)
                    && state.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && isBottomPersistentTracePos(pos)) {
                boolean persistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state);
                boolean nonRecursiveBottomCarrier =
                        SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state);
                boolean branchReached = state.getFluidState().isEmpty() && nonRecursiveBottomCarrier;
                System.out.println("[BOTTOM_PERSISTENT] getYOffsetInner pos=" + pos.toShortString()
                        + " state=" + state
                        + " slabType=" + state.get(SlabBlock.TYPE)
                        + " fluidEmpty=" + state.getFluidState().isEmpty()
                        + " worldClass=" + world.getClass().getName()
                        + " guard=" + IN_GET_Y_OFFSET.get()
                        + " persistentLoweredSlabCarrier=" + persistentCarrier
                        + " nonRecursiveBottomCarrier=" + nonRecursiveBottomCarrier
                        + " branchReached=" + branchReached);
            }
            if (state.contains(SlabBlock.TYPE)
                    && state.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && state.getFluidState().isEmpty()
                    && SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state)) {
                if (isBottomPersistentTracePos(pos)) {
                    System.out.println("[BOTTOM_PERSISTENT] branch=return_-0.5 pos=" + pos.toShortString());
                }
                return -0.5;
            }
            BlockPos belowPos = pos.down();
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
                    String side = (world instanceof net.minecraft.world.World w && w.isClient()) ? "CLIENT" : "SERVER";
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
            BlockPos belowPos = pos.down();
            BlockState belowSlab = world.getBlockState(belowPos);
            if (isBottomSlab(belowSlab) && isAdjacentSideSlabLowered(world, belowPos, belowSlab)) {
                if (com.slabbed.anchor.SlabAnchorAttachment.TRACE) {
                    String side = (world instanceof net.minecraft.world.World w && w.isClient()) ? "CLIENT" : "SERVER";
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
                String side = (world instanceof net.minecraft.world.World w && w.isClient()) ? "CLIENT" : "SERVER";
                Slabbed.LOGGER.info("[ANCHOR] dy applied side={} pos={} state={} dy=-0.5",
                        side, pos.toShortString(), state);
            }
            return -0.5;
        }

        if (isFloorTorch(state)) {
            BlockPos supportPos = pos.down();
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
            BlockPos supportPos = pos.down();
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

        // Terrain Slabs combining / mixed-slab compound: an object or vanilla slab resting on a
        // Terrain Slabs surface lowers -0.5 onto it; if the support directly below is itself a
        // lowered vanilla bottom slab (a MIXED slab) the drop compounds to -1.0; a vanilla TOP
        // slab adds another -0.5. Gated on directCustomSlabSupportDy != NaN, so any column that
        // is not Terrain-Slabs-backed takes the identical pre-existing path below.
        double directCustomSurfaceDy = directCustomSlabSupportDy(world, pos, state);
        if (!Double.isNaN(directCustomSurfaceDy)) {
            double dy = directCustomSurfaceDy;
            double supportLoweredDy = loweredBottomSlabSupportDy(world, pos.down());
            if (Double.isFinite(supportLoweredDy) && supportLoweredDy < -1.0e-6) {
                dy += supportLoweredDy;
            }
            if (state.getBlock() instanceof SlabBlock
                    && state.contains(SlabBlock.TYPE)
                    && state.get(SlabBlock.TYPE) == SlabType.TOP) {
                dy += -0.5;
            }
            if (dy < -1.0) {
                dy = -1.0;
            }
            return dy;
        }

        if (shouldOffset(world, pos, state)) {
            // Compound case: non-slab block above a bottom slab that is itself an adjacent-side
            // slab lowered by -0.5.  The block must drop an additional -0.5 to align with the
            // slab's visual top surface, for a total of -1.0.
            BlockState belowSlab = world.getBlockState(pos.down());
            if (isBottomSlab(belowSlab) && isAdjacentSideSlabLowered(world, pos.down(), belowSlab)) {
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
        if (state.getBlock() instanceof TrapdoorBlock
                && state.contains(Properties.BLOCK_HALF)
                && state.get(Properties.BLOCK_HALF) == BlockHalf.TOP) {
            BlockPos supportPos = pos.up();
            BlockState supportState = world.getBlockState(supportPos);
            if (supportState.getBlock() instanceof SlabBlock && supportState.contains(SlabBlock.TYPE)) {
                SlabType supportType = supportState.get(SlabBlock.TYPE);
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
            BlockPos supportPos = pos.up();
            BlockState supportState = world.getBlockState(supportPos);
            double supportDy = loweredSlabUndersideSupportDy(world, supportPos, supportState);
            if (Double.isFinite(supportDy) && supportDy < -1.0e-6d) {
                return supportState.get(SlabBlock.TYPE) == SlabType.TOP ? supportDy + 0.5d : supportDy;
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
            BlockPos supportPos = pos.up();
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
                || blk instanceof StairsBlock
                || blk instanceof FenceBlock
                || blk instanceof WallBlock
                || blk instanceof PaneBlock
                || isThinTopLayer(state)
                || state.isAir()
                || !state.getFluidState().isEmpty()
                || state.isSolidBlock(world, pos)) {
            return 0.0;
        }

        BlockState above = world.getBlockState(pos.up());

        // direct: ceiling-attached blocks directly under a top slab
        if (isCeilingAttached(state) && isTopSlab(above)) {
            return 0.5;
        }

        // cascading: ceiling-attached block below other ceiling-attached blocks
        // leading up to a top slab (e.g. 2nd dripstone, 2nd vine segment)
        if (isCeilingAttached(state)) {
            BlockPos cursor = pos.up();
            for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
                BlockState cur = world.getBlockState(cursor);
                if (isTopSlab(cur)) {
                    return 0.5;
                }
                if (isCeilingAttached(cur)) {
                    cursor = cursor.up();
                    continue;
                }
                break;
            }
        }

        return 0.0;
    }

    private static double floorTorchBottomSlabSupportDy(BlockView world, BlockPos pos, BlockState state) {
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
        BlockPos belowPos = pos.down();
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
        return pos != null && pos.getX() == 0 && pos.getY() == 202 && pos.getZ() == 0;
    }

    /**
     * Shared ownership rule for client raycast/outline retargeting of lowered
     * block-entity-style blocks (e.g. chests) sitting above a bottom slab.
     *
     * <p>When a block-entity block is visually lowered by -0.5 (its model, via
     * {@code BlockEntityOffsetMixin}, and its outline/raycast shapes, via
     * {@code SlabSupportStateMixin}), the lower half of its visible footprint
     * overflows into {@code pos.down()}'s voxel. Vanilla DDA raycast traversal
     * cannot see that overflowed portion at {@code pos} and instead hits the
     * slab below. This helper is the single source of truth for detecting
     * that case so raycast retarget and outline agree.
     *
     * @return true iff {@code state} is a {@link BlockEntityProvider} block
     *         at {@code pos} whose {@link #getYOffset} is exactly {@code -0.5}.
     */
    public static boolean isLoweredBlockEntityVisual(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (!(state.getBlock() instanceof BlockEntityProvider)) {
            return false;
        }
        return getYOffset(world, pos, state) == -0.5;
    }

    public static boolean isLoweredTorchVisual(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        Block block = state.getBlock();
        if (!(block instanceof net.minecraft.block.TorchBlock
                || block instanceof net.minecraft.block.WallTorchBlock)) {
            return false;
        }
        // compound dy (-1.0) also qualifies: torch above an adjacent-lowered bottom slab
        return getYOffset(world, pos, state) < 0.0;
    }

    public static boolean isCompoundVisibleSlabLaneOwner(BlockView world, BlockPos pos, BlockState state) {
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
            BlockView world, Entity entity, Vec3d eye, Vec3d end
    ) {
        if (world == null || eye == null || end == null) {
            return null;
        }
        Vec3d ray = end.subtract(eye);
        double reach = ray.length();
        if (reach <= 0.0d) {
            return null;
        }
        Vec3d dir = ray.normalize();
        int steps = Math.max(16, (int) Math.ceil(reach / 0.05d));

        BlockHitResult best = null;
        double bestDist2 = Double.POSITIVE_INFINITY;
        double maxDist2 = reach * reach + 1.0e-6d;
        for (int i = 1; i <= steps; i++) {
            double t = reach * i / steps;
            Vec3d sample = eye.add(dir.multiply(t));
            BlockPos samplePos = BlockPos.ofFloored(sample);

            BlockHitResult candidate = raycastCompoundVisibleSlabLaneOwner(world, entity, eye, end, samplePos);
            if (candidate != null && candidate.getPos().squaredDistanceTo(eye) <= maxDist2
                    && candidate.getPos().squaredDistanceTo(eye) < bestDist2 - 1.0e-6d) {
                best = candidate;
                bestDist2 = candidate.getPos().squaredDistanceTo(eye);
            }

            candidate = raycastCompoundVisibleSlabLaneOwner(world, entity, eye, end, samplePos.up());
            if (candidate != null && candidate.getPos().squaredDistanceTo(eye) <= maxDist2
                    && candidate.getPos().squaredDistanceTo(eye) < bestDist2 - 1.0e-6d) {
                best = candidate;
                bestDist2 = candidate.getPos().squaredDistanceTo(eye);
            }
        }
        return best;
    }

    private static BlockHitResult raycastCompoundVisibleSlabLaneOwner(
            BlockView world, Entity entity, Vec3d eye, Vec3d end, BlockPos pos
    ) {
        BlockState state = world.getBlockState(pos);
        if (!isCompoundVisibleSlabLaneOwner(world, pos, state)) {
            return null;
        }
        ShapeContext context = entity == null ? ShapeContext.absent() : ShapeContext.of(entity);
        VoxelShape outline = state.getOutlineShape(world, pos, context);
        if (outline == null || outline.isEmpty()) {
            return null;
        }
        return outline.raycast(eye, end, pos);
    }

    public static boolean isLoweredBedVisual(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        return state.getBlock() instanceof net.minecraft.block.BedBlock
                && state.contains(Properties.BED_PART)
                && getYOffset(world, pos, state) == -0.5;
    }

    /**
     * Redstone dust support surface — treat slab tops like valid ground for downward stepping.
     */
    public static boolean isRedstoneSupportTopSurface(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        if (state.isSideSolidFullSquare(world, pos, Direction.UP)) {
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
     *   <li>Every {@link BlockEntityProvider} block — chests, hoppers,
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
    private static boolean isSlabSitCandidate(BlockView world, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockEntityProvider) {
            return true;
        }
        if (block instanceof CraftingTableBlock) {
            return true;
        }
        return !state.isSolidBlock(world, pos);
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
    private static boolean hasSlabInColumn(BlockView world, BlockPos pos) {
        BlockPos cursor = pos.down();
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = world.getBlockState(cursor);
            if (isBottomSlab(cur)) {
                return true;
            }
            // Terrain Slabs compat (opt-in; inert without the mod): a custom BOTTOM_LIKE
            // surface in the column provides support. Checked before the SlabBlock
            // early-exit below because TS slabs are SlabBlock instances.
            if (CompatHooks.customSlabSurfaceKind(cur) == CompatSlabSurfaceKind.BOTTOM_LIKE) {
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
            cursor = cursor.down();
        }
        return false;
    }

    private static double slabColumnYOffset(BlockView world, BlockPos pos) {
        BlockPos cursor = pos.down();
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = world.getBlockState(cursor);
            if (cur.getBlock() instanceof SlabBlock
                    && isAdjacentSideSlabLowered(world, cursor, cur)) {
                return isBottomSlab(cur) ? -1.0 : -0.5;
            }
            // Terrain Slabs compat (opt-in; inert without the mod): a custom BOTTOM_LIKE
            // surface lowers the column object -0.5, like a vanilla bottom slab. Checked
            // before the SlabBlock early-exit below (TS slabs are SlabBlock instances).
            if (CompatHooks.customSlabSurfaceKind(cur) == CompatSlabSurfaceKind.BOTTOM_LIKE) {
                return -0.5;
            }
            if (isBottomSlab(cur) || SlabAnchorAttachment.isAnchored(world, cursor)) {
                return -0.5;
            }
            if (cur.isAir() || cur.getBlock() instanceof SlabBlock || isThinTopLayer(cur)) {
                return 0.0;
            }
            cursor = cursor.down();
        }
        return 0.0;
    }
}
