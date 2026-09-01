package com.slabbed.util;

import com.slabbed.Slabbed;
import com.slabbed.anchor.ClientRenderDyPrediction;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.compat.CompatHooks;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.slabbed.compat.CompatSlabSurfaceKind;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
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
import net.minecraft.world.level.block.state.properties.BellAttachType;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.function.Predicate;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelReader;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Central helper for slab support semantics.
 */
public final class SlabSupport {
    private static final String BOTTOM_PERSISTENT_TRACE_OPT_IN = "slabbed.bottomPersistentTrace";

    // Rule 19 (RULES.md §19): read ONCE at class-load, not per call. isBottomPersistentTracePos
    // sits on the dy hot path (getYOffset early-guard + two getYOffsetInner sites), so a live
    // Boolean.getBoolean locked the system-properties table on every BOTTOM-slab dy query.
    // Caching is exact because nothing setPropertys this flag at runtime (verified: the only
    // runtime setProperty calls in the tree target slabbed.render.offset.trace /
    // slabbed.target.trace, both in client-gametest sources that are NOT in the compiled
    // gametest include list). RE-INCLUSION TRAP: if a test that setPropertys
    // "slabbed.bottomPersistentTrace" is ever added to sourceSets.gametest in build.gradle,
    // this cache goes stale-false and the trace silently dies — re-audit cache-vs-live per
    // RULES.md §19 before adding such a test. Enable via -D at JVM launch only.
    private static final boolean BOTTOM_PERSISTENT_TRACE =
            Boolean.getBoolean(BOTTOM_PERSISTENT_TRACE_OPT_IN);

    private SlabSupport() {
    }

    /** Returns true for non-structural surface layers that must not author placement height. */
    public static boolean isThinTopLayer(BlockState state) {
        return state != null
                && (state.hasProperty(SnowLayerBlock.LAYERS)
                        || !hasExplicitAttachmentRoleProperty(state)
                                && hasThinFloorLayerGeometry(state));
    }

    private static boolean hasExplicitAttachmentRoleProperty(BlockState state) {
        return state.hasProperty(BlockStateProperties.HANGING)
                || state.hasProperty(BlockStateProperties.ATTACH_FACE)
                || state.hasProperty(BlockStateProperties.BELL_ATTACHMENT);
    }

