package com.slabbed.util;

import com.slabbed.compat.CompatHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * ENSEMBLE COHERENCE classifier — Phase 1 of docs/design/ENSEMBLE_COHERENCE_DESIGN.md (Maintainer-approved
 * lane, 2026-07-07). The video-vs-recorder correlation proved the dominant live complaint is a class
 * every per-block gate is structurally silent on: neighbors at different dys clip into each other
 * (flush hopper under −0.5 chest), leave mid-stack seams (−1.0 under −0.5), or render entirely below
 * their own cell so occupied space looks placeable (the 5× refused trapdoor clicks). Each block is
 * individually lawful; the ENSEMBLE is wrong — so nothing red-flagged it until now.
 *
 * <p>This is deliberately a MEASUREMENT gate, not a fix: pure, side-effect-free, headless-testable
 * classification of a vertical pair, wired to the sentinel's placement neighborhood so live sessions
 * name every clash as a row. Phase 2 (placement-time dy coherence) is designed against these rows.
 *
 * <p>Geometry (all in absolute Y, dy applied to the whole visual box):
 * lower's visual top = {@code lowerCellY + shapeMaxY(lower) + dyLower};
 * upper's visual bottom = {@code lowerCellY + 1 + shapeMinY(upper) + dyUpper}.
 * Only pairs whose VANILLA arrangement (both dy=0) is face-touching are classified — a bottom slab
 * under a block has a half-cell vanilla gap BY DESIGN and must never be flagged. With vanilla contact,
 * {@code contactGap = dyUpper − dyLower}: negative → INTERPENETRATION, positive → GAP.
 * OCCLUDED_OCCUPANCY is a single-block rule: a block whose visual TOP sits at/below its own cell floor
 * occupies zero visible volume in its cell (the t=98s slab-inside-the-lowered-log case).
 */
public final class SlabEnsembleCoherence {

    public static final double EPS = 1.0e-4;

    public enum Kind {
        COHERENT,
        INTERPENETRATION,
        GAP,
        OCCLUDED_OCCUPANCY
    }

    public record Verdict(Kind kind, double depth) {
        public static final Verdict COHERENT = new Verdict(Kind.COHERENT, 0.0);
    }

    private SlabEnsembleCoherence() {
    }

    /** Failure-mode-4 guard: TS-owned blocks are outside Slabbed's offset authority. Uses BOTH hooks —
     *  {@code shouldSkipOffset} (the authoritative live gate) and {@code shouldSkipSlabSupport} (which
     *  carries the documented test seam; {@code shouldSkipOffset} has none — HANDOFF test-seam note). */
    private static boolean isTsOwned(BlockState state) {
        return CompatHooks.shouldSkipOffset(state) || CompatHooks.shouldSkipSlabSupport(state);
    }

    /**
     * Classify the vertical pair (block at {@code lowerPos}, block at {@code lowerPos.above()}).
     * Air on either side, TS-owned blocks (failure-mode-4 guard), and vanilla non-contact pairs are
     * COHERENT by definition. dys are passed in (the caller knows which dy authority applies — live
     * logical dy at sample time).
     */
    public static Verdict classifyVerticalPair(BlockGetter world, BlockPos lowerPos,
                                               double dyLower, double dyUpper) {
        BlockPos upperPos = lowerPos.above();
        BlockState lower = world.getBlockState(lowerPos);
        BlockState upper = world.getBlockState(upperPos);
        return classifyVerticalPair(lower, dyLower, upper, dyUpper);
    }

