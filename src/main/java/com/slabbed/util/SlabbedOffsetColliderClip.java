package com.slabbed.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Offset-aware block collision for arrows only (maintainer ruling, 2026-08-28).
 *
 * <p><b>Why this exists.</b> A block's per-state collision shape stays UNLOWERED for every
 * {@code ClipContext.Block.COLLIDER} clip except a small named family (StairBlock, the beta35
 * fence/wall/gate contact family, GRINDSTONE) — the entity movement broadphase and this exact
 * shape-producing method are the same call and cannot be told apart, so a per-state fix cannot
 * discriminate its caller. That leaves a real gap: an arrow shot at the visible lower half of a
 * lowered block passes through it, because {@code Level.clip(ClipContext)} never constructs the
 * {@code BlockCollisions} the movement fix patches.
 *
 * <p><b>Why this is safe where a blanket per-state fix is not.</b> {@code Level.clip}'s DDA
 * traversal has no shell/interior classification and no cached "large collision shape" gate — the
 * specific mechanism that let the old walk-through-blocks regression skip an overhanging cell. It
 * visits every cell the ray geometrically crosses, unconditionally, so this class adds a
 * SUPPLEMENTARY search rather than touching the shape vanilla itself resolves — never lowering
 * anything in place, only checking whether an owner up to
 * {@link SlabbedOffsetRaycast#OWNER_WINDOW_RADIUS} cells away from a marched cell hangs down (or
 * up) far enough to be hit first, the same window {@link SlabbedOffsetRaycast} already searches
 * for the player's OUTLINE-clip crosshair target.
 *
 * <p><b>Attribution, not a union.</b> A candidate hit is clipped against the OWNER's own
 * {@link BlockPos}, never the marched cell's — {@code AbstractArrow} reads the resulting
 * {@code BlockHitResult}'s position to decide what it stuck into. A hit attributed to the wrong
 * cell would be a new wrong answer, not a fix.
 *
 * <p><b>Known, accepted limitation.</b> A ray that never visits the owner's own cell at all — one
 * that stays entirely inside the hang band, or one that starts inside it — still misses. That is
 * a strict improvement over today (today it misses always), never a regression, and it is the
 * same limitation {@link SlabbedOffsetRaycast} already accepts for the outline case.
 */
public final class SlabbedOffsetColliderClip {

