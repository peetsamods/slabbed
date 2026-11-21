package com.slabbed.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * One stored placement offset per Fancy World Animations animation. Only the client thread reads the
 * world and publishes it; drawing, frustum checks and occlusion workers read the published value, so a
 * culling thread never performs a live height lookup.
 */
public interface FwaPlacementSeat {
    void slabbed$publishSeat(double dy);

    /** The last client-published offset, or NaN before the first publication. Safe from any thread. */
    double slabbed$publishedSeat();

    /** Publishes the stored offset on the client thread; elsewhere it only returns the last published value. */
    static double publish(Object animation, BlockPos pos) {
        if (!(animation instanceof FwaPlacementSeat seat)) return 0;
        Minecraft client = Minecraft.getInstance();
        var world = client.level;
        if (world == null || pos == null || !client.isSameThread()) {
            double known = seat.slabbed$publishedSeat();
            return Double.isNaN(known) ? 0 : known;
        }
        double dy = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        if (!Double.isFinite(dy)) dy = 0;
        seat.slabbed$publishSeat(dy);
        return dy;
    }

    /** The offset published for this frame, publishing first only if nothing has been published yet. */
    static double current(Object animation, BlockPos pos) {
        if (animation instanceof FwaPlacementSeat seat) {
            double known = seat.slabbed$publishedSeat();
            if (!Double.isNaN(known)) return known;
        }
        return publish(animation, pos);
    }
}