    /**
     * Classify two vertically adjacent states using their already-resolved offsets.
     */
    public static Verdict classifyVerticalPair(BlockState lower, double dyLower,
                                               BlockState upper, double dyUpper) {
        if (lower.isAir() || upper.isAir() || isTsOwned(lower) || isTsOwned(upper)) {
            return Verdict.COHERENT;
        }
        VoxelShape lowerShape = vanillaShape(lower);
        VoxelShape upperShape = vanillaShape(upper);
        if (lowerShape.isEmpty() || upperShape.isEmpty()) {
            return Verdict.COHERENT;
        }
        double lowerMaxY = lowerShape.max(Direction.Axis.Y);
        double upperMinY = upperShape.min(Direction.Axis.Y);
        // Vanilla face contact required: lower's top reaches its cell ceiling AND upper's bottom sits
        // on its cell floor. Anything else (bottom slab below, hanging shapes) is a designed gap.
        boolean vanillaContact = lowerMaxY >= 1.0 - EPS && upperMinY <= EPS;
        if (vanillaContact) {
            double contactGap = dyUpper - dyLower;
            if (contactGap < -EPS) {
                return new Verdict(Kind.INTERPENETRATION, -contactGap);
            }
            if (contactGap > EPS) {
                return new Verdict(Kind.GAP, contactGap);
            }
        }
        return Verdict.COHERENT;
    }

    /**
     * Phase 3a (render tiling, first tranche): the height of the GAP-FILL BAND the mesher should emit
     * above the block at {@code lowerPos} to close a visible mid-stack air seam — exactly the
     * classifier's GAP depth, and 0 for every other verdict (coherent, interpenetration, by-design
     * vanilla gaps, TS-owned, air). Pure and headless-testable; the band emission itself is client-side
     * ({@code OffsetBlockStateModel}).
     */
    public static double gapFillBandHeight(BlockGetter world, BlockPos lowerPos,
                                           double dyLower, double dyUpper) {
        Verdict verdict = classifyVerticalPair(world, lowerPos, dyLower, dyUpper);
        return verdict.kind() == Kind.GAP ? verdict.depth() : 0.0;
    }

