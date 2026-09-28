package com.slabbed.client.model;

import com.slabbed.util.RailSlopeProfile;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Draws a straight rail on the profile {@link RailSlopeProfile} fits for it (maintainer ruling,
 * 2026-09-28).
 *
 * <p>Vanilla draws a rail as one plane: flat at 1/16, or a ramp from 1/16 to 17/16. Both are linear
 * along the rail's axis, so subtracting vanilla's rise and adding the fitted one leaves every quad
 * PLANAR — the same sprite, the same UVs, the texture stretched along the slope exactly as vanilla's
 * own ramp stretches it. No model is registered; vanilla's own quads are captured and re-emitted
 * with their vertices moved. A {@link RailSlopeProfile.Profile#kinked kinked} profile (a V) is drawn
 * as two planes: every quad is split at the middle of the cell, each half on its own plane.
 *
 * <p>The seat is applied here, once, for these quads: they are emitted straight into the section's
 * own emitter rather than through the seat-shifting wrapper, so a vertex written here is a vertex
 * drawn, with no second shift. Rail quads carry no cull face, so the step-seam cull work of the
 * ordinary path has nothing to do for them.
 */
public final class RailSlopeGeometry {

    private static final float EDGE = 1.0e-4f;
    private static final float MIDDLE = 0.5f;

    private RailSlopeGeometry() {
    }

    /**
     * Emits the rail at {@code pos} on its fitted profile and returns true, or returns false having
     * emitted nothing when the ordinary path must draw it: not a straight rail, or a profile vanilla
     * already draws. The allocation-free block test runs first so every non-rail block pays nothing;
     * the profile read is render-region guarded, and a read that steps outside the region border
     * falls back to vanilla geometry for this bake, exactly like the seat read does (the section
     * re-bakes with fuller bounds a frame later).
     */
    public static boolean emitIfFitted(FabricBlockStateModel model, QuadEmitter emitter,
                                       BlockAndTintGetter view, BlockPos pos, BlockState state,
                                       float dy, RandomSource random, Predicate<Direction> cullTest) {
        if (!(state.getBlock() instanceof BaseRailBlock)) {
            return false;
        }
        RailSlopeProfile.Profile profile;
        try {
            profile = RailSlopeProfile.resolve(view, pos, state);
        } catch (IndexOutOfBoundsException outsideRenderRegion) {
            return false;
        }
        if (profile == null || profile.isVanilla()) {
            return false;
        }
        Renderer renderer = Renderer.get();
        if (renderer == null) {
            return false;
        }
        MutableMesh captured = renderer.mutableMesh();
        model.emitQuads(captured.emitter(), view, pos, state, random, cullTest);
        boolean alongZ = profile.axis() == Direction.Axis.Z;
        captured.forEach(quad -> emitFitted(emitter, quad, profile, alongZ, dy));
        return true;
    }

    private static void emitFitted(QuadEmitter out, QuadView quad, RailSlopeProfile.Profile profile,
                                   boolean alongZ, float dy) {
        float[] t = new float[4];
        boolean low = false;
        boolean high = false;
        for (int i = 0; i < 4; i++) {
            t[i] = alongZ ? quad.z(i) : quad.x(i);
            low |= t[i] < MIDDLE - EDGE;
            high |= t[i] > MIDDLE + EDGE;
        }
        if (profile.kinked() && low && high) {
            int[] lowPartners = partners(quad, t, alongZ, true);
            int[] highPartners = partners(quad, t, alongZ, false);
            if (lowPartners != null && highPartners != null) {
                emitHalf(out, quad, profile, dy, t, true, lowPartners);
                emitHalf(out, quad, profile, dy, t, false, highPartners);
                return;
            }
            // A quad that does not span the cell as a rectangle cannot be split cleanly; draw it
            // whole on the fitted heights instead (only a modded rail model reaches this).
        }
        out.copyFrom(quad);
        for (int i = 0; i < 4; i++) {
            out.pos(i, quad.x(i), quad.y(i) + dy + (float) profile.liftAt(t[i]), quad.z(i));
        }
        out.emit();
    }

    /**
     * One half of a split quad: vertices inside the half keep their place; each vertex outside it
     * slides along the quad's edge toward its partner until it reaches the middle of the cell, its
     * texture coordinate sliding with it. The vertex order is unchanged, so the winding is too.
     */
    private static void emitHalf(QuadEmitter out, QuadView quad, RailSlopeProfile.Profile profile,
                                 float dy, float[] t, boolean lowHalf, int[] partners) {
        out.copyFrom(quad);
        for (int i = 0; i < 4; i++) {
            boolean inside = lowHalf ? t[i] <= MIDDLE + EDGE : t[i] >= MIDDLE - EDGE;
            if (inside) {
                out.pos(i, quad.x(i), quad.y(i) + dy + (float) profile.liftAt(t[i]), quad.z(i));
                continue;
            }
            int j = partners[i];
            float s = (t[i] - MIDDLE) / (t[i] - t[j]);
            float x = lerp(quad.x(i), quad.x(j), s);
            float y = lerp(quad.y(i), quad.y(j), s);
            float z = lerp(quad.z(i), quad.z(j), s);
            out.pos(i, x, y + dy + (float) profile.liftAt(MIDDLE), z);
            out.uv(i, lerp(quad.u(i), quad.u(j), s), lerp(quad.v(i), quad.v(j), s));
        }
        out.emit();
    }

    /**
     * For each vertex outside the half, the adjacent vertex inside it that shares its cross-axis
     * coordinate (the other end of the same edge along the rail); {@code -1} for vertices inside.
     * Null when some outside vertex has no such partner, i.e. the quad is not an axis-aligned
     * rectangle spanning the middle.
     */
    private static int[] partners(QuadView quad, float[] t, boolean alongZ, boolean lowHalf) {
        int[] partners = new int[4];
        for (int i = 0; i < 4; i++) {
            boolean inside = lowHalf ? t[i] <= MIDDLE + EDGE : t[i] >= MIDDLE - EDGE;
            if (inside) {
                partners[i] = -1;
                continue;
            }
            float cross = alongZ ? quad.x(i) : quad.z(i);
            int found = -1;
            for (int step = 1; step <= 3; step += 2) {
                int j = (i + step) & 3;
                boolean jInside = lowHalf ? t[j] <= MIDDLE + EDGE : t[j] >= MIDDLE - EDGE;
                float jCross = alongZ ? quad.x(j) : quad.z(j);
                if (jInside && Math.abs(jCross - cross) <= EDGE && Math.abs(t[i] - t[j]) > EDGE) {
                    found = j;
                    break;
                }
            }
            if (found < 0) {
                return null;
            }
            partners[i] = found;
        }
        return partners;
    }

    private static float lerp(float from, float to, float s) {
        return from + (to - from) * s;
    }
}
