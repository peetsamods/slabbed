package com.slabbed.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A lowered rail takes redstone at the cell it is DRAWN in (maintainer ruling, 2026-09-28).
 *
 * <p>A rail seated on a slab lives one grid cell above the slab but is drawn inside the slab's
 * cell. Vanilla reads a powered rail's input from the six neighbours of its GRID cell, so a torch or
 * a wire standing beside the slab — touching the drawn rail — is a diagonal to vanilla and never
 * powers it, and an unpowered powered rail is a brake. This class names the cell the rail is drawn
 * in and reads the signals arriving at that cell, so the rail is powered by what visibly touches it.
 *
 * <p>INVARIANT: this ADDS a cell the rail listens at; it never removes vanilla's own. A rail drawn in
 * its own cell (seat above -0.5) answers exactly as vanilla. The drawn cell is read exactly as
 * vanilla reads a rail's own cell — all six neighbours — so a torch under the slab a rail sits on
 * powers it the way a torch under a full block powers the rail on top.
 *
 * <p>INVARIANT (LAW.md): a read of the rail's stored seat, never a write.
 */
public final class RailVisualSignal {

    private static final double EPS = 1.0e-6d;

    private RailVisualSignal() {
    }

    /**
     * The cell the rail at {@code pos} is drawn in, or {@code null} when it is drawn in its own cell
     * (or is not a rail). A seat of -0.5 or -1.0 draws the rail in the cell below; deeper seats,
     * reachable only by a manual nudge, in the cell that far down.
     */
    public static BlockPos drawnCell(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !RailSlopeProfile.isRail(state)) {
            return null;
        }
        double seat = SlabSupport.getYOffset(world, pos, state);
        if (!Double.isFinite(seat) || seat > -0.5d + EPS) {
            return null;
        }
        int down = (int) Math.ceil(-seat - EPS);
        return down <= 0 ? null : pos.below(down);
    }

    /** True when a neighbour of the cell the rail at {@code pos} is drawn in emits toward that cell. */
    public static boolean hasDrawnCellSignal(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        BlockPos drawn = drawnCell(level, pos, level.getBlockState(pos));
        return drawn != null && level.hasNeighborSignal(drawn);
    }

    /**
     * True when the powered rail directly above {@code pos} is drawn in {@code pos}: a neighbour
     * change delivered to this cell is a change beside the drawn rail, and the rail must hear it.
     * Only rails that take input (the powered and activator rails) count.
     */
    public static boolean carriesADrawnPoweredRail(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        BlockPos above = pos.above();
        BlockState state = level.getBlockState(above);
        if (!(state.getBlock() instanceof PoweredRailBlock)) {
            return false;
        }
        return pos.equals(drawnCell(level, above, state));
    }
}
