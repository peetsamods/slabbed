package com.slabbed.util;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Offset-aware, nearest-hit block raycast — the single ownership rule for Slabbed
 * targeting. (MC 1.21.1 port of the 1.21.11 overhaul.)
 *
 * <p><b>Why this exists.</b> Slabbed renders some blocks at a visual Y offset
 * ({@link SlabSupport#getYOffset} returns one of {@code -1.0, -0.5, 0.0, +0.5}) and
 * offsets their outline/raycast {@link VoxelShape}s to match
 * ({@code SlabSupportStateMixin}). Vanilla {@code BlockView.raycast} uses a voxel DDA
 * that returns the <em>first cell</em> along the ray that yields a hit — not the
 * globally nearest hit. A shape offset out of its own voxel cell (a lowered block's
 * lower half poking down into {@code pos.down()}, or a near-horizontal ray that crosses
 * only the offset mid-height and never enters the block's logical cell) loses to a
 * nearer cell's block, or is missed entirely. That single ordering bug is the root of
 * the mistargeting and the brittle per-block-type rescue heuristics this class replaces.
 *
 * <p><b>What it does.</b> It reuses vanilla's exact DDA cell traversal (the static
 * {@link BlockView#raycast(Vec3d, Vec3d, Object, java.util.function.BiFunction, java.util.function.Function)}
 * helper) but, instead of stopping at the first cell, tests every block whose offset
 * outline could intersect the ray and keeps the <em>globally nearest</em> hit. At each
 * marched cell {@code C} it tests the outline of {@code C} plus the vertical neighbours
 * {@code C.up()}/{@code C.down()} that carry a non-zero visual offset.
 *
 * <p><b>±1 window completeness.</b> Visual offsets lie in {@code {-1.0,-0.5,0.0,+0.5}}
 * and every block shape is at most one cell tall, so an owner at {@code P} occupies at
 * most {@code {P, P.down()}} or {@code {P, P.up()}}; any ray hitting it enters a cell
 * within ±1 of {@code P}.
 *
 * <p><b>Parity with vanilla.</b> For non-offset blocks the nearest hit equals vanilla's
 * first-cell hit (ray distance is monotonic in march order, and a non-offset block is
 * only hittable from its own cell — visited as the primary cell). The primary cell is
 * tested exactly as vanilla (inside-hits included); non-offset neighbours are skipped;
 * neighbour inside-hits are suppressed. Side refinement is delegated to
 * {@link BlockView#raycastBlock} (outline-only, never the fluid-aware factory), so
 * {@code includeFluids=false} semantics hold.
 */
public final class SlabbedOffsetRaycast {

    private SlabbedOffsetRaycast() {
    }

    /**
     * Raycasts blocks from {@code start} to {@code end} against offset-aware outline
     * shapes and returns the nearest {@link BlockHitResult}, or a
     * {@link BlockHitResult#createMissed missed} result if nothing is hit. Never returns
     * {@code null}, matching {@code Entity.raycast}.
     */
    public static BlockHitResult raycast(BlockView world, Vec3d start, Vec3d end, ShapeContext shapeContext) {
        if (world == null || start == null || end == null) {
            return missed(start, end);
        }
        if (start.equals(end)) {
            return missed(start, end);
        }

        final NearestCollector collector = new NearestCollector(world, start, end, shapeContext);

        BlockView.raycast(
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

    private static BlockHitResult missed(Vec3d start, Vec3d end) {
        Vec3d safeStart = start != null ? start : Vec3d.ZERO;
        Vec3d safeEnd = end != null ? end : safeStart;
        Vec3d dir = safeStart.subtract(safeEnd);
        return BlockHitResult.createMissed(
                safeEnd,
                Direction.getFacing(dir.x, dir.y, dir.z),
                BlockPos.ofFloored(safeEnd));
    }

    private static final class NearestCollector {
        private final BlockView world;
        private final Vec3d start;
        private final Vec3d end;
        private final ShapeContext shapeContext;
        private final LongOpenHashSet shapeTested = new LongOpenHashSet();
        private final Long2DoubleOpenHashMap dyMemo = new Long2DoubleOpenHashMap();

        private BlockHitResult best = null;
        private double bestDistSq = Double.POSITIVE_INFINITY;

        NearestCollector(BlockView world, Vec3d start, Vec3d end, ShapeContext shapeContext) {
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
            VoxelShape outline = state.getOutlineShape(world, pos, shapeContext);
            if (outline.isEmpty()) {
                return;
            }
            BlockHitResult hit = world.raycastBlock(start, end, pos, outline, state);
            if (hit == null) {
                return;
            }
            if (!primary && hit.isInsideBlock()) {
                return;
            }
            double distSq = hit.getPos().squaredDistanceTo(start);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = hit;
            }
        }
    }
}
