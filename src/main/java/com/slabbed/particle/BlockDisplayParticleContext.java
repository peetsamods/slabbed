package com.slabbed.particle;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Carries one block's frozen visual height through its vanilla display-particle call. */
public final class BlockDisplayParticleContext {

    private static final ThreadLocal<Frames> FRAMES = ThreadLocal.withInitial(Frames::new);

    private BlockDisplayParticleContext() {
    }

    public static void runBlockDisplayTick(
            Block block, BlockState state, World world, BlockPos pos, Random random
    ) {
        Frames frames = FRAMES.get();
        Scope previous = frames.active;
        Scope scope = frames.free;
        if (scope == null) scope = new Scope();
        else frames.free = scope.next;
        scope.open(world, pos, state);
        frames.active = scope;
        // Every tick owns its scope, including flat and silent blocks. Resolve height only
        // when a particle needs it; an inner flat tick must not reuse an outer block's offset.
        try {
            block.randomDisplayTick(state, world, pos, random);
        } finally {
            frames.active = previous;
            scope.world = null;
            scope.state = null;
            scope.next = frames.free;
            frames.free = scope;
        }
    }

    /** Reports emission ownership without forcing a height lookup. */
    public static boolean isActive() {
        return FRAMES.get().active != null;
    }

    public static double translateActiveY(double y) {
        Scope scope = FRAMES.get().active;
        if (scope == null) {
            return y;
        }
        if (scope.translationCalls == 0) {
            scope.sourceY = y;
        }
        scope.translationCalls++;
        double dy = scope.resolveDy();
        return dy == 0.0d ? y : y + dy;
    }

    public static BlockPos activeOwner() {
        Scope scope = FRAMES.get().active;
        return scope == null || scope.resolveDy() == 0.0d ? null : scope.owner.toImmutable();
    }

    public static double activeDy() {
        Scope scope = FRAMES.get().active;
        return scope == null ? 0.0d : scope.resolveDy();
    }

    public static double activeSourceY() {
        Scope scope = FRAMES.get().active;
        return scope == null ? Double.NaN : scope.sourceY;
    }

    public static int activeTranslationCalls() {
        Scope scope = FRAMES.get().active;
        return scope == null ? 0 : scope.translationCalls;
    }

    public static void clearActiveTranslationObservation() {
        Scope scope = FRAMES.get().active;
        if (scope != null) {
            scope.sourceY = Double.NaN;
            scope.translationCalls = 0;
        }
    }

    public static double translateY(
            BlockView world, BlockPos pos, BlockState state, double y
    ) {
        double dy = SlabSupport.getYOffset(world, pos, state);
        return Double.isFinite(dy) ? y + dy : y;
    }

    private static final class Frames {
        private Scope active;
        private Scope free;
    }

    /** Reused per-thread frames avoid allocating for the many display ticks that emit nothing. */
    private static final class Scope {
        private final BlockPos.Mutable owner = new BlockPos.Mutable();
        private World world;
        private BlockState state;
        private Scope next;
        private boolean resolved;
        private double dy;
        private double sourceY = Double.NaN;
        private int translationCalls;

        private void open(World world, BlockPos owner, BlockState state) {
            this.world = world;
            this.owner.set(owner);
            this.state = state;
            next = null;
            resolved = false;
            dy = 0.0d;
            sourceY = Double.NaN;
            translationCalls = 0;
        }

        private double resolveDy() {
            if (!resolved) {
                double value = SlabSupport.getYOffset(world, owner, state);
                dy = Double.isFinite(value) ? value : 0.0d;
                resolved = true;
            }
            return dy;
        }
    }
}
