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
 * behind the enriched {@code /slabdy record} capture and the {@code /slabdy} overlay's live
 * red-flag line.
 *
 * <p>Ported (adapted, not transplanted) from the 1.21.11 sibling
 * ({@code <home>/CascadeProjects/Slabbed}, {@code com.slabbed.dev.SlabbedDiagnostics}, 2026-07-05).
 * The API differences vs. the sibling are mechanical: this branch has one authoritative
 * {@code SlabSupport.getYOffset} (no separate {@code getVisualYOffset}); {@code isOpaqueFullCube}
 * takes {@code (world, pos)} on 1.21.1 rather than being arg-less (used in {@link #analyze} and
 * {@link #smooshRisk}); and the state-only {@link #hasGridBasedOutline} substitutes the arg-less
 * {@code isOpaque()} for the sibling's arg-less {@code isOpaqueFullCube()} (same base-0 outline
 * set). Every pure predicate below is otherwise byte-for-byte the sibling's logic (same EPS, same
 * block-family gates) so its calibrated false-positive exclusions (TOP slabs, chains, lanterns)
 * carry over unchanged.
 *
 * <p>DEV-ONLY — lives under {@code com.slabbed.dev}, which this branch's {@code build.gradle}
 * strips from the release jar wholesale. It is reachable ONLY from the recorder path
 * ({@code /slabdy record}, itself dev-only) via reflection through
 * {@code RuntimeDiagnostics.recordVisualDiagnostic}; the always-shipped {@code /slabdy} overlay
 * and {@code /slabdy row} formatter ({@code com.slabbed.client.SlabdyRowFormatter}) do NOT
 * hard-reference this class, so a release build never links it.
 *
 * <p>Everything here is server-computable (no {@code MinecraftClient}): the visual triad
 * (outline/raycast/collision shapes + {@link SlabSupport#getYOffset}) all resolve from
 * {@code (world, pos, state)} alone, so {@link #analyze} can be exercised directly by a
 * gametest with a synthetic world. The one client-only input is the render {@code modelDy}
 * (from {@code OffsetBlockStateModel}'s trace), which callers pass in optionally.
 *
 * <p>Detection heuristics (each a small, unit-tested predicate):
 * <ul>
 *   <li><b>triadMismatch</b> — the outline/raycast shape did NOT get offset by the same dy
 *       as the visual/model. Robust signal: collision is never offset on this branch, so
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
        return analyze(world, pos, state, Double.NaN);
    }

    public static Sample analyze(BlockView world, BlockPos pos, BlockState state, double modelDy) {
        // This branch has a single authoritative dy (getYOffset); the sibling's getVisualYOffset
        // is the same authority under a different name — the outline mixin (slabbed$offsetOutline)
        // shifts the outline by exactly this value, so outlineMinY tracks it.
        double visualDy = SlabSupport.getYOffset(world, pos, state);
        double outlineMinY = minY(state.getOutlineShape(world, pos, ShapeContext.absent()));
        double raycastMinY = minY(state.getRaycastShape(world, pos));
        double collisionMinY = minY(state.getCollisionShape(world, pos, ShapeContext.absent()));
        boolean opaque = state.isOpaqueFullCube(world, pos);
        boolean slab = state.getBlock() instanceof SlabBlock;
        String anchor = anchorState(world, pos);

        BlockPos abovePos = pos.up();
        BlockState above = world.getBlockState(abovePos);
        double aboveDy = SlabSupport.getYOffset(world, abovePos, above);
        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        double belowDy = SlabSupport.getYOffset(world, belowPos, below);

        return new Sample(
                blockId(state),
                visualDy, outlineMinY, raycastMinY, collisionMinY, modelDy,
                opaque, slab, anchor,
                blockId(above), aboveDy,
                blockId(below), belowDy,
                triadMismatch(state, visualDy, outlineMinY),
                dodoRisk(opaque, visualDy),
                smooshRisk(world, pos, state, visualDy),
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
     * exposed on the sibling; the gap/DODO/smoosh checks cover those block families instead.
     */
    public static boolean triadMismatch(BlockState state, double visualDy, double outlineMinY) {
        if (Double.isNaN(outlineMinY) || !hasGridBasedOutline(state)) {
            return false;
        }
        return Math.abs(outlineMinY - visualDy) > EPS;
    }

    /**
     * True for blocks whose un-offset outline minY is exactly 0, so outlineMinY == dy once
     * the mixin offsets it. This EXCLUDES blocks with a variable base:
     * <ul>
     *   <li>TOP slabs (outline base 0.5 — a lowered top slab correctly reads outlineMinY 0.0,
     *       which a base-0 assumption misreads as a mismatch; this exact false positive was in
     *       the sibling's recorder data);</li>
     *   <li>stairs (base varies with half/shape).</li>
     * </ul>
     * DOUBLE slabs are full cubes and are caught by the opacity branch. NOTE: this predicate is
     * state-only (no world/pos). The sibling uses the arg-less {@code isOpaqueFullCube()} here, which
     * does not exist on 1.21.1 ({@code isOpaqueFullCube} requires {@code (world,pos)}). The state-only
     * {@code isOpaque()} is NOT a drop-in substitute — a TOP slab {@code isOpaque()} returns
     * {@code true} on 1.21.1 (a top slab is fully opaque for lighting) but is NOT a full cube, so a
     * naive {@code isOpaque()} branch first would misclassify the TOP slab as base-0 and re-introduce
     * exactly the recorded false positive this exclusion exists to prevent (proven RED: the TOP-slab
     * assertion failed until this ordering was fixed). The fix: classify slabs by TYPE FIRST
     * (BOTTOM→base-0 true, TOP→false, DOUBLE→full-cube true), and only then fall to {@code isOpaque()}
     * for genuine non-slab full opaque cubes (stone/dirt), whose outline base is 0. This reproduces
     * the sibling's exact truth table (BOTTOM/DOUBLE/stone/fence/wall/pane/fence-gate/bed → true;
     * TOP/stairs/decorations → false) without any (world,pos) context.
     */
    public static boolean hasGridBasedOutline(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof SlabBlock) {
            if (!state.contains(SlabBlock.TYPE)) {
                return false;
            }
            net.minecraft.block.enums.SlabType type = state.get(SlabBlock.TYPE);
            // BOTTOM (base 0) and DOUBLE (full cube, base 0) are grid-based; TOP (base 0.5) is not.
            return type == net.minecraft.block.enums.SlabType.BOTTOM
                    || type == net.minecraft.block.enums.SlabType.DOUBLE;
        }
        if (state.isOpaque()) {
            // Non-slab full opaque cube (stone/dirt): un-offset outline base is 0.
            return true;
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

    /**
     * A decoration (not slab, not full cube) lowered a full block or more = double offset.
     * The full-cube exclusion uses {@code (world, pos)} on this branch (context-sensitive
     * opacity); the sibling's arg-less form is equivalent for the block families that reach
     * this check.
     */
    public static boolean smooshRisk(BlockView world, BlockPos pos, BlockState state, double visualDy) {
        boolean decoration = !(state.getBlock() instanceof SlabBlock) && !state.isOpaqueFullCube(world, pos);
        return decoration && visualDy <= -1.0 + EPS;
    }

    /** Two vertically-adjacent connectable decorations (chain/lantern) at different dy = gap. */
    public static boolean dyDiscontinuity(BlockState a, BlockState b, double dyA, double dyB) {
        return isConnectableDecoration(a) && isConnectableDecoration(b)
                && Math.abs(dyA - dyB) > EPS;
    }

    /** Client-only: rendered model dy disagrees with the authoritative visual dy. */
    public static boolean modelMismatch(double visualDy, double modelDy) {
        return !Double.isNaN(modelDy) && Math.abs(modelDy - visualDy) > EPS;
    }

    /** Informational: does the collision box track the visual offset (false = vanilla, this branch's design)? */
    public static boolean collisionFollowsVisual(double visualDy, double outlineMinY, double collisionMinY) {
        if (Double.isNaN(outlineMinY) || Double.isNaN(collisionMinY)) {
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

    private static double minY(VoxelShape shape) {
        return shape == null || shape.isEmpty() ? Double.NaN : shape.getBoundingBox().minY;
    }

    private static String blockId(BlockState state) {
        var id = Registries.BLOCK.getId(state.getBlock());
        return id == null ? "?" : id.toString();
    }

    public static String format(double v) {
        return Double.isNaN(v) ? "NaN" : String.format(Locale.ROOT, "%.3f", v);
    }
}
