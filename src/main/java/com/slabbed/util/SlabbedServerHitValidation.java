package com.slabbed.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

/**
 * Server hit-tolerance lane: the pure center-shift decision behind
 * {@code ServerInteractBlockHitToleranceMixin}.
 *
 * <p>Vanilla {@code ServerPlayNetworkHandler.onPlayerInteractBlock} validates the
 * use-packet hit with a per-axis {@code |hit - Vec3d.ofCenter(pos)| < 1.0000001}
 * check. A block visually lowered by Slabbed's dy has its real body below the
 * cell; for a {@code -1.0} owner the legal lower-band hit is up to {@code 1.5}
 * below the vanilla center and is silently rejected (the click does nothing, no
 * error). This helper re-centers that check on the visually-correct position:
 * when the clicked owner is lowered ({@code dy < -1e-6}) the returned center is
 * {@code Vec3d.ofCenter(pos).add(0, dy, 0)}; otherwise the vanilla center is
 * returned unchanged. No tolerance widening — vanilla's own {@code 1.0000001}
 * per-axis test still runs, just against the shifted center.
 */
public final class SlabbedServerHitValidation {

    private static final double EPSILON = 1.0e-6;

    private SlabbedServerHitValidation() {
    }

    /**
     * Returns the validation center vanilla should test the use-packet hit
     * against: the visual center {@code ofCenter(pos) + (0, dy, 0)} when the
     * owner at {@code pos} is lowered ({@code dy < -1e-6}), else the vanilla
     * {@code Vec3d.ofCenter(pos)}.
     */
    public static Vec3d shiftedValidationCenter(BlockView world, BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        if (world == null || pos == null) {
            return center;
        }
        double dy = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        if (!Double.isFinite(dy) || dy >= -EPSILON) {
            return center;
        }
        return center.add(0.0d, dy, 0.0d);
    }
}
