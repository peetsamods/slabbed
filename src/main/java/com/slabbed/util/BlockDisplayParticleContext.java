package com.slabbed.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Carries the frozen visual height of the block whose vanilla display tick is running, so every
 * particle that tick emits lands on the drawn model instead of on the grid cell.
 *
 * <p>Invariants:
 * <ul>
 *   <li>dy is resolved LAZILY on the first particle inside a scope and memoised for the rest of it.
 *       The base {@code Block} display tick is an empty body and the client drives 1334 display
 *       ticks per client tick, so almost every scope emits nothing; reading dy in {@link #open}
 *       would pay a store lookup for each of them. Do not read dy in {@code open}.</li>
 *   <li>The {@link BlockPos} is BORROWED for the wrapped call's duration only — the caller passes
 *       the reused cursor that never outlives that call. Do not retain it and do not copy it.</li>
 *   <li>A hook that already translated an emission itself must wrap that emission in
 *       {@link #beginOwnedEmission()}/{@link #endOwnedEmission()}. While an owned emission is open
 *       this class translates nothing, or the emission moves twice.</li>
 *   <li>A non-finite dy resolves to {@code 0.0}. Never propagate NaN into a particle coordinate.</li>
 *   <li>Nesting past {@link #MAX_DEPTH} translates nothing rather than aliasing an outer frame:
 *       vanilla behaviour is the safe direction, a corrupted outer dy is not.</li>
 * </ul>
 * (maintainer ruling, 2026-09-06)
 */
public final class BlockDisplayParticleContext {

    /**
     * Frames are preallocated and reused because the opener runs once per display tick and must not
     * allocate. Vanilla only ever reaches depth 1; the extra room exists so a modded block whose
     * display tick drives another block's display tick cannot corrupt the outer frame.
     */
    private static final int MAX_DEPTH = 4;

    private static final ThreadLocal<Frames> FRAMES = ThreadLocal.withInitial(Frames::new);

    private BlockDisplayParticleContext() {
    }

    /** Opens the scope for exactly one vanilla display-tick call. Never reads dy. */
    public static void open(Level level, BlockPos pos, BlockState state) {
        Frames frames = FRAMES.get();
        // The depth always increments so that close() stays symmetric even on overflow.
        int depth = frames.depth++;
        if (depth >= MAX_DEPTH) {
            return;
        }
        Frame frame = frames.stack[depth];
        frame.level = level;
        frame.pos = pos;
        frame.state = state;
        frame.resolved = false;
        frame.dy = 0.0d;
    }

    /** Closes the scope and drops the borrowed references. MUST be called from a finally. */
    public static void close() {
        Frames frames = FRAMES.get();
        int depth = --frames.depth;
        if (depth < 0) {
            // Defensive: an unbalanced close must not leave the depth negative and disarm the scope.
            frames.depth = 0;
            return;
        }
        if (depth >= MAX_DEPTH) {
            return;
        }
        Frame frame = frames.stack[depth];
        frame.level = null;
        frame.pos = null;
        frame.state = null;
    }

    /**
     * Declares that the caller has already translated the emission it is about to make, so this
     * class must not translate it again. Balanced by {@link #endOwnedEmission()} in a finally.
     * Harmless when no scope is open, which is the direct-call and event-path case.
     */
    public static void beginOwnedEmission() {
        FRAMES.get().ownedDepth++;
    }

    /** Ends the owned emission opened by {@link #beginOwnedEmission()}. */
    public static void endOwnedEmission() {
        Frames frames = FRAMES.get();
        if (frames.ownedDepth > 0) {
            frames.ownedDepth--;
        }
    }

    /**
     * Translates a particle Y emitted inside the active scope. Returns {@code y} unchanged when no
     * scope is open, when the scope overflowed, or while an owned emission is open.
     */
    public static double translateActiveY(double y) {
        Frames frames = FRAMES.get();
        if (frames.ownedDepth > 0) {
            return y;
        }
        int depth = frames.depth - 1;
        if (depth < 0 || depth >= MAX_DEPTH) {
            return y;
        }
        Frame frame = frames.stack[depth];
        if (!frame.resolved) {
            double dy = SlabSupport.getYOffset(frame.level, frame.pos, frame.state);
            frame.dy = Double.isFinite(dy) ? dy : 0.0d;
            frame.resolved = true;
        }
        // Strict no-op at dy == 0: a flat block's emission must stay bit-identical to vanilla.
        return frame.dy == 0.0d ? y : y + frame.dy;
    }

    private static final class Frames {
        final Frame[] stack = new Frame[MAX_DEPTH];
        int depth;
        int ownedDepth;

        Frames() {
            for (int index = 0; index < MAX_DEPTH; index++) {
                stack[index] = new Frame();
            }
        }
    }

    private static final class Frame {
        Level level;
        BlockPos pos;
        BlockState state;
        boolean resolved;
        double dy;
    }
}
