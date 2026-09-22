package com.slabbed.compat.sable;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.PlacementDepthPolicy;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Hands Sable's physics the collision a cell really has under Slabbed.
 *
 * <p>Sable bakes one collider per block state and reuses it at every position, so a block
 * Slabbed placed lower keeps its unlowered collider there and physics objects rest half a block
 * above what is drawn. The height itself is never decided here: a cell's collision is exactly the
 * movement broadphase's answer, {@link SlabSupport#collisionShapeForBroadphaseCell}, so physics
 * objects, entities and the model share one authority. Cells no Slabbed height moves are left to
 * Sable's own collider untouched.
 *
 * <p>No Sable type appears here; the Sable side lives in the compatibility mixins, which only
 * load when Sable is present.
 *
 * <p>Known boundaries: a liquid cell keeps Sable's liquid collider, so a lowered block's hanging
 * half inside water has no side contact there; Sable skips all-air sections outright, so a hanging
 * half reaching into one is likewise absent (the lowered top face is always right); and a legacy,
 * fact-less column whose support changes more than one cell below is re-read when Sable next
 * re-adds the section, not on the change itself.
 */
public final class SablePhysicsHeight {
    /** Kill switch, and the RED lane of the physics proof: {@code -Dslabbed.sablePhysicsHeight=false}. */
    public static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("slabbed.sablePhysicsHeight", "true"));

    private static final double EPS = 1.0e-6d;
    /** The resolver's deepest column walk ({@code SlabSupport.MAX_CHAIN_DEPTH}). */
    private static final int CHAIN_DEPTH = 16;
    private static final Object UNCHANGED = new Object();
    private static final Map<Level, WeakReference<SablePlacementRefresh>> REFRESHERS = new WeakHashMap<>();
    private static boolean listening;

    /** A cell's collision under Slabbed, and the block whose physics material it carries. */
    public record CellCollision(VoxelShape shape, BlockState material) {
    }

    private SablePhysicsHeight() {
    }

    /**
     * Answers for one upload pass; neighbor checks revisit cells, so each is resolved once.
     * A memo lives only as long as the Sable call that created it and never outlives a block change.
     */
    public static final class Memo {
        private final ServerLevel level;
        private final Long2ObjectOpenHashMap<Object> cells = new Long2ObjectOpenHashMap<>();
        /** Each position's own height, resolved once: a cell and the cells below it all ask. */
        private final Long2DoubleOpenHashMap heights = new Long2DoubleOpenHashMap();
        /** Cells from a position down to the nearest cell that can seat a lowered column. */
        private final Long2IntOpenHashMap supportDistances = new Long2IntOpenHashMap();
        /** Stored-height and anchor presence per position; a position is asked from up to five cells. */
        private final Long2BooleanOpenHashMap markers = new Long2BooleanOpenHashMap();

        public Memo(ServerLevel level) {
            this.level = level;
            this.heights.defaultReturnValue(Double.NaN);
            this.supportDistances.defaultReturnValue(-1);
        }

        double height(BlockPos pos, BlockState state) {
            long key = pos.asLong();
            double cached = this.heights.get(key);
            if (Double.isNaN(cached)) {
                cached = this.mayBeMoved(pos, state) ? SlabSupport.getYOffset(this.level, pos, state) : 0.0d;
                this.heights.put(key, cached);
            }
            return cached;
        }

        /**
         * The mesh screen's question with every column walk shared across the pass. It answers
         * true wherever {@link SlabSupport#mayNeedMeshOffsetWork} does, and more: every clause is
         * that screen's own clause or a wider one (any seating cell within the full chain depth,
         * not the seat depth; neighbors checked whether or not they occlude). The one exception is
         * a fluid-holding block, which this answers false for: the broadphase cell collision never
         * moves one, as a cell or as an owner above. A false therefore keeps the mesh screen's
         * guarantee that no Slabbed lane moves the block's collision.
         */
        private boolean mayBeMoved(BlockPos pos, BlockState state) {
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                return false;
            }
            if (this.hasMarker(pos)
                    || !SlabSupport.isMeshScreenBelowResolvedState(state)
                    || this.supportDistance(pos) <= CHAIN_DEPTH) {
                return true;
            }
            BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                neighbor.setWithOffset(pos, direction);
                BlockState neighborState = this.level.getBlockState(neighbor);
                if (neighborState.isAir()) {
                    continue;
                }
                if (neighborState.getBlock() instanceof SlabBlock
                        || this.hasMarker(neighbor)
                        || this.supportDistance(neighbor) <= CHAIN_DEPTH) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasMarker(BlockPos pos) {
            long key = pos.asLong();
            if (this.markers.containsKey(key)) {
                return this.markers.get(key);
            }
            boolean marked = Double.isFinite(SlabPlacementHeightAttachment.storedOffset(this.level, pos))
                    || SlabAnchorAttachment.isAnchored(this.level, pos);
            this.markers.put(key, marked);
            return marked;
        }

        /** Whether a cell can seat what stands on it anywhere but on a flush, occluding full cube. */
        private boolean seats(BlockPos pos) {
            BlockState state = this.level.getBlockState(pos);
            return state.isAir()
                    || state.getBlock() instanceof SlabBlock
                    || SlabSupport.isThinTopLayer(state)
                    || !state.isSolidRender(this.level, pos)
                    || this.hasMarker(pos);
        }

        /** Distance down to the nearest seating cell, capped past the chain depth; each cell walked once. */
        private int supportDistance(BlockPos pos) {
            long key = pos.asLong();
            int cached = this.supportDistances.get(key);
            if (cached >= 0) {
                return cached;
            }
            BlockPos.MutableBlockPos cursor = pos.mutable();
            long[] keys = new long[CHAIN_DEPTH + 1];
            int length = 0;
            int base;
            boolean exhausted = false;
            while (true) {
                keys[length++] = cursor.asLong();
                cursor.move(Direction.DOWN);
                if (cursor.getY() < this.level.getMinBuildHeight()) {
                    base = CHAIN_DEPTH + 1;
                    break;
                }
                if (this.seats(cursor)) {
                    base = 1;
                    break;
                }
                int below = this.supportDistances.get(cursor.asLong());
                if (below >= 0) {
                    base = Math.min(CHAIN_DEPTH + 1, below + 1);
                    break;
                }
                if (length > CHAIN_DEPTH) {
                    base = CHAIN_DEPTH + 1;
                    exhausted = true;
                    break;
                }
            }
            // A walk cut off at the chain depth proves the far bound only for the cells it walked
            // at least CHAIN_DEPTH below; deeper cells are left for their own walk.
            for (int i = length - 1; i >= 0; i--) {
                int distance = Math.min(CHAIN_DEPTH + 1, base + (length - 1 - i));
                if (exhausted && length - i < CHAIN_DEPTH) {
                    continue;
                }
                this.supportDistances.put(keys[i], distance);
            }
            return Math.min(CHAIN_DEPTH + 1, base + (length - 1));
        }

        public @Nullable CellCollision cell(BlockPos pos) {
            long key = pos.asLong();
            Object cached = this.cells.get(key);
            if (cached == null) {
                CellCollision resolved = alteredCell(this.level, pos, this);
                cached = resolved == null ? UNCHANGED : resolved;
                this.cells.put(key, cached);
            }
            return cached == UNCHANGED ? null : (CellCollision) cached;
        }

        /** True when a neighbor's collision no longer fills the face Sable's culling assumes it does. */
        public boolean anyNeighborAltered(BlockPos pos) {
            BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
            for (Direction direction : Direction.values()) {
                probe.setWithOffset(pos, direction);
                if (this.cell(probe) != null) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * The collision this cell has when a Slabbed height moves it, or {@code null} when the cell
     * collides exactly as its block state does anywhere else. Server thread only; any other
     * caller keeps Sable's own collider.
     */
    public static @Nullable CellCollision alteredCell(ServerLevel level, BlockPos pos) {
        return alteredCell(level, pos, null);
    }

    private static @Nullable CellCollision alteredCell(ServerLevel level, BlockPos pos, @Nullable Memo memo) {
        if (!ENABLED || level == null || pos == null || !level.getServer().isSameThread()
                || level.isOutsideBuildHeight(pos)) {
            return null;
        }
        BlockState own = level.getBlockState(pos);
        if (isSableLiquid(own)) {
            // Sable gives a liquid cell one full liquid collider (buoyancy); a cell holds one
            // collider, so a lowered block's hanging half there is left out rather than drowning it.
            return null;
        }
        boolean moved = ownerReachesCell(level, pos, own, 0, memo);
        BlockState hanging = null;
        BlockPos.MutableBlockPos ownerPos = new BlockPos.MutableBlockPos();
        for (int delta = 1; delta <= PlacementDepthPolicy.ownerWindowRadius(); delta++) {
            ownerPos.setWithOffset(pos, 0, delta, 0);
            if (level.isOutsideBuildHeight(ownerPos)) {
                break;
            }
            BlockState owner = level.getBlockState(ownerPos);
            if (ownerReachesCell(level, ownerPos, owner, delta, memo)) {
                moved = true;
                if (hanging == null) {
                    hanging = owner;
                }
            }
        }
        if (!moved) {
            return null;
        }
        VoxelShape shape = SlabSupport.collisionShapeForBroadphaseCell(
                own, level, pos, CollisionContext.empty());
        BlockState material = own.isAir() && hanging != null ? hanging : own;
        return new CellCollision(shape, material);
    }

    /** Sable's own liquid test ({@code VoxelNeighborhoodState.isLiquid}), restated in vanilla types. */
    private static boolean isSableLiquid(BlockState state) {
        return state.liquid() || state.getBlock() instanceof KelpBlock || state.getBlock() instanceof KelpPlantBlock;
    }

    /** Whether the block {@code delta} cells above has a Slabbed height that changes this cell. */
    private static boolean ownerReachesCell(ServerLevel level, BlockPos ownerPos, BlockState owner, int delta,
                                            @Nullable Memo memo) {
        double dy = memo == null ? ownHeight(level, ownerPos, owner) : memo.height(ownerPos, owner);
        if (delta == 0) {
            return Math.abs(dy) > EPS;
        }
        return dy < -EPS && delta + dy < 1.0d - EPS;
    }

    /**
     * Whether any cell of this section could collide differently under Slabbed, answered from the
     * chunk's stored markers and section palettes alone. A cell is moved only by a lowered block
     * at or up to the owner window above it, and every lowered block either carries a stored
     * nonzero height or lowering marker, or stands in a column that reaches a partial-height
     * support within {@code MAX_CHAIN_DEPTH} (16) cells, or joins a cantilever lane whose
     * breadth-first walk reaches such a source within 16 connected cells sideways, which from
     * any cell stays inside the surrounding 3x3 chunks. So: no nonzero fact and no lowering
     * marker in those nine chunks, and no partial-height block in this section or the ones
     * directly above and below in them, means no cell here is moved and the whole section keeps
     * Sable's colliders. A chunk that is not loaded cannot be read and answers true.
     */
    public static boolean sectionMayBeAltered(ServerLevel level, int sectionX, int sectionY, int sectionZ) {
        return sectionsMayBeAltered(level, sectionX, sectionY, sectionY, sectionZ);
    }

    /** {@link #sectionMayBeAltered} for a vertical run of sections in one chunk column. */
    public static boolean sectionsMayBeAltered(ServerLevel level, int sectionX, int minSectionY, int maxSectionY,
                                               int sectionZ) {
        if (!ENABLED || level == null) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(sectionX + dx, sectionZ + dz);
                if (chunk == null || chunkMayLower(chunk, minSectionY - 1, maxSectionY + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean chunkMayLower(LevelChunk chunk, int minSectionY, int maxSectionY) {
        if (hasLoweringMarker(chunk)) {
            return true;
        }
        for (int y = minSectionY; y <= maxSectionY; y++) {
            int index = chunk.getSectionIndexFromSectionY(y);
            if (index < 0 || index >= chunk.getSectionsCount()) {
                continue;
            }
            LevelChunkSection section = chunk.getSection(index);
            if (!section.hasOnlyAir() && section.maybeHas(SablePhysicsHeight::isPartialHeightSupport)) {
                return true;
            }
        }
        return false;
    }

    /** Every marker that can hold a block below its own cell; the flat freeze marker cannot. */
    private static final List<DeferredHolder<AttachmentType<?>, AttachmentType<LongOpenHashSet>>> LOWERING_MARKERS =
            List.of(
                    SlabAnchorAttachment.ANCHOR_TYPE,
                    SlabAnchorAttachment.LOWERED_SLAB_CARRIER_TYPE,
                    SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE,
                    SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE,
                    SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE,
                    SlabAnchorAttachment.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE,
                    SlabAnchorAttachment.COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE);

    private static boolean hasLoweringMarker(LevelChunk chunk) {
        if (SlabPlacementHeightAttachment.hasNonFlushFact(chunk)) {
            return true;
        }
        for (DeferredHolder<AttachmentType<?>, AttachmentType<LongOpenHashSet>> marker : LOWERING_MARKERS) {
            LongOpenHashSet positions = chunk.getExistingDataOrNull(marker.get());
            if (positions != null && !positions.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** A block that can hold what stands on it below a full cell: any partial, non-empty collision. */
    private static boolean isPartialHeightSupport(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof SlabBlock || SlabSupport.isTaggedSlab(state) || SlabSupport.isThinTopLayer(state)) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return !shape.isEmpty() && !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** A block's own height, or 0 when the mesh screen proves no Slabbed lane can move it. */
    private static double ownHeight(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()
                || !SlabSupport.mayNeedMeshOffsetWork(level, pos, state)) {
            return 0.0d;
        }
        return SlabSupport.getYOffset(level, pos, state);
    }

    /** Registers one level's Sable physics system for refreshes after a placement height is stored. */
    public static synchronized void registerRefresher(Level level, SablePlacementRefresh refresher) {
        if (level == null || refresher == null) {
            return;
        }
        REFRESHERS.put(level, new WeakReference<>(refresher));
        if (!listening) {
            listening = true;
            SlabPlacementHeightAttachment.addChangeListener(SablePhysicsHeight::placementHeightChanged);
        }
    }

    /**
     * A stored height lands after the block itself is set, so Sable baked the cell before the fact
     * existed. Re-read the cell and every cell it can hang into once the fact is final.
     */
    private static void placementHeightChanged(Level level, BlockPos pos) {
        if (!ENABLED || !(level instanceof ServerLevel)) {
            return;
        }
        SablePlacementRefresh refresher;
        synchronized (SablePhysicsHeight.class) {
            WeakReference<SablePlacementRefresh> reference = REFRESHERS.get(level);
            refresher = reference == null ? null : reference.get();
        }
        if (refresher == null) {
            return;
        }
        int depth = PlacementDepthPolicy.ownerWindowRadius();
        int topSection = pos.getY() >> 4;
        int bottomSection = (pos.getY() - depth) >> 4;
        // A fact where nothing can be lowered stores exactly what Sable already baked.
        if (sectionsMayBeAltered((ServerLevel) level, pos.getX() >> 4, bottomSection, topSection, pos.getZ() >> 4)) {
            refresher.slabbed$refreshPlacementHeight(pos.immutable(), depth);
        }
    }
}
