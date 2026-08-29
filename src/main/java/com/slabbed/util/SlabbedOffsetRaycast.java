package com.slabbed.util;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;

/**
 * Offset-aware, nearest-hit block raycast — the single ownership rule for Slabbed
 * targeting.
 *
 * <p><b>Why this exists.</b> Slabbed renders some blocks at a canonical half-step
 * visual Y offset within the supported interaction envelope and offsets their
 * outline/raycast {@link VoxelShape}s to match
 * ({@code SlabSupportStateMixin}). Vanilla {@code BlockGetter.raycast} uses a voxel DDA
 * that returns the <em>first cell</em> along the ray that yields a hit — not the
 * globally nearest hit. A shape offset out of its own voxel cell (a lowered block's
 * lower half poking down into {@code pos.below()}, or a near-horizontal ray that crosses
 * only the offset mid-height and never enters the block's logical cell) loses to a
 * nearer cell's block, or is missed entirely. That single ordering bug is the root of
 * the mistargeting and the brittle per-block-type rescue heuristics this class replaces.
 *
 * <p><b>What it does.</b> It reuses vanilla's exact DDA cell traversal (the static
 * {@link BlockGetter#raycast(Vec3, Vec3, Object, java.util.function.BiFunction, java.util.function.Function)}
 * helper) but, instead of stopping at the first cell, tests every block whose offset
 * outline could intersect the ray and keeps the <em>globally nearest</em> hit. At each
 * marched cell {@code C} it tests the outline of {@code C} plus every vertical owner cell
 * inside the supported interaction window that carries a non-zero visual offset.
 *
 * <p><b>Window completeness.</b> The owner radius is derived from
 * {@link PlacementDepthPolicy#MIN_TARGETABLE_DY}; targeting and placement admission therefore
 * cannot drift to different supported depths.
 *
 * <p><b>Parity with vanilla.</b> For non-offset blocks the nearest hit equals vanilla's
 * first-cell hit (ray distance is monotonic in march order, and a non-offset block is
 * only hittable from its own cell — visited as the primary cell). Non-offset neighbours are
 * skipped. Inside hits remain owned by the translated shape that actually contains the eye.
 * Side refinement is delegated to
 * {@link BlockGetter#raycastBlock} (outline-only, never the fluid-aware factory), so
 * {@code includeFluids=false} semantics hold.
 */
public final class SlabbedOffsetRaycast {

    public static final int OWNER_WINDOW_RADIUS = PlacementDepthPolicy.ownerWindowRadius();

