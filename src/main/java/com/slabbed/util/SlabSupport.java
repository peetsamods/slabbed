package com.slabbed.util;

import com.slabbed.compat.CompatHooks;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.CaveVinesBodyBlock;
import net.minecraft.block.CaveVinesHeadBlock;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.HangingRootsBlock;
import net.minecraft.block.HangingSignBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.PaleMossCarpetBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.PointedDripstoneBlock;
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
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;

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
                || block instanceof PaleMossCarpetBlock;
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
        return isBottomSlab(world.getBlockState(pos.down()));
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
     * Primary query: should this slab top face count as solid support.
     */
    public static boolean canTreatAsSolidTopFace(WorldView world, BlockPos pos) {
        return isSupportingSlab(world, pos);
    }

    /** Overload for shape/world views. */
    public static boolean canTreatAsSolidTopFace(BlockView world, BlockPos pos) {
        return isSupportingSlab(world, pos);
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

        // never offset thin top-layer blocks (snow layers, carpet)
        if (isThinTopLayer(state)) {
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
     * Returns true if {@code slabPos} holds a bottom or double slab that is adjacent
     * to a solid full block sitting on a bottom slab — the exact condition that gives
     * the slab its -0.5 adjacent-side-slab dy.  Does not call getYOffset (safe inside
     * the IN_GET_Y_OFFSET recursion guard).
     */
    private static boolean isAdjacentSideSlabLowered(BlockView world, BlockPos slabPos, BlockState slabState) {
        SlabType type = slabState.get(SlabBlock.TYPE);
        if (type != SlabType.BOTTOM && type != SlabType.DOUBLE) {
            return false;
        }
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = slabPos.offset(dir);
            BlockState neighbor = world.getBlockState(neighborPos);
            if (neighbor.getBlock() instanceof SlabBlock) continue;
            if (!neighbor.isSolidBlock(world, neighborPos)) continue;
            if (hasBottomSlabBelow(world, neighborPos)) {
                return true;
            }
        }
        return false;
    }

    private static double getYOffsetInner(BlockView world, BlockPos pos, BlockState state) {
        // Slab-on-offset-block: a slab placed on top of a solid block that sits on a bottom slab
        // inherits the same -0.5 dy so the stack stays visually continuous (no gap).
        if (state.getBlock() instanceof SlabBlock) {
            BlockPos belowPos = pos.down();
            BlockState below = world.getBlockState(belowPos);
            Block belowBlock = below.getBlock();
            if (!(belowBlock instanceof SlabBlock) && !below.isAir()
                    && below.getFluidState().isEmpty()
                    && hasBottomSlabBelow(world, belowPos)) {
                return -0.5;
            }
            // Adjacent-side-slab alignment: a bottom or double slab placed at the side of a
            // lowered full block must visually inherit the lowered -0.5 dy so model/outline/
            // raycast align with the neighbor. Use hasBottomSlabBelow directly: calling
            // getYOffset here would be short-circuited to 0.0 by the IN_GET_Y_OFFSET recursion
            // guard since this code runs inside getYOffsetInner.
            if (isAdjacentSideSlabLowered(world, pos, state)) {
                return -0.5;
            }
        }

        if (shouldOffset(world, pos, state)) {
            // Compound case: non-slab block above a bottom slab that is itself an adjacent-side
            // slab lowered by -0.5.  The block must drop an additional -0.5 to align with the
            // slab's visual top surface, for a total of -1.0.
            BlockState belowSlab = world.getBlockState(pos.down());
            if (isBottomSlab(belowSlab) && isAdjacentSideSlabLowered(world, pos.down(), belowSlab)) {
                return -1.0;
            }
            return -0.5;
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
     */
    private static boolean hasSlabInColumn(BlockView world, BlockPos pos) {
        BlockPos cursor = pos.down();
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = world.getBlockState(cursor);
            if (isBottomSlab(cur)) {
                return true;
            }
            if (cur.isAir() || cur.getBlock() instanceof SlabBlock || isThinTopLayer(cur)) {
                return false;
            }
            cursor = cursor.down();
        }
        return false;
    }
}
