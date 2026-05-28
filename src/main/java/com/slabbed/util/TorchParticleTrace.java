package com.slabbed.util;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

public final class TorchParticleTrace {
    private static volatile HookEntry lastHook = HookEntry.missing();
    private static volatile ParticleEntry lastClientParticle = ParticleEntry.missing();

    private TorchParticleTrace() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("slabbed.torchParticleTrace");
    }

    public static void reset() {
        lastHook = HookEntry.missing();
        lastClientParticle = ParticleEntry.missing();
    }

    public static void recordHook(BlockPos pos, double dy, double particleY) {
        lastHook = new HookEntry(true, pos.toShortString(), dy, particleY);
    }

    public static void recordClientParticle(ParticleEffect effect, double x, double y, double z) {
        if (!enabled()) {
            return;
        }
        String particleId = Registries.PARTICLE_TYPE.getId(effect.getType()).toString();
        if (!"minecraft:smoke".equals(particleId)
                && !"minecraft:flame".equals(particleId)
                && !"minecraft:dust".equals(particleId)
                && !"minecraft:soul_fire_flame".equals(particleId)) {
            return;
        }
        lastClientParticle = new ParticleEntry(true, particleId, x, y, z);
    }

    public static HookEntry hookSnapshot() {
        return lastHook;
    }

    public static ParticleEntry clientParticleSnapshot() {
        return lastClientParticle;
    }

    public record HookEntry(boolean seen, String pos, double dy, double particleY) {
        private static HookEntry missing() {
            return new HookEntry(false, "none", 0.0, 0.0);
        }
    }

    public record ParticleEntry(boolean seen, String particleId, double x, double y, double z) {
        private static ParticleEntry missing() {
            return new ParticleEntry(false, "none", 0.0, 0.0, 0.0);
        }
    }
}
