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
 * ({@link SlabSupport#getVisualYOffset} returns one of {@code -1.0, -0.5, 0.0})
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
 * <p><b>Window completeness — as a function of the cap, not of a hardcoded radius.</b>
 * The window radius is not a free constant: it is fixed by the deepest offset the window
 * undertakes to attribute, {@link #DEEPEST_TARGETABLE_DY}, and {@link #WINDOW_RADIUS} is
 * DERIVED from it in code rather than written down a second time. The derivation:
 *
 * <ol>
 *   <li>Every block outline is at most one cell tall and starts at or above its own cell
 *       floor, so an owner at {@code P} carrying visual offset {@code dy} occupies world
 *       Y within {@code [P.y + dy, P.y + dy + 1]}.</li>
 *   <li>The cell layers that interval can touch are therefore
 *       {@code P.y + floor(dy) .. P.y}. With {@code dy <= 0} nothing is ever above
 *       {@code P.y}; the farthest layer BELOW the owner is {@code floor(dy)} cells away,
 *       which over {@code dy ∈ [DEEPEST_TARGETABLE_DY, 0]} is worst at the cap and equals
 *       {@code ceil(-DEEPEST_TARGETABLE_DY)}.</li>
 *   <li>A ray that intersects the owner's shape must march at least one of those layers
 *       — the DDA visits every cell the segment passes through — and each of them is
 *       within {@code ceil(-DEEPEST_TARGETABLE_DY)} of {@code P}.</li>
 * </ol>
 *
 * <p>So {@code WINDOW_RADIUS = ceil(-DEEPEST_TARGETABLE_DY)} is <em>sufficient</em>, and it
 * is also <em>necessary</em>: a half-height BOTTOM slab sitting at exactly the cap occupies
 * only the layer {@code P.y + floor(cap)} and nothing nearer, so any smaller radius loses
 * it to a side aim. <b>Change the cap and the radius moves with it, automatically. Change
 * the radius and you have changed which caps this class can honour.</b> The identity the
 * resolver must respect is therefore {@code MIN_RESOLVED_DY >= DEEPEST_TARGETABLE_DY} —
 * the alphabet may never run deeper than the window that has to click it. (Ruling of
 * 2026-08-06 in {@code docs/process/LIVE_LEDGER.md}, measured by
 * {@code DeepDyWindowCharacterisationTest}: required radius is 1 at {@code -1.0} and 2 at
 * both {@code -1.5} and {@code -2.0}, which is why the ruled cap is {@code -2.0} — the
 * largest magnitude a radius-2 window pays for.)
 *
 * <p><b>Slack is deliberate right now.</b> {@code SlabSupport.MIN_RESOLVED_DY} is still
 * {@code -1.0} while this window is built for {@code -2.0}: the window lands ahead of the
 * alphabet on purpose, so the permanent pick-path cost ships and live-tests by itself
 * rather than entangled with a geometry change. While that slack exists the extra probes
 * are <em>provably inert</em>: with every offset the build can mint confined to
 * {@code [-1.0, 0.0]}, a shape never reaches further than one cell from its owner, so the
 * outer ring of the window can only reach positions whose shape the ray does not intersect
 * — {@code raycastBlock} returns {@code null} for every one of them. The ring cannot even
 * perturb the primary/neighbour bookkeeping: cell Y is monotonic along a segment and the
 * DDA is contiguous, so any position the outer ring marks and that is later marched as a
 * primary cell was already marked, one cell earlier along the same ray, by the inner ring.
 * {@code PickWindowWideningTest} demonstrates this by running identical rays through both
 * radii and comparing the results, rather than leaving it as an argument.
 *
 * <p>The upward half of the window is symmetric defensive headroom: visual offsets lie in
 * the closed set {@code {-1.0,-0.5,0.0}} — the historical {@code +0.5} ceiling reach-up is
 * DEPRECATED (2026-07-03 live ruling; ceiling hangers now render FLUSH, see
 * {@code SlabSupport.isLoweringTopLikeCeiling}) — so no positive offset exists today and
 * the upward probes never yield a hit; if one ever returns it is covered up to
 * {@code +WINDOW_RADIUS}. The grazing gametests in {@code OffsetRaycastTargetingTest} pin
 * the extremes — the deep {@code -1.0} owner and the now-flush ceiling hanger — so a
 * future out-of-range offset trips a red test rather than silently mistargeting.
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

    /**
     * The deepest visual offset this pick window undertakes to attribute to its owner — the
     * window's CONTRACT, stated as a magnitude rather than as a cell count, because the magnitude
     * is the thing anyone has a reason to change.
     *
     * <p>Ruled {@code -2.0} by Maintainer on 2026-08-06. {@code SlabSupport.MIN_RESOLVED_DY}, the
     * deepest offset the resolver will ever PRODUCE, must never be deeper than this value; today
     * it is shallower ({@code -1.0}), which is the deliberate slack described in the class doc.
     */
    public static final double DEEPEST_TARGETABLE_DY = -2.0;

    /**
     * How many cells above and below each marched cell {@link NearestCollector#consumeCell} tests.
     *
     * <p><b>DERIVED, never written twice.</b> {@code ceil(-DEEPEST_TARGETABLE_DY)} is exactly the
     * number of cells that separate an owner from the deepest layer its shape can occupy at the
     * cap — see the completeness proof in the class doc. Computed once at class init, so the hot
     * path reads a folded constant and pays nothing for the derivation.
     */
    public static final int WINDOW_RADIUS = (int) Math.ceil(-DEEPEST_TARGETABLE_DY);

    private SlabbedOffsetRaycast() {
    }

    /**
     * What one ray cost, in operations rather than wall-clock — the instrument behind this line's
     * pick-path perf gate ({@code PickWindowWideningTest}).
     *
     * <p>Wall-clock is not admissible here: it is noisy, machine-dependent, and this project has
     * shipped a perf regression twice without a timing test ever going red. These are counts of
     * the work the window actually does, so they are exact, deterministic for a fixed scene and
     * ray, and comparable between two window radii.
     *
     * @param windowRadius   the radius the ray was run at
     * @param cellsMarched   DDA cells visited — a property of the ray, NOT of the window
     * @param neighborProbes calls into the neighbour arm; exactly {@code 2 * windowRadius} per
     *                       marched cell, before any de-duplication
     * @param posAllocations {@code BlockPos} objects allocated by the neighbour arm (probes that
     *                       survive the de-duplication set) — the allocation-regression instrument
     * @param dyResolutions  {@code SlabSupport.getVisualYOffset} calls that missed the memo — the
     *                       expensive resolution, one full support walk each
     * @param shapeRaycasts  {@code BlockView.raycastBlock} calls — the expensive shape test
     */
    public record Cost(int windowRadius,
                       int cellsMarched,
                       int neighborProbes,
                       int posAllocations,
                       int dyResolutions,
                       int shapeRaycasts) {
    }

    /**
     * Raycasts blocks from {@code start} to {@code end} against offset-aware outline
     * shapes and returns the nearest {@link BlockHitResult}, or a
     * {@link BlockHitResult#createMissed missed} result if nothing is hit. Never returns
     * {@code null}, matching {@code Entity.raycast}.
     */
    public static BlockHitResult raycast(BlockView world, Vec3d start, Vec3d end, ShapeContext shapeContext) {
        return raycastWithWindow(world, start, end, shapeContext, WINDOW_RADIUS);
    }

    /**
     * The same raycast at an explicitly chosen window radius. <b>Not a production entry point</b> —
     * the pick path always goes through {@link #raycast}, which fixes the radius at
     * {@link #WINDOW_RADIUS}. This exists so a test can run one identical ray through two radii and
     * compare, which is what turns "widening the window is behaviour-neutral at today's alphabet"
     * from an argument into a measurement, and what lets the perf gate state the cost of the
     * widening relative to the window it replaced instead of against a wall clock.
     */
    public static BlockHitResult raycastWithWindow(BlockView world, Vec3d start, Vec3d end,
                                                   ShapeContext shapeContext, int windowRadius) {
        if (world == null || start == null || end == null || start.equals(end)) {
            return missed(start, end);
        }
        BlockHitResult best = collect(world, start, end, shapeContext, windowRadius).best;
        return best != null ? best : missed(start, end);
    }

    /**
     * Runs one ray and reports what it COST rather than what it hit. See {@link Cost}.
     */
    public static Cost measureCost(BlockView world, Vec3d start, Vec3d end,
                                   ShapeContext shapeContext, int windowRadius) {
        if (world == null || start == null || end == null || start.equals(end)) {
            return new Cost(windowRadius, 0, 0, 0, 0, 0);
        }
        NearestCollector c = collect(world, start, end, shapeContext, windowRadius);
        return new Cost(windowRadius, c.cellsMarched, c.neighborProbes,
                c.posAllocations, c.dyResolutions, c.shapeRaycasts);
    }

    private static NearestCollector collect(BlockView world, Vec3d start, Vec3d end,
                                            ShapeContext shapeContext, int windowRadius) {
        final NearestCollector collector =
                new NearestCollector(world, start, end, shapeContext, windowRadius);

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
        return collector;
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
        private final int windowRadius;
        private final LongOpenHashSet shapeTested = new LongOpenHashSet();
        private final Long2DoubleOpenHashMap dyMemo = new Long2DoubleOpenHashMap();

        private BlockHitResult best = null;
        private double bestDistSq = Double.POSITIVE_INFINITY;

        // Work counters for the pick-path perf gate. Four int increments on a path whose unit of
        // work is a VoxelShape raycast; they are unconditional so the gate can never measure a
        // differently-instrumented path from the one that ships.
        private int cellsMarched;
        private int neighborProbes;
        private int posAllocations;
        private int dyResolutions;
        private int shapeRaycasts;

        NearestCollector(BlockView world, Vec3d start, Vec3d end, ShapeContext shapeContext,
                         int windowRadius) {
            this.world = world;
            this.start = start;
            this.end = end;
            this.shapeContext = shapeContext;
            this.windowRadius = windowRadius;
            this.dyMemo.defaultReturnValue(Double.NaN);
        }

        /**
         * Test the cell itself (always, for vanilla parity) then its vertical neighbours
         * out to {@link #WINDOW_RADIUS} (only those carrying a non-zero visual offset — a
         * non-offset neighbour is reached as its own primary cell).
         *
         * <p>The bound is the derived {@link #WINDOW_RADIUS}, so this loop widens or narrows with
         * {@link #DEEPEST_TARGETABLE_DY} and can never fall out of step with the completeness
         * proof in the class doc.
         */
        void consumeCell(int x, int y, int z) {
            cellsMarched++;
            testPrimary(x, y, z);
            for (int d = 1; d <= windowRadius; d++) {
                testNeighbor(x, y - d, z);
                testNeighbor(x, y + d, z);
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
            neighborProbes++;
            long key = BlockPos.asLong(x, y, z);
            if (shapeTested.contains(key)) {
                return;
            }
            posAllocations++;
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) {
                return;
            }
            // Only an offset neighbour can intrude into a cell that is not its own.
            double dy = dyMemo.get(key);
            if (Double.isNaN(dy)) {
                dyResolutions++;
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
            shapeRaycasts++;
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
