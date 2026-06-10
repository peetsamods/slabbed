package com.slabbed.util;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.compat.CompatHooks;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChiseledBookshelfBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.KelpBlock;
import net.minecraft.block.KelpPlantBlock;
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
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Central helper for slab support semantics.
 */
public final class SlabSupport {
    private static final Long2DoubleOpenHashMap CLIENT_VISUAL_Y_OFFSETS = new Long2DoubleOpenHashMap();
    private static final Object CLIENT_VISUAL_Y_OFFSETS_LOCK = new Object();

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

    private static boolean hasSlabTypeSurface(BlockState state) {
        return state != null
                && state.contains(SlabBlock.TYPE)
                && state.getFluidState().isEmpty();
    }

    private static boolean isCustomSlabSurface(BlockState state) {
        return hasSlabTypeSurface(state)
                && CompatHooks.customSlabSurfaceKind(state) != CompatSlabSurfaceKind.NONE;
    }

    private static boolean isLowerableSlabSurface(BlockState state) {
        if (!hasSlabTypeSurface(state)) {
            return false;
        }
        if (isCustomSlabSurface(state)) {
            return true;
        }
        return state.getBlock() instanceof SlabBlock
                && !CompatHooks.shouldSkipSlabSupport(state);
    }

    private static boolean isTopLikeLowerableSlabSurface(BlockState state) {
        if (!hasSlabTypeSurface(state)) {
            return false;
        }
        CompatSlabSurfaceKind customKind = CompatHooks.customSlabSurfaceKind(state);
        if (customKind == CompatSlabSurfaceKind.TOP_LIKE || customKind == CompatSlabSurfaceKind.DOUBLE_LIKE) {
            return true;
        }
        return state.getBlock() instanceof SlabBlock
                && !CompatHooks.shouldSkipSlabSupport(state)
                && state.get(SlabBlock.TYPE) != SlabType.BOTTOM;
    }

    private static boolean hasRaisedCeilingAttachBaseline(BlockState state) {
        return isTopSlab(state)
                || CompatHooks.customSlabSurfaceKind(state) == CompatSlabSurfaceKind.TOP_LIKE;
    }

    /**
     * Single source of truth: returns true iff the state is a TOP slab
     * and the queried face is DOWN (i.e. the underside of a top slab).
     */
    public static boolean isTopSlabUndersideSupport(BlockState state, Direction face) {
        return face == Direction.DOWN && isTopLikeCeilingSurface(state);
    }

    /**
     * True for bottom-like custom slabs when vanilla asks whether the visible
     * underside can carry a small hanging attachment such as a lantern.
     */
    public static boolean isBottomLikeSlabUndersideHookSupport(BlockState state, Direction face) {
        return face == Direction.DOWN
                && CompatHooks.customSlabSurfaceKind(state) == CompatSlabSurfaceKind.BOTTOM_LIKE;
    }

    /** True if the block at {@code posAbove} is a top or double slab that can provide ceiling support. */
    public static boolean isCeilingSupportBottomSurface(WorldView world, BlockPos posAbove) {
        BlockState stateAbove = world.getBlockState(posAbove);
        if (isTopLikeCeilingSurface(stateAbove)) {
            return true;
        }
        if (!isSupportingSlab(stateAbove)) {
            return false;
        }
        SlabType type = stateAbove.get(SlabBlock.TYPE);
        return type == SlabType.TOP || type == SlabType.DOUBLE;
    }

    /** Overload for shape/world views. */
    public static boolean isCeilingSupportBottomSurface(BlockView world, BlockPos posAbove) {
        BlockState stateAbove = world.getBlockState(posAbove);
        if (isTopLikeCeilingSurface(stateAbove)) {
            return true;
        }
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
        BlockState below = getBlockStateOrNull(world, pos.down());
        return below != null && isBottomSlab(below);
    }

    public static boolean isDirectObjectSupportSurface(BlockView world, BlockPos pos, BlockState state) {
        return getDirectObjectSupportTopOffset(state) > 0.0;
    }

    public static boolean isDirectCustomSlabSupportedObject(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isDirectCustomSlabSupportSubject(world, pos, state)) {
            return false;
        }

        if (state.contains(Properties.BED_PART) && state.contains(Properties.HORIZONTAL_FACING)) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            BedPart part = state.get(Properties.BED_PART);
            BlockPos otherPos = part == BedPart.FOOT
                    ? pos.offset(facing)
                    : pos.offset(facing.getOpposite());
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

