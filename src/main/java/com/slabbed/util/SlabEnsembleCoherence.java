package com.slabbed.util;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
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
 * ENSEMBLE COHERENCE classifier (maintainer ruling, 2026-07-07). Per-block gates are
 * structurally silent on a whole failure class: neighbors at different dys clip into each
 * other (flush hopper under −0.5 chest), leave mid-stack seams (−1.0 under −0.5), or render
 * entirely below their own cell so occupied space looks placeable. Each block is individually
 * lawful; the ENSEMBLE is wrong.
 *
 * <p>This is deliberately a MEASUREMENT gate, not a fix: pure, side-effect-free,
 * headless-testable classification of a vertical pair, wired to the sentinel's placement
 * neighborhood so a live session names every clash as a row.
 *
 * <p>Geometry (all in absolute Y, dy applied to the whole visual box):
 * lower's visual top = {@code lowerCellY + shapeMaxY(lower) + dyLower};
 * upper's visual bottom = {@code lowerCellY + 1 + shapeMinY(upper) + dyUpper}.
 * Only pairs whose VANILLA arrangement (both dy=0) is face-touching are classified — a bottom slab
 * under a block has a half-cell vanilla gap BY DESIGN and must never be flagged. With vanilla contact,
 * {@code contactGap = dyUpper − dyLower}: negative → INTERPENETRATION, positive → GAP.
 * OCCLUDED_OCCUPANCY is a single-block rule: a block whose visual TOP sits at/below its own cell floor
 * occupies zero visible volume in its cell.
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

    /** Failure-mode-4 guard: compat-owned states. Uses BOTH hooks —
     *  {@code shouldSkipOffset} (the authoritative live gate) and {@code shouldSkipSlabSupport} (which
     *  carries the documented test seam; {@code shouldSkipOffset} has none — HANDOFF test-seam note). */
    private static boolean isTsOwned(BlockState state) {
        return CompatHooks.shouldSkipOffset(state) || CompatHooks.shouldSkipSlabSupport(state);
    }

    /**
     * The exclusion this classifier actually wants: a compat-owned surface that Slabbed did not
     * author, and so cannot have given a height to.
     *
     * <p>It used to exclude every compat block, on the reasoning that they are all "outside
     * Slabbed's offset authority". That stopped being true when a PLACED compat slab began
     * recording a height (DY_SPEC L4, keyed on authorship): such a block is inside this mod's
     * authority, can clash with its neighbours exactly as a vanilla slab can, and was being
     * reported coherent no matter what it did. This classifier feeds the live recorder, so the
     * stale guard did not produce a wrong height — it produced a silent instrument, which is
     * worse to debug against than a noisy one.
     *
     * <p>Authorship is Slabbed's OWN record, never the compat mod's state. Do not be tempted by
     * that mod's {@code generated} flag: its worldgen disk and ore features rebuild slabs from a
     * default state, its grass resets the flag on random tick when it spreads or dies back, and a
     * DOUBLE merge inherits it from the cell merged into. A placement fact or an anchor stamp can
     * only be written by a real placement, which world generation never performs.
     */
    private static boolean isUnauthoredCompatSurface(BlockGetter world, BlockPos pos, BlockState state) {
        return isTsOwned(state) && !slabbedAuthored(world, pos);
    }

    /** Any durable record that this mod, rather than world generation, put this block here. */
    private static boolean slabbedAuthored(BlockGetter world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return Double.isFinite(SlabPlacementHeightAttachment.storedOffset(world, pos))
                || SlabAnchorAttachment.isAnchored(world, pos)
                || SlabAnchorAttachment.isFrozenFlat(world, pos);
    }

    /**
     * Classify the vertical pair (block at {@code lowerPos}, block at {@code lowerPos.above()}).
     * Air on either side, UNAUTHORED compat surfaces (failure-mode-4 guard), and vanilla
     * non-contact pairs are COHERENT by definition. An AUTHORED compat slab is classified like any
     * other block. dys are passed in (the caller knows which dy authority applies — live logical dy
     * at sample time).
     */
    public static Verdict classifyVerticalPair(BlockGetter world, BlockPos lowerPos,
                                               double dyLower, double dyUpper) {
        BlockPos upperPos = lowerPos.above();
        BlockState lower = world.getBlockState(lowerPos);
        BlockState upper = world.getBlockState(upperPos);
        // This overload knows WHERE both blocks are, so it can ask whether Slabbed authored them
        // and exclude only the unauthored ones. The state-only overload below cannot.
        if (isUnauthoredCompatSurface(world, lowerPos, lower)
                || isUnauthoredCompatSurface(world, upperPos, upper)) {
            return Verdict.COHERENT;
        }
        return classifyPairGeometry(lower, dyLower, upper, dyUpper);
    }

    /**
     * Classify two vertically adjacent states using their already-resolved offsets.
     *
     * <p>Position-free, so it cannot read a placement record and cannot tell an authored compat
     * slab from generated compat ground. It therefore keeps the older, broader exclusion and
     * declines to classify ANY compat-owned state. Prefer the {@link BlockGetter} overload, which
     * excludes only the unauthored ones; this one is conservative on purpose rather than by
     * oversight.
     */
    public static Verdict classifyVerticalPair(BlockState lower, double dyLower,
                                               BlockState upper, double dyUpper) {
        if (isTsOwned(lower) || isTsOwned(upper)) {
            return Verdict.COHERENT;
        }
        return classifyPairGeometry(lower, dyLower, upper, dyUpper);
    }

    /** The geometry rule itself, with every eligibility question already answered. */
    private static Verdict classifyPairGeometry(BlockState lower, double dyLower,
                                                BlockState upper, double dyUpper) {
        if (lower.isAir() || upper.isAir()) {
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
     * vanilla gaps, unauthored compat surfaces, air). Pure and headless-testable; the band emission
     * itself is client-side
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
        // Position-aware, so only an UNAUTHORED compat surface is excluded: an authored compat
        // slab can be pushed below its own cell floor exactly as a vanilla one can.
        if (state.isAir() || isUnauthoredCompatSurface(world, pos, state)) {
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

    /**
     * Deliberately state-shaped and deliberately still broad. Both callers
     * ({@link #relativeTranslationIncreasesBodyOverlap} and
     * {@link #canonicalOpenTransitionEnvelopesOverlap}) take positions but NO {@link BlockGetter},
     * so neither can read a placement record and neither can tell an authored compat slab from
     * generated compat ground. Widening this to the authorship test would mean inventing a world
     * to look it up in. Left conservative, and recorded here so the asymmetry with
     * {@link #isUnauthoredCompatSurface} reads as a decision rather than a missed call site.
     *
     * <p><b>Do not fold this into the authorship test just because a caller gains a world.</b>
     * Both consumers are envelope-OVERLAP classifiers, the shape a placement admission vetoes on.
     * Neither is called on this line today, which is exactly why the next person to wire one up is
     * the one at risk: on a sibling line the same two feed veto gates, and widening them there
     * would have let a change made for the recorder's benefit start REFUSING placements that are
     * admitted today. Narrowing a DIAGNOSTIC exclusion only ever costs a noisier report; narrowing
     * an ADMISSION exclusion costs the player a block they could previously place. If a consumer
     * appears and authorship genuinely belongs in it, that is its own slice with its own live pass,
     * not a follow-on to this one.
     *
     * <p>The second term is a no-op today — {@code shouldSkipSlabSupport} delegates to
     * {@code shouldSkipOffset}, so {@link #isTsOwned} already subsumes it — and is kept only
     * because the two hooks are documented as separately overridable seams.
     */
    private static boolean isTransitionEnvelopeExcluded(BlockState state) {
        return isTsOwned(state) || CompatHooks.shouldSkipOffset(state);
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
