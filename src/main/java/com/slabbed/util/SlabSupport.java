package com.slabbed.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.WallHangingSignBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
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

    /** True if the block immediately below {@code pos} is a bottom slab providing its top face. */
    public static boolean hasBottomSlabBelow(BlockView world, BlockPos pos) {
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
        // Conservative: solid ground blocks sitting on a bottom slab render at slab height.
        if (hasBottomSlabBelow(world, pos)) {
            Block block = state.getBlock();

            if (block instanceof SlabBlock
                    || block instanceof StairsBlock
                    || block instanceof FenceBlock
                    || block instanceof WallBlock
                    || block instanceof TrapdoorBlock
                    || block instanceof PaneBlock
                    || state.isAir()
                    || !state.getFluidState().isEmpty()) {
                // excluded partials / non-blocks / fluids — no slab anchoring expansion
            } else if (state.isSolidBlock(world, pos)) {
                return -0.5;
            }
        }

        if (shouldOffset(world, pos, state)) {
            return -0.5;
        }
        // hanging blocks under a top slab get pushed UP to sit against the slab bottom
        if (state.contains(Properties.HANGING) && state.get(Properties.HANGING)) {
            BlockState above = world.getBlockState(pos.up());
            if (isTopSlab(above)) {
                return 0.5;
            }
        }
        return 0.0;
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
            if (cur.isAir() || cur.getBlock() instanceof SlabBlock) {
                return false;
            }
            cursor = cursor.down();
        }
        return false;
    }
}
