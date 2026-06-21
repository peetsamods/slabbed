package com.slabbed.util;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Offset-aware, nearest-hit block raycast — the single ownership rule for Slabbed
 * targeting. (MC 26.2 Mojang-mapped port of the 1.21.1/1.21.11 overhaul.)
 *
 * <p><b>Why this exists.</b> Slabbed renders some blocks at a visual Y offset
 * ({@link SlabSupport#getYOffset} returns one of {@code -1.0, -0.5, 0.0, +0.5}) and
 * offsets their outline/raycast {@link VoxelShape}s to match
 * ({@code SlabSupportStateMixin} offsets {@code getShape} and {@code getInteractionShape}).
 * Vanilla {@code Level.clip} uses a voxel DDA that returns the <em>first cell</em> along
 * the ray that yields a hit — not the globally nearest hit. A shape offset out of its own
 * voxel cell (a lowered block's lower half poking down into {@code pos.below()}, or a
 * near-horizontal ray that crosses only the offset mid-height and never enters the block's
 * logical cell) loses to a nearer cell's block, or is missed entirely. That single ordering
 * bug is the root of the mistargeting and the brittle per-block-type rescue heuristics this
 * class replaces (the legacy {@code GameRendererCrosshairRetargetMixin}).
 *
 * <p><b>What it does.</b> It reuses vanilla's exact DDA cell traversal
 * ({@link BlockGetter#traverseBlocks}) but, instead of stopping at the first cell, tests
 * every block whose offset outline could intersect the ray and keeps the <em>globally
 * nearest</em> hit. At each marched cell {@code C} it tests the outline of {@code C} plus
 * the vertical neighbours {@code C.below()}/{@code C.above()} that carry a non-zero visual
 * offset.
 *
 * <p><b>±1 window completeness.</b> Visual offsets lie in {@code {-1.0,-0.5,0.0,+0.5}} and
 * every block shape is at most one cell tall, so an owner at {@code P} occupies at most
 * {@code {P, P.below()}} or {@code {P, P.above()}}; any ray hitting it enters a cell within
 * ±1 of {@code P}.
 *
 * <p><b>Parity with vanilla.</b> For non-offset blocks the nearest hit equals vanilla's
 * first-cell hit (ray distance is monotonic in march order, and a non-offset block is only
 * hittable from its own cell — visited as the primary cell). The primary cell is tested
 * exactly as vanilla (inside-hits included); non-offset neighbours are skipped;
 * neighbour inside-hits are suppressed. Side refinement is delegated to
 * {@link BlockGetter#clipWithInteractionOverride} (outline-shape + interaction-shape
 * override, never the fluid-aware clip), so {@code includeFluids=false} semantics hold.
 */
public final class SlabbedOffsetRaycast {

    /**
     * Master switch for the offset-aware targeting overhaul. Default ON. Set
     * {@code -Dslabbed.offsetRaycast=false} to fall back to the legacy
     * {@code GameRendererCrosshairRetargetMixin} post-hoc retarget lanes (the rollback
     * baseline) for live A/B comparison.
     */
    public static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("slabbed.offsetRaycast", "true"));

    /**
     * Dev-only probe. With {@code -Dslabbed.offsetRaycast.trace=true} the redirect logs the
     * raw vanilla hit vs the offset hit whenever they disagree (never ships behaviour).
     */
    public static final boolean TRACE = Boolean.getBoolean("slabbed.offsetRaycast.trace");

    private SlabbedOffsetRaycast() {
    }

    /**
     * Raycasts blocks from {@code start} to {@code end} against offset-aware outline
     * shapes and returns the nearest {@link BlockHitResult}, or a
     * {@link BlockHitResult#miss missed} result if nothing is hit. Never returns
     * {@code null}, matching {@code Entity.pick}.
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

    private static BlockHitResult missed(Vec3 start, Vec3 end) {
        Vec3 safeStart = start != null ? start : Vec3.ZERO;
        Vec3 safeEnd = end != null ? end : safeStart;
        Vec3 dir = safeStart.subtract(safeEnd);
        return BlockHitResult.miss(
                safeEnd,
                Direction.getApproximateNearest(dir.x, dir.y, dir.z),
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
            testNeighbor(x, y - 1, z);
            testNeighbor(x, y + 1, z);
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
            if (dy == 0.0) {
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
            if (!primary && hit.isInside()) {
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
