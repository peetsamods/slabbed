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
 * targeting.
 *
 * <p><b>Why this exists.</b> Slabbed renders some blocks at a visual Y offset
 * ({@link SlabSupport#getVisualYOffset} returns one of {@code -1.0, -0.5, 0.0, +0.5})
 * and offsets their outline/raycast {@link VoxelShape}s to match
 * ({@code SlabSupportStateMixin}). Vanilla {@code BlockView.raycast} uses a voxel DDA
 * that returns the <em>first cell</em> along the ray that yields a hit — not the
 * globally nearest hit. A shape that has been offset out of its own voxel cell
 * (e.g. a lowered block whose lower half pokes down into {@code pos.down()}, or that a
 * near-horizontal ray passes only at the offset mid-height and so never enters the
 * block's logical cell at all) loses to a nearer cell's block, or is missed entirely.
 * That single ordering bug is the root of the mistargeting, the "nightmare" placement
 * hijacks, and the brittle per-block-type rescue heuristics this class replaces.
 *
 * <p><b>What it does.</b> It reuses vanilla's exact DDA cell traversal (the static
 * {@link BlockView#raycast(Vec3d, Vec3d, Object, java.util.function.BiFunction, java.util.function.Function)}
 * helper, which marches cells in increasing ray order) but, instead of stopping at the
 * first cell, it tests every block whose offset outline could intersect the ray and
 * keeps the <em>globally nearest</em> hit. At each marched cell {@code C} it tests the
 * outline of {@code C} itself plus the <em>vertical neighbours</em> {@code C.up()} and
 * {@code C.down()} that carry a non-zero visual offset.
 *
 * <p><b>±1 window completeness.</b> Visual offsets lie in the closed set
 * {@code {-1.0,-0.5,0.0,+0.5}} and every block shape is at most one cell tall, so an
 * owner at {@code P} occupies at most {@code {P, P.down()}} (for {@code -1.0}) or
 * {@code {P, P.up()}} (for {@code +0.5}). Any ray that intersects the owner's shape must
 * enter one of those cells, and {@code P} is within ±1 of every such cell — so testing
 * {@code {C, C.up(), C.down()}} at each visited cell is provably sufficient. The grazing
 * gametests in {@code OffsetRaycastTargetingTest} pin both extremes so a future
 * out-of-range offset trips a red test rather than silently mistargeting.
 *
 * <p><b>Parity with vanilla.</b> For non-offset blocks (shapes contained in their own
 * cell) the nearest hit equals vanilla's first-cell hit, because ray distance is
 * monotonic in march order: a hit in an earlier-visited cell is never farther than a hit
 * in a later one, and a non-offset block can only be hit from its own cell — which the
 * DDA visits as the primary cell {@code C}. The primary cell is therefore tested exactly
 * as vanilla does (inside-hits and all), and a non-offset neighbour is skipped entirely
 * (it is covered when the DDA reaches it as a primary cell). Neighbours with a non-zero
 * offset can only override the primary hit when strictly closer.
 *
 * <p>The shape used is the block's <em>outline</em> shape with the supplied
 * {@link ShapeContext}, mirroring vanilla crosshair targeting
 * ({@code RaycastContext.ShapeType.OUTLINE}). Fluids are never tested — this mirrors the
 * pick path's {@code entity.raycast(reach, tickDelta, false)}. Side refinement is
 * delegated to {@link BlockView#raycastBlock}, never the fluid-aware RaycastContext
 * factory, so the reported face matches vanilla and the intentionally un-offset fluid
 * shapes can never beat an offset block outline.
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

        // Reuse vanilla's DDA: the per-cell factory always returns null so the helper
        // marches every cell along [start, end]; the nearest hit is accumulated as a
        // side effect (do NOT early-return — that reintroduces first-hit-not-nearest).
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

    /**
     * Mutable per-raycast state: tracks the nearest hit, de-duplicates the expensive
     * shape raycast per position, and memoises {@code getVisualYOffset} so a shared
     * neighbour position is resolved at most once per ray.
     */
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

        /**
         * Test the cell itself (always, for vanilla parity) then its vertical neighbours
         * (only those carrying a non-zero visual offset — a non-offset neighbour is
         * reached as its own primary cell).
         */
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
            // Only an offset neighbour can intrude into a cell that is not its own.
            double dy = dyMemo.get(key);
            if (Double.isNaN(dy)) {
                dy = SlabSupport.getVisualYOffset(world, pos, state);
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
            // raycastBlock mirrors vanilla: it raycasts the outline and refines the
            // reported side via getRaycastShape (both already dy-offset by the mixin).
            BlockHitResult hit = world.raycastBlock(start, end, pos, outline, state);
            if (hit == null) {
                return;
            }
            // The primary cell keeps inside-hits for exact vanilla parity. For an offset
            // neighbour poking into the eye's cell, a near-zero-distance inside-hit would
            // spuriously win, so suppress it — vanilla never examines that neighbour.
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