    /** Debug toggle (-Dslabbed.disableStepCull=true) to disable the step-face cull relaxation. */
    private static final boolean STEP_CULL_DISABLED = Boolean.getBoolean("slabbed.disableStepCull");

    /**
     * True if the {@code direction} side face of the opaque cube {@code state} at
     * {@code pos} should be DRAWN even though the chunk mesher culls it, because exactly
     * one of this block and its {@code direction} neighbour is a lowered custom-supported
     * object — i.e. they sit at different visual heights and the slab step exposes part of
     * the shared face (the see-through "window" / "doom-infinity" hole on a lowered cube in
     * a terrace).
     *
     * <p>Cheap: uses {@link #isDirectCustomSlabSupportedObject} (fast-false for ordinary
     * terrain) rather than the deep column walk in {@link #getYOffset}, so it is safe to
     * call from the per-face chunk culling path. Only ever ADDS faces.
     */
    public static boolean isSlabHeightStepFace(BlockView world, BlockPos pos, BlockState state, Direction direction) {
        if (STEP_CULL_DISABLED || world == null || pos == null || state == null || direction == null
                || !direction.getAxis().isHorizontal() || !state.isOpaqueFullCube()) {
            return false;
        }
        BlockPos neighborPos = pos.offset(direction);
        BlockState neighbor = getBlockStateOrNull(world, neighborPos);
        if (neighbor == null) {
            return false;
        }
        boolean selfLowered = isDirectCustomSlabSupportedObject(world, pos, state);
        boolean neighborLowered = isDirectCustomSlabSupportedObject(world, neighborPos, neighbor);
        return selfLowered != neighborLowered;
    }

