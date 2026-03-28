package com.slabbed.util;

import com.slabbed.Slabbed;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.TextGizmo;

import java.util.List;
import java.util.function.Predicate;

/// Central helper for slab support semantics.
public final class SlabSupport
{
    private SlabSupport()
    {
    }

    private static final boolean DEBUG_hasBottomSlabInColumnBelow = false;
    private static final boolean DEBUG_hasBottomSlabInColumnBelow_FAILED_ALL_BLOCKS = false;
    private static final boolean DEBUG_ALLOWED_SLAB_OFFSET = false;
    private static final boolean DEBUG_NORMAL_BLOCK_OFFSET_DOWN = false;
    private static final boolean DEBUG_CEILING_ATTATCHED = false;

    /// Lifespan for debug
    private static final int LP = 1000;

    // TODO: replace with a tag for most cases
    private static final List<Predicate<BlockState>> CEILING_PREDICATES = List.of(
        state -> state.getBlock() instanceof HangingSignBlock,
        state -> state.getBlock() instanceof BellBlock,
        state -> state.getBlock() instanceof LeverBlock,
        state -> state.getBlock() instanceof ButtonBlock,
        state -> state.getBlock() instanceof SporeBlossomBlock,
        state -> state.getBlock() instanceof HangingRootsBlock,
        state -> state.getBlock() instanceof PointedDripstoneBlock,
        state -> state.getBlock() instanceof CaveVinesHeadBlock,
        state -> state.getBlock() instanceof CaveVinesBodyBlock,
        state -> state.getBlock() instanceof TrapdoorBlock && state.contains(Properties.BLOCK_HALF) && state.get(Properties.BLOCK_HALF) == BlockHalf.TOP,
        state -> state.getBlock() instanceof ChainBlock && state.contains(Properties.AXIS) && state.get(Properties.AXIS) == Direction.Axis.Y,
        state -> state.contains(Properties.HANGING) && state.get(Properties.HANGING)
    );

    /// Max blocks to walk down when checking chain offset.
    private static final int MAX_CHAIN_DEPTH = 4;

    /// Recursion guard: prevents StackOverflow when isSolidBlock triggers getOutlineShape → getYOffset.
    private static final ThreadLocal<Boolean> IN_GET_Y_OFFSET = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /// Returns true if the block state represents a ceiling-attached block -
    /// one that hangs from the block above it by nature.
    public static boolean isCeilingAttached(BlockState state)
    {
        for (Predicate<BlockState> predicate : CEILING_PREDICATES)
        {
            if (predicate.test(state))
                return true;
        }
        return false;
    }

    public static boolean isTopStopper(BlockState state)
    {
        Block block = state.getBlock();
        return block instanceof SnowBlock || block instanceof CarpetBlock || block instanceof PaleMossCarpetBlock;
    }

    /// Returns true if the state is a slab with a defined type.
    public static boolean isSupportingSlab(BlockState state)
    {
        return state.getBlock() instanceof SlabBlock && state.contains(SlabBlock.TYPE) && state.contains(Slabbed.IS_SLABBED) && state.get(Slabbed.IS_SLABBED);
    }

