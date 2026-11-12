package com.slabbed.test;

import net.minecraft.particle.ParticleEffect;
import java.util.ArrayList;
import java.util.List;

/** Test-only final-sink samples, including zero-height emissions. */
public final class ParticleSinkSamples {
    private static List<Sample> active;
    public static void begin() { active = new ArrayList<>(); }
    public static List<Sample> finish() { var result = List.copyOf(active); active = null; return result; }
    public static void record(ParticleEffect effect, boolean important, boolean alwaysSpawn,
                              double x, double y, double z, double vx, double vy, double vz) {
        if (active != null) active.add(new Sample(effect.getType(), important, alwaysSpawn, x,y,z,vx,vy,vz));
    }
    public record Sample(Object type, boolean important, boolean alwaysSpawn,
                         double x, double y, double z, double vx, double vy, double vz) {}
}
