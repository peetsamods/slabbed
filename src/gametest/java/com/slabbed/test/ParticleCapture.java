package com.slabbed.test;

import com.slabbed.particle.BlockDisplayParticleContext;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Test-only recorder for particle calls made by real vanilla block display ticks. */
public final class ParticleCapture {

    private static final ThreadLocal<List<Emission>> ACTIVE = new ThreadLocal<>();

    private ParticleCapture() {
    }

    public static void begin() {
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("particle capture is already active");
        }
        ACTIVE.set(new ArrayList<>());
    }

    public static List<Emission> finish() {
        List<Emission> emissions = ACTIVE.get();
        if (emissions == null) {
            throw new IllegalStateException("particle capture is not active");
        }
        ACTIVE.remove();
        return List.copyOf(emissions);
    }

    public static void reset() {
        ACTIVE.remove();
    }

    public static void record(
            ParticleEffect effect, boolean important, boolean alwaysSpawn,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ
    ) {
        List<Emission> emissions = ACTIVE.get();
        if (emissions != null) {
            BlockPos owner = BlockDisplayParticleContext.activeOwner();
            emissions.add(new Emission(effect, important, alwaysSpawn,
                    x, y, z, velocityX, velocityY, velocityZ,
                    owner, BlockDisplayParticleContext.activeDy()));
        }
    }

    public record Emission(
            ParticleEffect effect,
            boolean important,
            boolean alwaysSpawn,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            BlockPos activeOwner,
            double activeDy
    ) {
    }
}
