package com.slabbed.client.model;

import com.slabbed.util.RailSlopeProfile;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shears a straight rail's vanilla quads to the profile {@link RailSlopeProfile} fits for it
 * (maintainer ruling, 2026-09-28).
 *
 * <p>Vanilla draws a rail as one plane: flat at 1/16, or a ramp from 1/16 to 17/16. Both are linear
 * along the rail's axis, so subtracting vanilla's rise and adding the fitted one leaves every quad
 * PLANAR — the same sprite, the same UVs, the texture stretched along the slope exactly as vanilla's
 * own ramp stretches it. Nothing is replaced and no model is registered; the vanilla quads are moved.
 *
 * <p>Composes with the seat translate: the seat is a constant added to every vertex, this adds a
 * per-vertex lift that depends only on the vertex's position along the axis, so the two commute and
 * the order the emitter applies them in cannot matter.
 */
public final class RailSlopeShear implements QuadTransform {

    private final boolean alongZ;
    private final float negativeDelta;
    private final float positiveDelta;

    private RailSlopeShear(boolean alongZ, float negativeDelta, float positiveDelta) {
        this.alongZ = alongZ;
        this.negativeDelta = negativeDelta;
        this.positiveDelta = positiveDelta;
    }

    /**
     * The shear for the rail at {@code pos}, or {@code null} when nothing needs shearing: not a
     * straight rail, or a profile vanilla already draws. The allocation-free block test runs first so
     * every non-rail block pays nothing; the profile read is render-region guarded, and a read that
     * steps outside the region border falls back to vanilla geometry for this bake, exactly like the
     * seat read does (the section re-bakes with fuller bounds a frame later).
     */
    public static QuadTransform forRail(BlockAndTintGetter view, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BaseRailBlock)) {
            return null;
        }
        RailSlopeProfile.Profile profile;
        try {
            profile = RailSlopeProfile.resolve(view, pos, state);
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return null;
        }
        if (profile == null || profile.isVanilla()) {
            return null;
        }
        return new RailSlopeShear(profile.axis() == Direction.Axis.Z,
                (float) profile.negativeDelta(), (float) profile.positiveDelta());
    }

    @Override
    public boolean transform(MutableQuadView quad) {
        for (int i = 0; i < 4; i++) {
            float x = quad.x(i);
            float z = quad.z(i);
            float t = alongZ ? z : x;
            float lift = negativeDelta * (1.0f - t) + positiveDelta * t;
            if (lift != 0.0f) {
                quad.pos(i, x, quad.y(i) + lift, z);
            }
        }
        return true;
    }
}
