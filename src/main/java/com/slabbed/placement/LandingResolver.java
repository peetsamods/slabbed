package com.slabbed.placement;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * THE LANDING RESOLVER — the pure landing computation of the unified GOES rule
 * ({@code docs/design/GOES-UNIFIED-LANDING-RULE.md} §1.2-1.3): a placement lands on the clicked
 * visible surface. Given the aim (the clicked owner and face) it returns the offset that seats the
 * placed block's family attachment plane exactly on the owner's visible plane — for every owner
 * shape and every depth, on all six faces — computed ONCE at placement and frozen (LAW.md).
 *
 * <p><b>C2-C5 scope</b> ({@code §4.4} rows C2-C5): wired for slabs, ordinary full blocks,
 * reciprocal floor-seated pairs, ordinary object blocks, UP-face AIM-KEYED thin layers, and
 * use-created full-cube contact blocks.
 * NOTE (reviewer amendment A2, disclosed deviation): the design's §1.3.5 occlusion REFUSAL is NOT in
 * C2 — PlacementResolution has no refusal field and this resolver never cancels a placement, so a
 * deep DOWN-face placement can mint a body intersecting an existing visible body until the refusal
 * lands (C3+). The design signature's {@code refusal?} output is deferred with it.
 *
 * <p>Recursion-safe (design R3): {@code ownerVisibleDy} reads the frozen store first and only falls
 * back to the PUBLIC {@link SlabSupport#getYOffset} (never the {@code getYOffsetInner} internals),
 * invoked from the placement context outside any {@code IN_GET_Y_OFFSET} guard. Fluid-blind (design
 * §1.3.7): fluid state is never consulted.
 */
public final class LandingResolver {

    private LandingResolver() {
    }

    /** The item families the resolver authors through C5. */
    public enum Family {
        /** A vanilla slab item (any {@link SlabBlock}). */
        SLAB,
        /** An ordinary opaque full cube (design row 4; excludes powder snow / thin layers / entity blocks). */
        FULL_BLOCK,
        /** A reciprocal DOUBLE_BLOCK_HALF or BED_PART pair owned by C3. */
        PAIRED_FLOOR_SEAT,
        /** An ordinary C4 object block, including partial shapes and block entities. */
        OBJECT,
        /** A C5 thin layer, seated only by a captured UP-face aim. */
        AIM_KEYED_FLOOR_SEAT,
        /** A full-cube block created by use, aligned to the captured contact on all six faces. */
        USE_CREATED_FULL_CUBE_CONTACT,
        /** Air and invalid states. */
        UNSUPPORTED
    }

    /**
     * Test-only compat seam. Terrain Slabs is absent from the headless game-test classpath, so a
     * focused test may stand in for its runtime on-top registry. Null in production.
     */
    public static volatile Predicate<BlockState> compatFinalStateTestOverride = null;

    /** The resolver's decision for a single placement: the height to freeze at {@code targetCell}. */
    public record PlacementResolution(BlockPos targetCell, double landingDy, boolean merge) {
    }

    /** Immutable root aim captured before recursive remaps and vanilla context transformation. */
    public record PlacementAim(
            BlockPos ownerPos,
            BlockState ownerState,
            double ownerVisibleDy,
            Direction clickedFace,
            Vec3 hitLocation,
            boolean replacementSameCell
    ) {
        public PlacementAim {
            ownerPos = ownerPos.immutable();
        }
    }

    public static PlacementAim captureAim(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos ownerPos = context.getClickedPos().immutable();
        BlockState ownerState = level.getBlockState(ownerPos);
        double ownerVisibleDy = ownerState.isAir()
                || CompatHooks.shouldSkipOffset(ownerState)
                || CompatHooks.shouldSkipSlabSupport(ownerState)
                ? 0.0d
                : visibleOwnerDy(level, ownerPos, ownerState);
        return new PlacementAim(
                ownerPos,
                ownerState,
                ownerVisibleDy,
                context.getClickedFace(),
                context.getClickLocation(),
                ownerState.canBeReplaced(new BlockPlaceContext(context)));
    }

    /**
     * Aim reconstruction for a placement whose cell was RELOCATED by the item's own
     * {@code updatePlacementContext} — a walk away from the clicked cell (vanilla scaffolding walks
     * its column and builds at the far end). The relocated context's own hit sits at the destination
     * with the WALK direction as its face, so the walk's SOURCE cell — the destination's neighbour
     * against that face — is the real owner. Feeding that to {@link #resolve} applies the same
     * arithmetic a direct aim on the source would: a flush source yields a flush destination, and a
     * lowered source yields its own recorded seat.
     *
     * <p>The compat guards mirror {@link #captureAim} exactly. They must not be thinned here: the
     * direct-aim path zeroes a compat-owned owner's dy, so a relocated capture that read one would
     * seat a relocated placement on a height the direct path refuses to use.
     */
    public static PlacementAim captureRelocatedAim(BlockPlaceContext context) {
        Level level = context.getLevel();
        Direction walkFace = context.getClickedFace();
        BlockPos ownerPos = context.getClickedPos().relative(walkFace.getOpposite()).immutable();
        BlockState ownerState = level.getBlockState(ownerPos);
        double ownerVisibleDy = ownerState.isAir()
                || CompatHooks.shouldSkipOffset(ownerState)
                || CompatHooks.shouldSkipSlabSupport(ownerState)
                ? 0.0d
                : visibleOwnerDy(level, ownerPos, ownerState);
        return new PlacementAim(
                ownerPos,
                ownerState,
                ownerVisibleDy,
                walkFace,
                context.getClickLocation(),
                false);
    }

    /**
     * Classifies the held/placed block into its placement-time resolver family.
     */
    public static Family classify(BlockState placedState) {
        if (placedState == null || placedState.isAir()) {
            return Family.UNSUPPORTED;
        }
        if (placedState.getBlock() instanceof SlabBlock) {
            return Family.SLAB;
        }
        if (placedState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || (placedState.getBlock() instanceof BedBlock
                && placedState.hasProperty(BlockStateProperties.BED_PART))) {
            return Family.PAIRED_FLOOR_SEAT;
        }
        if (placedState.getBlock() instanceof PowderSnowBlock) {
            return Family.USE_CREATED_FULL_CUBE_CONTACT;
        }
        if (SlabSupport.isThinTopLayer(placedState)) {
            return Family.AIM_KEYED_FLOOR_SEAT;
        }
        if (placedState.getBlock() instanceof EntityBlock) {
            return Family.OBJECT;
        }
        return placedState.isSolidRender() ? Family.FULL_BLOCK : Family.OBJECT;
    }

    /**
     * One final-state ownership gate shared by capture, landing, and hit validation. Terrain Slabs'
     * on-top registry already owns model, outline, and raycast for its members, so Slabbed must not
     * author a second stored dy for them.
     */
    public static boolean compatOwnsFinalState(BlockState state) {
        if (state == null) {
            return false;
        }
        Predicate<BlockState> override = compatFinalStateTestOverride;
        return (override != null && override.test(state))
                || CompatHooks.shouldSkipOffset(state)
                || CompatHooks.shouldSkipSlabSupport(state)
                || CompatHooks.terrainSlabsHandlesObjectOffset(state);
    }

    /**
     * Resolves the landing height for a placement whose target cell vanilla has already chosen
     * (the C2 capture-at-RETURN caller: {@code placedPos} is the filled cell, {@code placedState}
     * its state, {@code clickedFace} the face the player clicked on the owner).
     *
     * <p>Returns {@code null} when the family is not resolver-owned in C2 (caller leaves today's
     * capture untouched).
     */
    public static PlacementResolution resolve(
            BlockGetter world,
            BlockPos placedPos,
            BlockState placedState,
            Direction clickedFace,
            Family family) {
        if (world == null || placedPos == null || placedState == null || clickedFace == null
                || family == Family.UNSUPPORTED) {
            return null;
        }
        if (compatOwnsFinalState(placedState)) {
            return null;
        }
        if (family == Family.USE_CREATED_FULL_CUBE_CONTACT) {
            // Use-created contact requires the immutable root aim; reconstructing from the final
            // cell cannot preserve same-cell replacement or the player's original contact face.
            return null;
        }
        if (family == Family.AIM_KEYED_FLOOR_SEAT && clickedFace != Direction.UP) {
            return null;
        }

        // Same-cell DOUBLE merge (design §1.3.4 / family row 3): a fresh slab is never DOUBLE, so a
        // DOUBLE placedState means the click merged into the owner's own cell. Re-store the owner's
        // stored dy for the merged state (the value already at this cell), explicitly.
        if (family == Family.SLAB
                && placedState.getBlock() instanceof SlabBlock
                && placedState.hasProperty(SlabBlock.TYPE)
                && placedState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
            double dy = visibleOwnerDy(world, placedPos, placedState);
            return new PlacementResolution(placedPos, dy, true);
        }

        BlockPos ownerPos = placedPos.relative(clickedFace.getOpposite());
        BlockState ownerState = world.getBlockState(ownerPos);
        double ownerVisibleDy = visibleOwnerDy(world, ownerPos, ownerState);

        double landingDy;
        if (clickedFace == Direction.UP) {
            // §1.3.1 UP face (floor-seated). Lands on the owner's VISIBLE top plane. Includes the A-5
            // TOP-owner+UP precedence: a lowered TOP-type slab owner seats the placement flush on its
            // visible top (rule 1), never a DOUBLE merge (rule 4 is BOTTOM+UP, handled above).
            landingDy = ownerVisibleDy + topPlaneOffset(ownerState) - 1.0;
        } else if (clickedFace == Direction.DOWN) {
            // §1.3.2 DOWN face (hanging / underside). Lands relative to the support's visible bottom.
            landingDy = ownerVisibleDy + bottomPlaneOffset(ownerState);
        } else {
            // §1.3.3 horizontal faces (side attachment). The placed block adopts the owner's frame.
            landingDy = ownerVisibleDy;
        }
        return new PlacementResolution(placedPos, landingDy, false);
    }

    /** C3 resolver using the frozen root aim rather than reconstructing an owner from the target. */
    public static PlacementResolution resolve(
            BlockGetter world,
            PlacementAim aim,
            BlockPos actualTarget,
            BlockState finalState,
            Family family
    ) {
        if (aim == null || actualTarget == null || finalState == null || finalState.isAir()
                || family == Family.UNSUPPORTED
                || compatOwnsFinalState(finalState)) {
            return null;
        }
        if ((family == Family.PAIRED_FLOOR_SEAT || family == Family.AIM_KEYED_FLOOR_SEAT)
                && aim.clickedFace() != Direction.UP) {
            return null;
        }
        if (family == Family.SLAB
                && finalState.getBlock() instanceof SlabBlock
                && finalState.hasProperty(SlabBlock.TYPE)
                && finalState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE
                && aim.ownerPos().equals(actualTarget)) {
            return new PlacementResolution(actualTarget, aim.ownerVisibleDy(), true);
        }
        if ((family == Family.AIM_KEYED_FLOOR_SEAT
                || family == Family.USE_CREATED_FULL_CUBE_CONTACT)
                && aim.replacementSameCell()
                && aim.ownerPos().equals(actualTarget)) {
            return new PlacementResolution(actualTarget, aim.ownerVisibleDy(), true);
        }
        // SAME-CELL REPLACEMENT (the remaining families): the clicked owner IS the cell being filled —
        // grass, a fern, one-layer snow being replaced. The owner is the thing being DESTROYED, so its
        // plane can seat nothing; treating it like a clicked neighbour minted a height one whole cell
        // wrong (measured: stone through grass over a bottom slab froze +0.5 in the test config, +1.0
        // shipped, against a correct -0.5), which LAW 1 then preserved. Seat on the REAL support below
        // the cell instead — exactly what the control placement without the plant computes.
        // (The two same-cell merge families above keep the cell's own height on purpose: a second
        // candle or snow layer merges INTO the existing block rather than replacing scenery.)
        if (world != null && aim.ownerPos().equals(actualTarget)) {
            BlockPos supportPos = actualTarget.below();
            BlockState supportState = world.getBlockState(supportPos);
            double supportDy = supportState.isAir() ? 0.0
                    : visibleOwnerDy(world, supportPos, supportState);
            double replacedSeat = supportPos.getY() + supportDy + topPlaneOffset(supportState)
                    - actualTarget.getY();
            return Double.isFinite(replacedSeat)
                    ? new PlacementResolution(actualTarget, replacedSeat, false)
                    : null;
        }
        double landingDy;
        if (aim.clickedFace() == Direction.UP) {
            landingDy = aim.ownerPos().getY() + aim.ownerVisibleDy() + topPlaneOffset(aim.ownerState())
                    - actualTarget.getY();
        } else if (aim.clickedFace() == Direction.DOWN) {
            boolean flushTopVerticalChainBridge =
                    finalState.getBlock() instanceof ChainBlock
                            && finalState.hasProperty(BlockStateProperties.AXIS)
                            && finalState.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y
                            && aim.ownerState().getBlock() instanceof SlabBlock
                            && aim.ownerState().getValue(SlabBlock.TYPE) == SlabType.TOP
                            && Double.doubleToRawLongBits(aim.ownerVisibleDy())
                            == Double.doubleToRawLongBits(0.0d)
                            && actualTarget.equals(aim.ownerPos().below());
            // A flush TOP vertical chain intentionally stays at frozen dy=0 so its 24px bridge reaches
            // the support. Lowered chains keep the generic underside formula (for example -2.0 -> -1.5).
            landingDy = flushTopVerticalChainBridge
                    ? 0.0d
                    : aim.ownerPos().getY() + aim.ownerVisibleDy() + bottomPlaneOffset(aim.ownerState())
                            - (actualTarget.getY() + 1.0d);
        } else {
            landingDy = aim.ownerVisibleDy() + aim.ownerPos().getY() - actualTarget.getY();
        }
        return Double.isFinite(landingDy)
                ? new PlacementResolution(actualTarget, landingDy, false)
                : null;
    }

    /**
     * The single "how deep is the surface I clicked" authority (design §1.2): the frozen store first,
     * then the PUBLIC live read. TS-owned owners render flush ⇒ 0.0.
     */
    public static double visibleOwnerDy(BlockGetter world, BlockPos ownerPos, BlockState ownerState) {
        if (ownerState == null || ownerState.isAir() || CompatHooks.shouldSkipOffset(ownerState)) {
            return 0.0;
        }
        double stored = SlabAnchorAttachment.storedPlacementDy(world, ownerPos);
        if (Double.isFinite(stored)) {
            return stored;
        }
        double live = SlabSupport.getYOffset(world, ownerPos, ownerState);
        return Double.isFinite(live) ? live : 0.0;
    }

    /** Visible top-plane offset within the owner's cell: bottom slab 0.5; full / TOP / DOUBLE 1.0. */
    private static double topPlaneOffset(BlockState ownerState) {
        if (ownerState.getBlock() instanceof SlabBlock && ownerState.hasProperty(SlabBlock.TYPE)) {
            return ownerState.getValue(SlabBlock.TYPE) == SlabType.BOTTOM ? 0.5 : 1.0;
        }
        return 1.0;
    }

    /** Visible bottom-plane offset within the owner's cell: full / bottom / DOUBLE 0.0; TOP slab 0.5. */
    private static double bottomPlaneOffset(BlockState ownerState) {
        if (ownerState.getBlock() instanceof SlabBlock && ownerState.hasProperty(SlabBlock.TYPE)) {
            return ownerState.getValue(SlabBlock.TYPE) == SlabType.TOP ? 0.5 : 0.0;
        }
        return 0.0;
    }
}
