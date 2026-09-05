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

    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();

    private BlockDisplayParticleContext() {
    }

    public static void runBlockDisplayTick(
            Block block, BlockState state, World world, BlockPos pos, Random random
    ) {
        Scope previous = ACTIVE.get();
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (Double.isFinite(dy) && dy != 0.0d) {
            ACTIVE.set(new Scope(pos.toImmutable(), dy));
        } else {
            ACTIVE.remove();
        }
        try {
            block.randomDisplayTick(state, world, pos, random);
        } finally {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    public static boolean isActive() {
        return ACTIVE.get() != null;
    }

    public static double translateActiveY(double y) {
        Scope scope = ACTIVE.get();
        if (scope == null) {
            return y;
        }
        if (scope.translationCalls == 0) {
            scope.sourceY = y;
        }
        scope.translationCalls++;
        return y + scope.dy;
    }

    public static BlockPos activeOwner() {
        Scope scope = ACTIVE.get();
        return scope == null ? null : scope.owner;
    }

    public static double activeDy() {
        Scope scope = ACTIVE.get();
        return scope == null ? 0.0d : scope.dy;
    }

    public static double activeSourceY() {
        Scope scope = ACTIVE.get();
        return scope == null ? Double.NaN : scope.sourceY;
    }

    public static int activeTranslationCalls() {
        Scope scope = ACTIVE.get();
        return scope == null ? 0 : scope.translationCalls;
    }

    public static void clearActiveTranslationObservation() {
        Scope scope = ACTIVE.get();
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

    private static final class Scope {
        private final BlockPos owner;
        private final double dy;
        private double sourceY = Double.NaN;
        private int translationCalls;

        private Scope(BlockPos owner, double dy) {
            this.owner = owner;
            this.dy = dy;
        }
    }
}
