package com.slabbed.placement;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.PowderSnowBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.function.Predicate;

/**
 * THE LANDING RESOLVER — the pure landing computation of the unified GOES rule: a placement lands on
 * the clicked visible surface. Given the aim (the clicked owner and face) it returns the offset that
 * seats the placed block's family attachment plane exactly on the owner's visible plane — for every
 * owner shape and every depth, on all six faces — decided ONCE at placement and then frozen (LAW.md).
 *
 * <p>Every number here comes from the aim, never from the neighbourhood of the cell being filled, so
 * nothing this class returns can change when something is later built or broken nearby.
 *
 * <p>The arithmetic is absolute-Y and exact: no epsilons, no rounding. Callers compare raw bits.
 *
 * <p>Recursion-safe: {@code ownerVisibleDy} reads the frozen store first and only falls back to the
 * PUBLIC {@link SlabSupport#getYOffset} (never the {@code getYOffsetInner} internals), called from the
 * placement context outside any {@code IN_GET_Y_OFFSET} guard. Fluid-blind: fluid state is never read.
 *
 * <p>PORT NOTES (1.21.1 vs the 26.2 donor):
 * <ul>
 *   <li>{@code classify} is deliberately VIEW-FREE. It is handed default states that have no position
 *       and is reachable from paths sitting inside the {@code IN_GET_Y_OFFSET} recursion guard, where a
 *       view-dependent probe silently degrades to a wrong answer. The donor's no-arg
 *       {@code BlockState.isSolidRender()} does not exist in Yarn 1.21.1, so the full-cube test is
 *       {@link SlabSupport#isOpaqueFullCubeViewIndependent} (the state's own cached opacity flag plus
 *       an EmptyBlockView/ORIGIN shape probe). {@code isSolidBlock(world,pos)} is NOT used — it is the
 *       view-dependent call that tears world holes across Terrain Slabs terrain.</li>
 *   <li>The donor's third compat gate {@code terrainSlabsHandlesObjectOffset} has no counterpart on
 *       this line, so {@link #compatOwnsFinalState} is the narrower
 *       {@code shouldSkipOffset || shouldSkipSlabSupport} pair this line already uses at its C3 capture
 *       seam. Consequence of the narrowing: a state Terrain Slabs owns ONLY through its on-top object
 *       registry (and not through either skip hook) is not excluded here.</li>
 *   <li>The donor's legacy {@code BlockView} overload of {@code resolve} is not ported — the aim
 *       overload is the only entry, so no caller can reconstruct an owner from the filled cell.</li>
 * </ul>
 */
public final class LandingResolver {

    private LandingResolver() {
    }

    /** The item families the resolver authors. */
    public enum Family {
        /** A vanilla slab item (any {@link SlabBlock}). */
        SLAB,
        /** An ordinary opaque full cube (excludes powder snow / thin layers / block-entity blocks). */
        FULL_BLOCK,
        /** A reciprocal DOUBLE_BLOCK_HALF or BED_PART pair (doors, tall plants, beds). */
        PAIRED_FLOOR_SEAT,
        /** An ordinary object block, including partial shapes and block-entity blocks. */
        OBJECT,
        /** A thin layer, seated only by a captured UP-face aim. */
        AIM_KEYED_FLOOR_SEAT,
        /** A full-cube block created by use (powder snow bucket), aligned to the contact on all six faces. */
        USE_CREATED_FULL_CUBE_CONTACT,
        /** Air and invalid states. */
        UNSUPPORTED
    }

    /**
     * Test-only compat seam. Terrain Slabs is absent from the headless game-test classpath, so a
     * focused test may stand in for its runtime on-top registry. Null in production.
     */
    public static volatile Predicate<BlockState> compatFinalStateTestOverride = null;

    /**
     * The resolver's decision for a single placement: the height to freeze at {@code targetCell}.
     *
     * <p>{@code sameCellUpgrade} marks the two cases where the aim landed in the owner's OWN cell (the
     * same-cell DOUBLE upgrade, and a replaceable same-cell state) rather than a fresh cell.
     */
    public record PlacementResolution(BlockPos targetCell, double landingDy, boolean sameCellUpgrade) {
    }

