package com.slabbed.placement;

import com.slabbed.anchor.SlabPlacementDyAttachment;
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

/** Pure placement-height decision built from the player's immutable root aim. */
public final class LandingResolver {

    private LandingResolver() {
    }

    public enum Family {
        SLAB,
        FULL_BLOCK,
        PAIRED_FLOOR_SEAT,
        OBJECT,
        AIM_KEYED_FLOOR_SEAT,
        USE_CREATED_FULL_CUBE_CONTACT,
        UNSUPPORTED
    }

    public record PlacementResolution(BlockPos targetCell, double landingDy, boolean sameCellUpgrade) {
    }

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
     * Aim reconstruction for a placement whose cell was RELOCATED by the item's own placement
     * context (a multi-cell walk away from the clicked cell). The relocated context's hit sits
     * at the destination cell with the walk direction as its face, so the walk's SOURCE cell —
     * the destination's neighbor against that face — is the aim owner. The destination then
     * seats via the same arithmetic {@link #resolve} applies to a direct aim on that source: a
     * flush source yields a flush destination, and a lowered source yields its own recorded dy,
     * so a relocated stack follows the real column seat instead of the stale pre-relocation aim.
     */
    public static PlacementAim captureRelocatedAim(ItemPlacementContext context) {
        World world = context.getWorld();
        Direction walkFace = context.getSide();
        BlockPos ownerPos = context.getBlockPos().offset(walkFace.getOpposite());
        BlockState ownerState = world.getBlockState(ownerPos);
        double ownerVisibleDy = ownerState.isAir()
                ? 0.0d
                : visibleOwnerDy(world, ownerPos, ownerState);
        return new PlacementAim(
                ownerPos,
                ownerState,
                ownerVisibleDy,
                walkFace,
                context.getHitPos(),
                false);
    }

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
        return placedState.isOpaqueFullCube() ? Family.FULL_BLOCK : Family.OBJECT;
    }

    /** A compatibility-owned state keeps its native behavior unless the transaction authors it. */
    public static boolean compatOwnsFinalState(BlockState state) {
        return state != null
                && (CompatHooks.shouldSkipOffset(state)
                || CompatHooks.shouldSkipSlabSupport(state));
    }

    public static PlacementResolution resolve(
            BlockView world,
            PlacementAim aim,
            BlockPos actualTarget,
            BlockState finalState,
            Family family,
            boolean authoredCompatSlabFinal
    ) {
        if (world == null || aim == null || actualTarget == null || finalState == null
                || finalState.isAir()
                || family == Family.UNSUPPORTED
                || compatOwnsFinalState(finalState) && !authoredCompatSlabFinal) {
            return null;
        }
        if (family != Family.SLAB
                && aim.clickedFace().getAxis().isHorizontal()
                && compatOwnsFinalState(aim.ownerState())) {
            return Double.isFinite(aim.ownerVisibleDy())
                    ? new PlacementResolution(actualTarget, aim.ownerVisibleDy(), false)
                    : null;
        }
        if ((family == Family.PAIRED_FLOOR_SEAT || family == Family.AIM_KEYED_FLOOR_SEAT)
                && aim.clickedFace() != Direction.UP) {
            return null;
        }

        if (family == Family.SLAB
                && finalState.contains(SlabBlock.TYPE)
                && finalState.get(SlabBlock.TYPE) == SlabType.DOUBLE
                && aim.ownerPos().equals(actualTarget)) {
            return new PlacementResolution(actualTarget, aim.ownerVisibleDy(), true);
        }
        if ((family == Family.AIM_KEYED_FLOOR_SEAT
                || family == Family.USE_CREATED_FULL_CUBE_CONTACT)
                && aim.replacementSameCell()
                && aim.ownerPos().equals(actualTarget)) {
            return new PlacementResolution(actualTarget, aim.ownerVisibleDy(), true);
        }

        // FLUSH WINS (maintainer ruling, 2026-08-17): a placement that lands in its own aim cell
        // REPLACED that cell's occupant (vanilla canReplace — plants, one-layer snow, same-item
        // decoration merges not claimed by a branch above). The replaced occupant contributes no
        // plane of its own, so the seat is the REAL support surface below the cell — the same
        // arithmetic an upward column walk applies from that support. The owner-relative
        // arithmetic below this guard describes a NEIGHBOR relationship and must never see
        // owner == target: its plane offsets degenerate by a whole cell there (+1.0 on an
        // UP-face click, -1.0 on a DOWN-face click), and the minted value would freeze
        // permanently. Behavior-keyed: the discriminator is the observed same-cell placement,
        // never a block class.
        if (actualTarget.equals(aim.ownerPos())) {
            BlockPos supportPos = actualTarget.down();
            BlockState supportState = world.getBlockState(supportPos);
            double supportDy = supportState.isAir()
                    ? 0.0d
                    : visibleOwnerDy(world, supportPos, supportState);
            double replacedSeat = supportPos.getY() + supportDy + topPlaneOffset(supportState)
                    - actualTarget.getY();
            return Double.isFinite(replacedSeat)
                    ? new PlacementResolution(actualTarget, replacedSeat, false)
                    : null;
        }

        boolean insideOwnerColumn = actualTarget.getX() == aim.ownerPos().getX()
                && actualTarget.getZ() == aim.ownerPos().getZ();
        double landingDy;
        if (aim.clickedFace() == Direction.UP && insideOwnerColumn) {
            landingDy = aim.ownerPos().getY() + aim.ownerVisibleDy() + topPlaneOffset(aim.ownerState())
                    - actualTarget.getY();
        } else if (aim.clickedFace() == Direction.DOWN && insideOwnerColumn) {
            boolean flushTopVerticalChainBridge = finalState.getBlock() instanceof ChainBlock
                    && finalState.contains(Properties.AXIS)
                    && finalState.get(Properties.AXIS) == Direction.Axis.Y
                    && aim.ownerState().getBlock() instanceof SlabBlock
                    && aim.ownerState().contains(SlabBlock.TYPE)
                    && aim.ownerState().get(SlabBlock.TYPE) == SlabType.TOP
                    && Double.doubleToRawLongBits(aim.ownerVisibleDy())
                    == Double.doubleToRawLongBits(0.0d)
                    && actualTarget.equals(aim.ownerPos().down());
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

    public static double visibleOwnerDy(BlockView world, BlockPos ownerPos, BlockState ownerState) {
        if (ownerState == null || ownerState.isAir()) {
            return 0.0d;
        }
        double stored = SlabPlacementDyAttachment.storedDy(world, ownerPos);
        if (Double.isFinite(stored)) {
            return stored;
        }
        double live = SlabSupport.getYOffset(world, ownerPos, ownerState);
        return Double.isFinite(live) ? live : 0.0d;
    }

    private static double topPlaneOffset(BlockState ownerState) {
        if (ownerState.getBlock() instanceof SlabBlock && ownerState.contains(SlabBlock.TYPE)) {
            return ownerState.get(SlabBlock.TYPE) == SlabType.BOTTOM ? 0.5d : 1.0d;
        }
        return 1.0d;
    }

    private static double bottomPlaneOffset(BlockState ownerState) {
        if (ownerState.getBlock() instanceof SlabBlock && ownerState.contains(SlabBlock.TYPE)) {
            return ownerState.get(SlabBlock.TYPE) == SlabType.TOP ? 0.5d : 0.0d;
        }
        return 0.0d;
    }
}
