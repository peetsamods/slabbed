package com.slabbed.dev;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure, headlessly-testable diagnostics for the Slabbed dy system — the analysis layer
 * behind the enriched {@code /slabdy record} capture and the {@code /slabdy} overlay.
 *
 * <p>Everything here is server-computable (no {@code MinecraftClient}): the visual triad
 * (outline/raycast/collision shapes + {@link SlabSupport#getVisualYOffset}) all resolve
 * from {@code (world, pos, state)} alone, so {@link #analyze} can be exercised directly by
 * a gametest with a synthetic world. The one client-only input is the render {@code modelDy}
 * (from {@code OffsetBlockStateModel}'s trace), which callers pass in optionally.
 *
 * <p><b>Reading the three dy legs.</b> Every leg reports either a number or one of two sentinels
 * ({@link #NOT_SAMPLED}, {@link #MEASURED_EMPTY}) that say which kind of nothing was seen — never
 * a single shared blank. Concretely, on a live client:
 * <ul>
 *   <li><b>outline</b> — a number for any block with geometry; {@code EMPTY} for an air cell.</li>
 *   <li><b>raycast</b> — {@code EMPTY} for almost every block, and that is CORRECT: vanilla's
 *       default {@code getRaycastShape} is empty and only a block that overrides it (composter)
 *       refines the reported side on top of the outline hit. A number here means the block has its
 *       own targeting shape, and that number must track the outline.</li>
 *   <li><b>model</b> — a number only when a render-path capture was armed AND the block's chunk
 *       section was meshed since; {@code NOT_SAMPLED} otherwise, because quads are emitted at
 *       section-bake time rather than per frame.</li>
 * </ul>
 *
 * <p><b>THE TRIAD FLAG COVERS THREE LEGS, AND SAYS WHICH ONES IT ACTUALLY CHECKED.</b> Until
 * {@code 21ceeb68} the flag named {@code triadMismatch} compared the OUTLINE against
 * {@code visualDy} and nothing else: the raycast leg had no flag at all, and the model leg's
 * separate flag could never fire because {@link #analyze(BlockView, BlockPos, BlockState)}
 * supplies no model sample. So {@code TRIAD_MISMATCH: 0} certified one leg out of three on a
 * line whose documented failure mode is exactly one leg moving while the others do not, and this
 * campaign read it as coverage. It is now three per-leg predicates plus an explicit per-leg
 * COVERAGE state ({@link LegCheck}), and {@link Sample#triadMismatch()} is the OR of the three.
 * A leg that could not be checked reports {@link LegCheck#NOT_SAMPLED} / {@link
 * LegCheck#EMPTY_BY_DESIGN} / {@link LegCheck#NOT_DECIDABLE} in {@link Sample#triadCoverage()},
 * and {@link Sample#triadLegsVerified()} counts only the legs that a disagreement WOULD have
 * fired on — so a green counter can never again be mistaken for coverage it does not have.
 *
 * <p><b>What still cannot be checked from {@code analyze} alone, stated plainly:</b>
 * <ul>
 *   <li><b>the model leg, headlessly</b> — the only INDEPENDENT model sample is the one
 *       {@code OffsetBlockStateModel.emitQuads} captures against the mesher's own
 *       {@code BlockRenderView} (a {@code ChunkRendererRegion}, which historically answered
 *       {@code isAnchored} differently from the server world — that view difference is the whole
 *       reason the leg is worth checking). Re-evaluating {@code ClientDy.dyFor} against the
 *       caller's world here would be a TAUTOLOGY, because that method is a pure delegate to
 *       {@link SlabSupport#getVisualYOffset} — the same call {@code visualDy} already makes — so
 *       the flag would be green by construction. That is the defect being fixed, not a fix for
 *       it. Headless callers therefore get {@link LegCheck#NOT_SAMPLED}, visibly.</li>
 *   <li><b>the EFFECTIVE hit</b> — the leg that actually decides targeting is where a real
 *       {@code SlabbedOffsetRaycast} lands, and that needs a ray, which a per-cell probe has no
 *       business inventing. {@code getRaycastShape} is only a side REFINEMENT layered on the
 *       outline hit, so it is a weaker proxy. The effective hit is pinned by gametest instead
 *       ({@code SlabbedDiagnosticsTest#effectiveHitLegTracksTheVisualDy}).</li>
 * </ul>
 *
 * <p>Detection heuristics (each a small, unit-tested predicate):
 * <ul>
 *   <li><b>outlineMismatch</b> — the outline shape did NOT get offset by the same dy as the
 *       visual/model, i.e. the wireframe/hitbox is a phantom (the exact "vanilla slabs fence
 *       raycast broken" class of bug).</li>
 *   <li><b>raycastMismatch</b> — the block HAS its own targeting shape and that shape disagrees
 *       with the offset outline. Silent when the shape is empty, which is the correct answer for
 *       almost every block.</li>
 *   <li><b>modelMismatch</b> — client-only: the rendered model dy disagrees with the
 *       authoritative visual dy.</li>
 *   <li><b>dodoRisk</b> — an opaque full cube rendered at a nonzero dy <b>with at least one
 *       face where a real height step is exposed and {@code BlockRenderInfoCullMixin} does NOT
 *       redraw it</b>. The bare shape condition is only the precondition (see
 *       {@link #dodoShapePrecondition}); on its own it marked 36 of 55 rows of a measured
 *       recorder run — the mod's entire normal operating envelope — and carried no signal.</li>
 *   <li><b>smooshRisk</b> — a non-air decoration (not a slab, not a full cube) sitting AT the
 *       resolver's floor {@link SlabSupport#MIN_RESOLVED_DY}; the classic double-offset "smoosh"
 *       where two systems (e.g. Terrain Slabs' own offset + Slabbed's) both fire and the sum
 *       saturates the clamp. The threshold is READ from the cap, never restated.</li>
 *   <li><b>dyDiscontinuity{Above,Below}</b> — two vertically-adjacent connectable
 *       decorations (chain/lantern) at different dy, i.e. a visible gap in a hanging stack
 *       (chain-to-lantern gap).</li>
 * </ul>
 */
public final class SlabbedDiagnostics {

    public static final double EPS = 1.0e-6;

    /**
     * Sentinel: <b>the probe never obtained a value here.</b> Nothing was measured, so nothing
     * may be concluded — in particular this is NOT evidence that a leg is at 0, nor that it is
     * empty. Printed as {@code NOT_SAMPLED}.
     *
     * <p>Live sources: {@code modelDy} when no render-path capture is armed or the block's chunk
     * section has not been meshed since it was armed (the mesher emits quads at bake time, not
     * per frame, so a static cell holds no fresh sample); and any leg handed a null shape.
     */
    public static final double NOT_SAMPLED = Double.NaN;

    /**
     * Sentinel: <b>the API was asked and answered with an EMPTY shape.</b> This IS a measurement —
     * there is genuinely no geometry to take a {@code minY} from. Printed as {@code EMPTY}.
     *
     * <p>Live sources: an air cell's outline/collision, and — for essentially every block —
     * {@code raycastMinY}. {@code AbstractBlock.getRaycastShape} returns {@code VoxelShapes.empty()}
     * unless a block overrides it (composter, and a handful of others), because that shape is only
     * a <i>side refinement</i> layered on top of the outline hit in
     * {@code BlockView.raycastBlock}: an empty raycast shape means the outline alone decides where
     * the crosshair lands. So {@code EMPTY} on this leg is the correct, expected answer for stone,
     * logs, slabs and chains — not a gap in the triad.
     *
     * <p>Kept distinct from {@link #NOT_SAMPLED} on purpose: sharing one value between "not
     * measurable here" and "measured, and it is empty" is what made a whole recorder run read as
     * coverage it did not have.
     */
    public static final double MEASURED_EMPTY = Double.NEGATIVE_INFINITY;

    /** True only for an actual measured number — neither sentinel above. */
    public static boolean isMeasured(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    /**
     * Whether a dy leg was actually COMPARED against the resolver, and if not, why not.
     *
     * <p>This exists because "the flag did not fire" and "the flag could not fire" are different
     * facts that a boolean cannot tell apart, and conflating them is what let
     * {@code TRIAD_MISMATCH: 0} read as three-leg coverage for an entire campaign. Only
     * {@link #CHECKED} means a disagreement WOULD have been caught.
     */
    public enum LegCheck {
        /** Compared against the resolver. A disagreement on this leg would have fired. */
        CHECKED,
        /** No value was obtained for this leg here — conclude nothing from its silence. */
        NOT_SAMPLED,
        /**
         * Sampled, and the answer was an empty shape. A real measurement and the CORRECT answer
         * (vanilla's default {@code getRaycastShape} is empty), but an empty shape constrains no
         * dy, so it verifies nothing either.
         */
        EMPTY_BY_DESIGN,
        /**
         * A value exists but cannot be turned into a dy comparison for this block — e.g. a
         * hanging lantern's nonzero base outline makes {@code minY}-vs-dy meaningless (see
         * {@link #hasGridBasedOutline}), or the leg has no baseline to compare against.
         */
        NOT_DECIDABLE
    }

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private SlabbedDiagnostics() {
    }

    public record Sample(
            String blockId,
            double visualDy,
            double outlineMinY,
            double raycastMinY,
            double collisionMinY,
            double modelDy,
            boolean opaqueFullCube,
            boolean slab,
            String anchorState,
            String aboveId, double aboveDy,
            String belowId, double belowDy,
            boolean outlineMismatch,
            boolean dodoRisk,
            boolean smooshRisk,
            boolean dyDiscontinuityAbove,
            boolean dyDiscontinuityBelow,
            boolean collisionFollowsVisual,
            boolean modelMismatch,
            boolean raycastMismatch,
            LegCheck outlineLeg,
            LegCheck raycastLeg,
            LegCheck modelLeg) {

        /**
         * <b>The triad flag, and now it means its name:</b> any of the three dy legs disagreed.
         * Read it TOGETHER with {@link #triadLegsVerified()} — this being false means "no CHECKED
         * leg disagreed", which is only three-leg coverage when three legs were checked.
         */
        public boolean triadMismatch() {
            return outlineMismatch || raycastMismatch || modelMismatch;
        }

        /** How many of the three legs were actually compared against the resolver (0..3). */
        public int triadLegsVerified() {
            int n = 0;
            if (outlineLeg == LegCheck.CHECKED) n++;
            if (raycastLeg == LegCheck.CHECKED) n++;
            if (modelLeg == LegCheck.CHECKED) n++;
            return n;
        }

        /** Per-leg coverage, e.g. {@code outline=CHECKED raycast=EMPTY_BY_DESIGN model=NOT_SAMPLED}. */
        public String triadCoverage() {
            return "outline=" + outlineLeg + " raycast=" + raycastLeg + " model=" + modelLeg;
        }

        /** True if any red flag fired — the rows a tester most wants to see. */
        public boolean anySuspect() {
            return triadMismatch() || dodoRisk || smooshRisk
                    || dyDiscontinuityAbove || dyDiscontinuityBelow;
        }

        /**
         * Compact list of the flags that fired (empty string if clean). {@code TRIAD_MISMATCH} is
         * the umbrella — kept as a token so every historic grep for it still finds the outline
         * failures it used to name — and the leg that actually disagreed is named beside it.
         */
        public String flagSummary() {
            List<String> f = new ArrayList<>();
            if (triadMismatch()) f.add("TRIAD_MISMATCH");
            if (outlineMismatch) f.add("OUTLINE_MISMATCH");
            if (raycastMismatch) f.add("RAYCAST_MISMATCH");
            if (modelMismatch) f.add("MODEL_MISMATCH");
            if (dodoRisk) f.add("DODO");
            if (smooshRisk) f.add("SMOOSH");
            if (dyDiscontinuityAbove) f.add("GAP_ABOVE");
            if (dyDiscontinuityBelow) f.add("GAP_BELOW");
            return String.join(",", f);
        }
    }

    public static Sample analyze(BlockView world, BlockPos pos, BlockState state) {
        return analyze(world, pos, state, NOT_SAMPLED);
    }

    public static Sample analyze(BlockView world, BlockPos pos, BlockState state, double modelDy) {
        double visualDy = SlabSupport.getVisualYOffset(world, pos, state);
        double outlineMinY = minY(state.getOutlineShape(world, pos, ShapeContext.absent()));
        double raycastMinY = minY(state.getRaycastShape(world, pos));
        double collisionMinY = minY(state.getCollisionShape(world, pos, ShapeContext.absent()));
        boolean opaque = state.isOpaqueFullCube();
        boolean slab = state.getBlock() instanceof SlabBlock;
        String anchor = anchorState(world, pos);

        BlockPos abovePos = pos.up();
        BlockState above = world.getBlockState(abovePos);
        double aboveDy = SlabSupport.getVisualYOffset(world, abovePos, above);
        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        double belowDy = SlabSupport.getVisualYOffset(world, belowPos, below);

        return new Sample(
                blockId(state),
                visualDy, outlineMinY, raycastMinY, collisionMinY, modelDy,
                opaque, slab, anchor,
                blockId(above), aboveDy,
                blockId(below), belowDy,
                outlineMismatch(state, visualDy, outlineMinY),
                dodoRisk(world, pos, state, visualDy),
                smooshRisk(state, visualDy),
                dyDiscontinuity(state, above, visualDy, aboveDy),
                dyDiscontinuity(state, below, visualDy, belowDy),
                collisionFollowsVisual(visualDy, outlineMinY, collisionMinY),
                modelMismatch(visualDy, modelDy),
                raycastMismatch(raycastMinY, outlineMinY),
                outlineLeg(state, outlineMinY),
                raycastLeg(raycastMinY, outlineMinY),
                modelLeg(modelDy));
    }

    // ── pure predicates (unit-testable) ───────────────────────────────

    /**
     * The outline shape did not follow the visual dy — the fence-raycast bug class.
     *
     * <p>Only decidable for blocks whose base outline starts at localY 0: full cubes, slabs,
     * fences, walls, panes, fence gates, beds, stairs. For those, {@code outlineMinY == dy}
     * once the mixin offsets it, and a failure (outline pinned at grid while the model
     * lowered) reads {@code outlineMinY ≈ 0 != dy}. Blocks with a nonzero base outline —
     * hanging lanterns (~0.06), chains (~0.41), signs — are NOT checked here: their base
     * offset is indistinguishable from a dy failure using minY alone (a chain's 0.41 base is
     * within one 0.5 dy step), so a minY-vs-dy test false-positives on every one of them.
     * That exact false positive is what the first recorder pass over beds/lanterns/chains
     * exposed; the gap/DODO/smoosh checks cover those block families instead.
     */
    public static boolean outlineMismatch(BlockState state, double visualDy, double outlineMinY) {
        return outlineLeg(state, outlineMinY) == LegCheck.CHECKED
                && Math.abs(outlineMinY - visualDy) > EPS;
    }

    /** Coverage of the outline leg — see {@link #outlineMismatch} for what makes it decidable. */
    public static LegCheck outlineLeg(BlockState state, double outlineMinY) {
        if (outlineMinY == MEASURED_EMPTY) {
            return LegCheck.EMPTY_BY_DESIGN;
        }
        if (!isMeasured(outlineMinY)) {
            return LegCheck.NOT_SAMPLED;
        }
        return hasGridBasedOutline(state) ? LegCheck.CHECKED : LegCheck.NOT_DECIDABLE;
    }

    /**
     * The block has its OWN targeting shape and that shape disagrees with the offset outline.
     *
     * <p>Compared against the OUTLINE rather than against {@code visualDy} on purpose:
     * {@code getRaycastShape} is a side REFINEMENT layered on top of the outline hit in
     * {@code BlockView.raycastBlock}, so "tracks the outline" is the property that matters and it
     * is decidable for blocks whose base is not 0 (a chain, a lantern) where a dy comparison is
     * not. Silent when either shape is empty — {@link LegCheck#EMPTY_BY_DESIGN} is the correct
     * answer for stone, slabs and chains, and inventing a mismatch there would be a phantom.
     */
    public static boolean raycastMismatch(double raycastMinY, double outlineMinY) {
        return raycastLeg(raycastMinY, outlineMinY) == LegCheck.CHECKED
                && Math.abs(raycastMinY - outlineMinY) > EPS;
    }

    /** Coverage of the raycast leg. {@code EMPTY} is a measurement, but it verifies no dy. */
    public static LegCheck raycastLeg(double raycastMinY, double outlineMinY) {
        if (raycastMinY == MEASURED_EMPTY) {
            return LegCheck.EMPTY_BY_DESIGN;
        }
        if (!isMeasured(raycastMinY)) {
            return LegCheck.NOT_SAMPLED;
        }
        return isMeasured(outlineMinY) ? LegCheck.CHECKED : LegCheck.NOT_DECIDABLE;
    }

    /**
     * Coverage of the model leg. {@link LegCheck#NOT_SAMPLED} is the honest answer for every
     * headless caller, and it is now VISIBLE rather than a silent {@code false} — see the class
     * javadoc for why re-deriving the sample from the caller's own world would be a tautology.
     */
    public static LegCheck modelLeg(double modelDy) {
        if (modelDy == MEASURED_EMPTY) {
            return LegCheck.EMPTY_BY_DESIGN;
        }
        return isMeasured(modelDy) ? LegCheck.CHECKED : LegCheck.NOT_SAMPLED;
    }

    /**
     * True for blocks whose un-offset outline minY is exactly 0, so outlineMinY == dy once
     * the mixin offsets it. This EXCLUDES blocks with a variable base:
     * <ul>
     *   <li>TOP slabs (outline base 0.5 — a lowered top slab correctly reads outlineMinY 0.0,
     *       which a base-0 assumption misreads as a mismatch; verified in gametest against a
     *       stale-anchored top slab, and this exact false positive was in the recorder data);</li>
     *   <li>stairs (base varies with half/shape).</li>
     * </ul>
     * DOUBLE slabs are full cubes and are caught by the isOpaqueFullCube branch.
     */
    public static boolean hasGridBasedOutline(BlockState state) {
        if (state.isOpaqueFullCube()) {
            return true;
        }
        Block b = state.getBlock();
        if (b instanceof SlabBlock) {
            return state.contains(SlabBlock.TYPE)
                    && state.get(SlabBlock.TYPE) == net.minecraft.block.enums.SlabType.BOTTOM;
        }
        return b instanceof net.minecraft.block.FenceBlock
                || b instanceof net.minecraft.block.WallBlock
                || b instanceof net.minecraft.block.PaneBlock
                || b instanceof net.minecraft.block.FenceGateBlock
                || b instanceof net.minecraft.block.BedBlock;
    }

    /**
     * NECESSARY, NOT SUFFICIENT: an opaque full cube rendered at a nonzero dy — the SHAPE that can
     * expose a face-cull-vs-render see-through hole.
     *
     * <p>This was the whole of {@code dodoRisk} until {@code 21ceeb68}, and on its own it is
     * measured noise: over a full recorder run it selected exactly
     * {@code {opaqueFullCube && |visualDy| > EPS}} — <b>36 of 55 rows</b>, i.e. the mod's entire
     * normal operating envelope, with no reference to whether any hole is actually exposed or
     * whether {@code BlockRenderInfoCullMixin} already redraws it. Kept as a named precondition
     * because it IS the correct first gate; the flag is {@link #dodoRisk(BlockView, BlockPos,
     * BlockState, double)}.
     */
    public static boolean dodoShapePrecondition(boolean opaqueFullCube, double visualDy) {
        return opaqueFullCube && Math.abs(visualDy) > EPS;
    }

    /**
     * An opaque full cube at a nonzero dy with at least one face where a height step really is
     * exposed and the per-face mitigation does NOT cover it.
     *
     * <p><b>Horizontal faces.</b> {@code BlockRenderInfoCullMixin} redraws exactly the faces
     * {@link SlabSupport#isSlabHeightStepFace} claims, so a step that predicate answers for is
     * MITIGATED and must not be flagged. This probe therefore establishes the hole independently —
     * the neighbour is an opaque full cube (so the mesher culls the shared face at the grid voxel)
     * AND the two resolved heights differ (so the step exposes part of it) — and then flags only
     * when the mitigation declines the face. If the mitigation is switched off, or misses a case,
     * the flag comes back on its own.
     *
     * <p><b>Vertical faces are NOT mitigated at all</b> — the mixin is horizontal-only
     * ({@code direction.getAxis().isHorizontal()}) — so a genuine vertical step always flags. A dy
     * DIFFERENCE alone is not a vertical hole, though: a cube at {@code -0.5} standing on a flush
     * bottom slab meets its support exactly, which is the mod's ordinary geometry. Between two
     * full cubes the gap is the signed difference, so only {@code aboveDy > dy} (a gap under the
     * block above) and {@code dy > belowDy} (a gap over the block below) are holes. Measured
     * against the three recorder runs this vertical arm adds ZERO rows, so it is not what made the
     * old flag noisy — it is kept because leaving it out would make the flag falsely quiet on a
     * hole class nothing else covers.
     */
    public static boolean dodoRisk(BlockView world, BlockPos pos, BlockState state, double visualDy) {
        if (world == null || pos == null || state == null
                || !dodoShapePrecondition(state.isOpaqueFullCube(), visualDy)) {
            return false;
        }
        for (Direction d : HORIZONTAL) {
            BlockPos np = pos.offset(d);
            if (occludingStepNeighbor(world, np, visualDy) != 0
                    && !SlabSupport.isSlabHeightStepFace(world, pos, state, d)) {
                return true;
            }
        }
        // Vertical: sign matters. +1 = the neighbour sits higher than this block by its dy.
        return occludingStepNeighbor(world, pos.up(), visualDy) > 0
                || occludingStepNeighbor(world, pos.down(), visualDy) < 0;
    }

    /**
     * {@code 0} when the cell at {@code np} cannot expose a step against a block at
     * {@code visualDy} — it is not an opaque full cube (nothing culls the shared face), or the two
     * resolved heights agree. Otherwise the SIGN of {@code neighborDy - visualDy}, which the
     * vertical arm needs and the horizontal arm ignores.
     */
    private static int occludingStepNeighbor(BlockView world, BlockPos np, double visualDy) {
        BlockState n = world.getBlockState(np);
        if (n == null || !n.isOpaqueFullCube()) {
            return 0;
        }
        double delta = SlabSupport.getVisualYOffset(world, np, n) - visualDy;
        if (Math.abs(delta) <= EPS) {
            return 0;
        }
        return delta > 0 ? 1 : -1;
    }

    /**
     * A non-air decoration (not slab, not full cube) sitting AT the resolver's floor = the
     * double-offset "smoosh".
     *
     * <p><b>The threshold is READ from {@link SlabSupport#MIN_RESOLVED_DY}, not written down.</b>
     * It used to be the literal {@code -1.0}, which happened to equal the shipped cap; the moment
     * the cap moves to {@code -2.0} a hard {@code -1.0} would fire on every decoration merely
     * lowered past one block — the mod's new normal envelope — and repeat the DODO mistake one
     * cap later. Derived, it keeps meaning "this decoration has saturated the clamp", which is
     * what a double offset looks like AFTER clamping and is the only part of it still observable
     * through {@code getVisualYOffset}. At today's cap this is bit-for-bit the old threshold, so
     * the recorder's measured SMOOSH behaviour is unchanged by the derivation.
     *
     * <p><b>Air gate.</b> An air cell's {@code visualDy} reads the pre-placement lane's value and
     * has no geometry to smoosh, yet air satisfied "not a slab, not an opaque cube" and so
     * classified as a decoration: 4 of 6 SMOOSH rows in run {@code 9e925ab0}, 7 of 13 in
     * {@code b5d717d9}.
     */
    public static boolean smooshRisk(BlockState state, double visualDy) {
        if (state.isAir()) {
            return false;
        }
        boolean decoration = !(state.getBlock() instanceof SlabBlock) && !state.isOpaqueFullCube();
        return decoration && visualDy <= SlabSupport.MIN_RESOLVED_DY + EPS;
    }

    /** Two vertically-adjacent connectable decorations (chain/lantern) at different dy = gap. */
    public static boolean dyDiscontinuity(BlockState a, BlockState b, double dyA, double dyB) {
        return isConnectableDecoration(a) && isConnectableDecoration(b)
                && Math.abs(dyA - dyB) > EPS;
    }

    /**
     * Client-only: rendered model dy disagrees with the authoritative visual dy.
     *
     * <p>Silent on either sentinel — an unsampled or empty leg is not evidence of disagreement.
     * That silence is no longer invisible: {@link #modelLeg} reports it as
     * {@link LegCheck#NOT_SAMPLED} and {@link Sample#triadLegsVerified()} does not count it, so
     * "this flag cannot fire here" can no longer be read as "this flag fired and found nothing".
     */
    public static boolean modelMismatch(double visualDy, double modelDy) {
        return isMeasured(modelDy) && Math.abs(modelDy - visualDy) > EPS;
    }

    /** Informational: does the collision box track the visual offset (false = vanilla, main's design)? */
    public static boolean collisionFollowsVisual(double visualDy, double outlineMinY, double collisionMinY) {
        if (!isMeasured(outlineMinY) || !isMeasured(collisionMinY)) {
            return false;
        }
        // Collision "follows" if it sits at the same offset as the (correctly-offset) outline.
        return Math.abs(collisionMinY - outlineMinY) < EPS && Math.abs(visualDy) > EPS;
    }

    public static boolean isConnectableDecoration(BlockState state) {
        Block b = state.getBlock();
        return b instanceof ChainBlock || b instanceof LanternBlock;
    }

    private static String anchorState(BlockView world, BlockPos pos) {
        if (SlabAnchorAttachment.isFrozenFlat(world, pos)) {
            return "FROZEN-FLAT";
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return "ANCHORED";
        }
        return "none";
    }

    /**
     * {@code minY} of a shape, or a sentinel that says WHICH kind of nothing was seen:
     * {@link #MEASURED_EMPTY} when the API answered with an empty shape (the normal, correct
     * answer for {@code getRaycastShape} on any block that does not override it), and
     * {@link #NOT_SAMPLED} when there was no shape object to ask at all.
     */
    private static double minY(VoxelShape shape) {
        if (shape == null) {
            return NOT_SAMPLED;
        }
        return shape.isEmpty() ? MEASURED_EMPTY : shape.getBoundingBox().minY;
    }

    private static String blockId(BlockState state) {
        var id = Registries.BLOCK.getId(state.getBlock());
        return id == null ? "?" : id.toString();
    }

    /**
     * Renders a leg for a human or for the append-only recorder log. The two sentinels print as
     * DIFFERENT tokens on purpose, so a future reader of a session file can tell "this leg was
     * never sampled" from "this leg was sampled and is empty" without re-deriving either.
     */
    public static String format(double v) {
        if (Double.isNaN(v)) {
            return "NOT_SAMPLED";
        }
        if (v == MEASURED_EMPTY) {
            return "EMPTY";
        }
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