    /** Immutable root aim captured before recursive remaps and vanilla context transformation. */
    public record PlacementAim(
            BlockPos ownerPos,
            BlockState ownerState,
            double ownerVisibleDy,
            Direction clickedFace,
            Vec3d hitLocation,
            boolean replacementSameCell
    ) {
        public PlacementAim {
            ownerPos = ownerPos.toImmutable();
        }
    }

    public static PlacementAim captureAim(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos ownerPos = context.getBlockPos().toImmutable();
        BlockState ownerState = world.getBlockState(ownerPos);
        double ownerVisibleDy = ownerState.isAir()
                || CompatHooks.shouldSkipOffset(ownerState)
                || CompatHooks.shouldSkipSlabSupport(ownerState)
                ? 0.0d
                : visibleOwnerDy(world, ownerPos, ownerState);
        return new PlacementAim(
                ownerPos,
                ownerState,
                ownerVisibleDy,
                context.getSide(),
                context.getHitPos(),
                ownerState.canReplace(new ItemPlacementContext(context)));
    }

    /**
     * Classifies the held/placed block into its placement-time resolver family.
     *
     * <p>The order of the tests is load-bearing and must not be rearranged: powder snow is a full cube
     * that is NOT a thin layer, thin layers are not block-entity blocks, and the opaque-full-cube test
     * is the last resort rather than the first.
     *
     * <p>VIEW-FREE by contract (see the class javadoc): no {@code world}/{@code pos} may be consulted
     * from here.
     */
    public static Family classify(BlockState placedState) {
        if (placedState == null || placedState.isAir()) {
            return Family.UNSUPPORTED;
        }
        if (placedState.getBlock() instanceof SlabBlock) {
            return Family.SLAB;
        }
        if (placedState.contains(Properties.DOUBLE_BLOCK_HALF)
                || (placedState.getBlock() instanceof BedBlock
                && placedState.contains(Properties.BED_PART))) {
            return Family.PAIRED_FLOOR_SEAT;
        }
        if (placedState.getBlock() instanceof PowderSnowBlock) {
            return Family.USE_CREATED_FULL_CUBE_CONTACT;
        }
        if (SlabSupport.isThinTopLayer(placedState)) {
            return Family.AIM_KEYED_FLOOR_SEAT;
        }
        if (placedState.getBlock() instanceof BlockEntityProvider) {
            return Family.OBJECT;
        }
        return SlabSupport.isOpaqueFullCubeViewIndependent(placedState)
                ? Family.FULL_BLOCK
                : Family.OBJECT;
    }

    /**
     * One final-state ownership gate shared by capture, landing, and hit validation. A compat mod that
     * already owns a state's model, outline, and raycast owns its stored height too, so Slabbed must
     * not author a second one for it.
     */
    public static boolean compatOwnsFinalState(BlockState state) {
        if (state == null) {
            return false;
        }
        Predicate<BlockState> override = compatFinalStateTestOverride;
        return (override != null && override.test(state))
                || CompatHooks.shouldSkipOffset(state)
                || CompatHooks.shouldSkipSlabSupport(state);
    }