    /**
     * Master switch. Default ON. Set {@code -Dslabbed.arrowOffsetClip=false} to fall back to
     * vanilla's unlowered arrow collision for live A/B comparison.
     */
    public static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("slabbed.arrowOffsetClip", "true"));

    /**
     * Independent switch for the OCCLUSION consumers — mob line of sight and explosion shelter.
     * Default ON. Set {@code -Dslabbed.sightOffsetClip=false} to return those two to vanilla while
     * leaving arrows fixed. Deliberately separate from {@link #ENABLED}: these run far more often
     * than arrow ticks, so if their cost ever needs backing out it must be possible to do that
     * without also reverting the live-confirmed arrow behaviour.
     */
    public static final boolean SIGHT_ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("slabbed.sightOffsetClip", "true"));

    private SlabbedOffsetColliderClip() {
    }

    /**
     * Offset-aware occlusion for consumers that only ask "is the view blocked?" — mob line of
     * sight and explosion shelter. Returns a hit whenever the ray crosses a lowered block's drawn
     * body, so cover that is visibly solid stops behaving as though it were not there.
     *
     * <p>Far cheaper than {@link #clip} despite running far more often, because a boolean consumer
     * cannot tell WHICH obstruction it found: once vanilla has already reported a hit the answer is
     * settled and the owner-window search is pure waste, so it is skipped. In any enclosed space —
     * a cave, a room, terrain — most of these rays already terminate on ordinary geometry and cost
     * exactly nothing extra. Only a ray vanilla thinks is CLEAR pays for the search, which is
     * precisely the ray that might be wrong.
     *
     * <p>Additive only. It can add an obstruction the drawn world has and vanilla missed; it never
     * removes one vanilla found. So a lowered block's un-lowered upper half — solid to this query,
     * empty on screen — still blocks, and is a known remaining asymmetry rather than something this
     * lane silently fixed.
     */
    public static BlockHitResult clipForOcclusion(
            BlockGetter world, ClipContext context, Entity viewer, BlockHitResult vanillaHit
    ) {
        if (!SIGHT_ENABLED || vanillaHit == null || vanillaHit.getType() != HitResult.Type.MISS) {
            return vanillaHit;
        }
        return clip(world, context, viewer, vanillaHit);
    }

    /**
     * Returns whichever of {@code vanillaHit} and an owner-window collision search is nearer to
     * the clip's own start point. Never returns null; falls back to {@code vanillaHit} whenever
     * the search is disabled, unsafe off-thread, or finds nothing nearer.
     */
    public static BlockHitResult clip(
            BlockGetter world, ClipContext context, Entity shooter, BlockHitResult vanillaHit
    ) {
        if (!ENABLED || world == null || context == null || vanillaHit == null) {
            return vanillaHit;
        }
        // Background server queries must not touch chunk attachments or walk support geometry:
        // the authoritative main-thread tick will resolve and synchronize the visible value.
        if (slabbed$isUnsafeAsyncShapeContext(world)) {
            return vanillaHit;
        }

        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();
        if (from == null || to == null || from.equals(to)) {
            return vanillaHit;
        }

        CollisionContext shapeContext = shooter != null ? CollisionContext.of(shooter) : CollisionContext.empty();
        OwnerWindowCollector collector = new OwnerWindowCollector(world, from, to, shapeContext);
        BlockGetter.traverseBlocks(
                from,
                to,
                collector,
                (c, cell) -> {
                    c.consumeCell(cell.getX(), cell.getY(), cell.getZ());
                    return null;
                },
                c -> null);

        BlockHitResult candidate = collector.best;
        if (candidate == null) {
            return vanillaHit;
        }
        return (BlockHitResult) SlabbedOffsetRaycast.selectNearestOwnedHit(from, vanillaHit, candidate);
    }

    private static boolean slabbed$isUnsafeAsyncShapeContext(BlockGetter world) {
        if (world instanceof ServerLevel serverLevel) {
            return !serverLevel.getServer().isSameThread();
        }
        String name = Thread.currentThread().getName();
        return name.startsWith("Worker-Main") || name.contains("ForkJoinPool");
    }

    private static final class OwnerWindowCollector {
        private final BlockGetter world;
        private final Vec3 start;
        private final Vec3 end;
        private final CollisionContext shapeContext;

        private BlockHitResult best;
        private double bestDistSq = Double.POSITIVE_INFINITY;

        OwnerWindowCollector(BlockGetter world, Vec3 start, Vec3 end, CollisionContext shapeContext) {
            this.world = world;
            this.start = start;
            this.end = end;
            this.shapeContext = shapeContext;
        }

        void consumeCell(int x, int y, int z) {
            for (int distance = 1; distance <= SlabbedOffsetRaycast.OWNER_WINDOW_RADIUS; distance++) {
                testOwner(x, y - distance, z);
                testOwner(x, y + distance, z);
            }
        }

        private void testOwner(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) {
                return;
            }
            double dy = SlabSupport.getYOffset(world, pos, state);
            if (Math.abs(dy) <= 1.0e-6d) {
                return; // unshifted; already covered when the DDA visits this position directly
            }
            VoxelShape shape = SlabSupport.ownCollisionShape(world, pos, state, shapeContext);
            if (shape.isEmpty()) {
                return;
            }
            BlockHitResult hit = world.clipWithInteractionOverride(start, end, pos, shape, state);
            if (hit == null) {
                return;
            }
            double distSq = hit.getLocation().distanceToSqr(start);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = hit;
            }
        }
    }
}