    /**
     * True when a thin, floor-owned state follows the support face instead of owning a
     * permanent placement height. Geometry supplies the role; block identity does not.
     */
    public static boolean isSupportFollowingFloorLayer(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        // Snow layers are floor followers at EVERY layer count: the lane keys on the LAYERS
        // property (thick snow outgrows the thin-geometry bound), per the no-exceptions
        // lowering law (maintainer ruling, 2026-08-06; adopted 2026-08-16) — the old explicit
        // snow exclusion here left snow with no lane at all and structurally pinned it to 0.0.
        if (world == null || pos == null || state == null || state.isAir()
                || !state.getFluidState().isEmpty()
                || hasHorizontalConnectionGeometry(state)
                || !(state.hasProperty(SnowLayerBlock.LAYERS)
                        || hasThinFloorLayerGeometry(state))) {
            return false;
        }
        AttachmentRole role = attachmentRole(world, pos, state);
        return role == AttachmentRole.NONE;
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

    /** Bottom slab surfaces that placement support must not turn into mob-spawn floors. */
    public static boolean isSpawnProofBottomSlabSurface(BlockState state) {
        if (CompatHooks.customSlabSurfaceKind(state) == CompatSlabSurfaceKind.BOTTOM_LIKE) {
            // The direct-seat lane makes the surface support-capable, so it must also stay
            // spawn-proof, same as the vanilla bottom slabs it now behaves like.
            return true;
        }
        return state != null
                && state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /** True if this state is a top slab. */
    public static boolean isTopSlab(BlockState state) {
        return isSupportingSlab(state) && state.getValue(SlabBlock.TYPE) == SlabType.TOP;
    }

    /**
     * The SUPPORT question, kept geometric per LAW.md clause 2: a slab holds up what stands on it,
     * and holds up what hangs beneath it, because of where its faces are - never because of who
     * registered it or how that registration is spelled. Bound to the vanilla slabs tag
     * (maintainer ruling, 2026-08-24), the same opt-in the direct seat uses, so a mod that tags
     * its slabs is admitted without any name appearing in this repo.
     *
     * <p>Deliberately NOT routed through {@link #isSupportingSlab}: that predicate carries the
     * compat gate the OFFSET lane needs, where an unauthored compat surface must stay flush or
     * generated ground tears open. Support and lowering are different questions and they answer
     * differently here. Do not re-unify them.
     */
    public static boolean isSupportFaceBottomSlab(BlockState state) {
        return isTaggedSlab(state) && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /** Underside twin of {@link #isSupportFaceBottomSlab}; same geometric scope, same reasons. */
    public static boolean isSupportFaceTopSlab(BlockState state) {
        return isTaggedSlab(state) && state.getValue(SlabBlock.TYPE) == SlabType.TOP;
    }

    /**
     * True when this state's underside sits half a block above its own cell floor, which is the
     * whole of what the ceiling-tracking merge compensation needs to know.
     *
     * <p>Shape only - no tag, no namespace, no support-admission gate. A slab's underside is where
     * it is whoever registered the block, and keying the ARITHMETIC on an admission predicate is
     * how a converted gate still pays out the wrong number: the ceiling becomes trackable and then
     * gets tracked half a block low. Admission is decided by the caller; this answers geometry.
     */
    private static boolean presentsTopSlabUnderside(BlockState state) {
        return state != null
                && state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.TOP;
    }

    /**
     * Single source of truth: returns true iff the state is a TOP slab
     * and the queried face is DOWN (i.e. the underside of a top slab).
     *
     * <p>Vanilla reads this face to decide whether anything may hang beneath the block - a
     * stalactite's tip direction and a hanging sign's survival both come through here - so it is
     * an attachment question and takes the geometric predicate.
     */
    public static boolean isTopSlabUndersideSupport(BlockState state, Direction face) {
        return face == Direction.DOWN && isSupportFaceTopSlab(state);
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

    /**
     * The bridge treatment exists only for a FLUSH gap: with a lowered cap the normal chain box
     * moved to the merge dy already meets the cap underside, so extending the selection to the
     * flush span doubles the box and splits the visual triad (maintainer ruling, 2026-08-17).
     */
    public static boolean isFlushCeilingBridgedVerticalChain(
            BlockGetter world, BlockPos pos, BlockState state) {
        try {
            return isFlushCeilingBridgedVerticalChainWithinRegion(world, pos, state);
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            // Bridge geometry needs evidence this region cannot see; claim none.
            if (isRenderRegionView(world)) {
                return false;
            }
            throw outsideRenderRegion;
        }
    }

    private static boolean isFlushCeilingBridgedVerticalChainWithinRegion(
            BlockGetter world, BlockPos pos, BlockState state) {
        if (!isVerticalChainDirectlyUnderCeilingSupport(world, pos, state)) {
            return false;
        }
        double mergeDy = ceilingBridgedVerticalChainColumnMergeDy(world, pos, state);
        return Double.isFinite(mergeDy) && Math.abs(mergeDy) <= 1.0e-6d;
    }

    public static VoxelShape ceilingBridgedVerticalChainSelectionShape(
            BlockGetter world,
            BlockPos pos,
            BlockState state,
            VoxelShape fallback
    ) {
        VoxelShape base = fallback == null ? Shapes.empty() : fallback;
        if (!isFlushCeilingBridgedVerticalChain(world, pos, state)) {
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

    /**
     * Column-wide dy for a ceiling-bridged vertical chain column, or NaN when {@code (pos, state)}
     * is not a member ({@link #isCeilingBridgedVerticalChainColumnMember} owns the membership
     * question). Every member — and the hung addendum directly below the column — must read this
     * ONE value (maintainer ruling, 2026-08-16): 0.0 while the cap holds the bridge flush (flush
     * TOP slab, net-zero -0.5 TOP slab, DOUBLE slab), and the direct lane's merge compensation
     * ({@code capDy + 0.5}, netting below 0.0) once a marked TOP-slab cap lowers past net-zero.
     * A split column (merged top segment over grid-height descendants) overlaps the segment
     * models. Grid-height bridge treatment belongs to a flush-hanging column only — the same
     * dy-exactly-0.0 gate the 26.2 donor applies to its bridge geometry.
     */
    private static double ceilingBridgedVerticalChainColumnMergeDy(
            BlockGetter world, BlockPos pos, BlockState state
    ) {
        if (!isCeilingBridgedVerticalChainColumnMember(world, pos, state)) {
            return Double.NaN;
        }
        BlockPos cursor = pos;
        BlockState cur = state;
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            if (isVerticalChainDirectlyUnderCeilingSupport(world, cursor, cur)) {
                BlockPos capPos = cursor.above();
                BlockState cap = world.getBlockState(capPos);
                // Mirrors the walk-B direct lane exactly: a Terrain-Slabs-owned top slab is a
                // SELF-RENDERING surface whose recursion-visible dy must never feed the column.
                if (isTopSlab(cap) && !CompatHooks.shouldSkipOffset(cap)) {
                    double capDy = storedOwnerOrLegacyInnerYOffset(world, capPos, cap);
                    if (capDy < -1.0e-6d) {
                        return capDy + 0.5d;
                    }
                }
                return 0.0d;
            }
            cursor = cursor.above();
            cur = world.getBlockState(cursor);
        }
        return 0.0d;
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

    private enum AttachmentRole {
        NONE,
        FLOOR,
        SIDE,
        CEILING
    }

    /** True when the block at this position derives its height from a real support above. */
    public static boolean isDynamicCeilingFollower(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        return attachmentRole(world, pos, state) == AttachmentRole.CEILING;
    }

    private static AttachmentRole attachmentRole(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        if (world == null || pos == null || state == null) {
            return AttachmentRole.NONE;
        }
        if (state.hasProperty(BlockStateProperties.HANGING)) {
            return state.getValue(BlockStateProperties.HANGING)
                    ? AttachmentRole.CEILING
                    : AttachmentRole.FLOOR;
        }
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            return switch (state.getValue(BlockStateProperties.ATTACH_FACE)) {
                case FLOOR -> AttachmentRole.FLOOR;
                case WALL -> AttachmentRole.SIDE;
                case CEILING -> AttachmentRole.CEILING;
            };
        }
        if (state.hasProperty(BlockStateProperties.BELL_ATTACHMENT)) {
            return switch (state.getValue(BlockStateProperties.BELL_ATTACHMENT)) {
                case FLOOR -> AttachmentRole.FLOOR;
                case CEILING -> AttachmentRole.CEILING;
                case SINGLE_WALL, DOUBLE_WALL -> AttachmentRole.SIDE;
            };
        }
        if (isDownwardPointedDripstone(state)) {
            return AttachmentRole.CEILING;
        }
        if (isBeta35UpwardPointedDripstoneVisibleOwnerObject(state)) {
            return AttachmentRole.FLOOR;
        }
        if (state.getBlock() instanceof WallHangingSignBlock) {
            return AttachmentRole.SIDE;
        }
        if (isAlwaysCeilingHungDecoration(state)
                || state.getBlock() instanceof CaveVinesBlock
                || state.getBlock() instanceof CaveVinesPlantBlock) {
            return AttachmentRole.CEILING;
        }
        if (state.getBlock() instanceof TrapDoorBlock
                && state.hasProperty(BlockStateProperties.HALF)
                && state.getValue(BlockStateProperties.HALF) == Half.TOP) {
            return hasActualCeilingSupport(world, pos.above())
                    ? AttachmentRole.CEILING
                    : AttachmentRole.SIDE;
        }
        if (isBeta35VerticalChainVisibleOwnerObject(state)) {
            return verticalChainHasActualCeilingSupport(world, pos)
                    ? AttachmentRole.CEILING
                    : AttachmentRole.NONE;
        }
        return AttachmentRole.NONE;
    }

    private static boolean verticalChainHasActualCeilingSupport(BlockGetter world, BlockPos pos) {
        BlockPos cursor = pos.above();
        for (int depth = 0; depth < MAX_CHAIN_DEPTH; depth++) {
            BlockState state = world.getBlockState(cursor);
            if (isBeta35VerticalChainVisibleOwnerObject(state)) {
                cursor = cursor.above();
                continue;
            }
            return hasActualCeilingSupport(world, cursor);
        }
        return false;
    }

    private static boolean hasActualCeilingSupport(BlockGetter world, BlockPos pos) {
        if (IN_CEILING_CAP_PROBE.get()) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (state == null || state.isAir()) {
            return false;
        }

        boolean outerRawShapeProbe = IN_RAW_SHAPE_PROBE.get();
        IN_CEILING_CAP_PROBE.set(Boolean.TRUE);
        IN_RAW_SHAPE_PROBE.set(Boolean.TRUE);
        try {
            return isCeilingSupportBottomSurface(world, pos)
                    || state.isFaceSturdy(world, pos, Direction.DOWN, SupportType.CENTER);
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            IN_RAW_SHAPE_PROBE.set(outerRawShapeProbe);
            IN_CEILING_CAP_PROBE.set(Boolean.FALSE);
        }
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
            if (cur.isAir() || cur.getBlock() instanceof SlabBlock
                    || isDynamicCeilingFollower(world, cursor, cur)
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
     * A top-like ceiling surface that Slabbed itself lowers — one a ceiling-attached block
     * below would follow UP by +0.5 (raised-attach). The ONE shared choke point for EVERY
     * dy-computing ceiling walk (the {@link #ceilingHungDecorationDy} cursor loop and both
     * {@code getYOffsetInner} walks), so the ruling can never be applied to one walk and
     * forgotten on the others.
     *
     * <p>DEPRECATED (maintainer ruling, 2026-07-03; adopted on this line 2026-08-16): the
     * +0.5 "reach-up" for ceiling-attached objects (lantern / dripstone / chain / lever /
     * TOP-trapdoor / ...) under a FLUSH top slab is deprecated — everything hangs FLUSH. In
     * live testing the reach-up smooshed those objects UP into the slab. Returning false
     * disables the +0.5 at all three ceiling walks from one place, so the ruling is trivially
     * reversible if it regresses. The {@code slabDy + 0.5} merge COMPENSATION for a LOWERED
     * top slab is a DIFFERENT path — it nets &lt;= 0.0 (flush against the lowered underside),
     * not a reach-up — and deliberately stays.
     */
    private static boolean isLoweringTopLikeCeiling(BlockState state) {
        return false;
    }

    /**
     * Dy for objects whose only legal support is the block above them. A hanging
     * lantern below a ceiling-bridged chain is not another chain segment; it hangs
     * as an addendum below the column at the COLUMN's own dy
     * ({@link #ceilingBridgedVerticalChainColumnMergeDy}) — grid height while the
     * bridge is flush, the shared merge value once the cap lowers past net-zero.
     * Under a FLUSH top slab the decoration hangs flush per the ruling recorded on
     * {@link #isLoweringTopLikeCeiling}; under a LOWERED top slab it follows the
     * slab with the +0.5 merge compensation.
     */
    private static double ceilingHungDecorationDy(BlockGetter world, BlockPos pos, BlockState state) {
        BlockPos supportPos = pos.above();
        BlockState above = world.getBlockState(supportPos);
        double supportColumnDy = ceilingBridgedVerticalChainColumnMergeDy(world, supportPos, above);
        if (!Double.isNaN(supportColumnDy)) {
            return supportColumnDy;
        }
        if (isDownwardPointedDripstone(state)
                && downwardPointedDripstoneColumnRootsThroughTopSlabChain(world, supportPos, above)) {
            return 0.0d;
        }
        // AUTHORSHIP, not namespace, decides whether a compat ceiling may be tracked. An
        // unauthored compat surface renders itself and stays flush, so it carries nothing
        // anywhere and must stay out of this leg; an authored one holds a height, and whatever
        // hangs from it owes that height or it hangs INSIDE the surface it hangs from. The
        // namespace form here refused both alike and left the hanger at grid height.
        if (!above.isAir()
                && !isDynamicCeilingFollower(world, supportPos, above)
                && !isUnauthoredCompatCell(world, supportPos, above)) {
            double supportDy = storedOwnerOrLegacyInnerYOffset(world, supportPos, above);
            if (supportDy < -1.0e-6d) {
                return presentsTopSlabUnderside(above) ? supportDy + 0.5d : supportDy;
            }
        }
        BlockPos cursor = supportPos;
        for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
            BlockState cur = world.getBlockState(cursor);
            double cursorColumnDy = ceilingBridgedVerticalChainColumnMergeDy(world, cursor, cur);
            if (!Double.isNaN(cursorColumnDy)) {
                return cursorColumnDy;
            }
            // Flush ruling: dead while isLoweringTopLikeCeiling returns false (was isTopSlab(cur)).
            if (isLoweringTopLikeCeiling(cur)) {
                return 0.5d;
            }
            if (isDynamicCeilingFollower(world, cursor, cur)) {
                cursor = cursor.above();
                continue;
            }
            // Same authorship rule as the direct leg. Keep the two in step: a cascade that
            // stopped at a compat column top flattened the whole column below it, and a flat
            // column then reads as a flat surface to everything placed against it.
            if (!cur.isAir() && !isUnauthoredCompatCell(world, cursor, cur)) {
                double supportDy = storedOwnerOrLegacyInnerYOffset(world, cursor, cur);
                if (supportDy < -1.0e-6d) {
                    // Mirror the direct leg above: a lowered TOP-slab column top needs the +0.5
                    // merge compensation or a cascaded hanger sinks 0.5 BELOW its own carrier.
                    // Before the flush ruling the reach-up return shadowed this tail for every
                    // top-slab column top, so the missing compensation was unreachable.
                    return presentsTopSlabUnderside(cur) ? supportDy + 0.5d : supportDy;
                }
            }
            break;
        }
        return 0.0d;
    }

    private static double storedOwnerOrLegacyInnerYOffset(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        if (!isDynamicCeilingFollower(world, pos, state)) {
            double stored = SlabPlacementHeightAttachment.storedOffset(world, pos);
            if (Double.isFinite(stored)) {
                return stored;
            }
        }
        return getYOffsetInner(world, pos, state);
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
        if (hasHorizontalConnectionGeometry(state)
                && hasHorizontalConnectionGeometry(neighborState)) {
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
        double stored = SlabPlacementHeightAttachment.storedOffset(world, pos);
        if (Double.isFinite(stored)) {
            return stored;
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
        double stored = SlabPlacementHeightAttachment.storedOffset(world, pos);
        if (Double.isFinite(stored)) {
            return stored;
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

    /**
     * Resolves an unauthored structural subject from the top face directly below it.
     * Authored anchors and frozen-flat markers are handled before this fallback.
     */
    private static double factlessSupportSeatFollowerDy(
            BlockGetter world,
            BlockPos pos,
            BlockState state,
            int depth
    ) {
        boolean supportSeatSubject = world != null && pos != null && state != null
                && isFactlessSupportSeatSubject(world, pos, state);
        return factlessSupportSeatFollowerDy(world, pos, state, depth, supportSeatSubject);
    }

    private static double factlessSupportSeatFollowerDy(
            BlockGetter world,
            BlockPos pos,
            BlockState state,
            int depth,
            boolean supportSeatSubject
    ) {
        if (world == null || pos == null || state == null
                || depth > supportResolveDepthLimit()
                || SlabAnchorAttachment.isAnchored(world, pos)
                || SlabAnchorAttachment.isFrozenFlat(world, pos)
                || state.getBlock() instanceof SlabBlock
                        && SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
                || !supportSeatSubject) {
            return Double.NaN;
        }
        if (SUPPORT_RESOLUTION_CONTEXT.get().contains(pos)) {
            return Double.NaN;
        }

        double seatDy = supportTopFaceYOffset(world, pos.below(), depth);
        return supportRelativeFollowerDy(state, seatDy);
    }

    /**
     * The height to freeze for a block being placed right now.
     *
     * <p>Two questions, two answers. The lanes decide WHETHER this block lowers - that is where
     * the world-hole pin, the compat deferrals and every stay-flat rule live, and none of them
     * move here. This decides HOW FAR, and only for a block those lanes already chose to lower:
     * a lowered block rests on the surface beneath it, so it takes that surface's face rather
     * than a constant that is only correct while the support sits half a block down.
     *
     * <p>Deepen-only, and only from an already-lowered answer. A flush block stays flush, an
     * unknown seat keeps the lane's number, and no shallower reading can be produced here.
     *
     * <p>This is deliberately a PLACEMENT-time question. Resolving it live would move blocks in
     * saves that predate the placement store, whose anchors record that they are lowered but not
     * by how much; those keep their historical height forever, which is what LAW 1 promises them.
     */
    /**
     * The height of the column a block is being added to, when it is being added to one.
     *
     * <p>Stacking another of the same block directly on top is not a subject seating on a
     * surface - the two pieces are one object, contiguous by their own geometry, so the new
     * piece takes the height the column already has rather than deriving a fresh seat from the
     * piece below. Deriving instead reads the lower piece's own outline top, which for anything
     * that is not a full cube is somewhere inside its cell, and the column splits.
     *
     * <p>Slabs are excluded and must stay excluded: they are seat-followers, and a slab stacked
     * on a slab genuinely does seat on the face of the one below rather than continuing it. That
     * is the same carve-out the placement admission already makes for this shape.
     */
    private static double sameBlockColumnDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null
                || state.getBlock() instanceof SlabBlock) {
            return Double.NaN;
        }
        BlockPos belowPos = pos.below();
        BlockState below = world.getBlockState(belowPos);
        if (below == null || !below.is(state.getBlock())) {
            return Double.NaN;
        }
        double belowDy = getYOffset(world, belowPos, below);
        return Double.isFinite(belowDy) && belowDy < -1.0e-6d ? belowDy : Double.NaN;
    }

    /**
     * A slab-shaped state Slabbed may reason about, decided by geometry and never by who
     * registered it (LAW.md clause 2). Membership of the vanilla slabs tag is the opt-in: a mod
     * that adds its slabs to that tag is telling the game they are slabs, and one that does not
     * is opting out without needing a name here.
     */
    /**
     * A compat-owned cell this mod did not author. Authorship is OUR durable record - a stored
     * placement fact, an anchor, or a flat stamp - which world generation cannot forge, since
     * only a placement transaction writes one. Never derive this from the compat mod's own state:
     * its worldgen features rebuild slabs from a default, its grass resets the lookalike flag on
     * random tick, and a DOUBLE merge inherits it (the refuted {@code generated} family).
     */
    private static boolean isUnauthoredCompatCell(BlockGetter world, BlockPos pos, BlockState state) {
        return CompatHooks.shouldSkipOffset(state)
                && !(Double.isFinite(SlabPlacementHeightAttachment.storedOffset(world, pos))
                        || SlabAnchorAttachment.isAnchored(world, pos)
                        || SlabAnchorAttachment.isFrozenFlat(world, pos));
    }

    public static boolean isTaggedSlab(BlockState state) {
        if (state == null
                || !(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(SlabBlock.TYPE)) {
            return false;
        }
        try {
            return state.is(BlockTags.SLABS);
        } catch (IllegalStateException tagsNotBoundYet) {
            // A read can reach here during bootstrap, before tags bind. Declining is the
            // conservative answer: the subject keeps the height it had before this predicate
            // existed. Same guard shape as the log-family candidate above.
            if ("Tags not bound".equals(tagsNotBoundYet.getMessage())) {
                return false;
            }
            throw tagsNotBoundYet;
        }
    }

    /**
     * Seat derivation for a caller that has already established the gesture is a SEAT — the
     * placed block resting on the cell below, which is the thing that was clicked.
     *
     * <p>Named for its assumption rather than left as a bare overload, because the face-blind
     * SHAPE is the defect: asking "rest this on what is below" without knowing whether the player
     * pointed at what is below is precisely how a cantilever ended up a full block under the face
     * it was hung from. A future caller reaching this from a side-click path would reinstate that
     * with no compiler complaint, so the assumption is in the name where it has to be typed out.
     * Anything holding a placement context must call
     * {@link #placementSeatDy(BlockGetter, BlockPos, BlockState, Direction)} instead.
     */
    public static double placementSeatDyForSeatGesture(
            BlockGetter world, BlockPos pos, BlockState state) {
        return placementSeatDy(world, pos, state, null);
    }

    /**
     * As above, told which face the player clicked.
     *
     * <p>WYSIWYG (maintainer ruling, 2026-08-29): the block lands on the face that was pointed at.
     * A cantilever hung off a FLUSH horizontal face therefore lands flush, and nothing under the
     * landing cell may pull it down — that is scenery the aim never named.
     *
     * <p>The server reaches the same answer through the FROZEN_FLAT stamp its on-place writer
     * lays down, but that writer never runs on the client, so without this the client predicted
     * the resolver's below-derived height, drew it, and then jumped when the server's stamp
     * synced. Both sides must derive the same number from the same gesture or the placement
     * snaps in front of the player (live, 2026-08-29). This is the client's half of that
     * agreement, and it must stay in step with
     * {@code SlabAnchorAttachment.markWysiwygFlatClickedFlushFace}.
     *
     * <p>Restricted to a flush clicked owner: a side placement beside a LOWERED or compound owner
     * genuinely inherits that owner's height — the same law, read off a lowered face — and those
     * lanes route here deliberately. Widening past that reddens four such pins at once.
     */
    public static double placementSeatDy(
            BlockGetter world, BlockPos pos, BlockState state, Direction clickedFace) {
        if (clickedFace != null && clickedFace.getAxis().isHorizontal()) {
            BlockPos ownerPos = pos.relative(clickedFace.getOpposite());
            BlockState owner = world.getBlockState(ownerPos);
            if (!owner.isAir() && Math.abs(getYOffset(world, ownerPos, owner)) <= 1.0e-6d) {
                return 0.0d;
            }
        }
        double columnDy = sameBlockColumnDy(world, pos, state);
        if (Double.isFinite(columnDy)) {
            return columnDy;
        }
        double resolved = getUnstoredYOffset(world, pos, state);
        // A compat slab being AUTHORED must not stop at the resolver's flush answer: that answer
        // is the unauthored-surface law, and this call is the one moment a compat slab is not yet
        // unauthored. Fall through to the seat derivation a vanilla slab would use. Every other
        // subject still short-circuits here, which keeps the seat walk off the common path.
        // (This bypass was once removed as unexercised; the missing case was a FLUSH support,
        // where the aim lane has no opinion and this derivation is the only author. Found live:
        // the placed compat slab floated half a block above the flush slab it was placed on.)
        boolean authoringCompatSlab =
                CompatHooks.shouldSkipOffset(state) && isTaggedSlab(state);
        if (!authoringCompatSlab && !(resolved < -1.0e-6d)) {
            return resolved;
        }
        double seat = supportRelativeFollowerDy(
                state, supportTopFaceYOffset(world, pos.below(), 0));
        if (Double.isFinite(seat) && seat < resolved - 1.0e-6d) {
            return Math.max(seat, minResolvedDy());
        }
        return resolved;
    }

    /**
     * The height a subject takes when the cell below it is a lowered bottom slab.
     *
     * <p>Half a block down is that slab's visible top face only while the slab itself sits half a
     * block down: one half step for the slab's own drop and one for its half height. A slab
     * lowered deeper presents a deeper face, and a subject that keeps answering the constant
     * floats above its own support with a visible gap under it.
     *
     * <p>Downward-only from the historical constant. An unreadable seat, or one no deeper than
     * the constant, keeps that constant exactly - which is also what preserves an authored
     * compound height after its source slab is gone, where there is no seat left to read.
     */
    private static double compoundLoweredSupportSeatDy(BlockGetter world, BlockPos pos) {
        double seat = supportTopFaceYOffset(world, pos.below(), 0);
        if (Double.isFinite(seat) && seat < -1.0d - 1.0e-6d) {
            return Math.max(seat, minResolvedDy());
        }
        return -1.0d;
    }

    private static double supportRelativeFollowerDy(BlockState state, double seatDy) {
        if (!Double.isFinite(seatDy)) {
            return Double.NaN;
        }
        double rawBottom = rawOutlineMinY(state);
        if (!Double.isFinite(rawBottom)) {
            return Double.NaN;
        }
        // Flush contact follows the support's real surface (maintainer ruling, 2026-08-17),
        // and DERIVED descent stops at the resolved floor (maintainer ruling, 2026-08-21,
        // matching the reference line): a mixed slab/block column otherwise descends a half
        // block per slab course toward the envelope, which in play reads as each placement
        // snapping down out from under its preview. Consent deepens the floor to the envelope.
        return Math.max(seatDy - rawBottom, minResolvedDy());
    }

    /**
     * Resolves known floor-contact decorations and block entities from the exact support face.
     * Attachment-facing and multi-cell states keep their existing role-specific paths.
     */
    private static double factlessGeometricFloorFollowerDy(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        AttachmentRole role = attachmentRole(world, pos, state);
        if (world == null || pos == null || state == null
                || state.isAir()
                || !state.getFluidState().isEmpty()
                || state.hasProperty(SlabBlock.TYPE)
                || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || isThinTopLayer(state)
                || state.getBlock() instanceof PowderSnowBlock
                || CompatHooks.shouldSkipOffset(state)
                || role == AttachmentRole.CEILING
                || role == AttachmentRole.SIDE
                || SlabAnchorAttachment.isAnchored(world, pos)
                || SlabAnchorAttachment.isFrozenFlat(world, pos)
                || hasHorizontalConnectionGeometry(state)
                || !rawOutlineTouchesCellFloor(state)) {
            return Double.NaN;
        }
        double follower = getSupportFollowerYOffset(world, pos.below());
        // A bed is one rigid body over two cells and both halves read ONE value (P26 resting-dy
        // ruling): the bed follows its deepest resolved support, and a half whose own support is
        // unclassified inherits its partner's value. Each half resolves independently at read
        // time, so the pairing is folded into the resolution itself, not a call site.
        if (state.hasProperty(BlockStateProperties.BED_PART)) {
            Direction toOther = net.minecraft.world.level.block.BedBlock.getConnectedDirection(state);
            double other = getSupportFollowerYOffset(world, pos.relative(toOther).below());
            boolean ownFinite = Double.isFinite(follower);
            boolean otherFinite = Double.isFinite(other);
            if (ownFinite && otherFinite) {
                return Math.min(follower, other);
            }
            if (ownFinite) {
                return follower;
            }
            if (otherFinite) {
                return other;
            }
            return Double.NaN;
        }
        return follower;
    }

    /** Connection-state ownership is settled separately from support-relative floor following. */
    public static boolean hasHorizontalConnectionGeometry(BlockState state) {
        if (state == null) {
            return false;
        }
        boolean booleanConnections = state.hasProperty(BlockStateProperties.NORTH)
                && state.hasProperty(BlockStateProperties.EAST)
                && state.hasProperty(BlockStateProperties.SOUTH)
                && state.hasProperty(BlockStateProperties.WEST);
        boolean wallConnections = state.hasProperty(BlockStateProperties.NORTH_WALL)
                && state.hasProperty(BlockStateProperties.EAST_WALL)
                && state.hasProperty(BlockStateProperties.SOUTH_WALL)
                && state.hasProperty(BlockStateProperties.WEST_WALL);
        return booleanConnections || wallConnections;
    }

    private static final ThreadLocal<Boolean> IN_RAW_SHAPE_PROBE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> IN_CEILING_CAP_PROBE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final int RAW_PRESENT = 0;
    private static final int RAW_MIN_X = 1;
    private static final int RAW_MIN_Y = 2;
    private static final int RAW_MIN_Z = 3;
    private static final int RAW_MAX_X = 4;
    private static final int RAW_MAX_Y = 5;
    private static final int RAW_MAX_Z = 6;
    private static final double[] EMPTY_RAW_OUTLINE_METRICS = {
            0.0d,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            Double.NaN
    };
    private static final ConcurrentHashMap<BlockState, double[]> RAW_OUTLINE_METRICS =
            new ConcurrentHashMap<>();

    /** True only while the resolver reads a state's own unshifted outline geometry. */
    public static boolean isRawShapeProbeActive() {
        return IN_RAW_SHAPE_PROBE.get();
    }

    private static boolean hasThinFloorLayerGeometry(BlockState state) {
        double[] metrics = rawOutlineMetrics(state);
        return metrics[RAW_PRESENT] == 1.0d
                && Math.abs(metrics[RAW_MIN_Y]) <= 1.0e-6d
                && metrics[RAW_MAX_Y] > 1.0e-6d
                && metrics[RAW_MAX_Y] <= 0.125d + 1.0e-6d;
    }

    private static boolean rawOutlineTouchesCellFloor(BlockState state) {
        double[] metrics = rawOutlineMetrics(state);
        return metrics[RAW_PRESENT] == 1.0d && Math.abs(metrics[RAW_MIN_Y]) <= 1.0e-6d;
    }

    private static double rawOutlineMinY(BlockState state) {
        double[] metrics = rawOutlineMetrics(state);
        return metrics[RAW_PRESENT] == 1.0d ? metrics[RAW_MIN_Y] : Double.NaN;
    }

    private static double rawOutlineMaxY(BlockState state) {
        double[] metrics = rawOutlineMetrics(state);
        return metrics[RAW_PRESENT] == 1.0d ? metrics[RAW_MAX_Y] : Double.NaN;
    }

    private static double rawFullFootprintSupportTop(BlockState state) {
        double[] metrics = rawOutlineMetrics(state);
        if (metrics[RAW_PRESENT] != 1.0d
                || Math.abs(metrics[RAW_MIN_Y]) > 1.0e-6d
                || metrics[RAW_MAX_Y] <= 1.0e-6d
                || metrics[RAW_MAX_Y] > 1.0d + 1.0e-6d
                || Math.abs(metrics[RAW_MIN_X]) > 1.0e-6d
                || Math.abs(metrics[RAW_MIN_Z]) > 1.0e-6d
                || Math.abs(metrics[RAW_MAX_X] - 1.0d) > 1.0e-6d
                || Math.abs(metrics[RAW_MAX_Z] - 1.0d) > 1.0e-6d) {
            return Double.NaN;
        }
        return metrics[RAW_MAX_Y];
    }

    private static double[] rawOutlineMetrics(BlockState state) {
        if (state == null) {
            return EMPTY_RAW_OUTLINE_METRICS;
        }
        double[] cached = RAW_OUTLINE_METRICS.get(state);
        if (cached != null) {
            return cached;
        }
        boolean outerProbe = IN_RAW_SHAPE_PROBE.get();
        IN_RAW_SHAPE_PROBE.set(Boolean.TRUE);
        double[] metrics;
        try {
            VoxelShape outline = state.getShape(
                    EmptyBlockGetter.INSTANCE,
                    BlockPos.ZERO,
                    CollisionContext.empty());
            metrics = outline == null || outline.isEmpty()
                    ? EMPTY_RAW_OUTLINE_METRICS
                    : new double[] {
                            1.0d,
                            outline.min(Direction.Axis.X),
                            outline.min(Direction.Axis.Y),
                            outline.min(Direction.Axis.Z),
                            outline.max(Direction.Axis.X),
                            outline.max(Direction.Axis.Y),
                            outline.max(Direction.Axis.Z)
                    };
        } catch (RuntimeException ignored) {
            metrics = EMPTY_RAW_OUTLINE_METRICS;
        } finally {
            IN_RAW_SHAPE_PROBE.set(outerProbe);
        }
        double[] previous = RAW_OUTLINE_METRICS.putIfAbsent(state, metrics);
        return previous == null ? metrics : previous;
    }

    /**
     * Returns the resolved top-face offset of an immediate slab or structural support.
     * A missing or unclassified support returns {@link Double#NaN}; explicit zero remains zero.
     */
    public static double getSupportTopFaceYOffset(BlockGetter world, BlockPos supportPos) {
        return supportTopFaceYOffset(world, supportPos, 0);
    }

    /**
     * Returns a support-relative follower offset flush on the resolved support surface,
     * bounded by the targetable envelope (the depth the collision and targeting windows cover).
     */
    public static double getSupportFollowerYOffset(BlockGetter world, BlockPos supportPos) {
        double topFaceDy = getSupportTopFaceYOffset(world, supportPos);
        return Double.isFinite(topFaceDy)
                ? Math.max(topFaceDy, minResolvedDy())
                : Double.NaN;
    }

    private static double supportTopFaceYOffset(BlockGetter world, BlockPos supportPos, int depth) {
        SupportResolutionContext context = SUPPORT_RESOLUTION_CONTEXT.get();
        if (world == null || supportPos == null
                || depth > supportResolveDepthLimit()) {
            return context.answer(Double.NaN, SUPPORT_RESULT_UNKNOWN);
        }
        if (!context.enter(supportPos)) {
            return context.answer(Double.NaN, SUPPORT_RESULT_UNKNOWN);
        }
        try {
            return supportSeatDy(world, supportPos, depth, context);
        } finally {
            context.leave();
        }
    }

    private static boolean isFactlessSupportSeatSubject(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        if (state.getBlock() instanceof SlabBlock) {
            return state.hasProperty(SlabBlock.TYPE) && state.getFluidState().isEmpty();
        }
        return isStrictFullStructuralState(world, pos, state);
    }

    private static boolean isStrictFullStructuralState(
            BlockGetter world,
            BlockPos pos,
            BlockState state
    ) {
        if (world == null || pos == null || state == null || state.isAir()
                || !state.getFluidState().isEmpty()
                || state.getBlock() instanceof SlabBlock
                || state.getBlock() instanceof PowderSnowBlock
                || state.hasProperty(BlockStateProperties.BED_PART)
                || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || isThinTopLayer(state)
                || attachmentRole(world, pos, state) != AttachmentRole.NONE) {
            return false;
        }
        return state.isSolidRender(world, pos);
    }

    /**
     * Returns the subject offset implied by the support's current rendered top face.
     * Missing or unclassified supports return {@link Double#NaN}; a flush seat returns zero.
     */
    private static double supportSeatDy(
            BlockGetter world,
            BlockPos supportPos,
            int depth,
            SupportResolutionContext context
    ) {
        if (world == null || supportPos == null || depth > supportResolveDepthLimit()) {
            return context.answer(Double.NaN, SUPPORT_RESULT_UNKNOWN);
        }
        BlockState support = world.getBlockState(supportPos);
        // A compat cell declines the face read only while UNAUTHORED (maintainer ruling,
        // 2026-08-24: a placed compat slab supports its occupant exactly as a vanilla slab;
        // generated compat ground supports nothing - that half is L5's world-hole guard).
        if (support == null || support.isAir() || !support.getFluidState().isEmpty()
                || isUnauthoredCompatCell(world, supportPos, support)) {
            return context.answer(Double.NaN, SUPPORT_RESULT_UNKNOWN);
        }

        if (support.getBlock() instanceof SlabBlock && support.hasProperty(SlabBlock.TYPE)) {
            boolean authored = Double.isFinite(
                    SlabPlacementHeightAttachment.storedOffset(world, supportPos))
                    || SlabAnchorAttachment.isFrozenFlat(world, supportPos)
                    || SlabAnchorAttachment.isAnchored(world, supportPos);
            double ownDy = legacySlabOwnDy(world, supportPos, support);
            if (!Double.isFinite(ownDy)) {
                return context.answer(Double.NaN, SUPPORT_RESULT_UNKNOWN);
            }
            double rawTop = rawOutlineMaxY(support);
            if (!Double.isFinite(rawTop)) {
                return context.answer(Double.NaN, SUPPORT_RESULT_UNKNOWN);
            }
            return context.answer(
                    ownDy + rawTop - 1.0d,
                    authored ? SUPPORT_RESULT_AUTHORED : SUPPORT_RESULT_GEOMETRIC);
        }

        if (!isStrictFullStructuralState(world, supportPos, support)) {
            double rawTop = rawFullFootprintSupportTop(support);
            if (!Double.isFinite(rawTop)) {
                return context.answer(Double.NaN, SUPPORT_RESULT_UNKNOWN);
            }
            double storedDy = SlabPlacementHeightAttachment.storedOffset(world, supportPos);
            if (Double.isFinite(storedDy)) {
                return context.answer(storedDy + rawTop - 1.0d, SUPPORT_RESULT_AUTHORED);
            }
            return context.answer(rawTop - 1.0d, SUPPORT_RESULT_GEOMETRIC);
        }
        double storedDy = SlabPlacementHeightAttachment.storedOffset(world, supportPos);
        if (Double.isFinite(storedDy)) {
            return context.answer(storedDy, SUPPORT_RESULT_AUTHORED);
        }
        if (SlabAnchorAttachment.isAnchored(world, supportPos)) {
            double anchoredDy = loweredFullBlockUndersideSupportDy(world, supportPos, support);
            if (Double.isFinite(anchoredDy)) {
                return context.answer(anchoredDy, SUPPORT_RESULT_AUTHORED);
            }
        }
        if (SlabAnchorAttachment.isFrozenFlat(world, supportPos)) {
            return context.answer(0.0d, SUPPORT_RESULT_AUTHORED);
        }

        BlockPos belowSupportPos = supportPos.below();
        BlockState belowSupport = world.getBlockState(belowSupportPos);
        double inheritedDy = Double.NaN;
        int inheritedKind = SUPPORT_RESULT_UNKNOWN;
        if (depth < supportResolveDepthLimit()) {
            inheritedDy = supportRelativeFollowerDy(
                    support,
                    supportTopFaceYOffset(world, belowSupportPos, depth + 1));
            inheritedKind = context.lastResultKind();
            if (Double.isFinite(inheritedDy) && inheritedKind == SUPPORT_RESULT_AUTHORED) {
                return context.answer(inheritedDy, SUPPORT_RESULT_AUTHORED);
            }
        }

        double ownDy = loweredFullBlockUndersideSupportDy(world, supportPos, support);
        if (!Double.isFinite(ownDy)
                && belowSupport != null
                && belowSupport.isAir()
                && context.hasHorizontalNonAirNeighbor(world, supportPos)
                && isCantileverLoweredFullBlock(world, supportPos, support)) {
            ownDy = -0.5d;
        }
        if (Double.isFinite(ownDy)) {
            return context.answer(ownDy, SUPPORT_RESULT_GEOMETRIC);
        }
        if (Double.isFinite(inheritedDy)) {
            return context.answer(inheritedDy, inheritedKind);
        }
        if (belowSupport != null && !belowSupport.isAir()) {
            return context.answer(0.0d, SUPPORT_RESULT_INFERRED_FLAT);
        }
        return context.answer(Double.NaN, SUPPORT_RESULT_UNKNOWN);
    }

    /** Mirrors the existing authored and legacy slab ordering without entering the public resolver. */
    private static double legacySlabOwnDy(BlockGetter world, BlockPos pos, BlockState state) {
        double storedDy = SlabPlacementHeightAttachment.storedOffset(world, pos);
        if (Double.isFinite(storedDy)) {
            return storedDy;
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)) {
            return -1.0d;
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            if (world.getBlockState(pos.below()).isAir()
                    && !isCompoundVisibleOwnerTopSlab(world, pos, state)) {
                double sideDy = adjacentLoweredSideMagnitude(world, pos);
                if (sideDy < -0.5d - 1.0e-6d) {
                    return sideDy;
                }
            }
            return -0.5d;
        }
        if (SlabAnchorAttachment.isFrozenFlat(world, pos)) {
            return 0.0d;
        }
        if (state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                && SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state)) {
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
            // Flush door contact on the resolved support (maintainer ruling, 2026-08-17),
            // bounded by the resolved floor (maintainer ruling, 2026-08-21).
            return Math.max(supportDy - 0.5d, minResolvedDy());
        }
        // A door stands on whatever is under it, and until 2026-08-23 only a bottom slab counted:
        // on a lowered full block the lane declined and the door stayed at grid height while
        // everything beside it seated (maintainer ruling, 2026-08-23). Note the missing half
        // step - the branch above reads the slab's OWN height and subtracts the slab's half
        // height to reach its face, while this reads the face itself and must not subtract again.
        //
        // Only doors need this. The sibling contact lanes above look the same but their subjects
        // do hold a placement fact, which is already seat-derived; a door spans two cells and is
        // kept out of the store, so this live lane is the only thing that can seat it.
        double seatDy = getSupportTopFaceYOffset(world, supportPos);
        if (Double.isFinite(seatDy) && seatDy < -1.0e-6d) {
            return Math.max(seatDy, minResolvedDy());
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

    /** Upper bound on {@link #supportResolveDepthLimit()}, for fixed-size scratch storage. */
    private static final int SUPPORT_RESOLVE_DEPTH_CEILING =
            (int) Math.ceil(Math.abs(PlacementDepthPolicy.MIN_TARGETABLE_DY) / 0.5d) + 2;

    /**
     * The floor a factless derivation may reach.
     *
     * <p>A stacked slab course owes half a block of descent to the course below it, forever, so
     * the floor does not decide WHETHER a tall tower eventually stops - it decides at which
     * course it stops, and the course after that one carries the gap. Held at one block until
     * 2026-08-23, which stopped a tower at three flush courses; the maintainer ruling of that
     * date moved it to the full targetable envelope, then to three blocks (maintainer ruling,
     * 2026-08-23), which is seven flush courses.
     *
     * <p>It may not go deeper than the envelope. The pick window and the collision broadphase
     * are sized to that same value, so a height past it would render and then refuse to be
     * clicked or stood on. Both scale linearly with it: each are per-cell scans that run on
     * the picking and entity-movement paths, so depth is bought at a continuous cost.
     */
    private static final double SHIPPED_MIN_RESOLVED_DY = PlacementDepthPolicy.MIN_TARGETABLE_DY;
    public static final String DEEP_DY_ALPHABET_PROPERTY = "slabbed.deepDyAlphabet";
    public static final boolean DEEP_DY_ALPHABET =
            Boolean.parseBoolean(System.getProperty(DEEP_DY_ALPHABET_PROPERTY, "false"));
    private static volatile double minResolvedDy = capFor(false);
    private static final int SUPPORT_RESULT_UNKNOWN = 0;
    private static final int SUPPORT_RESULT_INFERRED_FLAT = 1;
    private static final int SUPPORT_RESULT_GEOMETRIC = 2;
    private static final int SUPPORT_RESULT_AUTHORED = 3;

    /** Reads the cached factless-resolution floor without touching save storage. */
    public static double minResolvedDy() {
        return minResolvedDy;
    }

    /**
     * The floor, which no longer varies by save.
     *
     * <p>Consent existed to keep a deeper floor away from saves that had not opted in. With the
     * floor now at the envelope there is nothing deeper to opt into, so every save resolves the
     * same way and the argument is accepted only so existing callers keep compiling.
     */
    public static double capFor(boolean consented) {
        return SHIPPED_MIN_RESOLVED_DY;
    }

    /** Updates the one cached floor used by server and client resolution. */
    public static void armDeepAlphabet(boolean consented) {
        minResolvedDy = capFor(consented);
    }

    /**
     * How far down a support walk may reach, derived from the floor rather than compared against
     * it. Each course costs half a block, plus two cells of headroom for the seat and its own
     * support. Comparing against a constant instead would SHORTEN this walk the moment the floor
     * and that constant became equal, which is backwards: a deeper floor needs a longer walk.
     */
    private static int supportResolveDepthLimit() {
        return (int) Math.ceil(Math.abs(minResolvedDy()) / 0.5d) + 2;
    }

    /** Recursion guard: prevents StackOverflow when isSolidBlock triggers getOutlineShape → getYOffset. */
    private static final ThreadLocal<Boolean> IN_GET_Y_OFFSET = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Allows bounded downward ancestry while refusing a revisit of an active support cell. */
    private static final ThreadLocal<SupportResolutionContext> SUPPORT_RESOLUTION_CONTEXT =
            ThreadLocal.withInitial(SupportResolutionContext::new);

    private static final class SupportResolutionContext {
        private final long[] activePositions = new long[SUPPORT_RESOLVE_DEPTH_CEILING + 2];
        private final BlockPos.MutableBlockPos neighborProbe = new BlockPos.MutableBlockPos();
        private int size;
        private int lastResultKind = SUPPORT_RESULT_UNKNOWN;

        private boolean contains(BlockPos pos) {
            long packed = pos.asLong();
            for (int index = 0; index < size; index++) {
                if (activePositions[index] == packed) {
                    return true;
                }
            }
            return false;
        }

        private boolean enter(BlockPos pos) {
            if (size >= activePositions.length || contains(pos)) {
                return false;
            }
            activePositions[size++] = pos.asLong();
            return true;
        }

        private void leave() {
            if (size > 0) {
                size--;
            }
        }

        private double answer(double value, int resultKind) {
            lastResultKind = resultKind;
            return value;
        }

        private int lastResultKind() {
            return lastResultKind;
        }

        private boolean hasHorizontalNonAirNeighbor(BlockGetter world, BlockPos pos) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            return !world.getBlockState(neighborProbe.set(x + 1, y, z)).isAir()
                    || !world.getBlockState(neighborProbe.set(x - 1, y, z)).isAir()
                    || !world.getBlockState(neighborProbe.set(x, y, z + 1)).isAir()
                    || !world.getBlockState(neighborProbe.set(x, y, z - 1)).isAir();
        }
    }

    /** Kill switch for the slab-height step-face cull relaxation ({@link #isSlabHeightStepFace}). */
    private static final boolean STEP_CULL_DISABLED = Boolean.getBoolean("slabbed.disableStepCull");

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

        // Thin top layers (snow layers, carpet) lower through the support-following floor-layer
        // lane, not this branch — excluding them here prevents double handling. Powder snow is a
        // full cube and lowers through this branch like any other cube (no-exceptions lowering
        // law — maintainer ruling, 2026-08-06; adopted on this line 2026-08-16). Terrain Slabs
        // surfaces stay protected by the compat gate below, not by a block-type exception.
        if (isThinTopLayer(state)) {
            return false;
        }

        if (CompatHooks.shouldSkipOffset(state)) {
            return false;
        }

        AttachmentRole role = attachmentRole(world, pos, state);
        if (role == AttachmentRole.SIDE) {
            return false;
        }

        // Ceiling-owned blocks must not also inherit the floor below them.
        //
        // Every path out of this branch returns false, including the fall-out below the loop, so
        // the walk only ever decides HOW EARLY it says no. The compat-gated isTopSlab inside it is
        // therefore INERT: a compat ceiling that the gate hides simply exits by the break instead
        // of the early return, and the answer is the same. Recorded because the gated predicate
        // sitting next to a role that is now decided geometrically reads like a half-converted
        // seam, and two readers have each spent time proving it is not one.
        if (role == AttachmentRole.CEILING) {
            BlockPos cursor = pos.above();
            for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
                BlockState cur = world.getBlockState(cursor);
                if (isTopSlab(cur)) {
                    return false;
                }
                if (isDynamicCeilingFollower(world, cursor, cur)) {
                    cursor = cursor.above();
                    continue;
                }
                break;
            }
            return false;
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
     *   <li>{@code slabDy + 0.5} (net &lt;= 0.0) for ceiling-attached blocks under a LOWERED top
     *       slab (merge compensation); under a FLUSH top slab they hang at {@code 0.0} — the old
     *       +0.5 reach-up is dead per the ruling recorded on {@code isLoweringTopLikeCeiling}.</li>
     *   <li>{@code 0.0} otherwise (no offset).</li>
     * </ul>
     */
    public static double getYOffset(BlockGetter world, BlockPos pos, BlockState state) {
        return getYOffsetGuarded(world, pos, state, true);
    }

    /**
     * Resolves the legacy geometry without consulting an existing placement fact.
     * Placement capture uses this twin so a new real placement overwrites stale truth instead of
     * reading its own predecessor.
     */
    public static double getUnstoredYOffset(BlockGetter world, BlockPos pos, BlockState state) {
        return getYOffsetGuarded(world, pos, state, false);
    }

    /**
     * Bounds the resolution against a chunk render region, which is a fixed array over a
     * bounded box: a neighbour walk that leaves it throws rather than falling back to the
     * level, on a mesh worker, mid-frame.
     *
     * <p>The bound is here, at the funnel, and NOT at the individual reads. Air is positive
     * evidence inside this resolver - {@link #isCantileverFullBlockCandidate} sinks a block
     * precisely because nothing is below it - so substituting air for an unreachable read
     * would answer a wrong height instead of declining. Evidence outside the box is
     * unreachable, not absent, so the whole resolution declines to flush: what vanilla would
     * draw. Anywhere but a render region an {@link IndexOutOfBoundsException} is a real
     * defect and is rethrown untouched.
     */
    private static double getYOffsetGuarded(
            BlockGetter world,
            BlockPos pos,
            BlockState state,
            boolean consultStoredHeight
    ) {
        try {
            return resolveYOffsetWithinRegion(world, pos, state, consultStoredHeight);
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            if (isRenderRegionView(world)) {
                return 0.0d;
            }
            reportUnrecognisedBoundedView(world);
            throw outsideRenderRegion;
        } catch (RuntimeException outsideWorldGenRegion) {
            // A world-generation region is the SECOND bounded view, and it signals an escaped
            // walk with a plain RuntimeException rather than an IndexOutOfBoundsException, so
            // the render-region arm above never sees it. The same law applies: evidence outside
            // the box is unreachable, not absent, so the whole resolution declines to flush.
            //
            // This is reachable in ordinary play, not just in tests: mob spawning during chunk
            // generation runs a collision sweep, our collision mixin resolves a height for each
            // cell, and a neighbour walk near the region edge leaves the box and kills worldgen.
            // Narrow on purpose - any other RuntimeException is a real defect and is rethrown.
            if (isWorldGenRegionView(world)) {
                return 0.0d;
            }
            throw outsideWorldGenRegion;
        }
    }

    /**
     * A world-generation region is a fixed square of chunks around the one being generated.
     * Unlike the render region this is a server class present on both distributions, so it needs
     * no injected detector.
     */
    private static boolean isWorldGenRegionView(BlockGetter view) {
        return view instanceof net.minecraft.server.level.WorldGenRegion;
    }

    private static final java.util.Set<String> REPORTED_UNRECOGNISED_VIEWS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Names a view whose bounded read escaped while the detector did not recognise it, once per
     * type. This runs only on the throw path, so it costs nothing in ordinary rendering, and it
     * turns the one boundary this guard has into a line a reader can act on.
     */
    private static void reportUnrecognisedBoundedView(BlockGetter world) {
        if (world == null) {
            return;
        }
        String name = world.getClass().getName();
        if (REPORTED_UNRECOGNISED_VIEWS.add(name)) {
            Slabbed.LOGGER.warn(
                    "[SLABBED] a bounded read escaped from an unrecognised view type: {}."
                    + " If this is a chunk renderer's own region type, the render-region bound"
                    + " is inert for it and the predicate needs widening.", name);
        }
    }

    private static double resolveYOffsetWithinRegion(
            BlockGetter world,
            BlockPos pos,
            BlockState state,
            boolean consultStoredHeight
    ) {
        if (world == null || pos == null) {
            return 0.0;
        }
        if (state == null || state.isAir()) {
            return 0.0;
        }
        // A compat mod that already positions this object owns its height; adding Slabbed's on
        // top of it sinks the block. Measured against the real mod 2026-08-20: snow on a vanilla
        // bottom slab took -0.5 from each and sank a full block.
        if (CompatHooks.handlesObjectOffset(world, pos, state)) {
            return 0.0;
        }

        // Powder snow lowers like any other full cube (no-exceptions lowering law — maintainer
        // ruling, 2026-08-06; adopted on this line 2026-08-16, retiring the always-flush guard).
        // The snowy-terrain step this guard once prevented is carried by the Terrain Slabs
        // compat gate instead: a TS surface is never a Slabbed support, so terrain-generated
        // powder snow on TS ground stays flush without a block-type exception.

        // Background server queries must not touch chunk attachments or walk support geometry.
        // The authoritative server thread will resolve and synchronize the visible value.
        if (world instanceof ServerLevel serverLevel
                && !serverLevel.getServer().isSameThread()) {
            return 0.0;
        }

        // Thin floor-owned layers remain live followers. Their unshifted geometry decides
        // the role, and the support's resolved top face decides the exact height.
        if (isSupportFollowingFloorLayer(world, pos, state)) {
            double supportDy = getSupportFollowerYOffset(world, pos.below());
            return Double.isFinite(supportDy) ? supportDy : 0.0d;
        }

        // A stored placement fact is the first height authority FOR EVERYTHING THAT KEEPS ONE.
        // It is read after the same state-level guards used during capture, but before any
        // recursive support geometry. Absence is the explicit old-world path and leaves every
        // legacy branch untouched.
        //
        // The ceiling-follower exception is not a guard on the read, it is a statement about who
        // owns the height: a follower's job is to track its ceiling, so it is excluded from the
        // store at capture (BlockItemPlacementIntentMixin storageExcluded) and its height is
        // resolved live here. The predicate is repeated rather than assumed because a subject can
        // BECOME a follower after it was placed - a ceiling appearing above it, or a support face
        // becoming sturdy - and when that happens any fact it already carries must go inert rather
        // than pin it at a height it no longer owns. Read "first authority" as first among the
        // things the store is allowed to hold, never as unconditional.
        if (consultStoredHeight && !isDynamicCeilingFollower(world, pos, state)) {
            double stored = SlabPlacementHeightAttachment.storedOffset(world, pos);
            if (Double.isFinite(stored)) {
                return stored;
            }
        }

        // A compat-owned surface Slabbed never authored stays flush: lowering generated ground
        // tears see-through world holes (DY_SPEC L5). Reaching here means no placement fact, and
        // a placement fact is the only thing world generation cannot forge - it is written by the
        // placement transaction alone. Do NOT re-derive this from the compat mod's own state: the
        // flag that looks like the answer is rebuilt from a default by that mod's disk and ore
        // features and decays to false when its grass spreads or dies back, so it answers
        // "which mod" and never "who built this".
        //
        // Placed BELOW the fact read on purpose. Above it, the gate returned before the fact
        // could be consulted, so no compat block could ever hold a height - the whole defect.
        //
        // A cell this client just placed is authored too, its fact merely in flight, so it must
        // resolve like a vanilla slab throughout that window (maintainer ruling, 2026-08-25:
        // compat slabs follow vanilla slabs, they are meant to be the same). Live, the gap made
        // the block DRAW lowered while its outline and targeting sat half a block higher.
        //
        // The prediction supplies AUTHORSHIP here and never a height - the lanes below still
        // compute the number - so the mesh-only law on predicted HEIGHTS is untouched. The store
        // is written only on the client (see the placement capture), so a server read finds it
        // empty by construction rather than by a side test, which is what keeps this reachable
        // from a headless row.
        //
        // Authorship has THREE durable arms - a stored fact, an anchor, a freeze-on-place
        // stamp - and this gate must consult all of them through the one shared predicate,
        // never re-derive its own subset. It once tested shouldSkipOffset alone (the fact arm
        // was already consumed above, the prediction escape below), so a compat cell whose
        // height rode the anchor family read FLUSH while its vanilla twin read lowered - found
        // by per-term mutation of the ceiling guard, whose two "unwatched" arms turned out to
        // be the two arms this gate was dropping.
        if (isUnauthoredCompatCell(world, pos, state)
                && ClientRenderDyPrediction.halfStepsOrAbsent(pos.asLong())
                        == SlabPlacementHeightAttachment.ABSENT_HALF_STEPS) {
            return 0.0;
        }

        // Direct compat seat: a named bottom-like compat surface supports curated objects and
        // vanilla slabs half a block down (maintainer ruling, 2026-08-21, the reference line's
        // lane). After the store - a placed fact always wins - and before every generic lane,
        // which keep skipping the compat namespace.
        // Recursion-armed: the subject predicate asks isSolidRender, whose occlusion probe
        // re-enters this resolver through the outline mixin. Under the armed flag the re-entry
        // short-circuits at the guard below, exactly as the other solidity-probing lanes do.
        if (!IN_GET_Y_OFFSET.get()) {
            IN_GET_Y_OFFSET.set(Boolean.TRUE);
            double directCustomDy;
            try {
                directCustomDy = directCustomSlabSupportDy(world, pos, state);
            } finally {
                IN_GET_Y_OFFSET.set(Boolean.FALSE);
            }
            if (Double.isFinite(directCustomDy)) {
                return directCustomDy;
            }
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

    private static VoxelShape vanillaCollisionShape(
            BlockState state,
            BlockGetter getter,
            BlockPos pos,
            CollisionContext context
    ) {
        boolean previous = isVanillaCollisionShapeQuery();
        VANILLA_COLLISION_SHAPE_QUERY.set(Boolean.TRUE);
        try {
            return state.getCollisionShape(
                    getter,
                    pos,
                    context == null ? CollisionContext.empty() : context);
        } finally {
            VANILLA_COLLISION_SHAPE_QUERY.set(previous);
        }
    }

    /**
     * Resolves the collision physically occupying one broadphase cell.
     *
     * <p>The logical owner's raw collision is translated by the same numeric fact used by the
     * model and outline. Owners in the legal targeting window above are then projected into this
     * cell. This removes collision left behind in the logical cell and makes deeply lowered owners
     * discoverable where they are drawn without loading or searching outside the bounded window.
     */
    public static VoxelShape collisionShapeForBroadphaseCell(
            BlockState state,
            BlockGetter getter,
            BlockPos pos,
            CollisionContext context
    ) {
        if (state == null || getter == null || pos == null) {
            return Shapes.empty();
        }

        VoxelShape own = vanillaCollisionShape(state, getter, pos, context);
        if (!COLLISION_FOLLOW) {
            return own;
        }
        if (!own.isEmpty()
                && !state.isAir()
                && state.getFluidState().isEmpty()
                && !(state.getBlock() instanceof ScaffoldingBlock)) {
            double ownDy = getYOffset(getter, pos, state);
            if (Math.abs(ownDy) > 1.0e-6d) {
                own = own.move(0.0d, ownDy, 0.0d);
            }
        }

        VoxelShape result = own;
        for (int delta = 1; delta <= PlacementDepthPolicy.ownerWindowRadius(); delta++) {
            BlockPos ownerPos = pos.above(delta);
            BlockState owner = getter.getBlockState(ownerPos);
            if (owner.isAir()
                    || !owner.getFluidState().isEmpty()
                    || owner.getBlock() instanceof ScaffoldingBlock) {
                continue;
            }
            double ownerDy = getYOffset(getter, ownerPos, owner);
            if (ownerDy >= -1.0e-6d) {
                continue;
            }
            VoxelShape rawOwner = vanillaCollisionShape(owner, getter, ownerPos, context);
            if (rawOwner.isEmpty()) {
                continue;
            }
            VoxelShape projected = rawOwner.move(0.0d, delta + ownerDy, 0.0d);
            double minY = projected.min(Direction.Axis.Y);
            double maxY = projected.max(Direction.Axis.Y);
            if (maxY <= 1.0e-6d || minY >= 1.0d - 1.0e-6d) {
                continue;
            }
            result = result.isEmpty() ? projected : Shapes.or(result, projected);
        }
        return result;
    }

    /**
     * A block's own collision shape, moved to where it actually collides after its own dy is
     * applied — never projected into a neighbour's cell.
     *
     * <p>{@link #collisionShapeForBroadphaseCell} answers "what floats into THIS cell from
     * above", a neighbour-projecting question the movement broadphase needs because it samples
     * one bounded cell at a time. This answers a different question — "where does THIS block
     * actually collide" — which is what a caller that will itself supply {@code pos} to a clip
     * needs, so a hit is attributed to the real block rather than to a cell it merely hangs into.
     * Fetched through the same guard {@link #vanillaCollisionShape} arms, so the already-lowered
     * StairBlock / beta35 fence-wall-gate / GRINDSTONE family is not lowered a second time.
     */
    public static VoxelShape ownCollisionShape(
            BlockGetter world, BlockPos pos, BlockState state, CollisionContext context
    ) {
        if (world == null || pos == null || state == null
                || state.isAir()
                || !state.getFluidState().isEmpty()
                || state.getBlock() instanceof ScaffoldingBlock) {
            return Shapes.empty();
        }
        VoxelShape own = vanillaCollisionShape(state, world, pos, context);
        if (own.isEmpty()) {
            return own;
        }
        double dy = getYOffset(world, pos, state);
        return Math.abs(dy) > 1.0e-6d ? own.move(0.0d, dy, 0.0d) : own;
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

        VoxelShape aboveVanilla = vanillaCollisionShape(
                above, getter, abovePos, CollisionContext.empty());
        if (aboveVanilla.isEmpty()) {
            return own;
        }
        VoxelShape hanging = aboveVanilla.move(0.0d, dy + 1.0d, 0.0d);
        return own.isEmpty() ? hanging : Shapes.or(own, hanging);
    }

    /** Seat-follower recursion reach (deep limit 6) plus margin; air or a partial-height
     * support within this depth can move the cell above, deeper ones cannot. */
    private static final int MESH_SCREEN_SEAT_DEPTH = 8;
    private static final Direction[] MESH_SCREEN_HORIZONTAL =
            {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /**
     * Tier-0 mesh screen: answers whether the cell could EVER resolve non-flush, need
     * step-face cull relaxation, or own an alternate-geometry lane — using only bounded
     * column reads and O(1) synced-marker lookups, with no resolver walk and no allocation.
     * FALSE is a guarantee of flush-and-vanilla-culled, so the chunk-mesh wrapper may skip
     * every per-block resolution for the cell; TRUE only grants the full resolution a
     * chance to run. Any uncertainty — an unmodeled state family, a partial support, a
     * bounded view rejecting a lookup — answers true. This screen is the hot-path posture
     * for the mesh wrapper: it runs once per block per section compile, so ordinary flush
     * terrain must pay approximately nothing here (the shipped-twice lag class).
     */
    public static boolean mayNeedMeshOffsetWork(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || state.isAir()) {
            return false;
        }
        try {
            if (Double.isFinite(SlabPlacementHeightAttachment.storedOffset(world, pos))
                    || SlabAnchorAttachment.isAnchored(world, pos)) {
                return true;
            }
            if (!isMeshScreenBelowResolvedState(state)) {
                return true;
            }
            if (meshScreenColumnSuspicious(world, pos)) {
                return true;
            }
            BlockPos.MutableBlockPos neighborProbe = new BlockPos.MutableBlockPos();
            for (Direction direction : MESH_SCREEN_HORIZONTAL) {
                neighborProbe.setWithOffset(pos, direction);
                BlockState neighbor = world.getBlockState(neighborProbe);
                if (neighbor.isAir()) {
                    continue;
                }
                if (neighbor.getBlock() instanceof SlabBlock) {
                    return true;
                }
                if (!neighbor.isSolidRender(world, neighborProbe)) {
                    // A non-occluding neighbor never culls this cell's side face, and with a
                    // solid support below (checked above) no horizontal lane can move this cell.
                    continue;
                }
                if (Double.isFinite(SlabPlacementHeightAttachment.storedOffset(world, neighborProbe))
                        || SlabAnchorAttachment.isAnchored(world, neighborProbe)
                        || meshScreenColumnSuspicious(world, neighborProbe)) {
                    return true;
                }
            }
            return false;
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return true;
        }
    }

    /**
     * States whose every dy lane reads only their own synced markers and the column below —
     * the families the tier-0 screen can bound. Followers, hangers, connectors, multi-cell
     * states, wall-mounted states, and the vertical-chain family each own lanes that read
     * sideways or upward, so they always resolve.
     */
    private static boolean isMeshScreenBelowResolvedState(BlockState state) {
        return !(state.getBlock() instanceof SlabBlock)
                && !(state.getBlock() instanceof PowderSnowBlock)
                && state.getFluidState().isEmpty()
                && !isThinTopLayer(state)
                && !hasHorizontalConnectionGeometry(state)
                && !state.hasProperty(BlockStateProperties.BED_PART)
                && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && !state.hasProperty(BlockStateProperties.HANGING)
                && !state.hasProperty(BlockStateProperties.ATTACH_FACE)
                && !state.hasProperty(BlockStateProperties.BELL_ATTACHMENT)
                && !(state.getBlock() instanceof TrapDoorBlock)
                && !(state.getBlock() instanceof WallSignBlock)
                && !(state.getBlock() instanceof WallBannerBlock)
                && !(state.getBlock() instanceof WallTorchBlock)
                && !(state.getBlock() instanceof WallHangingSignBlock)
                && !(state.getBlock() instanceof CaveVinesBlock)
                && !(state.getBlock() instanceof CaveVinesPlantBlock)
                && !isDownwardPointedDripstone(state)
                && !isBeta35UpwardPointedDripstoneVisibleOwnerObject(state)
                && !isAlwaysCeilingHungDecoration(state)
                && !isBeta35VerticalChainVisibleOwnerObject(state);
    }

    /**
     * Bounded below-column suspicion for the tier-0 screen. Mirrors the reach of the real
     * lanes: a slab, stored fact, or anchor anywhere in the {@code MAX_CHAIN_DEPTH} column
     * walk can lower the cell above; air or a partial-height (non-solid-render or thin)
     * support matters only within the seat-follower depth; the walk ends cleanly below the
     * world floor and at the depth cap.
     */
    private static boolean meshScreenColumnSuspicious(BlockGetter world, BlockPos top) {
        int minY = world.getMinBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 1; i <= MAX_CHAIN_DEPTH; i++) {
            int y = top.getY() - i;
            if (y < minY) {
                return false;
            }
            cursor.set(top.getX(), y, top.getZ());
            BlockState cur = world.getBlockState(cursor);
            if (cur.isAir()) {
                return i <= MESH_SCREEN_SEAT_DEPTH;
            }
            if (cur.getBlock() instanceof SlabBlock
                    || Double.isFinite(SlabPlacementHeightAttachment.storedOffset(world, cursor))
                    || SlabAnchorAttachment.isAnchored(world, cursor)) {
                return true;
            }
            if (isThinTopLayer(cur)) {
                return i <= MESH_SCREEN_SEAT_DEPTH;
            }
            if (i <= MESH_SCREEN_SEAT_DEPTH && !cur.isSolidRender(world, cursor)) {
                return true;
            }
        }
        return false;
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
     * block directly above a lowered one) are a documented follow-up; see the cull-window
     * design record in the maintainer notes. Disabled by {@code -Dslabbed.disableStepCull}.
     *
     * <p>The baked-model wrapper calls this from chunk-mesh render views. A bounded view may reject
     * a neighbor lookup at its edge; that means the seam is outside the supplied evidence, so the
     * predicate conservatively leaves vanilla culling unchanged.
     */
    public static boolean isSlabHeightStepFace(BlockGetter world, BlockPos pos, BlockState state, Direction direction) {
        if (STEP_CULL_DISABLED || world == null || pos == null || state == null || direction == null) {
            return false;
        }
        if (!direction.getAxis().isHorizontal()) {
            return false;
        }
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighbor;
        try {
            neighbor = world.getBlockState(neighborPos);
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return false;
        }
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
            return Math.max(minResolvedDy(), supportDy - 0.5d);
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
        // source line and keeps hanging lanterns below bridge-model chain columns
        // from inheriting a chain offset and merging into the chain model.
        if (isDynamicCeilingFollower(world, pos, state)
                && !isBeta35VerticalChainVisibleOwnerObject(state)) {
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
            double supportSeatFollowerDy = factlessSupportSeatFollowerDy(world, pos, state, 0);
            if (Double.isFinite(supportSeatFollowerDy)) {
                return supportSeatFollowerDy;
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
                    Slabbed.LOGGER.info("[ANCHOR] compound sidecar dy applied side={} pos={} state={}",
                            side, shortPos(pos), state);
                }
                return compoundLoweredSupportSeatDy(world, pos);
            }
            // An anchored full block above a lowered bottom-slab carrier must
            // align with the carrier's visible top at a total offset of -1.0.
            // The generic anchor offset alone would leave the two surfaces apart.
            BlockPos belowPos = pos.below();
            BlockState belowSlab = world.getBlockState(belowPos);
            if (isBottomSlab(belowSlab) && isAdjacentSideSlabLowered(world, belowPos, belowSlab)) {
                if (com.slabbed.anchor.SlabAnchorAttachment.TRACE) {
                    String side = (world instanceof net.minecraft.world.level.Level w && w.isClientSide()) ? "CLIENT" : "SERVER";
                    Slabbed.LOGGER.info("[ANCHOR] compound dy applied side={} pos={} state={} belowSlabPos={} belowSlabState={}",
                            side, shortPos(pos), state, shortPos(belowPos), belowSlab);
                }
                return compoundLoweredSupportSeatDy(world, pos);
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
        // (LAW 1 (the placement law): a placed block must not autonomously move). Read before any of the
        // geometric lowering below. Decorative followers are never frozen-flat, so they keep
        // tracking their supports.
        if (SlabAnchorAttachment.isFrozenFlat(world, pos)) {
            return 0.0;
        }

        boolean supportSeatSubject = isFactlessSupportSeatSubject(world, pos, state);
        if (supportSeatSubject) {
            double supportSeatFollowerDy = factlessSupportSeatFollowerDy(
                    world, pos, state, 0, true);
            if (Double.isFinite(supportSeatFollowerDy)) {
                return supportSeatFollowerDy;
            }
        } else {
            double floorContactFollowerDy = factlessGeometricFloorFollowerDy(world, pos, state);
            if (Double.isFinite(floorContactFollowerDy)) {
                return floorContactFollowerDy;
            }
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
            // -1.0 is inside the targetable envelope, the only depth bound on derivation.
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

        // A same-block vertical column of non-full members (chains, pointed dripstone) is one
        // connected body: every member inherits the column base's AUTHORED height instead of
        // re-deriving a categorical fixed step, so a deep column stays a column (maintainer
        // ruling, 2026-08-17). The read is the base's stored fact directly — the resolver's
        // re-entrancy guard forbids recursive resolution here — and a factless base falls
        // through to the legacy lanes unchanged.
        // Only floor-attached members are a downward-growing column; direction-agnostic
        // same-block pairs (a ceiling-hung twin above a floor twin) must not inherit downward.
        BlockPos columnProbe = pos.below();
        BlockState directlyBelow = world.getBlockState(columnProbe);
        if (directlyBelow.getBlock() == state.getBlock()
                && !isStrictFullStructuralState(world, pos, state)
                && attachmentRole(world, pos, state) == AttachmentRole.FLOOR) {
            BlockPos columnBase = columnProbe;
            int columnGuard = supportResolveDepthLimit();
            while (columnGuard-- > 0) {
                BlockPos nextPos = columnBase.below();
                if (world.getBlockState(nextPos).getBlock() != state.getBlock()) {
                    break;
                }
                columnBase = nextPos;
            }
            double columnFact = SlabPlacementHeightAttachment.storedOffset(world, columnBase);
            if (Double.isFinite(columnFact)) {
                return columnFact;
            }
        }

        if (shouldOffset(world, pos, state)) {
            // Compound case: a non-slab block above a bottom slab that is itself lowered. It
            // drops to that slab's visible top face, which is the slab's own drop plus its half
            // height - a full block down while the slab sits at half, and deeper below that.
            BlockState belowSlab = world.getBlockState(pos.below());
            if (isBottomSlab(belowSlab) && isAdjacentSideSlabLowered(world, pos.below(), belowSlab)) {
                return compoundLoweredSupportSeatDy(world, pos);
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

        // ── ceiling-attached blocks under a top slab: flush ruling ───
        // A FLUSH top slab no longer moves the block below it; a LOWERED top slab
        // merges the block against its underside (slabDy + 0.5, net <= 0.0).
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
        // bridge-model chain column, not a second chain segment. It hangs as an addendum at
        // the COLUMN's dy — never rising into the chain above it, never staying behind at
        // grid height once the cap has merged the column down.
        if (state.hasProperty(BlockStateProperties.HANGING)
                && state.getValue(BlockStateProperties.HANGING)) {
            double addendumColumnDy = ceilingBridgedVerticalChainColumnMergeDy(world, pos.above(), above);
            if (!Double.isNaN(addendumColumnDy)) {
                return addendumColumnDy;
            }
        }

        // direct: ceiling-attached blocks directly under a top slab. Track the slab's OWN dy so
        // a LOWERED top slab gives the block the flush merge (slabDy=-0.5 -> 0.0,
        // slabDy=-1.0 -> -0.5), never +0.5 up into the lowered slab. The FLUSH top slab's
        // +0.5 reach-up is gated by the ruling predicate (dead): a flush top slab no longer
        // moves the block.
        if (isDynamicCeilingFollower(world, pos, state) && isTopSlab(above)) {
            // A Terrain-Slabs-owned top slab is a SELF-RENDERING surface — its recursion-visible
            // dy must never feed the tracking leg. No-op without Terrain Slabs loaded.
            if (!CompatHooks.shouldSkipOffset(above)) {
                double aboveDy = storedOwnerOrLegacyInnerYOffset(world, pos.above(), above);
                if (aboveDy < -1.0e-6d) {
                    return aboveDy + 0.5d;
                }
            }
            if (isLoweringTopLikeCeiling(above)) {
                return 0.5;
            }
            return 0.0;
        }

        // Descendant chains in a ceiling-bridged column read the COLUMN's dy (maintainer ruling,
        // 2026-08-16): grid height while the cap holds the bridge flush — the direct top chain
        // hangs at grid height under the flush ruling, with the dedicated client bridge model
        // closing the visual seam to the slab underside — and the direct lane's merge value once
        // a marked TOP-slab cap lowers past net-zero. A hard 0.0 here would split the column:
        // the merged top segment would render half a block below its own grid-height descendant.
        double chainColumnDy = ceilingBridgedVerticalChainColumnMergeDy(world, pos, state);
        if (!Double.isNaN(chainColumnDy)) {
            return chainColumnDy;
        }

        // cascading: ceiling-attached block below other ceiling-attached blocks leading up to
        // a top slab (e.g. 2nd dripstone, 2nd vine segment). Deliberate asymmetry: this walk
        // carries no lowered-compensation tail — non-chain cascades resolve through
        // ceilingHungDecorationDy, whose cursor loop owns the merge compensation.
        if (isDynamicCeilingFollower(world, pos, state)) {
            BlockPos cursor = pos.above();
            for (int i = 0; i < MAX_CHAIN_DEPTH; i++) {
                BlockState cur = world.getBlockState(cursor);
                // Flush ruling: dead while isLoweringTopLikeCeiling returns false (was isTopSlab(cur)).
                if (isLoweringTopLikeCeiling(cur)) {
                    return 0.5;
                }
                if (isDynamicCeilingFollower(world, cursor, cur)) {
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
        double stored = SlabPlacementHeightAttachment.storedOffset(world, pos);
        if (Double.isFinite(stored)) {
            return stored;
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
        return BOTTOM_PERSISTENT_TRACE
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
    /**
     * Which subjects seat directly on a named compatible slab surface.
     *
     * <p>The terminal test pins natural terrain flush: lowering an opaque full cube while the
     * chunk mesher still culls at its grid-height voxel tears see-through holes across generated
     * terrain. That answer must not depend on which world view asks it, or the mesh worker and
     * the main thread disagree and the seam opens anyway.
     *
     * <p>Everything above the terminal test is a CURATED exception to it: block entities, the
     * crafting-table family, shelves, kelp, and the log family. Each is player-placed furniture
     * rather than generated ground, so seating it cannot open that seam. Add to this list only
     * for a family with that same property - a generated-terrain family here reopens the holes.
     */
    private static boolean isSlabSitCandidate(BlockGetter world, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof EntityBlock) {
            return true;
        }
        if (block instanceof CraftingTableBlock) {
            return true;
        }
        // The chiseled variant needs no row here: it stores books, so it carries a block entity
        // and the arm above already admits it.
        if (state.is(Blocks.BOOKSHELF) || state.is(Blocks.DRIED_KELP_BLOCK)) {
            return true;
        }
        if (block instanceof KelpBlock || block instanceof KelpPlantBlock) {
            return true;
        }
        if (isLogFamilySlabSitCandidate(state)) {
            return true;
        }
        return !state.isSolidRender(world, pos);
    }

    /**
     * The log family, read from its tag so a pack that retags a log moves with it.
     *
     * <p>Tag lookups throw before the registries bind. That happens during early startup, where
     * the honest answer is "not yet known" rather than a crash, so the unbound case answers no.
     */
    private static boolean isLogFamilySlabSitCandidate(BlockState state) {
        try {
            return state.is(BlockTags.LOGS);
        } catch (IllegalStateException tagsNotBoundYet) {
            if ("Tags not bound".equals(tagsNotBoundYet.getMessage())) {
                return false;
            }
            throw tagsNotBoundYet;
        }
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

    /**
     * Client entrypoint hook naming the render-region view type. A chunk render region is a
     * fixed array over a bounded box, so a read past its edge throws instead of falling back
     * to the level. The resolver walks neighbours and can therefore reach past that edge on a
     * mesh worker. Always null on a dedicated server, where no such view exists, so common
     * code never names a client class.
     *
     * <p>VERIFIED 2026-08-20 against a real client: the renderer hands the model path a
     * {@code net.minecraft.client.renderer.chunk.RenderChunkRegion}, which the installed
     * predicate matches, so the bound is live rather than inert on the vanilla renderer.
     *
     * <p>BOUNDARY: a third-party renderer supplying its OWN view type does not match, and the
     * escape rethrows exactly as it did before this guard existed - the pre-existing crash,
     * never a silently wrong height. That case no longer fails anonymously: an unrecognised
     * bounded view is named once in the log before the throw, so the fix is a one-line
     * predicate widening rather than an investigation.
     */
    private static volatile Predicate<BlockGetter> renderRegionDetector = null;

    /** Installs the render-region detector and returns the previous one for bounded tests. */
    public static Predicate<BlockGetter> installRenderRegionDetector(Predicate<BlockGetter> detector) {
        Predicate<BlockGetter> previous = renderRegionDetector;
        renderRegionDetector = detector;
        return previous;
    }

    private static boolean isRenderRegionView(BlockGetter view) {
        Predicate<BlockGetter> detector = renderRegionDetector;
        return view != null && detector != null && detector.test(view);
    }

    /** Bounds the direct-custom support column walk; mirrors the reference line's chain bound. */
    private static final int DIRECT_CUSTOM_SUPPORT_COLUMN_BOUND = 16;

    /**
     * A named compat bottom-like surface is a DIRECT seat: curated standing objects and vanilla
     * slabs resting on it sit half a block down, exactly as on a vanilla bottom slab, while the
     * generic support rules keep skipping the compat namespace (maintainer ruling, 2026-08-21,
     * porting the reference line's lane). Plain opaque cubes are deliberately NOT subjects -
     * {@link #isSlabSitCandidate} excludes the generic solid-cube fallback - so a full block on
     * bare compat terrain stays flush, the world-hole-safe answer this line already pins.
     */
    private static double directCustomSlabSupportDy(BlockGetter world, BlockPos pos, BlockState state) {
        if (!isDirectCustomSlabSupportedObject(world, pos, state)) {
            return Double.NaN;
        }
        // The named surface's seat is relative to its OWN recorded height. The historical
        // constant assumed every compat surface flush - true until a placed one could sink, at
        // which point a follower answering the constant floated above the very slab it stood on.
        // Downward-only from the constant, and read from the STORE rather than the resolver:
        // this lane runs inside the armed recursion window, and an attachment read cannot
        // re-enter it. A column that descends through intermediate courses keeps the constant -
        // the intermediate cells carry their own facts and are not this surface.
        BlockPos surfacePos = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                ? pos.below(2)
                : pos.below();
        if (CompatHooks.customSlabSurfaceKind(world.getBlockState(surfacePos))
                == CompatSlabSurfaceKind.BOTTOM_LIKE) {
            double own = SlabPlacementHeightAttachment.storedOffset(world, surfacePos);
            if (Double.isFinite(own) && own < -1.0e-6d) {
                return Math.max(own - 0.5d, minResolvedDy());
            }
        }
        return -0.5d;
    }

    public static boolean isDirectCustomSlabSupportedObject(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isDirectCustomSlabSupportSubject(world, pos, state)) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            BedPart part = state.getValue(BlockStateProperties.BED_PART);
            BlockPos otherPos = part == BedPart.FOOT
                    ? pos.relative(facing)
                    : pos.relative(facing.getOpposite());
            return hasDirectCustomBottomLikeSupportColumn(world, pos.below())
                    || hasDirectCustomBottomLikeSupportColumn(world, otherPos.below());
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
        return hasDirectCustomBottomLikeSupportColumn(world, supportPos);
    }

    private static boolean isDirectCustomSlabSupportSubject(BlockGetter world, BlockPos pos, BlockState state) {
        Block subjectBlock = state == null ? null : state.getBlock();
        boolean kelpFamily = subjectBlock instanceof KelpBlock || subjectBlock instanceof KelpPlantBlock;
        if (state == null
                || state.isAir()
                || state.getBlock() instanceof SlabBlock && !isTaggedSlab(state)
                // An object the compat mod positions itself gets exactly ONE offset - the mod's
                // own - so it is never a subject of this lane (the shared-predicate law: this is
                // the same deferral the resolve funnel applies).
                || CompatHooks.handlesObjectOffset(world, pos, state)
                || (!state.getFluidState().isEmpty() && !kelpFamily)
                || CompatHooks.shouldSkipOffset(state)) {
            return false;
        }
        return isTaggedSlab(state) || isSlabSitCandidate(world, pos, state);
    }

    private static boolean hasDirectCustomBottomLikeSupportColumn(BlockGetter world, BlockPos supportPos) {
        BlockPos cursor = supportPos;
        for (int i = 0; i < DIRECT_CUSTOM_SUPPORT_COLUMN_BOUND; i++) {
            BlockState supportState = world.getBlockState(cursor);
            if (supportState == null) {
                return false;
            }
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
}
