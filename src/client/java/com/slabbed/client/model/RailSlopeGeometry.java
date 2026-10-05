package com.slabbed.client.model;

import com.slabbed.util.RailSlopeProfile;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.function.Supplier;

/**
 * Draws a straight rail on the profile {@link RailSlopeProfile} fits for it (maintainer ruling,
 * 2026-09-28).
 *
 * <p>Vanilla draws a rail as one plane: flat at 1/16, or a ramp from 1/16 to 17/16. Both are linear
 * along the rail's axis, so subtracting vanilla's rise and adding the fitted one leaves every quad
 * PLANAR — the same sprite, the same UVs, the texture stretched along the slope exactly as vanilla's
 * own ramp stretches it. No model is registered; the rail model's own quads are captured and
 * re-emitted with their vertices moved. A {@link RailSlopeProfile.Profile#kinked kinked} profile (a
 * V) is drawn as two planes: every quad is split at the middle of the cell, each half on its own
 * plane.
 *
 * <p>CAPTURE on this renderer API: a quad transform can only edit a quad in place, never turn one
 * quad into two, so the V split cannot be a transform. Instead a capturing transform is pushed
 * around the wrapped model's own emission; it copies every quad into a mesh and drops it from the
 * output. The transform stack runs the most recently pushed transform first, so each quad is
 * captured before any outer transform touches it, and those outer transforms then apply exactly
 * once, to the fitted quads re-emitted through the context's own emitter. That emitter also applies
 * the context's face culling, so nothing here bypasses it. This works for every model kind the
 * wrapper holds: the renderer routes a vanilla model's quads through the same transform stack.
 *
 * <p>The seat is applied here, once, for these quads: the caller returns straight after, before
 * pushing its own seat translate, so a vertex written here is a vertex drawn with no second shift.
 * Rail quads carry no cull face, so the step-seam cull work of the ordinary path has nothing to do
 * for them.
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
     * re-bakes when its neighbourhood changes).
     */
    public static boolean emitIfFitted(BakedModel wrapped, BlockRenderView view, BlockState state,
                                       BlockPos pos, Supplier<Random> randomSupplier,
                                       RenderContext context, float dy) {
        if (!(state.getBlock() instanceof AbstractRailBlock)) {
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
        Renderer renderer = RendererAccess.INSTANCE.getRenderer();
        if (renderer == null) {
            return false;
        }
        MeshBuilder captured = renderer.meshBuilder();
        QuadEmitter capture = captured.getEmitter();
        context.pushTransform(quad -> {
            capture.copyFrom(quad);
            capture.emit();
            return false;
        });
        try {
            FabricBakedModel fabricWrapped = wrapped; // every BakedModel is one through interface injection
            if (!fabricWrapped.isVanillaAdapter()) {
                fabricWrapped.emitBlockQuads(view, state, pos, randomSupplier, context);
            } else {
                context.bakedModelConsumer().accept(wrapped, state);
            }
        } finally {
            context.popTransform();
        }
        boolean alongZ = profile.axis() == Direction.Axis.Z;
        captured.build().forEach(quad -> emitFitted(context, quad, profile, alongZ, dy));
        return true;
    }

    private static void emitFitted(RenderContext context, QuadView quad, RailSlopeProfile.Profile profile,
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
                emitHalf(context, quad, profile, dy, t, true, lowPartners);
                emitHalf(context, quad, profile, dy, t, false, highPartners);
                return;
            }
            // A quad that does not span the cell as a rectangle cannot be split cleanly; draw it
            // whole on the fitted heights instead (only a modded rail model reaches this).
        }
        QuadEmitter out = context.getEmitter();
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
    private static void emitHalf(RenderContext context, QuadView quad, RailSlopeProfile.Profile profile,
                                 float dy, float[] t, boolean lowHalf, int[] partners) {
        QuadEmitter out = context.getEmitter();
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
