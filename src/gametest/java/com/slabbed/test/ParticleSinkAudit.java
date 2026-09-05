package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.particle.BlockDisplayParticleContext;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.AbstractCandleBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.Map;

/** Test-mod-only observations from the final client particle renderer calls. */
public final class ParticleSinkAudit {

    private static final double EPSILON = 1.0e-9d;
    private static final Map<Long, MutableStats> BY_OWNER = new HashMap<>();
    private static final boolean LOG = Boolean.getBoolean("slabbed.particleAudit");
    private static final ThreadLocal<ForcedScope> FORCED = new ThreadLocal<>();

    private ParticleSinkAudit() {
    }

    public static synchronized void record(
            ParticleEffect effect, boolean important, boolean alwaysSpawn,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ
    ) {
        BlockPos owner = BlockDisplayParticleContext.activeOwner();
        boolean scopedTranslation = owner != null;
        ForcedScope forced = FORCED.get();
        if (owner == null) {
            if (forced == null) {
                return;
            }
            owner = forced.owner;
        }
        double dy = forced == null ? BlockDisplayParticleContext.activeDy() : forced.expectedDy;
        double sourceY = BlockDisplayParticleContext.activeSourceY();
        int translationCalls = BlockDisplayParticleContext.activeTranslationCalls();
        BlockDisplayParticleContext.clearActiveTranslationObservation();
        MutableStats stats = BY_OWNER.computeIfAbsent(owner.asLong(), ignored -> new MutableStats());
        stats.count++;
        ParticleType<?> type = effect.getType();
        if (stats.firstType == null) {
            stats.firstType = type;
        } else if (stats.firstType != type) {
            stats.mixedType = true;
        }
        stats.minY = Math.min(stats.minY, y);
        stats.maxY = Math.max(stats.maxY, y);
        stats.dy = dy;
        // Forced event observations use a separate count/origin-band oracle. Missing a display
        // scope there is not itself a transform mismatch, and cannot make exactOnceGreen true.
        if (!scopedTranslation && forced != null) return;
        boolean exactlyOnce = translationCalls == 1
                && Double.isFinite(sourceY)
                && Math.abs((y - sourceY) - dy) <= EPSILON;
        if (exactlyOnce) {
            stats.validatedCount++;
        } else {
            stats.mismatchCount++;
        }
        if (LOG) {
            Slabbed.LOGGER.info(
                    "[PARTICLE_SINK] owner={} dy={} count={} validated={} mismatches={} minY={} maxY={} type={} important={} alwaysSpawn={}",
                    owner.toShortString(), dy, stats.count,
                    stats.validatedCount, stats.mismatchCount, stats.minY, stats.maxY,
                    effect.getType(), important, alwaysSpawn);
        }
    }

    public static synchronized Snapshot snapshot(BlockPos owner) {
        MutableStats stats = BY_OWNER.get(owner.asLong());
        return stats == null
                ? new Snapshot(0L, 0L, 0L, Double.NaN, Double.NaN, 0.0d)
                : new Snapshot(stats.count, stats.validatedCount, stats.mismatchCount,
                        stats.minY, stats.maxY, stats.dy);
    }

    public static synchronized boolean containsOnlyType(
            BlockPos owner, ParticleType<?> expected
    ) {
        MutableStats stats = BY_OWNER.get(owner.asLong());
        return stats != null
                && stats.count > 0L
                && !stats.mixedType
                && stats.firstType == expected;
    }

    /** Drives the real ClientWorld display dispatcher at one exact owner cell. */
    public static Snapshot forceTickAt(ClientWorld world, BlockPos owner, long seed) {
        world.randomBlockDisplayTick(
                owner.getX(), owner.getY(), owner.getZ(),
                1, Random.create(seed), Blocks.AIR, new BlockPos.Mutable());
        return snapshot(owner);
    }

    /** Invokes the real client-only campfire extinguish emitter under an observation scope. */
    public static Snapshot forceCampfireExtinguish(ClientWorld world, BlockPos owner) {
        BlockState state = world.getBlockState(owner);
        return observeForced(world, owner,
                () -> CampfireBlock.extinguish(null, world, owner, state));
    }

    /** Invokes the real candle extinguish transition under an observation scope. */
    public static Snapshot forceCandleExtinguish(
            ClientWorld world, BlockPos owner, PlayerEntity player
    ) {
        BlockState state = world.getBlockState(owner);
        return observeForced(world, owner,
                () -> AbstractCandleBlock.extinguish(player, state, world, owner));
    }

    /** Invokes the real client lever interaction under an observation scope. */
    public static Snapshot forceLeverUse(
            ClientWorld world, BlockPos owner, PlayerEntity player, BlockHitResult hit
    ) {
        BlockState state = world.getBlockState(owner);
        return observeForced(world, owner, () -> state.onUse(world, player, hit));
    }

    /** Invokes one real client world-event handler under an observation scope. */
    public static Snapshot forceWorldEvent(
            WorldRenderer renderer, ClientWorld world, int eventId, BlockPos owner, int data
    ) {
        return observeForced(world, owner, () -> renderer.processWorldEvent(eventId, owner, data));
    }

    private static Snapshot observeForced(ClientWorld world, BlockPos owner, Runnable action) {
        BlockState state = world.getBlockState(owner);
        double dy = SlabSupport.getYOffset(world, owner, state);
        FORCED.set(new ForcedScope(owner.toImmutable(), dy));
        try {
            action.run();
        } finally {
            FORCED.remove();
        }
        return snapshot(owner);
    }

    public static synchronized void reset() {
        BY_OWNER.clear();
        FORCED.remove();
    }

    public record Snapshot(
            long count, long validatedCount, long mismatchCount,
            double minY, double maxY, double dy
    ) {
        public boolean exactOnceGreen() {
            return count > 0L && validatedCount == count && mismatchCount == 0L;
        }
    }

    private static final class MutableStats {
        private long count;
        private long validatedCount;
        private long mismatchCount;
        private ParticleType<?> firstType;
        private boolean mixedType;
        private double minY = Double.POSITIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double dy;
    }

    private record ForcedScope(BlockPos owner, double expectedDy) {
    }
}