    /**
     * Whether relative frozen translation introduces or increases strict collision-body overlap
     * beyond the same states' canonical adjacent-cell baseline. Face, edge, and corner contact remain legal.
     */
    public static boolean relativeTranslationIncreasesBodyOverlap(
            BlockState firstState,
            BlockPos firstPos,
            double firstDy,
            BlockState secondState,
            BlockPos secondPos,
            double secondDy
    ) {
        if (firstState == null || firstPos == null || secondState == null || secondPos == null
                || firstState.isAir() || secondState.isAir()
                || isTransitionEnvelopeExcluded(firstState)
                || isTransitionEnvelopeExcluded(secondState)
                || !Double.isFinite(firstDy) || !Double.isFinite(secondDy)) {
            return false;
        }

        VoxelShape firstBody = vanillaCollisionShape(firstState);
        VoxelShape secondBody = vanillaCollisionShape(secondState);
        if (firstBody.isEmpty() || secondBody.isEmpty()) {
            return false;
        }

        for (AABB firstBox : firstBody.toAabbs()) {
            for (AABB secondBox : secondBody.toAabbs()) {
                double xDepth = Math.min(
                        firstBox.maxX + firstPos.getX(),
                        secondBox.maxX + secondPos.getX())
                        - Math.max(
                        firstBox.minX + firstPos.getX(),
                        secondBox.minX + secondPos.getX());
                double zDepth = Math.min(
                        firstBox.maxZ + firstPos.getZ(),
                        secondBox.maxZ + secondPos.getZ())
                        - Math.max(
                        firstBox.minZ + firstPos.getZ(),
                        secondBox.minZ + secondPos.getZ());
                if (xDepth <= EPS || zDepth <= EPS) {
                    continue;
                }

                double vanillaYDepth = Math.min(
                        firstBox.maxY + firstPos.getY(),
                        secondBox.maxY + secondPos.getY())
                        - Math.max(
                        firstBox.minY + firstPos.getY(),
                        secondBox.minY + secondPos.getY());
                double translatedYDepth = Math.min(
                        firstBox.maxY + firstPos.getY() + firstDy,
                        secondBox.maxY + secondPos.getY() + secondDy)
                        - Math.max(
                        firstBox.minY + firstPos.getY() + firstDy,
                        secondBox.minY + secondPos.getY() + secondDy);
                if (translatedYDepth > EPS && translatedYDepth > vanillaYDepth + EPS) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Single-block rule: does this block render entirely at/below its own cell floor (zero visible
     * volume in its cell — occupied space that LOOKS placeable)? {@code dy} is the block's live dy.
     */
    public static boolean isOccludedOccupancy(BlockGetter world, BlockPos pos, double dy) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || isTsOwned(state)) {
            return false;
        }
        VoxelShape shape = vanillaShape(state);
        if (shape.isEmpty()) {
            return false;
        }
        return shape.max(Direction.Axis.Y) + dy <= EPS;
    }

    /**
     * Does either block's canonical {@code OPEN=false}/{@code OPEN=true} collision envelope strictly
     * overlap the other's at their immutable world-space landing offsets? At least one state must own
     * {@link BlockStateProperties#OPEN}; a state without it contributes its one unchanged collision
     * shape. Toggling OPEN through {@link BlockState#setValue} deliberately preserves every other
     * property, including POWERED, facing, half, and hinge.
     *
     * <p>This is a pure admission classifier: collision shapes are queried context-free at origin,
     * each cell position and dy are applied exactly once, and face/edge/corner contact remains legal.
     * Air, compat-owned states, non-finite offsets, and empty resulting envelopes are ineligible.
     */
    public static boolean canonicalOpenTransitionEnvelopesOverlap(
            BlockState firstState,
            BlockPos firstPos,
            double firstDy,
            BlockState secondState,
            BlockPos secondPos,
            double secondDy
    ) {
        if (firstState == null || secondState == null || firstPos == null || secondPos == null
                || firstState.isAir() || secondState.isAir()
                || isTransitionEnvelopeExcluded(firstState)
                || isTransitionEnvelopeExcluded(secondState)
                || !Double.isFinite(firstDy) || !Double.isFinite(secondDy)
                || (!firstState.hasProperty(BlockStateProperties.OPEN)
                && !secondState.hasProperty(BlockStateProperties.OPEN))) {
            return false;
        }

        VoxelShape firstEnvelope = canonicalOpenTransitionEnvelope(firstState)
                .move(firstPos.getX(), firstPos.getY() + firstDy, firstPos.getZ());
        VoxelShape secondEnvelope = canonicalOpenTransitionEnvelope(secondState)
                .move(secondPos.getX(), secondPos.getY() + secondDy, secondPos.getZ());
        if (firstEnvelope.isEmpty() || secondEnvelope.isEmpty()) {
            return false;
        }

        for (AABB firstBox : firstEnvelope.toAabbs()) {
            for (AABB secondBox : secondEnvelope.toAabbs()) {
                double xDepth = Math.min(firstBox.maxX, secondBox.maxX)
                        - Math.max(firstBox.minX, secondBox.minX);
                double yDepth = Math.min(firstBox.maxY, secondBox.maxY)
                        - Math.max(firstBox.minY, secondBox.minY);
                double zDepth = Math.min(firstBox.maxZ, secondBox.maxZ)
                        - Math.max(firstBox.minZ, secondBox.minZ);
                if (xDepth > EPS && yDepth > EPS && zDepth > EPS) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTransitionEnvelopeExcluded(BlockState state) {
        return isTsOwned(state) || CompatHooks.terrainSlabsHandlesObjectOffset(state);
    }

    private static VoxelShape canonicalOpenTransitionEnvelope(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.OPEN)) {
            return vanillaCollisionShape(state);
        }
        VoxelShape closed = vanillaCollisionShape(state.setValue(BlockStateProperties.OPEN, false));
        VoxelShape open = vanillaCollisionShape(state.setValue(BlockStateProperties.OPEN, true));
        return Shapes.or(closed, open);
    }

    private static VoxelShape vanillaCollisionShape(BlockState state) {
        return state.getCollisionShape(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
    }

    /**
     * VANILLA-space shape, deliberately queried context-free: on this branch {@code getShape(world,
     * pos)} is the triad's OUTLINE leg and is ALREADY dy-offset for a genuinely lowered block, so any
     * math that adds the caller's dy on top double-counts it — the /slabdy {@code 0bf59d56} disease,
     * reproduced by this classifier's own first live outing (TEST (5): 30 of 76 rows were false
     * OCCLUDED verdicts on −0.5 full cubes). With {@link EmptyBlockGetter} the dy lanes see no support
     * context and return the unshifted shape in BOTH live and headless environments.
     */
    private static VoxelShape vanillaShape(BlockState state) {
        return state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }
}
