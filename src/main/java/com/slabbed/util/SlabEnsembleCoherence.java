package com.slabbed.util;

import com.slabbed.compat.CompatHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
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
        if (lower.isAir() || upper.isAir() || isTsOwned(lower) || isTsOwned(upper)) {
            return Verdict.COHERENT;
        }
        VoxelShape lowerShape = lower.getShape(world, lowerPos);
        VoxelShape upperShape = upper.getShape(world, upperPos);
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
     * Single-block rule: does this block render entirely at/below its own cell floor (zero visible
     * volume in its cell — occupied space that LOOKS placeable)? {@code dy} is the block's live dy.
     */
    public static boolean isOccludedOccupancy(BlockGetter world, BlockPos pos, double dy) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || isTsOwned(state)) {
            return false;
        }
        VoxelShape shape = state.getShape(world, pos);
        if (shape.isEmpty()) {
            return false;
        }
        return shape.max(Direction.Axis.Y) + dy <= EPS;
    }
}
