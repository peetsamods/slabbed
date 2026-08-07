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
 * <p>Detection heuristics (each a small, unit-tested predicate):
 * <ul>
 *   <li><b>triadMismatch</b> — the outline/raycast shape did NOT get offset by the same dy
 *       as the visual/model. Robust signal: collision is never offset on main, so
 *       {@code outlineMinY - collisionMinY} is the offset actually applied to the outline;
 *       if it disagrees with {@code visualDy}, the wireframe/raycast is a phantom (the exact
 *       "vanilla slabs fence raycast broken" class of bug).</li>
 *   <li><b>dodoRisk</b> — an opaque full cube rendered at a nonzero dy. The chunk mesher
 *       culls its faces at the grid voxel while it renders lowered → see-through hole
 *       ("doom infinity window").</li>
 *   <li><b>smooshRisk</b> — a decoration (not a slab, not a full cube) lowered a FULL block
 *       or more; the classic double-offset "smoosh" where two systems (e.g. Terrain Slabs'
 *       own offset + Slabbed's) both fire.</li>
 *   <li><b>dyDiscontinuity{Above,Below}</b> — two vertically-adjacent connectable
 *       decorations (chain/lantern) at different dy, i.e. a visible gap in a hanging stack
 *       (chain-to-lantern gap).</li>
 *   <li><b>modelMismatch</b> — client-only: the rendered model dy disagrees with the
 *       authoritative visual dy.</li>
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
            boolean triadMismatch,
            boolean dodoRisk,
            boolean smooshRisk,
            boolean dyDiscontinuityAbove,
            boolean dyDiscontinuityBelow,
            boolean collisionFollowsVisual,
            boolean modelMismatch) {

        /** True if any red flag fired — the rows a tester most wants to see. */
        public boolean anySuspect() {
            return triadMismatch || dodoRisk || smooshRisk
                    || dyDiscontinuityAbove || dyDiscontinuityBelow || modelMismatch;
        }

        /** Compact space-separated list of the flags that fired (empty string if clean). */
        public String flagSummary() {
            List<String> f = new ArrayList<>();
            if (triadMismatch) f.add("TRIAD_MISMATCH");
            if (dodoRisk) f.add("DODO");
            if (smooshRisk) f.add("SMOOSH");
            if (dyDiscontinuityAbove) f.add("GAP_ABOVE");
            if (dyDiscontinuityBelow) f.add("GAP_BELOW");
            if (modelMismatch) f.add("MODEL_MISMATCH");
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
                triadMismatch(state, visualDy, outlineMinY),
                dodoRisk(opaque, visualDy),
                smooshRisk(state, visualDy),
                dyDiscontinuity(state, above, visualDy, aboveDy),
                dyDiscontinuity(state, below, visualDy, belowDy),
                collisionFollowsVisual(visualDy, outlineMinY, collisionMinY),
                modelMismatch(visualDy, modelDy));
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
    public static boolean triadMismatch(BlockState state, double visualDy, double outlineMinY) {
        if (!isMeasured(outlineMinY) || !hasGridBasedOutline(state)) {
            return false;
        }
        return Math.abs(outlineMinY - visualDy) > EPS;
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

    /** Opaque full cube rendered at a nonzero dy → face-cull-vs-render see-through hole. */
    public static boolean dodoRisk(boolean opaqueFullCube, double visualDy) {
        return opaqueFullCube && Math.abs(visualDy) > EPS;
    }

    /** A decoration (not slab, not full cube) lowered a full block or more = double offset. */
    public static boolean smooshRisk(BlockState state, double visualDy) {
        boolean decoration = !(state.getBlock() instanceof SlabBlock) && !state.isOpaqueFullCube();
        return decoration && visualDy <= -1.0 + EPS;
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
