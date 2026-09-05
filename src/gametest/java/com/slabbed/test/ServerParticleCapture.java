package com.slabbed.test;

import net.minecraft.particle.ParticleEffect;

import java.util.ArrayList;
import java.util.List;

/** Test-only recorder for server-origin particle requests. */
public final class ServerParticleCapture {

    private static final ThreadLocal<List<Emission>> ACTIVE = new ThreadLocal<>();

    private ServerParticleCapture() {
    }

    public static void begin() {
        ACTIVE.set(new ArrayList<>());
    }

    public static List<Emission> finish() {
        List<Emission> emissions = ACTIVE.get();
        ACTIVE.remove();
        return emissions == null ? List.of() : List.copyOf(emissions);
    }

    public static void reset() {
        ACTIVE.remove();
    }

    public static void record(
            ParticleEffect effect, double x, double y, double z, int count,
            double spreadX, double spreadY, double spreadZ, double speed
    ) {
        List<Emission> emissions = ACTIVE.get();
        if (emissions != null) {
            emissions.add(new Emission(effect, x, y, z, count,
                    spreadX, spreadY, spreadZ, speed));
        }
    }

    public record Emission(
            ParticleEffect effect, double x, double y, double z, int count,
            double spreadX, double spreadY, double spreadZ, double speed
    ) {
    }
}
