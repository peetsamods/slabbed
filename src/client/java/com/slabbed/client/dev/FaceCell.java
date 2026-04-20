package com.slabbed.client.dev;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Classifies a {@link BlockHitResult} hit point into a 3x3 face cell label
 * (TL/TC/TR, ML/MC/MR, BL/BC/BR) and provides the short face letter
 * (N/E/S/W/T/B).
 *
 * <p>Labels use a stable world-compass convention so repro descriptions
 * stay unambiguous regardless of player facing:
 * <ul>
 *   <li>For UP/DOWN faces, the face plane is X-Z.
 *       Row is derived from Z (low Z = north = T, high Z = south = B).
 *       Column is derived from X (low X = west = L, high X = east = R).</li>
 *   <li>For N/S faces, the face plane is X-Y.
 *       Row is derived from Y (high Y = T, low Y = B).
 *       Column is derived from X (low X = west = L, high X = east = R).</li>
 *   <li>For E/W faces, the face plane is Y-Z.
 *       Row is derived from Y (high Y = T, low Y = B).
 *       Column is derived from Z (low Z = north = L, high Z = south = R).</li>
 * </ul>
 */
final class FaceCell {
    private FaceCell() {
    }

    static String classify(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Vec3d p = hit.getPos();
        double lx = clamp01(p.x - pos.getX());
        double ly = clamp01(p.y - pos.getY());
        double lz = clamp01(p.z - pos.getZ());

        double row;
        double col;
        switch (hit.getSide()) {
            case UP, DOWN -> {
                row = lz;
                col = lx;
            }
            case NORTH, SOUTH -> {
                row = 1.0 - ly;
                col = lx;
            }
            case EAST, WEST -> {
                row = 1.0 - ly;
                col = lz;
            }
            default -> {
                row = 0.5;
                col = 0.5;
            }
        }
        return "" + rowLetter(row) + colLetter(col);
    }

    static String faceLetter(Direction side) {
        return switch (side) {
            case UP -> "T";
            case DOWN -> "B";
            case NORTH -> "N";
            case SOUTH -> "S";
            case EAST -> "E";
            case WEST -> "W";
        };
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static char rowLetter(double row) {
        if (row < 1.0 / 3.0) return 'T';
        if (row < 2.0 / 3.0) return 'M';
        return 'B';
    }

    private static char colLetter(double col) {
        if (col < 1.0 / 3.0) return 'L';
        if (col < 2.0 / 3.0) return 'C';
        return 'R';
    }
}