    public static boolean isBottomSlab(BlockState state)
    {
        return isSupportingSlab(state) && state.get(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    public static boolean isTopSlab(BlockState state)
    {
        return isSupportingSlab(state) && state.get(SlabBlock.TYPE) == SlabType.TOP;
    }

    /// Single source of truth: returns true if the state is a TOP slab
    /// and the queried face is DOWN (i.e. the underside of a top slab).
    public static boolean isTopSlabUndersideSupport(BlockState state, Direction face)
    {
        return face == Direction.DOWN && isTopSlab(state);
    }

    /// True if the block immediately below {@code pos} is a bottom slab providing its top face.
    public static boolean hasBottomSlabBelow(BlockView world, BlockPos pos)
    {
        if (world == null || pos == null)
        {
            return false;
        }
        return isBottomSlab(world.getBlockState(pos.down()));
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
    public static boolean shouldOffsetDown(BlockView world, BlockPos pos, BlockState state)
    {
        // never offset slabs themselves
        // Forbid placing chains on bottom slabs
        if (state.getBlock() instanceof SlabBlock || state.getBlock() instanceof ChainBlock)
        {
            return false;
        }

        boolean isCeilingAttatched = isCeilingAttached(state);

        // blocks under a top slab that get +0.5 UP via getYOffset should not
        // also get -0.5 DOWN. Use isCeilingAttached here (safe, no shape calcs)
        // since shouldOffset is called from paths outside the recursion guard.
        if (isCeilingAttatched && isTopSlab(world.getBlockState(pos.up())))
        {
            return false;
        }

        // blocks hanging from above (lanterns, etc.) — don't offset DOWN by slab below
        // (they may get a separate +0.5 UP offset via getYOffset)
        if (state.contains(Properties.HANGING) && state.get(Properties.HANGING))
        {
            return false;
        }

        // avoid calling hasBottomSlabInColumnBelow twice on the same value
        // once this var is set to true, we know there is a slabbed block below in the column
        // So the other call will get skipped (thanks short circuit!)
        boolean hasBottomSlabInColumnBelowCalculated = false;

        // ceiling-attached blocks further down a chain of ceiling blocks
        // leading to a top slab also get +0.5 UP; exclude from -0.5
        if (isCeilingAttatched)
        {
            Vec3d center = pos.toCenterPos();
            // if isCeilingAttatched block state
            if (DEBUG_CEILING_ATTATCHED)
                Slabbed.gizmo(() -> GizmoDrawing.box(Box.of(center.add(0, 0.1, 0), 0.2, 0.2, 0.2), DrawStyle.filled(0xff000060)).ignoreOcclusion().withLifespan(LP));

            hasBottomSlabInColumnBelowCalculated = true;
            if (!hasBottomSlabInColumnBelow(world, pos))
            {
                // Return false if there is a slabbed block below
                if (DEBUG_CEILING_ATTATCHED)
                    Slabbed.gizmo(() -> GizmoDrawing.box(Box.of(center.add(0, -0.1, 0), 0.2, 0.2, 0.2), DrawStyle.filled(0xff0060c0)).ignoreOcclusion().withLifespan(LP));
                return false;
            }
        }

        // ── double-block: upper half checks two blocks down ───────────
        if (state.contains(Properties.DOUBLE_BLOCK_HALF))
        {
            DoubleBlockHalf half = state.get(Properties.DOUBLE_BLOCK_HALF);
            if (half == DoubleBlockHalf.UPPER)
            {
                return isBottomSlab(world.getBlockState(pos.down(2)));
            }
            return hasBottomSlabBelow(world, pos);
        }

        // ── direct + recursive chain ──────────────────────────────────
        if (hasBottomSlabInColumnBelowCalculated || hasBottomSlabInColumnBelow(world, pos))
        {
            if (DEBUG_NORMAL_BLOCK_OFFSET_DOWN)
            {
                BlockPos blockPos = new BlockPos(pos);
                Slabbed.gizmo(() -> GizmoDrawing.box(blockPos, 0.02f, DrawStyle.stroked(0xff009933, 4f)).fadeOut().withLifespan(LP));
            }
            return true;
        }

        // ── wall-attached blocks: check the block they're mounted on ──
        Block block = state.getBlock();
        if ((block instanceof WallSignBlock ||
            block instanceof WallBannerBlock ||
            block instanceof WallTorchBlock ||
            block instanceof WallHangingSignBlock)
            && state.contains(Properties.HORIZONTAL_FACING))
        {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            BlockPos attachedPos = pos.offset(facing.getOpposite());
            return hasBottomSlabInColumnBelow(world, attachedPos);
        }

        return false;
    }

    /// Returns the Y offset for the block at `pos`.
    ///
    /// - `-0.5` for blocks sitting above a bottom slab (or chain).
    /// - `+0.5` for hanging blocks (HANGING=true) directly below a top slab.
    /// - `0.0` otherwise (no offset).
    ///
    public static double getYOffset(BlockView world, BlockPos pos, BlockState state)
    {
        if (world == null || pos == null)
        {
            return 0.0;
        }

        // Recursion guard: isSolidBlock → getCollisionShape → getOutlineShape (mixin) → getYOffset
        if (IN_GET_Y_OFFSET.get())
        {
            return 0.0;
        }
        IN_GET_Y_OFFSET.set(Boolean.TRUE);
        try
        {
            return getYOffsetInner(world, pos, state);
        } finally
        {
            IN_GET_Y_OFFSET.set(Boolean.FALSE);
        }
    }

    private static double getYOffsetInner(BlockView world, BlockPos pos, BlockState state)
    {
        // Slab-on-offset-block: a slab placed on top of a solid block that sits on a bottom slab
        // inherits the same -0.5 dy so the stack stays visually continuous (no gap).
        if (state.getBlock() instanceof SlabBlock)
        {
            BlockPos belowPos = pos.down();
            BlockState below = world.getBlockState(belowPos);
            Block belowBlock = below.getBlock();

            // Specific order to run tests from least to most expensive
            if (!(belowBlock instanceof SlabBlock) &&
                !below.isAir() &&
                below.getFluidState().isEmpty() &&
                !isTopStopper(below) &&
                hasBottomSlabBelow(world, belowPos) &&
                !isCeilingAttached(below))
            {
                if (DEBUG_ALLOWED_SLAB_OFFSET)
                {
                    BlockPos blockPos = new BlockPos(pos);
                    Vec3d boxCenter = blockPos.toCenterPos();
                    switch (state.get(Properties.SLAB_TYPE))
                    {
                        case TOP -> boxCenter = boxCenter.add(0, 0.25, 0);
                        case BOTTOM -> boxCenter = boxCenter.add(0, -0.25, 0);
                    }
                    Box box = Box.of(boxCenter, 1, state.get(Properties.SLAB_TYPE) == SlabType.DOUBLE ? 1 : 0.5, 1);
                    Slabbed.gizmo(() -> GizmoDrawing.box(box, DrawStyle.stroked(0xff32ff00, 4f)).fadeOut().withLifespan(LP));
                }

                return -0.5;
            }
        }

        // Conservative: solid ground blocks sitting on a bottom slab render at slab height.
        if (hasBottomSlabBelow(world, pos))
        {
            Block block = state.getBlock();

            // excluded partials / non-blocks / fluids / thin layers — no slab anchoring expansion
            if (!(
                block instanceof SlabBlock ||
                block instanceof StairsBlock ||
                block instanceof FenceBlock ||
                block instanceof WallBlock ||
                block instanceof TrapdoorBlock ||
                block instanceof PaneBlock ||
                isTopStopper(state) ||
                state.isAir() ||
                !state.getFluidState().isEmpty() ||
                !state.isSolidBlock(world, pos)
            ))
            {
                return -0.5;
            }
        }

        if (shouldOffsetDown(world, pos, state))
        {
            return -0.5;
        }

        // ── blocks under a top slab: +0.5 UP ──────────────────────────
        // Blacklist: these block types should NEVER float up into the slab space.
        // Everything else (small decorative / ceiling blocks) gets +0.5.
        // Note: isSolidBlock is safe here because getYOffset has a recursion guard.
        Block block = state.getBlock();
        if (block instanceof SlabBlock ||
            block instanceof StairsBlock ||
            block instanceof FenceBlock ||
            block instanceof WallBlock ||
            block instanceof PaneBlock ||
            isTopStopper(state) ||
            state.isAir() ||
            !state.getFluidState().isEmpty() ||
            state.isSolidBlock(world, pos))
        {
            return 0.0;
        }

        BlockState above = world.getBlockState(pos.up());

        // direct: any non-blacklisted block directly under a top slab
        if (isTopSlab(above))
        {
            return 0.5;
        }

        // cascading: ceiling-attached block below other ceiling-attached blocks
        // leading up to a top slab (e.g. 2nd dripstone, 2nd vine segment)
        if (isCeilingAttached(state))
        {
            BlockPos cursor = pos.up();
            for (int i = 0; i < MAX_CHAIN_DEPTH; i++)
            {
                BlockState cur = world.getBlockState(cursor);
                if (isTopSlab(cur))
                {
                    return 0.5;
                }
                if (isCeilingAttached(cur))
                {
                    cursor = cursor.up();
                    continue;
                }
                break;
            }
        }

        return 0.0;
    }

    /// Walks down from `pos` through non-air, non-slab blocks looking
    /// for a bottom slab. Returns true as soon as one is found.
    private static boolean hasBottomSlabInColumnBelow(BlockView world, BlockPos pos)
    {
        BlockPos cursor = pos.down();
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++)
        {
            BlockState cur = world.getBlockState(cursor);
            if (isCeilingAttached(cur))
            {
                return false;
            }
            if (isBottomSlab(cur))
            {
                if (DEBUG_hasBottomSlabInColumnBelow)
                {
                    Vec3d start = pos.toCenterPos();
                    Vec3d end = cursor.toCenterPos();
                    Slabbed.gizmo(() -> GizmoDrawing.arrow(start, end, 0xff90ff90).ignoreOcclusion().withLifespan(LP));
                }
                return true;
            }
            if (cur.isAir() || cur.getBlock() instanceof SlabBlock || isTopStopper(cur))
            {
                return false;
            }
            cursor = cursor.down();

            if (DEBUG_hasBottomSlabInColumnBelow)
            {
                int iFinal = i;
                BlockPos gizmoPos = new BlockPos(pos);
                Slabbed.gizmo(() -> GizmoDrawing.text("I: " + iFinal, gizmoPos.toCenterPos().add(0, iFinal / 5d - 0.1, 0), TextGizmo.Style.centered(0xffffffff)).ignoreOcclusion().withLifespan(LP));
            }
        }
        if (DEBUG_hasBottomSlabInColumnBelow_FAILED_ALL_BLOCKS)
        {
            Vec3d start = pos.toCenterPos();
            Vec3d end = cursor.toCenterPos();
            Slabbed.gizmo(() -> GizmoDrawing.arrow(start, end, 0xffff3030).ignoreOcclusion().withLifespan(LP));
        }
        return false;
    }

    /// Redstone dust support surface — treat slab tops like valid ground for downward stepping.
    public static boolean isRedstoneSupportTopSurface(BlockView world, BlockPos pos)
    {
        BlockState state = world.getBlockState(pos);

        if (state.isSideSolidFullSquare(world, pos, Direction.UP))
        {
            return true;
        }

        return isSupportingSlab(state) && (isBottomSlab(state) || isTopSlab(state));
    }
}
