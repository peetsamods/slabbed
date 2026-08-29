package com.slabbed.util;

import com.slabbed.mixin.ClipContextBlockAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Offset-aware {@code COLLIDER} clipping: projectiles hit, sight is blocked by, and explosions are
 * sheltered by a lowered block's DRAWN body (maintainer ruling, 2026-08-29 — include in 0.5.2).
 *
 * <p><b>The gap this closes, measured on this line.</b> With the per-state collision fix in place a
 * lowered block's own cell answers correctly, but vanilla's {@code Level.clip} DDA is cell-bounded:
 * while a ray crosses the cell BELOW a lowered block it asks that cell (air) and never sees the
 * neighbour's hanging body. Probe result: a ray through the drawn lower half = MISS on both the
 * unfixed and per-state-fixed trees. Movement got the neighbour-aware union
 * ({@code BlockCollisionsLoweredAboveMixin}); this class is that union's equivalent for the clip
 * path — a SUPPLEMENTARY owner search, never a mutation of what vanilla itself resolves.
 *
 * <p><b>Port provenance.</b> Ported as a CLASS from the NeoForge 1.21.1 line's
 * {@code SlabbedOffsetColliderClip} (its maintainer rulings 2026-08-28/29), re-derived against this
 * line's architecture rather than copied: (a) 26.2's per-state collision is already lowered, so the
 * own-cell case needs no help here — only neighbour-cell hang coverage; (b) the projectile seam on
 * 26.2 is {@code ProjectileUtil.getHitResult} (all projectiles), not {@code AbstractArrow.tick};
 * (c) 26.2's clip-mode is a call-site PARAMETER, so the mode gate below is mandatory, not
 * defensive; (d) dy on this line is never positive (reach-up retired; envelope [-3.0, 0]), so the
 * owner search looks UP only.
 *
 * <p><b>Attribution, not a union.</b> A candidate hit is clipped against the OWNER's own
 * {@link BlockPos} — projectile code reads the result's position to decide what it stuck into, so
 * a hit attributed to the marched cell would be a new wrong answer.
 *
 * <p><b>Known, accepted limitation</b> (same as the movement union's contract): a ray that starts
 * inside the hang band and ends there without ever crossing a cell within {@link #OWNER_REACH} of
 * an owner still misses. Strict improvement over vanilla (which misses always), never a regression.
 */
public final class SlabbedOffsetColliderClip {

    /**
     * Master switch for the PROJECTILE consumer. Default ON.
     * {@code -Dslabbed.projectileOffsetClip=false} restores vanilla projectile block collision for
     * live A/B. (The NeoForge sibling names this {@code arrowOffsetClip}; on 26.2 the seam is
     * ProjectileUtil, which serves every projectile, so the name follows the truth of this line.)
     */
    public static final boolean PROJECTILE_ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("slabbed.projectileOffsetClip", "true"));

    /**
     * Independent switch for the OCCLUSION consumers — mob line of sight and explosion shelter.
     * Default ON. {@code -Dslabbed.sightOffsetClip=false} returns those to vanilla while leaving
     * projectiles fixed. Separate on purpose: these run far more often, and backing their cost out
     * must not also revert projectile behaviour.
     */
    public static final boolean SIGHT_ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("slabbed.sightOffsetClip", "true"));

    /**
     * How many cells UP from a marched cell an owner can be and still hang into it. Derived, not
     * chosen: a block {@code k} cells up intersects the marched cell iff {@code k - |dy| < 1}, and
     * the legal envelope caps {@code |dy|} at 3.0 (maintainer ruling, 2026-08-24), so {@code k <= 3}.
     * If the envelope ever deepens, this must follow it.
     */
    static final int OWNER_REACH = 3;

    private static final double EPS = 1.0e-6;

    private SlabbedOffsetColliderClip() {
    }

    /**
     * Occlusion form — consumers that only ask "is the view blocked?" (mob sight, explosion
     * shelter). Once vanilla reports a hit the answer is settled and the search is skipped: a
     * boolean consumer cannot tell WHICH obstruction stopped it, so inside ordinary terrain most
     * rays cost nothing extra. Only a ray vanilla believes is clear pays, and that is exactly the
     * ray that might be wrong.
     */
    public static BlockHitResult clipForOcclusion(
            BlockGetter world, ClipContext context, BlockHitResult vanillaHit) {
        if (!SIGHT_ENABLED || vanillaHit == null || vanillaHit.getType() != HitResult.Type.MISS) {
            return vanillaHit;
        }
        return supplement(world, context, vanillaHit);
    }

    /**
     * Position form — projectiles, which read WHERE the hit landed. The search always runs and the
     * nearer of vanilla's answer and the owner-search answer wins.
     */
    public static BlockHitResult clipForProjectile(
            BlockGetter world, ClipContext context, BlockHitResult vanillaHit) {
        if (!PROJECTILE_ENABLED) {
            return vanillaHit;
        }
        return supplement(world, context, vanillaHit);
    }

    private static BlockHitResult supplement(
            BlockGetter world, ClipContext context, BlockHitResult vanillaHit) {
        if (world == null || context == null || vanillaHit == null) {
            return vanillaHit;
        }
        // MANDATORY mode gate: on 26.2 both redirected call sites take the clip mode as a
        // parameter, so OUTLINE/VISUAL contexts genuinely reach here. Those ask a different
        // question; collision geometry must not answer it.
        if (((ClipContextBlockAccessor) context).slabbed$blockMode() != ClipContext.Block.COLLIDER) {
            return vanillaHit;
        }
        // Background threads must not touch chunk attachments (the store read can block on a chunk
        // fetch); the authoritative main-thread pass resolves the visible value.
        if (isUnsafeAsyncShapeContext(world)) {
            return vanillaHit;
        }
        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();
        if (from == null || to == null || from.equals(to)) {
            return vanillaHit;
        }

        OwnerSearch search = new OwnerSearch(world, from, to);
        BlockGetter.traverseBlocks(from, to, search, (s, cell) -> {
            s.consumeCell(cell.getX(), cell.getY(), cell.getZ());
            return null;
        }, s -> null);

        BlockHitResult candidate = search.best;
        if (candidate == null) {
            return vanillaHit;
        }
        if (vanillaHit.getType() != HitResult.Type.MISS
                && vanillaHit.getLocation().distanceToSqr(from)
                        <= candidate.getLocation().distanceToSqr(from)) {
            return vanillaHit;
        }
        return candidate;
    }

    private static boolean isUnsafeAsyncShapeContext(BlockGetter world) {
        if (world instanceof ServerLevel serverLevel) {
            return !serverLevel.getServer().isSameThread();
        }
        String name = Thread.currentThread().getName();
        return name.startsWith("Worker-Main") || name.contains("ForkJoinPool");
    }

    private static final class OwnerSearch {
        private final BlockGetter world;
        private final Vec3 start;
        private final Vec3 end;

        private BlockHitResult best;
        private double bestDistSq = Double.POSITIVE_INFINITY;

        OwnerSearch(BlockGetter world, Vec3 start, Vec3 end) {
            this.world = world;
            this.start = start;
            this.end = end;
        }

        void consumeCell(int x, int y, int z) {
            // UP only: dy is never positive on this line (reach-up retired, FLUSH WINS ruling),
            // so nothing a marched cell needs can hang up from below it.
            for (int distance = 1; distance <= OWNER_REACH; distance++) {
                testOwner(x, y + distance, z);
            }
        }

        private void testOwner(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                return;
            }
            double dy = SlabSupport.getYOffset(world, pos, state);
            if (dy >= -EPS) {
                return; // unshifted (or would-be-raised): vanilla's own DDA already answered it
            }
            // The UNLOWERED shape moved by dy: on this line the ordinary getCollisionShape is
            // already lowered, so reading it and moving again would double-offset — the same trap
            // the placement gate hit (see SlabSupport#unloweredCollisionShape).
            net.minecraft.world.level.CollisionGetter collisionWorld = asCollisionGetter(world);
            if (collisionWorld == null) {
                return; // every wired seam passes a Level; anything else gets vanilla's answer
            }
            VoxelShape own = SlabSupport.unloweredCollisionShape(state, collisionWorld, pos);
            if (own.isEmpty()) {
                return;
            }
            VoxelShape moved = own.move(0.0d, dy, 0.0d);
            BlockHitResult hit = world.clipWithInteractionOverride(start, end, pos, moved, state);
            if (hit == null) {
                return;
            }
            double distSq = hit.getLocation().distanceToSqr(start);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = hit;
            }
        }

        private static net.minecraft.world.level.CollisionGetter asCollisionGetter(BlockGetter world) {
            return world instanceof net.minecraft.world.level.CollisionGetter cg ? cg : null;
        }
    }
}
