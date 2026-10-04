package com.slabbed.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A lowered redstone wire is read at the cell it is DRAWN in (maintainer ruling, 2026-09-28).
 *
 * <p>Vanilla wire steps up only onto a full conductor. A wire seated on a slab lives one grid cell
 * above the slab but is drawn inside the slab's cell, half a block above the wire beside the slab.
 * The slab is not a conductor, so the wire beside it looks UNDER the slab for a step down and never
 * at the wire drawn on top of it: power climbed the half-step (the lowered wire reads down past the
 * air over its lower neighbour) but never came back down. This class names the wire drawn in a
 * neighbouring cell so the wire beside it can read it, the way it would read a wire in that cell.
 *
 * <p>INVARIANT: this ADDS a read; it never removes vanilla's own. A wire drawn in its own cell
 * (seat above -0.5) is never counted — including vanilla's wire on a top slab or on glowstone, whose
 * one-way step is deliberate vanilla behaviour — and a wire drawn more than a block above or below
 * the reader's own drawn height is never counted, so the reach stays vanilla's one-block step.
 *
 * <p>INVARIANT (LAW.md): a read of stored seats, never a write.
 */
public final class WireVisualSignal {

    private static final double EPS = 1.0e-6d;

    /** The farthest apart two connected wires may be drawn: vanilla's own full-block step. */
    private static final double MAX_STEP = 1.0d;

    private WireVisualSignal() {
    }

    public static boolean isWire(BlockState state) {
        // Block identity, not class: the wire class is named differently on 26.2 and 26.3.
        return state != null && state.is(Blocks.REDSTONE_WIRE);
    }

    /** The drawn height of the block at {@code pos}: its grid row plus its seat (0 when it has none). */
    public static double drawnHeight(BlockGetter world, BlockPos pos, BlockState state) {
        double seat = SlabSupport.getYOffset(world, pos, state);
        return pos.getY() + (Double.isFinite(seat) ? seat : 0.0d);
    }

    /**
     * The cell the wire at {@code pos} is drawn in, or {@code null} when it is drawn in its own cell
     * (or is not a wire). A seat of -0.5 or -1.0 draws the wire in the cell below.
     */
    public static BlockPos drawnCell(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || !isWire(state)) {
            return null;
        }
        double seat = SlabSupport.getYOffset(world, pos, state);
        if (!Double.isFinite(seat) || seat > -0.5d + EPS) {
            return null;
        }
        int down = (int) Math.ceil(-seat - EPS);
        return down <= 0 ? null : pos.below(down);
    }

    /**
     * The wire drawn inside {@code neighborCell}, the cell beside the wire at {@code reader}, or
     * {@code null}. Only the wire one grid row above the neighbour cell can be drawn in it, and it
     * counts only when it is drawn there and within a block of the reader's own drawn height.
     */
    public static BlockPos wireDrawnIn(BlockGetter world, BlockPos reader, BlockPos neighborCell) {
        if (world == null || reader == null || neighborCell == null) {
            return null;
        }
        BlockPos above = neighborCell.above();
        BlockState aboveState = world.getBlockState(above);
        if (!isWire(aboveState) || !neighborCell.equals(drawnCell(world, above, aboveState))) {
            return null;
        }
        double step = drawnHeight(world, above, aboveState)
                - drawnHeight(world, reader, world.getBlockState(reader));
        return Math.abs(step) <= MAX_STEP + EPS ? above : null;
    }
}