    /**
     * Primary query: should this slab top face count as solid support.
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
        if (isCeilingAttached(state) && isTopLikeCeilingSurface(world.getBlockState(pos.up()))) {
            return false;
        }

        // ceiling-attached blocks further down a chain of ceiling blocks
        // leading to a top slab also get +0.5 UP; exclude from -0.5
        if (isCeilingAttached(state)) {
            BlockPos cursor = pos.up();
            for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
                BlockState cur = world.getBlockState(cursor);
                if (isTopLikeCeilingSurface(cur)) {
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
        if (CompatHooks.shouldSkipOffset(state)
                && !isLoweredCustomSlabSurface(world, pos, state)) {
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
     * Shared client visual-dy authority.
     *
     * <p>Main-thread client-world callers compute and publish the dy for {@code pos}.
     * Render-worker callers such as {@code ChunkRendererRegion} prefer that
     * published value, so chunk meshes, outline/raycast, and block-entity render use
     * the same per-position visual decision. A cache miss falls back to the
     * underlying calculation to preserve first-render behavior; dependent rerender
     * hooks prewarm the affected region before scheduling rebuilds.
     */
    public static double getVisualYOffset(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return 0.0;
        }
        if (isClientWorld(world)) {
            double dy = getYOffset(world, pos, state);
            putClientVisualYOffset(pos, dy);
            return dy;
        }
        Double cached = cachedClientVisualYOffset(pos);
        if (cached != null) {
            return cached;
        }
        return getYOffset(world, pos, state);
    }

    public static void refreshVisualYOffsetRegion(BlockView world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (!isClientWorld(world)) {
            return;
        }
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    mutable.set(x, y, z);
                    BlockState state = getBlockStateOrNull(world, mutable);
                    if (state == null) {
                        removeClientVisualYOffset(mutable);
                    } else {
                        putClientVisualYOffset(mutable, getYOffset(world, mutable, state));
                    }
                }
            }
        }
    }

    public static void clearVisualYOffsetCache() {
        synchronized (CLIENT_VISUAL_Y_OFFSETS_LOCK) {
            CLIENT_VISUAL_Y_OFFSETS.clear();
        }
    }

    private static boolean isClientWorld(BlockView world) {
        return world instanceof World w && w.isClient();
    }

    private static void putClientVisualYOffset(BlockPos pos, double dy) {
        synchronized (CLIENT_VISUAL_Y_OFFSETS_LOCK) {
            CLIENT_VISUAL_Y_OFFSETS.put(pos.asLong(), dy);
        }
    }

    private static void removeClientVisualYOffset(BlockPos pos) {
        synchronized (CLIENT_VISUAL_Y_OFFSETS_LOCK) {
            CLIENT_VISUAL_Y_OFFSETS.remove(pos.asLong());
        }
    }

    private static Double cachedClientVisualYOffset(BlockPos pos) {
        synchronized (CLIENT_VISUAL_Y_OFFSETS_LOCK) {
            long packed = pos.asLong();
            return CLIENT_VISUAL_Y_OFFSETS.containsKey(packed)
                    ? CLIENT_VISUAL_Y_OFFSETS.get(packed)
                    : null;
        }
    }

    /** Bounded depth used by the client dependent-rerender pass (Fix 3). */
    public static int chainRerenderDepth() {
        return MAX_CHAIN_DEPTH;
    }

    public static boolean isLoweredSideSlabVisual(BlockView world, BlockPos slabPos, BlockState slabState) {
        if (world == null
                || slabPos == null
                || slabState == null
                || !isLowerableSlabSurface(slabState)) {
            return false;
        }
        return SlabAnchorAttachment.isAnchored(world, slabPos)
                || isAdjacentSideSlabLowered(world, slabPos, slabState);
    }

    /**
     * LEAN gate for the client dependent-rerender mixin: true iff a bottom slab
     * or a persistent anchor lies in the bounded column directly below {@code pos}
     * (i.e. {@code pos} sits in a lowered column, so a change at/near it can shift
     * the rendered dy of stacked dependents above). Deliberately avoids the
     * adjacency / shape-triggering checks in {@link #hasSlabInColumn} so it is
     * cheap to call on every client block change.
     */
    public static boolean hasLoweringSourceInColumnBelow(BlockView world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockPos cursor = pos.down();
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = getBlockStateOrNull(world, cursor);
            if (cur == null) {
                return false;
            }
            if (isBottomSlab(cur) || SlabAnchorAttachment.isAnchored(world, cursor)) {
                return true;
            }
            // A Terrain Slabs custom BOTTOM_LIKE surface lowers everything stacked above
            // it just like a vanilla bottom slab. Recognise it here so a block placed on
            // Terrain Slabs terrain qualifies for a column anchor and keeps its lowered dy
            // when the block below it is broken (otherwise the live column walk hits the
            // gap, the block un-lowers, and it jumps up into the block above — the z-fight).
            if (CompatHooks.customSlabSurfaceKind(cur) == CompatSlabSurfaceKind.BOTTOM_LIKE) {
                return true;
            }
            if (cur.isAir() || isLowerableSlabSurface(cur) || isThinTopLayer(cur)) {
                return false;
            }
            cursor = cursor.down();
        }
        return false;
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
    private static boolean isAdjacentSideSlabLowered(BlockView world, BlockPos slabPos, BlockState slabState) {
        if (!slabState.contains(SlabBlock.TYPE)) {
            return false;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(slabPos);
        visited.add(slabPos.asLong());

        while (!queue.isEmpty() && visited.size() <= MAX_CHAIN_DEPTH) {
            BlockPos current = queue.removeFirst();
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos neighborPos = current.offset(dir);
                BlockState neighbor = getBlockStateOrNull(world, neighborPos);
                if (neighbor == null) {
                    continue;
                }
                if (isLoweredSideSlabSource(world, neighborPos, neighbor)) {
                    return true;
                }
                if (isLowerableSlabSurface(neighbor) && visited.add(neighborPos.asLong())) {
                    queue.addLast(neighborPos);
                }
            }
        }
        return false;
    }

    /**
     * Recursion-safe live check: does {@code pos} have a horizontal neighbour that is a
     * lowered ordinary full block? Mirrors {@code qualifiesForAdjacentLoweredFullBlockAnchor}
     * but determines the neighbour's lowering from its sources (anchor / column source
     * below) instead of {@code getYOffset}, so it is safe to call inside the
     * {@code IN_GET_Y_OFFSET} guard. Used to lower a cantilevered side-placed block live,
     * before its own anchor syncs (eliminating the placement snap).
     */
    private static boolean isAdjacentToLoweredFullBlock(BlockView world, BlockPos pos) {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(direction);
            BlockState neighbor = getBlockStateOrNull(world, neighborPos);
            if (neighbor == null || neighbor.isAir()) {
                continue;
            }
            Block block = neighbor.getBlock();
            if (isLowerableSlabSurface(neighbor) || block instanceof BlockEntityProvider) {
                continue;
            }
            if (!neighbor.isSolidBlock(world, neighborPos)) {
                continue;
            }
            if (SlabAnchorAttachment.isAnchored(world, neighborPos)
                    || hasLoweringSourceInColumnBelow(world, neighborPos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLoweredSideSlabSource(BlockView world, BlockPos pos, BlockState state) {
        if (isLowerableSlabSurface(state)) {
            return isVerticallyLoweredSlabSource(world, pos, state);
        }
        if (!state.isSolidBlock(world, pos)) {
            return false;
        }
        return hasBottomSlabBelow(world, pos)
                || SlabAnchorAttachment.isAnchored(world, pos)
                || isDirectCustomSlabSupportedObject(world, pos, state);
    }

    private static boolean isVerticallyLoweredSlabSource(BlockView world, BlockPos pos, BlockState state) {
        if (world == null
                || pos == null
                || state == null
                || !isLowerableSlabSurface(state)) {
            return false;
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return true;
        }
        BlockPos belowPos = pos.down();
        BlockState below = getBlockStateOrNull(world, belowPos);
        return below != null
                && (hasLoweredNonSlabTopSupport(world, belowPos, below)
                || hasLoweredTopLikeSlabSupport(world, belowPos, below));
    }

    private static boolean hasLoweredNonSlabTopSupport(BlockView world, BlockPos pos, BlockState state) {
        if (world == null
                || pos == null
                || state == null
                || state.isAir()
                || isLowerableSlabSurface(state)
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (hasBottomSlabBelow(world, pos) || SlabAnchorAttachment.isAnchored(world, pos)) {
            return true;
        }
        double directCustomDy = directCustomSlabSupportDy(world, pos, state);
        if (Double.isFinite(directCustomDy) && directCustomDy < -1.0e-6) {
            return true;
        }
        return shouldOffset(world, pos, state) && slabColumnYOffset(world, pos) < -1.0e-6;
    }

    private static boolean hasLoweredTopLikeSlabSupport(BlockView world, BlockPos pos, BlockState state) {
        if (world == null
                || pos == null
                || state == null
                || !isTopLikeLowerableSlabSurface(state)) {
            return false;
        }
        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        return SlabAnchorAttachment.isAnchored(world, pos)
                || hasLoweredNonSlabTopSupport(world, belowPos, below)
                || isAdjacentSideSlabLowered(world, pos, state);
    }

    private static boolean isAdjacentCustomSideSlabLowered(BlockView world, BlockPos slabPos, BlockState slabState) {
        if (!isCustomSlabSurface(slabState)) {
            return false;
        }
        return isAdjacentSideSlabLowered(world, slabPos, slabState);
    }

    private static boolean isLoweredCustomSlabSurface(BlockView world, BlockPos pos, BlockState state) {
        if (!isCustomSlabSurface(state)) {
            return false;
        }
        double dy = loweredSlabUndersideSupportDy(world, pos, state);
        return Double.isFinite(dy) && dy < -1.0e-6;
    }

    private static double getYOffsetInner(BlockView world, BlockPos pos, BlockState state) {
        // Slab-surface-on-offset-block: a slab placed on top of a solid block that sits on a
        // bottom slab inherits the same -0.5 dy so the stack stays visually continuous (no gap).
        if (isLowerableSlabSurface(state)) {
            if (SlabAnchorAttachment.isAnchored(world, pos)) {
                return -0.5;
            }
            BlockPos belowPos = pos.down();
            BlockState below = world.getBlockState(belowPos);
            if (hasLoweredNonSlabTopSupport(world, belowPos, below)
                    || hasLoweredTopLikeSlabSupport(world, belowPos, below)) {
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

        // Persistent slab-anchor: an ordinary FB placed directly on a bottom slab is
        // recorded on the chunk via SlabAnchorAttachment at placement time and cleared
        // when the FB itself is broken/replaced. Anchors persist across supporting BS
        // removal so the FB does not visually jump upward.
        // Only honour anchors for non-slab blocks; slabs were handled above.
        if (!isLowerableSlabSurface(state)
                && com.slabbed.anchor.SlabAnchorAttachment.isAnchored(world, pos)) {
            // Mixed-slab compound (mirrors the directCustom path below): a block anchored
            // directly on a vanilla BOTTOM slab that is itself lowered must follow the slab's
            // own drop on top of the anchor's -0.5, or it pops UP half a block the instant its
            // placement anchor syncs — the live directCustom path gives the correct -1.0 on the
            // first client frame, then this flat -0.5 anchor pinned it back up (the reported
            // crafting-table-on-mixed-slab pop). loweredBottomSlabSupportDy is recursion-safe
            // and returns 0.0/NaN for a non-lowered or non-bottom-slab support, so every other
            // anchor case (block on a plain slab, persisted anchor after its slab was broken,
            // column/adjacent/below-anchored) is unchanged at -0.5.
            double anchorDy = -0.5;
            double supportLoweredDy = loweredBottomSlabSupportDy(world, pos.down());
            if (Double.isFinite(supportLoweredDy) && supportLoweredDy < -1.0e-6) {
                anchorDy += supportLoweredDy;
            }
            if (com.slabbed.anchor.SlabAnchorAttachment.TRACE) {
                String side = (world instanceof net.minecraft.world.World w && w.isClient()) ? "CLIENT" : "SERVER";
                Slabbed.LOGGER.info("[ANCHOR] dy applied side={} pos={} state={} dy={}",
                        side, pos.toShortString(), state, anchorDy);
            }
            return anchorDy;
        }

        // Gap-fill (live + recursion-safe): an ordinary solid block sitting directly below
        // an anchored lowered block belongs to that lowered column, so it must lower to
        // match — even when its own support below is air (a broken-out gap that the
        // column/direct checks can't see). Computing this live (isAnchored never calls
        // getYOffset) means a refilled block lowers on the very first client frame instead
        // of un-lowering and z-fighting the anchored block above until its own anchor syncs.
        if (!isLowerableSlabSurface(state)
                && !(state.getBlock() instanceof BlockEntityProvider)
                && state.isSolidBlock(world, pos)
                && world.getBlockState(pos.down()).isAir()) {
            BlockPos abovePos = pos.up();
            BlockState above = world.getBlockState(abovePos);
            if (!isLowerableSlabSurface(above)
                    && com.slabbed.anchor.SlabAnchorAttachment.isAnchored(world, abovePos)) {
                return -0.5;
            }
            // Cantilevered perpendicular side placement: a block placed beside a lowered
            // full block with air below it lowers only via its (server-side, one-tick-late)
            // adjacent anchor. Detecting the lowered neighbour live makes the first client
            // mesh already -0.5, so it never renders at full height first (the snap).
            if (isAdjacentToLoweredFullBlock(world, pos)) {
                return -0.5;
            }
        }

        double directCustomSurfaceDy = directCustomSlabSupportDy(world, pos, state);
        if (!Double.isNaN(directCustomSurfaceDy)) {
            // Combined-slab compound. directCustomSlabSupportDy returns a flat -0.5: the drop to
            // sit on a half-height bottom-type surface (a Terrain Slabs BOTTOM_LIKE slab). Two
            // corrections keep stacked/combined slabs FLUSH instead of floating half a block:
            //  (1) If the immediate support is itself a lowered vanilla BOTTOM slab (a "mixed
            //      slab" — a vanilla bottom slab capping the terrain — or a stack of them),
            //      follow its drop too. Applies to objects, full blocks, AND vanilla slabs
            //      stacked on a mixed slab (vanilla-only: terrain slabs are skip-offset and
            //      never reach here). loweredBottomSlabSupportDy is recursion-safe.
            //  (2) A vanilla TOP slab caps from the UPPER half of its own block, so it needs an
            //      extra -0.5 to sit flush on a bottom-type surface (else a half-block gap shows
            //      underneath — the vanilla-TOP-slab-on-terrain bug). BOTTOM/DOUBLE slabs and
            //      non-slab objects are unaffected.
            // The result is clamped to -1.0: that is the deepest offset the offset-aware pick
            // raycast covers ({C, C.up, C.down}), so deeper niche combos (e.g. a TOP slab on a
            // mixed slab) settle at -1.0 rather than going untargetable.
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

        // ── Decorative hangers under a LOWERED support follow it down ──────────
        // A lantern / soul lantern / hanging sign / spore blossom / hanging roots /
        // pale hanging moss beneath a support that itself renders lowered must
        // inherit the support's negative dy, or the lowered support's underside clips
        // into the hanger's top. Chains are EXCLUDED so they keep extending to reach
        // the support. Runs BEFORE the +0.5 ceiling branch; the helpers return
        // 0.0/NaN for a normal (non-lowered) support so the already-correct flush and
        // +0.5 cases stay untouched. The helpers are recursion-safe mirrors of the dy
        // logic above and never call getYOffset (safe inside the IN_GET_Y_OFFSET guard).
        if (isLoweredUndersideHangerOwner(state)) {
            BlockPos supportPos = pos.up();
            BlockState supportState = world.getBlockState(supportPos);
            double slabSupportDy = loweredSlabUndersideSupportDy(world, supportPos, supportState);
            if (Double.isFinite(slabSupportDy) && slabSupportDy < -1.0e-6) {
                // A TOP slab's underside sits half a block higher than a hanger's
                // natural attach (support.y+1.5 vs hanger.y+1), so the hanger keeps
                // its +0.5 raised-attach baseline on top of the slab's lowering.
                return hasRaisedCeilingAttachBaseline(supportState) ? slabSupportDy + 0.5 : slabSupportDy;
            }
            double fullBlockSupportDy = loweredFullBlockUndersideSupportDy(world, supportPos, supportState);
            if (Double.isFinite(fullBlockSupportDy) && fullBlockSupportDy < -1.0e-6) {
                return fullBlockSupportDy;
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

        // A non-solid object (lantern, etc.) standing ON TOP of a full-block support that is
        // itself rendered lowered must follow that support down, or it floats above the
        // support's lowered top face (the reported still-floating-lantern bug: a standing
        // lantern on a lowered grass/dirt/planks block). The object's own shouldOffset column
        // walk above can MISS this lowering — e.g. when the support is lowered by a persisted
        // anchor or by adjacency but its own column below is air (a cantilever stops the walk)
        // or a full-height solid block (no slab in the lantern's column at all). Resolve the
        // support's actual rendered dy via the recursion-safe loweredFullBlockUndersideSupportDy
        // (anchor / direct-custom-TS / lowered column / compound -1.0; adjacency-lowered
        // cantilevers report through their persisted anchor), which never re-enters getYOffset,
        // and inherit it. Reached only by non-solid objects (solid blocks returned 0.0 just
        // above), so the common render path pays nothing; returns NaN/0.0 for a non-lowered
        // support so flush cases stay untouched.
        BlockPos sitSupportPos = pos.down();
        BlockState sitSupport = world.getBlockState(sitSupportPos);
        double sitSupportDy = loweredFullBlockUndersideSupportDy(world, sitSupportPos, sitSupport);
        if (Double.isFinite(sitSupportDy) && sitSupportDy < -1.0e-6) {
            return sitSupportDy;
        }
        // Cantilever fallback: a support lowered LIVE purely by adjacency to a lowered full
        // block (no persisted anchor yet, e.g. the first client frame after a side-placement)
        // is not yet reported by loweredFullBlockUndersideSupportDy, so detect it directly.
        if (!isLowerableSlabSurface(sitSupport)
                && !(sitSupport.getBlock() instanceof BlockEntityProvider)
                && sitSupport.isSolidBlock(world, sitSupportPos)
                && isAdjacentToLoweredFullBlock(world, sitSupportPos)) {
            return -0.5;
        }

        // A non-solid object (lantern/chain/…) standing on a TOP or DOUBLE slab that is
        // itself rendered lowered must follow that slab down, or it floats above the slab's
        // lowered top face (the reported TS+VS floating-lantern bug: a vanilla TOP/DOUBLE slab
        // sitting on a lowered Terrain-Slabs/full-block column). BOTTOM-slab supports are
        // already handled by the shouldOffset path above (which yields -0.5 or the compound
        // -1.0), and the object's own top face sits at the slab's top, so the object inherits
        // the slab's rendered dy. loweredSlabUndersideSupportDy is recursion-safe (never calls
        // getYOffset) and returns 0.0 for a non-lowered slab so flush cases stay untouched.
        if (isTopLikeLowerableSlabSurface(sitSupport)) {
            double slabSitDy = loweredSlabUndersideSupportDy(world, sitSupportPos, sitSupport);
            if (Double.isFinite(slabSitDy) && slabSitDy < -1.0e-6) {
                return slabSitDy;
            }
        }

        BlockState above = world.getBlockState(pos.up());

        // direct: ceiling-attached blocks directly under a top slab
        if (isCeilingAttached(state) && isTopLikeCeilingSurface(above)) {
            return 0.5;
        }

        // cascading: ceiling-attached block below other ceiling-attached blocks
        // leading up to a top slab (e.g. 2nd dripstone, 2nd vine segment)
        if (isCeilingAttached(state)) {
            BlockPos cursor = pos.up();
            for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
                BlockState cur = world.getBlockState(cursor);
                if (isTopLikeCeilingSurface(cur)) {
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
     * Decorative ceiling hangers that must FOLLOW a lowered support down so they
     * stay flush instead of clipping up into it: lanterns, soul lanterns, hanging
     * signs, spore blossoms, hanging roots, and pale hanging moss. Chains are
     * deliberately EXCLUDED — they extend to reach their support rather than tracking
     * its dy.
     */
    private static boolean isLoweredUndersideHangerOwner(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        return state.isOf(Blocks.LANTERN)
                || state.isOf(Blocks.SOUL_LANTERN)
                || block instanceof HangingSignBlock
                || block instanceof SporeBlossomBlock
                || block instanceof HangingRootsBlock
                || isPaleHangingMossBlock(state);
    }

    private static boolean isPaleHangingMossBlock(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        var id = Registries.BLOCK.getId(state.getBlock());
        if (id == null || !"minecraft".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return "pale_hanging_moss".equals(path) || "pale_hanging_moss_tip".equals(path);
    }

    /**
     * Recursion-safe rendered dy of a SLAB support directly above a hanger,
     * mirroring the slab branch of {@link #getYOffsetInner} without re-entering
     * {@link #getYOffset}. Returns {@code 0.0} for a non-lowered slab (the caller
     * gates on {@code < -1e-6}) or {@link Double#NaN} if not a slab.
     */
    private static double loweredSlabUndersideSupportDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || !isLowerableSlabSurface(state)) {
            return Double.NaN;
        }
        Double cachedDy = cachedClientVisualYOffset(pos);
        if (cachedDy != null) {
            return cachedDy;
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return -0.5;
        }
        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        if (hasLoweredNonSlabTopSupport(world, belowPos, below)
                || hasLoweredTopLikeSlabSupport(world, belowPos, below)) {
            return -0.5;
        }
        if (isAdjacentSideSlabLowered(world, pos, state)) {
            return -0.5;
        }
        return 0.0;
    }

    /**
     * Recursion-safe rendered dy of a SOLID NON-SLAB full-block support directly
     * above a hanger, mirroring the anchor / direct-custom / column branches of
     * {@link #getYOffsetInner} without re-entering {@link #getYOffset}. Returns a
     * negative lowered dy ({@code -0.5} / {@code -1.0}), {@code 0.0} (not lowered),
     * or {@link Double#NaN} (not a qualifying full block).
     */
    private static double loweredFullBlockUndersideSupportDy(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || isLowerableSlabSurface(state)
                || !state.getFluidState().isEmpty()
                || !state.isSolidBlock(world, pos)) {
            return Double.NaN;
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return -0.5;
        }
        double directCustomDy = directCustomSlabSupportDy(world, pos, state);
        if (!Double.isNaN(directCustomDy)) {
            return directCustomDy;
        }
        if (shouldOffset(world, pos, state)) {
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
        return Double.NaN;
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
        return getVisualYOffset(world, pos, state) == -0.5;
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
        return getVisualYOffset(world, pos, state) < 0.0;
    }

    public static boolean isLoweredBedVisual(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        return state.getBlock() instanceof net.minecraft.block.BedBlock
                && state.contains(Properties.BED_PART)
                && getVisualYOffset(world, pos, state) == -0.5;
    }

    public static boolean isLoweredCustomSupportedObjectVisual(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        return isDirectCustomSlabSupportedObject(world, pos, state)
                && getVisualYOffset(world, pos, state) == -0.5;
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
     *   <li>Log-family blocks in {@link BlockTags#LOGS} — logs, wood,
     *       stripped variants, and nether stems, once block tags are bound.</li>
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
        if (state.isOf(Blocks.BOOKSHELF)
                || state.isOf(Blocks.DRIED_KELP_BLOCK)
                || block instanceof ChiseledBookshelfBlock) {
            return true;
        }
        if (block instanceof KelpBlock || block instanceof KelpPlantBlock) {
            return true;
        }
        if (isLogFamilySlabSitCandidate(state)) {
            return true;
        }
        // Ordinary solid full blocks (grass, stone, dirt, …) also sit on and lower onto a
        // custom Terrain Slabs surface, matching how they already lower onto a vanilla
        // bottom slab via hasSlabInColumn. Previously only non-solid blocks qualified here,
        // so a stripped log lowered but a grass block did not — an inconsistency the player
        // reported. The exclusions in isDirectCustomSlabSupportSubject (air, non-vanilla
        // slabs, thin layers, fluids, Terrain Slabs' own blocks) already keep this tight.
        return true;
    }

    private static boolean isLogFamilySlabSitCandidate(BlockState state) {
        try {
            return state.isIn(BlockTags.LOGS);
        } catch (IllegalStateException e) {
            if ("Tags not bound".equals(e.getMessage())) {
                return false;
            }
            throw e;
        }
    }

    private static boolean isTopLikeCeilingSurface(BlockState state) {
        CompatSlabSurfaceKind customKind = CompatHooks.customSlabSurfaceKind(state);
        if (customKind == CompatSlabSurfaceKind.TOP_LIKE || customKind == CompatSlabSurfaceKind.DOUBLE_LIKE) {
            return true;
        }
        return isTopSlab(state)
                || isSupportingSlab(state) && state.get(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    private static double directCustomSlabSupportDy(BlockView world, BlockPos pos, BlockState state) {
        if (!isDirectCustomSlabSupportedObject(world, pos, state)) {
            return Double.NaN;
        }
        return -0.5;
    }

    /**
     * Recursion-safe rendered dy of a vanilla BOTTOM-slab support directly beneath a
     * standing object, used to compound a "mixed slab" lowering so the object follows
     * its support's own drop in addition to the normal sit-on-bottom-slab -0.5. Mirrors
     * the lowered cases of the slab branch in {@link #getYOffsetInner} without ever
     * re-entering {@link #getYOffset}. Returns {@link Double#NaN} when the support is not
     * a vanilla bottom slab and {@code 0.0} when it is a bottom slab that is not lowered,
     * so callers (which gate on {@code < -1e-6}) leave the flush case untouched.
     */
    private static double loweredBottomSlabSupportDy(BlockView world, BlockPos supportPos) {
        BlockState s = getBlockStateOrNull(world, supportPos);
        if (s == null
                || !(s.getBlock() instanceof SlabBlock)
                || !s.contains(SlabBlock.TYPE)
                || !isBottomSlab(s)
                || !s.getFluidState().isEmpty()) {
            return Double.NaN;
        }
        if (SlabAnchorAttachment.isAnchored(world, supportPos)) {
            return -0.5;
        }
        double directCustomDy = directCustomSlabSupportDy(world, supportPos, s);
        if (Double.isFinite(directCustomDy) && directCustomDy < -1.0e-6) {
            return directCustomDy;
        }
        if (isAdjacentSideSlabLowered(world, supportPos, s)) {
            return -0.5;
        }
        return 0.0;
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

    private static boolean isDirectCustomSlabSupportSubject(BlockView world, BlockPos pos, BlockState state) {
        Block block = state == null ? null : state.getBlock();
        boolean kelpFamily = block instanceof KelpBlock || block instanceof KelpPlantBlock;
        if (state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock && !isVanillaDirectCustomSlabSubject(state)
                || isThinTopLayer(state)
                || (!state.getFluidState().isEmpty() && !kelpFamily)
                || CompatHooks.shouldSkipOffset(state)) {
            return false;
        }
        return isVanillaDirectCustomSlabSubject(state) || isSlabSitCandidate(world, pos, state);
    }

    private static boolean isVanillaDirectCustomSlabSubject(BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock) || !state.contains(SlabBlock.TYPE)) {
            return false;
        }
        var id = Registries.BLOCK.getId(state.getBlock());
        return id != null && "minecraft".equals(id.getNamespace());
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
            BlockState cur = getBlockStateOrNull(world, cursor);
            if (cur == null) {
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
            cursor = cursor.down();
        }
        return false;
    }

    private static double slabColumnYOffset(BlockView world, BlockPos pos) {
        BlockPos cursor = pos.down();
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = getBlockStateOrNull(world, cursor);
            if (cur == null) {
                return 0.0;
            }
            if (cur.getBlock() instanceof SlabBlock
                    && (SlabAnchorAttachment.isAnchored(world, cursor)
                    || isAdjacentSideSlabLowered(world, cursor, cur))) {
                return isBottomSlab(cur) ? -1.0 : -0.5;
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

    private static BlockState getBlockStateOrNull(BlockView world, BlockPos pos) {
        try {
            return world.getBlockState(pos);
        } catch (IndexOutOfBoundsException e) {
            if (isChunkRendererRegion(world)) {
                return null;
            }
            throw e;
        }
    }

    private static boolean isChunkRendererRegion(BlockView world) {
        return world != null
                && "net.minecraft.client.render.chunk.ChunkRendererRegion".equals(world.getClass().getName());
    }
}