    /**
     * Resolves the landing height from the frozen root aim, for the cell vanilla actually filled.
     *
     * @return the decision, or {@code null} when the resolver does not own this placement and the
     *         caller must keep its own placement-time reading
     */
    public static PlacementResolution resolve(
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

        // Same-cell DOUBLE upgrade: a fresh slab is never DOUBLE, so a DOUBLE final state in the
        // owner's own cell means the click grew the piece that was already there. Its height was
        // decided when the first slab landed; re-store that exact value for the upgraded state.
        if (family == Family.SLAB
                && finalState.getBlock() instanceof SlabBlock
                && finalState.contains(SlabBlock.TYPE)
                && finalState.get(SlabBlock.TYPE) == SlabType.DOUBLE
                && aim.ownerPos().equals(actualTarget)) {
            return new PlacementResolution(actualTarget, aim.ownerVisibleDy(), true);
        }
        // Same-cell replacement: a thin layer stacking on itself, or a bucket filling a replaceable
        // cell. The aim named the owner's own cell, so the landing is the owner's own height.
        if ((family == Family.AIM_KEYED_FLOOR_SEAT
                || family == Family.USE_CREATED_FULL_CUBE_CONTACT)
                && aim.replacementSameCell()
                && aim.ownerPos().equals(actualTarget)) {
            return new PlacementResolution(actualTarget, aim.ownerVisibleDy(), true);
        }

        // The UP/DOWN plane formulas below say "the placed block rests ON (or UNDER) the owner's
        // visible plane". That premise only holds while the target stays in the owner's own vertical
        // column. A self-transforming item (scaffolding is the live case) can make vanilla put the
        // block BESIDE the owner off an UP-face click; there the block rests on nothing of the
        // owner's, and the plane formula answers +1.0 — a full cube rendered above its own cell,
        // outside this line's height domain entirely. When the column premise fails, the placement is
        // beside the owner, so the side formula (which keeps the owner's own frame) is the honest
        // answer. DEVIATION from the 26.2 donor, deliberate: the donor carries the same arithmetic but
        // its transformed-target test asserts only finiteness, so the value was never pinned there.
        boolean insideOwnerColumn = actualTarget.getX() == aim.ownerPos().getX()
                && actualTarget.getZ() == aim.ownerPos().getZ();

        double landingDy;
        if (aim.clickedFace() == Direction.UP && insideOwnerColumn) {
            // UP face: land on the owner's VISIBLE top plane. A lowered TOP-type slab owner seats the
            // placement flush on its visible top; only a BOTTOM-type owner takes the upgrade above.
            landingDy = aim.ownerPos().getY() + aim.ownerVisibleDy() + topPlaneOffset(aim.ownerState())
                    - actualTarget.getY();
        } else if (aim.clickedFace() == Direction.DOWN && insideOwnerColumn) {
            boolean flushTopVerticalChainBridge =
                    finalState.getBlock() instanceof ChainBlock
                            && finalState.contains(Properties.AXIS)
                            && finalState.get(Properties.AXIS) == Direction.Axis.Y
                            && aim.ownerState().getBlock() instanceof SlabBlock
                            && aim.ownerState().get(SlabBlock.TYPE) == SlabType.TOP
                            && Double.doubleToRawLongBits(aim.ownerVisibleDy())
                            == Double.doubleToRawLongBits(0.0d)
                            && actualTarget.equals(aim.ownerPos().down());
            // A flush TOP vertical chain intentionally stays at frozen dy=0 so its 24px bridge reaches
            // the support. Lowered chains keep the generic underside formula (for example -2.0 -> -1.5).
            landingDy = flushTopVerticalChainBridge
                    ? 0.0d
                    : aim.ownerPos().getY() + aim.ownerVisibleDy() + bottomPlaneOffset(aim.ownerState())
                            - (actualTarget.getY() + 1.0d);
        } else {
            // Horizontal faces, and any vertical-face click whose target left the owner's column:
            // the placed block sits in the owner's own frame.
            landingDy = aim.ownerVisibleDy() + aim.ownerPos().getY() - actualTarget.getY();
        }
        return Double.isFinite(landingDy)
                ? new PlacementResolution(actualTarget, landingDy, false)
                : null;
    }

    /**
     * The single "how deep is the surface I clicked" authority: the frozen store first, then the
     * PUBLIC live read. A Terrain Slabs owned owner renders flush, so it answers 0.0.
     */
    public static double visibleOwnerDy(BlockView world, BlockPos ownerPos, BlockState ownerState) {
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
        if (ownerState.getBlock() instanceof SlabBlock && ownerState.contains(SlabBlock.TYPE)) {
            return ownerState.get(SlabBlock.TYPE) == SlabType.BOTTOM ? 0.5 : 1.0;
        }
        return 1.0;
    }

    /** Visible bottom-plane offset within the owner's cell: full / bottom / DOUBLE 0.0; TOP slab 0.5. */
    private static double bottomPlaneOffset(BlockState ownerState) {
        if (ownerState.getBlock() instanceof SlabBlock && ownerState.contains(SlabBlock.TYPE)) {
            return ownerState.get(SlabBlock.TYPE) == SlabType.TOP ? 0.5 : 0.0;
        }
        return 0.0;
    }
}