    /**
     * Master switch for the offset-aware targeting overhaul. Default ON. Set
     * {@code -Dslabbed.offsetRaycast=false} to fall back to the legacy
     * post-hoc retarget lanes for live A/B comparison.
     */
    public static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("slabbed.offsetRaycast", "true"));

    /**
     * Dev-only probe. With {@code -Dslabbed.offsetRaycast.trace=true} the redirect logs the
     * raw vanilla hit vs the offset hit whenever they disagree.
     */
    public static final boolean TRACE = Boolean.getBoolean("slabbed.offsetRaycast.trace");

    private SlabbedOffsetRaycast() {
    }

    /**
     * Raycasts blocks from {@code start} to {@code end} against offset-aware outline
     * shapes and returns the nearest {@link BlockHitResult}, or a
     * {@link BlockHitResult#createMissed missed} result if nothing is hit. Never returns
     * {@code null}, matching {@code Entity.raycast}.
     */
    public static BlockHitResult raycast(BlockGetter world, Vec3 start, Vec3 end, CollisionContext shapeContext) {
        if (world == null || start == null || end == null) {
            return missed(start, end);
        }
        if (start.equals(end)) {
            return missed(start, end);
        }

        final NearestCollector collector = new NearestCollector(world, start, end, shapeContext);

        BlockGetter.traverseBlocks(
                start,
                end,
                collector,
                (c, cell) -> {
                    c.consumeCell(cell.getX(), cell.getY(), cell.getZ());
                    return null;
                },
                c -> null);

        BlockHitResult best = collector.best;
        return best != null ? best : missed(start, end);
    }

    /**
     * Preserves another loaded world's hit when it is at least as near as Slabbed's
     * ordinary-world hit. A missing, missed, or non-finite external hit cannot displace
     * Slabbed's result.
     */
    public static HitResult selectNearestOwnedHit(
            Vec3 eye,
            HitResult externalHit,
            BlockHitResult slabbedHit
    ) {
        if (externalHit == null || externalHit.getType() == HitResult.Type.MISS) {
            return slabbedHit;
        }
        if (slabbedHit == null || slabbedHit.getType() == HitResult.Type.MISS) {
            return externalHit;
        }

        double externalDistance = externalHit.getLocation().distanceToSqr(eye);
        double slabbedDistance = slabbedHit.getLocation().distanceToSqr(eye);
        if (!Double.isFinite(externalDistance)) {
            return slabbedHit;
        }
        if (!Double.isFinite(slabbedDistance) || externalDistance <= slabbedDistance) {
            return externalHit;
        }
        return slabbedHit;
    }

    private static BlockHitResult missed(Vec3 start, Vec3 end) {
        Vec3 safeStart = start != null ? start : Vec3.ZERO;
        Vec3 safeEnd = end != null ? end : safeStart;
        Vec3 dir = safeStart.subtract(safeEnd);
        return BlockHitResult.miss(
                safeEnd,
                Direction.getNearest(dir.x, dir.y, dir.z),
                BlockPos.containing(safeEnd));
    }

    private static final class NearestCollector {
        private final BlockGetter world;
        private final Vec3 start;
        private final Vec3 end;
        private final CollisionContext shapeContext;
        private final LongOpenHashSet shapeTested = new LongOpenHashSet();
        private final Long2DoubleOpenHashMap dyMemo = new Long2DoubleOpenHashMap();

        private BlockHitResult best = null;
        private double bestDistSq = Double.POSITIVE_INFINITY;

        NearestCollector(BlockGetter world, Vec3 start, Vec3 end, CollisionContext shapeContext) {
            this.world = world;
            this.start = start;
            this.end = end;
            this.shapeContext = shapeContext;
            this.dyMemo.defaultReturnValue(Double.NaN);
        }

        void consumeCell(int x, int y, int z) {
            testPrimary(x, y, z);
            for (int distance = 1; distance <= OWNER_WINDOW_RADIUS; distance++) {
                testNeighbor(x, y - distance, z);
                testNeighbor(x, y + distance, z);
            }
        }

        private void testPrimary(int x, int y, int z) {
            long key = BlockPos.asLong(x, y, z);
            if (!shapeTested.add(key)) {
                return;
            }
            accumulate(new BlockPos(x, y, z), true);
        }

        private void testNeighbor(int x, int y, int z) {
            long key = BlockPos.asLong(x, y, z);
            if (shapeTested.contains(key)) {
                return;
            }
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) {
                return;
            }
            double dy = dyMemo.get(key);
            if (Double.isNaN(dy)) {
                dy = SlabSupport.getYOffset(world, pos, state);
                dyMemo.put(key, dy);
            }
            if (dy == 0.0 && !SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(world, pos, state)) {
                return; // covered when the DDA visits this position as a primary cell
            }
            shapeTested.add(key);
            accumulate(pos, state, false);
        }

        private void accumulate(BlockPos pos, boolean primary) {
            accumulate(pos, world.getBlockState(pos), primary);
        }

        private void accumulate(BlockPos pos, BlockState state, boolean primary) {
            if (state.isAir()) {
                return;
            }
            VoxelShape outline = state.getShape(world, pos, shapeContext);
            if (outline.isEmpty()) {
                return;
            }
            BlockHitResult hit = world.clipWithInteractionOverride(start, end, pos, outline, state);
            if (hit == null) {
                return;
            }
            double distSq = hit.getLocation().distanceToSqr(start);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = hit;
            }
        }
    }
}
